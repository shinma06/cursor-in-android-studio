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


def archive(path, marker='fixture', topdir='plugin', jar_name='plugin.jar'):
    jar = io.BytesIO()
    with zipfile.ZipFile(jar, 'w') as z:
        z.writestr('META-INF/plugin.xml', '<idea-plugin><id>com.cursoragent.plugin</id><version>1</version></idea-plugin>')
        z.writestr('source.txt', marker)
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr(f'{topdir}/lib/{jar_name}', jar.getvalue())


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

    def test_legacy_stale_candidates_removed_in_every_sync_state(self):
        output = self.root / 'build/distributions'
        legacy = output / 'cursor-agent-plugin-0.1.0-SNAPSHOT.zip'
        archive(legacy, topdir='cursor-agent-plugin')
        self.runtime('sync', '--offline')
        self.assert_ready()
        self.assertFalse(legacy.exists())
        archive(legacy, topdir='cursor-agent-plugin')
        (self.root / 'source').write_text('dirty')
        self.runtime('sync', '--offline', check=False)
        self.assertEqual(json.loads((output / 'manifest.json').read_text())['status'], 'dirty')
        self.assertFalse(list(output.glob('*.zip')))
        self.git('checkout', '--', 'source')
        sha = self.git('rev-parse', 'HEAD').stdout.strip()
        (self.root / '.git/plugin-zip/cache' / sha / 'manifest.json').unlink()
        archive(legacy, topdir='cursor-agent-plugin')
        self.runtime('sync', '--offline', check=False)
        self.assertEqual(json.loads((output / 'manifest.json').read_text())['status'], 'unavailable')
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
        (hooks / 'commit-msg').write_text('#!/bin/sh\nexit 41\n')
        (hooks / 'commit-msg').chmod(0o755)
        self.git('config', '--worktree', 'core.hooksPath', str(hooks))
        self.runtime('setup')
        self.assertNotEqual(self.git('commit', '--allow-empty', '-m', 'blocked', check=False).returncode, 0)
        self.assertEqual((hooks / 'pre-commit').read_text(), '#!/bin/sh\nexit 37\n')
        installed = Path(self.git('config', '--get', 'core.hooksPath').stdout.strip())
        result = subprocess.run([str(installed / 'commit-msg'), 'message'], cwd=self.root, env=self.env)
        self.assertEqual(result.returncode, 41)

    def test_pull_fast_forward_and_merge(self):
        self.git('checkout', 'main')
        self.git('pull', '.', 'develop', '--ff-only')
        self.assert_ready()
        self.git('checkout', 'A')
        self.git('merge', 'B', '--ff-only')
        self.assert_ready()

    def test_real_merge_and_rebase_invalidate_unbuilt_result(self):
        # Only this disposable fixture allows writes; production protection is untouched.
        allow = self.root / '.git/hooks/pre-commit'
        allow.write_text('#!/bin/sh\nexit 0\n')
        allow.chmod(0o755)
        self.git('checkout', '-b', 'topic', 'main')
        (self.root / 'topic-file').write_text('topic')
        self.git('add', 'topic-file')
        self.git('commit', '-m', 'topic')
        self.cache()
        self.git('checkout', 'B')
        self.git('merge', 'topic', '--no-ff', '--no-edit')
        output = self.root / 'build/distributions'
        self.assertEqual(json.loads((output / 'manifest.json').read_text())['status'], 'unavailable')
        self.assertFalse(list(output.glob('*.zip')))
        self.cache()
        self.runtime('sync', '--offline')
        self.assert_ready()
        self.git('checkout', 'topic')
        self.git('rebase', 'A')
        self.assertEqual(json.loads((output / 'manifest.json').read_text())['status'], 'unavailable')
        self.assertFalse(list(output.glob('*.zip')))

    def test_release_download_and_timeout_clear_candidates(self):
        import shutil
        from unittest.mock import patch
        import plugin_zip
        sha = self.git('rev-parse', 'HEAD').stdout.strip()
        stored = self.root / '.git/plugin-zip/cache' / sha
        delivery = self.root.parent / 'release'
        shutil.copytree(stored, delivery)
        shutil.rmtree(stored)
        def fetch(url, destination):
            self.assertIn('/plugin-build-' + sha + '/', url)
            shutil.copyfile(delivery / destination.name, destination)
        old = Path.cwd()
        os.chdir(self.root)
        try:
            with patch.object(plugin_zip, 'download', side_effect=fetch):
                self.assertEqual(plugin_zip.sync(), 0)
            self.assert_ready()
            shutil.rmtree(stored)
            with patch.object(plugin_zip, 'download', side_effect=TimeoutError):
                self.assertEqual(plugin_zip.sync(), 1)
            self.assertFalse(list((self.root / 'build/distributions').glob('*.zip')))
            self.assertEqual(json.loads((self.root / 'build/distributions/manifest.json').read_text())['status'], 'unavailable')
        finally:
            os.chdir(old)

    def test_historical_build_only_recipe_and_legacy_archive_names(self):
        archive_path = self.root / 'build/distributions/historical.zip'
        archive(archive_path, topdir='cursor-agent-plugin', jar_name='cursor-agent-plugin-0.1.jar')
        output = self.root.parent / 'historical-delivery'
        self.runtime('record', str(archive_path), str(output), '--recipe', 'gradle-buildPlugin-v1',
                     '--platform', 'Android Studio 2026.1.3.8 (AI-261)', '--jdk', 'Gradle JDK 17; Kotlin toolchain 21')
        manifest = json.loads((output / 'manifest.json').read_text())
        self.assertEqual(manifest['recipe'], 'gradle-buildPlugin-v1')
        self.assertEqual(manifest['build_environment']['jdk'], 'Gradle JDK 17; Kotlin toolchain 21')
        self.assertEqual(manifest['build_environment']['platform'], 'Android Studio 2026.1.3.8 (AI-261)')
        self.assertNotEqual(self.runtime('record', str(archive_path), str(output), check=False).returncode, 0)
        self.assertNotEqual(self.runtime('record', str(archive_path), str(output), '--recipe', 'gradle-buildPlugin-v1',
                                        '--platform', '/private/platform', '--jdk', '17', check=False).returncode, 0)

    def test_sha_mismatch_and_corrupt_zip_rejected(self):
        import plugin_zip
        sha = self.git('rev-parse', 'HEAD').stdout.strip()
        tree = self.git('rev-parse', 'HEAD^{tree}').stdout.strip()
        folder = self.root / '.git/plugin-zip/cache' / sha
        with self.assertRaises(ValueError):
            plugin_zip.validate(folder, self.old, tree)
        data = json.loads((folder / 'manifest.json').read_text())
        data['tree'] = '0' * 40
        (folder / 'manifest.json').write_text(json.dumps(data))
        with self.assertRaises(ValueError):
            plugin_zip.validate(folder, sha, tree)


