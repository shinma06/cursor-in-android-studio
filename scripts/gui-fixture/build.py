#!/usr/bin/env python3
"""Build a new, fixed-source macOS Swing probe; never launch or install it."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def run(*args, cwd=None):
    return subprocess.check_output(args, cwd=cwd, text=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--run', required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}', args.run):
        parser.error('--run must be 1..64 lowercase letters, digits or hyphens')
    source = Path(__file__).resolve().parent
    repo = source.parent.parent
    if run('git', 'status', '--porcelain', cwd=repo):
        parser.error('Commit changes before building a fixed-source fixture')
    head = run('git', 'rev-parse', 'HEAD', cwd=repo)
    base = run('git', 'rev-parse', 'origin/main', cwd=repo)
    jdk = args.jdk.resolve()
    for tool in ('java', 'javac', 'jar', 'jpackage'):
        if not (jdk / 'bin' / tool).is_file():
            parser.error(f'Missing JDK tool: {tool}')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    classes = output / 'classes'
    classes.mkdir()
    jars = output / 'input'
    jars.mkdir()
    run(str(jdk / 'bin/javac'), '-encoding', 'UTF-8', '-d', str(classes), str(source / 'SwingCuaFixture.java'))
    (classes / 'identity.properties').write_text(f'head={head}\nbase={base}\nrun={args.run}\n')
    jar = jars / 'swing-cua.jar'
    run(str(jdk / 'bin/jar'), '--create', '--file', str(jar), '--main-class', 'SwingCuaFixture',
        '--date=2026-01-01T00:00:00Z', '-C', str(classes), '.')
    # Separate product-independent bundle ID, constant across this fixture's restarts.
    bundle = 'com.cursoragent.fixture.swingcua'
    run(str(jdk / 'bin/jpackage'), '--type', 'app-image', '--name', 'SwingCuaProbe',
        '--input', str(jars), '--main-jar', jar.name, '--main-class', 'SwingCuaFixture',
        '--dest', str(output / 'app'), '--app-version', '1.0',
        '--mac-package-identifier', bundle, '--add-modules', 'java.desktop,java.logging')
    app = output / 'app/SwingCuaProbe.app'
    packaged = app / 'Contents/app/swing-cua.jar'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if hashlib.sha256(packaged.read_bytes()).hexdigest() != digest:
        raise RuntimeError('Packaged JAR differs from plain-Java probe')
    manifest = dict(head=head, base=base, run=args.run, jar_sha256=digest, bundle_id=bundle,
                    app=str(app), jdk=str(jdk), java_version=run(str(jdk / 'bin/javac'), '--version'))
    (output / 'manifest.local.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({key: value for key, value in manifest.items() if key not in ('jdk', 'app')}, indent=2))


if __name__ == '__main__':
    main()
