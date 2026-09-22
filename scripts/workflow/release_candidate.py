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
from verification import verify_pr, git_read, scoped_candidate, PROMOTION

ROOT = Path(__file__).resolve().parents[2]
INPUTS = ('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
          'gradle/wrapper/gradle-wrapper.properties', 'scripts/workflow/plugin_compatibility.json')
MARKER = '<!-- release-candidate:v1 -->'
NOTES_START = '<!-- release-notes:start -->'
NOTES_END = '<!-- release-notes:end -->'


def validate_notes(notes, version):
    repo = os.environ['GITHUB_REPOSITORY']
    sections = re.findall(r'^## ([^\n]+)\n(.*?)(?=^## |\Z)', notes, re.M | re.S)
    pc.require([title for title, _ in sections] == ['主な変更', '対応環境', 'インストール', '制約・詳細']
               and all(text.strip() for _, text in sections), 'Release notes need four non-empty Japanese sections')
    pc.require(not re.search(r'TODO|TBD|未記入|<!--', notes, re.I), 'Unfinished or hidden release notes')
    pc.require(f'https://github.com/{repo}/releases/download/v{version}/{version_name(version)}' in notes
               and f'https://github.com/{repo}/blob/main/docs/releases/{version}.md' in notes,
               'Release notes need this version download and report links')


def release_notes(version):
    version_name(version)
    document = git_read('show', f'HEAD:docs/releases/{version}.md')
    pc.require(document.count(NOTES_START) == document.count(NOTES_END) == 1,
               'Reviewed version document needs one release-notes block')
    before, after = document.split(NOTES_START)
    pc.require(NOTES_END not in before and NOTES_END in after, 'Invalid release-notes block order')
    notes = after.split(NOTES_END)[0].strip()
    validate_notes(notes, version)
    return notes


def formal_record(body):
    """Read the old JSON-only body or the hidden record after public notes."""
    pc.require(body.count('release-candidate:v1') == 1, 'Missing or ambiguous release record')
    if body.startswith(MARKER + '\n'):
        return json.loads(body[len(MARKER):]), ''
    match = re.fullmatch(r'(.*?)\n<!-- release-candidate:v1\n([^\n]+)\n-->\s*', body, re.S)
    pc.require(match, 'Invalid formal release record')
    return json.loads(match[2]), match[1].strip()


def formal_body(notes, record):
    validate_notes(notes, record['candidate']['identity']['plugin.version'])
    return notes + '\n\n<!-- release-candidate:v1\n' + json.dumps(record, sort_keys=True) + '\n-->\n'


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


def java_version(output):
    match = re.search(r'^(?:openjdk|java) version "([0-9][0-9.+-]*)"', output, re.M)
    pc.require(match, 'Missing numeric Java version')
    return match[1]


def public_jbr(output):
    runtime = re.search(r'^OpenJDK Runtime Environment \(build ([0-9][0-9.b+-]*)\)', output, re.M)
    vm = re.search(r'^OpenJDK 64-Bit Server VM \(build ([0-9][0-9.b+-]*),', output, re.M)
    pc.require(runtime and vm, 'Missing bundled JBR build identity')
    return f'openjdk version "{java_version(output)}"\nOpenJDK Runtime Environment (build {runtime[1]})\nOpenJDK 64-Bit Server VM (build {vm[1]}, mixed mode)'


def build_command(version):
    return ['./gradlew', 'clean', 'test', 'buildPlugin', 'verifyPluginStructure', '--console=plain',
            '-PpluginVersion=' + version, '-PuseLocalPlatform=false']


