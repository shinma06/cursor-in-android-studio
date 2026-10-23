"""Synthetic delivery regressions; every remote boundary is isolated."""
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import release_delivery as delivery
import release_candidate as rc
import plugin_compatibility as pc
from change_impact import classify

SOURCE = 'a' * 40
NEXT = 'b' * 40
MAIN = 'c' * 40


def package(source=SOURCE, state='clean', version='1.2.3', policy=None):
    policy = policy or rc.read(pc.POLICY)
    compile = policy['compile']
    jar_bytes = io.BytesIO()
    with zipfile.ZipFile(jar_bytes, 'w') as jar:
        jar.writestr('META-INF/plugin.xml', f'<idea-plugin><id>{pc.PLUGIN_ID}</id><version>{version}</version>'
                     f'<idea-version since-build="{compile["since_build"]}" until-build="{compile["until_build"]}"/></idea-plugin>')
        jar.writestr('cursor-agent-build.properties', f'source.commit={source}\nsource.state={state}\nplugin.version={version}\n'
                     f'jvm.target={compile["jvm_target"]}\nsdk.build={policy["targets"][compile["target"]]["build"]}\n')
        jar.writestr('Fixture.class', bytes.fromhex('cafebabe0000') + compile['class_major'].to_bytes(2, 'big'))
    archive = io.BytesIO()
    with zipfile.ZipFile(archive, 'w') as outer:
        outer.writestr('plugin/lib/fixture.jar', jar_bytes.getvalue())
    return archive.getvalue()


class Repository:
    def __init__(self):
        self.releases = []
        self.refs = {}
        self.assets = {}
        self.files = {}
        self.next_id = 1
        self.head = SOURCE
        self.main = MAIN
        self.workflow_changes = ''
        self.mutations = []
        self.fail_upload = False
        self.fail_after_patch = False
        self.fail_patch = False
        self.advance_after_upload = False
        self.advance_workflows_after_upload = False
        self.latest = None
        self.policies = {source: rc.read(pc.POLICY) for source in (SOURCE, NEXT)}

    def pages(self, path):
        if path.startswith('releases?'):
            return copy.deepcopy(self.releases)
        number = int(path.split('/')[1])
        return [copy.deepcopy(a) for a in self.assets.values() if a['release'] == number]

    def api(self, path, method='GET', data=None):
        if path == '':
            return {'default_branch': 'main'}
        if path == 'git/ref/heads/main':
            return {'object': {'sha': self.main}}
        if path == 'git/ref/heads/develop':
            return {'object': {'sha': self.head}}
        if path.startswith('git/matching-refs/tags/'):
            tag = path.removeprefix('git/matching-refs/tags/')
            return [{'ref': 'refs/tags/' + tag, 'object': self.refs[tag]}] if tag in self.refs else []
        if path.startswith('git/ref/tags/'):
            return {'object': copy.deepcopy(self.refs[path.removeprefix('git/ref/tags/')])}
        self.mutations.append((path, method, copy.deepcopy(data)))
        if path == 'releases' and method == 'POST':
            result = dict(data, id=self.next_id, immutable=False)
            self.next_id += 1
            self.releases.append(result)
            return copy.deepcopy(result)
        if path.startswith('releases/assets/') and method == 'DELETE':
            self.assets.pop(int(path.split('/')[2]))
            return None
        if path.startswith('releases/') and method == 'PATCH':
            if self.fail_patch:
                raise OSError('connection failed before PATCH')
            result = next(r for r in self.releases if r['id'] == int(path.split('/')[1]))
            result.update(data)
            if data.get('make_latest') == 'true':
                self.latest = result['id']
            self.refs.setdefault(result['tag_name'], {'type': 'commit', 'sha': result['target_commitish'], 'url': 'tag-url'})
            if self.fail_after_patch:
                raise OSError('connection lost after PATCH')
            return copy.deepcopy(result)
        raise AssertionError((path, method, data))

    def git(self, *args):
        if args[0] == 'show':
            source, path = args[1].split(':', 1)
            assert path == 'scripts/workflow/plugin_compatibility.json'
            return json.dumps(self.policies[source])
        if args[0] == 'rev-list':
            return self.head
        return self.workflow_changes if args[0] == 'diff' else ''

    def command(self, args):
        if args[:3] == ['gh', 'pr', 'checks']:
            return json.dumps([{'name': n, 'state': 'SUCCESS'} for n in delivery.CI_CHECKS])
        url = next(a for a in args if a.startswith('https://uploads.github.com/'))
        name = url.split('name=')[1]
        data = Path(args[args.index('--input') + 1]).read_bytes()
        release = int(url.split('/releases/')[1].split('/')[0])
        asset = {'id': self.next_id, 'release': release, 'name': name, 'state': 'uploaded',
                 'size': len(data), 'digest': 'sha256:' + hashlib.sha256(data).hexdigest()}
        self.next_id += 1
        self.assets[asset['id']] = asset
        self.files[asset['id']] = data
        self.mutations.append(('upload', 'POST', name))
        if self.advance_after_upload:
            self.head = NEXT
        if self.advance_workflows_after_upload:
            self.main = NEXT
            self.workflow_changes = '.github/workflows/ci.yml'
        if self.fail_upload:
            raise OSError('upload response lost')
        return json.dumps(asset)

    def download(self, path, destination, accept='application/octet-stream'):
        destination.write_bytes(self.files[int(path.split('/')[2])])


