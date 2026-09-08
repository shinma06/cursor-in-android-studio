#!/usr/bin/env python3
"""Trusted, standard-library-only ZIP delivery runtime. Never invokes Gradle."""
import argparse
import contextlib
import fcntl
import hashlib
import io
import xml.etree.ElementTree as ET
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

REPOSITORY = 'shinma06/cursor-in-android-studio'
SHA = re.compile(r'^[0-9a-f]{40}$')
NAME = 'cursor-in-android-studio'


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


@contextlib.contextmanager
def lock(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('a') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX)
        yield


def validate(folder, sha, tree):
    manifest = json.loads((folder / 'manifest.json').read_text())
    if not SHA.fullmatch(sha) or manifest.get('schema') != 1 or manifest.get('repository') != REPOSITORY:
        raise ValueError('invalid identity')
    if manifest.get('commit') != sha or manifest.get('tree') != tree:
        raise ValueError('source mismatch')
    archive = folder / f'{NAME}-{sha}.zip'
    if hashlib.sha256(archive.read_bytes()).hexdigest() != manifest.get('sha256'):
        raise ValueError('digest mismatch')
    with zipfile.ZipFile(archive) as z:
        if sum(i.file_size for i in z.infolist()) > 300 * 1024 * 1024:
            raise ValueError('expanded archive too large')
        if z.testzip() or not any(n.endswith('.jar') and '/lib/' in n for n in z.namelist()):
            raise ValueError('invalid plugin archive')
        if any(n.startswith('/') or '..' in Path(n).parts for n in z.namelist()):
            raise ValueError('unsafe archive path')
        descriptors = []
        for name in z.namelist():
            if name.endswith('.jar') and '/lib/' in name:
                with zipfile.ZipFile(io.BytesIO(z.read(name))) as jar:
                    if 'META-INF/plugin.xml' in jar.namelist():
                        if jar.getinfo('META-INF/plugin.xml').file_size > 100000:
                            raise ValueError('descriptor too large')
                        descriptors.append(ET.fromstring(jar.read('META-INF/plugin.xml')))
        if len(descriptors) != 1 or descriptors[0].findtext('id') != 'com.cursoragent.plugin':
            raise ValueError('plugin identity mismatch')
    return manifest, archive


def download(url, destination):
    # No credentials; only this repository's public release endpoint is used.
    with urllib.request.urlopen(url, timeout=15) as response:
        data = response.read(100 * 1024 * 1024 + 1)
    if len(data) > 100 * 1024 * 1024:
        raise ValueError('asset too large')
    atomic(destination, data)


def sync(offline=False):
    root = Path(git('rev-parse', '--show-toplevel'))
    common = Path(git('rev-parse', '--path-format=absolute', '--git-common-dir'))
    workgit = Path(git('rev-parse', '--absolute-git-dir'))
    output = root / 'build/distributions'
    with lock(workgit / 'plugin-zip-output.lock'):
        sha = git('rev-parse', 'HEAD')
        tree = git('rev-parse', 'HEAD^{tree}')
        output.mkdir(parents=True, exist_ok=True)
        # All plugin candidates are invalidated before any potentially failing I/O.
        for old in output.glob(f'{NAME}-*.zip'):
            old.unlink()
        status = {'schema': 1, 'commit': sha, 'status': 'pending', 'recovery': 'Run the installed plugin_zip.py sync again after publication; checkout never builds.'}
        atomic(output / 'manifest.json', json.dumps(status).encode())
        try:
            if git('status', '--porcelain', '--untracked-files=normal'):
                status['status'] = 'dirty'
                raise ValueError('working tree differs from HEAD')
            cache = common / 'plugin-zip/cache' / sha
            with lock(common / 'plugin-zip/locks' / sha):
                try:
                    manifest, archive = validate(cache, sha, tree)
                except (OSError, ValueError, zipfile.BadZipFile):
                    if offline:
                        raise ValueError('offline cache unavailable')
                    cache.parent.mkdir(parents=True, exist_ok=True)
                    with tempfile.TemporaryDirectory(dir=cache.parent) as tmp:
                        staging = Path(tmp)
                        base = f'https://github.com/{REPOSITORY}/releases/download/plugin-build-{sha}'
                        download(base + '/manifest.json', staging / 'manifest.json')
                        download(base + f'/{NAME}-{sha}.zip', staging / f'{NAME}-{sha}.zip')
                        validate(staging, sha, tree)
                        cache.mkdir(exist_ok=True)
                        for item in staging.iterdir():
                            atomic(cache / item.name, item.read_bytes())
                    manifest, archive = validate(cache, sha, tree)
                if git('rev-parse', 'HEAD') != sha or git('status', '--porcelain', '--untracked-files=normal'):
                    status['status'] = 'dirty'
                    raise ValueError('source changed during retrieval')
                atomic(output / archive.name, archive.read_bytes())
                manifest = dict(manifest, status='ready')
                atomic(output / 'manifest.json', json.dumps(manifest, indent=2).encode())
            print(f'Plugin ZIP ready: {sha}')
            return 0
        except Exception:
            # Network exceptions may contain local paths; publish only bounded states.
            if status['status'] != 'dirty':
                status['status'] = 'unavailable'
            atomic(output / 'manifest.json', json.dumps(status, indent=2).encode())
            print(f"Plugin ZIP {status['status']}: {sha}; retry sync after build publication.", file=sys.stderr)
            return 1


