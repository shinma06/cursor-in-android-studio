"""Browser QA input checks only: no IDE, native JCEF, OS trust edits or external hosts."""
import http.client
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
