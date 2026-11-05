#!/usr/bin/env python3
"""One bounded PR transition per tick. Run from trusted main, never from PR code."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import uuid
from urllib.parse import quote

from agent_policy import CONTEXT, binding, eligible, gui_pass, in_scope, issue_number, next_action, remote_review_ready, review_time, update_parent, validate_review
from agent_worker import WorkerSettingsError, run_worker, verify_independent_review, worker_environment
from handoff_registry import read_review_evidence, register, resolve, retain_review_evidence
from verification import verify_pr, metadata
from issue_schema import validate_issue, done_labels, labels
from qa_handoff import handoff
from change_impact import git_impact, test_commands, report as impact_report

REPO = 'shinma06/cursor-in-android-studio'
OWNER = 'shinma06'
ROOT = Path(__file__).resolve().parents[2]
HANDOFF_V2 = '<!-- agent-loop-handoff:v2 -->'
STATE = '<!-- agent-loop-state:v1 -->'
HOST = socket.gethostname()


def command(args, cwd=None, timeout=120, check=True):
    cwd = ROOT if cwd is None else cwd
    result = subprocess.run(args, cwd=cwd, env=worker_environment(), text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f'{args[0]} failed: {result.stderr[-1800:]}')
    return result.stdout.strip()


def git(*args, cwd=None):
    return command(['git', *args], cwd)


class GitHub:
    def api(self, path, method='GET', data=None):
        args = ['gh', 'api', '--method', method, path]
        if data is None:
            out = command(args)
        else:
            result = subprocess.run(args + ['--input', '-'], input=json.dumps(data), text=True, cwd=ROOT,
                                    capture_output=True, timeout=120)
            if result.returncode:
                raise RuntimeError(result.stderr[-1800:])
            out = result.stdout
        return json.loads(out) if out.strip() else None

    def pages(self, path):
        out = command(['gh', 'api', '--paginate', '--slurp', path])
        return [item for page in json.loads(out) for item in page]

    def review_threads(self, number):
        # REST already supplies every reply; GraphQL adds resolution/outdated state.
        query = '''query($endCursor:String){repository(owner:"shinma06",name:"cursor-in-android-studio"){
          pullRequest(number:NUMBER){reviewThreads(first:100,after:$endCursor){
            nodes{id isResolved isOutdated comments(first:1){nodes{databaseId}}}
            pageInfo{hasNextPage endCursor}}}}}'''.replace('NUMBER', str(number))
        pages = json.loads(command(['gh', 'api', 'graphql', '--paginate', '--slurp', '-f', 'query=' + query]))
        if (not pages or any(page.get('errors') for page in pages) or
                pages[-1]['data']['repository']['pullRequest']['reviewThreads']['pageInfo'].get('hasNextPage') is not False):
            raise ValueError('Review thread acquisition failed; feedback is incomplete')
        return [node for page in pages for node in page['data']['repository']['pullRequest']['reviewThreads']['nodes']]

    def pr(self, number):
        pr = self.api(f'repos/{REPO}/pulls/{number}')
        if pr['state'] == 'open' and eligible(pr, OWNER, REPO):
            # pulls.base.sha can lag behind the branch after another PR merges.
            # Use the actual branch ref for review/GUI bindings and final merge checks.
            ref = self.api(f'repos/{REPO}/git/ref/heads/{quote(pr["base"]["ref"], safe="")}')
            if (ref.get('ref') != 'refs/heads/' + pr['base']['ref'] or
                    ref.get('object', {}).get('type') != 'commit' or
                    not re.fullmatch(r'[0-9a-f]{40}', ref['object'].get('sha', ''))):
                raise ValueError('Invalid base branch reference; cannot reconcile acceptance')
            pr['base']['sha'] = ref['object']['sha']
        return pr

    def issue(self, number):
        return self.api(f'repos/{REPO}/issues/{number}')

    def comments(self, number):
        return self.pages(f'repos/{REPO}/issues/{number}/comments?per_page=100')

    def comment(self, number, body, comment_id=None):
        path = f'repos/{REPO}/issues/comments/{comment_id}' if comment_id else f'repos/{REPO}/issues/{number}/comments'
        return self.api(path, 'PATCH' if comment_id else 'POST', {'body': body})

    def status(self, sha, state, description, url):
        existing = self.api(f'repos/{REPO}/commits/{sha}/status')['statuses']
        latest = next((x for x in existing if x['context'] == CONTEXT), None)
        if latest and latest['state'] == state and latest['description'] == description:
            return
        self.api(f'repos/{REPO}/statuses/{sha}', 'POST', {'context': CONTEXT, 'state': state,
                 'description': description[:140], 'target_url': url})

    def ci(self, pr):
        checks = json.loads(command(['gh', 'pr', 'checks', str(pr['number']), '--repo', REPO,
                                    '--json', 'name,state'], check=False) or '[]')
        states = {x['name']: x['state'] for x in checks}
        needed = [states.get(x) for x in ('test', 'PR policy', 'Acceptance gate')]
        if any(x in ('FAILURE', 'ERROR', 'CANCELLED', 'TIMED_OUT', 'ACTION_REQUIRED') for x in needed):
            return 'failure'
        return 'success' if all(x == 'SUCCESS' for x in needed) else 'pending'

    def merge(self, pr):
        # Both the head guard and active strict branch rules are enforced by GitHub.
        if pr['draft']:
            command(['gh', 'pr', 'ready', str(pr['number']), '--repo', REPO])
        result = self.api(f'repos/{REPO}/pulls/{pr["number"]}/merge', 'PUT',
                          {'sha': pr['head']['sha'], 'commit_title': pr['title'],
                           'commit_message': pr.get('body') or '', 'merge_method': 'merge' if metadata(pr)[2] == 'promotion' else 'squash'})
        if not result.get('merged'):
            raise RuntimeError('Merge was not confirmed: ' + str(result))
        return result


def unpack(comments, marker):
    selected = [c for c in comments if c['user']['login'] == OWNER and c['body'].startswith(marker + '\n')]
    if not selected:
        return None, None
    latest = max(selected, key=lambda c: c['id'])
    payload = latest['body'].split('```json\n', 1)[1].split('\n```', 1)[0]
    record = json.loads(payload)
    if not isinstance(record, dict) or not record:
        raise ValueError('Agent loop record must be a nonempty JSON object')
    return record, latest['id']


def pack(marker, state):
    return marker + '\n' + f'Agent loop: **{state.get("phase", "handoff")}**\n\n```json\n' + json.dumps(state, ensure_ascii=False, indent=2) + '\n```'


def retry_timestamp(state):
    value = state.get('retry_at', 0)
    # Keep large JSON integers exact; only floats can carry NaN or infinity.
    if type(value) not in (int, float) or (type(value) is float and not math.isfinite(value)):
        raise ValueError('Invalid retry_at: expected a finite number')
    return value


def handoff_matches(pr, handoff):
    # Selection shares tick's metadata check so unmanaged records cannot starve ready work.
    return bool(handoff and handoff.get('host') == HOST and handoff.get('pr') == pr['number'] and
                handoff.get('issue') == issue_number(pr) and handoff.get('branch') == pr['head']['ref'] and
                handoff.get('target', 'main') == pr['base']['ref'] and handoff.get('writer_stopped') is True)


@contextmanager
def coordinator_lock():
    # One active coordinator per OS user, including separate clones of this repo.
    path = Path('/tmp') / f'cursor-in-android-studio-coordinator-{os.getuid()}'
    fd = os.open(path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


class Loop:
    def __init__(self, gh=None, storage=None, worker=run_worker, review_retry_comment=None, review_retry_original=None):
        self.gh = gh or GitHub()
        # Stable across linked worktrees, persists through app restarts. Not checked in.
        common = Path(git('rev-parse', '--git-common-dir'))
        if not common.is_absolute():
            common = ROOT / common
        self.storage = Path(storage) if storage else common.resolve() / 'agent-loop'
        self.worker = worker
        self.review_retry_comment = review_retry_comment
        self.review_retry_original = review_retry_original

    def load(self, number, pr=None):
        pr = pr or self.gh.pr(number)
        if pr.get('number') != number or not eligible(pr, OWNER, REPO) or pr['state'] != 'open' or pr.get('merged'):
            raise ValueError('Normal execution requires an owned open PR; terminal records are read-only')
        comments = self.gh.comments(number)
        public, _ = unpack(comments, HANDOFF_V2)
        if not public:
            return None, {}, None, comments
        handoff = resolve(self.storage, public, HOST)
        state, comment_id = unpack(comments, STATE)
        if state and state.get('phase') == 'done':
            raise ValueError('Completed operation requires explicit owner recovery; do not reuse old approval')
        return handoff, state or {'phase': 'queued', 'fixes': 0, 'reviews': 0, 'errors': 0}, comment_id, comments

    def feedback(self, pr):
        comments = self.gh.comments(pr['number'])
        if not eligible(pr, OWNER, REPO):
            records = [*comments,
                       *self.gh.pages(f'repos/{REPO}/pulls/{pr["number"]}/comments?per_page=100'),
                       *self.gh.pages(f'repos/{REPO}/pulls/{pr["number"]}/reviews?per_page=100'),
                       *self.gh.review_threads(pr['number'])]
            return {'pr': pr['number'], 'binding': None, 'feedback': records,
                    'review_wait_satisfied': False, 'execution': 'read-only non-current metadata'}
        # History never needs the retired execution registry or malformed terminal STATE.
        prior, _ = unpack(comments, STATE) if pr['state'] == 'open' and not pr.get('merged') else (None, None)
        bound, records = self.bound(pr, self.gh.issue(issue_number(pr)), comments, prior)
        return {'pr': pr['number'], 'binding': bound, 'feedback': records,
                'review_wait_satisfied': remote_review_ready(bound)}

    @staticmethod
    def pending_finish(pr, public, state):
        bound, report, accepted = (state.get(key, {}) for key in ('binding', 'review', 'acceptance'))
        intent = state.get('merge_intent')
        if not all(isinstance(record, dict) for record in (bound, report, accepted)):
            return False
        return bool(pr.get('merged') and pr['state'] == 'closed' and eligible(pr, OWNER, REPO) and
                    public and public.get('version') == 2 and public.get('owner') and
                    public.get('writer_stopped') is True and public.get('pr') == pr['number'] and
                    public.get('issue') == issue_number(pr) and public.get('branch') == pr['head']['ref'] and
                    public.get('target') == pr['base']['ref'] and not state.get('paused') and
                    state.get('phase') in ('merging', 'cleanup') and
                    state.get('expected_head') == pr['head']['sha'] and
                    isinstance(intent, dict) and intent == {'registry_id': public.get('registry_id'),
                        'head': pr['head']['sha'], 'base': bound.get('base')} and
                    bound.get('head') == pr['head']['sha'] and bound.get('base') == pr['base']['sha'] and
                    bound.get('target') == pr['base']['ref'] and
                    all(re.fullmatch(r'[0-9a-f]{64}', str(bound.get(key, '')))
                        for key in ('title_hash', 'body_hash', 'issue_hash', 'feedback_hash')) and
                    report.get('verdict') == 'approved' and report.get('scope_complete') is True and
                    report.get('head') == bound.get('head') and report.get('base') == bound.get('base') and
                    accepted.get('allowed') is True and accepted.get('head') == bound.get('head') and
                    accepted.get('base') == bound.get('base'))

    def continue_finish(self, number):
        """Continue only a live operation's recorded merge intent, never ordinary closed execution."""
        pr = self.gh.pr(number)
        if not pr.get('merged') or pr['state'] != 'closed' or not eligible(pr, OWNER, REPO):
            raise ValueError('Postmerge continuation requires a confirmed owned merge')
        comments = self.gh.comments(number)
        try:
            public, _ = unpack(comments, HANDOFF_V2)
            state, comment_id = unpack(comments, STATE)
            current_operation = bool(state and self.pending_finish(pr, public, state))
        except (ValueError, KeyError, IndexError, TypeError):
            current_operation = False
        if not current_operation:
            raise ValueError('No current unpaused pending merge operation; preserve historical custody')
        h = resolve(self.storage, public, HOST)
        self.source_safe(h)
        if (self.storage / f'pr-{number}' / 'worker.json').exists():
            raise ValueError('Worker state remains; preserve postmerge resources')
        current, _ = self.bound(pr, self.gh.issue(h['issue']), comments, state)
        if current != state['binding']:
            raise ValueError('Postmerge binding changed; keep resources for the original owner')
        self.validate_review(state['review'], current, state['acceptance'])
        return self._finish_transition(pr, h, state, comment_id)

    def _finish_transition(self, pr, h, state, comment_id):
        if h.get('host') != HOST or not self.pending_finish(pr, h, state):
            raise ValueError('No current bound postmerge operation; preserve resources')
        try:
            return self.finish(pr, h, state, comment_id)
        except Exception as error:
            state.update(phase='cleanup', next=self.private_error(error))
            self.save(pr, state, comment_id)
            return {'pr': pr['number'], 'phase': 'cleanup', 'error': state['next']}

    def save(self, pr, state, comment_id):
        state['updated_at'] = int(time.time())
        # Raw private logs stay local. Public state may contain reviewer text, so remove local paths.
        state = self.public_state(state)
        body = pack(STATE, state)
        result = self.gh.comment(pr['number'], body, comment_id)
        # A dedicated Issue comment is the recoverable dashboard; don't overwrite Issue criteria.
        issue = issue_number(pr)
        marker = f'<!-- agent-loop-progress:pr-{pr["number"]} -->'
        comments = self.gh.comments(issue)
        prior = next((c for c in reversed(comments) if c['user']['login'] == OWNER and c['body'].startswith(marker)), None)
        progress = (marker + f'\nPR #{pr["number"]}: **{state["phase"]}**\n'
                    f'HEAD: {pr["head"]["sha"]}\nOwner: agent-loop\n'
                    f'Next: {state.get("next", state["phase"])}\nDetails: {result["html_url"]}')
        if not prior or prior['body'] != progress:
            self.gh.comment(issue, progress, prior['id'] if prior else None)
        return result['id']

    def private_error(self, error):
        identifier = uuid.uuid4().hex
        directory = self.storage / 'diagnostics'
        directory.mkdir(mode=0o700, exist_ok=True)
        path = directory / (identifier + '.txt')
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as stream:
            stream.write(str(error))
        return f'Operation failed ({type(error).__name__}); coordinator private diagnostic {identifier}'

    @staticmethod
    def public_session(value):
        from agent_policy import session_fingerprint, SESSION_HASH
        if isinstance(value, str) and SESSION_HASH.fullmatch(value):
            return value
        try:
            return session_fingerprint(value)
        except ValueError:
            return value  # Legacy reports are readable, never origin evidence.

    def public_state(self, value):
        if isinstance(value, dict):
            return {k: (self.public_session(v) if k == 'session' else self.public_state(v))
                    for k, v in value.items() if k not in ('host', 'source')}
        if isinstance(value, list):
            return [self.public_state(v) for v in value]
        if isinstance(value, str):
            for private in (str(self.storage), str(ROOT), str(Path.home()), HOST):
                value = value.replace(private, '[local]')
            return re.sub(r'/(?:Users|home|private|tmp|var)/[^\n`]+', '[local path]', value)
        return value

    def acceptance(self, pr, path):
        try:
            result = verify_pr(pr, lambda p: self.gh.api(f'repos/{REPO}/' + p),
                               lambda *args: git(*args, cwd=path))
            return dict(result, allowed=True)
        except (ValueError, KeyError, OSError, RuntimeError) as error:
            return {'allowed': False, 'reason': self.private_error(error)}

    def bound(self, pr, issue, comments, prior=None):
        value = binding(pr, issue)
        # Exclude only our changing dashboards, never bot/Issue/reviewer evidence.
        conversation = [c for c in comments + self.gh.comments(issue_number(pr))
                        if not (c['user']['login'] == OWNER and
                                c['body'].startswith(('<!-- agent-loop-handoff:v1 -->', HANDOFF_V2, STATE, '<!-- agent-loop-progress:',
                                                      '<!-- issue-qa-link:v1 ', '<!-- issue-qa-record:v1 ')))]
        inline = self.gh.pages(f'repos/{REPO}/pulls/{pr["number"]}/comments?per_page=100')
        reviews = self.gh.pages(f'repos/{REPO}/pulls/{pr["number"]}/reviews?per_page=100')
        threads = self.gh.review_threads(pr['number'])
        feedback = [*conversation, *inline, *reviews, *threads]
        value['feedback_hash'] = hashlib.sha256(json.dumps(feedback, sort_keys=True).encode()).hexdigest()
        value['remote_review_complete'] = remote_review_complete(pr['head']['sha'], comments)
        retry_id = getattr(self, 'review_retry_comment', None) or (prior or {}).get('review_retry_comment')
        reference = getattr(self, 'review_retry_original', None) or (prior or {}).get('review_retry_original')
        if not value['remote_review_complete'] and retry_id is not None and reference:
            original = read_review_evidence(self.storage, reference)
            if original.get('binding') != review_original_binding(pr, issue):
                raise ValueError('Original provider observation belongs to different PR metadata/source')
            retry = stalled_review_retry(pr, comments, inline, reviews, retry_id, original)
            if retry:
                value['review_retry'] = dict(retry, head=value['head'], base=value['base'], original_ref=reference)
        return value, feedback

    def prepare_review_retry(self, pr):
        """Capture actual API evidence before a single explicit retry, without posting one."""
        if not eligible(pr, OWNER, REPO) or pr['state'] != 'open':
            raise ValueError('Original observation requires an owned open PR')
        issue = self.gh.issue(issue_number(pr))
        original = {'binding': review_original_binding(pr, issue), 'comments': self.gh.comments(pr['number']),
                    'inline': self.gh.pages(f'repos/{REPO}/pulls/{pr["number"]}/comments?per_page=100'),
                    'reviews': self.gh.pages(f'repos/{REPO}/pulls/{pr["number"]}/reviews?per_page=100')}
        if review_original_binding(self.gh.pr(pr['number']), self.gh.issue(issue_number(pr))) != original['binding']:
            raise ValueError('PR/Issue changed during original observation')
        original['observed_at'] = datetime.now(timezone.utc).isoformat()
        return {'review_retry_original': retain_review_evidence(self.storage, original),
                'head': pr['head']['sha'], 'observed_at': original['observed_at']}

    def validate_review(self, report, bound, acceptance=None):
        verified = verify_independent_review(self.storage, report, bound) if bound.get('remote_review_complete') is False else False
        validate_review(report, bound, acceptance, origin_verified=verified)

    def accept_review(self, number, evidence):
        """Accept a reviewer-issued origin-bound report without enrolling, rebinding or resuming."""
        pr = self.gh.pr(number)
        h, state, comment_id, comments = self.load(number, pr)
        if (not h or h.get('version') != 2 or h.get('host') != HOST or not h.get('owner') or state.get('paused') or
                not eligible(pr, OWNER, REPO) or pr['state'] != 'open' or
                h.get('pr') != number or h.get('issue') != issue_number(pr) or
                h.get('branch') != pr['head']['ref'] or h.get('target') != pr['base']['ref'] or
                state.get('expected_head', h.get('head')) != pr['head']['sha'] or h.get('writer_stopped') is not True or
                (self.storage / f'pr-{number}' / 'worker.json').exists()):
            raise ValueError('Explicit report requires an existing stopped, unpaused v2 handoff')
        self.source_safe(h)
        bound, _ = self.bound(pr, self.gh.issue(h['issue']), comments, state)
        if bound.get('remote_review_complete') is not False or 'review_retry' not in bound:
            raise ValueError('Explicit stalled-review import requires a qualified pending-provider retry')
        self.validate_review(evidence, bound)
        state['review_retry_comment'] = bound['review_retry']['comment_id']
        state['review_retry_original'] = bound['review_retry']['original_ref']
        state.update(binding=bound, review=evidence, phase='reviewed')
        self.save(pr, state, comment_id)
        return {'pr': number, 'phase': 'reviewed', 'remote_review_complete': bound['remote_review_complete']}

    def source_safe(self, h):
        source = Path(h['source'])
        if source.exists():
            if git('branch', '--show-current', cwd=source) != h['branch'] or git('rev-parse', 'HEAD', cwd=source) != h['head']:
                raise ValueError('Original writer resumed or changed branch; handoff must be renewed')
            if git('status', '--porcelain', cwd=source):
                raise ValueError('Original handoff worktree has edits; refusing concurrent writer')

    def checkout(self, pr, h):
        path = self.storage / f'pr-{pr["number"]}' / 'checkout'
        if not path.exists():
            path.parent.mkdir(parents=True, exist_ok=True)
            git('clone', '--no-local', str(ROOT), str(path))
            git('remote', 'set-url', 'origin', f'https://github.com/{REPO}.git', cwd=path)
            git('fetch', 'origin', h['branch'], pr['base']['ref'], cwd=path)
            git('checkout', '-b', h['branch'], pr['head']['sha'], cwd=path)
            # Do not run hook implementations supplied by the PR; tests are run by coordinator.
            git('config', 'core.hooksPath', str(ROOT / '.githooks'), cwd=path)
        return path

    @staticmethod
    def invalidate_acceptance(state):
        for key in ('review', 'binding', 'gui', 'acceptance'):
            state.pop(key, None)

    def test_and_push(self, pr, path, state):
        self.invalidate_acceptance(state)
        expected = pr['head']['sha']
        actual = git('rev-parse', 'HEAD', cwd=path)
        remote = self.gh.pr(pr['number'])
        if remote['head']['sha'] != expected:
            raise ValueError('Remote HEAD changed; do not publish recovered work')
        # These repositories are maintainer-owned, enrolled work only. No forks are executed.
        impact = git_impact(pr['base']['sha'], actual, cwd=path)
        print(impact_report(impact))
        for test in test_commands(impact):
            command(test, path, 600)
        if impact.get('base'):
            command(['git', 'diff', '--check', impact['base'], impact['head'], '--'], path)
        if git('status', '--porcelain', cwd=path):
            raise ValueError('Tests left dirty files; cannot publish')
        remote = self.gh.pr(pr['number'])
        if (not eligible(remote, OWNER, REPO) or remote['state'] != 'open' or
                remote['head']['sha'] != expected):
            raise ValueError('Remote PR changed during tests; preserve unpublished work')
        git('push', 'origin', f'HEAD:refs/heads/{pr["head"]["ref"]}', cwd=path)
        state['expected_head'] = actual
        state.pop('review', None)
        state.pop('gui', None)
        state['phase'] = 'queued'
        state['next'] = 'Review the newly published HEAD'

    def tick(self, number, pr=None):
        pr = pr or self.gh.pr(number)
        if pr['state'] != 'open' or pr.get('merged'):
            return {'pr': number, 'phase': 'terminal-nonexecution', 'next': 'Read-only history; current pending operation uses continue-finish'}
        if not eligible(pr, OWNER, REPO):
            return {'pr': number, 'phase': 'unmanaged'}
        h, state, comment_id, comments = self.load(number, pr)
        if not handoff_matches(pr, h):
            return {'pr': number, 'phase': 'unmanaged'}
        if not state.get('expected_head'):
            state['expected_head'] = h['head']
        if pr['state'] != 'open' or state.get('paused'):
            return {'pr': number, 'phase': state['phase']}
        if retry_timestamp(state) > time.time():
            return {'pr': number, 'phase': 'backoff'}
        merge_requested = False
        try:
            issue = self.gh.issue(h['issue'])
            if issue['state'] != 'open' or 'pull_request' in issue:
                raise ValueError('Issue must remain open until scoped acceptance is evaluated')
            validate_issue(issue)
            self.source_safe(h)
            attempt_file = self.storage / 'attempts.json'
            attempts = json.loads(attempt_file.read_text()) if attempt_file.exists() else {}
            attempts[str(number)] = time.time()
            temporary = attempt_file.with_suffix('.tmp')
            temporary.write_text(json.dumps(attempts)); os.replace(temporary, attempt_file)
            path = self.checkout(pr, h)
            running = self.storage / f'pr-{number}' / 'worker.json'
            if running.exists():
                pid = json.loads(running.read_text())['pid']
                if pid is None:
                    raise ValueError('Worker launch was interrupted before PID acknowledgement; verify processes before resume')
                try:
                    os.killpg(pid, 0)
                    return {'pr': number, 'phase': 'worker-still-running'}
                except ProcessLookupError:
                    running.unlink()
            if pr['head']['sha'] != state['expected_head']:
                # A controller push can finish immediately before a crash. Recognize exactly its local tip.
                tip = git('rev-parse', 'HEAD', cwd=path)
                if (pr['head']['sha'] == tip == state.get('publish_head') and
                        not git('status', '--porcelain', cwd=path)):
                    state['expected_head'] = tip
                    self.invalidate_acceptance(state)
                else:
                    raise ValueError('External push after handoff; explicit re-enrollment required')
            bound, feedback = self.bound(pr, issue, comments, state)
            for name in ('review_retry_comment', 'review_retry_original'):
                selected = getattr(self, name, None)
                if selected is not None:
                    state[name] = selected
            if any(record and any(record.get(k) != bound[k] for k in ('head', 'base'))
                   for record in (state.get('binding'), state.get('gui'))):
                self.invalidate_acceptance(state)
                comment_id = self.save(pr, state, comment_id)
            self.gh.status(pr['head']['sha'], 'pending', 'Review/acceptance reconciliation in progress', pr['html_url'])
            current = git('rev-parse', 'HEAD', cwd=path)
            dirty = git('status', '--porcelain', cwd=path)
            if current != pr['head']['sha'] or dirty:
                if state['phase'] not in ('publishing', 'syncing'):
                    raise ValueError('Unexpected local changes; preserve and pause')
                if current != pr['head']['sha'] and current != state.get('publish_head'):
                    raise ValueError('Unrecognized local commit; coordinator must inspect history before publication')
                if dirty:
                    self.commit_fix(path, h, pr)
                    state['publish_head'] = git('rev-parse', 'HEAD', cwd=path)
                if git('merge-base', '--is-ancestor', pr['head']['sha'], 'HEAD', cwd=path) != '':
                    raise ValueError('Unexpected local ancestry')
                state['phase'] = 'publishing'
                comment_id = self.save(pr, state, comment_id)
                self.test_and_push(pr, path, state)
                self.save(pr, state, comment_id)
                return {'pr': number, 'phase': 'published-recovery'}
            # Strict base update is a normal merge, never a rebase/reset/force push.
            git('fetch', 'origin', pr['base']['ref'], h['branch'], cwd=path)
            latest = self.gh.pr(number)
            if (not eligible(latest, OWNER, REPO) or latest['state'] != 'open' or
                    latest['head']['sha'] != pr['head']['sha'] or latest['base']['ref'] != pr['base']['ref'] or
                    git('rev-parse', f'origin/{h["branch"]}', cwd=path) != pr['head']['sha']):
                self.invalidate_acceptance(state)
                raise ValueError('Remote PR changed during base fetch; preserve checkout and re-enroll')
            if (git('rev-parse', 'origin/' + pr['base']['ref'], cwd=path) != pr['base']['sha'] or
                    latest['base']['sha'] != pr['base']['sha']):
                self.invalidate_acceptance(state)
                state.update(phase='queued', next='Base moved during fetch; retry with the current branch ref')
                self.save(pr, state, comment_id)
                return {'pr': number, 'phase': 'base-race-retry'}
            check = subprocess.run(['git', 'merge-base', '--is-ancestor', pr['base']['sha'], 'HEAD'], cwd=path,
                                   env=worker_environment(), capture_output=True)
            if check.returncode not in (0, 1):
                raise ValueError('Cannot determine base ancestry; preserve checkout')
            if check.returncode:
                self.invalidate_acceptance(state)
                state['phase'] = 'syncing'
                comment_id = self.save(pr, state, comment_id)
                git('merge', '--no-edit', pr['base']['sha'], cwd=path)
                state['publish_head'] = git('rev-parse', 'HEAD', cwd=path)
                state['phase'] = 'publishing'
                self.save(pr, state, comment_id)
                self.test_and_push(pr, path, state)
                self.save(pr, state, comment_id)
                return {'pr': number, 'phase': 'base-synced'}
            if state.get('binding') == bound and state.get('review'):
                try:
                    self.validate_review(state['review'], bound, self.acceptance(pr, path))
                except ValueError:
                    self.invalidate_acceptance(state)
                    raise
            gui_needed = h['gui_required'] or bool(state.get('review', {}).get('gui_required'))
            acceptance = self.acceptance(pr, path)
            if gui_needed and acceptance.get('mode') in ('develop', 'tooling') and not acceptance.get('cases'):
                acceptance = {'allowed': False, 'reason': 'Reviewer requires GUI Cases; declare them in the matrix'}
            action = next_action(state, bound, self.gh.ci(pr), gui_needed, acceptance)
            if action == 'review' and bound.get('remote_review_complete') is False:
                state.update(phase='independent-review-wait', next='Authorized independent top-level reviewer: inspect fresh full feedback and issue a private origin-bound report; accept-review never starts a replacement worker')
                self.save(pr, state, comment_id)
                return {'pr': number, 'phase': state['phase']}
            if action == 'review':
                if state.get('reviews', 0) >= 8:
                    raise ValueError('Review limit reached; explicit resume required')
                state.pop('review', None)
                state.pop('binding', None)
                state.update(phase='reviewing', next='Independent read-only review',
                             reviews=state.get('reviews', 0) + 1)
                comment_id = self.save(pr, state, comment_id)
                running.write_text(json.dumps({'pid': None}))
                report = self.worker('review', path, {'pr': pr['number'], **bound, 'issue': issue['body'],
                                      'scope': h['scope'], 'close_issue_requested': h['close_issue'],
                                      'feedback': feedback, 'gui': state.get('gui'), 'target': pr['base']['ref'], 'acceptance': acceptance}, path.parent / 'workers',
                                      on_start=lambda pid: running.write_text(json.dumps({'pid': pid})))
                running.unlink(missing_ok=True)
                self.validate_review(report, bound, acceptance)
                if git('rev-parse', 'HEAD', cwd=path) != pr['head']['sha'] or git('status', '--porcelain', cwd=path):
                    raise ValueError('Reviewer changed checkout; approval rejected')
                state.update(binding=bound, review=report, phase='reviewed', errors=0, next='Reconcile verdict on the next tick')
            elif action == 'fix':
                state.pop('publish_head', None)
                state.update(phase='fixing', fixes=state.get('fixes', 0) + 1, next='Apply bounded review findings')
                comment_id = self.save(pr, state, comment_id)
                running.write_text(json.dumps({'pid': None}))
                self.worker('fix', path, {**bound, 'issue': issue['body'], 'scope': h['scope'],
                            'findings': state['review']['findings']}, path.parent / 'workers',
                            on_start=lambda pid: running.write_text(json.dumps({'pid': pid})))
                running.unlink(missing_ok=True)
                if git('rev-parse', 'HEAD', cwd=path) != pr['head']['sha']:
                    raise ValueError('Fixer changed git history; preserve and pause')
                # Persist recovery eligibility only after the worker returned verified settings.
                state.update(phase='publishing', next='Commit verified worker changes')
                comment_id = self.save(pr, state, comment_id)
                self.commit_fix(path, h, pr)
                state['publish_head'] = git('rev-parse', 'HEAD', cwd=path)
                state['phase'] = 'publishing'
                comment_id = self.save(pr, state, comment_id)
                self.test_and_push(pr, path, state)
            elif action == 'merge':
                latest = self.gh.pr(number)
                latest_issue = self.gh.issue(h['issue'])
                if latest_issue.get('state') != 'open' or 'pull_request' in latest_issue:
                    raise ValueError('Issue must remain open until scoped acceptance is evaluated')
                validate_issue(latest_issue)
                now, _ = self.bound(latest, latest_issue, self.gh.comments(number), state)
                if now != bound or self.gh.ci(latest) != 'success' or not self.acceptance(latest, path)['allowed']:
                    self.invalidate_acceptance(state)
                    state.update(phase='queued', next='PR/base/acceptance changed before merge; reconcile again')
                    self.save(latest, state, comment_id)
                    return {'pr': number, 'phase': 'changed-before-merge'}
                self.validate_review(state['review'], now, self.acceptance(latest, path))
                state['acceptance'] = dict(acceptance, head=bound['head'], base=bound['base'])
                self.gh.status(bound['head'], 'success', 'Independent review and target branch acceptance gate passed', pr['html_url'])
                state.update(phase='merging', next='Read back GitHub merge result',
                             merge_intent={'registry_id': h['registry_id'], 'head': bound['head'], 'base': bound['base']})
                comment_id = self.save(pr, state, comment_id)
                merge_requested = True
                self.gh.merge(latest)
                return self.continue_finish(number)
            else:
                state.update(phase=action, next={'gui-queued': '能力・許可を確認した指定GUI担当: 実対象・結果先・副作用の競合を調整し、実画面を観察して証拠を記録',
                             'acceptance-wait': acceptance.get('reason', 'Complete the required acceptance matrix'),
                             'ci-wait': 'Wait for latest required CI checks', 'ci-failed': 'Inspect CI failure; resume after repair',
                             'remote-review-wait': 'Read current-HEAD provider completion and all feedback before independent review',
                             'blocked': 'Inspect review findings or retry budget; resume explicitly'}[action])
                if action in ('blocked', 'ci-failed'):
                    state['paused'] = True
                if action == 'ci-wait' and acceptance['allowed']:
                    self.gh.status(bound['head'], 'success', 'Independent review and target branch acceptance gate passed', pr['html_url'])
            self.save(pr, state, comment_id)
            return {'pr': number, 'phase': state['phase'], 'next': state.get('next')}
        except Exception as error:
            if merge_requested:
                # A lost response or changed custody cannot authorize saving the premerge STATE.
                return {'pr': number, 'phase': 'postmerge-hold',
                        'next': 'Read back merge and current custody; use continue-finish only while still qualified'}
            state['errors'] = state.get('errors', 0) + 1
            state['next'] = self.private_error(error)
            # Preserve recoverable phases, but never publish a fix whose model execution is unverified.
            unverified_fix = state['phase'] == 'fixing'
            state['interrupted_phase'] = 'blocked' if unverified_fix or isinstance(error, WorkerSettingsError) else state['phase']
            if unverified_fix or isinstance(error, ValueError) or state['errors'] >= 3:
                state['paused'] = True
                state['phase'] = 'blocked'
            else:
                state['retry_at'] = time.time() + 300 * state['errors']
            self.save(pr, state, comment_id)
            return {'pr': number, 'phase': state['phase'], 'error': state['next']}

    def commit_fix(self, path, h, pr):
        changed = set(git('diff', '--name-only', 'HEAD', cwd=path).splitlines())
        changed.update(git('ls-files', '--others', '--exclude-standard', cwd=path).splitlines())
        if not changed or not in_scope(changed, h['scope']):
            raise ValueError('No fix or out-of-scope changes; preserve files for coordinator review')
        if git('ls-files', '-u', cwd=path):
            raise ValueError('Merge conflict requires explicit coordinator repair')
        from pr_policy import validate_title
        latest = self.gh.pr(pr['number'])
        current_issue = self.gh.issue(h['issue'])
        if binding(latest, current_issue) != binding(pr, current_issue):
            raise ValueError('PR metadata changed before fix commit; preserve changes')
        validate_title(latest, h['issue'])
        git('add', '--', *sorted(changed), cwd=path)
        git('commit', '-m', latest['title'], cwd=path)

    def finish(self, pr, h, state, comment_id):
        if h.get('host') != HOST or not self.pending_finish(pr, h, state):
            raise ValueError('Cleanup requires the current unpaused bound merge operation')
        self.source_safe(h)
        if (self.storage / f'pr-{pr["number"]}' / 'worker.json').exists():
            raise ValueError('Worker state remains; preserve postmerge resources')
        bound = state.get('binding', {})
        report = state.get('review', {})
        current_issue = self.gh.issue(h['issue'])
        if 'feedback_hash' in bound:
            current, _ = self.bound(pr, current_issue, self.gh.comments(pr['number']), state)
            if current != bound or not remote_review_ready(current):
                raise ValueError('Current post-merge feedback changed or provider review is pending; preserve resources')
            self.validate_review(report, current, state.get('acceptance'))
        accepted = state.get('acceptance', {})
        promotion_pass = (accepted.get('allowed') is True and accepted.get('mode') == 'promotion' and
                          accepted.get('gui_complete') is True and accepted.get('head') == bound.get('head') and
                          accepted.get('base') == bound.get('base'))
        complete = (pr['base']['ref'] == 'main' and h['close_issue'] and report.get('verdict') == 'approved' and report.get('scope_complete') and
                    report.get('issue_complete') and bound.get('head') == pr['head']['sha'] and
                    bound.get('issue_hash') == binding(pr, current_issue)['issue_hash'] and
                    (not (h['gui_required'] or report.get('gui_required')) or promotion_pass or gui_pass(state.get('gui'), bound)))
        transferred = False
        if pr['base']['ref'] == 'develop':
            axes = validate_issue(current_issue)
            # issue_complete means no implementation remains; GUI is tracked separately.
            implementation_complete = (axes['type'] in ('feature', 'bug', 'maintenance') and
                report.get('verdict') == 'approved' and report.get('scope_complete') is True and
                report.get('issue_complete') is True and bound.get('head') == pr['head']['sha'] and
                bound.get('issue_hash') == binding(pr, current_issue)['issue_hash'])
            if implementation_complete:
                _, case_path, _ = metadata(pr)
                git('fetch', '--no-tags', 'origin', pr['merge_commit_sha'])
                change = json.loads(git('show', f'{pr["merge_commit_sha"]}:{case_path}'))
                qa = handoff(self.gh, REPO, pr, current_issue, change)
                state['qa_issue'] = qa
                complete = transferred = True
        state.update(phase='cleanup', next='Update Issue and remove only verified finished resources')
        comment_id = self.save(pr, state, comment_id)
        issue = self.gh.issue(h['issue'])
        if complete and bound.get('issue_hash') != binding(pr, issue)['issue_hash']:
            raise ValueError('Issue acceptance changed during handoff; re-review before closure')
        if complete and 'feedback_hash' in bound:
            current, _ = self.bound(pr, issue, self.gh.comments(pr['number']), state)
            if current != bound or not remote_review_ready(current):
                raise ValueError('Feedback changed during handoff; preserve resources before closure')
            if current.get('remote_review_complete') is False:
                self.validate_review(report, current, state.get('acceptance'))
        if complete and issue['state'] == 'open':
            self.gh.api(f'repos/{REPO}/issues/{h["issue"]}', 'PATCH', {'state': 'closed', 'state_reason': 'completed', 'labels': done_labels(issue)})
            closed = self.gh.issue(h['issue'])
            if closed['state'] != 'closed' or 'status:done' not in labels(closed):
                raise ValueError('Issue closure readback failed')
        if complete and not transferred and h.get('parent') is not None:
            parent = self.gh.issue(h['parent'])
            body = update_parent(parent['body'], h['issue'])
            if body != parent['body']:
                self.gh.api(f'repos/{REPO}/issues/{h["parent"]}', 'PATCH', {'body': body})
        self.cleanup(pr, h)
        state.update(phase='done',
                     next=('Claim released; Issue closed' if complete else 'Claim released; Issue remains open for remaining acceptance') +
                          '; PM: reconcile parent roadmap and Project registration/status for origin and QA, Milestone and Relationship Status; record readback or retry owner',
                     merge_sha=pr['merge_commit_sha'])
        self.save(pr, state, comment_id)
        return {'pr': pr['number'], 'phase': 'done', 'issue_closed': bool(complete)}

    def cleanup(self, pr, h):
        branch = h['branch']
        if branch in ('main', 'master', 'develop'):
            raise ValueError('Persistent branches must never be cleaned up')
        path = self.storage / f'pr-{pr["number"]}' / 'checkout'
        if (path.parent / 'worker.json').exists():
            raise ValueError('Worker state remains; do not remove resources')
        if path.exists() and (git('rev-parse', 'HEAD', cwd=path) != pr['head']['sha'] or git('status', '--porcelain', cwd=path)):
            raise ValueError('Managed checkout changed; keep all resources')
        source = Path(h['source'])
        worktrees = git('worktree', 'list', '--porcelain')
        if source.exists():
            self.source_safe(h)
            if f'worktree {source}\n' not in worktrees or source.resolve() == ROOT.resolve():
                raise ValueError('Source is not an owned linked worktree; keep it')
        for entry in worktrees.split('\n\n'):
            if 'branch refs/heads/' + branch in entry and f'worktree {source}\n' not in entry:
                raise ValueError('Branch is active in another worktree; keep it')
        ref = 'refs/heads/' + branch
        old = command(['git', 'rev-parse', '--verify', ref], check=False)
        if old and old != h['head']:
            raise ValueError('Local branch changed; keep all resources')
        tip = git('ls-remote', f'https://github.com/{REPO}.git', ref)
        if tip:
            sha = tip.split()[0]
            if sha != pr['head']['sha']:
                raise ValueError('Remote branch was reused after merge; do not delete')
            # Compare-and-delete only. Never force-update a branch.
            if not path.exists():
                raise ValueError('No managed checkout for safe remote deletion; preserve ref')
            command(['git', 'push', f'--force-with-lease={ref}:{sha}', f'https://github.com/{REPO}.git',
                     ':' + ref], path)
        if path.exists():
            shutil.rmtree(path)
        if source.exists():
            git('worktree', 'remove', str(source))
        if old:
            git('update-ref', '-d', ref, old)


