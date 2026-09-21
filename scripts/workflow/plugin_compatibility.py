#!/usr/bin/env python3
"""Seal one standard Plugin ZIP, then verify its bytes without invoking a build task."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
POLICY = Path(__file__).with_name('plugin_compatibility.json')
PLUGIN_ID = 'com.cursoragent.plugin'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def properties(text):
    return dict(line.split('=', 1) for line in text.splitlines() if '=' in line and not line.startswith('#'))


def plugin_identity(archive):
    identities = []
    with zipfile.ZipFile(archive) as outer:
        for name in outer.namelist():
            if not name.endswith('.jar'):
                continue
            with zipfile.ZipFile(io.BytesIO(outer.read(name))) as jar:
                if 'cursor-agent-build.properties' not in jar.namelist():
                    continue
                identity = properties(jar.read('cursor-agent-build.properties').decode())
                descriptor = ET.fromstring(jar.read('META-INF/plugin.xml'))
                require(descriptor.findtext('id') == PLUGIN_ID, 'Unexpected plugin ID')
                require(descriptor.findtext('version') == identity['plugin.version'], 'Descriptor/version mismatch')
                identity['classes'] = sum(n.endswith('.class') for n in jar.namelist())
                identities.append(identity)
    require(len(identities) == 1, 'Expected exactly one product build identity')
    return identities[0]


def seal(archive, source, policy):
    identity = plugin_identity(archive)
    require(re.fullmatch('[0-9a-f]{40}', source), 'Invalid source SHA')
    require(identity['source.commit'] == source and identity['source.state'] == 'clean', 'Source must match a clean build')
    require(identity['sdk.build'] == policy['targets']['quail1']['build'], 'ZIP was not built with oldest SDK')
    require(identity['jvm.target'] == '21' and identity['classes'] > 0, 'Expected JVM 21 product classes')
    return {'schema': 1, 'sha256': digest(archive), 'size': archive.stat().st_size, 'identity': identity}


def check_archive(archive, manifest, sha):
    require(re.fullmatch('[0-9a-f]{64}', sha), 'Invalid expected ZIP hash')
    require(digest(archive) == sha == manifest['sha256'], 'ZIP hash mismatch')
    require(archive.stat().st_size == manifest['size'], 'ZIP size mismatch')
    require(plugin_identity(archive) == manifest['identity'], 'ZIP identity mismatch')


def sdk_identity(sdk, target):
    info_files = [p for p in (sdk / 'product-info.json', sdk / 'Resources/product-info.json') if p.is_file()]
    require(len(info_files) == 1, 'SDK product info missing or ambiguous')
    info = json.loads(info_files[0].read_text())
    build = info['productCode'] + '-' + info['buildNumber'].removeprefix(info['productCode'] + '-')
    require(build == target['build'], 'Verification SDK full build mismatch')
    runtimes = [p for p in (sdk / 'jbr', sdk / 'jbr/Contents/Home') if (p / 'release').is_file()]
    require(len(runtimes) == 1, 'Bundled JBR missing or ambiguous; no fallback allowed')
    runtime = runtimes[0]
    release = {k: v.strip('"') for k, v in properties((runtime / 'release').read_text()).items()}
    require(release['JAVA_VERSION'] == target['java_version'], 'Bundled JBR version mismatch')
    actual = subprocess.check_output([str(runtime / 'bin/java'), '-version'], stderr=subprocess.STDOUT, text=True)
    require(f'"{target["java_version"]}"' in actual, 'Actual JBR differs from its release file')
    return runtime, {'distribution_version': target['version'], 'product_version': info['version'],
                     'build': build, 'java_version': release['JAVA_VERSION'], 'java_runtime': actual.strip()}


def download_sdk(target, directory):
    # Fixed official archive/checksum. A corrupt cache is an error, never a fallback SDK.
    directory.mkdir(parents=True, exist_ok=True)
    archive = directory / 'sdk.tar.gz'
    if not archive.exists():
        temporary = directory / 'sdk.download'
        subprocess.run(['curl', '-fL', '--retry', '3', '-o', str(temporary), target['url']], check=True)
        require(digest(temporary) == target['sha256'], 'SDK archive checksum mismatch')
        temporary.rename(archive)
    require(digest(archive) == target['sha256'], 'Cached SDK checksum mismatch')
    subprocess.run(['tar', '-xzf', str(archive), '-C', str(directory)], check=True)
    return directory / 'android-studio'


def check_reports(reports, log, target, manifest, policy):
    version = manifest['identity']['plugin.version']
    directory = reports / target['build'] / 'plugins' / PLUGIN_ID / version
    verdicts = list(reports.rglob('verification-verdict.txt'))
    require(verdicts == [directory / 'verification-verdict.txt'], 'Missing, extra or wrong-target verdict')
    verdict = verdicts[0].read_text().strip()
    require(re.fullmatch(r'Compatible\.(?: \d+ usages? of scheduled for removal API and \d+ usages? of deprecated API\.| \d+ usages? of deprecated API\.| \d+ usages? of experimental API\.| \d+ usages? of internal API\.?)*', verdict),
            'Verifier did not certify a recognized compatible verdict')
    telemetry = (directory / 'telemetry.txt').read_text()
    count = re.search(r'^Verified classes in plugin artifact: (\d+)$', telemetry, re.M)
    require(count and int(count[1]) == manifest['identity']['classes'], 'Skipped or incomplete class verification')
    require('Scheduled verifications (1):' in log and 'Finished 1 of 1 verifications' in log, 'Verification did not complete once')
    dependencies = (directory / 'dependencies.txt').read_text()
    require(dependencies.splitlines()[0] == f'{PLUGIN_ID}:{version}', 'Wrong dependency report identity')
    failures = [line.strip() for line in dependencies.splitlines() if '(failed)' in line or 'not resolved' in line]
    # Optional absences require an explicit, reviewed policy entry; no blanket optional exemption.
    for line in failures:
        match = re.search(r'\(failed\) ([\w.]+) \(optional\):', line)
        require(match and match[1] in policy['optional_absences'], 'Unresolved dependency: ' + line)
    for category, name in (('(?:scheduled for removal|deprecated)', 'deprecated-usages.txt'),
                           ('experimental', 'experimental-api-usages.txt'),
                           ('internal', 'internal-api-usages.txt')):
        if re.search(r'\b[1-9]\d* usages? of ' + category + ' API', verdict):
            require((directory / name).is_file(), 'Missing API detail report: ' + name)
    warning_hashes = {}
    required = {'verification-verdict.txt', 'dependencies.txt', 'telemetry.txt'}
    for report in directory.iterdir():
        if report.suffix != '.txt' or report.name in required:
            continue
        expected = policy['reviewed_api_reports'].get(report.name)
        require(expected and digest(report) == expected['sha256'], 'New/unreviewed report: ' + report.name)
        warning_hashes[report.name] = digest(report)
    # The fixed Verifier is intentionally parsed conservatively; new log warnings/errors need review.
    for line in log.splitlines():
        if re.search(r'\bERROR\b', line):
            raise ValueError('Verifier logged an error')
        if ' WARN ' in line:
            require(any(line.endswith(marker) for marker in policy['reviewed_log_warnings']), 'Unreviewed Verifier warning')
    return {'verdict': verdict, 'verified_classes': int(count[1]), 'optional_absences': failures,
            'reviewed_api_reports': warning_hashes,
            'reports': {str(p.relative_to(reports)): digest(p) for p in sorted(reports.rglob('*.txt'))}}


def verify(args, policy):
    target = policy['targets'][args.target]
    archive = args.archive.resolve()
    manifest = json.loads(args.manifest.read_text())
    check_archive(archive, manifest, args.sha256)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)  # Stale reports cannot make a new invocation pass.
    sdk = args.sdk.resolve() if args.sdk else download_sdk(target, args.sdk_cache.resolve())
    runtime, info = sdk_identity(sdk, target)
    reports = output / 'reports'
    command = ['./gradlew', 'verifyPlugin', '--console=plain',
               '-PverificationArchive=' + str(archive), '-PverificationIdePath=' + str(sdk),
               '-PverificationRuntime=' + str(runtime), '-PverificationReports=' + str(reports)]
    with (output / 'verifier.log').open('w') as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    check_archive(archive, manifest, args.sha256)
    require(result.returncode == 0, 'Gradle Verifier failed; inspect verifier.log')
    log = (output / 'verifier.log').read_text()
    require('Starting the IntelliJ Plugin Verifier ' + policy['verifier_version'] in log, 'Wrong/missing Verifier version')
    require(not re.search(r'> Task :(?:compile\w+|buildPlugin|jar|prepareSandbox)\b', log), 'Verification unexpectedly rebuilt the plugin')
    outcome = check_reports(reports, log, target, manifest, policy)
    receipt = {'schema': 1, 'status': 'passed', 'artifact': manifest, 'target': info,
               'verifier_version': policy['verifier_version'], **outcome}
    (output / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt, indent=2))


def main():
    parser = argparse.ArgumentParser(__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    seal_parser = sub.add_parser('seal')
    seal_parser.add_argument('--source', required=True)
    seal_parser.add_argument('--directory', type=Path, default=ROOT / 'build/distributions')
    check = sub.add_parser('verify')
    check.add_argument('--target', required=True, choices=('quail1', 'quail4'))
    check.add_argument('--archive', type=Path, required=True)
    check.add_argument('--manifest', type=Path, required=True)
    check.add_argument('--sha256', required=True)
    check.add_argument('--output', type=Path, required=True)
    check.add_argument('--sdk', type=Path)
    check.add_argument('--sdk-cache', type=Path)
    args = parser.parse_args()
    policy = json.loads(POLICY.read_text())
    if args.command == 'seal':
        archives = list(args.directory.glob('*.zip'))
        require(len(archives) == 1, 'Expected exactly one standard ZIP')
        manifest = seal(archives[0], args.source, policy)
        (args.directory / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        print(manifest['sha256'])
    else:
        require(args.sdk is not None or args.sdk_cache is not None, '--sdk or --sdk-cache is required')
        verify(args, policy)


if __name__ == '__main__':
    main()
