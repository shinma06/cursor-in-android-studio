"""Acceptance data and fixed-candidate gates. Never execute code from a PR."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
from change_impact import path_impacts, TOOLING as IMPACT_TOOLING, KNOWLEDGE, METADATA

SHA = re.compile(r'[0-9a-f]{40}')
HASH = re.compile(r'[0-9a-f]{64}')
PROMOTION = 'docs/verification/promotion.json'
ENVIRONMENT = 'docs/verification/environments/rabbit1.json'
COMPATIBILITY = 'scripts/workflow/plugin_compatibility.json'
STATUSES = {'pending', 'blocked', 'fail', 'pass'}
TOOLING = ('docs/', 'scripts/', '.github/', '.githooks/', '.agents/', '.claude/skills/', '.cursor/rules/')


def field(body, name):
    values = re.findall(r'^' + re.escape(name) + r': (.+)$', body or '', re.M)
    if len(values) != 1:
        raise ValueError('Exactly one ' + name + ' is required')
    return values[0].strip()


def metadata(pr):
    issue = int(field(pr['body'], 'Issue').removeprefix('#'))
    path = field(pr['body'], 'Verification')
    expected = f'docs/verification/changes/issue-{issue}.json'
    if path != expected:
        raise ValueError('Verification must name the Issue acceptance JSON: ' + expected)
    mode = field(pr['body'], 'Integration')
    if mode not in ('develop', 'promotion', 'tooling'):
        raise ValueError('Integration must be develop, promotion or tooling')
    if pr['base']['ref'] != ('develop' if mode == 'develop' else 'main'):
        raise ValueError('Integration does not match PR target')
    return issue, path, mode


def nonempty(value):
    return isinstance(value, str) and bool(value.strip())


def validate_change(data, issue, gui):
    if data.get('schema') != 1 or data.get('issue') != issue or type(data.get('gui_required')) is not bool or data['gui_required'] != gui:
        raise ValueError('Acceptance Issue/GUI declaration mismatch')
    if not nonempty(data.get('reason')) or not data.get('cli_checks') or not all(nonempty(x) for x in data['cli_checks']):
        raise ValueError('Concrete reason and CLI checks are required')
    cases = data.get('cases')
    if not isinstance(cases, list) or (gui and not cases) or (not gui and cases):
        raise ValueError('Required GUI Cases missing or not-required contradicts Cases')
    ids = set()
    for case in cases:
        if not isinstance(case, dict) or not re.fullmatch(r'[A-Z][A-Z0-9-]+', case.get('id', '')) or case['id'] in ids:
            raise ValueError('Invalid or duplicate Case ID')
        ids.add(case['id'])
        if case.get('required_execution') not in (None, 'computer_use'):
            raise ValueError('Unknown required execution method')
        if not re.fullmatch(r'[a-z][a-z0-9_]*', case.get('artifact', 'plugin')):
            raise ValueError('Invalid Case artifact name')
        for key in ('change', 'preconditions', 'expected', 'next_action'):
            if not nonempty(case.get(key)):
                raise ValueError('Missing Case ' + key)
        if not case.get('steps') or not all(nonempty(x) for x in case['steps']):
            raise ValueError('Reproducible steps are required')
        if not case.get('provenance') or not all(nonempty(x) for x in case['provenance']):
            raise ValueError('Case provenance is required')
        for actor in ('gpt', 'human'):
            observation = case.get(actor)
            if not isinstance(observation, dict) or observation.get('status') not in STATUSES or not nonempty(observation.get('reason')):
                raise ValueError('Explicit agent (gpt key) and human status/reason required')
            if observation['status'] == 'pass':
                validate_observation(observation, observation.get('head'))
            if observation['status'] == 'fail' and (type(case.get('fix_issue')) is not int or case['fix_issue'] <= 0 or case['fix_issue'] == issue):
                raise ValueError('Product fail requires a dedicated fix Issue')
        if 'fix_issue' not in case or 'fix_pr' not in case or not nonempty(case.get('recheck')):
            raise ValueError('Fix/recheck tracking fields are required')
    return data


def validate_observation(result, candidate, artifact=None):
    if (result.get('status') != 'pass' or not SHA.fullmatch(candidate or '') or result.get('head') != candidate or
            not HASH.fullmatch(result.get('artifact_sha256', '')) or
            (artifact is not None and result['artifact_sha256'] != artifact)):
        raise ValueError('Case must pass on the exact candidate and build')
    if result.get('actor') not in ('gpt', 'human'):
        raise ValueError('Observer actor must use the legacy gpt or human value')
    for key in ('observer', 'at', 'evidence', 'loaded_identity', 'reason'):
        if not nonempty(result.get(key)):
            raise ValueError('Missing observed evidence: ' + key)
    if not re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:Z|[+-]\d\d:\d\d)', result['at']):
        raise ValueError('Use an ISO8601 observation timestamp')


def git_read(*args, cwd=None):
    return subprocess.check_output(['git', *args], cwd=cwd, text=True).strip()


def tooling_path(path):
    return path.startswith(TOOLING) or path in ('CLAUDE.md', 'AGENTS.md', 'README.md')


def promotion_history(candidate, head, base, allowed, git):
    """Keep candidate history fixed; import only reproducible, non-build main updates."""
    previous = candidate
    synced_paths = set()
    chain = git('rev-list', '--first-parent', '--reverse', f'{candidate}..{head}').splitlines()
    for commit in chain:
        parents = git('rev-list', '--parents', '-n', '1', commit).split()[1:]
        if not parents or len(parents) > 2 or parents[0] != previous:
            raise ValueError('Unexpected promotion ancestry; first parents must return to candidate')
        if len(parents) == 2:
            if git('rev-list', '--max-count=1', parents[1], '--not', base):
                raise ValueError('Unexpected promotion ancestry; merge parent must be an ancestor of main')
        changed = set(git('diff', '--ignore-submodules=none', '--no-renames', '--name-only', previous, commit).splitlines())
        for path in changed & allowed:
            entry = git('ls-tree', commit, '--', path).split()
            if entry and (len(entry) != 4 or entry[:2] != ['100644', 'blob']):
                raise ValueError('Promotion metadata must remain regular JSON')
        if len(parents) == 2:
            # Reverted main changes may leave no merge diff, but remain in ancestry.
            roots = git('merge-base', '--all', *parents).splitlines()
            if len(roots) != 1:
                raise ValueError('Main sync requires one unambiguous merge base')
            for incoming in git('rev-list', f'{roots[0]}..{parents[1]}').splitlines():
                inputs = git('rev-list', '--parents', '-n', '1', incoming).split()[1:]
                if not inputs or len(inputs) > 2:
                    raise ValueError('Unexpected ancestry in main sync history')
                if len(inputs) == 2:
                    # rev-list checks both parent histories. Check the merge's own
                    # changes against a clean merge, not already-tested shared ancestry.
                    merged = git('merge-tree', '--write-tree', '--no-messages', *inputs)
                    if not SHA.fullmatch(merged):
                        raise ValueError('Main history merge did not produce a clean merge tree')
                    inputs = [merged]
                for parent in inputs:
                    raw = git('diff', '--ignore-submodules=none', '--raw', '--no-abbrev', '--no-renames', '-z', parent, incoming).split('\0')
                    if raw[-1] != '':
                        raise ValueError('Malformed main sync diff')
                    for index in range(0, len(raw) - 1, 2):
                        header, path = raw[index].split(), raw[index + 1]
                        modes = (header[0].removeprefix(':'), header[1])
                        if (not set(modes) <= {'000000', '100644', '100755'} or
                                not path_impacts(path, modes) <= {IMPACT_TOOLING, KNOWLEDGE, METADATA}):
                            raise ValueError('Product, build or unknown change in main sync history')
                        # Content imports cannot revise existing Cases/policies/results.
                        # Historical metadata-only merges retain their prior policy.
                        # A newly merged GUI-free tooling PR may bring its own acceptance.
                        if changed - allowed and path.startswith('docs/verification/') and not path.endswith('.md'):
                            match = re.fullmatch(r'docs/verification/changes/issue-([1-9][0-9]*)\.json', path)
                            if not match or modes != ('000000', '100644') or git('ls-tree', candidate, '--', path):
                                raise ValueError('Main sync cannot revise acceptance or environment policy')
                            validate_change(regular_json(incoming, path, git), int(match[1]), False)
        if changed - allowed:
            if len(parents) != 2:
                raise ValueError('Untested commit in promotion history, even if later reverted')
            # An old metadata-only merge can have resolved historical main conflicts.
            # New content imports must instead reproduce Git's clean merge exactly.
            tree = git('merge-tree', '--write-tree', '--no-messages', *parents)
            if not SHA.fullmatch(tree):
                raise ValueError('Main sync did not produce a clean merge tree')
            if set(git('diff', '--ignore-submodules=none', '--no-renames', '--name-only', tree, commit).splitlines()) - allowed:
                raise ValueError('Main sync differs from the clean merge result')
            synced_paths.update(changed - allowed)
        previous = commit
    if previous != head or set(chain) != set(git('rev-list', head, '--not', candidate, base).splitlines()):
        raise ValueError('Unexplained commit in promotion history')
    if set(git('diff', '--ignore-submodules=none', '--no-renames', '--name-only', candidate, head).splitlines()) - allowed - synced_paths:
        raise ValueError('Promotion tree differs from tested candidate outside acceptance metadata or main sync')


def source_commits(source, base, git):
    """Bind squash results or a tooling-only main sync to their actual merged DAG."""
    merge = source['merge_commit_sha']
    parents = git('rev-list', '--parents', '-n', '1', merge).split()[1:]
    if len(parents) == 1:
        return {merge}
    if (len(parents) != 2 or parents != [source['base']['sha'], source['head']['sha']] or
            field(source['body'], 'GUI') != 'not-required'):
        raise ValueError('Sync merge must match the merged develop PR base/head and be tooling-only')
    develop, head = parents
    chain = git('rev-list', '--first-parent', f'{develop}..{head}').splitlines()
    synced_main = False
    for index, commit in enumerate(chain):
        inputs = git('rev-list', '--parents', '-n', '1', commit).split()[1:]
        previous = chain[index + 1] if index + 1 < len(chain) else develop
        if not inputs or len(inputs) > 2 or inputs[0] != previous:
            raise ValueError('Sync history must return to the merged develop parent along first parents')
        if len(inputs) == 2:
            git('merge-base', '--is-ancestor', inputs[1], base)
            synced_main = True
        # Check each edge, so a product edit followed by a revert cannot disappear.
        if any(not tooling_path(p) for p in git('diff', '--no-renames', '--name-only', inputs[0], commit).splitlines()):
            raise ValueError('Product change in sync history, even if later reverted')
    if not synced_main:
        raise ValueError('Sync PR must actually merge an ancestor of the fixed main base')
    for parent in parents:
        if any(not tooling_path(p) for p in git('diff', '--no-renames', '--name-only', parent, merge).splitlines()):
            raise ValueError('Product change in sync merge result')
    covered = {merge, *chain}
    actual = {merge, *git('rev-list', head, '--not', develop, base).splitlines()}
    if covered != actual:
        raise ValueError('Sync PR contains unexplained or already-main first-parent history')
    return covered


def regular_json(ref, path, git):
    entry = git('ls-tree', ref, '--', path).split()
    if len(entry) != 4 or entry[0] != '100644' or entry[1] != 'blob' or entry[3] != path:
        raise ValueError('Scope plan must be a regular JSON file in trusted main')
    def unique_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate key in trusted scope plan')
            result[key] = value
        return result
    return json.loads(git('show', f'{ref}:{path}'), object_pairs_hook=unique_pairs)


def json_hash(data):
    return hashlib.sha256(json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def environment_cases(base, candidate, git=git_read):
    """Only fixed trusted-main data may revise a historical Case's environment."""
    if not git('ls-tree', base, '--', ENVIRONMENT):
        return {}
    plan = regular_json(base, ENVIRONMENT, git)
    if (plan.get('schema') != 1 or plan.get('issue') != 479 or
            not SHA.fullmatch(plan.get('migration_commit', '')) or not nonempty(plan.get('decision'))):
        raise ValueError('Invalid environment revision provenance')
    # Valid non-ancestors produce output, not an adapter-specific command error.
    if git('rev-list', '--max-count=1', plan['migration_commit'], '--not', candidate):
        return {}  # Pre-migration candidates retain their original requirements.
    environment = plan.get('environment', {})
    if (set(environment) != {'build', 'java_version', 'jvm_target', 'class_major'} or
            not all(nonempty(environment.get(k)) for k in ('build', 'java_version', 'jvm_target')) or
            type(environment.get('class_major')) is not int):
        raise ValueError('Invalid environment identity')
    sdk = regular_json(candidate, 'scripts/workflow/plugin_compatibility.json', git)
    compile = sdk['compile']
    target = sdk['targets'][compile['target']]
    if environment != {**{k: target[k] for k in ('build', 'java_version')},
                       **{k: compile[k] for k in ('jvm_target', 'class_major')}}:
        raise ValueError('Candidate SDK differs from the reviewed environment revision')
    entries = plan.get('cases')
    if not isinstance(entries, dict) or not entries:
        raise ValueError('Environment revision requires explicit Case identities')
    revised = {}
    for key, entry in entries.items():
        if not re.fullmatch(r'[1-9][0-9]*:[A-Z][A-Z0-9-]+', key) or not isinstance(entry, dict):
            raise ValueError('Invalid environment Case key')
        issue, case_id = key.split(':')
        if (set(entry) - {'source_pr', 'source_merge', 'source_path', 'original_case_sha256', 'preconditions', 'expected'} or
                type(entry.get('source_pr')) is not int or entry['source_pr'] <= 0 or
                not SHA.fullmatch(entry.get('source_merge', '')) or
                entry.get('source_path') != f'docs/verification/changes/issue-{issue}.json' or
                not HASH.fullmatch(entry.get('original_case_sha256', '')) or
                not nonempty(entry.get('preconditions')) or ('expected' in entry and not nonempty(entry['expected']))):
            raise ValueError('Environment revision may change only preconditions and expected environment wording')
        original = regular_json(entry['source_merge'], entry['source_path'], git)
        validate_change(original, int(issue), True)
        matches = [case for case in original['cases'] if case['id'] == case_id]
        if len(matches) != 1 or json_hash(matches[0]) != entry['original_case_sha256']:
            raise ValueError('Environment revision original Case hash mismatch: ' + key)
        case = dict(matches[0], preconditions=entry['preconditions'])
        if 'expected' in entry:
            case['expected'] = entry['expected']
        revised[key] = dict(entry, case=case, original=matches[0], revision=json_hash(plan), environment=environment)
    return revised


