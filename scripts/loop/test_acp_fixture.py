import ast
import json
from types import SimpleNamespace
from unittest.mock import Mock, patch
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


class ScenarioProcessTest(unittest.TestCase):
    setUp = AdapterBoundaryTest.setUp

    def start(self, scenario, name='scenario', arguments=None):
        output = self.root / name
        launcher = acp_fixture.prepare(self.workspace, output, scenario)
        if arguments is None:
            arguments = ['acp'] if scenario in acp_fixture.ACP_SCENARIOS else [
                '-p', '--output-format', 'stream-json', '--stream-partial-output', '--trust',
                '--workspace', str(self.workspace), '--mode', 'ask', 'SYNTHETIC ' + name]
        process = subprocess.Popen([str(launcher), *arguments], cwd=self.workspace,
            env={'PROVIDER_SECRET': 'DO_NOT_COLLECT'}, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)
        received = queue.Queue()
        def read():
            for line in process.stdout:
                received.put(json.loads(line))
            received.put(None)
        threading.Thread(target=read, daemon=True).start()
        control = output / 'capture' / f'control-{process.pid}'
        def cleanup():
            if control.is_dir():
                (control / 'release-child').touch()
                (control / 'release').touch()
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
            for stream in (process.stdin, process.stdout, process.stderr):
                stream.close()
        self.addCleanup(cleanup)
        return process, received, control, output

    def send(self, process, **message):
        process.stdin.write(json.dumps({'jsonrpc': '2.0', **message}) + '\n')
        process.stdin.flush()

    def receive(self, received):
        value = received.get(timeout=5)
        self.assertIsNotNone(value)
        return value

    def wait_file(self, path):
        import time
        deadline = time.monotonic() + 5
        while not path.exists() and time.monotonic() < deadline:
            time.sleep(.01)
        self.assertTrue(path.exists(), str(path))

    def initialize(self, process, received, delayed=False):
        self.send(process, id=1, method='initialize', params={'protocolVersion': 1,
            'clientCapabilities': {'fs': {'readTextFile': False, 'writeTextFile': False}, 'terminal': False}})
        self.assertEqual(1, self.receive(received)['result']['protocolVersion'])
        self.send(process, id=2, method='session/new', params={'cwd': str(self.workspace), 'mcpServers': []})
        if not delayed:
            self.assertEqual('session-one', self.receive(received)['result']['sessionId'])

    def prompt(self, process, identifier=3):
        self.send(process, id=identifier, method='session/prompt', params={
            'sessionId': 'session-one', 'prompt': [{'type': 'text', 'text': 'SYNTHETIC'}]})

    def test_existing_normal_eof_and_config_mismatch_connect_via_plugin_argv(self):
        for scenario in ('normal', 'eof', 'bad-config'):
            with self.subTest(scenario=scenario):
                process, received, control, _ = self.start(scenario, scenario)
                self.initialize(process, received)
                if scenario == 'bad-config':
                    self.send(process, id=3, method='session/set_config_option', params={
                        'sessionId': 'session-one', 'configId': 'mode', 'value': 'ask'})
                    self.assertEqual('agent', self.receive(received)['result']['configOptions'][0]['currentValue'])
                else:
                    self.prompt(process)
                    if scenario == 'normal':
                        self.assertEqual(['はい', 'はい'], [self.receive(received)['params']['update']['content']['text'] for _ in range(2)])
                        self.assertEqual('end_turn', self.receive(received)['result']['stopReason'])
                    else:
                        self.assertIsNone(received.get(timeout=5))
                process.stdin.close()
                self.assertEqual(0, process.wait(timeout=5))

    def test_delayed_new_is_per_process_and_does_not_block_stdin_or_cancel(self):
        first = self.start('commands-delayed', 'first')
        second = self.start('commands-delayed', 'second')
        for process, received, control, _ in (first, second):
            self.initialize(process, received, delayed=True)
            self.wait_file(control / 'new-ready')
            with self.assertRaises(queue.Empty):
                received.get(timeout=.1)
        p, q, control, _ = first
        (control / 'release-new').touch()
        self.assertEqual(2, self.receive(q)['id'])
        self.assertEqual('available_commands_update', self.receive(q)['params']['update']['sessionUpdate'])
        p2, q2, c2, _ = second
        self.send(p2, method='session/cancel', params={'sessionId': 'session-one'})
        self.wait_file(c2 / 'cancel-response')
        (c2 / 'release-new').touch()
        with self.assertRaises(queue.Empty):
            q2.get(timeout=.15)
        for process in (p, p2):
            process.stdin.close()
            self.assertEqual(0, process.wait(timeout=5))

    def test_delayed_new_expires_before_client_timeout_and_cannot_release_late(self):
        process, received, control, _ = self.start('commands-delayed', 'timeout')
        self.initialize(process, received, delayed=True)
        self.wait_file(control / 'new-ready')
        response = received.get(timeout=15)  # Product session/new timeout is 20 seconds.
        self.assertEqual(2, response['id'])
        self.assertEqual(-32000, response['error']['code'])
        (control / 'release-new').touch()
        with self.assertRaises(queue.Empty):
            received.get(timeout=.15)
        process.stdin.close()
        self.assertEqual(0, process.wait(timeout=5))

    def test_pending_request_cancel_response_precedes_session_cancel(self):
        for scenario in ('permission', 'questions', 'plan'):
            with self.subTest(scenario=scenario):
                process, received, control, _ = self.start(scenario, 'stop-' + scenario)
                self.initialize(process, received)
                self.prompt(process)
                request = self.receive(received)
                # Exact answerJson(AgentAnswer.Cancel), then AcpSession.cancel notification.
                self.send(process, id=request['id'], result={'outcome': {'outcome': 'cancelled'}})
                response = self.receive(received)
                self.assertEqual(3, response['id'])
                self.assertEqual('cancelled', response['result']['stopReason'])
                self.send(process, method='session/cancel', params={'sessionId': 'session-one'})
                self.wait_file(control / 'cancel-response')
                with self.assertRaises(queue.Empty):
                    received.get(timeout=.15)
                process.stdin.close()
                self.assertEqual(0, process.wait(timeout=5))

    def test_child_cancel_and_owned_release_keep_other_process_separate(self):
        peers = [self.start('child', name) for name in ('child-a', 'child-b')]
        for process, received, control, _ in peers:
            self.initialize(process, received)
            self.prompt(process)
            self.assertEqual('running', self.receive(received)['params']['update']['content']['text'])
            self.wait_file(control / 'child-ready')
            self.send(process, method='session/cancel', params={'sessionId': 'session-one'})
            self.assertEqual('cancelled', self.receive(received)['result']['stopReason'])
        self.assertNotEqual((peers[0][2] / 'child.pid').read_text(), (peers[1][2] / 'child.pid').read_text())
        for process, received, control, _ in peers:
            (control / 'release-child').touch()
            process.stdin.close()
            self.assertEqual(0, process.wait(timeout=5))
            self.assertIsNone(received.get(timeout=5))  # Child released its inherited stdout too.

    def test_questions_plan_and_late_answers_after_cancel(self):
        for scenario, method, answer in [('questions', 'cursor/ask_question', {'outcome': {'outcome': 'answered', 'answers': [{'questionId': 'a', 'selectedOptionIds': ['yes']}]}}),
                                         ('plan', 'cursor/create_plan', {'outcome': {'outcome': 'rejected'}})]:
            process, received, _, _ = self.start(scenario, scenario)
            self.initialize(process, received)
            self.prompt(process)
            request = self.receive(received)
            self.assertEqual(method, request['method'])
            with self.assertRaises(queue.Empty):
                received.get(timeout=.1)
            self.send(process, id=request['id'], result=answer)
            self.assertEqual('end_turn', self.receive(received)['result']['stopReason'])
            self.prompt(process, 4)
            request = self.receive(received)
            self.send(process, method='session/cancel', params={'sessionId': 'session-one'})
            self.assertEqual(4, self.receive(received)['id'])
            self.prompt(process, 5)
            fresh = self.receive(received)
            self.assertNotEqual(request['id'], fresh['id'])
            self.send(process, id=request['id'], result=answer)
            with self.assertRaises(queue.Empty):
                received.get(timeout=.1)
            self.send(process, id=fresh['id'], result=answer)
            self.assertEqual(5, self.receive(received)['id'])

    def test_tool_updates_release_and_cancel_preserve_wire_shapes_and_disk(self):
        for cancel in (False, True):
            process, received, control, _ = self.start('events', str(cancel))
            self.initialize(process, received)
            self.prompt(process)
            self.assertEqual('agent_thought_chunk', self.receive(received)['params']['update']['sessionUpdate'])
            self.assertEqual('in_progress', self.receive(received)['params']['update']['status'])
            self.wait_file(control / 'events-ready')
            if cancel:
                self.send(process, method='session/cancel', params={'sessionId': 'session-one'})
                self.assertEqual('cancelled', self.receive(received)['result']['stopReason'])
            (control / 'release-events').touch()
            if cancel:
                with self.assertRaises(queue.Empty):
                    received.get(timeout=.15)
            else:
                rows = [self.receive(received) for _ in range(5)]
                self.assertEqual(2, len(rows[0]['params']['update']['content']))
                self.assertEqual('completed', rows[1]['params']['update']['status'])
                self.assertEqual([], rows[2]['params']['update']['content'])
                self.assertEqual(3, rows[-1]['id'])
            self.assertFalse((self.workspace / 'a.txt').exists())

    def test_all_finite_print_outputs_and_physical_exit(self):
        for scenario in acp_fixture.PRINT_SCENARIOS:
            process, received, control, output = self.start(scenario, scenario)
            self.wait_file(control / 'result-written')
            events = []
            if scenario == 'print-hold':
                # Observe the newline-delimited Result on stdout while the PID lives.
                while not events or events[-1].get('type') != 'result':
                    events.append(self.receive(received))
                self.assertIsNone(process.poll())
                (control / 'release').touch()
            expected_code = 7 if scenario == 'print-abnormal' else 0
            self.assertEqual(expected_code, process.wait(timeout=5))
            while True:
                value = received.get(timeout=5)
                if value is None:
                    break
                events.append(value)
            result = events[-1]
            self.assertEqual('result', result['type'])
            self.assertEqual(scenario == 'print-error', result['is_error'])
            assistants = [event for event in events if event['type'] == 'assistant']
            if scenario != 'print-result-only':
                # #254 final flush has neither timestamp nor call metadata. A call-only
                # record would force the real parser into its legacy fallback.
                self.assertEqual({'type': 'assistant', 'text': result['result']}, assistants[-1])
                deltas = [event['text'] for event in assistants if 'timestamp_ms' in event and 'model_call_id' not in event]
                self.assertEqual('はいはい😀😀' + ('完了' if scenario == 'print-tools' else ''), ''.join(deltas))
                self.assertEqual(result['result'], ''.join(deltas))
            if scenario == 'print-usage':
                self.assertEqual({'inputTokens': 28791, 'outputTokens': 141, 'cacheReadTokens': 5748, 'cacheWriteTokens': 0}, result['usage'])
            elif scenario == 'print-partial':
                self.assertEqual({'outputTokens': 0}, result['usage'])
            else:
                self.assertNotIn('usage', result)
            if scenario == 'print-result-only':
                self.assertEqual(['system', 'result'], [e['type'] for e in events])
            if scenario == 'print-tools':
                self.assertEqual(['started', 'completed', 'completed'] * 2, [e['subtype'] for e in events if e['type'] == 'tool_call'])
                self.assertFalse((self.workspace / 'a.txt').exists())
            raw = (output / 'capture' / f'wire-{process.pid}.jsonl').read_text()
            self.assertNotIn('DO_NOT_COLLECT', raw)
            self.assertEqual('Synthetic abnormal exit\n' if expected_code else '', process.stderr.read())

    def test_print_version_validation_and_two_process_release(self):
        launcher = acp_fixture.prepare(self.workspace, self.root / 'version', 'print-hold')
        args = ['-p', '--output-format', 'stream-json', '--stream-partial-output', '--trust', '--workspace', str(self.workspace)]
        for arguments, expected in [(['--version'], 0), (['mcp', 'list'], 64), (args + ['--unknown', 'x', 'SYNTHETIC'], 64),
                                    (args + ['--force', '--auto-review', 'SYNTHETIC'], 64), (args[:-1] + [str(self.root), 'SYNTHETIC'], 64)]:
            result = subprocess.run([str(launcher), *arguments], cwd=self.workspace, env={}, capture_output=True, text=True, timeout=5)
            self.assertEqual(expected, result.returncode)
            self.assertEqual(acp_fixture.PRINT_VERSION + '\n' if expected == 0 else '', result.stdout)
        peers = [self.start('print-hold', name) for name in ('print-a', 'print-b')]
        for process, _, control, _ in peers:
            self.wait_file(control / 'result-written')
        (peers[0][2] / 'release').touch()
        self.assertEqual(0, peers[0][0].wait(timeout=5))
        self.assertIsNone(peers[1][0].poll())
        peers[1][0].terminate()
        self.assertNotEqual(0, peers[1][0].wait(timeout=5))