def review_original_binding(pr, issue):
    return {'kind': 'provider-original-v1', 'repository': REPO, 'pr': pr['number'], **binding(pr, issue)}


def stalled_review_retry(pr, comments, inline, reviews, comment_id, original):
    """Recognize one real Codex retry against an immutable pre-retry API observation."""
    provider = 'chatgpt-codex-connector[bot]'
    requested = lambda c: re.search(r'^\s*@codex\s+review\b', c.get('body', ''), re.M)
    retried = lambda c: re.search(r're-request|再(?:レビュー)?依頼', c.get('body', ''), re.I)
    head = pr['head']['sha']
    try:
        now = datetime.now(timezone.utc)
        def posted(c):
            timestamp = c.get('created_at') or c['submitted_at']
            at = review_time(timestamp)
            if at > now or review_time(c.get('updated_at', timestamp)) < at:
                raise ValueError('Invalid server chronology')
            return at
        requests = [c for c in comments if requested(c)]
        latest = max(requests, key=posted)
        started = posted(latest)
        same_head = lambda c: head in re.findall(r'\b[0-9a-f]{40}\b', c.get('body', ''))
        if (type(comment_id) is not int or comment_id <= 0 or latest['id'] != comment_id or
                latest['user']['login'] != OWNER or not same_head(latest) or not retried(latest) or
                not re.search(r'(?:Reason|再(?:レビュー)?依頼|re-request)\s*[:：]\s*\S', latest['body'], re.I) or
                latest.get('updated_at') != latest['created_at'] or
                sum(bool(retried(c) and same_head(c)) for c in requests) != 1 or
                any(review_time(c.get('updated_at', c['created_at'])) > started for c in requests)):
            return None
        observed = review_time(original['observed_at'])
        if observed >= started or any(not isinstance(original[k], list) for k in ('comments', 'inline', 'reviews')):
            return None
        originals, completions = [], []
        # Only the immutable pre-retry observation can establish the original obligation.
        groups = [(comments, inline + reviews, now, False),
                  (original['comments'], original['inline'] + original['reviews'], observed, True)]
        for conversation, responses, cutoff, preserved in groups:
            summaries = [c for c in conversation if c.get('user', {}).get('login') == provider and
                         c.get('body', '').startswith('<!-- codex-pull-request-review-summary -->')]
            if len(summaries) > 1:
                return None
            for c in conversation:
                if preserved and requested(c) and same_head(c) and c['user']['login'] == OWNER and not retried(c):
                    at = posted(c)
                    if c.get('updated_at') == c['created_at'] and at < min(cutoff, started):
                        originals.append(at)
            for record in conversation + responses:
                if record.get('user', {}).get('login') != provider:
                    continue
                created = posted(record)
                updated = review_time(record.get('updated_at') or record.get('created_at') or record['submitted_at'])
                if created > cutoff or updated > cutoff:
                    return None
                body = record.get('body', '')
                if record in summaries:
                    if 'created_at' not in record or 'updated_at' not in record:
                        return None
                    machine = re.search(r'<!-- codex-review-state:v1 (.+?) -->', body)
                    if not machine:
                        return None
                    state = json.loads(machine[1])
                    code = state['code_review']
                    if (not {'status', 'commit_sha', 'has_any_findings', 'has_unresolved_findings'} <= set(code) or
                            code['status'] not in ('queued', 'running', 'completed') or
                            not re.fullmatch(r'[0-9a-f]{40}', str(code['commit_sha'])) or
                            any(code[k] is not None and code[k] is not False for k in ('has_any_findings', 'has_unresolved_findings')) or
                            any(v.get('status') is not None or v.get('has_any_findings') is True or
                                v.get('has_unresolved_findings') is True for k, v in state.items() if k != 'code_review')):
                        return None
                    rows = [line for line in body.splitlines() if '**Code Review**' in line]
                    match = re.fullmatch(r'\|[^|]*\*\*Code Review\*\*[^|]*\| [^|]*\*\*(Running|Queued|Completed)\*\* '
                                         r'(?:since )?<relative-time datetime="([^"]+)">[^<]+</relative-time> '
                                         r'\| `([0-9a-f]{7,40})` \|[^|]*\|', rows[0]) if len(rows) == 1 else None
                    if not match or match[1].lower() != code['status'] or not code['commit_sha'].startswith(match[3]):
                        return None
                    at = review_time(match[2])
                    if at > updated or at > cutoff:
                        return None
                    if code.get('commit_sha') == head and head.startswith(match[3]):
                        if code['status'] == 'completed':
                            completions.append(at)
                        elif preserved and created < started and at < min(started, cutoff):
                            originals.append(at)
                    continue
                current = record.get('commit_id', record.get('original_commit_id')) == head
                if current or created >= started:
                    return None  # A concrete response/finding is not a provider timeout.
        if originals and not any(at >= max(originals) for at in completions):
            return {'comment_id': comment_id, 'created_at': latest['created_at']}
    except (KeyError, TypeError, ValueError, AttributeError, OverflowError):
        pass
    return None


