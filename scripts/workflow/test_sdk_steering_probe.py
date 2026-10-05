"""Run the actual exportable Node module against synthetic Run handles, never Cursor."""
import json
from pathlib import Path
import tempfile
import unittest

try:
    from sdk_steering_probe import inspect_sdk, offline_check
except ImportError:
    from scripts.workflow.sdk_steering_probe import inspect_sdk, offline_check


class SteeringProbeTests(unittest.TestCase):
    def test_synthetic_boundaries_and_no_provider_execution(self):
        result = offline_check()
        self.assertEqual(result['offline_checks'], ['S1', 'S2', 'S3', 'S4', 'S5', 'S6'])
        self.assertEqual(set(result['provider_cases'].values()), {'blocked'})
        self.assertIn('fake-sdk-only', result['driver_checks'])
        self.assertEqual((result['sdk_agents'], result['provider_calls']), (0, 0))

    def test_wrong_sdk_version_is_refused_before_type_inspection(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, 'package.json').write_text(json.dumps({'name': '@cursor/sdk', 'version': '0.0.0'}))
            with self.assertRaisesRegex(ValueError, 'Expected @cursor/sdk 1.0.31'):
                inspect_sdk(directory)