class NewSessionCancelBoundaryTest(unittest.TestCase):
    def environment(self, root, output, clock=lambda: 0):
        # Execute the actual fixture function/cancel branch without a provider or subprocess.
        source = Path(__file__).resolve().parents[2] / 'src/test/resources/acp/fake_agent.py'
        tree = ast.parse(source.read_text())
        function = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'new_session')
        cancel = next(n.body for n in ast.walk(tree) if isinstance(n, ast.If)
                      and ast.unparse(n.test) == "method == 'session/cancel'")
        environment = dict(scenario='commands-delayed', control=root, config=[],
            cancelled=threading.Event(), closed=threading.Event(), wire_lock=threading.RLock(),
            time=SimpleNamespace(monotonic=clock), command_updates=lambda: None,
            send=lambda value: output.append('error'),
            response=lambda *args: output.append('response'),
            finish=lambda *args: output.append('cancelled'),
            threading=SimpleNamespace(Thread=lambda **kw: SimpleNamespace(start=lambda: output.append('worker'))))
        exec(compile(ast.Module(body=[function], type_ignores=[]), str(source), 'exec'), environment)
        code = compile(ast.Module(body=cancel, type_ignores=[]), str(source), 'exec')
        return environment, lambda: exec(code, environment)

    def test_cancel_during_wait_or_release_observation_refuses_all_new_session_output(self):
        for point in ('wait', 'release', 'timeout'):
            with self.subTest(point=point), tempfile.TemporaryDirectory() as temporary:
                root, output = Path(temporary), []
                ticks = iter((0, 11)) if point == 'timeout' else None
                env, cancel = self.environment(root, output, lambda: next(ticks) if ticks else 0)
                def cancel_then_release():
                    cancel()
                    self.assertTrue((root / 'cancel-response').exists())
                    (root / 'release-new').touch()
                def wait(_):
                    if point != 'release':
                        cancel_then_release()
                    return False
                env['closed'] = Mock(wait=wait, is_set=lambda: False)
                exists = Path.exists
                def observe(path):
                    if point == 'release' and path == root / 'release-new':
                        cancel_then_release()
                    return exists(path)
                with patch.object(Path, 'exists', observe):
                    env['new_session'](2)
                self.assertEqual(['cancelled'], output)

    def test_cancel_ack_cannot_overtake_committed_response_or_worker_start(self):
        for terminal in ('response', 'error'):
            with self.subTest(terminal=terminal), tempfile.TemporaryDirectory() as temporary:
                root, output = Path(temporary), []
                ticks = iter((0, 11)) if terminal == 'error' else None
                env, cancel = self.environment(root, output, lambda: next(ticks) if ticks else 0)
                (root / 'release-new').touch()
                entered, release, attempted, acknowledged = [threading.Event() for _ in range(4)]
                errors = []
                def hold(*args):
                    entered.set()
                    if not release.wait(5):
                        raise TimeoutError('Test response gate was not released')
                    output.append(terminal)
                env['response' if terminal == 'response' else 'send'] = hold
                def run(action):
                    try:
                        action()
                    except BaseException as error:
                        errors.append(error)
                def cancel_and_ack():
                    attempted.set()
                    cancel()
                    acknowledged.set()
                worker = threading.Thread(target=lambda: run(lambda: env['new_session'](2)))
                cancelling = threading.Thread(target=lambda: run(cancel_and_ack))
                worker.start()
                try:
                    self.assertTrue(entered.wait(5))
                    cancelling.start()
                    self.assertTrue(attempted.wait(5))
                    self.assertFalse(acknowledged.wait(.1), 'Cancellation overtook an uncommitted response')
                finally:
                    release.set()
                    worker.join(5)
                    if cancelling.ident is not None:
                        cancelling.join(5)
                self.assertFalse(worker.is_alive())
                self.assertFalse(cancelling.is_alive())
                self.assertEqual([], errors)
                self.assertTrue(acknowledged.is_set())
                self.assertEqual([terminal] + (['worker'] if terminal == 'response' else []) + ['cancelled'], output)


if __name__ == '__main__':
    unittest.main()
