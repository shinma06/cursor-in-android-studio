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


def validate_draft_assets(branch, sha, release):
    """Publishing a draft must not expose any unrelated private asset."""
    assets = release.get('assets')
    if assets == []:
        return
    if not isinstance(assets, list) or len(assets) != 1 or assets[0].get('name') != asset_for(sha):
        raise ValueError('Unverified draft assets; preserve private custody')
    asset = assets[0]
    # Completed same-SHA uploads survive retries whose rebuilt bytes differ.
    complete = (asset.get('state') == 'uploaded' and asset.get('size', 0) > 0
                and bool(re.fullmatch('sha256:[0-9a-f]{64}', asset.get('digest', ''))))
    partial = (release.get('body') == marker(branch, sha)
               and asset.get('state') == 'starter' and asset.get('size') == 0)
    if not (complete or partial):
        raise ValueError('Incomplete ZIP identity; preserve private custody')


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
        pending_retirement = (bool(previous)
                              and sum(r['tag_name'] == tag for r in releases) == 1
                              and bool(retirement_assets(name, release, asset_for(previous))))
        if previous == sha and not force_build and not pending_retirement:
            continue
        # Preserve/recover incomplete releases. A brand-new knowledge-only branch
        # may have no ZIP; never rename the last build to an unbuilt source SHA.
        if not force_build and not pending_retirement and (previous or not release):
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


def retirement_fingerprint(asset):
    identity = {k: asset.get(k) for k in ('id', 'name', 'state', 'size', 'digest')}
    return hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()


def retirement_label(assets):
    # Individual identity receipts survive partial DELETEs and advancing product SHAs.
    return 'branch-zip-predecessors-v2:' + ','.join(sorted(retirement_fingerprint(a) for a in assets))


def retirement_tokens(label):
    label = label or ''
    for prefix in ('branch-zip-predecessor-v1:', 'branch-zip-predecessors-v2:'):
        if label.startswith(prefix):
            tokens = label[len(prefix):].split(',')
            if not tokens or any(not re.fullmatch('[0-9a-f]{64}', t) for t in tokens):
                raise ValueError('Invalid retirement identity; preserve assets')
            return set(tokens)
    return set()


