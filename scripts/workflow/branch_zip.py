#!/usr/bin/env python3
"""Publish identified branch ZIPs while preserving existing downloads and drafts."""
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
        current = [a for a in assets if a['name'] == asset_for(sha)]
        if (data['branch'] == branch and len(current) == 1
                and current[0].get('state') == 'uploaded' and current[0].get('size', 0) > 0):
            return sha
    except (KeyError, TypeError, ValueError):
        pass
    return None


def selected_release(branch, releases):
    """Use the same deterministic, owned destination for planning and publishing."""
    matches = [r for r in releases if r['tag_name'] == tag_for(branch)]
    if not matches:
        return None
    if any(type(r.get('draft')) is not bool for r in matches):
        raise ValueError('Unknown release visibility; preserve all releases')
    published = [r for r in matches if not r['draft']]
    if len(published) > 1:
        raise ValueError('Multiple published releases for branch tag')
    if any(type(r.get('id')) is not int or r['id'] <= 0 for r in matches):
        raise ValueError('Invalid release identity')
    if len({r['id'] for r in matches}) != len(matches):
        raise ValueError('Duplicate release identity; preserve all releases')
    release = published[0] if published else max(matches, key=lambda r: r['id'])
    markers = re.findall(r'<!-- branch-zip:(\{[^\n]*\}) -->', release.get('body') or '')
    try:
        owned = json.loads(markers[0]) if len(markers) == 1 else {}
        valid = isinstance(owned, dict) and owned.get('branch') == branch and bool(re.fullmatch('[0-9a-f]{40}', owned.get('sha', '')))
    except (TypeError, ValueError):
        valid = False
    if (not valid or release.get('name') != f'Plugin ZIP — {branch}'
            or release.get('prerelease') is not True or release.get('immutable')):
        raise ValueError('Unowned or immutable branch release; preserve it for its owner')
    # Nonselected drafts are custody, not deletion permission. Retirement is separate.
    return release


def plan(branch=None, force_build=False):
    releases = pages('releases')
    pending = []
    for b in pages('branches'):
        name, sha = b['name'], b['commit']['sha']
        if branch is not None and name != branch:
            continue
        tag = tag_for(name)
        release = selected_release(name, releases) or {}
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
    release = selected_release(branch, pages('releases'))
    # Existing tags stay fixed; reviewed default tooling anchors new destinations.
    default_branch = api('')['default_branch']
    if release is None:
        release = api('releases', 'POST', {
            'tag_name': tag, 'target_commitish': default_branch, 'name': f'Plugin ZIP — {branch}',
            'body': marker(branch, sha), 'draft': True, 'prerelease': True, 'make_latest': 'false',
        })
    if release.get('immutable'):
        raise ValueError('Branch ZIP releases must remain mutable')
    release_id = release['id']
    # Numeric ID also works for drafts; gh release upload may resolve the public tag instead.
    assets = pages(f'releases/{release_id}/assets')
    matching = [a for a in assets if a['name'] == name]
    if len(matching) > 1:
        raise ValueError('Duplicate ZIP identity; preserve assets')
    existing = matching[0] if matching else None
    digest = 'sha256:' + hashlib.sha256(archive.read_bytes()).hexdigest()
    if existing and (existing.get('state') != 'uploaded' or existing.get('size', 0) <= 0
                     or not re.fullmatch('sha256:[0-9a-f]{64}', existing.get('digest', ''))):
        raise ValueError('Incomplete ZIP identity; owner must reconcile without automatic deletion')
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
    # Preserve historical URLs, unknown assets and partial uploads; retirement needs its own scope.
    print(f'https://github.com/{repo}/releases/tag/{tag}')


def cleanup_identity(release):
    """Require the publisher's complete identity, never a tag prefix alone."""
    try:
        markers = re.findall(r'<!-- branch-zip:(\{[^\n]*\}) -->', release.get('body') or '')
        if len(markers) != 1:
            return None
        data = json.loads(markers[0])
        branch = data['branch']
        sha = published_sha(branch, release)
        if (not isinstance(branch, str) or not branch or release['tag_name'] != tag_for(branch)
                or release.get('name') != f'Plugin ZIP — {branch}'
                or release.get('prerelease') is not True or release.get('immutable')
                or len(release.get('assets', [])) != 1 or not sha or sha != data['sha']):
            return None
        return {'branch': branch, 'sha': data['sha'], 'tag': release['tag_name'],
                'release_id': release['id']}
    except (KeyError, TypeError, ValueError):
        return None


