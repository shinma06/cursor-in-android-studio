#!/usr/bin/env python3
"""One bounded PR transition per tick. Run from trusted main, never from PR code."""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
from urllib.parse import quote

from agent_policy import CONTEXT, binding, eligible, gui_pass, in_scope, issue_number, next_action, update_parent, validate_review
from agent_worker import run_worker, worker_environment

REPO = 'shinma06/cursor-in-android-studio'
OWNER = 'shinma06'
ROOT = Path(__file__).resolve().parents[2]
HANDOFF = '<!-- agent-loop-handoff:v1 -->'
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

    def pr(self, number):
        return self.api(f'repos/{REPO}/pulls/{number}')

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
        needed = [states.get(x) for x in ('test', 'PR policy')]
        if any(x in ('FAILURE', 'ERROR', 'CANCELLED', 'TIMED_OUT', 'ACTION_REQUIRED') for x in needed):
            return 'failure'
        return 'success' if all(x == 'SUCCESS' for x in needed) else 'pending'

    def merge(self, pr):
        # Both the head guard and active strict branch rules are enforced by GitHub.
        if pr['draft']:
            command(['gh', 'pr', 'ready', str(pr['number']), '--repo', REPO])
        result = self.api(f'repos/{REPO}/pulls/{pr["number"]}/merge', 'PUT',
                          {'sha': pr['head']['sha'], 'merge_method': 'squash'})
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


