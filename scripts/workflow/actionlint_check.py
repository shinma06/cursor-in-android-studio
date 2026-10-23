#!/usr/bin/env python3
"""Run pinned actionlint without dropping the native delivery queue guarantee."""
import argparse
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DELIVERY = Path('.github/workflows/delivery.yml')
CONCURRENCY = ('concurrency:\n'
               '  group: plugin-release-delivery\n'
               '  queue: max\n'
               '  cancel-in-progress: false\n')


def delivery_projection(source):
    # shortcut: actionlint 1.7.12 lacks concurrency.queue; remove this projection when its pin supports it.
    if source.count(CONCURRENCY) != 1:
        raise ValueError('Delivery requires one exact group / queue:max / cancel-in-progress:false block')
    start = source.index(CONCURRENCY)
    if start and source[start - 1] != '\n':
        raise ValueError('Delivery concurrency must be a top-level mapping')
    # Validate the exact native field above; leave every other byte and diagnostic line intact.
    projected = CONCURRENCY.replace('  queue: max\n', '\n')
    return source[:start] + projected + source[start + len(CONCURRENCY):]


def check(actionlint):
    source = (ROOT / DELIVERY).read_text()
    projected = delivery_projection(source)
    others = sorted(p for p in (ROOT / '.github/workflows').iterdir()
                    if p.suffix in ('.yml', '.yaml') and p != ROOT / DELIVERY)
    command = [str(actionlint), '-shellcheck=', '-pyflakes=']
    if others:
        subprocess.run([*command, *(str(p.relative_to(ROOT)) for p in others)], cwd=ROOT, check=True)
    subprocess.run([*command, '-stdin-filename', str(DELIVERY), '-'], cwd=ROOT,
                   input=projected, text=True, check=True)


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--actionlint', type=Path, required=True)
    check(parser.parse_args().actionlint)


if __name__ == '__main__':
    main()
