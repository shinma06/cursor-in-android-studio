"""Fixed-candidate QA instructions use trusted contracts without changing old evidence."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from case_applicability import POLICY, case_revisions
from qa_document import render_document
from test_verification import change
from verification import ENVIRONMENT, COMPATIBILITY, json_hash

CLI = Path(__file__).with_name('verification.py')


class CandidateDocumentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git('init', '-q', '-b', 'main')
        self.git('config', 'user.name', 'Synthetic QA fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.change = change()
        self.case = self.change['cases'][0]
        self.case.update(required_execution='computer_use', artifact='swing_probe', fix_issue=404)
        self.case['gpt'] = {'status': 'fail', 'reason': '過去の合成FAIL（製品観察ではない）'}
        self.case_path = self.root / 'docs/verification/changes/issue-35.json'
        self.write(self.case_path, self.change)
        adopted_case = change(36)
        self.write(self.root / 'docs/verification/changes/issue-36.json', adopted_case)
        self.source = self.commit()
        self.write(self.root / 'fixture.txt', 'adopted')
        self.adopted = self.commit()
        self.entry = {'source_pr': 75, 'source_merge': self.source,
                      'source_path': 'docs/verification/changes/issue-35.json',
                      'original_case_sha256': json_hash(self.case), 'adopted_pr': 76,
                      'adopted_merge': self.adopted,
                      'adopted_cases': {'36:QA-1': json_hash(adopted_case['cases'][0])},
                      'changes': {'steps': ['現在の設定を開く'], 'expected': '現在の保存条件を保持する'},
                      'reason': 'Synthetic adopted condition', 'qa': [100]}
        self.policy = {'schema': 1, 'issue': 590, 'decision': 'https://example.invalid/adoption',
                       'cases': {'35:QA-1': self.entry}, 'scopes': {}}
        self.write(self.root / POLICY, self.policy)
        self.base = self.commit()
        self.git('update-ref', 'refs/remotes/origin/main', self.base)
        self.write(self.root / 'fixture.txt', 'candidate')
        self.candidate = self.commit()
        self.promotion = {'schema': 1, 'base': self.base, 'candidate': self.candidate,
                          'artifact_sha256': 'e' * 64, 'artifacts': {'swing_probe': 'f' * 64}, 'results': {}}
        self.manifest = self.root / 'promotion.private.json'
        self.output = self.root / 'current.md'
        self.write(self.manifest, self.promotion)

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, stderr=subprocess.PIPE, text=True).strip()

    def write(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False) if isinstance(value, dict) else value)

    def commit(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'Synthetic fixture state')
        return self.git('rev-parse', 'HEAD')

    def cli(self, *args):
        return subprocess.run([sys.executable, str(CLI), *map(str, args)], cwd=self.root,
                              capture_output=True, text=True)

    def test_real_cli_projects_current_table_and_keeps_original_failures_and_inputs(self):
        original, manifest = self.case_path.read_bytes(), self.manifest.read_bytes()
        result = self.cli(self.case_path, '--promotion', self.manifest, '--qa-document', '--output', self.output)
        self.assertEqual(result.returncode, 0, result.stderr)
        document = self.output.read_text()
        front = document.split('<details>', 1)[0]
        self.assertIn('現在の設定を開く', front)
        self.assertIn('現在の保存条件を保持する', front)
        self.assertIn('Computer Use必須', front)
        self.assertNotIn(self.case['expected'], front)
        self.assertIn('元期待結果（履歴）: ' + self.case['expected'], document)
        self.assertIn(self.case['gpt']['reason'], document)
        revision = case_revisions(self.base, self.candidate, self.git)['35:QA-1']['revision']
        self.assertIn('applicability_revision: ' + revision, document)
        self.assertIn('QA-1 の対象成果物: swing_probe', document)
        self.assertIn('f' * 64, document)
        self.assertNotIn('Case合格（全範囲gateは別途必要）', document)
        self.assertEqual(self.case_path.read_bytes(), original)
        self.assertEqual(self.manifest.read_bytes(), manifest)
        queue = self.cli(self.case_path, '--promotion', self.manifest)
        self.assertEqual(queue.returncode, 0, queue.stderr)
        self.assertIn('期待結果: 現在の保存条件を保持する', queue.stdout)

    def test_cli_rejects_missing_or_multiple_inputs_and_untrusted_candidate_base_without_overwrite(self):
        self.output.write_text('retained guide')
        for args in [(self.case_path, '--qa-document'),
                     (self.case_path, self.case_path, '--promotion', self.manifest, '--qa-document')]:
            result = self.cli(*args, '--output', self.output)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(self.output.read_text(), 'retained guide')
        self.promotion['base'] = self.candidate
        self.write(self.manifest, self.promotion)
        result = self.cli(self.case_path, '--promotion', self.manifest, '--qa-document', '--output', self.output)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('trusted main first-parent', result.stderr)
        self.assertEqual(self.output.read_text(), 'retained guide')
        self.promotion['base'] = self.base
        self.promotion['artifacts']['swing_probe'] = 'not-a-hash'
        self.write(self.manifest, self.promotion)
        result = self.cli(self.case_path, '--promotion', self.manifest, '--qa-document', '--output', self.output)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('valid artifact hashes', result.stderr)
        self.assertEqual(self.output.read_text(), 'retained guide')

    def test_scoped_document_requires_both_environment_and_applicability_and_rejects_wrong_sdk(self):
        environment = {'build': 'AI-262.9437.185.2621.16467767', 'java_version': '25.0.3',
                       'jvm_target': '25', 'class_major': 69}
        def origin(key, pr, merge, case):
            return {'old_key': key, 'original_merged_requirements': [
                {'source_pr': pr, 'source_merge': merge, 'verification': f'docs/verification/changes/issue-{key.split(":")[0]}.json',
                 'original_case_sha256': json_hash(case), 'case': case}]}
        adopted = change(36)['cases'][0]
        scoped_change = copy.deepcopy(self.change)
        scoped_change['cases'][0]['preconditions'] = 'Rabbitの固定fixture'
        plan = {'original_candidate': self.adopted,
                'original_provenance': {'candidate_commits': [{'commit': self.adopted, 'pr': 76}]},
                'case_origins': {'QA-1': origin('35:QA-1', 75, self.source, self.case),
                                 'ADOPTED': origin('36:QA-1', 76, self.adopted, adopted)},
                'acceptance': scoped_change, 'environment_cases': {'QA-1': '35:QA-1'}}
        env = {'schema': 1, 'issue': 479, 'decision': 'https://example.invalid/environment',
               'migration_commit': self.adopted, 'environment': environment,
               'cases': {'35:QA-1': {k: self.entry[k] for k in ('source_pr', 'source_merge', 'source_path', 'original_case_sha256')}}}
        env['cases']['35:QA-1']['preconditions'] = 'Rabbitの固定fixture'
        plan['environment_revision'] = json_hash(env)
        self.policy['scopes']['35'] = {'plan_sha256': json_hash(plan), 'original_candidate': self.adopted}
        self.write(self.root / POLICY, self.policy)
        self.write(self.root / ENVIRONMENT, env)
        self.write(self.root / 'docs/verification/scopes/issue-35.json', plan)
        sdk = {'compile': {'target': 'rabbit1', 'jvm_target': '25', 'class_major': 69},
               'targets': {'rabbit1': {'build': environment['build'], 'java_version': '25.0.3'}}}
        self.write(self.root / COMPATIBILITY, sdk)
        self.base = self.commit(); self.git('update-ref', 'refs/remotes/origin/main', self.base)
        self.write(self.root / 'fixture.txt', 'scoped candidate'); self.candidate = self.commit()
        self.promotion.update(base=self.base, candidate=self.candidate, scope='main')
        before = copy.deepcopy(scoped_change)
        document = render_document(scoped_change, 'https://example.invalid/original', self.promotion, self.git)
        self.assertIn('environment_revision: ' + json_hash(env), document)
        revision = case_revisions(self.base, self.candidate, self.git, 35)['35:QA-1']['revision']
        self.assertIn('applicability_revision: ' + revision, document)
        self.assertIn('Rabbitの固定fixture', document.split('<details>', 1)[0])
        self.assertEqual(scoped_change, before)
        sdk['compile']['class_major'] = 68
        self.write(self.root / COMPATIBILITY, sdk); self.promotion['candidate'] = self.commit()
        with self.assertRaisesRegex(ValueError, 'SDK differs'):
            render_document(scoped_change, 'https://example.invalid/original', self.promotion, self.git)


if __name__ == '__main__':
    unittest.main()
