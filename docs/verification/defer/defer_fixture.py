#!/usr/bin/env python3
"""Build an isolated #313 variant or control one opt-in verification trial."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import uuid
import zipfile

HELPER = Path('src/test/kotlin/com/cursoragent/verification/VerificationDefer.kt')
PATCH = Path('docs/verification/defer/instrumentation.patch')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    temporary = path.with_suffix('.tmp')
    if temporary.is_symlink():
        raise ValueError('Symlink is not a control file')
    temporary.write_text(json.dumps(value, indent=2) + '\n')
    temporary.replace(path)


def prepare(repo, output):
    repo, output = Path(repo).resolve(), Path(output).absolute()
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=repo).strip():
        raise ValueError('Commit the reviewed source before preparing the variant')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip()
    archive = subprocess.check_output(['git', 'archive', head], cwd=repo)
    output.mkdir(mode=0o700, parents=True, exist_ok=False)
    source = output / 'source'
    source.mkdir()
    with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
        tar.extractall(source, filter='data')
    subprocess.run(['git', 'init', '-q'], cwd=source, check=True)
    subprocess.run(['git', 'apply', '--check', str(PATCH)], cwd=source, check=True)
    subprocess.run(['git', 'apply', str(PATCH)], cwd=source, check=True)
    helper = source / HELPER
    target = source / str(HELPER).replace('src/test/', 'src/main/', 1)
    target.parent.mkdir(parents=True, exist_ok=True)
    helper.rename(target)
    product = {str(p.relative_to(source)): digest(p.read_bytes())
               for p in sorted((source / 'src/main').rglob('*')) if p.is_file()}
    manifest = {'head': head, 'normal_source_tree': subprocess.check_output(
        ['git', 'rev-parse', f'{head}:src/main'], cwd=repo, text=True).strip(),
        'patch_sha256': digest((source / PATCH).read_bytes()),
        'helper_sha256': digest(target.read_bytes()), 'instrumented_sources': product,
        'source_digest': digest(json.dumps(product, sort_keys=True).encode()),
        'variant': '0.1.0-verification-313', 'gui_performed': False}
    write_json(output / 'build-identity.json', manifest)
    return manifest


def build(output, platform_path=None):
    output = Path(output).resolve()
    args = ['./gradlew', 'test', 'buildPlugin', '--console=plain']
    if platform_path:
        args.append('-PplatformPath=' + platform_path)
    subprocess.run(args, cwd=output / 'source', check=True)
    plugin = output / 'source/build/distributions/cursor-in-android-studio-0.1.0-verification-313.zip'
    manifest = json.loads((output / 'build-identity.json').read_text())
    with zipfile.ZipFile(plugin) as archive:
        if archive.testzip() is not None:
            raise ValueError('Invalid variant ZIP')
        manifest['jars'] = {name: digest(archive.read(name)) for name in archive.namelist() if name.endswith('.jar')}
    manifest['zip_sha256'] = digest(plugin.read_bytes())
    manifest['zip'] = str(plugin)
    write_json(output / 'build-identity.json', manifest)
    return manifest


def control_root(directory):
    directory = Path(directory).absolute()
    if directory.is_symlink() or not directory.is_dir() or directory.stat().st_mode & 0o077:
        raise ValueError('An owned 0700 control directory is required')
    manifest = directory / 'manifest.json'
    if manifest.is_symlink() or not manifest.is_file() or manifest.stat().st_size > 4096:
        raise ValueError('Invalid control manifest')
    return directory, str(uuid.UUID(json.loads(manifest.read_text())['run']))


def initialize(parent=None):
    directory = Path(tempfile.mkdtemp(prefix='verification-313-', dir=parent))
    write_json(directory / 'manifest.json', {'run': str(uuid.uuid4())})
    return directory


def command(directory, op, **fields):
    directory, run = control_root(directory)
    command_file, state_file = directory / 'command.json', directory / 'state.json'
    if command_file.exists():
        previous = json.loads(command_file.read_text())['id']
        if not state_file.exists() or json.loads(state_file.read_text()).get('command') != previous:
            raise ValueError('Previous command is not acknowledged; inspect state before retrying')
    identity = str(uuid.uuid4())
    write_json(command_file, {'run': run, 'id': identity, 'op': op, **fields})
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        if state_file.exists():
            state = json.loads(state_file.read_text())
            if state.get('command') == identity:
                if state.get('result') == 'rejected':
                    raise ValueError('Command rejected; inspect state and the selected trial')
                return state
        time.sleep(.05)
    raise TimeoutError('No acknowledgement; command remains pending, do not blindly resend')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='operation', required=True)
    p = sub.add_parser('prepare'); p.add_argument('output'); p.add_argument('--repo', default='.')
    p = sub.add_parser('build'); p.add_argument('output'); p.add_argument('--platform-path')
    sub.add_parser('init')
    p = sub.add_parser('arm'); p.add_argument('directory'); p.add_argument('owner')
    p.add_argument('point', choices=['queue', 'preparation', 'popup', 'edt-chunk', 'edt-stop', 'edt-complete', 'edt-update'])
    p.add_argument('--token', default='next')
    p = sub.add_parser('release'); p.add_argument('directory'); p.add_argument('pending')
    p = sub.add_parser('close'); p.add_argument('directory')
    p = sub.add_parser('status'); p.add_argument('directory')
    args = parser.parse_args()
    if args.operation == 'prepare':
        result = prepare(args.repo, args.output)
    elif args.operation == 'build':
        result = build(args.output, args.platform_path)
    elif args.operation == 'init':
        result = {'directory': str(initialize()), 'vm_option': '-Dcursor.verification.directory=<directory>'}
    elif args.operation == 'arm':
        result = command(args.directory, 'arm', owner=args.owner, point=args.point, token=args.token)
    elif args.operation == 'release':
        result = command(args.directory, 'release', pending=args.pending)
    elif args.operation == 'close':
        result = command(args.directory, 'close')
    else:
        root, run = control_root(args.directory)
        result = {'run': run, 'state': json.loads((root / 'state.json').read_text()) if (root / 'state.json').exists() else None,
                  'recent_events': [json.loads(line) for line in (root / 'events.jsonl').read_text().splitlines()[-30:]] if (root / 'events.jsonl').exists() else []}
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