def validate_environment(result, revision):
    if revision and (result.get('environment_revision') != revision['revision'] or
                     result.get('environment') != revision['environment']):
        raise ValueError('Case requires a new observation bound to the exact environment revision and runtime')


def scoped_history(base, head, allowed, git, compatibility=None):
    """Inspect every edge, including changes later reverted; never follow symlinks."""
    previous = base
    commits = git('rev-list', '--reverse', f'{base}..{head}').splitlines()
    for commit in commits:
        if git('rev-list', '--parents', '-n', '1', commit).split() != [commit, previous]:
            raise ValueError('Scoped candidate and result history must be linear from the fixed base')
        raw = git('diff', '--raw', '--no-abbrev', '--no-renames', '--ignore-submodules=none', '-z', previous, commit)
        fields = raw.split('\0')
        if fields[-1] != '':
            raise ValueError('Malformed scoped diff')
        for index in range(0, len(fields) - 1, 2):
            header, path = fields[index].split(), fields[index + 1]
            if (len(header) != 5 or path not in allowed or header[0] not in (':000000', ':100644', ':100755')
                    or header[1] != allowed[path] or
                    (header[4] not in ('A', 'M') if allowed[path] != '000000' else
                     header[4] != 'D' or header[0] == ':000000' or header[3] != '0' * 40)):
                raise ValueError('Path or file mode outside trusted scope: ' + path)
            if path == COMPATIBILITY and json_hash(regular_json(commit, path, git)) != json_hash(compatibility):
                raise ValueError('Candidate compatibility policy must equal the trusted scope policy')
        previous = commit
    if previous != head:
        raise ValueError('Scoped candidate is not descended from the fixed base')
    return commits


