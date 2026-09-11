#!/usr/bin/env python3
"""Keep one standard Gradle plugin ZIP per live branch in GitHub Releases."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

from change_impact import git_impact, report as impact_report
from urllib.parse import quote


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True).strip()


def api(path, method='GET', data=None):
    endpoint = f'repos/{os.environ["GITHUB_REPOSITORY"]}' + (f'/{path}' if path else '')
    args = ['gh', 'api', endpoint, '--method', method]
    if data is not None:
        args += ['--input', '-']
    return json.loads(subprocess.check_output(args, input=json.dumps(data) if data is not None else None, text=True) or 'null')


def pages(path):
    result = []
    page = 1
    while True:
        rows = api(f'{path}?per_page=100&page={page}')
        result.extend(rows)
        if len(rows) < 100:
            return result
        page += 1


def tag_for(branch):
    # Full digest handles slashes, Unicode, long names and case-only differences.
    return 'branch-zip-' + hashlib.sha256(branch.encode()).hexdigest()


def asset_for(sha):
    if not re.fullmatch('[0-9a-f]{40}', sha):
        raise ValueError('Expected a full commit SHA')
    return f'cursor-in-android-studio-{sha}.zip'


def marker(branch, sha):
    return '<!-- branch-zip:' + json.dumps({'branch': branch, 'sha': sha}, ensure_ascii=True) + ' -->'


def current_head(branch):
    # Listing also distinguishes deletion from API/authentication failure.
    return next((b['commit']['sha'] for b in pages('branches') if b['name'] == branch), None)


def published_sha(branch, release):
    try:
        markers = re.findall(r'<!-- branch-zip:(\{[^\n]*\}) -->', release.get('body') or '')
        if len(markers) != 1 or release.get('draft', True):
            return None
        data = json.loads(markers[0])
        sha = data['sha']
        assets = release.get('assets', [])
        if (data['branch'] == branch and len(assets) == 1 and assets[0]['name'] == asset_for(sha)
                and assets[0].get('state') == 'uploaded' and assets[0].get('size', 0) > 0):
            return sha
    except (KeyError, TypeError, ValueError):
        pass
    return None


def plan(branch=None, force_build=False):
    releases = {r['tag_name']: r for r in pages('releases')}
    pending = []
    for b in pages('branches'):
        name, sha = b['name'], b['commit']['sha']
        if branch is not None and name != branch:
            continue
        tag = tag_for(name)
        release = releases.get(tag, {})
        previous = published_sha(name, release)
        if previous == sha and not force_build:
            continue
        # Preserve/recover incomplete releases. A brand-new knowledge-only branch
        # may have no ZIP; never rename the last build to an unbuilt source SHA.
        if not force_build and (previous or not release):
            impact = git_impact(previous, sha, merge_base=False)
            message = f'Branch {json.dumps(name)} / {sha}:\n' + impact_report(impact)
            print(message, file=sys.stderr)
            if os.environ.get('GITHUB_STEP_SUMMARY'):
                with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
                    summary.write(message + '\n\n')
            if not impact['plugin_zip']:
                continue
        pending.append({'branch': name, 'sha': sha, 'tag': tag})
    # GitHub matrix supports at most 256 jobs; fail visibly instead of dropping branches.
    if len(pending) > 256:
        raise ValueError('More than 256 pending branches; split the manual dispatch by branch')
    return pending


def publish(branch, sha, directory):
    name = asset_for(sha)
    archive = Path(directory) / name
    if not archive.is_file() or archive.stat().st_size == 0:
        raise ValueError('Expected the standard Gradle ZIP transferred by this run')
    if current_head(branch) != sha:
        print('Branch moved or was deleted; skip obsolete build')
        return
    tag = tag_for(branch)
    matches = [r for r in pages('releases') if r['tag_name'] == tag]
    if len(matches) > 1:
        raise ValueError('Multiple releases for branch tag')
    # Anchor new tags on the default branch; source-only workflow changes can
    # require permissions unavailable to GITHUB_TOKEN when tagging their SHA.
    # Release updates leave existing tag refs untouched.
    default_branch = api('')['default_branch']
    release = matches[0] if matches else api('releases', 'POST', {
        'tag_name': tag, 'target_commitish': default_branch, 'name': f'Plugin ZIP — {branch}',
        'draft': True, 'prerelease': True, 'make_latest': 'false',
    })
    if release.get('immutable'):
        raise ValueError('Branch ZIP releases must remain mutable')
    release_id = release['id']
    # Numeric ID also works for drafts; gh release upload may resolve the public tag instead.
    assets = pages(f'releases/{release_id}/assets')
    existing = next((a for a in assets if a['name'] == name), None)
    digest = 'sha256:' + hashlib.sha256(archive.read_bytes()).hexdigest()
    if existing and (existing.get('state') != 'uploaded' or existing.get('size', 0) == 0):
        api(f'releases/assets/{existing["id"]}', 'DELETE')
        existing = None
    if not existing:
        repo = os.environ['GITHUB_REPOSITORY']
        # Upload new bytes before removing the last working download. Never execute the ZIP.
        uploaded = json.loads(gh('api', '--method', 'POST',
                                 f'https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?name={quote(name)}',
                                 '-H', 'Content-Type: application/zip', '--input', str(archive)))
        if uploaded.get('digest') != digest or uploaded.get('size') != archive.stat().st_size:
            raise ValueError('Uploaded ZIP digest/size mismatch')
    # A completed same-SHA upload is reusable (build timestamps can differ on retry).
    if current_head(branch) != sha:
        print('Branch moved during upload; leave last published metadata for next run to reconcile')
        return
    repo = os.environ['GITHUB_REPOSITORY']
    body = (f'ブランチ: `{branch}`\n\nソース HEAD: [{sha}](https://github.com/{repo}/commit/{sha})\n\n'
            f'Assets の `{name}` を Install Plugin from Disk... で選択してください。\n'
            'Source code の ZIP はインストール用ではありません。タグは配布ページの固定識別子です。'
            '新規タグは作成時の既定ブランチを参照し、既存タグは移動しません。ZIPのソースは上記HEADです。\n\n'
            + marker(branch, sha))
    api(f'releases/{release_id}', 'PATCH', {'name': f'Plugin ZIP — {branch}', 'body': body,
        'target_commitish': default_branch, 'draft': False, 'prerelease': True, 'make_latest': 'false'})
    for asset in pages(f'releases/{release_id}/assets'):
        if asset['name'] != name:
            api(f'releases/assets/{asset["id"]}', 'DELETE')
    print(f'https://github.com/{repo}/releases/tag/{tag}')


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('command', choices=['plan', 'publish'])
    parser.add_argument('--branch')
    parser.add_argument('--sha')
    parser.add_argument('--directory', default='delivery')
    parser.add_argument('--force-build', action='store_true')
    args = parser.parse_args()
    if args.command == 'plan':
        print(json.dumps(plan(args.branch or None, args.force_build), ensure_ascii=True))
    else:
        if not args.branch or not args.sha:
            parser.error('publish needs --branch and --sha')
        publish(args.branch, args.sha, args.directory)


if __name__ == '__main__':
    main()