def cleanup_report(branch=None):
    releases = pages('releases')
    branches = {b['name'] for b in pages('branches')}
    protected = {'main', 'master', 'develop', api('')['default_branch']}
    tags = {r['ref'].removeprefix('refs/tags/'): r['object']
            for r in api('git/matching-refs/tags/branch-zip-')}
    candidates, kept = [], []
    for release in releases:
        tag = release['tag_name']
        identity = cleanup_identity(release)
        reason = None
        if not identity:
            reason = 'unmanaged, draft, immutable, or incomplete publisher identity'
        elif identity['branch'] in protected:
            reason = 'protected integration/default branch'
        elif identity['branch'] in branches:
            reason = 'branch exists (age is irrelevant)'
        elif branch is not None and identity['branch'] != branch:
            reason = 'outside requested branch'
        elif sum(r['tag_name'] == tag for r in releases) != 1:
            reason = 'ambiguous duplicate release tag'
        elif tag not in tags:
            reason = 'release tag missing; inspect history before retry'
        if reason:
            kept.append({'tag': tag, 'release_id': release['id'], 'reason': reason})
        else:
            candidates.append(identity)
    release_tags = {r['tag_name'] for r in releases}
    kept.extend({'tag': tag, 'reason': 'tag only; ownership cannot be inferred'}
                for tag in tags.keys() - release_tags)
    if len(candidates) > 256:
        raise ValueError('More than 256 cleanup candidates; select a branch for manual dispatch')
    return {'candidates': candidates, 'kept': kept}


def cleanup_note(record):
    message = json.dumps(record, ensure_ascii=True)
    print(message, file=sys.stderr)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
            summary.write('```json\n' + message + '\n```\n')


def cleanup(branch, sha, release_id):
    expected = {'branch': branch, 'sha': sha, 'tag': tag_for(branch), 'release_id': release_id}
    # Called only under the same Actions tag concurrency group as publish.
    # Re-plan after acquiring that lock; an old plan cannot authorize deletion.
    if expected not in cleanup_report(branch)['candidates']:
        cleanup_note({**expected, 'result': 'held', 'next': 'inspect current dry-run; candidate changed'})
        return
    tag_path = 'git/matching-refs/tags/' + expected['tag']
    tag_ref = 'refs/tags/' + expected['tag']
    refs = [r for r in api(tag_path) if r['ref'] == tag_ref]
    if len(refs) != 1 or current_head(branch) is not None:
        cleanup_note({**expected, 'result': 'held', 'next': 'branch recreated or tag changed; re-plan'})
        return
    receipt = {**expected, 'tag_object': refs[0]['object']}
    # The receipt precedes deletion. If tag deletion fails, never guess ownership
    # from the remaining tag: keep it for an operator to reconcile with this run.
    cleanup_note({**receipt, 'result': 'verified', 'next': 'delete release/assets, then unchanged tag'})
    api(f'releases/{release_id}', 'DELETE')
    remaining = pages('releases')
    refs = [r for r in api(tag_path) if r['ref'] == tag_ref]
    if (any(r['tag_name'] == expected['tag'] for r in remaining)
            or current_head(branch) is not None
            or (refs and (len(refs) != 1 or refs[0]['object'] != receipt['tag_object']))):
        cleanup_note({**receipt, 'result': 'tag held',
                      'next': 'inspect recreation/change; live branch publisher recovers its release'})
        return
    try:
        if refs:
            api('git/refs/tags/' + expected['tag'], 'DELETE')
    except Exception:
        cleanup_note({**receipt, 'result': 'release deleted; tag deletion unconfirmed',
                      'next': 'operator compares this receipt with fresh branch/ref APIs; tag-only is held on rerun'})
        raise
    if (any(r['tag_name'] == expected['tag'] for r in pages('releases'))
            or any(r['ref'] == tag_ref for r in api(tag_path))):
        raise RuntimeError('Cleanup readback changed; inspect receipt and re-run dry-run')
    cleanup_note({**receipt, 'result': 'deleted', 'next': 'none'})


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('command', choices=['plan', 'publish', 'cleanup-plan', 'cleanup'])
    parser.add_argument('--branch')
    parser.add_argument('--sha')
    parser.add_argument('--release-id', type=int)
    parser.add_argument('--directory', default='delivery')
    parser.add_argument('--force-build', action='store_true')
    args = parser.parse_args()
    if args.command == 'plan':
        print(json.dumps(plan(args.branch or None, args.force_build), ensure_ascii=True))
    elif args.command == 'cleanup-plan':
        report = cleanup_report(args.branch or None)
        cleanup_note(report)
        print(json.dumps(report['candidates'], ensure_ascii=True))
    elif args.command == 'cleanup':
        if not args.branch or not args.sha or not args.release_id:
            parser.error('cleanup needs --branch, --sha and --release-id')
        cleanup(args.branch, args.sha, args.release_id)
    else:
        if not args.branch or not args.sha:
            parser.error('publish needs --branch and --sha')
        publish(args.branch, args.sha, args.directory)


if __name__ == '__main__':
    main()
