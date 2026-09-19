#!/usr/bin/env python3
"""Prepare a pinned launcher for the existing synthetic ACP server. No IDE changes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import sys

MARKER = 'ACP_SYNTHETIC_FIXTURE.json'
PURPOSE = 'synthetic-acp-fixture'
ACP_SCENARIOS = ('permission', 'normal', 'eof', 'cancel', 'child', 'bad-config', 'commands-delayed', 'events', 'questions', 'plan')
PRINT_SCENARIOS = ('print-usage', 'print-missing', 'print-partial', 'print-repeat', 'print-result-only', 'print-error', 'print-abnormal', 'print-hold', 'print-tools')
PRINT_VERSION = '2026.09.10-fd3934a'  # Synthetic producer, not an installed Cursor version.


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def workspace_marker(workspace):
    marker = workspace / MARKER
    if marker.is_symlink() or not marker.is_file():
        raise ValueError('Explicit synthetic workspace marker is required')
    value = json.loads(marker.read_text(encoding='utf-8'))
    if not isinstance(value, dict) or type(value.get('schema')) is not int or value != {
        'schema': 1, 'purpose': PURPOSE, 'workspace': str(workspace),
    }:
        raise ValueError('Synthetic workspace marker does not match this root')
    return marker


def prepare(workspace, output, scenario='permission'):
    if scenario not in ACP_SCENARIOS + PRINT_SCENARIOS:
        raise ValueError('Unknown finite scenario')
    workspace = workspace.resolve(strict=True)
    if not workspace.is_dir():
        raise ValueError('Workspace must be a directory')
    marker = workspace_marker(workspace)
    output = output.absolute()
    if output.resolve().is_relative_to(workspace):
        raise ValueError('Capture output must be outside the context workspace')
    script = Path(__file__).resolve()
    fake = script.parents[2] / ('src/test/resources/stream-json-fixtures/replay_agent.py' if scenario in PRINT_SCENARIOS else 'src/test/resources/acp/fake_agent.py')
    if not fake.is_file() or fake.is_symlink():
        raise ValueError('Fixed repository fake is unavailable')
    python = Path(sys.executable).resolve(strict=True)
    output.mkdir(mode=0o700, parents=False, exist_ok=False)
    output = output.resolve()
    capture = output / 'capture'
    capture.mkdir(mode=0o700)
    config = {
        'schema': 1, 'purpose': PURPOSE, 'workspace': str(workspace), 'scenario': scenario,
        'python_sha256': digest(python),
        'marker_sha256': digest(marker), 'python': str(python),
        'fake': str(fake), 'fake_sha256': digest(fake),
        'adapter': str(script), 'adapter_sha256': digest(script),
        'capture': str(capture),
        'inputs': {str(fake.parent / name): digest(fake.parent / name) for name in ('02_edit_completed.jsonl', '03_shell_completed.jsonl')} if scenario in PRINT_SCENARIOS else {},
    }
    config_path = output / 'launch.json'
    config_path.write_text(json.dumps(config, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    config_path.chmod(0o600)
    launcher = output / 'agent-fixture'
    command = [str(python), '-I', str(script), 'run', str(config_path)]
    launcher.write_text('#!/bin/sh\nexec ' + shlex.join(command) + ' "$@"\n', encoding='utf-8')
    launcher.chmod(0o700)
    return launcher


def run(config_path, arguments):
    config_path = Path(config_path)
    if config_path.is_symlink():
        raise ValueError('Launch configuration must not be a symlink')
    config = json.loads(config_path.read_text(encoding='utf-8'))
    if type(config['schema']) is not int or config['schema'] != 1 or config['purpose'] != PURPOSE:
        raise ValueError('Invalid synthetic launch configuration')
    scenario = config['scenario']
    if scenario not in ACP_SCENARIOS + PRINT_SCENARIOS:
        raise ValueError('Unknown finite scenario')
    root = Path(config['workspace']).resolve(strict=True)
    if Path.cwd().resolve() != root:
        raise ValueError('Working directory does not match the pinned synthetic root')
    if digest(workspace_marker(root)) != config['marker_sha256']:
        raise ValueError('Synthetic marker changed; prepare a new run')
    script, fake = Path(config['adapter']), Path(config['fake'])
    if script != Path(__file__).resolve() or digest(script) != config['adapter_sha256']:
        raise ValueError('Adapter changed; prepare a new run')
    expected_fake = script.parents[2] / ('src/test/resources/stream-json-fixtures/replay_agent.py' if scenario in PRINT_SCENARIOS else 'src/test/resources/acp/fake_agent.py')
    if fake != expected_fake or fake.is_symlink() or digest(fake) != config['fake_sha256']:
        raise ValueError('Fake changed; prepare a new run')
    expected_inputs = {str(fake.parent / name): digest(fake.parent / name) for name in ('02_edit_completed.jsonl', '03_shell_completed.jsonl')} if scenario in PRINT_SCENARIOS else {}
    if config['inputs'] != expected_inputs or any(Path(name).is_symlink() for name in expected_inputs):
        raise ValueError('Print inputs changed; prepare a new run')
    python = Path(config['python'])
    if python != Path(sys.executable).resolve() or not python.is_absolute() or digest(python) != config['python_sha256']:
        raise ValueError('Python does not match the pinned interpreter')
    capture = Path(config['capture'])
    if capture.is_symlink() or capture.resolve().is_relative_to(root) or capture.resolve() != config_path.resolve().parent / 'capture' or not capture.is_dir():
        raise ValueError('Capture directory does not match this run')
    if scenario in PRINT_SCENARIOS:
        if arguments == ['--version']:
            print(PRINT_VERSION)
            return
        validate_print_arguments(arguments, root)
        args = [str(python), '-I', str(fake), scenario, str(root), str(capture), *arguments]
    else:
        if arguments != ['acp']:
            raise ValueError('ACP launcher accepts exactly: acp')
        args = [str(python), '-I', str(fake), scenario, str(root), '--capture', str(capture)]
    # No inherited credentials, executable lookup, real metadata command, or fallback.
    os.execve(str(python), args, {})


def validate_print_arguments(arguments, root):
    prefix = ['-p', '--output-format', 'stream-json', '--stream-partial-output', '--trust', '--workspace', str(root)]
    if arguments[:7] != prefix or len(arguments) < 8 or not arguments[-1] or len(arguments[-1]) > 1048576:
        raise ValueError('Expected the Plugin print contract and one synthetic prompt')
    options = arguments[7:-1]
    seen = set()
    while options:
        name, *options = options
        if name in seen:
            raise ValueError('Duplicate option')
        seen.add(name)
        if name in ('-w', '--force', '--auto-review'):
            continue
        if name not in ('--resume', '--model', '--mode', '--sandbox') or not options:
            raise ValueError('Unknown print option')
        value, *options = options
        if not value or value.startswith('-') or len(value) > 1024 or any(ord(c) < 32 for c in value):
            raise ValueError('Invalid option value')
        if name == '--mode' and value not in ('ask', 'plan'):
            raise ValueError('Unknown mode')
        if name == '--sandbox' and value not in ('enabled', 'disabled'):
            raise ValueError('Unknown sandbox')
    if {'--force', '--auto-review'} <= seen:
        raise ValueError('Conflicting permission flags')


def main():
    try:
        if len(sys.argv) > 1 and sys.argv[1] == 'run':
            if len(sys.argv) < 3:
                raise ValueError('Launch configuration is required')
            run(sys.argv[2], sys.argv[3:])
        else:
            parser = argparse.ArgumentParser(description=__doc__)
            parser.add_argument('--workspace', required=True, type=Path)
            parser.add_argument('--output', required=True, type=Path)
            parser.add_argument('--scenario', choices=ACP_SCENARIOS + PRINT_SCENARIOS, default='permission')
            args = parser.parse_args()
            print(prepare(args.workspace, args.output, args.scenario))
    except (ValueError, KeyError, OSError, TypeError):
        print('Synthetic ACP fixture refused: check arguments, marker, root and pinned files.', file=sys.stderr)
        return 64
    return 0


if __name__ == '__main__':
    sys.exit(main())
