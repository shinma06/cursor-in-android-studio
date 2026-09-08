#!/usr/bin/env python3
"""Run only from trusted main. Inventory is independent of branch workflows."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile

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


def complete_draft(folder, sha, tree, tag, release):
    required = {'manifest.json', f'{NAME}-{sha}.zip'}
    existing = {asset['name'] for asset in release['assets']}
    # A retry can only preserve matching data and add missing expected assets.
    # Never clobber any existing asset, including partially uploaded drafts.
    with tempfile.TemporaryDirectory() as tmp:
        staging = Path(tmp)
        for name in sorted(required & existing):
            gh('release', 'download', tag, '--repo', REPOSITORY, '--dir', tmp, '--pattern', name)
            if (staging / name).read_bytes() != (folder / name).read_bytes():
                raise ValueError('draft asset differs; investigate without overwriting')
        for name in sorted(required - existing):
            gh('release', 'upload', tag, str(folder / name), '--repo', REPOSITORY)
            gh('release', 'download', tag, '--repo', REPOSITORY, '--dir', tmp, '--pattern', name)
            if (staging / name).read_bytes() != (folder / name).read_bytes():
                raise ValueError('uploaded asset readback differs')
        validate(staging, sha, tree)
    gh('release', 'edit', tag, '--repo', REPOSITORY, '--draft=false', '--prerelease', '--latest=false')


def publish(folder, sha):
    if not SHA.fullmatch(sha):
        raise ValueError('full SHA required')
    tree = git('rev-parse', f'{sha}^{{tree}}')
    manifest, archive = validate(folder, sha, tree)
    tag = f'plugin-build-{sha}'
    found = subprocess.run(['gh', 'api', f'repos/{REPOSITORY}/releases/tags/{tag}'], capture_output=True, text=True)
    if found.returncode == 0:
        release = json.loads(found.stdout)
        if release['draft']:
            complete_draft(folder, sha, tree, tag, release)
            return
        with tempfile.TemporaryDirectory() as tmp:
            gh('release', 'download', tag, '--repo', REPOSITORY, '--dir', tmp)
            existing, _ = validate(Path(tmp), sha, tree)
            if existing != manifest:
                raise ValueError('immutable SHA publication differs; investigate, never overwrite')
        return
    # Do not treat authentication/server failures as evidence of absence.
    if '404' not in found.stderr:
        raise ValueError('release lookup failed')
    gh('release', 'create', tag, '--repo', REPOSITORY, '--target', sha, '--draft', '--prerelease',
       '--latest=false', '--title', f'Plugin build {sha}', '--notes', f'Source commit: {sha}\nRecipe: {manifest["recipe"]}')
    complete_draft(folder, sha, tree, tag, {'assets': []})


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