@contextmanager
def coordinator_lock():
    # One active coordinator per OS user, including separate clones of this repo.
    path = Path('/tmp') / f'cursor-in-android-studio-coordinator-{os.getuid()}'
    fd = os.open(path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


class Loop:
    def __init__(self, gh=None, storage=None, worker=run_worker):
        self.gh = gh or GitHub()
        # Stable across linked worktrees, persists through app restarts. Not checked in.
        common = Path(git('rev-parse', '--git-common-dir'))
        if not common.is_absolute():
            common = ROOT / common
        self.storage = Path(storage) if storage else common.resolve() / 'agent-loop'
        self.storage.mkdir(parents=True, exist_ok=True)
        self.worker = worker

    def load(self, number):
        comments = self.gh.comments(number)
        handoff, _ = unpack(comments, HANDOFF)
        state, comment_id = unpack(comments, STATE)
        return handoff, state or {'phase': 'queued', 'fixes': 0, 'reviews': 0, 'errors': 0}, comment_id, comments

    def save(self, pr, state, comment_id):
        state['updated_at'] = int(time.time())
        body = pack(STATE, state)
        result = self.gh.comment(pr['number'], body, comment_id)
        # A dedicated Issue comment is the recoverable dashboard; don't overwrite Issue criteria.
        issue = issue_number(pr)
        marker = f'<!-- agent-loop-progress:pr-{pr["number"]} -->'
        comments = self.gh.comments(issue)
        prior = next((c for c in reversed(comments) if c['user']['login'] == OWNER and c['body'].startswith(marker)), None)
        progress = (marker + f'\nPR #{pr["number"]}: **{state["phase"]}**\n'
                    f'HEAD: {pr["head"]["sha"]}\nOwner: agent-loop@{HOST}\n'
                    f'Next: {state.get("next", state["phase"])}\nDetails: {result["html_url"]}')
        if not prior or prior['body'] != progress:
            self.gh.comment(issue, progress, prior['id'] if prior else None)
        return result['id']

    def bound(self, pr, issue, comments):
        value = binding(pr, issue)
        # Fresh human feedback triggers a new review; machine dashboard edits do not.
        human = [c['body'] for c in comments if c['user']['login'] == OWNER and
                 not c['body'].startswith((HANDOFF, STATE))]
        inline = self.gh.pages(f'repos/{REPO}/pulls/{pr["number"]}/comments?per_page=100')
        reviews = self.gh.pages(f'repos/{REPO}/pulls/{pr["number"]}/reviews?per_page=100')
        value['feedback_hash'] = hashlib.sha256(json.dumps([human, inline, reviews], sort_keys=True).encode()).hexdigest()
        return value, human + [x.get('body', '') for x in inline + reviews]

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
            git('fetch', 'origin', h['branch'], 'main', cwd=path)
            git('checkout', '-b', h['branch'], pr['head']['sha'], cwd=path)
            # Do not run hook implementations supplied by the PR; tests are run by coordinator.
            git('config', 'core.hooksPath', str(ROOT / '.githooks'), cwd=path)
        return path

    def test_and_push(self, pr, path, state):
        expected = pr['head']['sha']
        actual = git('rev-parse', 'HEAD', cwd=path)
        remote = self.gh.pr(pr['number'])
        if remote['head']['sha'] != expected:
            raise ValueError('Remote HEAD changed; do not publish recovered work')
        # These repositories are maintainer-owned, enrolled work only. No forks are executed.
        command(['python3', '-m', 'unittest', 'discover', '-s', 'scripts/workflow', '-p', 'test_*.py'], path, 180)
        command(['python3', '-m', 'unittest', 'discover', '-s', 'scripts/loop', '-p', 'test_*.py'], path, 180)
        command(['./gradlew', 'test', '--console=plain'], path, 600)
        if git('status', '--porcelain', cwd=path):
            raise ValueError('Tests left dirty files; cannot publish')
        git('push', 'origin', f'HEAD:refs/heads/{pr["head"]["ref"]}', cwd=path)
        state['expected_head'] = actual
        state.pop('review', None)
        state.pop('gui', None)
        state['phase'] = 'queued'
        state['next'] = 'Review the newly published HEAD'

    def tick(self, number):
        pr = self.gh.pr(number)
        if not eligible(pr, OWNER, REPO):
            return {'pr': number, 'phase': 'unmanaged'}
        h, state, comment_id, comments = self.load(number)
        if (not h or h.get('host') != HOST or h.get('pr') != number or h.get('issue') != issue_number(pr)
                or h.get('branch') != pr['head']['ref'] or h.get('writer_stopped') is not True):
            return {'pr': number, 'phase': 'unmanaged'}
        if not state.get('expected_head'):
            state['expected_head'] = h['head']
        if pr.get('merged'):
            if state.get('phase') == 'done':
                return {'pr': number, 'phase': 'done'}
            try:
                return self.finish(pr, h, state, comment_id)
            except Exception as error:
                state.update(phase='cleanup', next=str(error)[:1800])
                self.save(pr, state, comment_id)
                return {'pr': number, 'phase': 'cleanup', 'error': state['next']}
        if pr['state'] != 'open' or state.get('paused'):
            return {'pr': number, 'phase': state['phase']}
        if state.get('retry_at', 0) > time.time():
            return {'pr': number, 'phase': 'backoff'}
        try:
            issue = self.gh.issue(h['issue'])
            if issue['state'] != 'open' or 'pull_request' in issue:
                raise ValueError('Issue must remain open until scoped acceptance is evaluated')
            self.source_safe(h)
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
                    state.pop('review', None)
                else:
                    raise ValueError('External push after handoff; explicit re-enrollment required')
            bound, feedback = self.bound(pr, issue, comments)
            self.gh.status(pr['head']['sha'], 'pending', 'Review/acceptance reconciliation in progress', pr['html_url'])
            current = git('rev-parse', 'HEAD', cwd=path)
            dirty = git('status', '--porcelain', cwd=path)
            if current != pr['head']['sha'] or dirty:
                if state['phase'] not in ('fixing', 'publishing', 'syncing'):
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
            git('fetch', 'origin', 'main', h['branch'], cwd=path)
            if git('rev-parse', 'origin/main', cwd=path) != pr['base']['sha']:
                return {'pr': number, 'phase': 'base-race-retry'}
            check = subprocess.run(['git', 'merge-base', '--is-ancestor', pr['base']['sha'], 'HEAD'], cwd=path,
                                   env=worker_environment(), capture_output=True)
            if check.returncode:
                state['phase'] = 'syncing'
                comment_id = self.save(pr, state, comment_id)
                git('merge', '--no-edit', 'origin/main', cwd=path)
                state['publish_head'] = git('rev-parse', 'HEAD', cwd=path)
                state['phase'] = 'publishing'
                self.save(pr, state, comment_id)
                self.test_and_push(pr, path, state)
                self.save(pr, state, comment_id)
                return {'pr': number, 'phase': 'base-synced'}
            if state.get('binding') == bound and state.get('review'):
                validate_review(state['review'], bound)
            gui_needed = h['gui_required'] or bool(state.get('review', {}).get('gui_required'))
            action = next_action(state, bound, self.gh.ci(pr), gui_needed)
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
                                      'feedback': feedback, 'gui': state.get('gui')}, path.parent / 'workers',
                                      on_start=lambda pid: running.write_text(json.dumps({'pid': pid})))
                running.unlink(missing_ok=True)
                validate_review(report, bound)
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
                self.commit_fix(path, h, pr)
                state['publish_head'] = git('rev-parse', 'HEAD', cwd=path)
                state['phase'] = 'publishing'
                comment_id = self.save(pr, state, comment_id)
                self.test_and_push(pr, path, state)
            elif action == 'merge':
                latest = self.gh.pr(number)
                now, _ = self.bound(latest, self.gh.issue(h['issue']), self.gh.comments(number))
                if now != bound or self.gh.ci(latest) != 'success':
                    return {'pr': number, 'phase': 'changed-before-merge'}
                self.gh.status(bound['head'], 'success', 'Independent review and scoped acceptance passed', pr['html_url'])
                state.update(phase='merging', next='Read back GitHub merge result')
                comment_id = self.save(pr, state, comment_id)
                self.gh.merge(latest)
                return self.finish(self.gh.pr(number), h, state, comment_id)
            else:
                state.update(phase=action, next={'gui-queued': 'Designated GPT operator: acquire GUI lease, observe and record evidence',
                             'ci-wait': 'Wait for latest required CI checks', 'ci-failed': 'Inspect CI failure; resume after repair',
                             'blocked': 'Inspect review findings or retry budget; resume explicitly'}[action])
                if action in ('blocked', 'ci-failed'):
                    state['paused'] = True
                if action == 'ci-wait' and (not gui_needed or gui_pass(state.get('gui'), bound)):
                    self.gh.status(bound['head'], 'success', 'Independent review and scoped acceptance passed', pr['html_url'])
            self.save(pr, state, comment_id)
            return {'pr': number, 'phase': state['phase'], 'next': state.get('next')}
        except Exception as error:
            state['errors'] = state.get('errors', 0) + 1
            state['next'] = str(error)[:1800]
            # Preserve fixing/publishing phase so an interrupted worker can be recovered without discarding files.
            state['interrupted_phase'] = state['phase']
            if isinstance(error, ValueError) or state['errors'] >= 3:
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
        git('add', '--', *sorted(changed), cwd=path)
        git('commit', '-m', f'Address automated review for #{h["issue"]}', cwd=path)

    def finish(self, pr, h, state, comment_id):
        if not pr.get('merged'):
            raise ValueError('Cleanup requires confirmed GitHub merge')
        bound = state.get('binding', {})
        report = state.get('review', {})
        current_issue = self.gh.issue(h['issue'])
        complete = (h['close_issue'] and report.get('verdict') == 'approved' and report.get('scope_complete') and
                    report.get('issue_complete') and bound.get('head') == pr['head']['sha'] and
                    bound.get('issue_hash') == binding(pr, current_issue)['issue_hash'] and
                    (not (h['gui_required'] or report.get('gui_required')) or gui_pass(state.get('gui'), bound)))
        state.update(phase='cleanup', next='Update Issue and remove only verified finished resources')
        comment_id = self.save(pr, state, comment_id)
        issue = self.gh.issue(h['issue'])
        if complete and issue['state'] == 'open':
            self.gh.api(f'repos/{REPO}/issues/{h["issue"]}', 'PATCH', {'state': 'closed', 'state_reason': 'completed'})
        if complete:
            parent = self.gh.issue(h['parent'])
            body = update_parent(parent['body'], h['issue'])
            if body != parent['body']:
                self.gh.api(f'repos/{REPO}/issues/{h["parent"]}', 'PATCH', {'body': body})
        self.cleanup(pr, h)
        state.update(phase='done', next='Claim released; Issue closed' if complete else 'Claim released; Issue remains open for remaining acceptance',
                     merge_sha=pr['merge_commit_sha'])
        self.save(pr, state, comment_id)
        return {'pr': pr['number'], 'phase': 'done', 'issue_closed': bool(complete)}

    def cleanup(self, pr, h):
        from gui_lease import locked, transition
        lease_dir = Path('/tmp') / f'cursor-in-android-studio-gui-{os.getuid()}'
        with locked(lease_dir) as lease:
            if transition(lease, 'status').get('state') != 'free':
                raise ValueError('GUI is occupied; defer resource cleanup until operator release')
            return self._cleanup_resources(pr, h)

    def _cleanup_resources(self, pr, h):
        branch = h['branch']
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
    existing, state, state_id, _ = loop.load(args.pr)
    if existing:
        raise ValueError('Already enrolled; use resume for the existing handoff')
    h = {'pr': args.pr, 'issue': issue_number(pr), 'parent': args.parent, 'host': HOST,
         'head': pr['head']['sha'], 'base': pr['base']['sha'], 'branch': pr['head']['ref'],
         'source': str(source), 'scope': args.scope, 'close_issue': args.close_issue,
         'gui_required': not bool(re.search(r'^GUI: not-required\s*$', pr.get('body') or '', re.M)),
         'previous_owner': args.owner, 'owner': 'agent-loop@' + HOST, 'writer_stopped': True}
    loop.gh.comment(args.pr, pack(HANDOFF, h))
    loop.gh.status(h['head'], 'pending', 'Enrolled for independent review', pr['html_url'])
    loop.save(pr, {'phase': 'queued', 'expected_head': h['head'], 'fixes': 0, 'reviews': 0, 'errors': 0}, None)
    return h