class InactiveTest(unittest.TestCase):
    def test_all_new_cli_and_publication_entries_stop_before_external_calls(self):
        self.assertFalse(delivery.ENABLED)
        with patch.object(delivery, 'command') as command, patch.object(delivery, 'git') as git, \
                patch.object(rc.github, 'api') as old_api, patch.object(rc, 'publication') as gate:
            for argv in (['early', '--run', '1', '--source', SOURCE],
                         ['promoted', '--run', '1', '--source', SOURCE],
                         ['stable', '--promotion-pr', '1', '--directory', 'unused', '--sha256', 'd' * 64]):
                with patch.object(sys, 'argv', ['delivery', *argv]), self.assertRaisesRegex(ValueError, 'inactive'):
                    delivery.main()
            with self.assertRaisesRegex(ValueError, 'inactive'): rc.publish(Path('unused'), 'd' * 64, 1)
            with self.assertRaisesRegex(ValueError, 'inactive'):
                rc.preserve('v1.2.3', MAIN, Path('unused'), [], False, '')
            with self.assertRaisesRegex(ValueError, 'retired'): rc.store(Path('unused'), 'd' * 64)
            for mock in (command, git, old_api, gate): mock.assert_not_called()

    def test_new_workflow_jobs_have_literal_false_gates_and_no_schedule(self):
        for name in ('delivery.yml', 'release-candidate.yml'):
            content = (rc.ROOT / '.github/workflows' / name).read_text()
            self.assertIn("if: ${{ false && github.ref == 'refs/heads/main' }}", content)
            self.assertNotIn('schedule:', content)
            self.assertNotIn('vars.', content)
            self.assertNotIn('secrets.', content)
            self.assertEqual(content.count('    runs-on:'), content.count("    if: ${{ false && github.ref == 'refs/heads/main' }}"))
        # Existing ordinary events retain only their legacy consumers.
        legacy = (rc.ROOT / '.github/workflows/branch-zip.yml').read_text()
        self.assertNotIn('release_delivery', legacy)
        self.assertNotIn('release_candidate', legacy)
        self.assertNotIn('release_delivery', (rc.ROOT / '.github/workflows/ci.yml').read_text())


class DeliveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / 'product.zip'; self.path.write_bytes(package())
        self.repo = Repository()
        patches = [patch.object(delivery, 'ENABLED', True),
                   patch.dict(os.environ, {'GITHUB_ACTIONS': 'false'}),
                   patch.object(delivery, 'api', side_effect=self.repo.api),
                   patch.object(delivery, 'pages', side_effect=self.repo.pages),
                   patch.object(delivery, 'command', side_effect=self.repo.command),
                   patch.object(delivery, 'download', side_effect=self.repo.download),
                   patch.object(delivery, 'git', side_effect=self.repo.git),
                   patch.object(delivery, 'producer_run', return_value={'head_sha': SOURCE}),
                   patch.object(delivery, 'early_gate', return_value=7)]
        for p in patches:
            p.start(); self.addCleanup(p.stop)

    def record(self, source=SOURCE):
        return dict(delivery.identity(self.path, source, delivery.digest(self.path)), schema=1,
                    repository=delivery.REPO, channel='early', pr=7, run=11, develop_head=self.repo.head)

    def test_current_identity_rejects_dirty_source_hash_and_wrong_bytecode(self):
        record = self.record()
        self.assertEqual(record['version'], '1.2.3')
        for data, source, expected in ((package(state='dirty'), SOURCE, None),
                                       (package(), NEXT, None), (package(), SOURCE, 'f' * 64)):
            self.path.write_bytes(data)
            with self.assertRaises(ValueError): delivery.identity(self.path, source, expected or delivery.digest(self.path))
        self.assertEqual(self.repo.mutations, [])

    def test_pinned_bytes_reject_replacement_before_first_write(self):
        record = self.record(); self.path.write_bytes(package(NEXT))
        with self.assertRaisesRegex(ValueError, 'changed after identity'): delivery.publish(self.path, record, MAIN)
        self.assertEqual(self.repo.mutations, [])

    def test_rolling_update_rechecks_same_bytes_and_preserves_fixed_tag_and_unknown_assets(self):
        record = self.record(); delivery.publish(self.path, record, MAIN)
        original_tag = copy.deepcopy(self.repo.refs['early'])
        unknown = {'id': 90, 'release': 1, 'name': 'human-notes.txt'}
        self.repo.assets[90] = unknown
        self.repo.head = NEXT; self.path.write_bytes(package(NEXT))
        next_record = self.record(NEXT)
        delivery.publish(self.path, next_record, 'e' * 40)
        self.assertEqual(self.repo.refs['early'], original_tag)
        self.assertEqual(len(self.repo.releases), 1)
        self.assertIn(90, self.repo.assets)
        self.assertEqual(len([a for a in self.repo.assets.values() if a['name'].endswith('.zip')]), 1)
        before = len(self.repo.mutations)
        delivery.publish(self.path, dict(next_record, run=99), MAIN)
        self.assertEqual(len(self.repo.mutations), before)
        self.assertEqual(delivery.read_record(self.repo.releases[0]['body']), next_record)

    def test_upload_failure_readback_keeps_old_bytes_and_resumes_without_duplicate(self):
        delivery.publish(self.path, self.record(), MAIN)
        old_body = self.repo.releases[0]['body']
        self.path.write_bytes(package(NEXT)); self.repo.head = NEXT
        record = self.record(NEXT); self.repo.fail_upload = True
        with self.assertRaisesRegex(RuntimeError, 'Indeterminate'): delivery.publish(self.path, record, MAIN)
        self.assertEqual(self.repo.releases[0]['body'], old_body)
        self.assertEqual(len(self.repo.assets), 2)
        self.repo.fail_upload = False
        delivery.publish(self.path, record, MAIN)
        self.assertEqual(len(self.repo.assets), 1)
        self.assertEqual(len([w for w in self.repo.mutations if w[0] == 'upload']), 2)

    def test_develop_advance_after_upload_cannot_switch_body_or_delete_previous_zip(self):
        delivery.publish(self.path, self.record(), MAIN)
        old_body = self.repo.releases[0]['body']
        self.path.write_bytes(package(NEXT)); self.repo.head = NEXT
        record = self.record(NEXT); self.repo.advance_after_upload = True
        # Fixture's ordinary advance destination is NEXT; move it beyond this candidate.
        original = self.repo.command
        def upload(args):
            result = original(args); self.repo.head = 'e' * 40; return result
        with patch.object(delivery, 'command', side_effect=upload):
            with self.assertRaisesRegex(ValueError, 'Develop advanced'): delivery.publish(self.path, record, MAIN)
        self.assertEqual(self.repo.releases[0]['body'], old_body)
        self.assertEqual(len(self.repo.assets), 2)

    def test_foreign_release_and_changed_owned_digest_stop_cleanup(self):
        delivery.publish(self.path, self.record(), MAIN)
        self.repo.releases[0]['body'] = 'human release'
        writes = len(self.repo.mutations)
        with self.assertRaisesRegex(ValueError, 'not owned'): delivery.publish(self.path, self.record(), MAIN)
        self.assertEqual(len(self.repo.mutations), writes)
        self.repo.releases[0]['body'] = delivery.release_body(self.record())
        old = copy.deepcopy(next(iter(self.repo.assets.values())))
        old.update(id=99, name='cursor-in-android-studio-' + 'e' * 64 + '.zip', digest='sha256:' + 'f' * 64)
        self.repo.assets[99] = old
        with self.assertRaisesRegex(ValueError, 'Old asset identity'): delivery.publish(self.path, self.record(), MAIN)
        self.assertIn(99, self.repo.assets)

    def test_automatic_target_workflow_difference_refuses_before_any_write(self):
        self.repo.workflow_changes = '.github/workflows/ci.yml'
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'true'}):
            with self.assertRaisesRegex(ValueError, 'Workflows write'): delivery.publish(self.path, self.record(), MAIN)
        self.assertEqual(self.repo.mutations, [])

    def test_ci_artifact_binds_transport_manifest_and_standard_zip(self):
        manifest = pc.seal(self.path, SOURCE, rc.read(pc.POLICY))
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            archive.writestr('standard.zip', self.path.read_bytes())
            archive.writestr('manifest.json', json.dumps(manifest))
        data = stream.getvalue()
        item = {'id': 12, 'name': 'compatibility-input', 'expired': False,
                'size_in_bytes': len(data), 'digest': 'sha256:' + hashlib.sha256(data).hexdigest()}
        def download(endpoint, destination, accept):
            self.assertEqual(accept, 'application/vnd.github+json'); destination.write_bytes(data)
        with patch.object(delivery, 'api', return_value={'total_count': 1, 'artifacts': [item]}), \
                patch.object(delivery, 'download', side_effect=download):
            output = self.root / 'artifact'; output.mkdir()
            archive, _ = delivery.artifact(11, SOURCE, output)
            self.assertEqual(archive.read_bytes(), self.path.read_bytes())
            item['expired'] = True
            with self.assertRaisesRegex(ValueError, 'live source-bound'): delivery.artifact(11, SOURCE, output)
            item['expired'] = False; item['digest'] = 'sha256:' + 'd' * 64
            other = self.root / 'other'; other.mkdir()
            with self.assertRaisesRegex(ValueError, 'transport digest'): delivery.artifact(11, SOURCE, other)

    def test_early_source_policy_binds_artifact_cli_and_staged_bytes_before_writes(self):
        inspector = rc.read(pc.POLICY)
        policy = copy.deepcopy(inspector)
        policy['targets']['rabbit1']['build'] = 'AI-263.1000.1.2631.1'
        policy['compile'].update(since_build='263.1000.1', until_build='263.*')
        self.repo.policies[SOURCE] = policy
        self.path.write_bytes(package(policy=policy))
        manifest = pc.seal(self.path, SOURCE, policy)
        item = {'id': 12, 'name': 'compatibility-input', 'expired': False}
        run = {'id': 11, 'head_sha': SOURCE, 'event': 'push', 'head_branch': 'develop'}
        def transport():
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, 'w') as archive:
                archive.writestr('standard.zip', self.path.read_bytes())
                archive.writestr('manifest.json', json.dumps(manifest))
            data = stream.getvalue()
            item.update(size_in_bytes=len(data), digest='sha256:' + hashlib.sha256(data).hexdigest())
            return data
        def api(path, method='GET', data=None):
            if path == 'actions/runs/11/artifacts?per_page=100':
                return {'total_count': 1, 'artifacts': [item]}
            if path.startswith('actions/workflows/ci.yml/runs?'):
                return {'total_count': 1, 'workflow_runs': [run]}
            return self.repo.api(path, method, data)
        data = transport()
        def download(endpoint, destination, accept='application/octet-stream'):
            if endpoint == 'actions/artifacts/12/zip': destination.write_bytes(data)
            else: self.repo.download(endpoint, destination, accept)
        with patch.object(delivery, 'api', side_effect=api), patch.object(delivery, 'download', side_effect=download), \
                patch.object(delivery, 'producer_run', return_value=run), patch.object(delivery, 'distribution_change', return_value=True), \
                patch.object(delivery, 'git', side_effect=self.repo.git) as git, \
                patch.object(sys, 'argv', ['delivery', 'early', '--run', '11', '--source', SOURCE]):
            delivery.main()  # Real early_product -> Artifact -> CLI identity -> captured-byte identity -> stub publisher.
            self.assertEqual([c.args for c in git.call_args_list if c.args[0] == 'show'],
                             [('show', SOURCE + ':scripts/workflow/plugin_compatibility.json')] * 3)
            record = delivery.read_record(self.repo.releases[0]['body'])
            uploaded = next(a for a in self.repo.assets.values() if a['name'].endswith('.zip'))
            self.assertEqual(self.repo.files[uploaded['id']], self.path.read_bytes())
            self.assertEqual(record['source'], SOURCE)
            writes = len(self.repo.mutations)
            self.repo.policies[SOURCE] = inspector
            output = self.root / 'wrong-policy'; output.mkdir()
            with self.assertRaisesRegex(ValueError, 'supported compile SDK'):
                delivery.artifact(11, SOURCE, output)
            with self.assertRaisesRegex(ValueError, 'supported compile SDK'):
                delivery.publish(self.path, record, MAIN)
            self.repo.policies[SOURCE] = policy
            manifest['identity']['since.build'] = 'wrong range'
            data = transport(); output = self.root / 'wrong-manifest'; output.mkdir()
            with self.assertRaisesRegex(ValueError, 'manifest identity differs'):
                delivery.artifact(11, SOURCE, output)
            self.path.write_bytes(self.path.read_bytes() + b'changed')
            with self.assertRaisesRegex(ValueError, 'changed after identity'):
                delivery.publish(self.path, record, MAIN)
            self.assertEqual(len(self.repo.mutations), writes)
        self.assertEqual(rc.read(pc.POLICY), inspector)

    def test_cleanup_failure_keeps_latest_bytes_and_retry_only_finishes_owned_cleanup(self):
        delivery.publish(self.path, self.record(), MAIN)
        self.repo.head = NEXT; self.path.write_bytes(package(NEXT)); record = self.record(NEXT)
        original = self.repo.api
        def failed_delete(path, method='GET', data=None):
            if method == 'DELETE': raise OSError('synthetic cleanup interruption')
            return original(path, method, data)
        with patch.object(delivery, 'api', side_effect=failed_delete):
            with self.assertRaises(OSError): delivery.publish(self.path, record, MAIN)
        self.assertEqual(delivery.read_record(self.repo.releases[0]['body']), record)
        self.assertEqual(len(self.repo.assets), 2)
        uploads = len([w for w in self.repo.mutations if w[0] == 'upload'])
        delivery.publish(self.path, record, MAIN)
        self.assertEqual(len(self.repo.assets), 1)
        self.assertEqual(len([w for w in self.repo.mutations if w[0] == 'upload']), uploads)


