"""Original NG cannot be hidden by labels, metadata-only fixes or a partial retest."""
import copy
import json
import unittest

import verification as v
from agent_policy import session_fingerprint, validate_review
import test_evidence_reuse as ter
from test_agent_loop import pr_data, report
from test_verification import CASE, change, observation


class RepairTests(unittest.TestCase):
    git = ter.EvidenceReuseTests.git
    write = ter.EvidenceReuseTests.write
    commit = ter.EvidenceReuseTests.commit
    def setUp(self):
        ter.EvidenceReuseTests.setUp(self)
        failed = dict(copy.deepcopy(CASE), fix_issue=571,
                      gpt={'status': 'fail', 'reason': '元の実観察で保存に失敗'})
        self.write(self.case_path, json.dumps(dict(change(), cases=[failed]), ensure_ascii=False))
        self.failed_source = self.commit('preserved original NG')
        self.base = self.failed_source
        self.write('src/main/Product.kt', 'class CorrectedProduct\n')
        self.corrected = self.commit('actual correction')
        old = dict(observation(), status='fail', head=self.failed_source, actor='gpt',
                   at='2099-01-01T00:00:00Z', reason='原失敗')
        self.record = {'schema': 1, 'fix_issue': 571, 'failures': {'NG1': {
            'case': '35:QA-1', 'source_candidate': self.failed_source,
            'source_case_sha256': v.json_hash(failed), 'original_result': old,
            'guarantees': {'persistence': '保存後に設定が失われる'}}}}
        self.data = dict(change(571, False), repair={
            'candidate': self.corrected, 'artifacts': {'plugin': 'f' * 64},
            'failure_record_sha256': v.json_hash(self.record),
            'contributors': ['codex-session-sha256:' + '2' * 64],
            'origin_evidence': 'Synthetic sealed runtime mapping, actual review unperformed',
            'results': {'NG1': {
                'observation': dict(observation(), head=self.corrected, artifact_sha256='f' * 64,
                                    at='2099-01-02T00:00:00Z'),
                'coverage': {'persistence': 'Synthetic faithful correction regression/native glue'},
                'method': 'Synthetic method covers the original failed guarantee'}}})
        self.write('docs/verification/changes/issue-571.json', json.dumps(self.data, ensure_ascii=False))
        self.head = self.commit('only result metadata after frozen correction')
        self.pr = pr_data()
        self.pr['body'] = self.pr['body'].replace('#35', '#571').replace('issue-35', 'issue-571').replace('tooling', 'develop')
        self.pr['base'].update(ref='develop', sha=self.base)
        self.pr['head'].update(sha=self.head, ref='codex/571-fix-persistence')
        self.issue = {'labels': ['type:bug'], 'body': 'Acceptance repair: 123'}

    def api(self, path):
        self.assertEqual(path, 'issues/comments/123')
        return {'issue_url': 'https://api.github.com/repos/example/project/issues/571',
                'body': '<!-- acceptance-repair:v1 -->\n' + json.dumps(self.record)}

    def verify(self):
        return v.verify_repair(self.pr, self.issue, self.data, self.api, self.git)

    def test_full_original_ng_retest_and_actual_independent_assessment(self):
        accepted = self.verify()
        assessed = dict(report(), repair_revision=accepted['repair_revision'],
                        repair_assessment='Synthetic full guarantee and original failure assessment',
                        session=self.reviewer, origin_evidence='Synthetic runtime turn receipt')
        validate_review(assessed, {'head': assessed['head'], 'base': assessed['base']}, accepted)
        for mutation in ({'repair_assessment': ''}, {'repair_revision': '0' * 64},
                         {'session': accepted['repair_contributors'][0]}):
            with self.assertRaises(ValueError):
                validate_review(dict(assessed, **mutation), {'head': assessed['head'], 'base': assessed['base']}, accepted)

    def test_none_or_relabel_cannot_hide_preserved_original_failure(self):
        self.data.pop('repair')
        for labels in (['type:bug'], ['type:feature']):
            self.issue.update(labels=labels, body='Acceptance repair: none')
            with self.assertRaisesRegex(ValueError, 'cannot be hidden'):
                self.verify()

    def test_partial_failed_guarantee_stale_hash_or_old_artifact_blocks(self):
        original = copy.deepcopy(self.data)
        for mutate in (lambda r: r.update(failure_record_sha256='0' * 64),
                       lambda r: r['results']['NG1'].update(coverage={}),
                       lambda r: r['results']['NG1']['observation'].update(artifact_sha256='e' * 64),
                       lambda r: r.update(candidate=self.base)):
            self.data = copy.deepcopy(original)
            mutate(self.data['repair'])
            with self.assertRaises(ValueError):
                self.verify()


if __name__ == '__main__':
    unittest.main()