def audit_branches(gh, apply=False):
    """Clean completed local refs only; preserve checked-out or reused branches."""
    git('fetch', '--prune', 'origin')
    prs = gh.pages(f'repos/{REPO}/pulls?state=closed&per_page=100')
    merged = {pr['head']['ref']: pr for pr in prs if pr.get('merged_at') and eligible(pr, OWNER, REPO)}
    checked = set(re.findall(r'^branch refs/heads/(.+)$', git('worktree', 'list', '--porcelain'), re.M))
    results = []
    for line in git('for-each-ref', '--format=%(refname:short) %(objectname)', 'refs/heads/').splitlines():
        branch, sha = line.split()
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
    sub = parser.add_subparsers(dest='action', required=True)
    tick = sub.add_parser('tick'); tick.add_argument('--pr', type=int)
    sub.add_parser('scan')
    audit = sub.add_parser('cleanup-branches'); audit.add_argument('--apply', action='store_true')
    en = sub.add_parser('enroll'); en.add_argument('--pr', type=int, required=True)
    en.add_argument('--source', type=Path, required=True); en.add_argument('--owner', required=True)
    en.add_argument('--scope', nargs='+', required=True); en.add_argument('--parent', type=int, default=1)
    en.add_argument('--close-issue', action='store_true')
    en.add_argument('--writer-stopped', action='store_true', required=True)
    resume = sub.add_parser('resume'); resume.add_argument('--pr', type=int, required=True)
    resume.add_argument('--reason', required=True)
    gui = sub.add_parser('gui'); gui.add_argument('--pr', type=int, required=True); gui.add_argument('--evidence', type=Path, required=True); gui.add_argument('--lease-token', required=True)
    args = parser.parse_args()
    ROOT = args.repo_root.resolve()
    try:
        with coordinator_lock():
            loop = Loop()
            if args.action == 'cleanup-branches':
                result = audit_branches(loop.gh, args.apply)
            elif args.action == 'enroll':
                result = enroll(loop, args)
            elif args.action in ('resume', 'gui'):
                pr = loop.gh.pr(args.pr); h, state, sid, comments = loop.load(args.pr)
                if not h or h['host'] != HOST:
                    raise ValueError('No local managed handoff')
                if args.action == 'gui':
                    evidence = json.loads(args.evidence.read_text())
                    from gui_lease import locked, transition
                    with locked(Path('/tmp') / f'cursor-in-android-studio-gui-{os.getuid()}') as lease:
                        current = transition(lease, 'status')
                        if (current.get('token') != args.lease_token or current.get('expired') or
                                current.get('issue') != h['issue'] or current.get('head') != pr['head']['sha'] or
                                current.get('owner') != evidence.get('observer')):
                            raise ValueError('GUI evidence requires the current designated operator lease')
                    bound, _ = loop.bound(pr, loop.gh.issue(h['issue']), comments)
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
                attempts = json.loads(attempt_file.read_text()) if attempt_file.exists() else {}
                skipped = []
                for pr in prs:
                    if eligible(pr, OWNER, REPO):
                        try:
                            h, state, _, _ = loop.load(pr['number'])
                        except (ValueError, KeyError, IndexError) as error:
                            skipped.append({'pr': pr['number'], 'error': str(error)})
                            continue
                        if h and h.get('host') == HOST and state.get('phase') != 'done':
                            targets.append(pr['number'])
                if args.action == 'scan':
                    result = {'managed_prs': targets, 'invalid_records': skipped}
                else:
                    selected = [args.pr] if args.pr else sorted(targets, key=lambda n: attempts.get(str(n), 0))[:3]
                    result = []
                    for n in selected:
                        attempts[str(n)] = time.time()
                        temporary = attempt_file.with_suffix('.tmp')
                        temporary.write_text(json.dumps(attempts)); os.replace(temporary, attempt_file)
                        try:
                            result.append(loop.tick(n))
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
