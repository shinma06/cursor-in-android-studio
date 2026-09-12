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


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def workspace_marker(workspace):
    marker = workspace / MARKER
    if marker.is_symlink() or not marker.is_file():
        raise ValueError('Explicit synthetic workspace marker is required')
    if json.loads(marker.read_text(encoding='utf-8')) != {
        'schema': 1, 'purpose': PURPOSE, 'workspace': str(workspace),
    }:
        raise ValueError('Synthetic workspace marker does not match this root')
    return marker


def prepare(workspace, output):
    workspace = workspace.resolve(strict=True)
    if not workspace.is_dir():
        raise ValueError('Workspace must be a directory')
    marker = workspace_marker(workspace)
    output = output.absolute()
    if output.resolve().is_relative_to(workspace):
        raise ValueError('Capture output must be outside the context workspace')
    script = Path(__file__).resolve()
    fake = script.parents[2] / 'src/test/resources/acp/fake_agent.py'
    if not fake.is_file() or fake.is_symlink():
        raise ValueError('Fixed repository fake is unavailable')
    python = Path(sys.executable).resolve(strict=True)
    output.mkdir(mode=0o700, parents=False, exist_ok=False)
    output = output.resolve()
    capture = output / 'capture'
    capture.mkdir(mode=0o700)
    config = {
        'schema': 1, 'purpose': PURPOSE, 'workspace': str(workspace),
        'marker_sha256': digest(marker), 'python': str(python),
        'fake': str(fake), 'fake_sha256': digest(fake),
        'adapter': str(script), 'adapter_sha256': digest(script),
        'capture': str(capture),
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
    if arguments != ['acp']:
        raise ValueError('Fixture launcher accepts exactly one argument: acp')
    config_path = Path(config_path)
    if config_path.is_symlink():
        raise ValueError('Launch configuration must not be a symlink')
    config = json.loads(config_path.read_text(encoding='utf-8'))
    if config['schema'] != 1 or config['purpose'] != PURPOSE:
        raise ValueError('Invalid synthetic launch configuration')
    root = Path(config['workspace']).resolve(strict=True)
    if Path.cwd().resolve() != root:
        raise ValueError('Working directory does not match the pinned synthetic root')
    if digest(workspace_marker(root)) != config['marker_sha256']:
        raise ValueError('Synthetic marker changed; prepare a new run')
    script, fake = Path(config['adapter']), Path(config['fake'])
    if script != Path(__file__).resolve() or digest(script) != config['adapter_sha256']:
        raise ValueError('Adapter changed; prepare a new run')
    if fake != script.parents[2] / 'src/test/resources/acp/fake_agent.py' or fake.is_symlink() or digest(fake) != config['fake_sha256']:
        raise ValueError('Fake changed; prepare a new run')
    python = Path(config['python'])
    if python != Path(sys.executable).resolve() or not python.is_absolute():
        raise ValueError('Python does not match the pinned interpreter')
    capture = Path(config['capture'])
    if capture.is_symlink() or capture.resolve().is_relative_to(root) or capture.resolve() != config_path.resolve().parent / 'capture' or not capture.is_dir():
        raise ValueError('Capture directory does not match this run')
    # No inherited provider credentials, shell lookup, metadata command, or fallback.
    os.execve(str(python), [str(python), '-I', str(fake), 'permission', str(root), '--capture', str(capture)], {})


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
            args = parser.parse_args()
            print(prepare(args.workspace, args.output))
    except (ValueError, KeyError, OSError, TypeError):
        print('Synthetic ACP fixture refused: check arguments, marker, root and pinned files.', file=sys.stderr)
        return 64
    return 0


if __name__ == '__main__':
    sys.exit(main())
