#!/usr/bin/env python3
"""Run only from trusted main. Inventory is independent of branch workflows."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile

from plugin_zip import NAME, REPOSITORY, SHA, git, validate


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True).strip()


def inventory(limit):
    releases = json.loads(gh('api', '--paginate', '--slurp', f'repos/{REPOSITORY}/releases?per_page=100'))
    published = {r['tag_name'] for page in releases for r in page if not r['draft'] and
                 {'manifest.json', NAME + '-' + r['tag_name'].removeprefix('plugin-build-') + '.zip'}
                 <= {a['name'] for a in r['assets']}}
    # --remotes includes every reachable intermediate commit, not only push tips.
    tips = [line.split()[0] for line in git('ls-remote', '--heads', 'origin').splitlines()]
    commits = list(dict.fromkeys(tips + (git('rev-list', '--reverse', *tips).splitlines() if tips else [])))
    missing = [sha for sha in commits if f'plugin-build-{sha}' not in published]
    return missing[:limit]


def publish(folder, sha):
    if not SHA.fullmatch(sha):
        raise ValueError('full SHA required')
    tree = git('rev-parse', f'{sha}^{{tree}}')
    manifest, archive = validate(folder, sha, tree)
    tag = f'plugin-build-{sha}'
    found = subprocess.run(['gh', 'api', f'repos/{REPOSITORY}/releases/tags/{tag}'], capture_output=True, text=True)
    if found.returncode == 0:
        release = json.loads(found.stdout)
        with tempfile.TemporaryDirectory() as tmp:
            gh('release', 'download', tag, '--repo', REPOSITORY, '--dir', tmp)
            existing, _ = validate(Path(tmp), sha, tree)
            if existing != manifest:
                raise ValueError('immutable SHA publication differs; investigate, never overwrite')
        if release['draft']:
            gh('release', 'edit', tag, '--repo', REPOSITORY, '--draft=false', '--prerelease', '--latest=false')
        return
    # Do not treat authentication/server failures as evidence of absence.
    if '404' not in found.stderr:
        raise ValueError('release lookup failed')
    gh('release', 'create', tag, '--repo', REPOSITORY, '--target', sha, '--draft', '--prerelease',
       '--latest=false', '--title', f'Plugin build {sha}', '--notes', f'Source commit: {sha}\nRecipe: {manifest["recipe"]}')
    gh('release', 'upload', tag, str(archive), str(folder / 'manifest.json'), '--repo', REPOSITORY)
    gh('release', 'edit', tag, '--repo', REPOSITORY, '--draft=false', '--prerelease', '--latest=false')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('command', choices=['inventory', 'publish'])
    parser.add_argument('--limit', type=int, default=10)
    parser.add_argument('--sha')
    parser.add_argument('--directory', type=Path)
    args = parser.parse_args()
    if args.command == 'inventory':
        print(json.dumps(inventory(args.limit)))
    else:
        publish(args.directory, args.sha)
