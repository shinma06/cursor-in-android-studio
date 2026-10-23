#!/usr/bin/env python3
"""Inactive delivery definitions. Activation requires a separate reviewed change."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from urllib.parse import quote
import zipfile

import plugin_compatibility as pc
import release_candidate as rc
from change_impact import git_impact
from verification import git_read as git

REPO = 'shinma06/cursor-in-android-studio'
CI_CHECKS = ('test', 'PR policy', 'Agent review', 'Acceptance gate')
MARKER = '<!-- plugin-delivery:v1\n'
MAX_ARCHIVE = 256 * 1024 * 1024
SHA = re.compile(r'[0-9a-f]{40}')
HASH = re.compile(r'[0-9a-f]{64}')
# No environment variable or existing event can enable the new operation.
ENABLED = False


def require_enabled():
    require(ENABLED, 'Delivery definitions are inactive; activation is outside Issue 572')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def command(args, input=None):
    # Reviewed publisher code needs the workflow token; agent workers must still strip it.
    result = subprocess.run(args, input=input, text=True, capture_output=True, timeout=180)
    if result.returncode:
        raise RuntimeError(f'{args[0]} failed: {result.stderr[-1800:]}')
    return result.stdout.strip()


def api(path, method='GET', data=None):
    if method != 'GET':
        require_enabled()
    args = ['gh', 'api', '--method', method, f'repos/{REPO}' + ('/' + path if path else '')]
    try:
        out = command(args + (['--input', '-'] if data is not None else []),
                      json.dumps(data) if data is not None else None)
        return json.loads(out) if out else None
    except (OSError, RuntimeError, subprocess.SubprocessError, ValueError):
        if method != 'GET':
            uncertain_write('early')
        raise


def pages(path):
    return [item for page in json.loads(command(['gh', 'api', '--paginate', '--slurp',
                                                 f'repos/{REPO}/{path}'])) for item in page]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def download(path, destination, accept='application/octet-stream'):
    with destination.open('xb') as output:
        subprocess.run(['gh', 'api', f'repos/{REPO}/{path}', '-H', 'Accept: ' + accept],
                       stdout=output, check=True, timeout=180)
    require(destination.stat().st_size <= MAX_ARCHIVE, 'Downloaded archive exceeds the delivery limit')


def members(archive):
    entries = archive.infolist()
    require(len({e.filename for e in entries}) == len(entries), 'Duplicate ZIP entries')
    require(sum(e.file_size for e in entries) <= MAX_ARCHIVE, 'Expanded archive exceeds the delivery limit')
    for entry in entries:
        name = entry.filename
        require(name and not name.startswith(('/', '\\')) and '\\' not in name
                and '..' not in name.split('/') and ':' not in name
                and (entry.external_attr >> 16) & 0o170000 != 0o120000,
                'Unsafe ZIP entry')
    require(archive.testzip() is None, 'Corrupt ZIP')
    return entries


def identity(path, source, expected_hash):
    require(SHA.fullmatch(source or '') and HASH.fullmatch(expected_hash or ''), 'Fixed source/hash required')
    require(path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= MAX_ARCHIVE,
            'Bounded regular plugin ZIP required')
    require(digest(path) == expected_hash, 'ZIP hash mismatch')
    with zipfile.ZipFile(path) as outer:
        for entry in members(outer):
            if entry.filename.endswith('.jar'):
                with zipfile.ZipFile(io.BytesIO(outer.read(entry))) as jar:
                    members(jar)
                    if 'META-INF/plugin.xml' in jar.namelist():
                        xml = jar.read('META-INF/plugin.xml')
                        require(b'<!DOCTYPE' not in xml and b'<!ENTITY' not in xml, 'Unexpected XML declarations')
    policy = json.loads(git('show', f'{source}:scripts/workflow/plugin_compatibility.json'))
    manifest = pc.seal(path, source, policy)
    require(manifest['sha256'] == expected_hash, 'ZIP changed during identity inspection')
    version = manifest['identity']['plugin.version']
    require(re.fullmatch(r'[0-9A-Za-z.+-]{1,120}', version), 'Invalid plugin version')
    return {'source': source, 'version': version, 'sha256': expected_hash, 'size': manifest['size']}


def candidate_artifact(expected_hash, directory, run_id=None):
    require(HASH.fullmatch(expected_hash or ''), 'Fixed candidate hash required')
    require(run_id is None or type(run_id) is int and run_id > 0, 'Positive candidate run required')
    path = (f'actions/runs/{run_id}/artifacts?per_page=100' if run_id is not None else
            f'actions/artifacts?name=candidate-{expected_hash}&per_page=100')
    collection = api(path)
    items = collection.get('artifacts', [])
    require(collection.get('total_count') == len(items), 'Incomplete candidate artifact listing')
    matches = [a for a in items if a.get('name') == 'candidate-' + expected_hash and a.get('expired') is False]
    require(len(matches) == 1, 'One live candidate artifact required; retain the identical local bundle for expiry')
    item = matches[0]
    producer = item.get('workflow_run', {}).get('id', run_id)
    require(type(producer) is int and producer > 0 and (run_id is None or producer == run_id),
            'Candidate producer identity missing')
    run = api(f'actions/runs/{producer}')
    producer_run(producer, run.get('head_sha'), '.github/workflows/release-candidate.yml')
    require(run.get('event') == 'workflow_dispatch' and run.get('head_branch') == 'main',
            'Candidate must come from the trusted main workflow')
    git('fetch', '--no-tags', 'origin', 'main')
    git('merge-base', '--is-ancestor', run['head_sha'], 'origin/main')
    directory.mkdir(parents=True, exist_ok=False)
    download_artifact(item, directory)
    rc.fetch_source(directory)
    manifest = rc.validate_bundle(directory, expected_hash)
    require(set(p.name for p in directory.iterdir()) == {*rc.read(directory / 'bundle.json'), 'bundle.json'},
            'Unexpected candidate artifact files')
    return manifest


def release_body(record):
    name = asset_name(record)
    return (f'# Cursor in Android Studio early\n\n'
            '開発版です。CI成功は全GUI受入の完了を示しません。\n\n'
            f'[インストール用ZIP](https://github.com/{REPO}/releases/download/early/{name})\n\n'
            f'- Plugin version: `{record["version"]}`\n- Source: `{record["source"]}`\n'
            f'- SHA-256: `{record["sha256"]}`\n'
            f'- [変更・受入](https://github.com/{REPO}/pull/{record["pr"]})\n'
            f'- 確認したdevelop: `{record["develop_head"]}`\n\n'
            'early tagは保管場所の固定識別子です。製品sourceは上記のSHAです。\n'
            'GitHub自動生成のSource code ZIPはインストール用ではありません。\n\n'
            + MARKER + json.dumps(record, sort_keys=True, ensure_ascii=False) + '\n-->\n')


def uncertain_write(tag):
    # Never retry an indeterminate write; retain a bounded, public-safe readback receipt.
    try:
        release = find_release(tag)
        assets = pages(f'releases/{release["id"]}/assets?per_page=100') if release else []
        refs = api('git/matching-refs/tags/' + tag)
        receipt = f'release={release["id"] if release else None}, assets={len(assets)}, refs={len(refs)}'
    except (OSError, RuntimeError, subprocess.SubprocessError, ValueError) as error:
        receipt = 'readback unavailable: ' + type(error).__name__
    raise RuntimeError('Indeterminate delivery write; ' + receipt + '; preserve state and reconcile before retry')


def workflow_matches(run, path):
    workflow = api('actions/workflows/' + quote(Path(path).name, safe=''))
    branch = run.get('head_branch')
    # Resolve current identity independently of the run's optional ref suffix.
    return (isinstance(workflow, dict) and type(workflow.get('id')) is int and workflow['id'] > 0
            and workflow.get('path') == path and type(run.get('workflow_id')) is int
            and run['workflow_id'] == workflow['id']
            and (run.get('path') == path or isinstance(branch, str) and branch != ''
                 and run.get('path') == path + '@' + branch))


def producer_run(run_id, source, workflow='.github/workflows/ci.yml'):
    require(type(run_id) is int and run_id > 0 and SHA.fullmatch(source or ''), 'Invalid run or source')
    run = api(f'actions/runs/{run_id}')
    require(run.get('repository', {}).get('full_name') == REPO
            and run.get('head_repository', {}).get('full_name') == REPO
            and run.get('head_sha') == source and run.get('status') == 'completed'
            and run.get('conclusion') == 'success' and workflow_matches(run, workflow),
            'Artifact must come from the identified successful repository workflow')
    require(run.get('event') in ('push', 'workflow_dispatch'), 'PR artifacts cannot directly become release assets')
    return run


def artifact(run_id, source, directory, workflow='.github/workflows/ci.yml'):
    run = producer_run(run_id, source, workflow)
    collection = api(f'actions/runs/{run_id}/artifacts?per_page=100')
    items = collection.get('artifacts', [])
    require(collection.get('total_count') == len(items), 'Incomplete artifact listing')
    selected = [a for a in items if a.get('name') == 'compatibility-input']
    require(len(selected) == 1 and selected[0].get('expired') is False, 'One live source-bound artifact required')
    files = download_artifact(selected[0], directory)
    archives = [p for p in files if p.suffix == '.zip']
    require(len(archives) == 1 and {p.name for p in files} == {archives[0].name, 'manifest.json'},
            'CI artifact needs exactly one standard ZIP and manifest')
    manifest = rc.read(directory / 'manifest.json')
    actual = identity(archives[0], source, manifest['sha256'])
    require(actual == {key: manifest[key] for key in ('sha256', 'size')} |
            {'source': manifest['identity']['source.commit'], 'version': manifest['identity']['plugin.version']},
            'CI manifest differs from the actual ZIP')
    require(pc.plugin_identity(archives[0]) == manifest['identity'], 'CI manifest identity differs')
    return archives[0], run


def download_artifact(item, directory):
    require(type(item.get('id')) is int and item['id'] > 0
            and type(item.get('size_in_bytes')) is int and 0 < item['size_in_bytes'] <= MAX_ARCHIVE
            and re.fullmatch(r'sha256:[0-9a-f]{64}', item.get('digest', '')),
            'Missing artifact transport identity')
    transport = directory / 'transport.zip'
    # Artifact API redirects use JSON negotiation; release assets request binary content.
    download(f'actions/artifacts/{item["id"]}/zip', transport, accept='application/vnd.github+json')
    require(transport.stat().st_size == item['size_in_bytes'], 'Artifact transport size mismatch')
    require('sha256:' + digest(transport) == item['digest'], 'Artifact transport digest mismatch')
    with zipfile.ZipFile(transport) as archive:
        files = [e for e in members(archive) if not e.is_dir()]
        require(files and all(Path(e.filename).name == e.filename for e in files), 'Expected flat artifact files')
        require(all(not (directory / e.filename).exists() for e in files), 'Artifact output already exists')
        for entry in files:
            with (directory / entry.filename).open('xb') as output:
                output.write(archive.read(entry))
    transport.unlink()
    return [directory / e.filename for e in files]


def checks(pr):
    data = json.loads(command(['gh', 'pr', 'checks', str(pr['number']), '--repo', REPO,
                               '--json', 'name,state']))
    for name in CI_CHECKS:
        states = [item['state'] for item in data if item['name'] == name]
        require(states and all(state == 'SUCCESS' for state in states), 'Required PR check incomplete: ' + name)


def current_develop(source):
    require(api('git/ref/heads/develop').get('object', {}).get('sha') == source,
            'Develop advanced; a stale run must not replace early')


def early_gate(run, head):
    source = run['head_sha']
    require(run.get('event') == 'push' and run.get('head_branch') == 'develop', 'Early requires a develop push')
    current_develop(head)
    matches = [p for p in pages(f'commits/{source}/pulls?per_page=100')
               if p.get('merge_commit_sha') == source and p.get('base', {}).get('ref') == 'develop']
    require(len(matches) == 1, 'Early source needs one merged develop PR')
    pr = api(f'pulls/{matches[0]["number"]}')
    require(pr.get('merged') and pr['base']['repo']['full_name'] == REPO
            and (pr.get('head', {}).get('repo') or {}).get('full_name') == REPO,
            'Early source needs a merged same-repository PR')
    checks(pr)
    return pr['number']


def distribution_change(source):
    result = git_impact(source + '^1', source, merge_base=False)
    if result.get('base') and not result['files']:
        return False
    require(not result['reason'], 'Cannot establish complete delivery inputs')
    return result['plugin_zip']


def early_product(run_id, source, directory):
    trigger = producer_run(run_id, source)
    require(trigger.get('event') == 'push' and trigger.get('head_branch') == 'develop',
            'Early requires a develop push')
    git('fetch', '--no-tags', 'origin', source)
    if not distribution_change(source):
        return None  # An operational/test-only event cannot retry or rewrite a release.
    head = api('git/ref/heads/develop').get('object', {}).get('sha')
    require(SHA.fullmatch(head or ''), 'Current develop identity missing')
    git('fetch', '--no-tags', 'origin', head)
    git('merge-base', '--is-ancestor', source, head)
    for candidate in git('rev-list', '--first-parent', '--max-count=100', head).splitlines():
        current_develop(head)
        runs = api('actions/workflows/ci.yml/runs?branch=develop&event=push&head_sha=' + candidate + '&per_page=100')
        items = runs.get('workflow_runs', [])
        require(runs.get('total_count') == len(items) and items
                and all(type(r.get('id')) is int and r['id'] > 0 for r in items),
                'Current product history needs complete CI records; do not fall back past unknown history')
        selected = max(items, key=lambda run: run['id'])
        run = producer_run(selected['id'], candidate)
        number = early_gate(run, head)
        if distribution_change(candidate):
            archive, run = artifact(selected['id'], candidate, directory)
            return archive, run, number, head
        # A later non-product commit needs successful checks, but no replacement ZIP.
    raise ValueError('No identified product artifact in the verified develop history')


def read_record(body):
    require(isinstance(body, str) and body.count(MARKER) == 1, 'Release is not owned by this publisher')
    match = re.fullmatch(r'.*\n<!-- plugin-delivery:v1\n([^\n]+)\n-->\n?', body, re.S)
    require(match is not None, 'Malformed release identity')
    record = json.loads(match[1])
    require(record.get('schema') == 1 and record.get('repository') == REPO
            and record.get('channel') == 'early' and SHA.fullmatch(record.get('source', ''))
            and HASH.fullmatch(record.get('sha256', '')) and type(record.get('size')) is int
            and 0 < record['size'] <= MAX_ARCHIVE and SHA.fullmatch(record.get('develop_head', ''))
            and re.fullmatch(r'[0-9A-Za-z.+-]{1,120}', record.get('version', ''))
            and type(record.get('pr')) is int and record['pr'] > 0
            and type(record.get('run')) is int and record['run'] > 0, 'Invalid release identity')
    return record


def asset_name(record):
    return 'cursor-in-android-studio-' + record['sha256'] + '.zip'


def find_release(tag):
    matches = [r for r in pages('releases?per_page=100') if r['tag_name'] == tag]
    require(len(matches) <= 1, 'Ambiguous release')
    return matches[0] if matches else None


def verify_asset(asset, record):
    require(asset.get('state') == 'uploaded' and asset.get('size') == record['size']
            and asset.get('digest') == 'sha256:' + record['sha256'], 'Published asset metadata differs')
    with tempfile.TemporaryDirectory() as temporary:
        fetched = Path(temporary) / 'asset.zip'
        download(f'releases/assets/{asset["id"]}', fetched)
        require(digest(fetched) == record['sha256'], 'Published asset bytes differ')


def automatic_release_target(target):
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        return
    default = api('').get('default_branch')
    require(isinstance(default, str) and default, 'Default branch identity missing')
    head = api('git/ref/heads/' + quote(default, safe='')).get('object', {}).get('sha')
    require(SHA.fullmatch(head or ''), 'Default branch commit missing')
    git('fetch', '--no-tags', 'origin', head, target)
    changed = git('diff', '--name-only', head, target, '--', '.github/workflows')
    require(not changed,
            'Automatic delivery needs Workflows write permission for fixed target ' + target +
            '; GITHUB_TOKEN cannot provide it. Preserve the target/ZIP for the separately authorized activation owner; retrying cannot grant permission.')


def publish(path, record, target):
    require_enabled()
    # Own the accepted bytes throughout upload/readback; the caller's path may change.
    require(path.is_file() and not path.is_symlink() and 0 < record['size'] <= MAX_ARCHIVE,
            'A bounded regular accepted ZIP is required')
    with path.open('rb') as source:
        data = source.read(MAX_ARCHIVE + 1)
    require(len(data) == record['size'] and hashlib.sha256(data).hexdigest() == record['sha256'],
            'ZIP changed after identity verification; no release changed')
    with tempfile.TemporaryDirectory() as temporary:
        fixed = Path(temporary) / 'accepted.zip'
        fixed.write_bytes(data)
        require(identity(fixed, record['source'], record['sha256']) ==
                {key: record[key] for key in ('source', 'version', 'sha256', 'size')},
                'Fixed ZIP identity differs from the accepted record; no release changed')
        run = producer_run(record['run'], record['source'])
        require(early_gate(run, record['develop_head']) == record['pr'], 'Early PR identity changed')
        return publish_fixed(fixed, record, target)


def publish_fixed(path, record, target):
    require_enabled()
    require(record['channel'] == 'early', 'Only the rolling early channel is mutable')
    develop_head = record.get('develop_head', record['source'])
    tag = 'early'
    body, name = release_body(record), asset_name(record)
    require(SHA.fullmatch(target or ''), 'Fixed tag target required')
    release = find_release(tag)
    previous = None
    if release:
        previous = read_record(release['body'])
        require(release['body'] == release_body(previous), 'Release notes changed outside this publisher')
        require(not release.get('immutable') and release['prerelease'] is True,
                'Release ownership/channel/immutability mismatch')
        if all(previous.get(key) == record.get(key) for key in ('source', 'sha256', 'size', 'version')):
            body = release['body']  # Rechecking the same product does not churn its run/head metadata.
    refs = [r for r in api('git/matching-refs/tags/' + tag) if r['ref'] == 'refs/tags/' + tag]
    require(len(refs) <= 1, 'Ambiguous tag')
    if refs:
        require(refs[0]['object']['type'] == 'commit' and release is not None,
                'Existing tag is not the owned fixed target')
        require(SHA.fullmatch(refs[0]['object'].get('sha', '')) and
                release['target_commitish'] == refs[0]['object']['sha'], 'Owned early tag moved')
    fixed_target = refs[0]['object']['sha'] if refs else target
    if release and release['draft'] and not refs:
        fixed_target = release.get('target_commitish')
        require(SHA.fullmatch(fixed_target or ''), 'Draft target is not the owned fixed commit')
        git('fetch', '--no-tags', 'origin', fixed_target, 'main')
        git('merge-base', '--is-ancestor', fixed_target, 'origin/main')
    require(not release or release['draft'] or refs, 'Published release tag missing')
    current_develop(develop_head)
    if not release or release['draft'] or release['body'] != body:
        automatic_release_target(fixed_target)
    if not release:
        release = api('releases', 'POST', {'tag_name': tag, 'target_commitish': target,
                      'name': 'Cursor in Android Studio early',
                      'body': body, 'draft': True, 'prerelease': True, 'make_latest': 'false'})
    require(type(release.get('id')) is int and release['id'] > 0, 'Release identity missing')
    assets = pages(f'releases/{release["id"]}/assets?per_page=100')
    require(all(type(a.get('id')) is int and a['id'] > 0 for a in assets), 'Asset identity missing')
    require(len({a['name'] for a in assets}) == len(assets), 'Duplicate release assets')
    existing = next((a for a in assets if a['name'] == name), None)
    if existing:
        verify_asset(existing, record)
    else:
        try:
            uploaded = json.loads(command(['gh', 'api', '--method', 'POST',
                f'https://uploads.github.com/repos/{REPO}/releases/{release["id"]}/assets?name={quote(name)}',
                '-H', 'Content-Type: application/zip', '--input', str(path)]))
        except (OSError, RuntimeError, subprocess.SubprocessError, ValueError):
            uncertain_write(tag)
        verify_asset(uploaded, record)
    current = find_release(tag)
    require(current and all(current[k] == release[k] for k in ('id', 'body', 'draft', 'prerelease', 'target_commitish')),
            'Release changed during preparation; preserve both assets')
    current_develop(develop_head)
    if current['body'] != body or current['draft']:
        automatic_release_target(fixed_target)
        api(f'releases/{release["id"]}', 'PATCH', {'body': body, 'draft': False,
            'make_latest': 'false'})
    final = find_release(tag)
    require(final and final['id'] == release['id'] and not final['draft'] and final['body'] == body
            and final['prerelease'] is True, 'Publication readback differs')
    final_ref = api('git/ref/tags/' + tag)
    require(final_ref['object']['type'] == 'commit' and final_ref['object']['sha'] == fixed_target,
            'Published tag moved')
    final_assets = pages(f'releases/{release["id"]}/assets?per_page=100')
    published = [a for a in final_assets if a['name'] == name]
    require(len(published) == 1, 'Published asset missing or ambiguous')
    verify_asset(published[0], record)
    # Failed earlier updates can leave more than the previous body's ZIP behind.
    old = [a for a in final_assets if a['name'] != name
               and re.fullmatch(r'cursor-in-android-studio-[0-9a-f]{64}\.zip', a['name'])]
    for asset in old:
        old_hash = asset['name'].removeprefix('cursor-in-android-studio-').removesuffix('.zip')
        require(asset.get('state') == 'uploaded'
                and type(asset.get('size')) is int and 0 < asset['size'] <= MAX_ARCHIVE
                and asset.get('digest') == 'sha256:' + old_hash,
                'Old asset identity changed')
    fields = ('id', 'name', 'state', 'size', 'digest', 'updated_at')
    remaining = {a['id']: tuple(a.get(k) for k in fields) for a in final_assets}
    require(len(remaining) == len(final_assets)
            and len({a['name'] for a in final_assets}) == len(final_assets), 'Duplicate release assets')
    for asset in [*old, None]:
        current_develop(develop_head)
        current = find_release(tag)
        require(current and all(current.get(k) == final.get(k) for k in
                ('id', 'body', 'draft', 'prerelease', 'target_commitish', 'immutable')),
                'Release changed during cleanup; preserve remaining assets')
        require(api('git/ref/tags/' + tag)['object'] == final_ref['object'], 'Published tag moved')
        current_assets = pages(f'releases/{release["id"]}/assets?per_page=100')
        require(len(current_assets) == len(remaining)
                and {a['id']: tuple(a.get(k) for k in fields) for a in current_assets} == remaining,
                'Release assets changed during cleanup; preserve remaining assets')
        if asset is None:
            break
        api(f'releases/assets/{asset["id"]}', 'DELETE')
        del remaining[asset['id']]
    return f'https://github.com/{REPO}/releases/tag/{tag}'


def stable_release(number, directory):
    require_enabled()
    require(type(number) is int and number > 0, 'Promotion PR required')
    pr = api(f'pulls/{number}')
    require(pr.get('merged') and pr.get('base', {}).get('ref') == 'main'
            and pr['base']['repo']['full_name'] == REPO
            and (pr.get('head', {}).get('repo') or {}).get('full_name') == REPO,
            'Merged same-repository main promotion required')
    head = pr['head']['sha']
    require(SHA.fullmatch(head or ''), 'Fixed promotion HEAD required')
    git('fetch', '--no-tags', 'origin', head, 'main')
    promotion = json.loads(git('show', f'{head}:{rc.PROMOTION}'))
    expected_hash = promotion.get('artifact_sha256')
    candidate_artifact(expected_hash, directory)
    # The existing validator reconstructs the merge, every Case, hash and required checks.
    rc.publish(directory, expected_hash, number, retained_bundle=True)


def promoted(run_id, source, directory):
    require_enabled()
    run = producer_run(run_id, source)
    require(run.get('head_branch') == 'main' and run.get('event') == 'push', 'Main push CI required')
    matches = [p for p in pages(f'commits/{source}/pulls?per_page=100')
               if p.get('merge_commit_sha') == source and p.get('base', {}).get('ref') == 'main'
               and re.search(r'^Integration: promotion$', p.get('body') or '', re.M)]
    require(len(matches) <= 1, 'Ambiguous main promotion')
    if matches:
        stable_release(matches[0]['number'], directory)
    else:
        print('No promotion in this main commit; no release changed.')


def main():
    parser = argparse.ArgumentParser(__doc__)
    commands = parser.add_subparsers(dest='channel', required=True)
    early = commands.add_parser('early')
    early.add_argument('--run', type=int, required=True)
    early.add_argument('--source', required=True)
    stable = commands.add_parser('stable')
    stable.add_argument('--directory', type=Path)
    stable.add_argument('--sha256')
    stable.add_argument('--promotion-pr', type=int, required=True)
    stable.add_argument('--retained-bundle', action='store_true')
    automatic = commands.add_parser('promoted')
    automatic.add_argument('--run', type=int, required=True)
    automatic.add_argument('--source', required=True)
    args = parser.parse_args()
    require_enabled()  # Before Git/API/download/build or any other external operation.
    require(os.environ.get('GITHUB_REPOSITORY', REPO) == REPO, 'Unexpected delivery repository')
    os.environ.setdefault('GITHUB_REPOSITORY', REPO)
    if args.channel == 'stable':
        if args.directory is not None:
            require(args.sha256 is not None, 'Fixed candidate hash required with a retained directory')
            rc.publish(args.directory, args.sha256, args.promotion_pr, retained_bundle=args.retained_bundle)
        else:
            require(args.sha256 is None and not args.retained_bundle, 'A retained bundle needs a directory and fixed hash')
            with tempfile.TemporaryDirectory() as temporary:
                stable_release(args.promotion_pr, Path(temporary) / 'candidate')
    elif args.channel == 'promoted':
        with tempfile.TemporaryDirectory() as temporary:
            promoted(args.run, args.source, Path(temporary) / 'candidate')
    else:
        with tempfile.TemporaryDirectory() as temporary:
            product = early_product(args.run, args.source, Path(temporary))
            if product is None:
                print('No distribution input changed; no release changed.')
                return
            archive, run, number, head = product
            record = dict(identity(archive, run['head_sha'], digest(archive)), schema=1,
                          repository=REPO, channel='early', pr=number, run=run['id'], develop_head=head)
            target = api('git/ref/heads/main')['object']['sha']
            print(publish(archive, record, target))


if __name__ == '__main__':
    main()
