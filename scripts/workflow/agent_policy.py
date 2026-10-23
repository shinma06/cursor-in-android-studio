"""Pure decisions shared by the autonomous coordinator and its tests."""
import fnmatch
import hashlib
import json
import re
import uuid
from git_guard import BRANCH

CONTEXT = 'Agent review'
SHA = re.compile(r'[0-9a-f]{40}')

SESSION_HASH = re.compile(r'(?:codex|native)-session-sha256:[0-9a-f]{64}')
NATIVE_SESSION = re.compile(r'native-session-v1:([0-9a-f]+):([0-9a-f]+)')


def native_session(issuer, identity):
    """Issuer/instance and opaque ID come from the trusted host, never a role/model label."""
    if (not isinstance(issuer, str) or not re.fullmatch(r'[a-z0-9][a-z0-9./_-]*', issuer) or
            'codex' in issuer or not isinstance(identity, str) or not identity or
            any(ord(c) < 32 or ord(c) == 127 for c in identity) or SESSION_HASH.fullmatch(identity) or
            identity.startswith('native-session-')):
        raise ValueError('Invalid native issuer/session identity')
    # UUID identities retain their single historical identity, never a namespace alias.
    try:
        uuid.UUID(identity)
    except ValueError:
        pass
    else:
        return canonical_session(identity)
    return f'native-session-v1:{issuer.encode().hex()}:{identity.encode().hex()}'


def canonical_session(session):
    match = NATIVE_SESSION.fullmatch(session) if isinstance(session, str) else None
    if match:
        try:
            issuer, identity = (bytes.fromhex(part).decode() for part in match.groups())
            if native_session(issuer, identity) == session:
                return session
        except (ValueError, UnicodeError):
            pass
        raise ValueError('Noncanonical native session identity')
    try:
        value = uuid.UUID(session) if isinstance(session, str) else None
    except ValueError:
        value = None
    if value is None or str(value) != session or value.variant != uuid.RFC_4122:
        raise ValueError('Canonical originating session is missing or malformed')
    return session


def codex_session(session):
    session = canonical_session(session)
    if session.startswith('native-session-v1:'):
        raise ValueError('Codex source requires its canonical UUID session')
    return session


def session_fingerprint(session):
    session = canonical_session(session)
    if session.startswith('native-session-v1:'):
        return 'native-session-sha256:' + hashlib.sha256(session.encode()).hexdigest()
    return 'codex-session-sha256:' + hashlib.sha256(('codex-session-v1:' + session).encode()).hexdigest()


def issue_number(pr):
    match = BRANCH.fullmatch(pr['head']['ref'])
    issues = re.findall(r'^Issue: #([1-9][0-9]*)\s*$', pr.get('body') or '', re.M)
    if not match or issues != [match.group(2)]:
        raise ValueError('PR branch and Issue metadata do not match')
    return int(issues[0])


def eligible(pr, owner, repo):
    try:
        issue_number(pr)
        return (pr['user']['login'] == owner and pr['base']['ref'] in ('main', 'develop') and
                pr['base']['repo']['full_name'] == repo and pr['head']['repo'] is not None and
                pr['head']['repo']['full_name'] == repo)
    except (ValueError, KeyError, TypeError):
        return False


def binding(pr, issue):
    return {'head': pr['head']['sha'], 'base': pr['base']['sha'], 'target': pr['base']['ref'],
            'title_hash': hashlib.sha256((pr.get('title') or '').encode()).hexdigest(),
            'body_hash': hashlib.sha256((pr.get('body') or '').encode()).hexdigest(),
            'issue_bug': any((x.get('name') if isinstance(x, dict) else x) == 'type:bug' for x in issue.get('labels', [])),
            'issue_hash': hashlib.sha256((issue.get('body') or '').encode()).hexdigest()}