def scoped_candidate(base, candidate, issue, git=git_read):
    if not SHA.fullmatch(base or '') or not SHA.fullmatch(candidate or '') or type(issue) is not int or issue <= 0:
        raise ValueError('Invalid scoped candidate identity')
    path = f'docs/verification/scopes/issue-{issue}.json'
    plan = regular_json(base, path, git)
    if plan.get('schema') != 1 or plan.get('issue') != issue:
        raise ValueError('Trusted scope Issue mismatch')
    allowed = plan.get('files')
    if (not isinstance(allowed, dict) or not allowed or
            any(not isinstance(p, str) or p.startswith(('/', 'docs/verification/')) or
                (p.startswith('scripts/workflow/') and p != COMPATIBILITY) or
                any(part in ('', '.', '..') for part in p.split('/')) or '\\' in p or m not in ('000000', '100644', '100755') for p, m in allowed.items())):
        raise ValueError('Scope cannot modify its plan, acceptance or release gates')
    protected = {'.github/workflows/acceptance.yml', '.github/workflows/pr-policy.yml', '.github/workflows/agent-review.yml'}
    if set(allowed) & protected:
        raise ValueError('Scope cannot modify trusted check workflows')
    change = validate_change(plan['acceptance'], issue, True)
    compatibility = plan.get('compatibility_policy')
    if COMPATIBILITY in allowed and (allowed[COMPATIBILITY] != '100644' or not isinstance(compatibility, dict) or not compatibility):
        raise ValueError('Scope requires a frozen compatibility policy')
    if not scoped_history(base, candidate, allowed, git, compatibility):
        raise ValueError('Scoped candidate has no changes')
    for deleted in (p for p, mode in allowed.items() if mode == '000000'):
        original = git('ls-tree', base, '--', deleted).split()
        if (len(original) != 4 or original[0] not in ('100644', '100755') or original[1] != 'blob' or
                original[3] != deleted or git('ls-tree', candidate, '--', deleted)):
            raise ValueError('Declared deletion must remove a regular file from the fixed base: ' + deleted)
    if COMPATIBILITY in allowed and json_hash(regular_json(candidate, COMPATIBILITY, git)) != json_hash(compatibility):
        raise ValueError('Candidate compatibility policy must equal the trusted scope policy')
    scoped_environment_cases(base, candidate, issue, git)
    return change


