"""Verify the fixed source overlay without starting an IDE or changing normal sources."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('defer_fixture', ROOT / 'docs/verification/defer/defer_fixture.py')
fixture = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fixture)


class DeferFixtureTest(unittest.TestCase):
    def test_committed_overlay_isolated_from_all_normal_product_sources(self):
        before = subprocess.check_output(['git', 'diff', '--', 'src/main', 'build.gradle.kts'], cwd=ROOT)
        with tempfile.TemporaryDirectory(prefix='defer-overlay-test-') as temporary:
            target = Path(temporary) / 'variant'
            manifest = fixture.prepare(ROOT, target)
            source = target / 'source'
            self.assertEqual(manifest['helper_sha256'], fixture.digest((ROOT / fixture.HELPER).read_bytes()))
            self.assertFalse((source / fixture.HELPER).exists())
            self.assertTrue((source / str(fixture.HELPER).replace('src/test/', 'src/main/', 1)).is_file())
            self.assertIn('0.1.0-verification-313', (source / 'build.gradle.kts').read_text())
            self.assertEqual(manifest['build_inputs'], fixture.source_inputs(source))
            changed = []
            for original in (ROOT / 'src/main').rglob('*'):
                if original.is_file() and original.read_bytes() != (source / original.relative_to(ROOT)).read_bytes():
                    changed.append(str(original.relative_to(ROOT)))
            self.assertEqual(sorted(changed), [
                'src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt',
                'src/main/kotlin/com/cursoragent/ui/AgentUiController.kt',
                'src/main/kotlin/com/cursoragent/ui/composer/mention/MentionPopupController.kt',
            ])
            (source / 'build.gradle.kts').write_text('changed after preparation')
            with self.assertRaisesRegex(ValueError, 'inputs changed'):
                fixture.build(target)
        self.assertEqual(before, subprocess.check_output(['git', 'diff', '--', 'src/main', 'build.gradle.kts'], cwd=ROOT))

    def test_owned_control_initialization_and_missing_previous_ack(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = fixture.initialize(temporary)
            self.assertEqual(root.stat().st_mode & 0o777, 0o700)
            directory, run = fixture.control_root(root)
            self.assertEqual(directory, root)
            (root / 'command.json').write_text(json.dumps({'id': 'unacknowledged'}))
            with self.assertRaisesRegex(ValueError, 'not acknowledged'):
                fixture.command(root, 'close')
            root.chmod(0o755)
            with self.assertRaises(ValueError):
                fixture.control_root(root)


if __name__ == '__main__':
    unittest.main()