def retirement_assets(branch, release, name):
    if not release or not published_sha(branch, release):
        return []
    assets = release['assets']
    if len(assets) == 1:
        return [a for a in assets if a['name'] != name]
    # A staged or current normal upload must account for every other remaining asset.
    # No receipt (including protected mixed history) means no inferred retirement.
    claimed = False
    for proof in assets:
        tokens = retirement_tokens(proof.get('label'))
        claimed = claimed or bool(tokens)
        others = [a for a in assets if a['id'] != proof['id']]
        if (tokens and re.fullmatch(r'cursor-in-android-studio-[0-9a-f]{40}\.zip', proof.get('name', ''))
                and proof.get('state') == 'uploaded' and proof.get('size', 0) > 0
                and re.fullmatch('sha256:[0-9a-f]{64}', proof.get('digest') or '')
                and all(retirement_fingerprint(a) in tokens for a in others)):
            return [a for a in assets if a['name'] != name]
    if claimed:
        raise ValueError('Pending predecessor identity changed; preserve assets')
    return []


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
    release = selected_release(branch, matches)
    if release is not None:
        release['assets'] = pages(f'releases/{release["id"]}/assets')
    mixed = any(r['draft'] for r in matches) and any(not r['draft'] for r in matches)
    obsolete = []
    predecessors = []
    if len(matches) > 1 and all(r['draft'] for r in matches):
        # Only this invocation's all-owned, empty obsolete drafts can be retired.
        for candidate in matches:
            selected_release(branch, [candidate])
            candidate['assets'] = pages(f'releases/{candidate["id"]}/assets')
            if candidate['id'] != release['id']:
                if candidate.get('assets') != []:
                    raise ValueError('Nonempty obsolete draft requires owner reconciliation')
                obsolete.append(candidate)
    if release is not None and release['draft']:
        release['assets'] = pages(f'releases/{release["id"]}/assets')
        validate_draft_assets(branch, sha, release)
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
    release['assets'] = assets
    if len(matches) == 1:
        predecessors = retirement_assets(branch, release, name)
    digest = 'sha256:' + hashlib.sha256(archive.read_bytes()).hexdigest()
    if existing and (existing.get('state') != 'uploaded' or existing.get('size', 0) <= 0
                     or not re.fullmatch('sha256:[0-9a-f]{64}', existing.get('digest', ''))):
        partial_owned = (not mixed and release.get('draft') is True
                         and release.get('body') == marker(branch, sha)
                         and existing.get('state') == 'starter' and existing.get('size') == 0)
        if not partial_owned:
            raise ValueError('Incomplete ZIP identity; owner must reconcile without automatic deletion')
        api(f'releases/assets/{existing["id"]}', 'DELETE')
        existing = None
    if not existing:
        repo = os.environ['GITHUB_REPOSITORY']
        # Only normal single-predecessor uploads carry a durable retirement claim.
        label = retirement_label(predecessors) if predecessors else ''
        query = f'name={quote(name)}' + (f'&label={quote(label)}' if label else '')
        # Upload new bytes before removing the last working download. Never execute the ZIP.
        uploaded = json.loads(gh('api', '--method', 'POST',
                                 f'https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?{query}',
                                 '-H', 'Content-Type: application/zip', '--input', str(archive)))
        if (uploaded.get('digest') != digest or uploaded.get('size') != archive.stat().st_size
                or (label and uploaded.get('label') != label)):
            raise ValueError('Uploaded ZIP digest/size mismatch')
    # Mixed success revokes every historical normal claim, not just this SHA's label.
    updates = assets if mixed else ([existing] if existing and predecessors else [])
    for owned in updates:
        target_label = '' if mixed else retirement_label(predecessors)
        if not retirement_tokens(owned.get('label')) and mixed:
            continue
        if owned.get('label') == target_label:
            continue
        changed = api(f'releases/assets/{owned["id"]}', 'PATCH', {'label': target_label})
        if ((changed.get('label') or '') != target_label or any(changed.get(k) != owned.get(k)
                for k in ('id', 'name', 'state', 'size', 'digest'))):
            raise ValueError('Retirement identity update unconfirmed; preserve assets')
        if existing and owned['id'] == existing['id']:
            existing = changed
    # A completed same-SHA upload is reusable (build timestamps can differ on retry).
    if current_head(branch) != sha:
        print('Branch moved during upload; leave last published metadata for next run to reconcile')
        return
    if release['draft']:
        # Re-read all assets and the selected identity at the private-to-public boundary.
        live = selected_release(branch, pages('releases'))
        if (live is None or live['id'] != release_id or not live['draft']
                or live.get('body') != release.get('body')):
            raise ValueError('Draft identity changed before publication')
        live['assets'] = pages(f'releases/{release_id}/assets')
        validate_draft_assets(branch, sha, live)
        expected = existing if existing else uploaded
        if (len(live['assets']) != 1
                or any(live['assets'][0].get(k) != expected.get(k)
                       for k in ('id', 'name', 'state', 'size', 'digest'))):
            raise ValueError('Draft upload changed before publication')
        if current_head(branch) != sha:
            raise ValueError('Branch changed before draft publication')
    repo = os.environ['GITHUB_REPOSITORY']
    body = (f'ブランチ: `{branch}`\n\nソース HEAD: [{sha}](https://github.com/{repo}/commit/{sha})\n\n'
            f'Assets の `{name}` を Install Plugin from Disk... で選択してください。\n'
            'Source code の ZIP はインストール用ではありません。タグは配布ページの固定識別子です。'
            '新規タグは作成時の既定ブランチを参照し、既存タグは移動しません。ZIPのソースは上記HEADです。\n\n'
            + marker(branch, sha))
    api(f'releases/{release_id}', 'PATCH', {'name': f'Plugin ZIP — {branch}', 'body': body,
        'target_commitish': default_branch, 'draft': False, 'prerelease': True, 'make_latest': 'false'})
    # Only the original single known predecessor is eligible; never infer retirement from not-mixed.
    for previous_asset in predecessors:
        if current_head(branch) != sha:
            raise ValueError('Branch changed before predecessor retirement')
        live_matches = [r for r in pages('releases') if r['tag_name'] == tag]
        if len(live_matches) != 1:
            raise ValueError('Release set changed; preserve predecessor')
        current = selected_release(branch, live_matches)
        if current is not None:
            current['assets'] = pages(f'releases/{current["id"]}/assets')
        verified = [a for a in (current or {}).get('assets', []) if a['name'] == name]
        expected_digest = existing['digest'] if existing else digest
        expected_size = existing['size'] if existing else archive.stat().st_size
        if (current is None or current['id'] != release_id or published_sha(branch, current) != sha
                or len(verified) != 1 or verified[0].get('digest') != expected_digest
                or verified[0].get('size') != expected_size):
            raise ValueError('Published ZIP readback changed; preserve predecessor')
        old = next((a for a in current['assets'] if a['id'] == previous_asset['id']), None)
        if old is not None:
            if any(old.get(k) != previous_asset.get(k) for k in ('id', 'name', 'state', 'size', 'digest')):
                raise ValueError('Predecessor changed; preserve it')
            api(f'releases/assets/{old["id"]}', 'DELETE')
            if any(a['id'] == old['id'] for a in pages(f'releases/{release_id}/assets')):
                raise ValueError('Predecessor retirement unconfirmed; preserve receipt')
    # Mixed custody is never resumed as an all-draft retirement on a later invocation.
    retired = set()
    for candidate in obsolete:
        if current_head(branch) != sha:
            raise ValueError('Branch changed before draft retirement')
        current = [r for r in pages('releases') if r['tag_name'] == tag]
        if {r['id'] for r in current} != {r['id'] for r in matches} - retired:
            raise ValueError('Release set changed; preserve obsolete drafts')
        chosen = selected_release(branch, current)
        if chosen is not None:
            chosen['assets'] = pages(f'releases/{chosen["id"]}/assets')
        verified = [a for a in (chosen or {}).get('assets', []) if a['name'] == name]
        expected_digest = existing['digest'] if existing else digest
        expected_size = existing['size'] if existing else archive.stat().st_size
        if (chosen is None or chosen['id'] != release_id or published_sha(branch, chosen) != sha
                or len(verified) != 1 or len(chosen['assets']) != 1
                or verified[0].get('digest') != expected_digest
                or verified[0].get('size') != expected_size):
            raise ValueError('Published ZIP readback changed; preserve obsolete drafts')
        live = next((r for r in current if r['id'] == candidate['id']), None)
        if live is not None:
            live['assets'] = pages(f'releases/{live["id"]}/assets')
        if live != candidate or live.get('assets') != []:
            raise ValueError('Obsolete draft changed; preserve it')
        selected_release(branch, [live])
        api(f'releases/{candidate["id"]}', 'DELETE')
        if any(r['id'] == candidate['id'] for r in pages('releases')):
            raise ValueError('Draft retirement unconfirmed; inspect before retry')
        retired.add(candidate['id'])
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