def build(source, version, directory, scope_issue=None):
    name = version_name(version)
    pc.require(re.fullmatch('[0-9a-f]{40}', source), 'Full source SHA required')
    pc.require(git_read('rev-parse', 'HEAD') == source and not git_read('status', '--porcelain'), 'Clean fixed source required')
    if scope_issue is None:
        git_read('fetch', '--no-tags', 'origin', 'develop')
        git_read('merge-base', '--is-ancestor', source, 'origin/develop')
    else:
        git_read('fetch', '--no-tags', 'origin', 'main')
        scoped_candidate(git_read('rev-parse', 'origin/main'), source, scope_issue)
    pc.require(not directory.is_relative_to(ROOT), 'Candidate output must be outside the source checkout')
    java = Path(os.environ['JAVA_HOME']) / 'bin/java'
    runtime = subprocess.check_output([str(java), '-version'], stderr=subprocess.STDOUT, text=True).strip()
    pc.require(java_version(runtime).startswith('21.'), 'Gradle runtime JDK 21 required')
    directory.mkdir(parents=True, exist_ok=False)
    args = build_command(version)
    inputs = {'source': source, 'version': version, 'command': args, 'files': source_inputs(source), 'java_version': java_version(runtime)}
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
    pc.require(set(inputs) == {'source', 'version', 'command', 'files', 'java_version', 'libraries'}
               and re.fullmatch(r'21\.[0-9.+-]+', inputs['java_version'])
               and inputs['command'] == build_command(version), 'Invalid public build inputs')
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
    if sealed:
        pc.require(result['target']['java_runtime'] == public_jbr(result['target']['java_runtime']), 'Raw/private JVM output in public receipt')
    log = (directory / ('verifier-summary.txt' if sealed else 'verifier.log')).read_text()
    pc.require('Starting the IntelliJ Plugin Verifier ' + policy['verifier_version'] in log, 'Missing verifier identity')
    pc.require(not re.search(r'> Task :(?:compile\w+|buildPlugin|jar|prepareSandbox)\b', log), 'Verification rebuilt the ZIP')
    checked = pc.check_reports(directory / 'reports', log, target, manifest, policy)
    pc.require(all(result[k] == v for k, v in checked.items()), 'Compatibility report/receipt mismatch')


def bundle(directory, expected_hash, evidence):
    manifest, policy = candidate(directory, expected_hash)
    pc.require(set(evidence) == {'quail1', 'quail4'}, 'Both IDE reports required')
    for key, source in evidence.items():
        check_evidence(source, key, manifest, policy)
    with tempfile.TemporaryDirectory(dir=directory) as temporary:
        staging = Path(temporary)
        for key, source in evidence.items():
            # Deterministic report archives make an interrupted transfer retryable.
            with zipfile.ZipFile(staging / f'compatibility-{key}.zip', 'x') as archive:
                log = (source / 'verifier.log').read_text()
                summary = '\n'.join(re.findall(r'Starting the IntelliJ Plugin Verifier [0-9.]+|Scheduled verifications \(1\):|Finished 1 of 1 verifications', log)) + '\n'
                archive.writestr(zipfile.ZipInfo('verifier-summary.txt'), summary, compress_type=zipfile.ZIP_DEFLATED)
                for path in [source / 'result.json', *sorted((source / 'reports').rglob('*.txt'))]:
                    text = path.read_text()
                    if path.name == 'result.json':
                        receipt = json.loads(text)
                        receipt['target']['java_runtime'] = public_jbr(receipt['target']['java_runtime'])
                        text = json.dumps(receipt, ensure_ascii=False, indent=2) + '\n'
                    pc.require(not re.search(r'/(?:Users|home|private|tmp|Applications)/|[A-Za-z]:\\', text), 'Private paths in public report; retain locally')
                    archive.writestr(zipfile.ZipInfo(path.relative_to(source).as_posix()), text, compress_type=zipfile.ZIP_DEFLATED)
        files = [version_name(manifest['identity']['plugin.version']), 'manifest.json', 'inputs.json']
        hashes = {name: pc.digest(directory / name) for name in files}
        hashes.update({path.name: pc.digest(path) for path in staging.iterdir()})
        write(staging / 'bundle.json', hashes)
        for path in staging.iterdir():
            destination = directory / path.name
            if destination.exists():
                pc.require(pc.digest(destination) == pc.digest(path), 'Existing bundle differs; no overwrite allowed')
            else:
                os.link(path, destination)  # Atomic create, never truncate an existing file.
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