def validate_review(report, expected, acceptance=None):
    if (report.get('model') not in ('gpt-6.1-sol', 'gpt-6-astra') or
            report.get('reasoning_effort') not in ('medium', 'high', 'xhigh') or
            not report.get('model_evidence')):
        raise ValueError('Independent review requires verified assigned model/effort settings')
    for key in ('head', 'base'):
        if report.get(key) != expected[key]:
            raise ValueError('Stale reviewer ' + key)
    if report.get('verdict') not in ('approved', 'changes_requested', 'blocked'):
        raise ValueError('Invalid review verdict')
    for key in ('scope_complete', 'issue_complete', 'gui_required'):
        if type(report.get(key)) is not bool:
            raise ValueError('Missing review decision: ' + key)
    if not isinstance(report.get('findings'), list) or not all(isinstance(x, str) for x in report['findings']):
        raise ValueError('Invalid findings')
    if not report.get('session') or not report.get('evidence'):
        raise ValueError('Review session/evidence missing')
    if report['verdict'] == 'approved' and (report['findings'] or not report['scope_complete']):
        raise ValueError('Approval conflicts with incomplete scope or unresolved findings')
    if report['verdict'] == 'approved' and (acceptance or {}).get('repair_revision'):
        session = report['session'] if SESSION_HASH.fullmatch(report['session']) else session_fingerprint(report['session'])
        if (report.get('repair_revision') != acceptance['repair_revision'] or
                session in acceptance['repair_contributors'] or not report.get('origin_evidence') or
                not isinstance(report.get('repair_assessment'), str) or not report['repair_assessment'].strip()):
            raise ValueError('Repair requires independent actual-origin assessment of every original failed guarantee')
    if report['verdict'] == 'approved' and (acceptance or {}).get('certificate_revision'):
        if (report.get('certificate_revision') != acceptance['certificate_revision'] or
                (report['session'] if SESSION_HASH.fullmatch(report['session']) else session_fingerprint(report['session'])) not in acceptance['certificate_reviewers'] or
                not report.get('origin_evidence') or
                not isinstance(report.get('certificate_assessment'), str) or not report['certificate_assessment'].strip()):
            raise ValueError('Certificate requires its actual independent reviewer and substantive fixed assessment')
    if report['verdict'] == 'changes_requested' and not report['findings']:
        raise ValueError('Changes requested without concrete findings')


def in_scope(paths, scope):
    return all(any(p == s or (s.endswith('/') and p.startswith(s)) or
                   ('*' in s and fnmatch.fnmatchcase(p, s)) for s in scope) for p in paths)


def gui_pass(evidence, bound):
    return bool(evidence and evidence.get('processes_stopped') is True and evidence.get('head') == bound['head'] and evidence.get('base') == bound['base'] and
                re.fullmatch(r'[0-9a-f]{64}', evidence.get('artifact_sha256', '')) and
                all(evidence.get(k) for k in ('run', 'observer', 'loaded_identity', 'evidence_url')) and
                evidence.get('cases') and all(x.get('status') == 'pass' and x.get('id') and x.get('observation')
                                              for x in evidence['cases']))


def next_action(state, bound, ci, gui, acceptance=None):
    if bound.get('remote_review_complete') is False:
        return 'remote-review-wait'
    if state.get('binding') != bound:
        return 'review'
    report = state.get('review')
    if not report:
        return 'review'
    if report['verdict'] == 'blocked':
        return 'blocked'
    if report['verdict'] == 'changes_requested':
        return 'fix' if state.get('fixes', 0) < 3 else 'blocked'
    if acceptance is not None and not acceptance.get('allowed'):
        return 'acceptance-wait'
    if acceptance is None and gui and not gui_pass(state.get('gui'), bound):
        return 'gui-queued'
    if ci == 'failure':
        return 'ci-failed'
    if ci != 'success':
        return 'ci-wait'
    return 'merge'


def update_parent(body, issue):
    """Only check a line dedicated to this Issue; never check aggregate lines."""
    lines = body.splitlines(keepends=True)
    for i, line in enumerate(lines):
        if re.match(rf'^\s*- \[ \] #{issue}(?!\d)(?:\s|:|：|—|$)', line) and len(re.findall(r'#\d+', line)) == 1:
            lines[i] = line.replace('[ ]', '[x]', 1)
    return ''.join(lines)
