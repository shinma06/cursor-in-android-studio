import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import agent_loop as al
from agent_policy import binding, next_action
from handoff_registry import register, resolve
from verification import metadata, validate_change, verify_pr, render_queue
from test_agent_loop import pr_data, report, HEAD, BASE, NEW
import test_agent_loop as tal


CASE = {'id': 'QA-1', 'change': '日本語表示', 'preconditions': '固定候補ZIPをロードする',
        'steps': ['設定を開く', '保存して開き直す'], 'expected': '設定を保持する',
        'provenance': ['https://github.com/example/issues/1'], 'next_action': '人間がまとめて確認',
        'gpt': {'status': 'blocked', 'reason': '環境が画面を取得できない'},
        'human': {'status': 'pending', 'reason': '候補未確認'}, 'fix_issue': None, 'fix_pr': None,
        'recheck': '修正後は新候補buildで再確認'}


def change(issue=35, gui=True):
    return {'schema': 1, 'issue': issue, 'gui_required': gui, 'reason': '設定表示の変更',
            'cli_checks': ['./gradlew test: success'], 'cases': [copy.deepcopy(CASE)] if gui else []}


def observation():
    return {'status': 'pass', 'head': NEW, 'artifact_sha256': 'e' * 64, 'actor': 'human',
            'observer': 'reviewer-1', 'at': '2026-09-07T10:00:00+09:00', 'evidence': 'https://example.invalid/evidence',
            'loaded_identity': 'ZIP and loaded JAR verified', 'reason': '設定が保持された'}


class AcceptanceTests(unittest.TestCase):
    def setUp(self):
        self.pr = pr_data()
        self.pr['body'] = self.pr['body'].replace('tooling', 'promotion')
        self.manifest = {'schema': 1, 'base': BASE, 'candidate': NEW, 'artifact_sha256': 'e' * 64,
                         'changes': [{'commit': NEW, 'pr': 99}], 'results': {'36:QA-1': observation()}}
        self.source = copy.deepcopy(self.pr)
        self.source.update(merged=True, merge_commit_sha=NEW)
        self.source['base']['ref'] = 'develop'
        self.source['body'] = ('Issue: #36\nGUI: required\nIntegration: develop\n'
                               'Verification: docs/verification/changes/issue-36.json')
        self.source['head']['ref'] = 'codex/36-change'
        self.documents = {f'{HEAD}:docs/verification/changes/issue-35.json': change(gui=False),
                          f'{NEW}:docs/verification/changes/issue-36.json': change(36)}
        self.range = [NEW]
        self.product_diff = []

    def git(self, *args):
        if args[0] == 'show':
            if args[1].endswith(':docs/verification/promotion.json'):
                return json.dumps(self.manifest)
            return json.dumps(self.documents[args[1]])
        if args[0] == 'diff':
            if args[2] == NEW:
                return '\n'.join(self.product_diff + ['docs/verification/promotion.json'])
            return 'docs/verification/promotion.json'
        if args[:2] == ('rev-list', '--parents'):
            return args[-1] + ' ' + BASE
        if args[0] == 'rev-list':
            return '\n'.join(self.range)
        if args[0] == 'fetch':
            return ''
        if args[0] == 'merge-base':
            return ''
        raise AssertionError(args)

    def api(self, path):
        return {'object': {'sha': NEW}} if path.startswith('git/ref') else self.source

    def verify(self):
        return verify_pr(self.pr, self.api, self.git)

    def test_all_fixed_candidate_cases_allow_gpt_or_human(self):
        for actor in ('gpt', 'human'):
            self.manifest['results']['36:QA-1']['actor'] = actor
            self.assertEqual(self.verify()['cases'], 1)

    def test_one_pass_cannot_promote_two_commits(self):
        self.range.append('f' * 40)
        with self.assertRaisesRegex(ValueError, 'EVERY'):
            self.verify()

    def test_missing_required_case_rejected(self):
        self.documents[f'{NEW}:docs/verification/changes/issue-36.json']['cases'].append(dict(CASE, id='QA-2'))
        with self.assertRaisesRegex(ValueError, 'ALL'):
            self.verify()

    def test_old_build_unknown_blocked_failed_or_unsigned_results_rejected(self):
        original = copy.deepcopy(self.manifest)
        for patch_value in ({'head': HEAD}, {'artifact_sha256': 'a' * 64}, {'status': 'blocked'},
                            {'status': 'pending'}, {'status': 'fail'}, {'observer': ''}, {'at': ''}, {'evidence': ''}):
            self.manifest = copy.deepcopy(original)
            self.manifest['results']['36:QA-1'].update(patch_value)
            with self.assertRaises(ValueError):
                self.verify()

    def test_untested_product_change_in_promotion_rejected(self):
        self.product_diff = ['src/main/kotlin/Product.kt']
        with self.assertRaisesRegex(ValueError, 'tree differs'):
            self.verify()

    def test_wrong_source_merge_base_or_moving_develop_rejected(self):
        self.source['merge_commit_sha'] = HEAD
        with self.assertRaises(ValueError): self.verify()
        self.source['merge_commit_sha'] = NEW
        self.manifest['base'] = HEAD
        with self.assertRaises(ValueError): self.verify()
        self.manifest['base'] = BASE
        original_git = self.git
        self.git = lambda *args: (_ for _ in ()).throw(ValueError('not ancestor')) if args == ('merge-base', '--is-ancestor', NEW, 'f' * 40) else original_git(*args)
        self.api = lambda _: {'object': {'sha': 'f' * 40}}
        with self.assertRaisesRegex(ValueError, 'not ancestor'): self.verify()

    def test_develop_allows_pending_blocked_and_tracked_product_fail(self):
        self.pr['base']['ref'] = 'develop'
        self.pr['body'] = self.pr['body'].replace('promotion', 'develop').replace('GUI: not-required', 'GUI: required')
        for status in ('pending', 'blocked', 'fail'):
            data = change()
            data['cases'][0]['gpt']['status'] = status
            data['cases'][0]['fix_issue'] = 100 if status == 'fail' else None
            self.documents[f'{HEAD}:docs/verification/changes/issue-35.json'] = data
            self.assertEqual(self.verify()['mode'], 'develop')
        data['cases'][0]['fix_issue'] = None
        with self.assertRaisesRegex(ValueError, 'dedicated fix'): self.verify()

    def test_missing_matrix_repro_steps_or_no_gui_contradiction_rejected(self):
        for mutation in ({'cases': []}, {'gui_required': False}):
            with self.assertRaises(ValueError): validate_change(dict(change(), **mutation), 35, True)
        data = change(); data['cases'][0]['steps'] = []
        with self.assertRaises(ValueError): validate_change(data, 35, True)

    def test_direct_main_product_no_gui_route_rejected(self):
        self.pr['body'] = self.pr['body'].replace('promotion', 'tooling')
        with self.assertRaises(ValueError):
            verify_pr(self.pr, self.api, lambda *args: 'src/main/Product.kt' if args[0] == 'diff' else self.git(*args))

    def test_target_change_invalidates_review(self):
        bound = binding(self.pr, {'body': ''})
        other = copy.deepcopy(self.pr); other['base']['ref'] = 'develop'
        self.assertNotEqual(bound, binding(other, {'body': ''}))
        state = {'review': report(), 'binding': bound}
        self.assertEqual(next_action(state, bound, 'failure', True, {'allowed': True}), 'ci-failed')
        self.assertEqual(next_action(state, bound, 'success', True, {'allowed': True}), 'merge')
        self.assertEqual(next_action(state, bound, 'success', False, {'allowed': False}), 'acceptance-wait')

    def test_public_queue_contains_steps_and_never_promotes_historical_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'case.json'; path.write_text(json.dumps(change()))
            output = render_queue([path])
            self.assertIn('1. 設定を開く', output)
            self.assertIn('固定候補のpass未登録', output)


