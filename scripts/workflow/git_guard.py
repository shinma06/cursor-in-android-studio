#!/usr/bin/env python3
"""Local accident prevention, not a substitute for server branch protection."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys

BRANCH = re.compile(r'(codex|claude|cursor)/([1-9][0-9]*)-[a-z0-9][a-z0-9-]*')
REPO = 'shinma06/cursor-in-android-studio'
OID = re.compile(r'[0-9a-f]{40}')


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def check_branch(branch):
    if not BRANCH.fullmatch(branch):
        raise ValueError('Use an Issue branch in its own worktree: codex|claude|cursor/<issue>-<slug>. main/detached commits are forbidden.')


def private_bytes(path, expected=...):
    # Only the initial packet read omits its digest; supplied originals always require SHA-256.
    if expected is not ... and (not isinstance(expected, str) or not re.fullmatch(r'[0-9a-f]{64}', expected)):
        raise ValueError('Retirement original digest must be a SHA-256 hex string')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
            raise ValueError('Retirement originals must be private and locally owned')
        raw = stream.read()
    if expected is not ... and hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError('Retirement original digest changed')
    return raw


def retirement_packet(path, digest, remote, location, branch, head, dirty):
    """A one-operation binding to existing qualified custody, not a deletion allowlist."""
    check_branch(branch)
    packet = json.loads(private_bytes(path, digest))
    if not isinstance(packet, dict):
        raise ValueError('Expected one retirement binding object')
    urls = {f'https://github.com/{REPO}', f'https://github.com/{REPO}.git', f'git@github.com:{REPO}.git'}
    if (type(packet.get('schema')) is not int or packet['schema'] != 1 or packet.get('repository') != REPO or dirty or
            packet.get('operation_branch') != branch or packet.get('operation_head') != head or
            packet.get('git_common_dir') != str(Path(git('rev-parse', '--path-format=absolute', '--git-common-dir')).resolve()) or
            not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', remote) or packet.get('remote') != remote or
            location not in urls or packet.get('remote_url') != location or
            git('remote', 'get-url', '--push', remote) != location or
            packet.get('writer_stopped') is not True or packet.get('unused') is not True or
            not isinstance(packet.get('owner'), str) or not packet['owner'].strip()):
        raise ValueError('Retirement repository, remote, owner or clean operation binding differs')
    issue = BRANCH.fullmatch(branch)[2]
    if not re.fullmatch(r'https://github\.com/' + re.escape(REPO) + r'/issues/' + issue + r'#issuecomment-[1-9][0-9]*',
                        str(packet.get('assignment'))):
        raise ValueError('Use the actual operation Issue assignment')
    ref = packet.get('ref', '')
    name = ref.removeprefix('refs/heads/')
    if (not ref.startswith('refs/heads/') or not name.startswith(('codex/', 'claude/', 'cursor/')) or
            BRANCH.fullmatch(name) or not OID.fullmatch(str(packet.get('expected_oid')))):
        raise ValueError('Only one exact legacy task head is eligible; current Issue cleanup is unchanged')
    subprocess.run(['git', 'check-ref-format', ref], check=True, capture_output=True)
    trust = packet.get('trusted_main')
    if not isinstance(trust, str) or not OID.fullmatch(trust) or git('rev-parse', 'refs/remotes/origin/main') != trust:
        raise ValueError('Retirement requires the fixed adopted main guard')
    root = Path(git('rev-parse', '--show-toplevel'))
    hooks = Path(git('config', '--get', 'core.hooksPath'))
    hooks = hooks if hooks.is_absolute() else root / hooks
    if not os.access(hooks/'pre-push', os.X_OK):
        raise ValueError('The trusted pre-push hook must remain executable')
    for source, actual in [('scripts/workflow/git_guard.py', Path(__file__)), ('.githooks/pre-push', hooks/'pre-push')]:
        if actual.is_symlink() or subprocess.check_output(['git', 'show', f'{trust}:{source}']) != actual.read_bytes():
            raise ValueError('Use the trusted adopted guard and pre-push hook without overrides')
    original = packet['manifest']
    manifest = json.loads(private_bytes(original['path'], original['sha256']))
    if (not isinstance(manifest, dict) or not isinstance(manifest.get('ref_actions'), list) or
            not all(isinstance(r, dict) for r in manifest['ref_actions'])):
        raise ValueError('Invalid original retirement manifest')
    record_ref = 'refs/remotes/' + remote + '/' + name
    records = [r for r in manifest['ref_actions'] if r.get('ref') == record_ref]
    if manifest.get('issue') != int(issue) or len(records) != 1:
        raise ValueError('Original manifest does not select this operation/ref')
    record = records[0]
    restored = record.get('actual_restore_HEAD_tree', {})
    if (record.get('expected_OID') != packet['expected_oid'] or restored.get('HEAD') != packet['expected_oid'] or
            record.get('force_flags') is not False or record.get('custody_owner') != packet['owner'] or
            restored.get('tree') != git('rev-parse', packet['expected_oid'] + '^{tree}')):
        raise ValueError('Original stopped target, custody and restored object identity differ')
    private_bytes(record['private_bundle'], record['bundle_sha256'])
    heads = subprocess.check_output(['git', 'bundle', 'list-heads', record['private_bundle']], text=True).splitlines()
    if not any(line.split()[0] == packet['expected_oid'] for line in heads):
        raise ValueError('Preserved source bundle does not contain the exact target')
    return packet


def check_push(lines, branch, head, dirty, retirement=None):
    if retirement is not None:
        if retirement.get('ref') in ('refs/heads/main', 'refs/heads/master', 'refs/heads/develop'):
            raise ValueError('Direct push/deletion of main/master/develop is forbidden. Use a GitHub PR.')
        if len(lines) != 1 or dirty:
            raise ValueError('Retirement accepts one deletion only from a clean assigned Issue branch')
        expected = ['(delete)', '0'*40, retirement['ref'], retirement['expected_oid']]
        if lines[0].split() != expected:
            raise ValueError('Retirement target or advertised remote OID changed; no mixed/update push')
        check_branch(branch)
        return False
    changes = False
    for line in lines:
        local_ref, local_sha, remote_ref, remote_sha = line.split()
        if remote_ref in ('refs/heads/main', 'refs/heads/master', 'refs/heads/develop'):
            raise ValueError('Direct push/deletion of main/master/develop is forbidden. Use a GitHub PR.')
        if set(local_sha) == {'0'}:
            # Own task branch cleanup only; tags/other namespaces are not this workflow.
            check_branch(branch)
            if remote_ref != 'refs/heads/' + branch:
                raise ValueError('Delete only your current Issue branch, never another task branch.')
            continue
        check_branch(branch)
        if remote_ref != 'refs/heads/' + branch or local_sha != head:
            raise ValueError('Push only the current tested HEAD to its same-name Issue branch.')
        changes = True
    if changes and dirty:
        raise ValueError('Commit your assigned changes before push; dirty files would invalidate the test/HEAD match.')
    return changes


def main():
    try:
        branch = git('branch', '--show-current')
        if sys.argv[1] == 'retire-ref':
            parser = argparse.ArgumentParser(description='Submit one qualified legacy-head deletion through the normal hook')
            parser.add_argument('--packet', type=Path, required=True)
            args = parser.parse_args(sys.argv[2:])
            raw = private_bytes(args.packet)
            packet = json.loads(raw)
            digest = hashlib.sha256(raw).hexdigest()
            retirement_packet(args.packet, digest, packet['remote'], packet['remote_url'], branch,
                              git('rev-parse', 'HEAD'), bool(git('status', '--porcelain')))
            environment = dict(os.environ, CAS_REF_RETIREMENT_PACKET=str(args.packet.resolve()), CAS_REF_RETIREMENT_SHA256=digest)
            subprocess.run(['git', 'push', packet['remote'], ':' + packet['ref']], env=environment, check=True)
            if git('ls-remote', '--heads', packet['remote_url'], packet['ref']):
                raise ValueError('Target present after deletion; preserve result and inspect without retry')
        elif sys.argv[1] == 'commit':
            check_branch(branch)
        elif sys.argv[1] == 'push':
            lines = sys.stdin.read().splitlines()
            head, dirty = git('rev-parse', 'HEAD'), bool(git('status', '--porcelain'))
            retirement = None
            if os.environ.get('CAS_REF_RETIREMENT_PACKET') or os.environ.get('CAS_REF_RETIREMENT_SHA256'):
                if len(sys.argv) != 4:
                    raise ValueError('Retirement requires the actual hook remote identity')
                retirement = retirement_packet(os.environ['CAS_REF_RETIREMENT_PACKET'], os.environ['CAS_REF_RETIREMENT_SHA256'],
                                               sys.argv[2], sys.argv[3], branch, head, dirty)
            changes = check_push(lines, branch, head, dirty, retirement)
            for line in lines:
                _, local_sha, _, remote_sha = line.split()
                if set(local_sha) != {'0'} and set(remote_sha) != {'0'}:
                    if subprocess.run(['git', 'merge-base', '--is-ancestor', remote_sha, local_sha],
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
                        raise ValueError('Non-fast-forward or unknown remote history. Fetch and reconcile; never force push.')
            # Hook uses output to skip tests for branch-deletion-only pushes.
            print('test' if changes else 'skip')
        else:
            raise ValueError('Use commit, push or retire-ref')
        return 0
    except (ValueError, KeyError, TypeError, OSError, subprocess.CalledProcessError) as error:
        print('[git guard] ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