def remote_review_complete(head, comments, not_before=None):
    summaries = [c for c in comments if c['user']['login'] == 'chatgpt-codex-connector[bot]' and
                 c['body'].startswith('<!-- codex-pull-request-review-summary -->')]
    if len(summaries) != 1:
        return False
    # Fail closed if the provider changes its visible summary format/status.
    rows = [line for line in summaries[0]['body'].splitlines() if '**Code Review**' in line]
    if len(rows) != 1:
        return False
    match = re.fullmatch(r'\|[^|]*\*\*Code Review\*\*[^|]*\| ✅ \*\*Completed\*\* '
                         r'<relative-time datetime="([^"]+)">[^<]+</relative-time> '
                         r'\| `([0-9a-f]{7,40})` \|[^|]*\|', rows[0])
    if not match or not head.startswith(match[2]):
        return False
    try:
        completed = datetime.fromisoformat(match[1].replace('Z', '+00:00'))
        requests = [datetime.fromisoformat(c.get('updated_at', c['created_at']).replace('Z', '+00:00'))
                    for c in comments if c['user']['login'] != 'chatgpt-codex-connector[bot]' and
                    re.search(r'^\s*@codex\s+review\b', c['body'], re.M)]
        if not_before:
            requests.append(datetime.fromisoformat(not_before.replace('Z', '+00:00')))
        if completed.tzinfo is None or any(at.tzinfo is None for at in requests):
            return False
    except (KeyError, ValueError):
        return False
    # A newer explicit request may not have reached the provider summary yet.
    return not any(at > completed for at in requests)


