#!/usr/bin/env python3
"""Local GUI QA preparation and evidence gate. No AI calls or UI automation."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]


def git(*args, cwd=ROOT):
    return subprocess.check_output(['git', *args], cwd=cwd, text=True).strip()


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def case_key(value):
    if not re.fullmatch(r'(cursor|plugin):MV-\d{3}', value):
        raise argparse.ArgumentTypeError('case must be cursor:MV-001 or plugin:MV-001')
    return value


def prepare(args):
    if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}', args.run):
        raise ValueError('run must be a short identifier, not a path')
    if args.issue <= 0 or len(set(args.case)) != len(args.case):
        raise ValueError('positive Issue and unique cases required')
    # Never reuse or reset a previous run: its fixtures and evidence are immutable history.
    target = ROOT / '.loop-runs' / args.run
    target.mkdir(parents=True, exist_ok=False)
    (target / 'evidence').mkdir()
    write_json(target / 'plan.json', {'run': args.run, 'required_cases': args.case})
    for name in ('cursor', 'plugin'):
        fixture = target / name
        fixture.mkdir()
        (fixture / 'LOOP_FIXTURE.txt').write_text(f'Run: {args.run}\nSurface: {name}\nPath: {fixture}\n')
        (fixture / 'greeting.txt').write_text('Hello loop\n')
        (fixture / 'README.md').write_text('# Loop fixture\n\nOnly greeting.txt is editable for the smoke scenario.\n')
        (fixture / 'AGENTS.md').write_text(
            'This is a disposable GUI QA fixture, not the plugin source repository.\n'
            'Only perform the task explicitly requested in chat. Do not push, contact external\n'
            'services, install dependencies, or edit outside this fixture.\n')
        (fixture / '.gitignore').write_text('.idea/\n')
        git('init', '-b', 'main', cwd=fixture)
        git('add', '.', cwd=fixture)
        git('-c', 'user.name=Loop Fixture', '-c', 'user.email=loop@example.invalid',
            '-c', 'commit.gpgsign=false', 'commit', '-m', 'Initial GUI fixture', cwd=fixture)
    write_json(target / 'run.json', {
        'run': args.run, 'issue': args.issue,
        'created_at': datetime.now(timezone.utc).isoformat(),
        'source_head': git('rev-parse', 'HEAD'),
        'source_status': git('status', '--short'),
        'operator': 'GPT / Computer Use', 'state': 'prepared',
        'budget': {'max_minutes': 45, 'max_fix_cycles': 3, 'max_cursor_sends': 8},
        'build': {'source_head': '', 'source_clean': False, 'zip_sha256': '',
                  'installed_identity_evidence': ''},
        'environment': {'ide': '', 'cursor': '', 'cli_path': '', 'cli_version': '',
                        'model': '', 'permission': '', 'sandbox': '', 'worktree': ''},
        'cases': [{'surface': c.split(':')[0], 'id': c.split(':')[1], 'status': 'pending'}
                  for c in args.case], 'next_action': 'Identify build and GUI target; follow scenarios.md'
    })
    (target / 'report.md').write_text(
        '# GUI loop run\n\nIssue: #' + str(args.issue) + '\n\n'
        '## Scope and acceptance\n\nTODO: chosen MV IDs, source SHA, fixture paths, budget.\n\n'
        '## Observations\n\nTODO: time, app/window, action, expected/actual, evidence file or tool reference.\n\n'
        '## Review and next action\n\nTODO: Claude findings, GPT disposition, remaining blockers.\n')
    print(target)


def build(args):
    target = Path(args.directory).resolve()
    manifest = target / 'run.json'
    data = json.loads(manifest.read_text())
    status = git('status', '--porcelain')
    if status:
        raise ValueError('commit source changes before building a product QA run')
    head = git('rev-parse', 'HEAD')
    log = target / 'build.log'
    if log.exists():
        raise ValueError('build already attempted; prepare a new run to preserve evidence')
    with log.open('x') as output:
        subprocess.run(['./gradlew', 'test', 'buildPlugin'], cwd=ROOT,
                       stdout=output, stderr=subprocess.STDOUT, check=True)
    if git('status', '--porcelain') or git('rev-parse', 'HEAD') != head:
        raise ValueError('source changed during build; prepare a new run')
    archives = list((ROOT / 'build/distributions').glob('*.zip'))
    if len(archives) != 1:
        raise ValueError('expected exactly one plugin ZIP in build/distributions')
    archive = archives[0]
    data['build'] = {'source_head': git('rev-parse', 'HEAD'), 'source_clean': True,
                     'zip_path': str(archive), 'zip_sha256': digest(archive),
                     'build_log': 'build.log', 'built_at': datetime.now(timezone.utc).isoformat(),
                     'installed_identity_evidence': ''}
    write_json(manifest, data)
    print('Built and recorded ZIP. Installation identity still needs GUI/restart evidence.')


def validate(data, base, plan):
    errors = []
    if not isinstance(data, dict) or not isinstance(plan, dict):
        return ['invalid manifest or plan object']
    if data.get('run') != plan.get('run'):
        errors.append('run does not match declared plan')
    required = plan.get('required_cases', [])
    if not isinstance(required, list) or not required or not all(
            isinstance(c, str) and re.fullmatch(r'(cursor|plugin):MV-\d{3}', c) for c in required):
        return errors + ['invalid required case plan']

    build = data.get('build', {})
    if not isinstance(build, dict):
        return errors + ['invalid build object']
    if not re.fullmatch(r'[0-9a-f]{40}', str(build.get('source_head', ''))) or build.get('source_clean') is not True:
        errors.append('missing clean build source SHA')
    if not re.fullmatch(r'[0-9a-f]{64}', str(build.get('zip_sha256', ''))):
        errors.append('missing plugin ZIP SHA-256')

    def evidence(value):
        if not isinstance(value, str) or not value.strip():
            return False
        # Tool references must include an actual session/turn identifier, not just "screenshot".
        if value.startswith('tool:'):
            return re.fullmatch(r'tool:[A-Za-z0-9_-]{8,}/[A-Za-z0-9_-]{4,}', value) is not None
        path = (base / value).resolve()
        return path.is_relative_to(base.resolve()) and path.is_file() and path.stat().st_size > 0

    if not evidence(build.get('build_log')):
        errors.append('missing build log')
    if not evidence(build.get('installed_identity_evidence')):
        errors.append('missing installed-build identity evidence')
    env = data.get('environment', {})
    if not isinstance(env, dict):
        return errors + ['invalid environment object']
    if not all(isinstance(env.get(k), str) and env[k].strip() for k in (
            'ide', 'cursor', 'cli_path', 'cli_version', 'model', 'permission', 'sandbox', 'worktree')):
        errors.append('incomplete environment')
    cases = data.get('cases', [])
    if not isinstance(cases, list) or not cases:
        return errors + ['no GUI cases']
    actual_cases = {f"{c.get('surface')}:{c.get('id')}" for c in cases if isinstance(c, dict)}
    if actual_cases != set(required):
        errors.append('results do not match predeclared cases (missing or extra)')
    seen = set()
    for case in cases:
        if not isinstance(case, dict):
            errors.append('invalid case object')
            continue
        key = (str(case.get('id')), str(case.get('surface')))
        label = '/'.join(str(x) for x in key)
        if key in seen:
            errors.append(label + ': duplicate case')
        seen.add(key)
        if case.get('surface') not in ('cursor', 'plugin') or not re.fullmatch(r'MV-\d{3}', str(case.get('id', ''))):
            errors.append(label + ': invalid surface or MV ID')
        if case.get('status') != 'pass':
            errors.append(label + ': not pass')
        if case.get('method') not in ('computer-use', 'human-gui'):
            errors.append(label + ': CLI is not GUI evidence')
        if not all(isinstance(case.get(k), str) and case[k].strip() for k in ('expected', 'actual', 'observer', 'observed_at')):
            errors.append(label + ': incomplete observation')
        if case.get('surface') == 'plugin':
            if case.get('build_source_head') != build.get('source_head'):
                errors.append(label + ': observation belongs to another build')
            try:
                observed = datetime.fromisoformat(case.get('observed_at', ''))
                built = datetime.fromisoformat(build.get('built_at', ''))
                if observed.tzinfo is None or built.tzinfo is None or observed < built:
                    raise ValueError('stale or timezone-free observation')
            except (ValueError, TypeError):
                errors.append(label + ': observation must be dated after build with timezone')
        if not evidence(case.get('evidence')):
            errors.append(label + ': missing evidence')
    if not any(c.get('surface') == 'plugin' for c in cases if isinstance(c, dict)):
        errors.append('no plugin GUI case; Cursor alone cannot verify plugin')
    return errors


def check(args):
    target = Path(args.directory).resolve()
    errors = validate(json.loads((target / 'run.json').read_text()), target,
                      json.loads((target / 'plan.json').read_text()))
    for error in errors:
        print('- ' + error)
    if errors:
        print('INCOMPLETE: do not mark the scoped GUI verification complete.')
        return 1
    print('FORMAT ONLY: content and actual GUI/build correspondence have NOT been verified. GPT must review evidence; no auto-pass or Issue mutation.')
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest='command', required=True)
    p = subs.add_parser('prepare', help='create a new run and two independent Git fixtures')
    p.add_argument('run')
    p.add_argument('--issue', type=int, required=True)
    p.add_argument('--case', type=case_key, action='append', required=True)
    p.set_defaults(func=prepare)
    p = subs.add_parser('build', help='test/build clean source and record ZIP identity before GUI installation')
    p.add_argument('directory')
    p.set_defaults(func=build)
    p = subs.add_parser('check', help='reject missing/non-GUI evidence; never changes status')
    p.add_argument('directory')
    p.set_defaults(func=check)
    args = parser.parse_args()
    try:
        return args.func(args) or 0
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print(str(exc), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
