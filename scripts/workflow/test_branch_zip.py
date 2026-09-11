import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import branch_zip as bz
from change_impact import classify

A = 'a' * 40
B = 'b' * 40


class BranchZipTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.directory = Path(self.tmp.name)
        (self.directory / bz.asset_for(A)).write_bytes(b'standard Gradle ZIP bytes')
        self.branch = 'feature/日本語/A'
        self.branches = [{'name': self.branch, 'commit': {'sha': A}}]
        self.releases = []
        self.operations = []
        self.default_branch = 'trunk/release'
        self.release_writes = []
        self.fail_upload = False
        self.fail_patch = False
        self.move_after_upload = False
        self.addCleanup(patch.stopall)
        patch.dict(os.environ, {'GITHUB_REPOSITORY': 'owner/repo'}).start()
        self.impact = patch.object(bz, 'git_impact', return_value=classify([], reason='fixture unknown')).start()
        patch.object(bz, 'api', side_effect=self.api).start()
        patch.object(bz, 'gh', side_effect=self.upload).start()

    def api(self, path, method='GET', data=None):
        self.operations.append((method, path))
        if method == 'GET':
            if path == '':
                return {'default_branch': self.default_branch}
            endpoint, query = path.split('?')
            page = int(query.split('page=')[-1])
            rows = self.branches if endpoint == 'branches' else self.releases if endpoint == 'releases' else self.releases[0]['assets']
            return copy.deepcopy(rows[(page - 1) * 100:page * 100])
        if method == 'POST':
            self.assertEqual(path, 'releases')
            self.release_writes.append((method, copy.deepcopy(data)))
            self.releases.append({**data, 'id': 7, 'assets': []})
            return copy.deepcopy(self.releases[-1])
        if method == 'PATCH':
            self.assertEqual(path, 'releases/7')
            self.release_writes.append((method, copy.deepcopy(data)))
            if self.fail_patch:
                raise RuntimeError('metadata update failed')
            self.releases[0].update(data)
            return copy.deepcopy(self.releases[0])
        if method == 'DELETE':
            self.releases[0]['assets'] = [a for a in self.releases[0]['assets'] if a['id'] != int(path.split('/')[-1])]
            return None
        raise AssertionError((path, method))

    def upload(self, *args):
        self.operations.append(('UPLOAD', ''))
        if self.fail_upload:
            raise RuntimeError('upload failed')
        self.assertIn('https://uploads.github.com/repos/owner/repo/releases/7/assets?name=' + bz.asset_for(A), args)
        asset = {'id': 12, 'name': bz.asset_for(A), 'state': 'uploaded', 'size': 25,
                 'digest': 'sha256:' + hashlib.sha256((self.directory / bz.asset_for(A)).read_bytes()).hexdigest()}
        self.releases[0]['assets'].append(asset)
        if self.move_after_upload:
            self.branches[0]['commit']['sha'] = B
        return json.dumps(asset)

    def old_release(self):
        self.releases = [{'id': 7, 'tag_name': bz.tag_for(self.branch), 'draft': False,
                          'target_commitish': B,
                          'body': bz.marker(self.branch, B), 'assets': [
                              {'id': 11, 'name': bz.asset_for(B), 'state': 'uploaded', 'size': 25}]}]

    def test_names_do_not_alias_slash_case_unicode_or_truncation(self):
        names = ['a/b', 'a-b', 'A/b', '日本語', 'a' * 300, 'a' * 301]
        self.assertEqual(len({bz.tag_for(n) for n in names}), len(names))
        self.assertTrue(all(len(bz.tag_for(n)) == 75 for n in names))

    def test_plan_only_live_heads_and_all_pages(self):
        self.branches = [{'name': f'branch/{i}', 'commit': {'sha': A}} for i in range(101)]
        self.assertEqual(len(bz.plan()), 101)
        self.assertEqual(bz.plan('branch/100')[0]['sha'], A)
        self.assertEqual(bz.plan('deleted'), [])

    def test_new_publish_then_unchanged_plan_does_not_rebuild(self):
        bz.publish(self.branch, A, self.directory)
        self.assertEqual(bz.plan(), [])
        self.assertEqual(len(self.releases), 1)
        self.assertFalse(self.releases[0]['draft'])
        self.assertEqual([method for method, _ in self.release_writes], ['POST', 'PATCH'])
        self.assertTrue(all(data['target_commitish'] == self.default_branch
                            for _, data in self.release_writes))
        self.assertIn(bz.marker(self.branch, A), self.releases[0]['body'])
        self.assertEqual([a['name'] for a in self.releases[0]['assets']], [bz.asset_for(A)])

    def test_update_uploads_before_deleting_previous_zip(self):
        self.old_release()
        bz.publish(self.branch, A, self.directory)
        self.assertEqual([a['name'] for a in self.releases[0]['assets']], [bz.asset_for(A)])
        methods = [method for method, path in self.operations]
        self.assertLess(methods.index('UPLOAD'), methods.index('DELETE'))
        self.assertLess(methods.index('PATCH'), methods.index('DELETE'))

    def test_update_uses_current_repository_default_without_moving_existing_tag(self):
        self.old_release()
        self.default_branch = 'integration/next'
        bz.publish(self.branch, A, self.directory)
        self.assertEqual([method for method, _ in self.release_writes], ['PATCH'])
        self.assertEqual(self.release_writes[0][1]['target_commitish'], self.default_branch)
        self.assertEqual(self.releases[0]['tag_name'], bz.tag_for(self.branch))
        self.assertFalse(any('git/refs' in path or 'git/tags' in path for _, path in self.operations))
        self.assertIn(bz.marker(self.branch, A), self.releases[0]['body'])
        self.assertEqual([a['name'] for a in self.releases[0]['assets']], [bz.asset_for(A)])

    def test_failed_upload_or_metadata_keeps_previous_zip_and_retries(self):
        for stage in ('upload', 'patch'):
            with self.subTest(stage=stage):
                self.old_release()
                self.fail_upload, self.fail_patch = stage == 'upload', stage == 'patch'
                with self.assertRaises(RuntimeError):
                    bz.publish(self.branch, A, self.directory)
                self.assertIn(bz.asset_for(B), [a['name'] for a in self.releases[0]['assets']])
                self.assertEqual(len(bz.plan()), 1)
                self.fail_upload = self.fail_patch = False
                bz.publish(self.branch, A, self.directory)
                self.assertEqual(bz.plan(), [])

    def test_stale_or_deleted_head_never_publishes(self):
        for branches in ([], [{'name': self.branch, 'commit': {'sha': B}}]):
            self.branches = branches
            bz.publish(self.branch, A, self.directory)
            self.assertEqual(self.releases, [])

    def test_head_moves_during_upload_preserves_previous_metadata(self):
        self.old_release()
        self.move_after_upload = True
        bz.publish(self.branch, A, self.directory)
        self.assertEqual(self.releases[0]['body'], bz.marker(self.branch, B))
        self.assertEqual(len(bz.plan()), 1)
        self.assertFalse(any(method in ('DELETE', 'PATCH') for method, _ in self.operations))

    def test_partial_draft_upload_recovery(self):
        self.old_release()
        self.releases[0]['draft'] = True
        self.releases[0]['assets'] = [{'id': 11, 'name': bz.asset_for(A), 'state': 'starter', 'size': 0}]
        bz.publish(self.branch, A, self.directory)
        self.assertEqual(bz.plan(), [])

    def test_knowledge_skip_keeps_built_sha_and_manual_override_builds(self):
        self.old_release()
        original = copy.deepcopy(self.releases)
        self.impact.return_value = classify([('docs/guide.md', ('100644',))])
        self.assertEqual(bz.plan(), [])
        self.impact.assert_called_once_with(B, A, merge_base=False)
        self.assertEqual(self.releases, original)
        self.assertEqual(bz.plan(force_build=True)[0]['sha'], A)
        self.assertEqual(self.releases, original)

    def test_new_docs_branch_has_no_fake_zip_and_invalid_release_is_recovered(self):
        self.impact.return_value = classify([('CLAUDE.md', ('100644',))])
        self.assertEqual(bz.plan(), [])
        self.impact.assert_called_once_with(None, A, merge_base=False)
        self.assertEqual(self.releases, [])
        self.old_release(); self.releases[0]['assets'][0]['size'] = 0
        self.assertEqual(len(bz.plan()), 1)
        self.assertEqual(self.impact.call_count, 1)

    def test_runtime_build_and_unknown_require_zip_but_tests_and_tooling_do_not(self):
        self.old_release()
        for path, expected in [('src/main/resources/help.md', 1), ('build.gradle.kts', 1),
                               ('unknown.xyz', 1), ('src/test/Test.kt', 0), ('scripts/loop/loop.py', 0)]:
            with self.subTest(path=path):
                self.impact.return_value = classify([(path, ('100644',))])
                self.assertEqual(len(bz.plan()), expected)
