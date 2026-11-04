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
        self.tags = []
        self.fail_delete = None
        self.operations = []
        self.default_branch = 'trunk/release'
        self.release_writes = []
        self.fail_upload = False
        self.fail_patch = False
        self.move_after_upload = False
        self.addCleanup(patch.stopall)
        patch.dict(os.environ, {'GITHUB_REPOSITORY': 'owner/repo', 'GITHUB_STEP_SUMMARY': '', 'GITHUB_OUTPUT': ''}).start()
        self.impact = patch.object(bz, 'git_impact', return_value=classify([], reason='fixture unknown')).start()
        patch.object(bz, 'api', side_effect=self.api).start()
        patch.object(bz, 'gh', side_effect=self.upload).start()

    def api(self, path, method='GET', data=None):
        self.operations.append((method, path))
        if method == 'GET':
            if path == '':
                return {'default_branch': self.default_branch}
            endpoint = path.split('?')[0]
            if endpoint.startswith('git/matching-refs/'):
                # This API returns the complete matching set, ignoring page/per_page.
                prefix = 'refs/' + endpoint.removeprefix('git/matching-refs/')
                return copy.deepcopy([r for r in self.tags if r['ref'].startswith(prefix)])
            _, query = path.split('?')
            page = int(query.split('page=')[-1])
            if endpoint == 'branches':
                rows = self.branches
            elif endpoint == 'releases':
                rows = self.releases
            else:
                # Handle releases/{id}/assets endpoint
                release_id = int(endpoint.split('/')[1])
                target_release = next((r for r in self.releases if r['id'] == release_id), None)
                rows = target_release['assets'] if target_release else []
            return copy.deepcopy(rows[(page - 1) * 100:page * 100])
        if method == 'POST':
            self.assertEqual(path, 'releases')
            self.release_writes.append((method, copy.deepcopy(data)))
            # Use ID 7 for new releases created by the test
            self.releases.append({**data, 'id': 7, 'assets': []})
            return copy.deepcopy(self.releases[-1])
        if method == 'PATCH':
            # Extract release ID from path (format: releases/{id})
            if not path.startswith('releases/'):
                raise AssertionError(f'Unexpected PATCH path: {path}')
            release_id = int(path.split('/')[-1])
            self.release_writes.append((method, copy.deepcopy(data)))
            if self.fail_patch:
                raise RuntimeError('metadata update failed')
            # Find and update the matching release
            target_release = next((r for r in self.releases if r['id'] == release_id), None)
            if target_release:
                target_release.update(data)
                return copy.deepcopy(target_release)
            raise AssertionError(f'Release {release_id} not found for PATCH')
        if method == 'DELETE':
            if self.fail_delete == path:
                raise RuntimeError('delete failed')
            if path.startswith('git/refs/'):
                self.tags = [r for r in self.tags if r['ref'] != path.removeprefix('git/')]
                return None
            if path.startswith('releases/'):
                # Handle both full release deletion and asset deletion
                parts = path.split('/')
                if parts[1] == 'assets':
                    # releases/assets/{asset_id} - delete asset from any release
                    asset_id = int(parts[2])
                    for release in self.releases:
                        release['assets'] = [a for a in release['assets'] if a['id'] != asset_id]
                    return None
                elif len(parts) == 2:  # releases/{id}
                    release_id = int(parts[1])
                    self.releases = [r for r in self.releases if r['id'] != release_id]
                    return None
            return None
        raise AssertionError((path, method))

    def upload(self, *args):
        self.operations.append(('UPLOAD', ''))
        if self.fail_upload:
            raise RuntimeError('upload failed')
        # Extract release ID from the upload URL
        url = [arg for arg in args if 'uploads.github.com' in str(arg)][0]
        release_id = int(url.split('/releases/')[1].split('/assets')[0])
        self.assertIn(f'https://uploads.github.com/repos/owner/repo/releases/{release_id}/assets?name=' + bz.asset_for(A), args)
        asset = {'id': 12, 'name': bz.asset_for(A), 'state': 'uploaded', 'size': 25,
                 'digest': 'sha256:' + hashlib.sha256((self.directory / bz.asset_for(A)).read_bytes()).hexdigest()}
        # Find the release with the matching ID
        target_release = next((r for r in self.releases if r['id'] == release_id), self.releases[0])
        target_release['assets'].append(asset)
        if self.move_after_upload:
            self.branches[0]['commit']['sha'] = B
        return json.dumps(asset)

    def old_release(self):
        self.releases = [{'id': 7, 'tag_name': bz.tag_for(self.branch), 'draft': False,
                          'target_commitish': B, 'name': f'Plugin ZIP — {self.branch}', 'prerelease': True,
                          'body': bz.marker(self.branch, B), 'assets': [
                              {'id': 11, 'name': bz.asset_for(B), 'state': 'uploaded', 'size': 25}]}]

        self.tags = [{'ref': 'refs/tags/' + bz.tag_for(self.branch), 'object': {'type': 'commit', 'sha': B}}]

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

    def test_normal_update_uploads_before_deleting_previous_zip(self):
        self.old_release()
        bz.publish(self.branch, A, self.directory)
        self.assertEqual([a['name'] for a in self.releases[0]['assets']], [bz.asset_for(A)])
        methods = [m for m, _ in self.operations]
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
        # The branch returned to its already published source; retained old bytes remain valid.
        self.assertEqual(bz.plan(), [])
        self.assertFalse(any(method in ('DELETE', 'PATCH') for method, _ in self.operations))

    def test_partial_draft_upload_recovery(self):
        self.old_release()
        self.releases[0].update(draft=True, body=bz.marker(self.branch, A), assets=[
            {'id': 11, 'name': bz.asset_for(A), 'state': 'starter', 'size': 0}])
        bz.publish(self.branch, A, self.directory)
        self.assertEqual(bz.plan(), [])

    def test_unknown_partial_draft_is_preserved(self):
        self.old_release()
        self.releases[0].update(draft=True, assets=[
            {'id': 11, 'name': bz.asset_for(A), 'state': 'starter', 'size': 0}])
        original = copy.deepcopy(self.releases)
        with self.assertRaisesRegex(ValueError, 'Incomplete ZIP identity'):
            bz.publish(self.branch, A, self.directory)
        self.assertEqual(self.releases, original)
        self.assertFalse(any(method != 'GET' for method, _ in self.operations))

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

    def test_cleanup_dry_run_and_deleted_branch_removes_release_assets_and_tag(self):
        self.old_release()
        self.assertEqual(bz.cleanup_report()['candidates'], [])
        self.assertIn('exists', bz.cleanup_report()['kept'][0]['reason'])
        self.branches = []
        candidate = bz.cleanup_report()['candidates'][0]
        self.assertEqual(candidate['release_id'], 7)
        self.assertFalse(any(method == 'DELETE' for method, _ in self.operations))
        bz.cleanup(self.branch, B, 7)
        self.assertEqual(self.releases, [])
        self.assertEqual(self.tags, [])
        self.assertEqual(bz.cleanup_report(), {'candidates': [], 'kept': []})
        bz.cleanup(self.branch, B, 7)  # A completed run is safe to retry.

    def test_cleanup_protects_identity_failures_and_integration_branches(self):
        self.branches = []
        for changes in ({'draft': True}, {'immutable': True}, {'prerelease': False},
                        {'name': 'formal release'}, {'tag_name': 'branch-zip-unmanaged'},
                        {'body': bz.marker('other', B)}, {'body': bz.marker(self.branch, B) * 2},
                        {'assets': []}, {'body': bz.marker(self.branch, None)},
                        {'body': bz.marker(self.branch, 'invalid')}):
            with self.subTest(changes=changes):
                self.old_release()
                self.releases[0].update(changes)
                self.assertEqual(bz.cleanup_report()['candidates'], [])
                bz.cleanup(self.branch, B, 7)
                self.assertTrue(self.releases)
        for branch in ('main', 'master', 'develop', self.default_branch):
            self.branch = branch
            self.old_release()
            self.assertEqual(bz.cleanup_report()['candidates'], [])
            self.assertIn('protected', bz.cleanup_report()['kept'][0]['reason'])
        self.assertFalse(any(method == 'DELETE' for method, _ in self.operations))

    def test_cleanup_holds_tag_only_missing_tag_duplicate_and_branch_filter(self):
        self.old_release()
        self.branches = []
        self.assertEqual(bz.cleanup_report('other')['candidates'], [])
        saved = copy.deepcopy(self.releases)
        self.releases *= 2
        self.assertEqual(bz.cleanup_report()['candidates'], [])
        self.releases = []
        self.assertIn('tag only', bz.cleanup_report()['kept'][0]['reason'])
        self.releases = saved
        self.tags = []
        self.assertIn('tag missing', bz.cleanup_report()['kept'][0]['reason'])
        bz.cleanup(self.branch, B, 7)
        self.assertTrue(self.releases)

    def test_cleanup_rechecks_candidate_id_sha_and_branch_after_lock(self):
        self.old_release()
        self.branches = []
        for sha, release_id in ((A, 7), (B, 8)):
            bz.cleanup(self.branch, sha, release_id)
            self.assertTrue(self.releases)
        with patch.object(bz, 'current_head', return_value=A):
            bz.cleanup(self.branch, B, 7)
        self.assertTrue(self.releases)
        self.assertFalse(any(method == 'DELETE' for method, _ in self.operations))

    def test_cleanup_recreation_between_deletes_keeps_tag_and_publisher_recovers(self):
        self.old_release()
        self.branches = []
        with patch.object(bz, 'current_head', side_effect=[None, A]):
            bz.cleanup(self.branch, B, 7)
        self.assertFalse(self.releases)
        self.assertTrue(self.tags)
        self.branches = [{'name': self.branch, 'commit': {'sha': A}}]
        bz.publish(self.branch, A, self.directory)
        self.assertEqual(bz.published_sha(self.branch, self.releases[0]), A)

    def test_cleanup_api_failure_is_not_branch_absence(self):
        self.old_release()
        self.branches = []
        for endpoint in ('branches?', 'releases?', 'git/matching-refs/', ''):
            def failed_api(path, method='GET', data=None):
                if (endpoint and path.startswith(endpoint)) or (not endpoint and path == ''):
                    raise RuntimeError('API/authentication unavailable')
                return self.api(path, method, data)
            with self.subTest(endpoint=endpoint), patch.object(bz, 'api', side_effect=failed_api):
                with self.assertRaises(RuntimeError):
                    bz.cleanup(self.branch, B, 7)
            self.assertTrue(self.releases)
        self.assertFalse(any(method == 'DELETE' for method, _ in self.operations))

    def test_cleanup_partial_failure_retries_release_but_holds_tag_only(self):
        self.old_release()
        self.branches = []
        self.fail_delete = 'releases/7'
        with self.assertRaises(RuntimeError):
            bz.cleanup(self.branch, B, 7)
        self.assertEqual(len(bz.cleanup_report()['candidates']), 1)
        self.fail_delete = 'git/refs/tags/' + bz.tag_for(self.branch)
        with self.assertRaises(RuntimeError), patch.object(bz, 'cleanup_note') as note:
            bz.cleanup(self.branch, B, 7)
        self.assertIn('unconfirmed', note.call_args.args[0]['result'])
        self.assertFalse(self.releases)
        self.assertEqual(bz.cleanup_report()['candidates'], [])
        self.assertIn('tag only', bz.cleanup_report()['kept'][0]['reason'])
        self.fail_delete = None
        bz.cleanup(self.branch, B, 7)
        self.assertTrue(self.tags)

    def test_cleanup_keeps_tag_if_external_actor_changes_it(self):
        self.old_release()
        self.branches = []
        original_api = self.api
        def changed_api(path, method='GET', data=None):
            result = original_api(path, method, data)
            if method == 'DELETE' and path == 'releases/7':
                self.tags[0]['object']['sha'] = A
            return result
        with patch.object(bz, 'api', side_effect=changed_api):
            bz.cleanup(self.branch, B, 7)
        self.assertTrue(self.tags)
        self.assertEqual(self.tags[0]['object']['sha'], A)

    def test_workflow_cleanup_is_default_branch_only_and_shares_publish_lock(self):
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/branch-zip.yml').read_text()
        self.assertEqual(workflow.count('group: ${{ matrix.tag }}'), 2)
        self.assertIn("github.event_name != 'push' && github.ref == format('refs/heads/{0}', github.event.repository.default_branch)", workflow)
        self.assertIn("if [ \"$APPLY\" != true ]; then candidates='[]'; fi", workflow)

    def test_cleanup_reads_over_100_matching_refs_once_without_pagination(self):
        self.tags = [{'ref': 'refs/tags/' + bz.tag_for(str(i)), 'object': {'sha': B}}
                     for i in range(101)]
        result = bz.cleanup_report()
        self.assertEqual(result['candidates'], [])
        self.assertEqual(len(result['kept']), 101)
        self.assertEqual([p for m, p in self.operations if p.startswith('git/matching-refs/')],
                         ['git/matching-refs/tags/branch-zip-'])

    def test_mixed_release_selection_is_order_independent_and_preserves_draft_and_old_urls(self):
        for reverse in (False, True):
            self.old_release()
            public = copy.deepcopy(self.releases[0])
            draft = dict(public, id=8, draft=True, immutable=True, body='protected unknown custody', assets=[])
            self.releases = [draft, public] if reverse else [public, draft]
            original_draft = copy.deepcopy(draft)
            self.impact.return_value = classify([('src/main/Changed.kt', ('100644',))])
            self.assertEqual(len(bz.plan()), 1)
            bz.publish(self.branch, A, self.directory)
            self.assertEqual(next(r for r in self.releases if r['id'] == 8), original_draft)
            current = next(r for r in self.releases if r['id'] == 7)
            self.assertEqual([a['name'] for a in current['assets']], [bz.asset_for(B), bz.asset_for(A)])
            self.assertEqual(bz.plan(), [])
            self.assertFalse(any(m == 'DELETE' for m, _ in self.operations))

    def test_all_drafts_reuse_newest_owned_and_retire_empty_obsolete_after_readback(self):
        self.old_release()
        older = dict(self.releases[0], id=6, draft=True, assets=[])
        newer = dict(self.releases[0], id=7, draft=True, assets=[])
        self.releases = [newer, older]
        bz.publish(self.branch, A, self.directory)
        self.assertEqual([r['id'] for r in self.releases], [7])
        self.assertFalse(self.releases[0]['draft'])
        self.assertIn(('DELETE', 'releases/6'), self.operations)
        self.assertFalse(any(m == 'POST' for m, _ in self.operations))
        self.assertEqual(bz.plan(), [])

    def test_multiple_published_and_unknown_or_immutable_selection_stop_before_write(self):
        for change in ('duplicate', 'foreign', 'immutable', 'unknown_visibility'):
            self.old_release()
            if change == 'duplicate':
                self.releases.append(dict(self.releases[0], id=8))
            elif change == 'foreign':
                self.releases[0]['body'] = ''
            elif change == 'immutable':
                self.releases[0]['immutable'] = True
            else:
                self.releases[0].pop('draft')
            with self.assertRaises(ValueError): bz.plan()
            with self.assertRaises(ValueError): bz.publish(self.branch, A, self.directory)
            self.assertFalse(any(m != 'GET' for m, _ in self.operations))

    def test_owned_draft_failures_preserve_all_resources_and_retry_without_new_draft(self):
        for stage in ('upload', 'patch'):
            self.old_release()
            self.releases[0].update(draft=True, assets=[])
            self.releases.append(dict(self.releases[0], id=6, assets=[]))
            original = copy.deepcopy(self.releases)
            self.fail_upload, self.fail_patch = stage == 'upload', stage == 'patch'
            with self.assertRaises(RuntimeError): bz.publish(self.branch, A, self.directory)
            self.assertEqual(self.releases[1], original[1])
            self.assertEqual(self.releases[0]['body'], original[0]['body'])
            self.fail_upload = self.fail_patch = False
            bz.publish(self.branch, A, self.directory)
            self.assertEqual(bz.plan(), [])
            self.assertFalse(any(m == 'POST' for m, _ in self.operations))


    def test_newest_draft_unknown_identity_never_falls_back_to_older_owned(self):
        self.old_release()
        self.releases[0]['draft'] = True
        self.releases.append(dict(self.releases[0], id=8, body='unknown owner'))
        with self.assertRaises(ValueError): bz.plan()
        with self.assertRaises(ValueError): bz.publish(self.branch, A, self.directory)
        self.assertFalse(any(m != 'GET' for m, _ in self.operations))

    def test_all_drafts_selection_is_order_independent_and_rejects_ambiguous_ids(self):
        self.old_release()
        self.releases = [dict(self.releases[0], id=i, draft=True, assets=[]) for i in (6, 8, 7)]
        for rows in (self.releases, list(reversed(self.releases))):
            self.assertEqual(bz.selected_release(self.branch, rows)['id'], 8)
        with self.assertRaises(ValueError):
            bz.selected_release(self.branch, [self.releases[0], dict(self.releases[0], body='other')])

    def test_nonempty_or_foreign_obsolete_draft_stops_without_writes(self):
        for changed in ({'assets': [{'id': 9, 'name': 'private-custody.zip'}]},
                        {'body': ''}, {'immutable': True}):
            self.old_release()
            self.releases[0].update(draft=True, assets=[])
            self.releases.append(dict(self.releases[0], id=6, **changed))
            with self.assertRaises(ValueError): bz.publish(self.branch, A, self.directory)
            self.assertFalse(any(m != 'GET' for m, _ in self.operations))

    def test_retirement_failure_does_not_delete_protected_mixed_drafts_on_retry(self):
        self.old_release()
        self.releases[0].update(draft=True, assets=[])
        self.releases += [dict(self.releases[0], id=i, assets=[]) for i in (5, 6)]
        self.fail_delete = 'releases/6'
        with self.assertRaises(RuntimeError): bz.publish(self.branch, A, self.directory)
        self.assertEqual([r['id'] for r in self.releases], [7, 6])
        self.assertEqual(bz.published_sha(self.branch, self.releases[0]), A)
        self.fail_delete = None
        self.operations.clear()
        bz.publish(self.branch, A, self.directory)
        self.assertEqual([r['id'] for r in self.releases], [7, 6])
        self.assertFalse(any(m == 'DELETE' for m, _ in self.operations))

    def test_head_move_or_published_readback_change_prevents_obsolete_deletion(self):
        for failure in ('head', 'readback'):
            self.branches[0]['commit']['sha'] = A
            self.old_release()
            self.releases[0].update(draft=True, assets=[])
            self.releases.append(dict(self.releases[0], id=6, assets=[]))
            original = self.api
            def changed(path, method='GET', data=None):
                result = original(path, method, data)
                if method == 'PATCH':
                    if failure == 'head': self.branches[0]['commit']['sha'] = B
                    else: self.releases[0]['assets'][0]['digest'] = 'sha256:' + 'f' * 64
                return result
            with patch.object(bz, 'api', side_effect=changed), self.assertRaises(ValueError):
                bz.publish(self.branch, A, self.directory)
            self.assertEqual(len(self.releases), 2)
            self.assertFalse(any(m == 'DELETE' for m, _ in self.operations))

    def test_obsolete_draft_or_set_changes_after_publish_are_not_deleted(self):
        for changed in ('draft', 'set'):
            self.old_release()
            self.releases[0].update(draft=True, assets=[])
            self.releases.append(dict(self.releases[0], id=6, assets=[]))
            original = self.api
            def external(path, method='GET', data=None):
                result = original(path, method, data)
                if method == 'PATCH':
                    if changed == 'draft': self.releases[1]['immutable'] = True
                    else: self.releases.append(dict(self.releases[1], id=9, assets=[]))
                return result
            with patch.object(bz, 'api', side_effect=external), self.assertRaises(ValueError):
                bz.publish(self.branch, A, self.directory)
            self.assertFalse(any(m == 'DELETE' for m, _ in self.operations))