def preserve(tag, target, directory, names, prerelease, body, update_notes=False):
    """Create only, or resume an identical draft; never replace/delete an asset."""
    release = release_for(tag)
    pc.require(not update_notes or (release and not release['draft'] and not prerelease),
               'Notes update requires an existing published formal release')
    if not prerelease:
        record, notes = formal_record(body)
        version = record['candidate']['identity']['plugin.version']
        validate_notes(notes, version)
        if release:
            old_record, old_notes = formal_record(release['body'])
            pc.require(old_record == record, 'Immutable release identity differs')
            if not update_notes:
                validate_notes(old_notes, version)
                body = release['body']  # Preserve editorial changes on an ordinary retry.
    if release:
        pc.require((update_notes or release['body'] == body) and release['prerelease'] == prerelease and
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
    if update_notes and release['body'] != body:
        # Only after every existing asset passed byte comparison; never patch identity/assets.
        current = release_for(tag)
        pc.require(current and all(current[k] == release[k] for k in
                   ('id', 'body', 'target_commitish', 'draft', 'prerelease')), 'Release changed during notes verification')
        github.api(f'releases/{release["id"]}', 'PATCH', {'body': body})
    if release['draft']:
        github.api(f'releases/{release["id"]}', 'PATCH', {'draft': False, 'make_latest': 'false'})
    published = release_for(tag)
    pc.require(published and not published['draft'] and published['body'] == body and published['prerelease'] == prerelease
               and published['target_commitish'] == target, 'Publication readback differs')
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


def publish(directory, expected_hash, number, update_notes=False):
    manifest, merge = publication(directory, expected_hash, number)
    notes = release_notes(manifest['identity']['plugin.version'])
    # Re-fetch the preserved RC, not a caller-provided replacement of equal-looking metadata.
    with tempfile.TemporaryDirectory() as temporary:
        saved = Path(temporary) / 'saved'
        pc.require(fetch(rc_tag(manifest), expected_hash, saved) == manifest, 'Saved RC differs')
        tag = 'v' + manifest['identity']['plugin.version']
        refs = github.api('git/matching-refs/tags/' + tag)
        matching = [r for r in refs if r['ref'] == 'refs/tags/' + tag]
        pc.require((matching or not update_notes) and
                   (not matching or (matching[0]['object']['type'] == 'commit' and matching[0]['object']['sha'] == merge)),
                   'Existing final tag does not identify the promotion merge')
        body = formal_body(notes, {'candidate': manifest, 'promotion_pr': number, 'main_merge': merge})
        preserve(tag, merge, saved, [*read(saved / 'bundle.json'), 'bundle.json'], False, body, update_notes)
        ref = github.api('git/ref/tags/' + tag)
        pc.require(ref['object']['type'] == 'commit' and ref['object']['sha'] == merge, 'Published tag differs')


def main():
    os.chdir(ROOT)
    p = argparse.ArgumentParser(__doc__)
    commands = p.add_subparsers(dest='command', required=True)
    for command in ('build', 'bundle', 'store', 'fetch', 'publish'):
        s = commands.add_parser(command)
        s.add_argument('--directory', type=Path, required=True)
        if command == 'build':
            s.add_argument('--source', required=True); s.add_argument('--version', required=True)
            s.add_argument('--scope-issue', type=int, help='Use a preapproved main scope instead of develop')
        else:
            s.add_argument('--sha256', required=True)
        if command == 'bundle':
            s.add_argument('--quail1', type=Path, required=True); s.add_argument('--quail4', type=Path, required=True)
        if command == 'fetch': s.add_argument('--tag', required=True)
        if command == 'publish':
            s.add_argument('--promotion-pr', type=int, required=True)
            s.add_argument('--update-notes', action='store_true', help='Update only reviewed notes after verifying the published identity and all assets')
    args = p.parse_args()
    if args.command == 'build': build(args.source, args.version, args.directory.resolve(), args.scope_issue)
    elif args.command == 'bundle': bundle(args.directory, args.sha256, {'quail1': args.quail1, 'quail4': args.quail4})
    elif args.command == 'store': store(args.directory, args.sha256)
    elif args.command == 'fetch': fetch(args.tag, args.sha256, args.directory)
    else: publish(args.directory, args.sha256, args.promotion_pr, args.update_notes)


if __name__ == '__main__':
    main()
