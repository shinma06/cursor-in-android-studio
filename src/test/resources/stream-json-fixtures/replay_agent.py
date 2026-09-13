"""Finite synthetic print output. No shell, provider, network, or workspace edits."""
import json
import os
from pathlib import Path
import sys
import time


def main():
    scenario, root, destination = sys.argv[1:4]
    root, destination = Path(root), Path(destination)
    marker = root / 'ACP_SYNTHETIC_FIXTURE.json'
    if root.resolve() != Path.cwd().resolve() or marker.is_symlink() or json.loads(marker.read_text()) != {
        'schema': 1, 'purpose': 'synthetic-acp-fixture', 'workspace': str(root),
    } or destination.is_symlink() or not destination.is_dir() or destination.resolve().is_relative_to(root):
        raise ValueError('Invalid synthetic scope')
    descriptor = os.open(destination / f'wire-{os.getpid()}.jsonl', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    control = destination / f'control-{os.getpid()}'
    control.mkdir(mode=0o700)
    with os.fdopen(descriptor, 'w', encoding='utf-8') as capture:
        def record(direction, payload):
            capture.write(json.dumps({'direction': direction, 'pid': os.getpid(),
                'monotonic_ns': time.monotonic_ns(), 'payload': payload}, ensure_ascii=False) + '\n')
            capture.flush()

        def send(payload, newline=True):
            record('out', payload)
            print(json.dumps(payload, ensure_ascii=False), end='\n' if newline else '', flush=True)

        record('in', {'argv': sys.argv[4:]})
        send({'type': 'system', 'subtype': 'init', 'session_id': 'synthetic-session'})
        answer = 'はいはい😀😀'
        if scenario != 'print-result-only':
            for index, text in enumerate(['はい', 'はい', '😀', '😀']):
                send({'type': 'assistant', 'text': text, 'timestamp_ms': index + 1})
        if scenario == 'print-tools':
            send({'type': 'assistant', 'text': answer, 'timestamp_ms': 5, 'model_call_id': 'synthetic-flush'})
            for filename, kind in [('02_edit_completed.jsonl', 'editToolCall'), ('03_shell_completed.jsonl', 'shellToolCall')]:
                completed = json.loads((Path(__file__).parent / filename).read_text())
                call = completed['tool_call'][kind]
                completed = {'type': 'tool_call', 'subtype': 'completed', 'call_id': 'synthetic-' + kind,
                             'session_id': 'synthetic-session', 'tool_call': {kind: call}}
                if kind == 'editToolCall':
                    target = str(root / 'a.txt')
                    call['args']['path'] = call['result']['success']['path'] = target
                    call['result']['success']['message'] = 'Synthetic event only; disk is unchanged'
                    call['result']['success']['diffString'] = '--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n-hello\n+world'
                started = {'type': 'tool_call', 'subtype': 'started', 'call_id': completed['call_id'],
                           'tool_call': {kind: {'args': call['args']}}}
                send(started)
                send(completed)
                send(completed)  # Same-call duplicate, not a second real edit/command.
            send({'type': 'assistant', 'text': '完了', 'timestamp_ms': 6})
            answer += '完了'
        if scenario != 'print-result-only':
            send({'type': 'assistant', 'text': answer})
        result = {'type': 'result', 'subtype': 'success', 'session_id': 'synthetic-session',
                  'request_id': 'Synthetic-Opaque', 'is_error': scenario == 'print-error', 'result': answer}
        if scenario == 'print-usage':
            result['usage'] = {'inputTokens': 28791, 'outputTokens': 141, 'cacheReadTokens': 5748, 'cacheWriteTokens': 0}
        elif scenario == 'print-partial':
            result['usage'] = {'outputTokens': 0}
        send(result, newline=scenario != 'print-hold')
        (control / 'result-written').touch()
        if scenario == 'print-hold':
            deadline = time.monotonic() + 300
            while not (control / 'release').exists():
                if time.monotonic() >= deadline:
                    record('exit', {'code': 64, 'reason': 'release timeout'})
                    return 64
                time.sleep(.01)
        code = 7 if scenario == 'print-abnormal' else 0
        if code:
            print('Synthetic abnormal exit', file=sys.stderr)
            record('stderr', {'text': 'Synthetic abnormal exit'})
        record('exit', {'code': code})
        return code


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError, KeyError, TypeError):
        print('Synthetic print fixture refused.', file=sys.stderr)
        sys.exit(64)
