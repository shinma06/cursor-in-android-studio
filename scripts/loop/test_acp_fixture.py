import json
import queue
import shlex
from pathlib import Path
import subprocess
import tempfile
import threading
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


    def test_direct_capture_refuses_missing_marker_and_never_overwrites(self):
        config = json.loads((self.output / 'launch.json').read_text())
        command = [config['python'], '-I', config['fake'], 'permission', str(self.workspace),
                   '--capture', str(self.output / 'capture')]
        marker = self.marker.read_text()
        self.marker.unlink()
        rejected = subprocess.run(command, cwd=self.workspace, env={}, input='',
                                  capture_output=True, text=True, timeout=5)
        self.assertEqual(64, rejected.returncode)
        self.assertEqual('', rejected.stdout)
        self.assertEqual([], list((self.output / 'capture').iterdir()))
        self.marker.write_text(marker)
        # exec preserves PID, reproducing a stale log from PID reuse without a mock.
        script = ("import os,pathlib,sys; "
                  "p=pathlib.Path(sys.argv[-1])/f'wire-{os.getpid()}.jsonl'; "
                  "p.write_text('KEEP'); os.execve(sys.argv[1],sys.argv[1:],{})")
        collision = subprocess.run([config['python'], '-I', '-c', script, *command],
                                   cwd=self.workspace, env={}, input='',
                                   capture_output=True, text=True, timeout=5)
        self.assertEqual(64, collision.returncode)
        self.assertEqual('', collision.stdout)
        self.assertEqual(['KEEP'], [path.read_text() for path in (self.output / 'capture').iterdir()])

    def test_two_process_protocol_and_capture_are_separate(self):
        processes = []

        def launch():
            process = subprocess.Popen([str(self.launcher), 'acp'], cwd=self.workspace,
                                       env={'PROVIDER_SECRET': 'DO_NOT_COLLECT'},
                                       stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE, text=True, bufsize=1)
            processes.append(process)
            received = queue.Queue()

            def read():
                for line in process.stdout:
                    received.put(line)
                received.put(None)

            threading.Thread(target=read, daemon=True).start()
            return process, received

        def send(process, message):
            process.stdin.write(json.dumps({'jsonrpc': '2.0', **message}) + '\n')
            process.stdin.flush()

        def receive(received):
            line = received.get(timeout=5)
            self.assertIsNotNone(line, 'Fixture exited before the expected response')
            return json.loads(line)

        try:
            peers = [launch(), launch()]
            for index, (process, received) in enumerate(peers):
                send(process, {'id': 1, 'method': 'initialize', 'params': {
                    'protocolVersion': 1, 'clientCapabilities': {
                        'fs': {'readTextFile': False, 'writeTextFile': False}, 'terminal': False}}})
                self.assertEqual({'protocolVersion': 1}, receive(received)['result'])
                send(process, {'id': 2, 'method': 'session/new', 'params': {
                    'cwd': str(self.workspace), 'mcpServers': []}})
                self.assertEqual('session-one', receive(received)['result']['sessionId'])
                send(process, {'id': 3, 'method': 'session/prompt', 'params': {
                    'sessionId': 'session-one', 'prompt': [{'type': 'text', 'text': f'SYNTHETIC-{index}'}]}})
                permission = receive(received)
                self.assertEqual('session/request_permission', permission['method'])
                # The real permission response is the release; no extra control server.
                with self.assertRaises(queue.Empty):
                    received.get(timeout=0.1)
                send(process, {'id': permission['id'], 'result': {
                    'outcome': {'outcome': 'selected', 'optionId': 'reject'}}})
                terminal = receive(received)
                self.assertEqual(3, terminal['id'])
                self.assertEqual({'stopReason': 'end_turn'}, terminal['result'])
                send(process, {'id': 4, 'method': 'session/prompt', 'params': {
                    'sessionId': 'session-one', 'prompt': [{'type': 'text', 'text': f'CANCEL-{index}'}]}})
                self.assertEqual('session/request_permission', receive(received)['method'])
                send(process, {'method': 'session/cancel', 'params': {'sessionId': 'session-one'}})
                cancelled = receive(received)
                self.assertEqual(4, cancelled['id'])
                self.assertEqual({'stopReason': 'cancelled'}, cancelled['result'])
                process.stdin.close()
                self.assertEqual(0, process.wait(timeout=5))
                self.assertEqual('', process.stderr.read())

            captures = sorted((self.output / 'capture').glob('wire-*.jsonl'))
            self.assertEqual(2, len(captures))
            for index, process in enumerate(processes):
                capture = self.output / 'capture' / f'wire-{process.pid}.jsonl'
                records = [json.loads(line) for line in capture.read_text().splitlines()]
                self.assertEqual({process.pid}, {record['pid'] for record in records})
                self.assertEqual(['in', 'out'] * 6, [record['direction'] for record in records])
                times = [record['monotonic_ns'] for record in records]
                self.assertEqual(sorted(times), times)
                for record in records:
                    payload = record['payload']
                    self.assertEqual(payload.get('id'), record['rpc_id'])
                    self.assertEqual('2.0', payload['jsonrpc'])
                    session = payload.get('params', {}).get('sessionId') or payload.get('result', {}).get('sessionId')
                    self.assertEqual(session, record['session_id'])
                self.assertEqual(0o600, capture.stat().st_mode & 0o777)
                raw = capture.read_text()
                self.assertIn(f'SYNTHETIC-{index}', raw)
                self.assertNotIn(f'SYNTHETIC-{1-index}', raw)
                self.assertNotIn('DO_NOT_COLLECT', raw)
                prompts = [record for record in records if record['direction'] == 'in'
                           and record['payload'].get('method') == 'session/prompt']
                self.assertEqual([3, 4], [record['rpc_id'] for record in prompts])
            self.assertFalse((self.workspace / 'wire.jsonl').exists())
        finally:
            for process in processes:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=5)
                for stream in (process.stdin, process.stdout, process.stderr):
                    stream.close()

if __name__ == '__main__':
    unittest.main()
