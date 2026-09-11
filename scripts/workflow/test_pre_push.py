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
        for key in ('GITHUB_STEP_SUMMARY', 'GITHUB_OUTPUT'):
            self.env.pop(key, None)
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
        for name in ('git_guard.py', 'change_impact.py'):
            shutil.copy2(ROOT / 'scripts/workflow' / name, self.repo / 'scripts/workflow' / name)
        (self.repo / '.gitignore').write_text('build/\ngradle-args\n__pycache__/\n')
        (self.repo / 'gradlew').write_text(
            '#!/usr/bin/env bash\nset -eu\nprintf "%s\\n" "$@" > gradle-args\n'
            'if [ "${FIXTURE_BUILD_FAIL:-0}" = 1 ]; then exit 23; fi\n'
            'exit 0\n')
        (self.repo / 'gradlew').chmod(0o755)
        self.git('add', '.')
        self.git('commit', '-m', 'fixture')
        self.git('remote', 'add', 'origin', str(base / 'remote.git'))

    def git(self, *args, check=True):
        return subprocess.run(['git', *args], cwd=self.repo, env=self.env,
                              text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check)

    def test_push_runs_tests_without_zip_cache(self):
        self.git('push', 'origin', BRANCH)
        head = self.git('rev-parse', 'HEAD').stdout.strip()
        self.assertEqual((self.repo / 'gradle-args').read_text().splitlines(),
                         ['test', '--console=plain'])
        self.assertFalse((self.repo / '.git/plugin-zip').exists())
        self.assertIn(head, self.git('ls-remote', 'origin', 'refs/heads/' + BRANCH).stdout)

    def test_test_failure_blocks_remote_update(self):
        self.env['FIXTURE_BUILD_FAIL'] = '1'
        result = self.git('push', 'origin', BRANCH, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue((self.repo / 'gradle-args').exists())
        self.assertEqual(self.git('ls-remote', 'origin', 'refs/heads/' + BRANCH).stdout, '')

    def test_dirty_push_is_rejected_before_tests(self):
        (self.repo / 'uncommitted').write_text('dirty')
        self.assertNotEqual(self.git('push', 'origin', BRANCH, check=False).returncode, 0)
        self.assertFalse((self.repo / 'gradle-args').exists())

    def test_no_update_and_deletion_skip_tests(self):
        self.git('push', 'origin', BRANCH)
        (self.repo / 'gradle-args').unlink()
        self.env['FIXTURE_BUILD_FAIL'] = '1'
        self.git('push', 'origin', BRANCH)
        self.assertFalse((self.repo / 'gradle-args').exists())
        self.git('push', 'origin', '--delete', BRANCH)
        self.assertFalse((self.repo / 'gradle-args').exists())
        self.assertEqual(self.git('ls-remote', 'origin', 'refs/heads/' + BRANCH).stdout, '')

    def test_knowledge_branch_skips_gradle_but_mixed_push_runs_it(self):
        self.git('update-ref', 'refs/remotes/origin/develop', 'HEAD')
        (self.repo / 'AGENTS.md').write_text('Knowledge only\n')
        self.git('add', '.'); self.git('commit', '-m', 'docs')
        self.env['FIXTURE_BUILD_FAIL'] = '1'
        first = self.git('push', 'origin', BRANCH)
        self.assertIn('IMPACT_KNOWLEDGE_ONLY', first.stdout + first.stderr)
        self.assertFalse((self.repo / 'gradle-args').exists())
        before = self.git('rev-parse', 'HEAD').stdout.strip()
        (self.repo / 'src/main').mkdir(parents=True)
        (self.repo / 'src/main/App.kt').write_text('class App\n')
        self.git('add', '.'); self.git('commit', '-m', 'runtime')
        result = self.git('push', 'origin', BRANCH, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue((self.repo / 'gradle-args').exists())
        self.assertIn(before, self.git('ls-remote', 'origin', 'refs/heads/' + BRANCH).stdout)

    def test_legacy_branch_without_classifier_runs_full_tests(self):
        (self.repo / 'scripts/workflow/change_impact.py').unlink()
        self.git('add', '.'); self.git('commit', '-m', 'legacy branch')
        self.git('push', 'origin', BRANCH)
        self.assertEqual((self.repo / 'gradle-args').read_text().splitlines(), ['test', '--console=plain'])
