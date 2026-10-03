"""Offline evidence consistency and wire-size illustration; no product or live test."""
import base64
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent


def main():
    data = json.loads((ROOT / 'issue-10-observations.json').read_text())
    for name, expected in data['images'].items():
        png = (ROOT / 'issue-10-images' / name).read_bytes()
        assert len(png) == expected['bytes']
        assert hashlib.sha256(png).hexdigest() == expected['sha256']
        assert png[:8] == b'\x89PNG\r\n\x1a\n' and png[12:16] == b'IHDR'
        assert struct.unpack('>II', png[16:24]) == (600, 400)
        encoded = base64.b64encode(png)
        assert base64.b64decode(encoded, validate=True) == png
        assert len(encoded) == 4 * ((len(png) + 2) // 3)
    assert data['agent_capabilities'] == {'image': True, 'loadSession': True}
    assert data['acp_client_callback_requests'] == data['stderr_bytes'] == 0
    acp = {case['case']: case for case in data['acp']}
    assert len(acp) == 7
    for name in ('a1', 'a2', 'a3'):
        case = acp[name]
        assert case['answer']['cells'] == data['images'][case['image']]['cells']
        assert case['source_copy_deleted_before_send'] == (name != 'a1')
    assert all(case['stopReason'] == 'end_turn' for case in acp.values())
    assert 'R8Q2' not in acp['seed']['answer']
    assert acp['resume']['answer'] == acp['other-root']['answer'] == 'R8Q2'
    assert acp['invalid']['answer'] == 'UNAVAILABLE'
    prints = {case['case']: case for case in data['print']}
    assert len(prints) == 7
    for name, image in [('normal', '7c4e2a91.png'), ('resume', 'a90d63f2.png'), ('isolated', '7c4e2a91.png')]:
        assert prints[name]['answer']['cells'] == data['images'][image]['cells']
    for name in ('missing', 'other-root', 'isolated-untracked', 'isolated-absolute'):
        assert prints[name]['answer'] == {'unavailable': True}
        assert any(event.get('error') == 'File not found' for event in prints[name]['tool_events'])
    assert all(case['exit'] == 0 and case['is_error'] is False for case in prints.values())
    assert prints['isolated']['cwd'] == '<isolated-root>'
    assert prints['other-root']['cwd'] == '<other-root>'
    absolute = prints['isolated-absolute']
    assert '資料' in absolute['requested_path']
    assert any('资料' in event.get('path', '') for event in absolute['tool_events'])
    assert any(event['kind'] == 'globToolCall' for event in prints['other-root']['tool_events'])
    # A size illustration, not the product Gson serializer or its acceptance test.
    png = (ROOT / 'issue-10-images' / '7c4e2a91.png').read_bytes()
    frame = {'jsonrpc': '2.0', 'id': 1, 'method': 'session/prompt', 'params': {
        'sessionId': 'fixture', 'prompt': [{'type': 'text', 'text': ''},
        {'type': 'image', 'mimeType': 'image/png', 'data': base64.b64encode(png).decode()}]}}
    def size(text):
        frame['params']['prompt'][0]['text'] = text
        return len((json.dumps(frame, ensure_ascii=False, separators=(',', ':')) + '\n').encode())
    limit = data['limits']['frame_bytes']
    padding = limit - size('')
    assert size('x' * (padding - 1)) == limit - 1
    assert size('x' * padding) == limit
    assert size('x' * (padding + 1)) == limit + 1
    assert size('資' * padding) > limit  # Count UTF-8 bytes, not string length.
    assert 4 * ((data['limits']['normalized_png_bytes'] + 2) // 3) < limit
    print('PASS: 4 PNG hashes/dimensions, 14 live projections, deletion/root/Unicode boundaries, 4 wire-size examples')


if __name__ == '__main__':
    main()