class InputGateTest(unittest.TestCase):
    def test_late_product_event_uses_current_checked_history_and_never_falls_back_past_new_product(self):
        runs = {11: {'id': 11, 'head_sha': SOURCE, 'event': 'push', 'head_branch': 'develop'},
                12: {'id': 12, 'head_sha': NEXT, 'event': 'push', 'head_branch': 'develop'}}
        def api(path):
            if path == 'git/ref/heads/develop': return {'object': {'sha': NEXT}}
            current = 12 if 'head_sha=' + NEXT in path else 11
            return {'total_count': 1, 'workflow_runs': [runs[current]]}
        def git(*args): return NEXT + '\n' + SOURCE if args[0] == 'rev-list' else ''
        with patch.object(delivery, 'api', side_effect=api), patch.object(delivery, 'git', side_effect=git), \
                patch.object(delivery, 'producer_run', side_effect=lambda n, s: runs[n]), \
                patch.object(delivery, 'early_gate', return_value=7), \
                patch.object(delivery, 'distribution_change', side_effect=lambda s: s == SOURCE), \
                patch.object(delivery, 'artifact', return_value=(Path('synthetic.zip'), runs[11])) as artifact:
            self.assertEqual(delivery.early_product(11, SOURCE, Path('unused'))[3], NEXT)
            self.assertEqual(artifact.call_args.args[:2], (11, SOURCE))
            with patch.object(delivery, 'distribution_change', return_value=True), \
                    patch.object(delivery, 'artifact', side_effect=ValueError('new product artifact missing')) as missing:
                with self.assertRaisesRegex(ValueError, 'new product artifact missing'):
                    delivery.early_product(11, SOURCE, Path('unused'))
                self.assertEqual(missing.call_args.args[:2], (12, NEXT))
                self.assertEqual(missing.call_count, 1)
            runs[12]['conclusion'] = 'failure'
            with patch.object(delivery, 'producer_run', side_effect=ValueError('current CI failed')):
                with self.assertRaises(ValueError): delivery.early_product(11, SOURCE, Path('unused'))

    def test_candidate_requires_one_live_artifact_from_the_trusted_successful_workflow(self):
        expected = 'd' * 64
        run = {'head_sha': MAIN, 'event': 'workflow_dispatch', 'head_branch': 'main'}
        item = {'id': 14, 'name': 'candidate-' + expected, 'expired': False, 'workflow_run': {'id': 13}}
        collection = {'total_count': 1, 'artifacts': [item]}
        def api(path): return run if path == 'actions/runs/13' else collection
        def extract(item, directory):
            (directory / 'bundle.json').write_text('{"plugin.zip":"synthetic"}')
            (directory / 'plugin.zip').write_bytes(b'synthetic')
        with tempfile.TemporaryDirectory() as folder, patch.object(delivery, 'api', side_effect=api), \
                patch.object(delivery, 'producer_run', return_value=run) as producer, \
                patch.object(delivery, 'git'), patch.object(delivery, 'download_artifact', side_effect=extract), \
                patch.object(rc, 'validate_bundle', return_value={'synthetic': True}):
            self.assertEqual(delivery.candidate_artifact(expected, Path(folder) / 'candidate', 13), {'synthetic': True})
            self.assertEqual(producer.call_args.args, (13, MAIN, '.github/workflows/release-candidate.yml'))
            for key, value in [('head_branch', 'develop'), ('event', 'push')]:
                before = run[key]; run[key] = value
                with self.assertRaises(ValueError): delivery.candidate_artifact(expected, Path(folder) / 'refused')
                run[key] = before
            item['expired'] = True
            with self.assertRaisesRegex(ValueError, 'live candidate'): delivery.candidate_artifact(expected, Path(folder) / 'expired')

    def test_indeterminate_api_write_reads_back_once_and_never_repeats_the_write(self):
        mutations = []
        def command(args, input=None):
            if '--method' in args and args[args.index('--method') + 1] == 'PATCH':
                mutations.append(args); raise OSError('synthetic response lost')
            if '--slurp' in args: return '[[{"id":1,"tag_name":"early"}]]' if '/assets?' not in args[-1] else '[[]]'
            return '[]'
        with patch.object(delivery, 'ENABLED', True), patch.object(delivery, 'command', side_effect=command):
            with self.assertRaisesRegex(RuntimeError, 'readback|release=1'):
                delivery.api('releases/1', 'PATCH', {'draft': False})
        self.assertEqual(len(mutations), 1)

    def test_main_tooling_event_does_not_publish_or_fetch_candidate(self):
        with patch.object(delivery, 'ENABLED', True), \
                patch.object(delivery, 'producer_run', return_value={'head_branch': 'main', 'event': 'push'}), \
                patch.object(delivery, 'pages', return_value=[]), patch.object(delivery, 'stable_release') as stable:
            delivery.promoted(11, SOURCE, Path('unused'))
            stable.assert_not_called()

    def test_nonproduct_notification_does_not_touch_history_artifact_or_releases(self):
        run = {'head_sha': SOURCE, 'event': 'push', 'head_branch': 'develop'}
        with patch.object(delivery, 'producer_run', return_value=run), patch.object(delivery, 'git'), \
                patch.object(delivery, 'distribution_change', return_value=False), \
                patch.object(delivery, 'api') as api, patch.object(delivery, 'artifact') as artifact:
            self.assertIsNone(delivery.early_product(11, SOURCE, Path('unused')))
            api.assert_not_called(); artifact.assert_not_called()

    def test_unknown_history_is_error_and_unknown_input_requires_distribution(self):
        with patch.object(delivery, 'git_impact', return_value=classify([], reason='missing history')):
            with self.assertRaisesRegex(ValueError, 'complete delivery inputs'): delivery.distribution_change(SOURCE)
        for path, expected in [('src/test/Test.kt', False), ('docs/guide.md', False),
                               ('unknown/input', True), ('src/main/resources/help.md', True)]:
            result = classify([(path, ('100644',))]); result.update(base=SOURCE, head=NEXT)
            with patch.object(delivery, 'git_impact', return_value=result):
                self.assertEqual(delivery.distribution_change(NEXT), expected)

    def test_required_checks_do_not_hide_failure_behind_duplicate_success(self):
        good = [{'name': n, 'state': 'SUCCESS'} for n in delivery.CI_CHECKS]
        for data in (good[:-1], good + [{'name': 'test', 'state': 'FAILURE'}],
                     [dict(i, state='PENDING') for i in good]):
            with patch.object(delivery, 'command', return_value=json.dumps(data)):
                with self.assertRaisesRegex(ValueError, 'incomplete'): delivery.checks({'number': 7})

    def test_workflow_run_must_match_current_repository_workflow_id_and_source(self):
        run = {'id': 11, 'repository': {'full_name': delivery.REPO}, 'head_repository': {'full_name': delivery.REPO},
               'head_sha': SOURCE, 'head_branch': 'develop', 'event': 'push', 'status': 'completed',
               'conclusion': 'success', 'path': '.github/workflows/ci.yml@develop', 'workflow_id': 22}
        workflow = {'id': 22, 'path': '.github/workflows/ci.yml'}
        with patch.object(delivery, 'api', side_effect=lambda path: run if path.startswith('actions/runs/') else workflow):
            self.assertEqual(delivery.producer_run(11, SOURCE), run)
            for key, value in [('workflow_id', 23), ('event', 'pull_request'), ('head_sha', NEXT), ('conclusion', 'failure')]:
                previous = run[key]; run[key] = value
                with self.assertRaises(ValueError): delivery.producer_run(11, SOURCE)
                run[key] = previous


if __name__ == '__main__':
    unittest.main()
