#!/usr/bin/env python3
"""Owned loopback inputs for Browser QA; no browser, file serving, or OS setup."""
import argparse
import hmac
import hashlib
import http.client
import ipaddress
import json
from pathlib import Path
import re
import secrets
import signal
import socket
import socketserver
import ssl
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlsplit

IDN = 'xn--r8jz45g.xn--zckzah'
FILES = {'.owner', 'manifest.json', 'requests.jsonl', 'ca.key', 'ca.pem', 'ca.srl',
         'server.key', 'server.csr', 'server.pem', 'server.ext', 'hosts.fragment'}
FORBIDDEN = {'file': 'file:///__browser_qa_no_such_file__',
             'javascript': "javascript:alert('browser-qa-blocked')",
             'data': 'data:text/html,browser-qa-blocked',
             'custom': 'browser-qa-invalid://fixture'}
PAGE_A = '''<!doctype html><meta charset="utf-8"><title>Browser QA A</title>
<h1>管理ページ A</h1><a href="/b">Bへ</a> <a href="#target">アンカー</a>
<a href="/redirect">HTTP redirect</a> <a href="/404">404</a> <a href="/500">500</a>
<a href="/cut">途中切断</a> <a href="/delay">固定遅延</a>
<a href="/hold/first">明示release待ち first</a>
<button onclick="window.open('/b', '_blank')">popup B</button>
<p>禁止scheme（明示操作で確認）</p>''' + ''.join(
    '<p><a href="' + target.replace('"', '&quot;') + '">' + kind + 'リンク</a> '
    '<a href="/redirect-' + kind + '">' + kind + ' redirect</a></p>'
    for kind, target in FORBIDDEN.items()) + '<div style="height:900px"></div><h2 id="target">アンカー到達</h2>'
PAGE_B = '<!doctype html><meta charset="utf-8"><title>Browser QA B</title><h1>管理ページ B</h1><a href="/a">Aへ</a>'


class Server(ThreadingHTTPServer):
    daemon_threads = False
    allow_reuse_address = False

    def __init__(self, owner, host, tls=None):
        if not ipaddress.ip_address(host).is_loopback:
            raise ValueError('loopback only')
        self.address_family = socket.AF_INET6 if ':' in host else socket.AF_INET
        self.owner, self.tls = owner, tls
        super().__init__((host, 0), Handler)

    def server_bind(self):
        # HTTPServer normally calls getfqdn; these fixed numeric listeners need no DNS.
        socketserver.TCPServer.server_bind(self)
        self.server_name, self.server_port = self.server_address[:2]

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(3)
        if self.tls:
            try:
                connection = self.tls.wrap_socket(connection, server_side=True)
            except Exception:
                connection.close()
                raise
        return connection, address

    def handle_error(self, request, client_address):
        self.owner.log('connection-ended')  # No raw headers or request contents.


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def send(self, status, body='', headers=None):
        data = body.encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(data)
        self.wfile.flush()
        self.server.owner.log('response', path=self.path[:2048], status=status)

    def permitted(self):
        try:
            host = urlsplit('//' + self.headers.get('Host', '')).hostname
        except ValueError:
            host = None
        return host in ('127.0.0.1', '::1', 'localhost', IDN) and self.path.startswith('/') and not self.path.startswith('//')

    def do_GET(self):
        run = self.server.owner
        run.log('request', method='GET', path=self.path[:2048])
        if not self.permitted():
            self.send(403)
            return
        path = unquote(urlsplit(self.path).path)
        if path in ('/', '/a'):
            self.send(200, PAGE_A)
        elif path == '/b':
            self.send(200, PAGE_B)
        elif path == '/検索':
            self.send(200, '<!doctype html><meta charset="utf-8"><h1>IDN・Unicode検索ページ</h1>')
        elif path == '/identity':
            self.send(200, run.identity)
        elif path == '/redirect':
            self.send(302, headers={'Location': '/b'})
        elif path.removeprefix('/redirect-') in FORBIDDEN:
            self.send(302, headers={'Location': FORBIDDEN[path.removeprefix('/redirect-')]})
        elif path in ('/404', '/500'):
            self.send(int(path[1:]), '管理されたHTTPエラー')
        elif path == '/cut':
            self.send_response(200)
            self.send_header('Content-Length', '65536')
            self.end_headers()
            self.wfile.write(b'partial')
            self.wfile.flush()
            run.log('cut', path=path, status=200)
            self.close_connection = True
            self.connection.shutdown(socket.SHUT_RDWR)
        elif path == '/delay':
            run.stopped.wait(1)
            self.send(200, '固定遅延後の応答')
        elif re.fullmatch(r'/hold/[A-Za-z0-9_-]{1,32}', path):
            tag = path.rsplit('/', 1)[1]
            with run.lock:
                if len(run.holds) >= 64 and tag not in run.holds:
                    self.send(429)
                    return
                release = run.holds.setdefault(tag, threading.Event())
            run.log('held', tag=tag)
            released = release.wait(120)
            if run.stopped.is_set():
                return
            self.send(200 if released else 504, '明示release後の応答' if released else 'release待ち時間超過')
        else:
            self.send(404)

    def do_POST(self):
        run = self.server.owner
        if not self.permitted() or not hmac.compare_digest(self.headers.get('X-Fixture-Token', ''), run.token):
            self.send(403)
            return
        if self.path == '/stop':
            run.stopped.set()
            self.send(200, '停止します')
        elif re.fullmatch(r'/release/[A-Za-z0-9_-]{1,32}', self.path):
            tag = self.path.rsplit('/', 1)[1]
            with run.lock:
                release = run.holds.get(tag)
            if release is None:
                self.send(404)
                return
            release.set()
            run.log('released', tag=tag)
            self.send(200, '解放しました')
        else:
            self.send(404)