def enroll(loop, args):
    pr = loop.gh.pr(args.pr)
    if not eligible(pr, OWNER, REPO) or pr['state'] != 'open':
        raise ValueError('Only maintainer-owned, same-repository open Issue PRs can enroll')
    source = args.source.resolve()
    if source == ROOT.resolve() or git('branch', '--show-current', cwd=source) != pr['head']['ref']:
        raise ValueError('Handoff requires the dedicated Issue worktree')
    if git('rev-parse', 'HEAD', cwd=source) != pr['head']['sha'] or git('status', '--porcelain', cwd=source):
        raise ValueError('Source must be clean and match remote HEAD')
    issue = loop.gh.issue(issue_number(pr))
    if issue['state'] != 'open' or 'pull_request' in issue:
        raise ValueError('Associated Issue is not open')
    existing, state, state_id, _ = loop.load(args.pr, pr)
    if existing:
        raise ValueError('Already enrolled; use resume for the existing handoff')
    h = {'pr': args.pr, 'issue': issue_number(pr), 'parent': args.parent, 'target': pr['base']['ref'],
         'head': pr['head']['sha'], 'base': pr['base']['sha'], 'branch': pr['head']['ref'],
         'scope': args.scope, 'close_issue': args.close_issue,
         'gui_required': not bool(re.search(r'^GUI: not-required\s*$', pr.get('body') or '', re.M)),
         'previous_owner': args.owner, 'owner': 'agent-loop', 'writer_stopped': True}
    metadata(pr)
    if any(Path(x).is_absolute() or '..' in Path(x).parts for x in args.scope):
        raise ValueError('Use repository-relative enrollment scope')
    h = register(loop.storage, h, source, HOST)
    loop.gh.comment(args.pr, pack(HANDOFF_V2, h))
    loop.gh.status(h['head'], 'pending', 'Enrolled for independent review', pr['html_url'])
    loop.save(pr, {'phase': 'queued', 'expected_head': h['head'], 'fixes': 0, 'reviews': 0, 'errors': 0}, None)
    return h


