#!/usr/bin/env python3
"""Build once; preserve a reviewed candidate; publish the same bytes after promotion."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.parse import quote
import zipfile

import branch_zip as github
import plugin_compatibility as pc
from verification import verify_pr, git_read, PROMOTION

ROOT = Path(__file__).resolve().parents[2]
INPUTS = ('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
          'gradle/wrapper/gradle-wrapper.properties', 'scripts/workflow/plugin_compatibility.json')
MARKER = '<!-- release-candidate:v1 -->'


def read(path):
    return json.loads(path.read_text())


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def version_name(version):
    pc.require(re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version),
               'An explicitly selected final major.minor.patch version is required')
    return f'cursor-in-android-studio-{version}.zip'


def source_inputs(source):
    return {name: hashlib.sha256(git_read('show', f'{source}:{name}').encode()).hexdigest() for name in INPUTS}


def build(source, version, directory):
    name = version_name(version)
    pc.require(re.fullmatch('[0-9a-f]{40}', source), 'Full source SHA required')
    pc.require(git_read('rev-parse', 'HEAD') == source and not git_read('status', '--porcelain'), 'Clean fixed source required')
    git_read('fetch', '--no-tags', 'origin', 'develop')
    git_read('merge-base', '--is-ancestor', source, 'origin/develop')
    pc.require(not directory.is_relative_to(ROOT), 'Candidate output must be outside the source checkout')
    java = Path(os.environ['JAVA_HOME']) / 'bin/java'
    runtime = subprocess.check_output([str(java), '-version'], stderr=subprocess.STDOUT, text=True).strip()
    pc.require(re.search(r'version \"21\.', runtime), 'Gradle runtime JDK 21 required')
    directory.mkdir(parents=True, exist_ok=False)
    args = ['./gradlew', 'clean', 'test', 'buildPlugin', 'verifyPluginStructure', '--console=plain',
            '-PpluginVersion=' + version, '-PuseLocalPlatform=false']
    inputs = {'source': source, 'version': version, 'command': args, 'files': source_inputs(source), 'java_runtime': runtime}
    write(directory / 'inputs.json', inputs)  # Freeze version and inputs BEFORE building.
    env = {k: v for k, v in os.environ.items() if not k.endswith(('TOKEN', 'API_KEY'))}
    subprocess.run(args, cwd=ROOT, env=env, check=True)
    pc.require(not git_read('status', '--porcelain') and git_read('rev-parse', 'HEAD') == source, 'Source changed during build')
    archives = list((ROOT / 'build/distributions').glob('*.zip'))
    pc.require(len(archives) == 1, 'One standard ZIP required')
    policy = read(pc.POLICY)
    manifest = pc.seal(archives[0], source, policy)
    pc.require(manifest['identity']['plugin.version'] == version, 'Build version mismatch')
    with zipfile.ZipFile(archives[0]) as archive:
        inputs['libraries'] = sorted(Path(n).name for n in archive.namelist() if n.endswith('.jar'))
    write(directory / 'inputs.json', inputs)
    shutil.copyfile(archives[0], directory / name)
    write(directory / 'manifest.json', manifest)
    print(manifest['sha256'])


def candidate(directory, expected_hash):
    manifest = read(directory / 'manifest.json')
    inputs = read(directory / 'inputs.json')
    version = manifest['identity']['plugin.version']
    source = manifest['identity']['source.commit']
    pc.require(re.fullmatch('[0-9a-f]{40}', source), 'Invalid source')
    pc.require(inputs['source'] == source and inputs['version'] == version, 'Candidate inputs differ')
    pc.require(inputs['files'] == source_inputs(source), 'Build input files differ from fixed source')
    archive = directory / version_name(version)
    pc.check_archive(archive, manifest, expected_hash)
    with zipfile.ZipFile(archive) as product:
        pc.require(inputs['libraries'] == sorted(Path(n).name for n in product.namelist() if n.endswith('.jar')), 'Packaged dependencies differ')
    policy = json.loads(git_read('show', f'{source}:scripts/workflow/plugin_compatibility.json'))
    pc.require(pc.seal(directory / version_name(version), source, policy) == manifest, 'Invalid candidate identity')
    return manifest, policy


def check_evidence(directory, key, manifest, policy, sealed=False):
    result = read(directory / 'result.json')
    pc.require(result['status'] == 'passed' and result['artifact'] == manifest, 'Compatibility receipt does not match ZIP')
    target = policy['targets'][key]
    pc.require(result['verifier_version'] == policy['verifier_version'] and
               result['target']['build'] == target['build'] and result['target']['java_version'] == target['java_version'] and
               result['target']['distribution_version'] == target['version'] and
               f'"{target["java_version"]}"' in result['target']['java_runtime'], 'Wrong verifier/SDK/JBR receipt')
    log = (directory / ('verifier-summary.txt' if sealed else 'verifier.log')).read_text()
    pc.require('Starting the IntelliJ Plugin Verifier ' + policy['verifier_version'] in log, 'Missing verifier identity')
    pc.require(not re.search(r'> Task :(?:compile\w+|buildPlugin|jar|prepareSandbox)\b', log), 'Verification rebuilt the ZIP')
    checked = pc.check_reports(directory / 'reports', log, target, manifest, policy)
    pc.require(all(result[k] == v for k, v in checked.items()), 'Compatibility report/receipt mismatch')


def bundle(directory, expected_hash, evidence):
    manifest, policy = candidate(directory, expected_hash)
    for key, source in evidence.items():
        check_evidence(source, key, manifest, policy)
        destination = directory / f'compatibility-{key}.zip'
        # Only verifier reports are archived; the product ZIP is never repackaged.
        with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
            # Raw logs contain SDK/host paths. Publish only the checked completion markers.
            log = (source / 'verifier.log').read_text()
            summary = '\n'.join(re.findall(r'Starting the IntelliJ Plugin Verifier [0-9.]+|Scheduled verifications \(1\):|Finished 1 of 1 verifications', log)) + '\n'
            archive.writestr('verifier-summary.txt', summary)
            for path in [source / 'result.json', *sorted((source / 'reports').rglob('*.txt'))]:
                text = path.read_text()
                pc.require(not re.search(r'/(?:Users|home|private|tmp|Applications)/|[A-Za-z]:\\', text), 'Private paths in public report; retain locally')
                archive.writestr(path.relative_to(source).as_posix(), text)
    files = [version_name(manifest['identity']['plugin.version']), 'manifest.json', 'inputs.json',
             'compatibility-quail1.zip', 'compatibility-quail4.zip']
    write(directory / 'bundle.json', {name: pc.digest(directory / name) for name in files})
    validate_bundle(directory, expected_hash)


def validate_bundle(directory, expected_hash):
    manifest, policy = candidate(directory, expected_hash)
    expected = {version_name(manifest['identity']['plugin.version']), 'manifest.json', 'inputs.json',
                'compatibility-quail1.zip', 'compatibility-quail4.zip'}
    hashes = read(directory / 'bundle.json')
    pc.require(set(hashes) == expected, 'Incomplete or unknown candidate assets')
    pc.require(all(pc.digest(directory / name) == value for name, value in hashes.items()), 'Candidate asset changed')
    for key in ('quail1', 'quail4'):
        with tempfile.TemporaryDirectory() as temporary, zipfile.ZipFile(directory / f'compatibility-{key}.zip') as archive:
            for info in archive.infolist():
                path = Path(temporary) / info.filename
                pc.require(path.resolve().is_relative_to(Path(temporary).resolve()) and not info.is_dir(), 'Unsafe report path')
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(archive.read(info))
            check_evidence(Path(temporary), key, manifest, policy, sealed=True)
    return manifest


def rc_tag(manifest):
    return f'plugin-rc-{manifest["identity"]["plugin.version"]}-{manifest["sha256"]}'


def download_asset(asset, destination):
    with destination.open('wb') as output:
        subprocess.run(['gh', 'api', f'repos/{os.environ["GITHUB_REPOSITORY"]}/releases/assets/{asset["id"]}',
                        '-H', 'Accept: application/octet-stream'], stdout=output, check=True)


def release_for(tag):
    matches = [r for r in github.pages('releases') if r['tag_name'] == tag]
    pc.require(len(matches) <= 1, 'Ambiguous release')
    return matches[0] if matches else None


def fetch(tag, expected_hash, directory):
    pc.require(re.fullmatch(r'plugin-rc-\d+\.\d+\.\d+-[0-9a-f]{64}', tag), 'Expected candidate tag')
    release = release_for(tag)
    pc.require(release and release['body'].startswith(MARKER) and not release['draft'] and release['prerelease'], 'Missing complete RC')
    directory.mkdir(parents=True, exist_ok=False)
    assets = github.pages(f'releases/{release["id"]}/assets')
    names = [a['name'] for a in assets]
    pc.require(len(names) == 6 and len(set(names)) == 6, 'Unexpected candidate assets')
    for asset in assets:
        pc.require(Path(asset['name']).name == asset['name'] and asset['state'] == 'uploaded', 'Invalid asset')
        download_asset(asset, directory / asset['name'])
    manifest = validate_bundle(directory, expected_hash)
    pc.require(rc_tag(manifest) == tag, 'Candidate tag mismatch')
    return manifest


def preserve(tag, target, directory, names, prerelease, body):
    """Create only, or resume an identical draft; never replace/delete an asset."""
    release = release_for(tag)
    if release:
        pc.require(release['body'] == body and release['prerelease'] == prerelease and
                   release['target_commitish'] == target, 'Existing release differs; refuse replacement')
    else:
        release = github.api('releases', 'POST', {'tag_name': tag, 'target_commitish': target,
                             'name': tag, 'body': body, 'draft': True, 'prerelease': prerelease, 'make_latest': 'false'})
    assets = github.pages(f'releases/{release["id"]}/assets')
    pc.require(len({a['name'] for a in assets}) == len(assets) and {a['name'] for a in assets} <= set(names), 'Unexpected existing assets')
    for name in names:
        existing = next((a for a in assets if a['name'] == name), None)
        if existing:
            with tempfile.TemporaryDirectory() as temporary:
                copy = Path(temporary) / name
                download_asset(existing, copy)
                pc.require(pc.digest(copy) == pc.digest(directory / name), 'Existing bytes differ; no overwrite allowed')
        else:
            pc.require(release['draft'], 'Published release is incomplete; never repair by replacing it')
            uploaded = json.loads(github.gh('api', '--method', 'POST',
                f'https://uploads.github.com/repos/{os.environ["GITHUB_REPOSITORY"]}/releases/{release["id"]}/assets?name={quote(name)}',
                '-H', 'Content-Type: application/octet-stream', '--input', str(directory / name)))
            pc.require(uploaded.get('digest') == 'sha256:' + pc.digest(directory / name), 'Upload digest mismatch')
    if release['draft']:
        github.api(f'releases/{release["id"]}', 'PATCH', {'draft': False, 'make_latest': 'false'})
    published = release_for(tag)
    pc.require(published and not published['draft'] and published['body'] == body and published['prerelease'] == prerelease, 'Publication readback differs')
    assets = github.pages(f'releases/{release["id"]}/assets')
    pc.require(len(assets) == len(names) and {a['name'] for a in assets} == set(names), 'Published asset set differs')
    with tempfile.TemporaryDirectory() as temporary:
        for asset in assets:
            copy = Path(temporary) / asset['name']
            download_asset(asset, copy)
            pc.require(pc.digest(copy) == pc.digest(directory / asset['name']), 'Published download hash mismatch')
    print(f'https://github.com/{os.environ["GITHUB_REPOSITORY"]}/releases/tag/{tag}')


def store(directory, expected_hash):
    manifest = validate_bundle(directory, expected_hash)
    # RC tag is a storage identifier. A default-branch anchor works with contents:write
    # without granting Workflows write for a develop-only workflow revision.
    target = github.api('')['default_branch']
    body = MARKER + '\n' + json.dumps(manifest, sort_keys=True) + '\nRC: 未GUI受入。正式公開ではありません。'
    preserve(rc_tag(manifest), target, directory, [*read(directory / 'bundle.json'), 'bundle.json'], True, body)


def publication(directory, expected_hash, number):
    manifest = validate_bundle(directory, expected_hash)
    pr = github.api(f'pulls/{number}')
    repo = os.environ['GITHUB_REPOSITORY']
    pc.require(pr.get('merged') and pr['base']['ref'] == 'main' and pr['base']['repo']['full_name'] == repo
               and pr['head']['repo']['full_name'] == repo, 'A merged same-repository main promotion is required')
    merge = pr['merge_commit_sha']
    git_read('fetch', '--no-tags', 'origin', merge, 'main')
    parents = git_read('rev-list', '--parents', '-n', '1', merge).split()[1:]
    pc.require(len(parents) == 2 and parents[1] == pr['head']['sha'], 'Expected promotion merge commit')
    pr['base']['sha'] = parents[0]  # Fixed merge base, not a later main HEAD.
    git_read('merge-base', '--is-ancestor', merge, 'origin/main')
    result = verify_pr(pr, github.api)
    pc.require(result['mode'] == 'promotion' and result['gui_complete'] and
               result['candidate'] == manifest['identity']['source.commit'], 'Promotion candidate differs')
    promotion = json.loads(git_read('show', f'{pr["head"]["sha"]}:{PROMOTION}'))
    pc.require(promotion['artifact_sha256'] == expected_hash, 'GUI was performed on another ZIP')
    checks = json.loads(github.gh('pr', 'checks', str(number), '--repo', repo, '--json', 'name,state'))
    states = {c['name']: c['state'] for c in checks}
    pc.require(all(states.get(k) == 'SUCCESS' for k in ('test', 'PR policy', 'Agent review', 'Acceptance gate')), 'Required checks incomplete')
    return manifest, merge


def publish(directory, expected_hash, number):
    manifest, merge = publication(directory, expected_hash, number)
    # Re-fetch the preserved RC, not a caller-provided replacement of equal-looking metadata.
    with tempfile.TemporaryDirectory() as temporary:
        saved = Path(temporary) / 'saved'
        pc.require(fetch(rc_tag(manifest), expected_hash, saved) == manifest, 'Saved RC differs')
        tag = 'v' + manifest['identity']['plugin.version']
        refs = github.api('git/matching-refs/tags/' + tag)
        matching = [r for r in refs if r['ref'] == 'refs/tags/' + tag]
        pc.require(not matching or (matching[0]['object']['type'] == 'commit' and matching[0]['object']['sha'] == merge),
                   'Existing final tag does not identify the promotion merge')
        body = MARKER + '\n' + json.dumps({'candidate': manifest, 'promotion_pr': number, 'main_merge': merge}, sort_keys=True)
        preserve(tag, merge, saved, [*read(saved / 'bundle.json'), 'bundle.json'], False, body)


def main():
    os.chdir(ROOT)
    p = argparse.ArgumentParser(__doc__)
    commands = p.add_subparsers(dest='command', required=True)
    for command in ('build', 'bundle', 'store', 'fetch', 'publish'):
        s = commands.add_parser(command)
        s.add_argument('--directory', type=Path, required=True)
        if command == 'build':
            s.add_argument('--source', required=True); s.add_argument('--version', required=True)
        else:
            s.add_argument('--sha256', required=True)
        if command == 'bundle':
            s.add_argument('--quail1', type=Path, required=True); s.add_argument('--quail4', type=Path, required=True)
        if command == 'fetch': s.add_argument('--tag', required=True)
        if command == 'publish': s.add_argument('--promotion-pr', type=int, required=True)
    args = p.parse_args()
    if args.command == 'build': build(args.source, args.version, args.directory.resolve())
    elif args.command == 'bundle': bundle(args.directory, args.sha256, {'quail1': args.quail1, 'quail4': args.quail4})
    elif args.command == 'store': store(args.directory, args.sha256)
    elif args.command == 'fetch': fetch(args.tag, args.sha256, args.directory)
    else: publish(args.directory, args.sha256, args.promotion_pr)


if __name__ == '__main__':
    main()