def record(archive, destination):
    sha, tree = git('rev-parse', 'HEAD'), git('rev-parse', 'HEAD^{tree}')
    if git('status', '--porcelain', '--untracked-files=normal'):
        raise ValueError('record requires a clean source')
    destination.mkdir(parents=True, exist_ok=True)
    data = archive.read_bytes()
    atomic(destination / f'{NAME}-{sha}.zip', data)
    atomic(destination / 'manifest.json', json.dumps({'schema': 1, 'repository': REPOSITORY,
        'commit': sha, 'tree': tree, 'sha256': hashlib.sha256(data).hexdigest(),
        'recipe': 'gradle-test-buildPlugin-v1'}, indent=2).encode())
    validate(destination, sha, tree)


def install():
    root = Path(git('rev-parse', '--show-toplevel'))
    common = Path(git('rev-parse', '--path-format=absolute', '--git-common-dir'))
    # Git documents migration of these settings before enabling worktreeConfig.
    for key in ('core.worktree', 'core.bare', 'core.sparseCheckout', 'core.sparseCheckoutCone'):
        result = subprocess.run(['git', 'config', '--local', '--get', key], capture_output=True, text=True)
        if result.returncode == 0 and result.stdout.strip() not in ('false', ''):
            raise ValueError(f'migrate {key} to worktree config before setup')
    runtime = common / 'plugin-zip/runtime'
    hooks = runtime / 'hooks'
    with lock(common / 'plugin-zip/setup.lock'):
        hooks.mkdir(parents=True, exist_ok=True)
        previous = subprocess.run(['git', 'config', '--get', 'core.hooksPath'], capture_output=True, text=True).stdout.strip()
        previous_path = Path(previous) if previous else common / 'hooks'
        if not previous_path.is_absolute():
            previous_path = root / previous_path
        config = Path(git('rev-parse', '--absolute-git-dir')) / 'plugin-zip-hooks.json'
        if previous_path.resolve() != hooks.resolve():
            atomic(config, json.dumps({'previous': str(previous_path.resolve())}).encode())
        atomic(runtime / 'plugin_zip.py', Path(__file__).read_bytes())
        names = {'applypatch-msg', 'pre-applypatch', 'post-applypatch', 'pre-commit',
                 'pre-merge-commit', 'prepare-commit-msg', 'commit-msg', 'post-commit',
                 'pre-rebase', 'post-checkout', 'post-merge', 'pre-push', 'pre-receive',
                 'update', 'post-receive', 'post-update', 'push-to-checkout', 'pre-auto-gc',
                 'post-rewrite', 'sendemail-validate', 'fsmonitor-watchman',
                 'reference-transaction', 'proc-receive', 'post-index-change'}
        required = {'pre-commit', 'pre-push', 'post-checkout', 'post-merge', 'post-rewrite'}
        for hook in sorted(names):
            if hook not in required and not (previous_path / hook).exists():
                continue
            # Paths are quoted by shlex, never interpolated as shell code.
            import shlex
            body = '#!/bin/sh\nexec python3 ' + shlex.quote(str(runtime / 'plugin_zip.py')) + ' hook ' + hook + ' "$@"\n'
            atomic(hooks / hook, body.encode())
            (hooks / hook).chmod(0o755)
        subprocess.run(['git', 'config', '--local', 'extensions.worktreeConfig', 'true'], check=True)
        subprocess.run(['git', 'config', '--worktree', 'core.hooksPath', str(hooks)], check=True)
    print('Persistent ZIP hooks installed for this worktree; run sync to retrieve HEAD.')


def hook(name, args):
    config = Path(git('rev-parse', '--absolute-git-dir')) / 'plugin-zip-hooks.json'
    if not config.exists():
        if name in ('post-checkout', 'post-merge', 'post-rewrite'):
            sync()
            return 0
        return 1 if name in ('pre-commit', 'pre-push') else 0
    previous = Path(json.loads(config.read_text())['previous']) / name
    result = 0
    if previous.is_file() and os.access(previous, os.X_OK):
        result = subprocess.run([str(previous), *args]).returncode
    elif name in ('pre-commit', 'pre-push'):
        # Historical trees without protection must not silently allow writes.
        print('Protected write hook unavailable; return to a supported source before writing.', file=sys.stderr)
        return 1
    if name in ('post-checkout', 'post-merge', 'post-rewrite'):
        sync()
    return result


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('command', choices=['sync', 'setup', 'record', 'cache-built', 'hook'])
    parser.add_argument('args', nargs='*')
    parser.add_argument('--offline', action='store_true')
    opts = parser.parse_args()
    if opts.command == 'sync':
        return sync(opts.offline)
    if opts.command == 'setup':
        install()
    elif opts.command == 'cache-built':
        for delivered in Path('build/distributions').glob(f'{NAME}-*.zip'):
            if SHA.fullmatch(delivered.stem.removeprefix(NAME + '-')):
                delivered.unlink()
        archives = list(Path('build/distributions').glob('*.zip'))
        if len(archives) != 1:
            raise ValueError('expected exactly one built archive')
        common = Path(git('rev-parse', '--path-format=absolute', '--git-common-dir'))
        sha = git('rev-parse', 'HEAD')
        with lock(common / 'plugin-zip/locks' / sha):
            record(archives[0], common / 'plugin-zip/cache' / sha)
    elif opts.command == 'record':
        record(Path(opts.args[0]), Path(opts.args[1]))
    else:
        return hook(opts.args[0], opts.args[1:])
    return 0


if __name__ == '__main__':
    sys.exit(main())