def scoped_environment_cases(base, candidate, issue, git=git_read):
    """Project trusted historical Rabbit revisions without fetching or executing old sources."""
    plan = regular_json(base, f'docs/verification/scopes/issue-{issue}.json', git)
    bindings = plan.get('environment_cases', {})
    if not isinstance(bindings, dict):
        raise ValueError('Scoped environment bindings must be an object')
    if not bindings:
        return {}
    environment = regular_json(base, ENVIRONMENT, git)
    if environment.get('schema') != 1 or environment.get('issue') != 479 or json_hash(environment) != plan.get('environment_revision'):
        raise ValueError('Scoped environment revision must match trusted main')
    sdk = regular_json(candidate, COMPATIBILITY, git)
    compile = sdk['compile']
    target = sdk['targets'][compile['target']]
    if environment['environment'] != {**{k: target[k] for k in ('build', 'java_version')},
                                     **{k: compile[k] for k in ('jvm_target', 'class_major')}}:
        raise ValueError('Candidate SDK differs from the reviewed environment revision')
    cases = {case['id']: case for case in plan['acceptance']['cases']}
    revised = {}
    for case_id, original_key in bindings.items():
        entry = environment['cases'].get(original_key)
        origin = plan.get('case_origins', {}).get(case_id, {})
        if case_id not in cases or not entry or origin.get('old_key') != original_key:
            raise ValueError('Scoped environment binding requires its original Case identity')
        sources = [source for source in origin.get('original_merged_requirements', [])
                   if source.get('source_pr') == entry['source_pr'] and source.get('source_merge') == entry['source_merge'] and
                   source.get('verification') == entry['source_path'] and
                   source.get('original_case_sha256') == entry['original_case_sha256'] and
                   json_hash(source.get('case')) == entry['original_case_sha256']]
        if len(sources) != 1:
            raise ValueError('Scoped environment original Case hash/provenance mismatch: ' + original_key)
        revised[f'{issue}:{case_id}'] = dict(entry, case=cases[case_id], original=sources[0]['case'],
                                           revision=json_hash(environment), environment=environment['environment'])
    return revised