def rebind_target(loop, args):
    pr = loop.gh.pr(args.pr)
    if not eligible(pr, OWNER, REPO) or pr['state'] != 'open' or pr.get('merged'):
        raise ValueError('Only an owned open PR can rebind')
    h, state, sid, _ = loop.load(args.pr, pr)
    if not h or h.get('host') != HOST or not eligible(pr, OWNER, REPO) or pr['state'] != 'open':
        raise ValueError('No local open managed PR to rebind')
    if h['branch'] != pr['head']['ref'] or h['issue'] != issue_number(pr):
        raise ValueError('Rebinding cannot change branch/Issue ownership')
    if (loop.storage / f'pr-{args.pr}' / 'worker.json').exists():
        raise ValueError('Worker record remains; confirm stop before rebinding')
    source = Path(h['source'])
    if args.source is not None:
        from source_relocation import relocated_source
        source = relocated_source(ROOT, h, args.source, args.migration_record, git)
    elif args.migration_record is not None or not source.exists():
        raise ValueError('Missing source requires explicit --source and migration evidence')
    loop.source_safe(dict(h, source=str(source)))
    path = loop.storage / f'pr-{args.pr}' / 'checkout'
    if path.exists() and (git('rev-parse', 'HEAD', cwd=path) != pr['head']['sha'] or git('status', '--porcelain', cwd=path)):
        raise ValueError('Managed checkout is not clean/current; preserve it')
    if state.get('expected_head', h['head']) != pr['head']['sha']:
        raise ValueError('External HEAD changed; rebind cannot adopt a new writer')
    metadata(pr)
    public = {k: v for k, v in h.items() if k not in ('source', 'host', 'registry_id', 'version')}
    public = loop.public_state(public)
    public['owner'] = 'agent-loop'
    public['target'] = pr['base']['ref']
    public = register(loop.storage, public, source, HOST)
    loop.gh.comment(args.pr, pack(HANDOFF_V2, public))
    loop.invalidate_acceptance(state)
    state.update(phase='queued', next='Target explicitly rebound; independent review and acceptance required')
    loop.save(pr, state, sid)
    loop.gh.status(pr['head']['sha'], 'pending', 'Target rebound; prior acceptance invalidated', pr['html_url'])
    return public


