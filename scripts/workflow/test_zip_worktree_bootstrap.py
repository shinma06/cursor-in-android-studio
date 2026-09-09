"""Real Git worktree config inheritance must retain project write protection."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[2]


class WorktreeBootstrapTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / 'repo'
        self.root.mkdir()
        self.env = os.environ.copy()
        for key in subprocess.check_output(['git', 'rev-parse', '--local-env-vars'], text=True).splitlines():
            self.env.pop(key, None)
        self.env.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM='1')
        self.command('git', 'init', '-b', 'codex/124-root')
        self.command('git', 'config', 'user.name', 'Fixture')
        self.command('git', 'config', 'user.email', 'fixture@example.invalid')
        for name in ('.githooks/pre-commit', '.githooks/pre-push', 'scripts/workflow/git_guard.py',
                     'scripts/workflow/bootstrap.sh'):
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(SOURCE / name, target)
        (self.root / '.gitignore').write_text('build/\n')
        self.command('git', 'add', '.')
        self.command('git', 'commit', '-m', 'fixture')
        self.command('bash', 'scripts/workflow/bootstrap.sh')
        runtime = self.root / '.git/plugin-zip/runtime/hooks'
        runtime.mkdir(parents=True)
        self.command('git', 'config', '--local', 'extensions.worktreeConfig', 'true')
        self.command('git', 'config', '--worktree', 'core.hooksPath', str(runtime))
        self.child = self.root.parent / 'child'
        self.command('git', 'worktree', 'add', '-b', 'codex/124-child', str(self.child))
        self.workgit = Path(self.command('git', 'rev-parse', '--absolute-git-dir', cwd=self.child).stdout.strip())
        self.assertFalse((self.workgit / 'plugin-zip-hooks.json').exists())
        self.assertIn('plugin-zip/runtime/hooks', self.command('git', 'config', '--get', 'core.hooksPath', cwd=self.child).stdout)

    def command(self, *args, cwd=None, check=True):
        return subprocess.run(args, cwd=cwd or self.root, env=self.env, check=check, capture_output=True, text=True)

    def test_bootstrap_repairs_inherited_runtime_and_delegates_real_git_guards(self):
        self.command('bash', 'scripts/workflow/bootstrap.sh', cwd=self.child)
        self.assertEqual(self.command('git', 'config', '--get', 'core.hooksPath', cwd=self.child).stdout.strip(), '.githooks')
        self.assertFalse((self.workgit / 'plugin-zip-hooks.json').exists())
        self.command('git', 'commit', '--allow-empty', '-m', 'after bootstrap', cwd=self.child)
        remote = self.root.parent / 'remote.git'
        self.command('git', 'init', '--bare', str(remote))
        self.command('git', 'remote', 'add', 'origin', str(remote), cwd=self.child)
        rejected = self.command('git', 'push', 'origin', 'HEAD:main', cwd=self.child, check=False)
        self.assertNotEqual(rejected.returncode, 0)
        self.assertIn('Direct push/deletion', rejected.stderr)
        self.assertEqual(self.command('git', '--git-dir', str(remote), 'show-ref', check=False).returncode, 1)

    def test_bootstrap_preserves_custom_local_hooks_before_any_overwrite(self):
        self.command('git', 'config', '--local', 'core.hooksPath', 'custom-hooks', cwd=self.child)
        before = self.command('git', 'config', '--show-origin', '--get-regexp', 'core.hooksPath', cwd=self.child).stdout
        rejected = self.command('bash', 'scripts/workflow/bootstrap.sh', cwd=self.child, check=False)
        self.assertNotEqual(rejected.returncode, 0)
        self.assertIn('Custom hook configuration', rejected.stderr)
        self.assertEqual(before, self.command('git', 'config', '--show-origin', '--get-regexp', 'core.hooksPath', cwd=self.child).stdout)
        self.assertFalse((self.workgit / 'plugin-zip-hooks.json').exists())
