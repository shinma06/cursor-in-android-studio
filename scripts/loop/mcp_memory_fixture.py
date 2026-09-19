#!/usr/bin/env python3
"""Pin the official QA49 Memory server in a new owned directory; never configure Cursor."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import threading
import uuid

VERSION = '2026.8.31'
INTEGRITY = 'sha512-ljj/3S4aGjxdNSQWw6gucKKGnTLdBPWxzapyY/MT2tOVyZwvxChvevXSLPwx59nKJAlVpUVW+cOlnVRXRwiqMQ=='
PURPOSE = 'qa49-official-memory-fixture'
SOURCE = Path(__file__).resolve().parents[2] / 'docs/verification/fixtures/mcp-memory'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package_files(root):
    files = {}
    package = root / 'package'
    for path in sorted(package.rglob('*')):
        relative = str(path.relative_to(root))
        if path.is_symlink():
            target = path.resolve(strict=True)
            if not target.is_relative_to(package) or not target.is_file():
                raise ValueError('Package link must target a fixed file inside the package')
            files[relative] = {'symlink': os.readlink(path), 'sha256': digest(target)}
        elif path.is_file():
            files[relative] = digest(path)
    return files


def prepare(root, node, npm):
    node, npm = node.resolve(strict=True), npm.resolve(strict=True)
    lock = json.loads((SOURCE / 'package-lock.json').read_text())
    memory = lock['packages']['node_modules/@modelcontextprotocol/server-memory']
    if memory['version'] != VERSION or memory['integrity'] != INTEGRITY:
        raise ValueError('Official distribution pin changed')
    for name, package in lock['packages'].items():
        if name and (not package.get('resolved', '').startswith('https://registry.npmjs.org/') or not package.get('integrity')):
            raise ValueError('Expected locked public-registry packages only')
    # Existing roots, data and symlinks are never reused or overwritten.
    root = root.parent.resolve(strict=True) / root.name
    root.mkdir(mode=0o700)
    for name in ('package', 'data', 'evidence'):
        (root / name).mkdir(mode=0o700)
    marker = {'schema': 1, 'purpose': PURPOSE, 'root': str(root), 'id': 'qa49-memory-' + uuid.uuid4().hex[:12]}
    (root / 'MARKER.json').write_text(json.dumps(marker, indent=2) + '\n')
    for name in ('package.json', 'package-lock.json'):
        shutil.copyfile(SOURCE / name, root / 'package' / name)
    for name in ('user.npmrc', 'global.npmrc'):
        (root / name).write_text('')
    environment = {'PATH': str(node.parent) + os.pathsep + os.defpath}
    command = [str(node), str(npm), '--prefix', str(root / 'package'), '--cache', str(root / 'cache'),
               '--userconfig', str(root / 'user.npmrc'), '--globalconfig', str(root / 'global.npmrc'),
               '--registry=https://registry.npmjs.org']
    with (root / 'evidence/install.txt').open('x') as log:
        subprocess.run(command + ['ci', '--ignore-scripts', '--no-audit', '--no-fund'], cwd=root, env=environment, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=180)
    entry = root / 'package/node_modules/@modelcontextprotocol/server-memory/dist/index.js'
    if entry.is_symlink() or not entry.is_file():
        raise ValueError('Official entry point is unavailable')
    server = {'type': 'stdio', 'command': str(node), 'args': [str(entry)],
              'env': {'MEMORY_FILE_PATH': str(root / 'data/memory.jsonl')}}
    (root / 'project-mcp.template.json').write_text(json.dumps({'mcpServers': {marker['id']: server}}, indent=2) + '\n')
    manifest = {**marker, 'package_version': VERSION, 'integrity': INTEGRITY,
                'node': str(node), 'node_sha256': digest(node),
                'node_version': subprocess.check_output([str(node), '--version'], env={}, text=True).strip(),
                'npm': str(npm), 'npm_version': subprocess.check_output(command + ['--version'], cwd=root, env=environment, text=True).strip(),
                'files': package_files(root), 'template_sha256': digest(root / 'project-mcp.template.json')}
    (root / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    return root


def verify(root):
    if root.is_symlink():
        raise ValueError('Owned root must not be a symlink')
    root = root.resolve(strict=True)
    for name in ('MARKER.json', 'manifest.json', 'package', 'data', 'evidence', 'project-mcp.template.json'):
        if (root / name).is_symlink():
            raise ValueError('Owned paths must not be symlinks')
    marker = json.loads((root / 'MARKER.json').read_text())
    manifest = json.loads((root / 'manifest.json').read_text())
    if marker != {key: manifest[key] for key in ('schema', 'purpose', 'root', 'id')} or marker['purpose'] != PURPOSE or marker['root'] != str(root):
        raise ValueError('Owned root marker mismatch')
    node = Path(manifest['node'])
    if node.is_symlink() or digest(node) != manifest['node_sha256']:
        raise ValueError('Pinned Node changed')
    if any((root / 'data').iterdir()):
        raise ValueError('Only a fresh empty data directory can be checked')
    if package_files(root) != manifest['files'] or digest(root / 'project-mcp.template.json') != manifest['template_sha256']:
        raise ValueError('Pinned package/template changed')
    entry = root / 'package/node_modules/@modelcontextprotocol/server-memory/dist/index.js'
    if entry.is_symlink():
        raise ValueError('Entry must not be a symlink')
    messages = []
    received = queue.Queue()
    with (root / 'evidence/server-stderr.txt').open('x') as error:
        process = subprocess.Popen([str(node), str(entry)], cwd=root, env={'MEMORY_FILE_PATH': str(root / 'data/memory.jsonl')},
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=error, text=True, bufsize=1)
        def read():
            for line in process.stdout:
                received.put(line)
            received.put(None)
        threading.Thread(target=read, daemon=True).start()
        def send(value):
            messages.append({'direction': 'in', 'payload': value})
            process.stdin.write(json.dumps(value) + '\n')
            process.stdin.flush()
        def response(identifier):
            line = received.get(timeout=10)
            if line is None:
                raise ValueError('Server exited before a response')
            value = json.loads(line)
            messages.append({'direction': 'out', 'payload': value})
            if value.get('id') != identifier or 'error' in value:
                raise ValueError('Unexpected response')
            return value['result']
        try:
            send({'jsonrpc': '2.0', 'id': 1, 'method': 'initialize', 'params': {
                'protocolVersion': '2025-06-18', 'capabilities': {},
                'clientInfo': {'name': 'qa49-owned-check', 'version': '1'}}})
            initialized = response(1)
            if initialized['serverInfo'] != {'name': 'memory-server', 'version': '0.6.3'} or initialized['protocolVersion'] != '2025-06-18':
                raise ValueError('Unexpected fixed-server identity/protocol')
            send({'jsonrpc': '2.0', 'method': 'notifications/initialized'})
            send({'jsonrpc': '2.0', 'id': 2, 'method': 'tools/call', 'params': {'name': 'read_graph', 'arguments': {}}})
            result = response(2)
            if result.get('isError') or len(result['content']) != 1 or result['content'][0]['type'] != 'text' or json.loads(result['content'][0]['text']) != {'entities': [], 'relations': []}:
                raise ValueError('Expected an empty graph')
            process.stdin.close()
            if process.wait(timeout=10) != 0:
                raise ValueError('Server did not exit successfully on stdin EOF')
            if received.get(timeout=5) is not None:
                raise ValueError('Unexpected trailing stdout')
            if any((root / 'data').iterdir()):
                raise ValueError('Read-only check unexpectedly wrote data')
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
            process.stdin.close()
            process.stdout.close()
    report = {'package_version': VERSION, 'server_info': initialized['serverInfo'], 'node_version': manifest['node_version'],
              'pid': process.pid, 'exit_code': process.returncode, 'stdin_eof_exit': True, 'empty_graph': True,
              'data_unchanged': True, 'messages': messages}
    (root / 'evidence/check.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    prep = sub.add_parser('prepare')
    prep.add_argument('--root', required=True, type=Path)
    prep.add_argument('--node', required=True, type=Path)
    prep.add_argument('--npm', required=True, type=Path)
    check = sub.add_parser('check')
    check.add_argument('--root', required=True, type=Path)
    args = parser.parse_args()
    try:
        if args.action == 'prepare':
            print(prepare(args.root, args.node, args.npm))
        else:
            report = verify(args.root)
            print(json.dumps({key: value for key, value in report.items() if key != 'messages'}))
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError, queue.Empty) as error:
        print('Memory fixture preparation/check failed: ' + type(error).__name__, file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
