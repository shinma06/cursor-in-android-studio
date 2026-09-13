import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import mcp_memory_fixture as fixture


class OwnedMemoryBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='qa49-memory-boundary-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        for name in ('package', 'data', 'evidence'):
            (self.root / name).mkdir()
        self.node = self.root / 'node'
        self.node.write_text('not an executable')
        (self.root / 'package/entry.js').write_text('synthetic test marker')
        self.marker = {'schema': 1, 'purpose': fixture.PURPOSE, 'root': str(self.root), 'id': 'qa49-memory-test'}
        (self.root / 'MARKER.json').write_text(json.dumps(self.marker))
        (self.root / 'project-mcp.template.json').write_text('{}')
        manifest = {**self.marker, 'node': str(self.node), 'node_sha256': fixture.digest(self.node),
                    'files': fixture.package_files(self.root),
                    'template_sha256': fixture.digest(self.root / 'project-mcp.template.json')}
        (self.root / 'manifest.json').write_text(json.dumps(manifest))

    def refused(self):
        with patch.object(fixture.subprocess, 'Popen', side_effect=AssertionError('Must refuse before server launch')):
            with self.assertRaises(ValueError):
                fixture.verify(self.root)
        self.assertEqual([], list((self.root / 'evidence').iterdir()))

    def test_existing_root_is_never_replaced(self):
        with self.assertRaises(FileExistsError):
            fixture.prepare(self.root, self.node, self.node)
        self.assertEqual('not an executable', self.node.read_text())

    def test_existing_memory_is_not_read_or_changed(self):
        memory = self.root / 'data/memory.jsonl'
        memory.write_text('KEEP')
        self.refused()
        self.assertEqual('KEEP', memory.read_text())

    def test_symlink_data_is_refused(self):
        (self.root / 'data').rmdir()
        (self.root / 'data').symlink_to(self.root / 'package', target_is_directory=True)
        self.refused()

    def test_changed_runtime_package_template_or_marker_is_refused(self):
        for relative in ('node', 'package/entry.js', 'project-mcp.template.json', 'MARKER.json'):
            with self.subTest(relative=relative):
                path = self.root / relative
                original = path.read_text()
                path.write_text('{}' if relative == 'MARKER.json' else 'changed')
                self.refused()
                path.write_text(original)


if __name__ == '__main__':
    unittest.main()
