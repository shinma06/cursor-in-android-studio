"""Fail-closed verification: missing results, changed bytes, and dependency errors."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import plugin_compatibility as pc


class CompatibilityTest(unittest.TestCase):
    def test_verification_cannot_pass_incomplete_or_unreviewed_reports(self):
        policy = json.loads(pc.POLICY.read_text())
        target = policy['targets']['quail1']
        manifest = {'identity': {'plugin.version': '1.0.0', 'classes': 427}}
        log = 'Scheduled verifications (1):\nFinished 1 of 1 verifications'
        with tempfile.TemporaryDirectory() as temp:
            reports = Path(temp)
            directory = reports / target['build'] / 'plugins' / pc.PLUGIN_ID / '1.0.0'
            directory.mkdir(parents=True)
            files = {'verification-verdict.txt': 'Compatible.',
                     'telemetry.txt': 'Verified classes in plugin artifact: 427\n',
                     'dependencies.txt': pc.PLUGIN_ID + ':1.0.0\n'}
            for name, text in files.items():
                (directory / name).write_text(text)
            self.assertEqual(pc.check_reports(reports, log, target, manifest, policy)['verified_classes'], 427)
            for category in ('deprecated', 'experimental', 'internal'):
                (directory / 'verification-verdict.txt').write_text(f'Compatible. 1 usage of {category} API.')
                with self.assertRaisesRegex(ValueError, 'Missing API detail report'):
                    pc.check_reports(reports, log, target, manifest, policy)
            (directory / 'verification-verdict.txt').write_text('Compatible. 1 usage of scheduled for removal API and 0 usages of deprecated API.')
            with self.assertRaisesRegex(ValueError, 'Missing API detail report'):
                pc.check_reports(reports, log, target, manifest, policy)
            (directory / 'verification-verdict.txt').write_text(files['verification-verdict.txt'])
            for name, text in [('verification-verdict.txt', ''), ('verification-verdict.txt', 'Unavailable'),
                               ('verification-verdict.txt', 'Compatible. Unknown result.'),
                               ('telemetry.txt', 'Verified classes in plugin artifact: 0\n'),
                               ('dependencies.txt', 'other-plugin:1.0.0\n'),
                               ('dependencies.txt', files['dependencies.txt'] + '+--- (failed) missing: not resolved'),
                               ('dependencies.txt', files['dependencies.txt'] + '+--- (failed) unknown (optional): not resolved')]:
                with self.subTest(name=name, text=text):
                    (directory / name).write_text(text)
                    with self.assertRaises(ValueError):
                        pc.check_reports(reports, log, target, manifest, policy)
                    (directory / name).write_text(files[name])
            allowed = copy.deepcopy(policy)
            allowed['optional_absences'] = {'known': 'reviewed optional dependency'}
            (directory / 'dependencies.txt').write_text(files['dependencies.txt'] + '+--- (failed) known (optional): not resolved')
            pc.check_reports(reports, log, target, manifest, allowed)
            (directory / 'dependencies.txt').write_text(files['dependencies.txt'] + '+--- (failed) known: not resolved')
            with self.assertRaises(ValueError):
                pc.check_reports(reports, log, target, manifest, allowed)
            (directory / 'dependencies.txt').write_text(files['dependencies.txt'])
            for name in files:
                (directory / name).unlink()
                with self.assertRaises((ValueError, FileNotFoundError)):
                    pc.check_reports(reports, log, target, manifest, policy)
                (directory / name).write_text(files[name])
            for extra in ('compatibility-problems.txt', 'experimental-api-usages.txt', 'unknown.txt'):
                (directory / extra).write_text('new problem')
                with self.assertRaises(ValueError):
                    pc.check_reports(reports, log, target, manifest, policy)
                (directory / extra).unlink()
            for bad_log in ('Scheduled verifications (0):', log + '\n ERROR failure', log + '\n WARN new warning'):
                with self.assertRaises(ValueError):
                    pc.check_reports(reports, bad_log, target, manifest, policy)
            with self.assertRaises(ValueError):
                pc.check_reports(reports, log, policy['targets']['quail4'], manifest, policy)

    def test_same_archive_and_identity_are_checked_before_and_after_verification(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(pc, 'plugin_identity', return_value={'source.commit': 'abc'}):
            archive = Path(temp) / 'plugin.zip'
            archive.write_bytes(b'original')
            manifest = {'size': 8, 'sha256': pc.digest(archive), 'identity': {'source.commit': 'abc'}}
            pc.check_archive(archive, manifest, manifest['sha256'])
            for field, value in [('size', 7), ('sha256', '0' * 64), ('identity', {'source.commit': 'other'})]:
                changed = copy.deepcopy(manifest); changed[field] = value
                with self.assertRaises(ValueError):
                    pc.check_archive(archive, changed, manifest['sha256'])
            archive.write_bytes(b'replaced')
            with self.assertRaises(ValueError):
                pc.check_archive(archive, manifest, manifest['sha256'])

    def test_sdk_mismatch_or_missing_bundled_runtime_has_no_fallback(self):
        target = json.loads(pc.POLICY.read_text())['targets']['quail1']
        with tempfile.TemporaryDirectory() as temp:
            sdk = Path(temp)
            info = {'productCode': 'AI', 'buildNumber': 'wrong', 'version': 'wrong'}
            (sdk / 'product-info.json').write_text(json.dumps(info))
            with self.assertRaisesRegex(ValueError, 'full build mismatch'):
                pc.sdk_identity(sdk, target)
            info['buildNumber'] = target['build'].removeprefix('AI-')
            (sdk / 'product-info.json').write_text(json.dumps(info))
            with self.assertRaisesRegex(ValueError, 'Bundled JBR missing'):
                pc.sdk_identity(sdk, target)

    def test_required_ci_gate_accepts_only_complete_or_safe_skip(self):
        import os
        import subprocess
        workflow = (pc.ROOT / '.github/workflows/ci.yml').read_text()
        gate = workflow.split('      - name: Enforce complete CI outcome', 1)[1].split('        run: |\n', 1)[1]
        script = '\n'.join(line[10:] for line in gate.splitlines())
        for checks, required, result, ok in [('success', 'true', 'success', True),
                                              ('success', 'false', 'skipped', True),
                                              ('success', 'true', 'skipped', False),
                                              ('success', 'true', 'failure', False),
                                              ('failure', 'false', 'skipped', False),
                                              ('success', '', 'skipped', False),
                                              ('success', 'false', 'success', False)]:
            env = dict(os.environ, CHECKS=checks, REQUIRED=required, COMPATIBILITY=result)
            run = subprocess.run(['bash', '-e', '-c', script], env=env, capture_output=True)
            self.assertEqual(run.returncode == 0, ok, (checks, required, result))
