#!/usr/bin/env python3
"""Exercise real Gradle input guards; SDK paths remain local command arguments."""
import argparse
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--local-sdk', required=True, help='Verified Quail 1 SDK root')
    parser.add_argument('--wrong-sdk', required=True, help='Another valid installed SDK root')
    args = parser.parse_args()
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