def verify_pr(pr, api, git=git_read):
    """api(path) uses the same repo; git sees fetched PR/candidate objects only as data."""
    issue, path, mode = metadata(pr)
    head, base = pr['head']['sha'], pr['base']['sha']
    data = json.loads(git('show', f'{head}:{path}'))
    gui = field(pr['body'], 'GUI') == 'required'
    validate_change(data, issue, gui)
    files = git('diff', '--name-only', base, head).splitlines()
    if mode == 'develop':
        # Outcome is deliberately unrestricted, but every required Case has steps and tracking.
        for case in data['cases']:
            if any(case[actor]['status'] == 'fail' for actor in ('gpt', 'human')):
                fix = api(f'issues/{case["fix_issue"]}')
                if fix.get('state') != 'open' or 'pull_request' in fix:
                    raise ValueError('Product failure needs an open dedicated fix Issue')
        return {'mode': mode, 'gui_complete': not gui, 'cases': len(data['cases'])}
    if mode == 'tooling':
        if gui or not files or any(not tooling_path(f) for f in files):
            raise ValueError('Direct main tooling route is restricted to documented non-product paths')
        return {'mode': mode, 'gui_complete': True, 'cases': 0}
    promotion = json.loads(git('show', f'{head}:{PROMOTION}'))
    candidate = promotion.get('candidate')
    if promotion.get('schema') != 1 or promotion.get('base') != base or not SHA.fullmatch(candidate or ''):
        raise ValueError('Promotion must bind the current main base and fixed candidate')
    scope = promotion.get('scope', 'develop')
    if scope not in ('develop', 'main'):
        raise ValueError('Unknown promotion scope')
    revisions = {} if scope == 'main' else environment_cases(base, candidate, git)
    if scope == 'main':
        if not gui or 'changes' in promotion:
            raise ValueError('Scoped promotion requires GUI and uses trusted plan, not develop changes')
        change = scoped_candidate(base, candidate, issue, git)
        revisions = scoped_environment_cases(base, candidate, issue, git)
        if data != change:
            raise ValueError('Scoped acceptance must equal the trusted plan; observations belong in results')
        scoped_history(candidate, head, {PROMOTION: '100644', path: '100644'}, git)
        required = {f'{issue}:{case["id"]}': (case.get('required_execution'), case.get('artifact', 'plugin'))
                    for case in change['cases']}
    else:
        # Later develop work belongs to the next batch. The fixed candidate must still be its ancestor.
        ref = api('git/ref/heads/develop')
        develop = ref.get('object', {}).get('sha')
        if not SHA.fullmatch(develop or ''):
            raise ValueError('Invalid develop reference')
        git('fetch', '--no-tags', 'origin', develop)
        git('merge-base', '--is-ancestor', candidate, develop)
        git('merge-base', '--is-ancestor', candidate, head)
        git('merge-base', '--is-ancestor', base, head)
        promotion_history(candidate, head, base, {PROMOTION, path}, git)
        commits = git('rev-list', f'{base}..{candidate}').splitlines()
        if not commits or len(commits) != len(set(commits)):
            raise ValueError('No candidate changes or duplicate commits')
        changes = promotion.get('changes', [])
        if not isinstance(changes, list) or {x.get('commit') for x in changes} != set(commits) or len(changes) != len(commits):
            raise ValueError('Promotion must cover EVERY candidate commit absent from main exactly once')
        by_pr = {}
        for item in changes:
            number = item.get('pr')
            if type(number) is not int or number <= 0:
                raise ValueError('Each candidate commit needs its merged develop PR')
            by_pr.setdefault(number, set()).add(item['commit'])
        required = {}
        for number, covered in by_pr.items():
            source = api(f'pulls/{number}')
            if (not source.get('merged') or source['base']['ref'] != 'develop' or
                    source['merge_commit_sha'] not in covered or
                    source['base']['repo']['full_name'] != pr['base']['repo']['full_name'] or
                    source['head']['repo'] is None or source['head']['repo']['full_name'] != pr['base']['repo']['full_name']):
                raise ValueError('Commit is not the identified same-repository merged develop PR')
            if covered != source_commits(source, base, git):
                raise ValueError('Candidate commits do not exactly match their merged develop PR provenance')
            source_issue, source_path, source_mode = metadata(source)
            if source_mode != 'develop':
                raise ValueError('Missing develop acceptance provenance')
            source_gui = field(source['body'], 'GUI') == 'required'
            change = validate_change(json.loads(git('show', f'{source["merge_commit_sha"]}:{source_path}')), source_issue, source_gui)
            for case in change['cases']:
                key = f'{source_issue}:{case["id"]}'
                revision = revisions.get(key)
                if revision and (revision['source_pr'] != number or
                                 revision['source_merge'] != source['merge_commit_sha'] or
                                 revision['original_case_sha256'] != json_hash(case)):
                    raise ValueError('Environment revision does not match merged Case provenance: ' + key)
                requirement = (case.get('required_execution'), case.get('artifact', 'plugin'))
                if key in required and required[key] != requirement:
                    raise ValueError('Case execution requirement changed across candidate commits')
                required[key] = requirement
    results = promotion.get('results', {})
    if not isinstance(results, dict):
        raise ValueError('Candidate results must be an object matching ALL required Cases')
    if set(results) != set(required):
        raise ValueError('Candidate results must match ALL required Cases; missing=' +
                         ','.join(sorted(set(required) - set(results))) + '; extra=' +
                         ','.join(sorted(set(results) - set(required))))
    artifacts = dict(promotion.get('artifacts', {}))
    if promotion.get('artifact_sha256'):
        artifacts['plugin'] = promotion['artifact_sha256']
    for key, result in results.items():
        execution, artifact_name = required[key]
        artifact = artifacts.get(artifact_name)
        if not HASH.fullmatch(artifact or ''):
            raise ValueError('Fixed candidate artifact hash is required: ' + artifact_name)
        validate_observation(result, candidate, artifact)
        validate_environment(result, revisions.get(key))
        if execution and result.get('execution') != execution:
            raise ValueError('Case requires its specified execution method: ' + key)
    return {'mode': mode, 'gui_complete': True, 'cases': len(required), 'candidate': candidate}


