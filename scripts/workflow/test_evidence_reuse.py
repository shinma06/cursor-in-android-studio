"""Trusted adoption, immutable originals, all-edge impact and current-load binding."""
import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import verification as v
from agent_policy import session_fingerprint, validate_review
from test_verification import CASE, change, observation
from test_agent_loop import report


class EvidenceReuseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = {k: val for k, val in os.environ.items() if not k.startswith('GIT_')}
        self.git('init', '-b', 'main')
        self.git('config', 'user.name', 'Synthetic evidence fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.case_path = 'docs/verification/changes/issue-35.json'
        self.write(self.case_path, json.dumps(change(), ensure_ascii=False))
        self.write('src/main/Product.kt', 'class Product\n')
        self.write('src/main/Other.kt', 'class Other\n')
        self.source = self.commit('source')
        self.write('src/main/Other.kt', 'class OtherChanged\n')
        self.candidate = self.commit('unrelated product correction')
        old = dict(observation(), head=self.source, at='2099-01-01T00:00:00Z')
        current = dict(observation(), head=self.candidate, artifact_sha256='f' * 64,
                       at='2099-01-02T00:00:00Z', reason='現在loadと前提の適合確認')
        self.result = {'status': 'reused', 'head': self.candidate, 'artifact_sha256': 'f' * 64,
                       'source_candidate': self.source, 'source_result': old, 'confirmation': current}
        blob = self.git('rev-parse', self.source + ':src/main/Product.kt')
        # Synthetic RFC 4122 fixture; never use a live runtime identifier.
        self.reviewer = '11111111-2222-4333-8444-555555555555'
        self.certificate = {
            'schema': 1, 'key': '35:QA-1', 'unresolved_failures': [], 'result': copy.deepcopy(self.result),
            'case_sha256': v.json_hash(CASE), 'original_result_sha256': v.json_hash(old),
            'original_file_sha256': '1' * 64, 'original_record_evidence': 'Synthetic sealed original receipt',
            'history': v.evidence_history(self.source, self.candidate, self.git),
            'review': {'status': 'approved', 'at': '2099-01-03T00:00:00Z', 'session': session_fingerprint(self.reviewer),
                       'contributors': ['codex-session-sha256:' + '2' * 64],
                       'evidence': 'Synthetic substantive original/coverage/impact assessment',
                       'origin_evidence': 'Synthetic runtime identity mapping; no actual review claimed'},
            'guarantees': {'setting-persistence': {
                'disposition': 'unaffected',
                'impact': {k: 'Synthetic relevant condition checked' for k in (
                    'source', 'callers', 'callees', 'dataflow', 'build', 'configuration',
                    'dependencies', 'fixtures', 'runtime', 'display', 'failure_attribution')},
                'components': {'src/main/Product.kt': {'source': blob, 'candidate': blob}},
                'evidence': 'Synthetic full guarantee coverage plus current load fact'}}}

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, env=self.env,
                                       stderr=subprocess.PIPE, text=True).strip()

    def write(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)

    def commit(self, title):
        self.git('add', '.')
        self.git('commit', '-m', title)
        return self.git('rev-parse', 'HEAD')

    def adopt(self):
        self.adoption_path = 'docs/verification/changes/issue-571.json'
        data = dict(change(571, False), evidence_certificates={'C1': self.certificate})
        self.acceptance = v.certificate_registration(data, 571, 'tooling', self.git)
        self.write(self.adoption_path, json.dumps(data, ensure_ascii=False))
        self.base = self.commit('synthetic trusted independent adoption')
        self.result['certificate'] = {'commit': self.base, 'path': self.adoption_path,
                                      'id': 'C1', 'sha256': v.json_hash(self.certificate)}

    def validate(self):
        return v.validate_candidate_result(self.result, '35:QA-1', CASE, self.candidate,
                                           'f' * 64, self.base, self.git)

    def test_changed_zip_can_retain_qualified_unaffected_fact(self):
        self.adopt()
        self.assertEqual(self.validate(), self.result['confirmation'])
        self.assertNotEqual(self.result['source_result']['artifact_sha256'], 'f' * 64)
        self.assertEqual(self.result['source_result'], self.certificate['result']['source_result'])

    def test_candidate_self_adoption_is_not_trusted_main(self):
        self.adopt()
        self.base = self.source
        with self.assertRaises(subprocess.CalledProcessError):
            self.validate()

    def test_missing_coverage_original_current_load_or_independence_is_rejected(self):
        original = copy.deepcopy(self.certificate)
        mutations = [lambda c: c.update(history=[]),
                     lambda c: c.update(original_file_sha256=''),
                     lambda c: c.update(original_result_sha256='0' * 64),
                     lambda c: c['result']['source_result'].update(status='fail'),
                     lambda c: c['result']['source_result'].update(status='reused'),
                     lambda c: c['result']['confirmation'].update(loaded_identity=''),
                     lambda c: c['result']['confirmation'].update(at='2000-01-01T00:00:00Z'),
                     lambda c: c['review'].update(session=c['review']['contributors'][0]),
                     lambda c: c['review'].update(origin_evidence=''),
                     lambda c: c.update(guarantees={}),
                     lambda c: c['guarantees']['setting-persistence']['impact'].pop('dependencies')]
        for mutate in mutations:
            with self.subTest(mutation=mutate):
                self.certificate = copy.deepcopy(original)
                mutate(self.certificate)
                with self.assertRaises(ValueError):
                    v.evidence_certificate(self.certificate, self.git)

    def test_exact_adopted_result_case_and_artifact_cannot_be_rebound(self):
        self.adopt()
        original = copy.deepcopy(self.result)
        mutations = [lambda r: r.update(artifact_sha256='9' * 64),
                     lambda r: r['source_result'].update(observer='another observer'),
                     lambda r: r['confirmation'].update(reason='different current claim'),
                     lambda r: r['certificate'].update(sha256='0' * 64),
                     lambda r: r['certificate'].update(path='../outside.json')]
        for mutate in mutations:
            self.result = copy.deepcopy(original)
            mutate(self.result)
            with self.assertRaises(ValueError):
                self.validate()

    def test_component_revert_and_case_revert_are_not_hidden(self):
        for path, content in [('src/main/Product.kt', 'class Changed\n'),
                              (self.case_path, json.dumps(dict(change(), cases=[dict(CASE, expected='別契約')])) )]:
            with self.subTest(path=path):
                before = (self.root / path).read_text()
                self.write(path, content)
                self.commit('changed')
                self.write(path, before)
                self.candidate = self.commit('reverted')
                c = copy.deepcopy(self.certificate)
                c['result']['head'] = self.candidate
                c['result']['confirmation']['head'] = self.candidate
                c['history'] = v.evidence_history(self.source, self.candidate, self.git)
                with self.assertRaises(ValueError):
                    v.evidence_certificate(c, self.git)

    def test_registration_requires_actual_independent_fixed_assessment(self):
        self.adopt()
        assessed = dict(report(), session=self.reviewer,
                        certificate_revision=self.acceptance['certificate_revision'],
                        certificate_assessment='Synthetic full originals/conditions/guarantees evaluated',
                        origin_evidence='Synthetic actual runtime identity receipt')
        expected = {'head': assessed['head'], 'base': assessed['base']}
        validate_review(assessed, expected, self.acceptance)
        for update in ({'certificate_revision': '0' * 64}, {'certificate_assessment': ''},
                       {'session': 'another-role-alias'}):
            with self.assertRaises(ValueError):
                validate_review(dict(assessed, **update), expected, self.acceptance)
        for update in ({'certificate_reviewers': self.acceptance['certificate_reviewers'] + ['codex-session-sha256:' + '3' * 64]},
                       {'certificate_contributors': [session_fingerprint(self.reviewer)]},
                       {'certificate_contributors': []}):
            with self.subTest(acceptance=update), self.assertRaises(ValueError):
                validate_review(assessed, expected, dict(self.acceptance, **update))
        with self.assertRaises(ValueError):
            v.certificate_registration(dict(change(), evidence_certificates={'C1': self.certificate}),
                                       35, 'develop', self.git)

    def certificate_bundle(self):
        second_case = dict(CASE, id='QA-2', expected='Synthetic second guarantee')
        self.write(self.case_path, json.dumps(dict(change(), cases=[CASE, second_case])))
        source = self.commit('synthetic two-case source')
        self.write('src/main/Other.kt', 'class OtherChangedAgain\n')
        candidate = self.commit('synthetic unrelated candidate')
        certificates = {}
        for name, case in [('C1', CASE), ('C2', second_case)]:
            c = copy.deepcopy(self.certificate)
            c.update(key='35:' + case['id'], case_sha256=v.json_hash(case),
                     history=v.evidence_history(source, candidate, self.git))
            c['result'].update(head=candidate, source_candidate=source)
            c['result']['source_result']['head'] = source
            c['result']['confirmation']['head'] = candidate
            c['original_result_sha256'] = v.json_hash(c['result']['source_result'])
            certificates[name] = c
        return certificates

    def test_complete_bundle_requires_one_common_independent_reviewer(self):
        certificates = self.certificate_bundle()
        certificates['C2']['review']['contributors'] = ['codex-session-sha256:' + '4' * 64]
        data = dict(change(571, False), evidence_certificates=certificates)
        accepted = v.certificate_registration(data, 571, 'tooling', self.git)
        self.assertEqual(accepted['certificate_reviewers'], [session_fingerprint(self.reviewer)])
        self.assertEqual(accepted['certificate_contributors'], sorted(
            certificates['C1']['review']['contributors'] + certificates['C2']['review']['contributors']))
        assessed = dict(report(), session=self.reviewer,
                        certificate_revision=accepted['certificate_revision'],
                        certificate_assessment='Synthetic whole-bundle original/origin assessment',
                        origin_evidence='Synthetic actual runtime receipt')
        validate_review(assessed, {'head': assessed['head'], 'base': assessed['base']}, accepted)
        assessed['certificate_revision'] = '0' * 64
        with self.assertRaises(ValueError):
            validate_review(assessed, {'head': assessed['head'], 'base': assessed['base']}, accepted)

    def test_mixed_reviewers_and_cross_certificate_contribution_are_rejected(self):
        certificates = self.certificate_bundle()
        certificates['C2']['review']['session'] = 'codex-session-sha256:' + '3' * 64
        for contributors in (certificates['C2']['review']['contributors'], [session_fingerprint(self.reviewer)]):
            certificates['C2']['review']['contributors'] = contributors
            with self.subTest(contributors=contributors), self.assertRaises(ValueError):
                v.certificate_registration(dict(change(571, False), evidence_certificates=certificates),
                                           571, 'tooling', self.git)


if __name__ == '__main__':
    unittest.main()