class Fixture:
    def __init__(self, ipv6=True):
        self.directory = Path(tempfile.mkdtemp(prefix='browser-qa-'))
        self.identity, self.token = secrets.token_hex(16), secrets.token_hex(24)
        self.lock, self.stopped = threading.RLock(), threading.Event()
        self.holds, self.servers, self.threads = {}, {}, []
        self.closed = False
        self.manifest = {'schema': 1, 'run_id': self.identity, 'state': 'preparing',
                         'control_token': self.token, 'idn': IDN, 'urls': {},
                         'source_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
        (self.directory / '.owner').write_text(self.identity)
        self.write_manifest()
        try:
            self.certificates()
            self.manifest['cert_sha256'] = {name: hashlib.sha256((self.directory / name).read_bytes()).hexdigest()
                                            for name in ('ca.pem', 'server.pem')}
            tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            tls.minimum_version = ssl.TLSVersion.TLSv1_2
            tls.load_cert_chain(self.directory / 'server.pem', self.directory / 'server.key')
            for family, host in [('4', '127.0.0.1')] + ([('6', '::1')] if ipv6 else []):
                try:
                    for scheme, context in [('http', None), ('https', tls)]:
                        name = scheme + family
                        server = Server(self, host, context)
                        self.servers[name] = server
                        thread = threading.Thread(target=server.serve_forever, daemon=True)
                        thread.start()
                        self.threads.append(thread)
                        literal = '[' + host + ']' if family == '6' else host
                        self.manifest['urls'][name] = f'{scheme}://{literal}:{server.server_port}'
                except OSError as error:
                    if family == '4':
                        raise
                    self.manifest['ipv6_unavailable'] = str(error)
            self.manifest['state'] = 'running'
            self.write_manifest()
            self.log('started', urls=self.manifest['urls'])
        except BaseException:
            self.close()
            raise

    def certificates(self):
        def openssl(*args):
            subprocess.run(['openssl', *args], cwd=self.directory, check=True, capture_output=True, timeout=30)
        openssl('req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', 'ca.key', '-out', 'ca.pem',
                '-days', '2', '-subj', '/CN=Browser QA ' + self.identity,
                '-addext', 'basicConstraints=critical,CA:TRUE', '-addext', 'keyUsage=critical,keyCertSign,cRLSign')
        openssl('req', '-new', '-newkey', 'rsa:2048', '-nodes', '-keyout', 'server.key', '-out', 'server.csr', '-subj', '/CN=localhost')
        (self.directory / 'server.ext').write_text(
            'basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\n'
            'extendedKeyUsage=serverAuth\nsubjectAltName=DNS:localhost,DNS:' + IDN + ',IP:127.0.0.1,IP:::1\n')
        openssl('x509', '-req', '-in', 'server.csr', '-CA', 'ca.pem', '-CAkey', 'ca.key', '-CAcreateserial',
                '-out', 'server.pem', '-days', '2', '-extfile', 'server.ext')
        for name in ('ca.key', 'server.key'):
            (self.directory / name).chmod(0o600)
        (self.directory / 'hosts.fragment').write_text('127.0.0.1 ' + IDN + '\n')

    def log(self, event, **fields):
        with self.lock, (self.directory / 'requests.jsonl').open('a') as output:
            output.write(json.dumps({'run_id': self.identity, 'at': time.time(), 'event': event, **fields}, ensure_ascii=False) + '\n')

    def write_manifest(self):
        (self.directory / 'manifest.json').write_text(json.dumps(self.manifest, indent=2) + '\n')

    def close(self):
        if self.closed:
            return
        self.closed = True
        self.stopped.set()
        with self.lock:
            for release in self.holds.values():
                release.set()
        for server in self.servers.values():
            server.shutdown()
            server.server_close()
        for thread in self.threads:
            thread.join(5)
        self.manifest['state'] = 'stopped'
        self.write_manifest()
        self.log('stopped')


def read_run(directory):
    directory = Path(directory)
    if directory.is_symlink() or not directory.name.startswith('browser-qa-'):
        raise ValueError('owned Browser QA directory required')
    for name in ('.owner', 'manifest.json'):
        if (directory / name).is_symlink():
            raise ValueError('symlink is not owned state')
    manifest = json.loads((directory / 'manifest.json').read_text())
    if manifest.get('schema') != 1 or manifest['run_id'] != (directory / '.owner').read_text():
        raise ValueError('owner mismatch')
    return directory, manifest


def control(directory, path):
    _, manifest = read_run(directory)
    if manifest['state'] != 'running':
        raise ValueError('run is not running')
    url = urlsplit(manifest['urls']['http4'])
    if url.hostname != '127.0.0.1' or url.scheme != 'http':
        raise ValueError('owned IPv4 loopback required')
    connection = http.client.HTTPConnection('127.0.0.1', url.port, timeout=3)
    try:
        connection.request('GET', '/identity')
        if connection.getresponse().read().decode() != manifest['run_id']:
            raise ValueError('listener is no longer this run')
        connection.request('POST', path, headers={'X-Fixture-Token': manifest['control_token']})
        response = connection.getresponse()
        body = response.read().decode()
        if response.status != 200:
            raise ValueError(f'control failed: {response.status}')
        return body
    finally:
        connection.close()


def cleanup(directory):
    directory, manifest = read_run(directory)
    if manifest['state'] != 'stopped':
        raise ValueError('stop the owned run first')
    entries = list(directory.iterdir())
    if any(entry.name not in FILES or entry.is_symlink() or not entry.is_file() for entry in entries):
        raise ValueError('unexpected file; preserve directory for inspection')
    for entry in entries:
        entry.unlink()
    directory.rmdir()


def main():
    parser = argparse.ArgumentParser(__doc__)
    subs = parser.add_subparsers(dest='command', required=True)
    start = subs.add_parser('serve')
    start.add_argument('--ipv4-only', action='store_true', help='Explicitly leave IPv6 untested')
    for name in ('release', 'stop', 'cleanup'):
        sub = subs.add_parser(name)
        sub.add_argument('directory')
        if name == 'release':
            sub.add_argument('tag')
    args = parser.parse_args()
    if args.command == 'serve':
        fixture = Fixture(ipv6=not args.ipv4_only)
        for signum in (signal.SIGINT, signal.SIGTERM):
            signal.signal(signum, lambda *_: fixture.stopped.set())
        print(fixture.directory, flush=True)
        try:
            fixture.stopped.wait()
        finally:
            fixture.close()
    elif args.command == 'cleanup':
        cleanup(args.directory)
    elif args.command == 'release':
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,32}', args.tag):
            parser.error('tag must be 1–32 ASCII letters/digits/underscore/hyphen')
        print(control(args.directory, '/release/' + args.tag))
    else:
        print(control(args.directory, '/stop'))


if __name__ == '__main__':
    main()
