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

    def test_version_probe_uses_same_owned_npm_configuration_as_install(self):
        target = self.root / 'new-run'
        npm = self.root / 'npm-cli.js'
        npm.write_text('synthetic npm')
        calls = []
        def install(command, **options):
            calls.append(command)
            entry = target / 'package/node_modules/@modelcontextprotocol/server-memory/dist/index.js'
            entry.parent.mkdir(parents=True)
            entry.write_text('synthetic official entry')
        def version(command, **options):
            if str(npm) not in command:
                return 'v26.8.2\n'
            self.assertEqual(target, options.get('cwd'))
            for name, relative in (('--userconfig', 'user.npmrc'), ('--globalconfig', 'global.npmrc'),
                                   ('--prefix', 'package'), ('--cache', 'cache')):
                self.assertIn(name, command)
                value = str(target / relative)
                self.assertEqual(value, command[command.index(name) + 1])
                self.assertEqual(value, calls[0][calls[0].index(name) + 1])
            self.assertEqual(b'', (target / 'user.npmrc').read_bytes())
            self.assertEqual(b'', (target / 'global.npmrc').read_bytes())
            self.assertEqual({'PATH'}, set(options['env']))
            return '11.19.1\n'
        with patch.object(fixture.subprocess, 'run', side_effect=install), \
             patch.object(fixture.subprocess, 'check_output', side_effect=version):
            fixture.prepare(target, self.node, npm)

    def test_added_package_symlink_is_refused_before_launch(self):
        (self.root / 'package/extra.js').symlink_to('entry.js')
        self.refused()

    def test_changed_package_symlink_is_refused_before_launch(self):
        package = self.root / 'package'
        (package / 'second.js').write_text('another fixed file')
        (package / '.bin').mkdir()
        link = package / '.bin/server'
        link.symlink_to('../entry.js')
        manifest_path = self.root / 'manifest.json'
        manifest = json.loads(manifest_path.read_text())
        manifest['files'] = fixture.package_files(self.root)
        manifest_path.write_text(json.dumps(manifest))
        link.unlink()
        link.symlink_to('../second.js')
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
