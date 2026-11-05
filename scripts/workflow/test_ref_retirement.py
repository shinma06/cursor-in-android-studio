"""Real pre-push and receive-pack reject stale one-ref deletion without force flags."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

import git_guard as guard

SOURCE = Path(__file__).resolve().parents[2]
REF = 'refs/heads/cursor/old-fixture'
URL = 'git@github.com:shinma06/cursor-in-android-studio.git'


class RefRetirementTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name); self.root = self.folder/'client'; self.root.mkdir()
        self.remote = self.folder/'remote.git'
        self.env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_') and not k.startswith('CAS_REF_RETIREMENT_')}
        self.env.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM='1')
        self.command('git', 'init', '-q', '-b', 'codex/31-retire')
        self.command('git', 'config', 'user.name', 'Fixture')
        self.command('git', 'config', 'user.email', 'fixture@example.invalid')
        for name in ['scripts/workflow/git_guard.py', '.githooks/pre-push']:
            target = self.root/name; target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(SOURCE/name, target)
        self.command('git', 'add', '.'); self.command('git', 'commit', '-qm', 'adopted fixture guard')
        self.head = self.command('git', 'rev-parse', 'HEAD').stdout.strip()
        self.tree = self.command('git', 'rev-parse', 'HEAD^{tree}').stdout.strip()
        self.command('git', 'clone', '--bare', str(self.root), str(self.remote))
        self.server('update-ref', 'refs/heads/main', self.head)
        self.server('update-ref', REF, self.head)
        self.server('config', 'user.name', 'Fixture')
        self.server('config', 'user.email', 'fixture@example.invalid')
        # Keep the actual Git/SSH URL and hook arguments; only its transport is isolated.
        bridge = self.folder/'ssh-bridge.py'
        bridge.write_text('#!' + sys.executable + '\nimport os,sys,shlex\n'
                          'service=shlex.split(sys.argv[-1])[0]\n'
                          'assert service in ("git-upload-pack","git-receive-pack")\n'
                          f'os.execvp("git",["git",service.removeprefix("git-"),{str(self.remote)!r}])\n')
        bridge.chmod(0o700)
        self.env.update(GIT_SSH_COMMAND=str(bridge), GIT_SSH_VARIANT='ssh')
        self.command('git', 'remote', 'add', 'origin', URL)
        self.command('git', 'fetch', '-q', 'origin', 'refs/heads/main:refs/remotes/origin/main')
        self.command('git', 'config', 'core.hooksPath', '.githooks')
        self.bundle = self.folder/'source.bundle'
        self.command('git', 'bundle', 'create', str(self.bundle), 'HEAD'); self.bundle.chmod(0o600)
        self.manifest = self.folder/'manifest.json'
        self.write(self.manifest, {'issue': 31, 'ref_actions': [{
            'ref': 'refs/remotes/origin/cursor/old-fixture', 'expected_OID': self.head,
            'private_bundle': str(self.bundle), 'bundle_sha256': self.digest(self.bundle),
            'actual_restore_HEAD_tree': {'HEAD': self.head, 'tree': self.tree},
            'custody_owner': 'fixture-owner', 'force_flags': False}]})
        self.packet = {'schema': 1, 'repository': guard.REPO, 'git_common_dir': str((self.root/'.git').resolve()),
                       'operation_branch': 'codex/31-retire', 'operation_head': self.head,
                       'trusted_main': self.head, 'remote': 'origin', 'remote_url': URL,
                       'ref': REF, 'expected_oid': self.head,
                       'assignment': 'https://github.com/'+guard.REPO+'/issues/31#issuecomment-123',
                       'owner': 'fixture-owner', 'writer_stopped': True, 'unused': True,
                       'manifest': {'path': str(self.manifest), 'sha256': self.digest(self.manifest)}}
        self.approval = self.folder/'approval.json'; self.write(self.approval, self.packet)

    def command(self, *args, check=True, input=None):
        return subprocess.run(args, cwd=self.root, env=self.env, text=True,
                              input=input, capture_output=True, check=check)

    def server(self, *args, **kwargs):
        return self.command('git', '--git-dir', str(self.remote), *args, **kwargs)

    @staticmethod
    def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

    @staticmethod
    def write(path, data):
        path.write_text(json.dumps(data)); path.chmod(0o600)

    def retire(self, *args):
        return self.command(sys.executable, 'scripts/workflow/git_guard.py', 'retire-ref',
                        '--packet', str(self.approval), *args, check=False)

    def test_real_trusted_hook_deletes_exact_legacy_ref_and_keeps_main(self):
        result = self.retire()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotEqual(self.server('show-ref', '--verify', REF, check=False).returncode, 0)
        self.assertEqual(self.server('rev-parse', 'refs/heads/main').stdout.strip(), self.head)
        self.assertEqual(self.command('git', 'rev-parse', 'HEAD').stdout.strip(), self.head)
        self.assertEqual(self.command('git', 'status', '--porcelain').stdout, '')

    def new_remote_commit(self):
        return self.server('commit-tree', self.tree, '-p', self.head, input='concurrent writer\n').stdout.strip()

    def test_change_before_advertisement_is_rejected_by_real_pre_push(self):
        changed = self.new_remote_commit(); self.server('update-ref', REF, changed)
        result = self.retire()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('advertised remote OID changed', result.stderr)
        self.assertEqual(self.server('rev-parse', REF).stdout.strip(), changed)

    def test_change_after_client_hook_is_rejected_by_server_old_oid(self):
        changed = self.new_remote_commit(); marker = self.folder/'server-race-ran'
        hook = self.remote/'hooks/pre-receive'
        hook.write_text('#!' + sys.executable + '\nimport os,subprocess\n'
                        f'open({str(marker)!r},"w").write("client hook already passed")\n'
                        'env={k:v for k,v in os.environ.items() if not k.startswith("GIT_")}\n'
                        f'subprocess.run(["git","--git-dir",{str(self.remote)!r},"update-ref",{REF!r},{changed!r}],env=env,check=True)\n')
        hook.chmod(0o700)
        result = self.retire()
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(marker.exists(), result.stderr)
        self.assertIn('expected', result.stderr)
        self.assertEqual(self.server('rev-parse', REF).stdout.strip(), changed)

    def test_wrong_identity_or_custody_and_force_options_cannot_reach_remote(self):
        variants = [{'repository': 'foreign/repo'}, {'ref': 'refs/tags/cursor/old-fixture'},
                    {'ref': 'refs/heads/main'}, {'ref': 'refs/heads/master'}, {'ref': 'refs/heads/develop'}, {'ref': 'refs/heads/codex/99-other'},
                    {'ref': 'refs/heads/cursor/*'}, {'expected_oid': 'bad'},
                    {'expected_oid': 'f'*40}, {'remote_url': 'git@github.com:foreign/repo.git'},
                    {'operation_head': 'f'*40}, {'git_common_dir': '/different/repository'}, {'remote': 'other'}, {'writer_stopped': False}, {'unused': False},
                    {'owner': 'unassigned'}, {'assignment': 'https://github.com/'+guard.REPO+'/issues/99#issuecomment-123'},
                    {'manifest': {'path': str(self.manifest), 'sha256': 'f'*64}}]
        for change in variants:
            self.write(self.approval, dict(self.packet, **change))
            with self.subTest(change=change): self.assertNotEqual(self.retire().returncode, 0)
            self.assertEqual(self.server('rev-parse', REF).stdout.strip(), self.head)
        self.write(self.approval, self.packet)
        self.assertNotEqual(self.retire('--force').returncode, 0)
        dirty = self.root/'uncommitted'; dirty.write_text('uncommitted source')
        self.assertNotEqual(self.retire().returncode, 0)
        self.assertEqual(self.server('rev-parse', REF).stdout.strip(), self.head)
        dirty.unlink()
        self.approval.chmod(0o644)
        self.assertNotEqual(self.retire().returncode, 0)

    def test_hook_rejects_mixed_multi_update_and_tampered_trusted_hook(self):
        env = dict(self.env, CAS_REF_RETIREMENT_PACKET=str(self.approval),
                   CAS_REF_RETIREMENT_SHA256=self.digest(self.approval))
        deletion = f'(delete) {"0"*40} {REF} {self.head}\n'
        for lines in [deletion*2, deletion+f'HEAD {self.head} refs/heads/codex/31-retire {"0"*40}\n',
                      f'HEAD {self.head} {REF} {self.head}\n']:
            result = subprocess.run([sys.executable, 'scripts/workflow/git_guard.py', 'push', 'origin', URL],
                                    cwd=self.root, env=env, input=lines, text=True, capture_output=True)
            self.assertNotEqual(result.returncode, 0)
        (self.root/'.githooks/pre-push').write_text('#!/bin/sh\nexit 0\n')
        self.command('git', 'add', '.githooks/pre-push')
        self.command('git', 'commit', '-qm', 'untrusted hook change')
        changed_head = self.command('git', 'rev-parse', 'HEAD').stdout.strip()
        self.write(self.approval, dict(self.packet, operation_head=changed_head))
        result = self.retire()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('trusted adopted guard', result.stderr)
        self.assertEqual(self.server('rev-parse', REF).stdout.strip(), self.head)


    def test_nonexecuting_hook_and_changed_packet_fail_before_deletion(self):
        env = dict(self.env, CAS_REF_RETIREMENT_PACKET=str(self.approval),
                   CAS_REF_RETIREMENT_SHA256=self.digest(self.approval))
        self.write(self.approval, dict(self.packet, unused=False))
        result = subprocess.run([sys.executable, 'scripts/workflow/git_guard.py', 'push', 'origin', URL],
            cwd=self.root, env=env, input=f'(delete) {"0"*40} {REF} {self.head}\n', text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('digest changed', result.stderr)
        (self.root/'.githooks/pre-push').chmod(0o644)
        self.command('git', 'add', '.githooks/pre-push'); self.command('git', 'commit', '-qm', 'hook not executable')
        changed_head = self.command('git', 'rev-parse', 'HEAD').stdout.strip()
        self.write(self.approval, dict(self.packet, operation_head=changed_head))
        result = self.retire()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('remain executable', result.stderr)
        self.assertEqual(self.server('rev-parse', REF).stdout.strip(), self.head)

    def recreate_after_deletion(self):
        changed = self.new_remote_commit()
        hook = self.remote/'hooks/post-receive'
        hook.write_text('#!' + sys.executable + '\nimport os,subprocess\n'
                        'env={k:v for k,v in os.environ.items() if not k.startswith("GIT_")}\n'
                        f'subprocess.run(["git","--git-dir",{str(self.remote)!r},"update-ref",{REF!r},{changed!r}],env=env,check=True)\n')
        hook.chmod(0o700)
        return changed

    def test_recreated_ref_is_reported_on_the_same_push_endpoint(self):
        changed = self.recreate_after_deletion()
        result = self.retire()
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertIn('Target present after deletion', result.stderr)
        self.assertEqual(self.server('rev-parse', REF).stdout.strip(), changed)

    def test_split_fetch_mirror_cannot_hide_recreated_push_ref(self):
        mirror = self.folder/'mirror.git'
        self.command('git', 'init', '--bare', '-q', str(mirror))
        bridge = self.folder/'ssh-bridge.py'
        bridge.write_text('#!' + sys.executable + '\nimport os,sys,shlex\n'
                          'service,path=shlex.split(sys.argv[-1])\n'
                          'assert service in ("git-upload-pack","git-receive-pack")\n'
                          f'target={str(mirror)!r} if path=="fixture/mirror.git" else {str(self.remote)!r}\n'
                          'os.execvp("git",["git",service.removeprefix("git-"),target])\n')
        self.command('git', 'remote', 'set-url', 'origin', 'git@mirror.invalid:fixture/mirror.git')
        self.command('git', 'remote', 'set-url', '--push', 'origin', URL)
        changed = self.recreate_after_deletion()
        self.assertEqual(self.command('git', 'ls-remote', '--heads', 'origin', REF).stdout, '')
        result = self.retire()
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertIn('Target present after deletion', result.stderr)
        self.assertEqual(self.server('rev-parse', REF).stdout.strip(), changed)

    def test_required_original_digests_reject_missing_null_and_malformed_values(self):
        original = json.loads(self.manifest.read_text())
        for member in ('manifest', 'bundle'):
            for value in (None, '', 0, 'g'*64, 'a'*63, 'a'*65, 'missing'):
                self.server('update-ref', REF, self.head)
                with self.subTest(member=member, value=value):
                    packet = json.loads(json.dumps(self.packet))
                    manifest = json.loads(json.dumps(original))
                    record, key = ((packet['manifest'], 'sha256') if member == 'manifest'
                                   else (manifest['ref_actions'][0], 'bundle_sha256'))
                    if value == 'missing': record.pop(key)
                    else: record[key] = value
                    self.write(self.manifest, manifest)
                    if member == 'bundle': packet['manifest']['sha256'] = self.digest(self.manifest)
                    self.write(self.approval, packet)
                    result = self.retire()
                    self.assertNotEqual(result.returncode, 0, result.stdout)
                    self.assertEqual(self.server('rev-parse', REF).stdout.strip(), self.head)


if __name__ == '__main__':
    unittest.main()