def audit_branches(gh, apply=False):
    """Clean completed local refs only; preserve checked-out or reused branches."""
    # Tracking-only history may be the last recoverable copy; audit must not prune it.
    prs = gh.pages(f'repos/{REPO}/pulls?state=closed&per_page=100')
    merged = {pr['head']['ref']: pr for pr in prs if pr.get('merged_at') and eligible(pr, OWNER, REPO)}
    checked = set(re.findall(r'^branch refs/heads/(.+)$', git('worktree', 'list', '--porcelain'), re.M))
    results = []
    for line in git('for-each-ref', '--format=%(refname:short) %(objectname)', 'refs/heads/').splitlines():
        branch, sha = line.split()
        if branch in ('main', 'master', 'develop'):
            continue
        pr = merged.get(branch)
        if not pr or sha != pr['head']['sha']:
            continue
        if branch in checked:
            results.append({'branch': branch, 'state': 'retained-active-worktree'})
            continue
        remote = git('ls-remote', 'origin', 'refs/heads/' + branch)
        if remote:
            results.append({'branch': branch, 'state': 'retained-remote-ref'})
            continue
        if apply:
            git('update-ref', '-d', 'refs/heads/' + branch, sha)
        results.append({'branch': branch, 'state': 'deleted' if apply else 'candidate', 'pr': pr['number']})
    return results


