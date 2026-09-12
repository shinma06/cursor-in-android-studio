import json
import shlex
from pathlib import Path
import subprocess
import tempfile
import unittest

import acp_fixture


class AdapterBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='acp-synthetic-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.workspace = self.root / '合成 workspace'
        self.workspace.mkdir()
        self.marker = self.workspace / acp_fixture.MARKER
        self.marker.write_text(json.dumps({'schema': 1, 'purpose': acp_fixture.PURPOSE,
                                          'workspace': str(self.workspace)}))
        self.output = self.root / 'run'
        self.launcher = acp_fixture.prepare(self.workspace, self.output)

    def refused(self, arguments, cwd=None):
        trap = self.root / 'agent'
        trap.write_text('#!/bin/sh\nprintf used > ' + shlex.quote(str(self.root / 'FALLBACK_USED')) + '\n')
        trap.chmod(0o700)
        result = subprocess.run([str(self.launcher), *arguments], cwd=cwd or self.workspace,
                                env={'PATH': str(self.root), 'PROVIDER_SECRET': 'DO_NOT_COLLECT'},
                                capture_output=True, text=True, timeout=5)
        self.assertEqual(64, result.returncode)
        self.assertEqual('', result.stdout)
        self.assertNotIn('DO_NOT_COLLECT', result.stderr)
        self.assertFalse((self.root / 'FALLBACK_USED').exists())
        self.assertEqual([], list((self.output / 'capture').iterdir()))
        self.assertFalse((self.workspace / 'wire.jsonl').exists())

    def test_plugin_arguments_are_exact_and_never_fall_back(self):
        for args in ([], ['--help'], ['-p', '合成'], ['acp', 'extra'], ['mcp', 'list']):
            with self.subTest(args=args):
                self.refused(args)

    def test_root_and_marker_rejections(self):
        self.refused(['acp'], self.root)
        self.marker.unlink()
        self.refused(['acp'])
        self.marker.write_text('{}')
        self.refused(['acp'])
        self.marker.write_text(json.dumps({'schema': True, 'purpose': acp_fixture.PURPOSE, 'workspace': str(self.workspace)}))
        self.refused(['acp'])

    def test_pins_reject_changed_manifest_and_prepare_preserves_existing_output(self):
        config_path = self.output / 'launch.json'
        original = config_path.read_text()
        for field in ('fake_sha256', 'adapter_sha256', 'marker_sha256'):
            config = json.loads(original)
            config[field] = '0' * 64
            config_path.write_text(json.dumps(config))
            self.refused(['acp'])
        config_path.write_text(original)
        with self.assertRaises(FileExistsError):
            acp_fixture.prepare(self.workspace, self.output)
        self.assertEqual(original, config_path.read_text())

    def test_prepare_requires_explicit_marker_and_separate_capture(self):
        with self.assertRaises(ValueError):
            acp_fixture.prepare(self.workspace, self.workspace / 'capture')
        self.marker.unlink()
        with self.assertRaises(ValueError):
            acp_fixture.prepare(self.workspace, self.root / 'other')
        target = self.root / 'marker-target'
        target.write_text('{}')
        self.marker.symlink_to(target)
        with self.assertRaises(ValueError):
            acp_fixture.prepare(self.workspace, self.root / 'other')


if __name__ == '__main__':
    unittest.main()