def render_queue(paths, promotion=None, git=git_read):
    """Human view is generated from JSON; it is never a second editable status source."""
    promotion = promotion or {}
    candidate = promotion.get('candidate')
    results = promotion.get('results', {})
    revisions = (environment_cases(promotion['base'], candidate, git)
                 if candidate and promotion.get('scope', 'develop') == 'develop' else {})
    rows = []
    for path in paths:
        data = json.loads(Path(path).read_text())
        validate_change(data, data['issue'], data['gui_required'])
        if candidate and promotion.get('scope') == 'main':
            revisions.update(scoped_environment_cases(promotion['base'], candidate, data['issue'], git))
        rows.extend((data, revisions.get(f'{data["issue"]}:{case["id"]}', {}).get('case', case))
                    for case in data['cases'])
    lines = ['# 今回の動作確認一覧', '',
             '> 自動生成。結果は正本JSONへ入力して再生成してください。過去buildの結果は参考です。', '',
             '固定候補SHA: ' + (candidate or '未固定'),
             '対象ZIP SHA-256: ' + (promotion.get('artifact_sha256') or '未登録'), '',
             '| Case / Issue / PR | 対象 | 候補結果 | main可否（Case単位） | 修正先 |',
             '|---|---|---|---|---|']
    for data, case in rows:
        key = f'{data["issue"]}:{case["id"]}'
        result = results.get(key, {})
        passed = False
        try:
            artifacts = dict(promotion.get('artifacts', {}))
            if promotion.get('artifact_sha256'):
                artifacts['plugin'] = promotion['artifact_sha256']
            artifact = artifacts.get(case.get('artifact', 'plugin'))
            if not HASH.fullmatch(artifact or ''):
                raise ValueError('Candidate artifact not registered')
            validate_observation(result, candidate, artifact)
            validate_environment(result, revisions.get(key))
            passed = not case.get('required_execution') or result.get('execution') == case['required_execution']
        except ValueError:
            pass
        lines.append(f'| {case["id"]} / #{data["issue"]} / #{data.get("pr", "未作成")} | {case["change"]} | '
                     f'{result.get("status", "pending")} | {"Case合格（全範囲gateは別途必要）" if passed else "不可・固定候補のpass未登録"} | '
                     f'{case["fix_issue"] or "—"} / {case["fix_pr"] or "—"} |')
    for data, case in rows:
        lines += ['', f'## #{data["issue"]} / {case["id"]}: {case["change"]}', '',
                  f'PR: [#{data["pr"]}](https://github.com/shinma06/cursor-in-android-studio/pull/{data["pr"]})' if data.get('pr') else 'PR: 未登録', '', '前提・対象build: ' + case['preconditions'], '']
        if data.get('pr_role') == 'related_evidence_only':
            lines += ['このPRは関連証拠です。親Issueの残条件であり、当該PRのmain受入へ追加しません。', '']
        revision = revisions.get(f'{data["issue"]}:{case["id"]}')
        if revision:
            lines += ['適用環境revision: ' + revision['revision'],
                      'Rabbit移行後の新しい受入条件です。旧Quailの合格・互換性を示すものではありません。',
                      '旧固定Caseの前提（履歴）: ' + revision['original']['preconditions'],
                      '旧固定Caseの期待結果（履歴）: ' + revision['original']['expected'], '']
        lines += [f'{n}. {step}' for n, step in enumerate(case['steps'], 1)]
        lines += ['', '期待結果: ' + case['expected'], '']
        result = results.get(f'{data["issue"]}:{case["id"]}')
        if result:
            lines += ['今回の候補結果: ' + result.get('status', 'pending'),
                      f'確認者: {result.get("actor", "未登録")} / {result.get("observer", "未登録")}',
                      '確認日時: ' + result.get('at', '未登録'), '実施経路: ' + result.get('execution', '未登録'),
                      '観察/失敗理由: ' + result.get('reason', '未登録'),
                      '証拠: ' + result.get('evidence', '未登録'),
                      'ロード実体: ' + result.get('loaded_identity', '未登録'),
                      '対象artifact SHA-256: ' + result.get('artifact_sha256', '未登録'), '']
        for actor, label in (('gpt', 'Agent（互換キーgpt）'), ('human', '人間')):
            recorded = case[actor]
            lines += [f'初期登録時の{label}: {recorded["status"]} — {recorded["reason"]}']
        lines += ['', f'修正Issue/PR: {case["fix_issue"] or "未登録"} / {case["fix_pr"] or "未登録"}',
                  '再確認: ' + case['recheck'], '次の操作: ' + case['next_action'],
                  '根拠: ' + ', '.join(case['provenance']), '']
        if case.get('history'):
            lines += ['<details><summary>過去の観察（新候補へ転記しない）</summary>', '', '```json',
                      json.dumps(case['history'], ensure_ascii=False, indent=2), '```', '', '</details>', '']
    return '\n'.join(lines)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('files', nargs='*', help='Select only this batch of change JSON files')
    parser.add_argument('--batch', type=Path, help='JSON with repository-relative files and optional promotion path')
    parser.add_argument('--promotion', type=Path, help='Candidate/results JSON; observations are rendered for this build')
    parser.add_argument('--output', type=Path, help='Generated Markdown view; never edit it directly')
    args = parser.parse_args()
    batch = json.loads(args.batch.read_text()) if args.batch else {}
    files = args.files or batch.get('files', [])
    if not files:
        parser.error('Select a batch or change JSON files')
    result_path = args.promotion or batch.get('promotion')
    promotion = json.loads(Path(result_path).read_text()) if result_path else None
    rendered = render_queue(files, promotion)
    if args.output:
        args.output.write_text(rendered + '\n')
    else:
        print(rendered)