class PublisherTest(unittest.TestCase):
    def test_inventory_live_tips_middle_commits_and_missing_zip(self):
        from unittest.mock import patch
        import plugin_zip_publish as publisher
        tip, middle, old = 'a' * 40, 'b' * 40, 'c' * 40
        releases = [[{'tag_name': 'plugin-build-' + old, 'draft': False,
                      'assets': [{'name': 'manifest.json'}, {'name': publisher.NAME + '-' + old + '.zip'}]},
                     {'tag_name': 'plugin-build-' + middle, 'draft': False,
                      'assets': [{'name': 'manifest.json'}]}]]
        with patch.object(publisher, 'gh', return_value=json.dumps(releases)), patch.object(
                publisher, 'git', side_effect=[tip + '\trefs/heads/main', '\n'.join([old, middle, tip])]):
            self.assertEqual(publisher.inventory(10), [tip, middle])

    def test_existing_publication_different_digest_never_overwritten(self):
        from unittest.mock import patch, Mock
        import plugin_zip_publish as publisher
        sha = 'a' * 40
        with patch.object(publisher, 'git', return_value='tree'), patch.object(
                publisher, 'validate', side_effect=[({'sha256': 'new'}, Path('archive')), ({'sha256': 'old'}, Path('archive'))]), patch.object(
                publisher.subprocess, 'run', return_value=Mock(returncode=0, stdout='{"draft": false}')), patch.object(publisher, 'gh') as gh:
            with self.assertRaises(ValueError):
                publisher.publish(Path('delivery'), sha)
            self.assertEqual(gh.call_count, 1)
            self.assertEqual(gh.call_args.args[:2], ('release', 'download'))
