import copy
import tempfile
import unittest
from pathlib import Path
from loop import validate


class EvidenceGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        (self.base / 'capture.txt').write_text('Observed UI and installation transcript')
        self.plan = {'run': 'test', 'required_cases': ['plugin:MV-001']}
        self.data = {
            'run': 'test',
            'build': {'source_head': 'a' * 40, 'source_clean': True, 'zip_sha256': 'b' * 64,
                      'installed_identity_evidence': 'capture.txt', 'build_log': 'capture.txt',
                      'built_at': '2026-09-05T00:00:00Z'},
            'environment': dict.fromkeys(('ide', 'cursor', 'cli_path', 'cli_version', 'model',
                                           'permission', 'sandbox', 'worktree'), 'recorded'),
            'cases': [{'id': 'MV-001', 'surface': 'plugin', 'status': 'pass',
                       'method': 'computer-use', 'expected': 'reply', 'actual': 'reply visible',
                       'build_source_head': 'a' * 40, 'observer': 'GPT', 'observed_at': '2026-09-06T00:00:00Z', 'evidence': 'capture.txt'}]}

    def test_complete_structure(self):
        self.assertEqual([], validate(self.data, self.base, self.plan))

    def test_cli_cannot_substitute_for_gui(self):
        self.data['cases'][0]['method'] = 'cli'
        self.assertTrue(validate(self.data, self.base, self.plan))

    def test_each_incomplete_status_is_rejected(self):
        for status in ('pending', 'blocked', 'fail', 'merged', 'skipped'):
            with self.subTest(status=status):
                self.data['cases'][0]['status'] = status
                self.assertTrue(validate(self.data, self.base, self.plan))

    def test_missing_evidence_and_install_identity(self):
        for key in ('evidence',):
            self.data['cases'][0][key] = 'missing.png'
        self.data['build']['installed_identity_evidence'] = ''
        self.assertGreaterEqual(len(validate(self.data, self.base, self.plan)), 2)

    def test_cursor_alone_is_not_plugin_verification(self):
        self.data['cases'][0]['surface'] = 'cursor'
        self.assertTrue(validate(self.data, self.base, self.plan))

    def test_duplicate_and_dirty_build_rejected(self):
        self.data['cases'].append(copy.deepcopy(self.data['cases'][0]))
        self.data['build']['source_clean'] = False
        self.assertGreaterEqual(len(validate(self.data, self.base, self.plan)), 2)

    def test_evidence_must_stay_in_run(self):
        self.data['cases'][0]['evidence'] = '../other-run/capture.txt'
        self.assertTrue(validate(self.data, self.base, self.plan))

    def test_missing_planned_case_rejected(self):
        self.plan['required_cases'].append('plugin:MV-023')
        self.assertTrue(validate(self.data, self.base, self.plan))

    def test_stale_observation_and_wrong_build_rejected(self):
        self.data['cases'][0]['observed_at'] = '2026-09-04T00:00:00Z'
        self.data['cases'][0]['build_source_head'] = 'c' * 40
        self.assertGreaterEqual(len(validate(self.data, self.base, self.plan)), 2)

    def test_invalid_tool_reference_rejected(self):
        self.data['cases'][0]['evidence'] = 'tool:aaaaaaaa'
        self.assertTrue(validate(self.data, self.base, self.plan))

    def test_invalid_shape_returns_errors(self):
        self.assertTrue(validate([], self.base, self.plan))
        self.data['build'] = []
        self.assertTrue(validate(self.data, self.base, self.plan))


if __name__ == '__main__':
    unittest.main()
