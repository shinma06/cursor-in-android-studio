"""Browser QA input checks only: no IDE, native JCEF, OS trust edits or external hosts."""
import http.client
import contextlib
import io
import tempfile
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
from pathlib import Path
import socket
import selectors
import subprocess
import sys
import ssl
import threading
import time
import unittest
from urllib.parse import urlsplit

SOURCE = Path(__file__).resolve().parents[2] / 'docs/verification/fixtures/browser_fixture.py'
spec = importlib.util.spec_from_file_location('browser_fixture', SOURCE)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


class BrowserFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = fixture.Fixture()

    @classmethod
    def tearDownClass(cls):
        cls.fixture.close()
        fixture.cleanup(cls.fixture.directory)

    def request(self, path, method='GET', headers=None, run=None, listener='http4'):
        run = run or self.fixture
        url = urlsplit(run.manifest['urls'][listener])
        connection = http.client.HTTPConnection(url.hostname, url.port, timeout=3)
        try:
            connection.request(method, path, headers=headers or {})
            response = connection.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            connection.close()

    def tls(self, host, context, listener='https4'):
        url = urlsplit(self.fixture.manifest['urls'][listener])
        raw = socket.create_connection((url.hostname, url.port), timeout=3)
        try:
            with context.wrap_socket(raw, server_hostname=host) as stream:
                authority = '[' + host + ']' if ':' in host else host
                stream.sendall(('GET /%E6%A4%9C%E7%B4%A2?q=%E6%97%A5%E6%9C%AC%E8%AA%9E HTTP/1.1\r\n'
                                'Host: ' + authority + '\r\nConnection: close\r\n\r\n').encode())
                response = http.client.HTTPResponse(stream)
                response.begin()
                return response.status, response.read()
        finally:
            raw.close()

    def test_control_never_sends_token_on_a_second_connection(self):
        for reason in ('http10', 'connection-close', 'lost-socket'):
            with self.subTest(reason=reason):
                requests = []
                class Listener(BaseHTTPRequestHandler):
                    protocol_version = 'HTTP/1.0' if reason == 'http10' else 'HTTP/1.1'
                    def log_message(self, *_):
                        pass
                    def reply(self, body):
                        self.send_response(200)
                        self.send_header('Content-Length', str(len(body)))
                        if reason == 'connection-close':
                            self.send_header('Connection', 'close')
                        self.end_headers()
                        self.wfile.write(body)
                    def do_GET(self):
                        requests.append(('GET', self.client_address))
                        self.reply(b'owned-run')
                    def do_POST(self):
                        requests.append(('POST', self.client_address))
                        self.reply(b'forged-success')
                server = ThreadingHTTPServer(('127.0.0.1', 0), Listener)
                worker = threading.Thread(target=server.serve_forever)
                worker.start()
                manifest = {'state': 'running', 'run_id': 'owned-run', 'control_token': 'synthetic-token',
                            'urls': {'http4': f'http://127.0.0.1:{server.server_port}'}}
                class LostConnection(http.client.HTTPConnection):
                    def getresponse(self):
                        response = super().getresponse()
                        read = response.read
                        def read_then_lose(*args, **kwargs):
                            data = read(*args, **kwargs)
                            self.close()  # Simulate loss after identity, before the token-bearing request.
                            return data
                        response.read = read_then_lose
                        return response
                connection_type = LostConnection if reason == 'lost-socket' else http.client.HTTPConnection
                try:
                    with patch.object(fixture, 'read_run', return_value=(Path('unused'), manifest)), \
                         patch.object(fixture.http.client, 'HTTPConnection', connection_type):
                        with self.assertRaises((ValueError, http.client.NotConnected, ConnectionError)):
                            fixture.control('unused', '/stop')
                    self.assertEqual(['GET'], [request[0] for request in requests])
                finally:
                    server.shutdown()
                    server.server_close()
                    worker.join(3)

    def test_setup_failures_remove_owned_keys_and_stop_started_listeners(self):
        for stage in ('certificate', 'listener'):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary) / 'browser-qa-failed'
                directory.mkdir(mode=0o700)
                servers = []
                original_server = fixture.Server
                def fail_certificate(run):
                    (run.directory / 'ca.key').write_text('synthetic-key')
                    raise RuntimeError('certificate setup failed')
                def fail_second_listener(*args, **kwargs):
                    if servers:
                        raise RuntimeError('listener setup failed')
                    server = original_server(*args, **kwargs)
                    servers.append(server)
                    return server
                with patch.object(fixture.tempfile, 'mkdtemp', return_value=str(directory)):
                    target = patch.object(fixture.Fixture, 'certificates', fail_certificate) if stage == 'certificate' else patch.object(fixture, 'Server', fail_second_listener)
                    with target, self.assertRaisesRegex(RuntimeError, stage + ' setup failed'):
                        fixture.Fixture(ipv6=False)
                try:
                    self.assertFalse(directory.exists(), 'failed setup must remove owned keys and directory')
                    for server in servers:
                        self.assertEqual(-1, server.socket.fileno())
                finally:
                    if directory.exists():
                        fixture.cleanup(directory)

    def test_thread_start_failure_returns_original_error_and_reclaims_started_and_unstarted_servers(self):
        script = r"""
import importlib.util, sys
from pathlib import Path
from unittest.mock import patch
spec = importlib.util.spec_from_file_location('fixture', sys.argv[1])
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
directory = Path(sys.argv[2])
directory.mkdir(mode=0o700)
servers, threads = [], []
original_server, original_start = fixture.Server, fixture.threading.Thread.start
class ObservedServer(original_server):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        servers.append(self)
def start(thread):
    threads.append(thread)
    if len(threads) == int(sys.argv[3]):
        raise RuntimeError('synthetic thread start failure')
    original_start(thread)
with patch.object(fixture, 'Server', ObservedServer), patch.object(fixture.threading.Thread, 'start', start), patch.object(fixture.tempfile, 'mkdtemp', return_value=str(directory)):
    try:
        fixture.Fixture(ipv6=False)
    except RuntimeError as error:
        assert str(error) == 'synthetic thread start failure', error
    else:
        raise AssertionError('Original thread error must be returned')
assert servers and all(server.socket.fileno() == -1 for server in servers)
assert threads and all(not thread.is_alive() for thread in threads)
assert not directory.exists(), 'owned setup keys must be reclaimed'
print('reclaimed')
"""
        for fail_at in (1, 2):
            with self.subTest(fail_at=fail_at), tempfile.TemporaryDirectory() as temporary:
                result = subprocess.run([sys.executable, '-c', script, str(SOURCE),
                                         str(Path(temporary) / 'browser-qa-thread-failure'), str(fail_at)],
                                        capture_output=True, text=True, timeout=5)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual('reclaimed', result.stdout.strip())

    def test_setup_preserves_unknown_files_and_reports_directory_without_hiding_original_error(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / 'browser-qa-preserved'
            directory.mkdir(mode=0o700)
            def fail_certificate(run):
                (run.directory / 'keep.txt').write_text('unrelated')
                raise RuntimeError('original setup failure')
            output = io.StringIO()
            with patch.object(fixture.tempfile, 'mkdtemp', return_value=str(directory)), \
                 patch.object(fixture.Fixture, 'certificates', fail_certificate), contextlib.redirect_stderr(output):
                with self.assertRaisesRegex(RuntimeError, 'original setup failure'):
                    fixture.Fixture(ipv6=False)
            self.assertEqual('unrelated', (directory / 'keep.txt').read_text())
            self.assertIn(str(directory), output.getvalue())
            (directory / 'keep.txt').unlink()
            fixture.cleanup(directory)

    def test_fixed_pages_redirects_errors_and_no_file_or_proxy_endpoint(self):
        status, _, body = self.request('/a')
        self.assertEqual(200, status)
        self.assertIn(b'href="/b"', body)
        self.assertIn(b'href="#target"', body)
        self.assertIn(b"window.open('/b'", body)
        self.assertNotIn(b'<script src', body)
        self.assertNotIn(b'<img', body)
        self.assertEqual(200, self.request('/b')[0])
        self.assertEqual('/b', self.request('/redirect')[1]['Location'])
        for kind, target in fixture.FORBIDDEN.items():
            status, headers, _ = self.request('/redirect-' + kind)
            self.assertEqual((302, target), (status, headers['Location']))
        for code in (404, 500):
            self.assertEqual(code, self.request('/' + str(code))[0])
        for path in ('/../ca.key', '/ca.key', '/proxy?url=http://outside.invalid', '/shell?cmd=test'):
            self.assertEqual(404, self.request(path)[0])
        self.assertEqual(403, self.request('http://outside.invalid/a')[0])
        self.assertEqual(403, self.request('/a', headers={'Host': 'outside.invalid'})[0])
        self.assertEqual(501, self.request('/head-probe', method='HEAD')[0])
        log = [json.loads(line) for line in (self.fixture.directory / 'requests.jsonl').read_text().splitlines()]
        self.assertTrue(any(row.get('method') == 'HEAD' and row.get('path') == '/head-probe' for row in log))

    def test_partial_response_is_truncated_not_a_normal_error_page(self):
        with self.assertRaises(http.client.IncompleteRead) as failure:
            self.request('/cut')
        self.assertEqual(b'partial', failure.exception.partial)
        self.assertGreater(failure.exception.expected, 0)

    def test_fixed_delay_and_manual_release_are_observable_and_separate_per_run(self):
        before = time.monotonic()
        self.assertEqual(200, self.request('/delay')[0])
        self.assertGreaterEqual(time.monotonic() - before, 0.9)
        result = []
        thread = threading.Thread(target=lambda: result.append(self.request('/hold/lifetime')))
        thread.start()
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            with self.fixture.lock:
                if 'lifetime' in self.fixture.holds:
                    break
            time.sleep(0.01)
        self.assertTrue(thread.is_alive())
        self.assertEqual(403, self.request('/release/lifetime', method='POST')[0])
        self.assertTrue(thread.is_alive())
        other = fixture.Fixture(ipv6=False)
        try:
            with self.assertRaisesRegex(ValueError, '404'):
                fixture.control(other.directory, '/release/lifetime')
            self.assertTrue(thread.is_alive())
            fixture.control(self.fixture.directory, '/release/lifetime')
            thread.join(3)
            self.assertFalse(thread.is_alive())
            self.assertEqual(200, result[0][0])
            log = [json.loads(line) for line in (self.fixture.directory / 'requests.jsonl').read_text().splitlines()]
            self.assertTrue(all(row['run_id'] == self.fixture.identity for row in log))
            self.assertTrue(any(row['event'] == 'held' and row.get('tag') == 'lifetime' for row in log))
            self.assertTrue(any(row['event'] == 'released' and row.get('tag') == 'lifetime' for row in log))
        finally:
            self.fixture.holds['lifetime'].set()
            thread.join(3)
            other.close()
            fixture.cleanup(other.directory)

    def test_TLS_requires_explicit_CA_and_matching_IP_localhost_or_IDN_without_DNS_change(self):
        trusted = ssl.create_default_context(cafile=str(self.fixture.directory / 'ca.pem'))
        for host in ('127.0.0.1', 'localhost', fixture.IDN):
            status, body = self.tls(host, trusted)
            self.assertEqual(200, status)
            self.assertIn('Unicode'.encode(), body)
        with self.assertRaises(ssl.SSLCertVerificationError):
            self.tls('wrong-name.invalid', trusted)
        with self.assertRaises(ssl.SSLCertVerificationError):
            self.tls(fixture.IDN, ssl.create_default_context())

    def test_IPv6_loopback_and_TLS_name_verification_when_available(self):
        if 'https6' not in self.fixture.servers:
            self.skipTest(self.fixture.manifest.get('ipv6_unavailable', 'IPv6 unavailable'))
        self.assertEqual(200, self.request('/b', listener='http6')[0])
        trusted = ssl.create_default_context(cafile=str(self.fixture.directory / 'ca.pem'))
        self.assertEqual(200, self.tls('::1', trusted, 'https6')[0])

    def test_listeners_are_dynamic_loopback_only_and_unknown_control_is_rejected(self):
        ports = set()
        for server in self.fixture.servers.values():
            self.assertIn(server.server_address[0], ('127.0.0.1', '::1'))
            self.assertGreater(server.server_port, 0)
            ports.add(server.server_port)
        self.assertEqual(len(self.fixture.servers), len(ports))
        with self.assertRaisesRegex(ValueError, 'loopback'):
            fixture.Server(self.fixture, '0.0.0.0')
        self.assertEqual(403, self.request('/stop', method='POST', headers={'X-Fixture-Token': 'wrong'})[0])
        self.assertFalse(self.fixture.stopped.is_set())
        with self.assertRaisesRegex(ValueError, 'first'):
            fixture.cleanup(self.fixture.directory)

    def test_documented_CLI_start_stop_cleanup_contract(self):
        process = subprocess.Popen([sys.executable, str(SOURCE), 'serve', '--ipv4-only'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        directory = None
        try:
            with selectors.DefaultSelector() as ready:
                ready.register(process.stdout, selectors.EVENT_READ)
                self.assertTrue(ready.select(15), 'CLI did not publish its run directory')
            directory = Path(process.stdout.readline().strip())
            _, manifest = fixture.read_run(directory)
            self.assertEqual('running', manifest['state'])
            self.assertEqual({'http4', 'https4'}, set(manifest['urls']))
            subprocess.run([sys.executable, str(SOURCE), 'stop', str(directory)], check=True, capture_output=True, timeout=5)
            # The documented foreground terminal must return before cleanup; stop only acknowledges intent.
            _, errors = process.communicate(timeout=10)
            self.assertEqual(0, process.returncode, errors)
            subprocess.run([sys.executable, str(SOURCE), 'cleanup', str(directory)], check=True, capture_output=True, timeout=5)
            self.assertFalse(directory.exists())
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
            process.stdout.close()
            process.stderr.close()
            if directory is not None and directory.exists():
                fixture.cleanup(directory)

    def test_close_refuses_hold_accepted_after_release_sweep(self):
        run = fixture.Fixture(ipv6=False)
        entered, resume, received = threading.Event(), threading.Event(), threading.Event()
        server = run.servers['http4']
        shutdown = server.shutdown
        def delayed_shutdown():
            entered.set()  # close already set stopped and swept every existing hold.
            resume.wait(5)
            shutdown()
        server.shutdown = delayed_shutdown
        closer = threading.Thread(target=run.close)
        def late_request():
            try:
                self.request('/hold/late', run=run)
            except (http.client.RemoteDisconnected, ConnectionError, TimeoutError):
                pass
            finally:
                received.set()
        client = threading.Thread(target=late_request)
        try:
            closer.start()
            self.assertTrue(entered.wait(3))
            client.start()
            deadline = time.monotonic() + 3
            while not received.is_set() and time.monotonic() < deadline:
                with run.lock:
                    if 'late' in run.holds:
                        break
                received.wait(.01)
            resume.set()
            closer.join(2)
            self.assertFalse(closer.is_alive(), 'late hold must not delay server_close')
            self.assertNotIn('late', run.holds)
            self.assertEqual('stopped', fixture.read_run(run.directory)[1]['state'])
        finally:
            resume.set()
            with run.lock:
                for release in run.holds.values():
                    release.set()
            closer.join(5)
            if client.ident is not None:
                client.join(5)
            run.close()
            fixture.cleanup(run.directory)

    def test_close_releases_pending_requests_and_cleanup_preserves_unexpected_files(self):
        run = fixture.Fixture(ipv6=False)
        addresses = [(s.server_address[0], s.server_port) for s in run.servers.values()]
        disconnected = []
        def pending():
            try:
                self.request('/hold/close', run=run)
            except (http.client.RemoteDisconnected, ConnectionError):
                disconnected.append(True)
        thread = threading.Thread(target=pending)
        thread.start()
        try:
            deadline = time.monotonic() + 3
            while 'close' not in run.holds and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertIn('close', run.holds)
            fixture.control(run.directory, '/stop')
            self.assertTrue(run.stopped.is_set())
            run.close()
            thread.join(3)
            self.assertFalse(thread.is_alive())
            self.assertEqual([True], disconnected)
            self.assertTrue(all(not thread.is_alive() for thread in run.threads))
            for address in addresses:
                with self.assertRaises(OSError):
                    socket.create_connection(address, timeout=0.2)
            self.assertEqual('stopped', fixture.read_run(run.directory)[1]['state'])
            extra = run.directory / 'keep.txt'
            extra.write_text('preserve')
            with self.assertRaisesRegex(ValueError, 'unexpected'):
                fixture.cleanup(run.directory)
            self.assertEqual('preserve', extra.read_text())
            extra.unlink()
            fixture.cleanup(run.directory)
            self.assertFalse(run.directory.exists())
        finally:
            run.close()
            thread.join(3)
            if run.directory.exists():
                fixture.cleanup(run.directory)


if __name__ == '__main__':
    unittest.main()