def main():
    global ROOT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, default=ROOT, help='Trusted main checkout (bootstrap/test override)')
    parser.add_argument('--review-retry-comment', type=int, help='Actual same-HEAD explicit retry ID; no retry is posted')
    parser.add_argument('--review-retry-original', help='Private pre-retry API observation reference')
    sub = parser.add_subparsers(dest='action', required=True)
    tick = sub.add_parser('tick'); tick.add_argument('--pr', type=int)
    sub.add_parser('scan')
    continuation = sub.add_parser('continue-finish', help='Continue an existing bound unpaused pending merge; never resume retired history')
    continuation.add_argument('--pr', type=int, required=True)
    original = sub.add_parser('prepare-review-retry', help='Preserve one real pre-retry API observation; no provider request or worker')
    original.add_argument('--pr', type=int, required=True)
    accept = sub.add_parser('accept-review', help='Read an existing reviewer-issued private origin-bound report')
    accept.add_argument('--pr', type=int, required=True)
    accept.add_argument('--evidence', type=Path, required=True)
    feedback = sub.add_parser('feedback', help='Read-only fixed feedback snapshot; never starts a worker')
    feedback.add_argument('--pr', type=int, required=True)
    audit = sub.add_parser('cleanup-branches'); audit.add_argument('--apply', action='store_true')
    en = sub.add_parser('enroll'); en.add_argument('--pr', type=int, required=True)
    en.add_argument('--source', type=Path, required=True); en.add_argument('--owner', required=True)
    en.add_argument('--scope', nargs='+', required=True); en.add_argument('--parent', type=int, help='Actual parent Issue; omit for Standalone')
    en.add_argument('--close-issue', action='store_true')
    en.add_argument('--writer-stopped', action='store_true', required=True)
    rebound = sub.add_parser('rebind-target'); rebound.add_argument('--pr', type=int, required=True)
    rebound.add_argument('--writer-stopped', action='store_true', required=True)
    rebound.add_argument('--source', type=Path, help='Explicit relocated original linked worktree')
    rebound.add_argument('--migration-record', type=Path, help='Private verified directory migration JSON')
    resume = sub.add_parser('resume'); resume.add_argument('--pr', type=int, required=True)
    resume.add_argument('--reason', required=True)
    gui = sub.add_parser('gui'); gui.add_argument('--pr', type=int, required=True); gui.add_argument('--evidence', type=Path, required=True); gui.add_argument('--operator', required=True, help='Designated operator from the actual Issue/PR assignment; must match evidence observer')
    args = parser.parse_args()
    ROOT = args.repo_root.resolve()
    try:
        with coordinator_lock():
            loop = Loop(review_retry_comment=args.review_retry_comment, review_retry_original=args.review_retry_original)
            if args.action == 'prepare-review-retry':
                result = loop.prepare_review_retry(loop.gh.pr(args.pr))
            elif args.action == 'accept-review':
                pr = loop.gh.pr(args.pr)
                if pr['state'] != 'open' or pr.get('merged'):
                    raise ValueError('Normal execution requires an open PR; terminal records are read-only')
                result = loop.accept_review(args.pr, json.loads(args.evidence.read_text()))
            elif args.action == 'feedback':
                result = loop.feedback(loop.gh.pr(args.pr))
            elif args.action == 'continue-finish':
                result = loop.continue_finish(args.pr)
            elif args.action == 'cleanup-branches':
                result = audit_branches(loop.gh, args.apply)
            elif args.action == 'rebind-target':
                result = rebind_target(loop, args)
            elif args.action == 'enroll':
                result = enroll(loop, args)
            elif args.action in ('resume', 'gui'):
                pr = loop.gh.pr(args.pr); h, state, sid, comments = loop.load(args.pr, pr)
                if not h or h['host'] != HOST:
                    raise ValueError('No local managed handoff')
                if args.action == 'gui':
                    evidence = json.loads(args.evidence.read_text())
                    if not args.operator.strip() or evidence.get('observer') != args.operator:
                        raise ValueError('GUI evidence observer must match the designated operator from the current assignment')
                    bound, _ = loop.bound(pr, loop.gh.issue(h['issue']), comments, state)
                    if not gui_pass(evidence, bound):
                        raise ValueError('GUI evidence is incomplete or belongs to another HEAD/base')
                    state['gui'] = evidence
                    state['phase'] = 'reviewed'
                else:
                    state.update(paused=False, errors=0, retry_at=0, phase=state.get('interrupted_phase', 'queued'),
                                 next=args.reason)
                    # Explicit human/coordinator decision permits another bounded attempt window.
                    state.update(reviews=0, fixes=0)
                    # A blocked verdict may depend on evidence added since the review.
                    # Keep that evidence, but require an independent review on resume.
                    if state.get('review', {}).get('verdict') == 'blocked':
                        state.pop('review', None)
                        state.pop('binding', None)
                loop.save(pr, state, sid); result = state
            else:
                prs = loop.gh.pages(f'repos/{REPO}/pulls?state=all&per_page=100')
                targets = []
                attempt_file = loop.storage / 'attempts.json'
                attempts = {}
                skipped, terminal = [], []
                for pr in prs:
                    if pr['state'] != 'open' or pr.get('merged'):
                        terminal.append(pr['number'])
                        continue
                    if eligible(pr, OWNER, REPO):
                        try:
                            h, state, _, _ = loop.load(pr['number'], pr)
                            # Keep waiting records discoverable without consuming automatic execution slots.
                            if (h and h.get('host') == HOST and state.get('phase') != 'done' and
                                    (args.action == 'scan' or args.pr is not None or
                                     (handoff_matches(pr, h) and not state.get('paused') and
                                      retry_timestamp(state) <= time.time()))):
                                targets.append(pr['number'])
                        except (ValueError, KeyError, IndexError) as error:
                            skipped.append({'pr': pr['number'], 'error': str(error)})
                            continue
                if args.action == 'scan':
                    result = {'managed_prs': targets, 'terminal_prs': terminal, 'invalid_records': skipped}
                else:
                    if targets and args.pr is None and attempt_file.exists():
                        attempts = json.loads(attempt_file.read_text())
                    selected = [args.pr] if args.pr else sorted(targets, key=lambda n: attempts.get(str(n), 0))[:3]
                    result = []
                    for n in selected:
                        try:
                            current_pr = loop.gh.pr(n)
                            result.append(loop.tick(n, current_pr))
                        except Exception as error:
                            result.append({'pr': n, 'phase': 'error', 'error': str(error)})
                    result.extend(skipped)
            print(json.dumps(result, ensure_ascii=False, indent=2))
            return 0
    except BlockingIOError:
        print('{"phase":"coordinator-busy"}'); return 0
    except Exception as error:
        print(str(error), file=sys.stderr); return 1


if __name__ == '__main__':
    sys.exit(main())
