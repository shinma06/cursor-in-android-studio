"""Project the seven #590 contracts from trusted main; never rewrite their evidence."""
import re

from verification import HASH, SHA, json_hash, nonempty, regular_json, validate_change

POLICY = 'docs/verification/applicability/issue-590.json'
KEY = re.compile(r'([1-9][0-9]*):([A-Z][A-Z0-9-]+)')


def original_case(entry, key, git):
    issue, case_id = KEY.fullmatch(key).groups()
    data = regular_json(entry['source_merge'], entry['source_path'], git)
    validate_change(data, int(issue), True)
    matches = [case for case in data['cases'] if case['id'] == case_id]
    if len(matches) != 1 or json_hash(matches[0]) != entry['original_case_sha256']:
        raise ValueError('Applicability original Case hash mismatch: ' + key)
    return matches[0]


def scoped_original(plan, key, merge, digest, pr):
    matches = [(case_id, origin) for case_id, origin in plan['case_origins'].items()
               if origin.get('old_key') == key]
    if len(matches) != 1:
        raise ValueError('Applicability requires one scoped original: ' + key)
    case_id, origin = matches[0]
    sources = [source for source in origin['original_merged_requirements']
               if source['source_pr'] == pr and source['source_merge'] == merge and source['original_case_sha256'] == digest
               and json_hash(source['case']) == digest]
    if len(sources) != 1:
        raise ValueError('Applicability scoped original hash/provenance mismatch: ' + key)
    return case_id, sources[0]['case']


def case_revisions(base, candidate, git, scope_issue=None):
    if not base or not candidate or not git('ls-tree', base, '--', POLICY):
        return {}
    policy = regular_json(base, POLICY, git)
    if (set(policy) != {'schema', 'issue', 'decision', 'cases', 'scopes'} or
            policy['schema'] != 1 or policy['issue'] != 590 or not nonempty(policy['decision']) or
            not isinstance(policy['cases'], dict) or not policy['cases'] or not isinstance(policy['scopes'], dict)):
        raise ValueError('Invalid trusted Case applicability policy')
    scope = None
    if scope_issue is not None:
        binding = policy['scopes'].get(str(scope_issue))
        if binding is None:
            return {}
        scope = regular_json(base, f'docs/verification/scopes/issue-{scope_issue}.json', git)
        if (set(binding) != {'plan_sha256', 'original_candidate'} or
                json_hash(scope) != binding['plan_sha256'] or
                scope['original_candidate'] != binding['original_candidate']):
            raise ValueError('Applicability scoped plan identity mismatch')
        ancestry = {item['commit'] for item in scope['original_provenance']['candidate_commits']}
    else:
        ancestry = set(git('rev-list', candidate).splitlines())
    revisions = {}
    for key, entry in policy['cases'].items():
        match = KEY.fullmatch(key)
        fields = {'source_pr', 'source_merge', 'source_path', 'original_case_sha256',
                  'adopted_pr', 'adopted_merge', 'adopted_cases', 'changes', 'reason', 'qa'}
        if (not match or not isinstance(entry, dict) or set(entry) != fields or
                any(type(entry.get(k)) is not int or entry[k] <= 0 for k in ('source_pr', 'adopted_pr')) or
                any(not isinstance(entry.get(k), str) or not SHA.fullmatch(entry[k])
                    for k in ('source_merge', 'adopted_merge')) or
                entry['source_path'] != f'docs/verification/changes/issue-{match[1]}.json' or
                not isinstance(entry.get('original_case_sha256'), str) or
                not HASH.fullmatch(entry['original_case_sha256']) or
                not isinstance(entry['adopted_cases'], dict) or not entry['adopted_cases'] or
                any(not KEY.fullmatch(k) or not isinstance(v, str) or not HASH.fullmatch(v)
                    for k, v in entry['adopted_cases'].items()) or
                not isinstance(entry['changes'], dict) or not entry['changes'] or
                set(entry['changes']) - {'steps', 'expected'} or
                ('expected' in entry['changes'] and not nonempty(entry['changes']['expected'])) or
                ('steps' in entry['changes'] and (not isinstance(entry['changes']['steps'], list) or
                 not entry['changes']['steps'] or not all(nonempty(s) for s in entry['changes']['steps']))) or
                not nonempty(entry['reason']) or not isinstance(entry['qa'], list) or
                not entry['qa'] or any(type(n) is not int or n <= 0 for n in entry['qa'])):
            raise ValueError('Invalid bounded Case applicability entry: ' + key)
        if entry['adopted_merge'] not in ancestry:
            continue
        if scope is None:
            case = original_case(entry, key, git)
            result_key = key
        else:
            case_id, _ = scoped_original(scope, key, entry['source_merge'], entry['original_case_sha256'], entry['source_pr'])
            cases = [c for c in scope['acceptance']['cases'] if c['id'] == case_id]
            if len(cases) != 1:
                raise ValueError('Applicability scoped acceptance missing or duplicated: ' + key)
            case = cases[0]
            result_key = f'{scope_issue}:{case_id}'
        for adopted_key, digest in entry['adopted_cases'].items():
            if scope is not None:
                scoped_original(scope, adopted_key, entry['adopted_merge'], digest, entry['adopted_pr'])
            else:
                adopted_issue = KEY.fullmatch(adopted_key)[1]
                original_case(dict(entry, source_merge=entry['adopted_merge'],
                                   source_path=f'docs/verification/changes/issue-{adopted_issue}.json',
                                   original_case_sha256=digest), adopted_key, git)
        revisions[result_key] = dict(entry, original=case, case=dict(case, **entry['changes']),
                                     revision=json_hash({'policy': json_hash(policy), 'key': key, 'scope': scope_issue}),
                                     decision=policy['decision'])
    return revisions


def apply_revision(case, revision, environment=None):
    if revision and case != revision['original']:
        raise ValueError('Applicability does not match the selected original Case')
    if revision or environment:
        return dict(environment['case'] if environment else case, **(revision['changes'] if revision else {}))
    return case


def validate_applicability(result, revision):
    if revision and (result.get('status') == 'reused' or
                     result.get('applicability_revision') != revision['revision']):
        raise ValueError('Changed Case contract requires a fresh observation bound to its applicability revision')
