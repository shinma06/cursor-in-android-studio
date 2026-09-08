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

    def test_fresh_setup_order_and_repair_previous_incomplete_setup(self):
        # Simulate a fresh clone's tracked protection arriving after this fixture's old source.
        hooks = self.root / '.githooks'
        hooks.mkdir()
        for name in ('pre-commit', 'pre-push'):
            (hooks / name).write_text('#!/bin/sh\nexit 0\n')
            (hooks / name).chmod(0o755)
        # Existing old setup has .git/hooks as its broken prior destination.
        result = self.runtime('setup', check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('bootstrap.sh', result.stderr)
        self.git('config', '--local', 'core.hooksPath', '.githooks')
        self.runtime('setup')
        config = json.loads((self.root / '.git/plugin-zip-hooks.json').read_text())
        self.assertEqual(Path(config['previous']).resolve(), hooks.resolve())
        self.git('add', '.githooks')
        self.git('commit', '-m', 'correct Issue protection delegation')
        # A new clone has no worktree/local hooks config: setup must stop before installing.
        fresh = self.root.parent / 'fresh'
        self.git('clone', str(self.root), str(fresh))
        result = self.runtime('setup', check=False, cwd=fresh)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((fresh / '.git/plugin-zip/runtime/hooks').exists())
        self.git('config', '--local', 'core.hooksPath', '.githooks', cwd=fresh)
        self.runtime('setup', cwd=fresh)
        self.assertIn('plugin-zip/runtime/hooks', self.git('config', '--get', 'core.hooksPath', cwd=fresh).stdout)

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

    def test_array_manifest_cache_recovers_from_release(self):
        from unittest.mock import patch
        import plugin_zip
        sha = self.git('rev-parse', 'HEAD').stdout.strip()
        tree = self.git('rev-parse', 'HEAD^{tree}').stdout.strip()
        stored = self.root / '.git/plugin-zip/cache' / sha
        assets = {item.name: item.read_bytes() for item in stored.iterdir()}
        (stored / 'manifest.json').write_text('[]')
        with self.assertRaises(ValueError):
            plugin_zip.validate(stored, sha, tree)

        def fetch(url, destination):
            self.assertEqual(url, f'https://github.com/{plugin_zip.REPOSITORY}/releases/download/plugin-build-{sha}/{destination.name}')
            destination.write_bytes(assets[destination.name])

        old = Path.cwd()
        os.chdir(self.root)
        try:
            with patch.object(plugin_zip, 'download', side_effect=fetch) as download:
                self.assertEqual(plugin_zip.sync(), 0)
                self.assertEqual(download.call_count, 2)
            self.assert_ready()
            plugin_zip.validate(stored, sha, tree)
            self.assertEqual((stored / 'manifest.json').read_bytes(), assets['manifest.json'])
            with patch.object(plugin_zip, 'download') as download:
                self.assertEqual(plugin_zip.sync(offline=True), 0)
                download.assert_not_called()
            self.assert_ready()
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
        with patch.object(publisher, 'ensure_tip'), patch.object(publisher, 'gh', return_value=json.dumps(releases)), patch.object(
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

    def test_draft_retry_preserves_uploaded_asset_and_fills_missing(self):
        from unittest.mock import patch
        import plugin_zip_publish as publisher
        sha = 'a' * 40
        remote, uploads = {}, []
        fail = [True]
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            for name in ('manifest.json', f'{publisher.NAME}-{sha}.zip'):
                (folder / name).write_bytes(name.encode())
            def gh(*args):
                if args[1] == 'upload':
                    path = Path(args[3])
                    uploads.append(path.name)
                    if path.name == 'manifest.json' and fail[0]:
                        fail[0] = False
                        raise RuntimeError('temporary upload failure')
                    remote[path.name] = path.read_bytes()
                elif args[1] == 'download':
                    name = args[args.index('--pattern') + 1]
                    (Path(args[args.index('--dir') + 1]) / name).write_bytes(remote[name])
            with patch.object(publisher, 'gh', side_effect=gh), patch.object(publisher, 'validate') as validate:
                with self.assertRaises(RuntimeError):
                    publisher.complete_draft(folder, sha, 'tree', 'tag', {'assets': []})
                publisher.complete_draft(folder, sha, 'tree', 'tag', {'assets': [{'name': n} for n in remote]})
                self.assertEqual(uploads.count(f'{publisher.NAME}-{sha}.zip'), 1)
                self.assertEqual(uploads.count('manifest.json'), 2)
                validate.assert_called_once()
                remote['manifest.json'] = b'different'
                with self.assertRaises(ValueError):
                    publisher.complete_draft(folder, sha, 'tree', 'tag', {'assets': [{'name': n} for n in remote]})

    def test_default_branch_event_signal_does_not_trust_event_code(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / '.github/workflows/plugin-zip.yml').read_text()
        signal = (root / '.github/workflows/plugin-zip-signal.yml').read_text()
        self.assertIn('workflows: [CI, Plugin ZIP signal]', workflow)
        self.assertIn("github.ref == 'refs/heads/main'", workflow)
        self.assertNotIn('github.event.workflow_run.', workflow)
        self.assertIn('permissions: {}', signal)
        self.assertNotIn('checkout', signal)

    def test_missing_snapshot_tip_fetch_is_bounded_and_keeps_exact_sha(self):
        from unittest.mock import Mock, patch
        import plugin_zip_publish as publisher
        sha = 'd' * 40
        with patch.object(publisher.subprocess, 'run', side_effect=[Mock(returncode=1), Mock(returncode=0), Mock(returncode=0)]) as run:
            publisher.ensure_tip(sha)
            self.assertEqual(run.call_args_list[1].args[0],
                             ['git', 'fetch', '--no-tags', '--no-write-fetch-head', 'origin', sha])
            self.assertEqual(run.call_count, 3)
        with patch.object(publisher.subprocess, 'run', return_value=Mock(returncode=1)) as run:
            with self.assertRaises(ValueError):
                publisher.ensure_tip(sha)
            self.assertEqual(run.call_count, 5)
