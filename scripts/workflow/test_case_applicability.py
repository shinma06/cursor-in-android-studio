"""Synthetic contract revisions: immutable originals, candidate bounds and no reused PASS."""
import copy
import json
from pathlib import Path
import unittest
import tempfile

from case_applicability import POLICY, apply_revision, case_revisions, validate_applicability
from test_verification import change, observation
from verification import json_hash

BASE, SOURCE, ADOPTED, CANDIDATE = (letter * 40 for letter in 'abcd')


class ApplicabilityTests(unittest.TestCase):
    def setUp(self):
        self.old = change(35)
        self.old['cases'][0]['required_execution'] = 'computer_use'
        self.adopted = change(36)
        self.entry = {'source_pr': 75, 'source_merge': SOURCE,
                      'source_path': 'docs/verification/changes/issue-35.json',
                      'original_case_sha256': json_hash(self.old['cases'][0]),
                      'adopted_pr': 76, 'adopted_merge': ADOPTED,
                      'adopted_cases': {'36:QA-1': json_hash(self.adopted['cases'][0])},
                      'changes': {'steps': ['新条件で設定を開く', '保存して開き直す'],
                                  'expected': '採用後の保存条件を保持する'},
                      'reason': 'Synthetic adopted contract, not actual product evidence', 'qa': [100, 101]}
        self.policy = {'schema': 1, 'issue': 590, 'decision': 'https://example.invalid/adoption',
                       'cases': {'35:QA-1': self.entry}, 'scopes': {}}
        self.documents = {f'{BASE}:{POLICY}': self.policy,
                          f'{SOURCE}:docs/verification/changes/issue-35.json': self.old,
                          f'{ADOPTED}:docs/verification/changes/issue-36.json': self.adopted}
        self.ancestors = [CANDIDATE, ADOPTED, SOURCE]

    def git(self, *args):
        if args[0] == 'ls-tree':
            return ('100644 blob ' + 'e' * 40 + '\t' + args[-1]
                    if f'{args[1]}:{args[-1]}' in self.documents else '')
        if args[0] == 'show':
            return json.dumps(self.documents[args[1]], ensure_ascii=False)
        if args == ('rev-list', CANDIDATE):
            return '\n'.join(self.ancestors)
        raise AssertionError(args)

    def revisions(self, scope=None):
        return case_revisions(BASE, CANDIDATE, self.git, scope)

    def scope(self):
        def origin(issue, pr, merge, data):
            case = data['cases'][0]
            return {'old_key': f'{issue}:QA-1', 'original_merged_requirements': [
                {'source_pr': pr, 'source_merge': merge, 'original_case_sha256': json_hash(case),
                 'case': copy.deepcopy(case)}]}
        scoped = dict(copy.deepcopy(self.old['cases'][0]), id='I35-QA-1', issue=562)
        self.plan = {'original_candidate': CANDIDATE,
                     'original_provenance': {'candidate_commits': [{'commit': ADOPTED, 'pr': 76}]},
                     'case_origins': {'I35-QA-1': origin(35, 75, SOURCE, self.old),
                                      'I36-QA-1': origin(36, 76, ADOPTED, self.adopted)},
                     'acceptance': dict(change(562), cases=[scoped])}
        self.documents[f'{BASE}:docs/verification/scopes/issue-562.json'] = self.plan
        self.bind_scope()

    def bind_scope(self):
        self.policy['scopes']['562'] = {'plan_sha256': json_hash(self.plan),
                                       'original_candidate': CANDIDATE}

    def test_projection_preserves_observations_identity_execution_and_tracking(self):
        before = copy.deepcopy(self.old)
        revision = self.revisions()['35:QA-1']
        effective = apply_revision(self.old['cases'][0], revision)
        self.assertEqual(self.old, before)
        for key in set(effective) - {'steps', 'expected'}:
            self.assertEqual(effective[key], before['cases'][0][key])
        self.assertEqual(effective['required_execution'], 'computer_use')
        self.assertEqual(revision['revision'], json_hash({'policy': json_hash(self.policy), 'key': '35:QA-1', 'scope': None}))

    def test_pre_adoption_candidate_and_absent_policy_keep_original_contract(self):
        self.ancestors.remove(ADOPTED)
        self.assertEqual(self.revisions(), {})
        self.documents.pop(f'{BASE}:{POLICY}')
        self.assertEqual(self.revisions(), {})
        self.assertIs(apply_revision(self.old['cases'][0], None), self.old['cases'][0])

    def test_untrusted_head_policy_is_ignored(self):
        self.documents[f'{CANDIDATE}:{POLICY}'] = {'schema': 'untrusted'}
        self.assertEqual(set(self.revisions()), {'35:QA-1'})

    def test_changed_original_adoption_or_selected_contract_is_rejected(self):
        for document in (self.old, self.adopted):
            saved = copy.deepcopy(document)
            document['cases'][0]['expected'] = 'tampered'
            with self.assertRaisesRegex(ValueError, 'hash mismatch'):
                self.revisions()
            document.clear(); document.update(saved)
        revision = self.revisions()['35:QA-1']
        with self.assertRaisesRegex(ValueError, 'selected original'):
            apply_revision(dict(self.old['cases'][0], required_execution=None), revision)

    def test_policy_cannot_drop_execution_artifact_failures_or_add_unknown_fields(self):
        for field, value in [('required_execution', None), ('artifact', 'other'),
                             ('gpt', {'status': 'pass'}), ('history', []), ('fix_issue', None),
                             ('id', 'OTHER'), ('preconditions', 'weakened')]:
            with self.subTest(field=field):
                self.entry['changes'][field] = value
                with self.assertRaises(ValueError):
                    self.revisions()
                self.entry['changes'].pop(field)

    def test_fresh_revision_binding_is_required_and_reused_result_is_rejected(self):
        revision = self.revisions()['35:QA-1']
        result = observation()
        before = copy.deepcopy(result)
        for value in (None, 'f' * 64):
            with self.assertRaisesRegex(ValueError, 'fresh observation'):
                validate_applicability(dict(result, applicability_revision=value), revision)
        validate_applicability(dict(result, applicability_revision=revision['revision']), revision)
        with self.assertRaisesRegex(ValueError, 'fresh observation'):
            validate_applicability(dict(result, status='reused', applicability_revision=revision['revision']), revision)
        self.assertEqual(result, before)

    def test_malformed_provenance_is_rejected_at_the_policy_boundary(self):
        for field, value in [('source_merge', None), ('adopted_merge', 12),
                             ('original_case_sha256', []), ('adopted_cases', {'36:QA-1': None})]:
            saved = self.entry[field]
            self.entry[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.revisions()
            self.entry[field] = saved

    def test_scope_uses_exact_trusted_origins_without_requiring_merged_ancestry(self):
        self.scope()
        self.ancestors = []
        revisions = self.revisions(562)
        self.assertEqual(set(revisions), {'562:I35-QA-1'})
        self.assertEqual(revisions['562:I35-QA-1']['case']['id'], 'I35-QA-1')
        self.assertEqual(self.revisions(999), {})
        self.plan['original_provenance']['candidate_commits'] = []
        self.bind_scope()
        self.assertEqual(self.revisions(562), {})

    def test_scope_plan_and_embedded_original_tampering_are_rejected(self):
        self.scope()
        self.plan['acceptance']['cases'][0]['expected'] = 'changed plan'
        with self.assertRaisesRegex(ValueError, 'plan identity'):
            self.revisions(562)
        self.bind_scope()
        self.plan['case_origins']['I35-QA-1']['original_merged_requirements'][0]['case']['expected'] = 'tampered'
        self.bind_scope()
        with self.assertRaisesRegex(ValueError, 'hash/provenance'):
            self.revisions(562)

    def test_repository_policy_keeps_the_finite_seven_contracts_and_one_scope(self):
        policy = json.loads((Path(__file__).resolve().parents[2] / POLICY).read_text())
        self.assertEqual(set(policy['cases']), {'42:PROMPT-TAB-1', '205:PROMPT-TAB-FIX',
            '97:KEYS-97-DRAFT', '97:KEYS-97-RUNNING', '48:QUEUE-ORDER',
            '153:TOOLWINDOW-SELECTED-RUN', '180:COMPACT-HEADER-LAYOUT'})
        self.assertEqual(set(policy['scopes']), {'562'})


class ConsumerTests(unittest.TestCase):
    def fixture(self, environment=False):
        from test_verification import AcceptanceTests, BASE, NEW
        f = AcceptanceTests(); f.setUp()
        if environment:
            f.environment_fixture()
        original = f.documents[f'{NEW}:docs/verification/changes/issue-36.json']['cases'][0]
        entry = {'source_pr': 99, 'source_merge': NEW,
                 'source_path': 'docs/verification/changes/issue-36.json',
                 'original_case_sha256': json_hash(original),
                 'adopted_pr': 99, 'adopted_merge': NEW,
                 'adopted_cases': {'36:QA-1': json_hash(original)},
                 'changes': {'expected': '新しい保存条件を保持する'},
                 'reason': 'Synthetic contract revision', 'qa': [100]}
        f.documents[f'{BASE}:{POLICY}'] = {'schema': 1, 'issue': 590,
            'decision': 'https://example.invalid/adoption', 'cases': {'36:QA-1': entry}, 'scopes': {}}
        previous = f.git
        def git(*args):
            if args[0] == 'ls-tree':
                return ('100644 blob ' + 'a'*40 + '\t' + args[-1]
                        if f'{args[1]}:{args[-1]}' in f.documents else '')
            return previous(*args)
        f.git = git
        revision = case_revisions(BASE, NEW, f.git)['36:QA-1']
        return f, original, revision

    def render(self, f):
        from test_verification import NEW
        from verification import render_queue
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'issue-36.json'
            path.write_text(json.dumps(f.documents[f'{NEW}:docs/verification/changes/issue-36.json'], ensure_ascii=False))
            return render_queue([path], f.manifest, f.git)

    def test_gate_and_view_share_revision_and_keep_old_contract(self):
        f, original, revision = self.fixture()
        before = copy.deepcopy(original)
        with self.assertRaisesRegex(ValueError, 'fresh observation'):
            f.verify()
        output = self.render(f)
        self.assertNotIn('Case合格', output)
        self.assertIn('旧固定Caseの期待結果（履歴）: ' + original['expected'], output)
        self.assertIn('期待結果: 新しい保存条件を保持する', output)
        f.manifest['results']['36:QA-1']['applicability_revision'] = revision['revision']
        self.assertEqual(f.verify()['cases'], 1)
        self.assertIn('Case合格', self.render(f))
        self.assertEqual(original, before)
        f.manifest['results']['36:QA-1']['status'] = 'reused'
        with self.assertRaisesRegex(ValueError, 'fresh observation'):
            f.verify()
        self.assertNotIn('Case合格', self.render(f))

    def test_environment_execution_and_applicability_bindings_are_all_required(self):
        from test_verification import BASE
        f, original, revision = self.fixture(environment=True)
        result = f.manifest['results']['36:QA-1']
        result['applicability_revision'] = revision['revision']
        self.assertEqual(f.verify()['cases'], 1)
        self.assertIn('Case合格', self.render(f))
        for mutation in ({'applicability_revision': None}, {'environment_revision': None},
                         {'execution': 'manual'}, {'head': BASE}, {'artifact_sha256': 'f'*64}):
            before = copy.deepcopy(result)
            result.update(mutation)
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                f.verify()
            self.assertNotIn('Case合格', self.render(f))
            result.clear(); result.update(before)

    def test_scoped_gate_uses_the_same_revision_with_real_git_history(self):
        from test_scoped_promotion import ScopedPromotionTests
        from verification import render_queue
        f = ScopedPromotionTests(); f.setUp()
        self.addCleanup(f.doCleanups)
        f.git('checkout', '-qb', 'adopted-main', f.base)
        original = f.acceptance['cases'][0]
        adopted = change(36)['cases'][0]
        def origin(issue, pr, merge, case):
            return {'old_key': f'{issue}:QA-1', 'original_merged_requirements': [
                {'source_pr': pr, 'source_merge': merge, 'original_case_sha256': json_hash(case), 'case': case}]}
        f.plan.update(original_candidate=ADOPTED,
                      original_provenance={'candidate_commits': [{'commit': ADOPTED, 'pr': 76}]},
                      case_origins={'QA-1': origin(35, 75, SOURCE, original),
                                    'ADOPTED': origin(36, 76, ADOPTED, adopted)})
        entry = {'source_pr': 75, 'source_merge': SOURCE, 'source_path': f.case_path,
                 'original_case_sha256': json_hash(original), 'adopted_pr': 76, 'adopted_merge': ADOPTED,
                 'adopted_cases': {'36:QA-1': json_hash(adopted)},
                 'changes': {'expected': '新しい保存条件'}, 'reason': 'Synthetic scoped revision', 'qa': [100]}
        policy = {'schema': 1, 'issue': 590, 'decision': 'https://example.invalid/adoption',
                  'cases': {'35:QA-1': entry},
                  'scopes': {'35': {'plan_sha256': json_hash(f.plan), 'original_candidate': ADOPTED}}}
        f.write(f.plan_path, f.plan); f.write(POLICY, policy)
        f.base = f.commit()
        f.write('build.gradle.kts', 'modern'); f.candidate = f.commit()
        f.pr['base']['sha'] = f.base
        f.promotion.update(base=f.base, candidate=f.candidate)
        f.promotion['results']['35:QA-1']['head'] = f.candidate
        f.results_commit()
        with self.assertRaisesRegex(ValueError, 'fresh observation'):
            f.verify()
        revision = case_revisions(f.base, f.candidate, f.git, 35)['35:QA-1']
        f.promotion['results']['35:QA-1']['applicability_revision'] = revision['revision']
        f.results_commit()
        self.assertEqual(f.verify()['cases'], 1)
        output = render_queue([f.root / f.case_path], f.promotion, f.git)
        self.assertIn('期待結果: 新しい保存条件', output)
        self.assertIn('Case合格', output)
        self.assertEqual(json.loads((f.root / f.plan_path).read_text())['acceptance'], f.acceptance)


if __name__ == '__main__':
    unittest.main()
