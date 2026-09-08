"""Real Git transitions against persistent runtime; synthetic plugin archives only."""
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

RUNTIME = Path(__file__).with_name('plugin_zip.py').resolve()


def archive(path, marker='fixture'):
    jar = io.BytesIO()
    with zipfile.ZipFile(jar, 'w') as z:
        z.writestr('META-INF/plugin.xml', '<idea-plugin><id>com.cursoragent.plugin</id><version>1</version></idea-plugin>')
        z.writestr('source.txt', marker)
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr('plugin/lib/plugin.jar', jar.getvalue())


class DeliveryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / 'repo'
        self.root.mkdir()
        self.env = os.environ.copy()
        for key in subprocess.check_output(['git', 'rev-parse', '--local-env-vars'], text=True).splitlines():
            self.env.pop(key, None)
        self.env.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM='1')
        self.git('init', '-b', 'main')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('config', 'user.name', 'Fixture')
        (self.root / '.gitignore').write_text('build/\n')
        (self.root / 'source').write_text('old')
        self.git('add', '.')
        self.git('commit', '-m', 'old without hooks')
        self.old = self.git('rev-parse', 'HEAD').stdout.strip()
        self.cache()
        for branch in ['develop', 'A', 'B']:
            self.git('checkout', '-b', branch)
            (self.root / 'source').write_text(branch)
            self.git('commit', '-am', branch)
            self.cache()
        self.runtime('setup')

    def git(self, *args, check=True, cwd=None):
        return subprocess.run(['git', *args], cwd=cwd or self.root, env=self.env, check=check,
                              text=True, capture_output=True)

    def runtime(self, *args, check=True, cwd=None):
        return subprocess.run(['python3', str(RUNTIME), *args], cwd=cwd or self.root, env=self.env,
                              check=check, text=True, capture_output=True)

    def cache(self):
        out = self.root / 'build/distributions'
        out.mkdir(parents=True, exist_ok=True)
        archive(out / 'fixture.zip', self.git('rev-parse', 'HEAD').stdout.strip())
        self.runtime('cache-built')
        (out / 'fixture.zip').unlink()

    def assert_ready(self, root=None):
        root = root or self.root
        data = json.loads((root / 'build/distributions/manifest.json').read_text())
        self.assertEqual(data['status'], 'ready')
        self.assertEqual(data['commit'], self.git('rev-parse', 'HEAD', cwd=root).stdout.strip())
        self.assertEqual(len(list((root / 'build/distributions').glob('*.zip'))), 1)

    def test_branch_old_checkout_and_gradle_config_override(self):
        for ref in ['main', 'develop', 'A', 'B', self.old, 'main']:
            self.git('config', '--local', 'core.hooksPath', '.githooks')
            self.git('checkout', ref)
            self.assert_ready()
        self.assertIn('plugin-zip/runtime/hooks', self.git('config', '--get', 'core.hooksPath').stdout)
        # No Gradle executable even exists anywhere in this fixture.
        self.assertFalse((self.root / 'gradlew').exists())

    def test_dirty_offline_corruption_fail_closed(self):
        self.runtime('sync', '--offline')
        self.assert_ready()
        (self.root / 'source').write_text('dirty')
        self.assertNotEqual(self.runtime('sync', '--offline', check=False).returncode, 0)
        output = self.root / 'build/distributions'
        self.assertEqual(json.loads((output / 'manifest.json').read_text())['status'], 'dirty')
        self.assertFalse(list(output.glob('*.zip')))
        self.git('checkout', '--', 'source')
        sha = self.git('rev-parse', 'HEAD').stdout.strip()
        cache = self.root / '.git/plugin-zip/cache' / sha
        (cache / f'cursor-in-android-studio-{sha}.zip').write_bytes(b'corrupt')
        self.assertNotEqual(self.runtime('sync', '--offline', check=False).returncode, 0)
        self.assertFalse(list(output.glob('*.zip')))

    def test_two_worktrees_parallel_cache(self):
        other = self.root.parent / 'other'
        self.git('worktree', 'add', '--detach', str(other), self.old)
        self.runtime('setup', cwd=other)
        jobs = [subprocess.Popen(['python3', str(RUNTIME), 'sync', '--offline'], cwd=p, env=self.env,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE) for p in [self.root, other]]
        for job in jobs:
            job.communicate(timeout=20)
            self.assertEqual(job.returncode, 0)
        self.assert_ready()
        self.assert_ready(other)

    def test_existing_global_hook_preserved_and_old_write_fails_closed(self):
        self.assertNotEqual(self.git('commit', '--allow-empty', '-m', 'blocked', check=False).returncode, 0)
        hooks = self.root.parent / 'custom-hooks'
        hooks.mkdir()
        (hooks / 'pre-commit').write_text('#!/bin/sh\nexit 37\n')
        (hooks / 'pre-commit').chmod(0o755)
        self.git('config', '--worktree', 'core.hooksPath', str(hooks))
        self.runtime('setup')
        self.assertNotEqual(self.git('commit', '--allow-empty', '-m', 'blocked', check=False).returncode, 0)
        self.assertEqual((hooks / 'pre-commit').read_text(), '#!/bin/sh\nexit 37\n')

    def test_pull_fast_forward_and_merge(self):
        self.git('checkout', 'main')
        self.git('pull', '.', 'develop', '--ff-only')
        self.assert_ready()
        self.git('checkout', 'A')
        self.git('merge', 'B', '--ff-only')
        self.assert_ready()
