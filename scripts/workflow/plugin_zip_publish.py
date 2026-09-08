#!/usr/bin/env python3
"""Run only from trusted main. Inventory is independent of branch workflows."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

from plugin_zip import NAME, REPOSITORY, SHA, git, validate

SOURCE_PREFIX = 'plugin-source-'


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True).strip()


def retain_event(event_path):
    """Retain push roots before applying a build limit or executing source code."""
    event = json.loads(event_path.read_text())
    if event.get('repository', {}).get('full_name') != REPOSITORY:
        raise ValueError('unexpected event repository')
    signal = event.get('workflow_run')
    if not signal:
        return
    run_id = signal.get('id')
    if type(run_id) is not int or run_id <= 0:
        raise ValueError('invalid workflow run id')
    run = json.loads(gh('api', f'repos/{REPOSITORY}/actions/runs/{run_id}'))
    # PR runs (including forks) are not a source of trusted repository roots.
    if run.get('event') != 'push' or run.get('head_repository', {}).get('full_name') != REPOSITORY:
        return
    sha = run.get('head_sha', '')
    if not SHA.fullmatch(sha) or sha != signal.get('head_sha'):
        raise ValueError('invalid or mismatched event SHA')
    retain_source(sha)


def source_api(path, method='GET', data=None):
    args = ['api', path] if method == 'GET' else ['api', '--method', method, path]
    for key, value in (data or {}).items():
        args.extend(['-f', f'{key}={value}'])
    return json.loads(gh(*args))


def retain_source(sha, api=None):
    """Create-only durable root; a failed create is safe only after exact readback."""
    if not SHA.fullmatch(sha):
        raise ValueError('invalid source SHA')
    api = source_api if api is None else api
    ref = f'tags/{SOURCE_PREFIX}{sha}'
    try:
        api(f'repos/{REPOSITORY}/git/refs', 'POST', {'ref': f'refs/{ref}', 'sha': sha})
    except (subprocess.CalledProcessError, RuntimeError):
        # An existing tag or concurrent creator may make POST fail. Never update it.
        pass
    saved = api(f'repos/{REPOSITORY}/git/ref/{ref}')
    obj = saved.get('object', {})
    if saved.get('ref') != f'refs/{ref}' or obj.get('type') != 'commit' or obj.get('sha') != sha:
        raise ValueError('source root readback differs')


def ensure_tip(sha):
    if not SHA.fullmatch(sha):
        raise ValueError('invalid remote tip')
    for attempt in range(3):
        present = subprocess.run(['git', 'cat-file', '-e', sha + '^{commit}'], capture_output=True)
        if present.returncode == 0:
            return
        if attempt == 2:
            break
        try:
            subprocess.run(['git', 'fetch', '--no-tags', '--no-write-fetch-head', 'origin', sha],
                           capture_output=True, timeout=30, check=False)
        except subprocess.TimeoutExpired:
            pass
    raise ValueError('snapshot tip unavailable after bounded fetch; retry inventory')


def inventory(limit):
    releases = json.loads(gh('api', '--paginate', '--slurp', f'repos/{REPOSITORY}/releases?per_page=100'))
    published = {r['tag_name'] for page in releases for r in page if not r['draft'] and
                 {'manifest.json', NAME + '-' + r['tag_name'].removeprefix('plugin-build-') + '.zip'}
                 <= {a['name'] for a in r['assets']}}
    # Durable event roots keep unbuilt ancestors reachable after branch deletion.
    refs = git('ls-remote', 'origin', 'refs/heads/*', f'refs/tags/{SOURCE_PREFIX}*')
    tips = []
    for line in refs.splitlines():
        sha, ref = line.split()
        if ref.startswith(f'refs/tags/{SOURCE_PREFIX}') and ref != f'refs/tags/{SOURCE_PREFIX}{sha}':
            raise ValueError('invalid persistent source root')
        tips.append(sha)
    tips = list(dict.fromkeys(tips))
    for sha in tips:
        ensure_tip(sha)
    commits = list(dict.fromkeys(tips + (git('rev-list', '--reverse', *tips).splitlines() if tips else [])))
    missing = [sha for sha in commits if f'plugin-build-{sha}' not in published]
    return missing[:limit]


def release_api(path, method='GET', data=None):
    args = ['gh', 'api', '--method', method, path]
    if data is not None:
        args += ['--input', '-']
    return json.loads(subprocess.check_output(args, text=True,
                                             input=json.dumps(data) if data is not None else None))


def api_id(value):
    if type(value) is not int or value <= 0:
        raise ValueError('invalid Release or asset ID')
    return value


def releases_for_tag(tag):
    # The tag endpoint only finds published releases. List every page to find drafts.
    pages = json.loads(gh('api', '--paginate', '--slurp', f'repos/{REPOSITORY}/releases?per_page=100'))
    return sorted((r for page in pages for r in page if r['tag_name'] == tag),
                  key=lambda r: api_id(r['id']))


def read_release(release_id, tag):
    release_id = api_id(release_id)
    release = release_api(f'repos/{REPOSITORY}/releases/{release_id}')
    if release.get('id') != release_id or release.get('tag_name') != tag or type(release.get('draft')) is not bool:
        raise ValueError('Release ID readback differs')
    return release


def required_assets(release_id, sha):
    pages = json.loads(gh('api', '--paginate', '--slurp',
                         f'repos/{REPOSITORY}/releases/{api_id(release_id)}/assets?per_page=100'))
    required = {'manifest.json', f'{NAME}-{sha}.zip'}
    assets = {}
    for page in pages:
        for asset in page:
            name = asset['name']
            if name not in required:
                continue
            if name in assets or asset.get('state') != 'uploaded':
                raise ValueError('duplicate or incomplete asset; preserve it for operator inspection')
            api_id(asset['id'])
            assets[name] = asset
    return assets


def download_asset(asset, destination):
    # Never ask gh release to resolve a tag: several drafts may share it.
    data = subprocess.check_output(['gh', 'api',
        f'repos/{REPOSITORY}/releases/assets/{api_id(asset["id"])}',
        '-H', 'Accept: application/octet-stream'])
    destination.write_bytes(data)


def verify_release(release, sha, tree, destination):
    assets = required_assets(release['id'], sha)
    if len(assets) != 2:
        raise ValueError('published or complete Release is missing required assets')
    for name, asset in assets.items():
        download_asset(asset, destination / name)
    return validate(destination, sha, tree)[0]


def complete_draft(folder, sha, tree, tag, release):
    release_id = api_id(release['id'])
    current = read_release(release_id, tag)
    if not current['draft']:
        with tempfile.TemporaryDirectory() as tmp:
            return verify_release(current, sha, tree, Path(tmp))
    required = {'manifest.json', f'{NAME}-{sha}.zip'}
    assets = required_assets(release_id, sha)
    with tempfile.TemporaryDirectory() as tmp:
        staging = Path(tmp)
        for name, asset in assets.items():
            download_asset(asset, staging / name)
            if (staging / name).read_bytes() != (folder / name).read_bytes():
                raise ValueError('draft asset differs; investigate without overwriting')
        for name in sorted(required - assets.keys()):
            path = folder / name
            # ID-addressed upload. The file name is generated locally, never from API data.
            endpoint = f'https://uploads.github.com/repos/{REPOSITORY}/releases/{release_id}/assets?name={name}'
            try:
                subprocess.check_output(['gh', 'api', '--method', 'POST', endpoint,
                    '-H', 'Content-Type: application/octet-stream', '--input', str(path)])
            except subprocess.CalledProcessError:
                # A concurrent matching upload or a lost success response is safe only after readback.
                if name not in required_assets(release_id, sha):
                    raise
            saved = required_assets(release_id, sha)
            if name not in saved:
                raise ValueError('uploaded asset missing from Release ID readback')
            download_asset(saved[name], staging / name)
            if (staging / name).read_bytes() != path.read_bytes():
                raise ValueError('uploaded asset readback differs')
        validate(staging, sha, tree)
    release_api(f'repos/{REPOSITORY}/releases/{release_id}', 'PATCH',
                {'draft': False, 'prerelease': True, 'make_latest': 'false'})
    published = read_release(release_id, tag)
    if published['draft']:
        raise ValueError('Release publication readback is still draft')
    with tempfile.TemporaryDirectory() as tmp:
        return verify_release(published, sha, tree, Path(tmp))


def publish(folder, sha):
    if not SHA.fullmatch(sha):
        raise ValueError('full SHA required')
    tree = git('rev-parse', f'{sha}^{{tree}}')
    manifest, _ = validate(folder, sha, tree)
    tag = f'plugin-build-{sha}'
    matches = releases_for_tag(tag)
    published = [r for r in matches if not r['draft']]
    if len(published) > 1:
        raise ValueError('multiple published Releases; operator inspection required')
    if published:
        current = read_release(published[0]['id'], tag)
        if current['draft']:
            raise ValueError('published Release changed to draft; retry discovery')
        with tempfile.TemporaryDirectory() as tmp:
            canonical = verify_release(current, sha, tree, Path(tmp))
        # A rebuild can have a different platform, recipe or archive hash. Preserve the verified first publication.
        print(f'Reusing published Release ID {current["id"]} for {sha}; canonical ZIP {canonical["sha256"]}')
        return canonical
    if not matches:
        created = release_api(f'repos/{REPOSITORY}/releases', 'POST',
            {'tag_name': tag, 'target_commitish': sha, 'draft': True, 'prerelease': True,
             'make_latest': 'false', 'name': f'Plugin build {sha}',
             'body': f'Source commit: {sha}\nRecipe: {manifest["recipe"]}'})
        # Record and use the returned numeric identity, including when another publisher races.
        matches = [read_release(created['id'], tag)]
    # Inspect drafts independently: a broken duplicate must not hide a valid complete draft.
    partial, invalid = [], []
    for release in matches:
        current = read_release(release['id'], tag)
        try:
            assets = required_assets(current['id'], sha)
        except ValueError:
            invalid.append(current['id'])
            continue
        if len(assets) == 2:
            with tempfile.TemporaryDirectory() as tmp:
                staging = Path(tmp)
                try:
                    verify_release(current, sha, tree, staging)
                except (ValueError, zipfile.BadZipFile):
                    invalid.append(current['id'])
                    continue
                # Once a valid ID is selected, any upload/publication failure must stop this attempt.
                return complete_draft(staging, sha, tree, tag, current)
        partial.append((len(assets), api_id(current['id']), current))
    if invalid:
        raise ValueError(f'no valid complete draft; inspect incompatible Release IDs {invalid}')
    # No complete envelope exists: resume the fullest compatible partial draft, never overwrite.
    _, _, selected = min(partial, key=lambda entry: (-entry[0], entry[1]))
    return complete_draft(folder, sha, tree, tag, selected)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('command', choices=['inventory', 'publish', 'retain-event'])
    parser.add_argument('--event-path', type=Path)
    parser.add_argument('--limit', type=int, default=10)
    parser.add_argument('--sha')
    parser.add_argument('--directory', type=Path)
    args = parser.parse_args()
    if args.command == 'inventory':
        print(json.dumps(inventory(args.limit)))
    elif args.command == 'retain-event':
        retain_event(args.event_path)
    else:
        publish(args.directory, args.sha)