class RegistryTests(unittest.TestCase):
    def test_opaque_roundtrip_and_tampering_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            public = register(tmp, {'pr': 12, 'scope': ['src/'], 'owner': 'agent-loop'}, '/private/worktree', 'private-host')
            self.assertNotIn('private', json.dumps(public))
            self.assertEqual(resolve(tmp, public, 'private-host')['source'], '/private/worktree')
            for altered, host in ((dict(public, scope=['/']), 'private-host'), (public, 'other-host'),
                                  (dict(public, registry_id='../escape'), 'private-host')):
                with self.assertRaises(ValueError): resolve(tmp, altered, host)
            path = Path(tmp) / 'registry' / (public['registry_id'] + '.json')
            path.chmod(0o644)
            with self.assertRaises(ValueError): resolve(tmp, public, 'private-host')
            path.unlink()
            with self.assertRaises(ValueError): resolve(tmp, public, 'private-host')

    def test_unknown_new_registry_cannot_fall_back_to_legacy_writer(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(al, 'git', return_value=tmp):
            gh = Mock()
            gh.comments.return_value = [
                {'id': 1, 'user': {'login': al.OWNER}, 'body': al.pack(al.HANDOFF, {'host': al.HOST, 'source': '/old'})},
                {'id': 2, 'user': {'login': al.OWNER}, 'body': al.pack(al.HANDOFF_V2, {'version': 2, 'registry_id': 'a' * 32})}]
            loop = al.Loop(gh, tmp)
            with self.assertRaises(ValueError): loop.load(12)

    def test_no_local_paths_or_host_in_public_state_and_errors(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(al, 'git', return_value=tmp):
            loop = al.Loop(Mock(), tmp)
            data = loop.public_state({'source': '/private/worktree', 'host': al.HOST,
                                      'next': f'failed {tmp}/secret.log on {al.HOST}'})
            self.assertNotIn(tmp, json.dumps(data)); self.assertNotIn(al.HOST, json.dumps(data))
            reason = loop.private_error(RuntimeError('/private/token.log raw content'))
            self.assertNotIn('raw content', reason)

    def test_cleanup_explicitly_protects_main_and_develop(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(al, 'git', return_value=tmp):
            loop = al.Loop(Mock(), tmp)
            for branch in ('main', 'master', 'develop'):
                with self.assertRaisesRegex(ValueError, 'never be cleaned'):
                    loop._cleanup_resources(pr_data(), {'branch': branch})


class DevelopLoopTests(unittest.TestCase):
    setUp = tal.LoopTests.setUp
    # Reuse the real transition fixture without duplicating inherited scenario discovery.
    def test_develop_merge_keeps_issue_open(self):
        self.gh.pull['base']['ref'] = 'develop'
        self.gh.pull['body'] = self.gh.pull['body'].replace('tooling', 'develop')
        h, _, _, _ = self.loop.load(36)
        h['target'] = 'develop'
        self.gh.messages[36][0]['body'] = al.pack(al.HANDOFF, h)
        original_git = al.git.side_effect
        al.git.side_effect = lambda *args, **kw: BASE if args[:2] == ('rev-parse', 'origin/develop') else original_git(*args, **kw)
        self.loop.acceptance.return_value = {'allowed': True, 'mode': 'develop', 'cases': 1}
        self.loop.tick(36)
        self.assertEqual(self.loop.tick(36)['phase'], 'done')
        self.assertEqual(self.gh.issues[35]['state'], 'open')
        self.assertEqual(self.gh.issues[1]['body'], '- [ ] #35 task')
