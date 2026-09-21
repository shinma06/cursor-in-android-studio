#!/usr/bin/env python3
"""Exercise real Gradle input guards; SDK paths remain local command arguments."""
import argparse
import os
import textwrap
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = 'AI-261.23567.138.2611.15503007'


def gradle(arguments, succeeds, diagnostic):
    result = subprocess.run(['./gradlew', '--console=plain', *arguments], cwd=ROOT,
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if (result.returncode == 0) != succeeds or diagnostic not in result.stdout:
        raise AssertionError(result.stdout)



def check_legacy_sdk_selection():
    workflow = (ROOT / '.github/workflows/branch-zip.yml').read_text()
    select = textwrap.dedent(workflow.split('        id: sdk\n        run: |\n', 1)[1].split('      - ', 1)[0])
    build = textwrap.dedent(workflow.split('          LEGACY_SDK: ${{ steps.sdk.outputs.legacy }}\n        run: |\n', 1)[1].split('      - ', 1)[0])
    for properties, legacy in [('platformPath=/old/sdk\n', 'true'),
                               ('  platformPath = /old/sdk\n', 'true'),
                               ('# platformPath=/comment\npluginVersion=0.1.0-SNAPSHOT\n', 'false')]:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'gradle.properties').write_text(properties)
            output = root / 'output'
            subprocess.run(['bash', '-e', '-c', select], cwd=root, check=True,
                           env=dict(os.environ, GITHUB_OUTPUT=str(output)))
            assert output.read_text().strip() == 'legacy=' + legacy
            (root / 'gradlew').write_text('#!/bin/sh\nprintf "%s\n" "$@" > arguments\n')
            (root / 'gradlew').chmod(0o700)
            subprocess.run(['bash', '-e', '-c', build], cwd=root, check=True,
                           env=dict(os.environ, LEGACY_SDK=legacy))
            arguments = (root / 'arguments').read_text().splitlines()
            assert arguments == ['buildPlugin', '--console=plain'] + (
                ['-PplatformPath=/tmp/android-studio'] if legacy == 'true' else [])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--local-sdk', required=True, help='Verified Quail 1 SDK root')
    parser.add_argument('--wrong-sdk', required=True, help='Another valid installed SDK root')
    args = parser.parse_args()
    check_legacy_sdk_selection()
    gradle(['verifyBuildSdk'], True, 'Compile SDK verified: ' + EXPECTED)
    gradle(['verifyBuildSdk', '-PuseLocalPlatform=true', '-PplatformPath=' + args.local_sdk],
           True, 'Compile SDK verified: ' + EXPECTED)
    # Even when classes/resources are cached, a newer local SDK must not package.
    gradle(['buildPlugin', '-PuseLocalPlatform=true', '-PplatformPath=' + args.wrong_sdk],
           False, 'Compile SDK must be Quail 1')
    with tempfile.TemporaryDirectory() as absent_sdk:
        gradle(['verifyBuildSdk', '-PplatformPath=' + absent_sdk],
               True, 'Compile SDK verified: ' + EXPECTED)
        result = subprocess.run(['./gradlew', 'buildPlugin', '--console=plain',
                                 '-PuseLocalPlatform=true', '-PplatformPath=' + absent_sdk],
                                cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if result.returncode == 0 or 'BUILD FAILED' not in result.stdout:
            raise AssertionError(result.stdout)
    gradle(['help', '-PpluginVersion=../invalid'], False, 'pluginVersion must be')
    print('Build input checks passed: default/local SDK, wrong/unknown SDK, implicit override, invalid version')


if __name__ == '__main__':
    main()
