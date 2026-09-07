"""Exercise the real hook through git push, with a deterministic Gradle stand-in."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
BRANCH = 'codex/87-hook-fixture'


class PrePushTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        base = Path(self.temp.name)
        self.repo = base / 'source'
        self.repo.mkdir()
        self.env = os.environ.copy()
        for key in subprocess.check_output(['git', 'rev-parse', '--local-env-vars'], text=True).splitlines():
            self.env.pop(key, None)
        self.env['GIT_CONFIG_NOSYSTEM'] = '1'
        self.env['GIT_CONFIG_GLOBAL'] = os.devnull
        self.git('init', '--bare', str(base / 'remote.git'))
        self.git('init', '-b', BRANCH)
        self.git('config', 'user.name', 'Hook fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'core.hooksPath', '.githooks')
        (self.repo / '.githooks').mkdir()
        (self.repo / 'scripts/workflow').mkdir(parents=True)
        (self.repo / 'scripts/loop').mkdir()
        for directory in ('workflow', 'loop'):
            (self.repo / 'scripts' / directory / 'test_fixture.py').write_text(
                'import unittest\nclass Fixture(unittest.TestCase):\n'
                '    def test_fixture(self): self.assertTrue(True)\n')
        shutil.copy2(ROOT / '.githooks/pre-push', self.repo / '.githooks/pre-push')
        shutil.copy2(ROOT / 'scripts/workflow/git_guard.py', self.repo / 'scripts/workflow/git_guard.py')
        (self.repo / '.gitignore').write_text('build/\ngradle-args\n')
        (self.repo / 'gradlew').write_text(
            '#!/usr/bin/env bash\nset -eu\nprintf "%s\\n" "$@" > gradle-args\n'
            'if [ "${FIXTURE_BUILD_FAIL:-0}" = 1 ]; then exit 23; fi\n'
            'mkdir -p build/distributions\n'
            'git rev-parse HEAD > build/distributions/fixture.zip\n')
        (self.repo / 'gradlew').chmod(0o755)
        self.git('add', '.')
        self.git('commit', '-m', 'fixture')
        self.git('remote', 'add', 'origin', str(base / 'remote.git'))

    def git(self, *args, check=True):
        return subprocess.run(['git', *args], cwd=self.repo, env=self.env,
                              text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check)

    def test_push_builds_zip_for_pushed_head(self):
        self.git('push', 'origin', BRANCH)
        head = self.git('rev-parse', 'HEAD').stdout.strip()
        self.assertEqual((self.repo / 'gradle-args').read_text().splitlines(),
                         ['test', 'buildPlugin', '--console=plain'])
        self.assertEqual((self.repo / 'build/distributions/fixture.zip').read_text().strip(), head)
        self.assertIn(head, self.git('ls-remote', 'origin', 'refs/heads/' + BRANCH).stdout)

    def test_build_failure_blocks_remote_update(self):
        self.env['FIXTURE_BUILD_FAIL'] = '1'
        result = self.git('push', 'origin', BRANCH, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue((self.repo / 'gradle-args').exists())
        self.assertEqual(self.git('ls-remote', 'origin', 'refs/heads/' + BRANCH).stdout, '')

    def test_dirty_push_is_rejected_before_build(self):
        (self.repo / 'uncommitted').write_text('dirty')
        self.assertNotEqual(self.git('push', 'origin', BRANCH, check=False).returncode, 0)
        self.assertFalse((self.repo / 'gradle-args').exists())

    def test_no_update_and_deletion_skip_build(self):
        self.git('push', 'origin', BRANCH)
        (self.repo / 'gradle-args').unlink()
        self.env['FIXTURE_BUILD_FAIL'] = '1'
        self.git('push', 'origin', BRANCH)
        self.assertFalse((self.repo / 'gradle-args').exists())
        self.git('push', 'origin', '--delete', BRANCH)
        self.assertFalse((self.repo / 'gradle-args').exists())
        self.assertEqual(self.git('ls-remote', 'origin', 'refs/heads/' + BRANCH).stdout, '')
