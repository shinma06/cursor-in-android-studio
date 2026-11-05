"""Real API chronology and independent-origin admission, without network or workers."""
import copy
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import agent_loop as al
import agent_worker as aw
from agent_policy import next_action, remote_review_ready, validate_review
from handoff_registry import read_review_evidence, retain_review_evidence
from test_agent_loop import BASE, HEAD, pr_data, report
import test_agent_loop as fixtures

REVIEWER = '11111111-1111-4111-8111-111111111111'
WRITER = '22222222-2222-4222-8222-222222222222'
TURN = '33333333-3333-4333-8333-333333333333'
NOW = datetime.now(timezone.utc)

def at(minutes):
    return (NOW + timedelta(minutes=minutes)).isoformat()

def dashboard(start=-40, created=-50, updated=-33, status='Running', head=HEAD):
    code = {'status': status.lower(), 'commit_sha': head,
            'has_any_findings': None, 'has_unresolved_findings': None}
    return {'id': 10, 'user': {'login': 'chatgpt-codex-connector[bot]'},
            'created_at': at(created), 'updated_at': at(updated),
            'body': '<!-- codex-pull-request-review-summary -->\n'
                    '<!-- codex-review-state:v1 ' + json.dumps({'code_review': code, 'security_review': {'status': None}}) + ' -->\n'
                    f'| **Code Review** | ✅ **{status}** since <relative-time datetime="{at(start)}">time</relative-time> | `{head[:7]}` | ready |'}

def retry():
    return {'id': 20, 'user': {'login': al.OWNER}, 'created_at': at(-31), 'updated_at': at(-31),
            'body': f'@codex review {HEAD}\n再依頼: response stalled'}

def original(pr=None, issue=None):
    return {'binding': al.review_original_binding(pr or pr_data(), issue or {'body': 'scope'}),
            'observed_at': at(-32), 'comments': [dashboard()], 'inline': [], 'reviews': []}

class ChronologyTests(unittest.TestCase):
    def eligible(self, comments=None, observation=None, inline=None, reviews=None):
        return al.stalled_review_retry(pr_data(), comments or [dashboard(-30, updated=-30), retry()],
                                       inline or [], reviews or [], 20, observation or original())

    def test_auto_start_snapshot_survives_same_dashboard_overwrite(self):
        facts = self.eligible()
        self.assertEqual(facts, {'comment_id': 20, 'created_at': at(-31)})
        bound = {'head': HEAD, 'base': BASE, 'remote_review_complete': False,
                 'review_retry': dict(facts, head=HEAD, base=BASE, original_ref='a'*32+':'+'b'*64)}
        self.assertFalse(remote_review_ready(bound, NOW - timedelta(minutes=1, seconds=1)))
        self.assertTrue(remote_review_ready(bound, NOW - timedelta(minutes=1)))
        self.assertFalse(bound['remote_review_complete'])
        self.assertEqual(next_action({}, bound, 'success', False), 'review')
        state = {'binding': bound, 'review': report()}
        self.assertEqual(next_action(state, bound, 'pending', False), 'ci-wait')
        self.assertEqual(next_action(state, bound, 'failure', False), 'ci-failed')
        self.assertEqual(next_action(state, bound, 'success', False, {'allowed': False}), 'acceptance-wait')
        with self.assertRaises(ValueError): validate_review(report(), bound)

    def test_reject_edited_future_wrong_head_multiple_or_missing_real_retry(self):
        variants = []
        for change in ({'updated_at': at(-30)}, {'created_at': at(1), 'updated_at': at(1)},
                       {'body': '@codex review ' + 'f'*40 + '\n再依頼: stalled'},
                       {'user': {'login': 'foreign'}}, {'body': f'@codex review {HEAD}'},
                       {'created_at': 'bad'}, {'created_at': '2026-01-01T00:00:00'}):
            variants.append([dashboard(), dict(retry(), **change)])
        variants += [[dashboard()], [dashboard(), retry(), dict(retry(), id=21)],
                     [dashboard(), retry(), dict(retry(), id=21, body='@codex review '+HEAD, created_at=at(-30), updated_at=at(-30))]]
        for comments in variants:
            with self.subTest(comments=comments): self.assertIsNone(self.eligible(comments=comments))
        for value in (True, -1, '20'):
            self.assertIsNone(al.stalled_review_retry(pr_data(), [dashboard(), retry()], [], [], value, original()))

    def test_weak_late_unknown_failure_or_different_head_original_is_not_evidence(self):
        variants = []
        for field, value in [('observed_at', at(-30)), ('observed_at', 'bad'), ('comments', []), ('inline', None)]:
            o = original(); o[field] = value; variants.append(o)
        for change in ({'body': 'Running'}, {'updated_at': at(-30)},
                       {'body': dashboard(head='f'*40)['body']},
                       {'body': dashboard(status='Failed')['body']}):
            o = original(); o['comments'] = [dict(dashboard(), **change)]; variants.append(o)
        for o in variants:
            with self.subTest(o=o): self.assertIsNone(self.eligible(observation=o))
        weak = dashboard(); weak['body'] = '\n'.join(x for x in weak['body'].splitlines() if 'codex-review-state' not in x)
        o = original(); o['comments'] = [weak]; self.assertIsNone(self.eligible(observation=o))

    def test_completed_original_and_current_findings_cannot_be_overridden(self):
        complete = dashboard(start=-35, updated=-33, status='Completed')
        o = original(); o['comments'] = [dashboard(), complete]
        self.assertIsNone(self.eligible(observation=o))
        finding = {'id': 21, 'user': {'login': 'chatgpt-codex-connector[bot]'},
                   'created_at': at(-20), 'commit_id': HEAD, 'body': 'concrete defect'}
        self.assertIsNone(self.eligible(inline=[finding]))
        bad = dashboard(-30, updated=-30); bad['body'] = bad['body'].replace('"has_any_findings": null', '"has_any_findings": true')
        self.assertIsNone(self.eligible(comments=[bad, retry()]))

    def test_older_completion_does_not_satisfy_a_later_real_original(self):
        o = original(); o['comments'] = [dashboard(start=-45, updated=-44, status='Completed')]
        o['comments'].append({'id': 11, 'user': {'login': al.OWNER}, 'body': '@codex review '+HEAD,
                              'created_at': at(-40), 'updated_at': at(-40)})
        self.assertIsNotNone(self.eligible(observation=o))
        o['comments'][0] = dashboard(start=-39, updated=-38, status='Completed')
        self.assertIsNone(self.eligible(observation=o))

class OriginAndConsumerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name); self.home = self.root/'codex'; self.storage = self.root/'storage'
        self.runtime(REVIEWER); self.runtime(WRITER)
        self.env = self.enterContext(patch.dict(os.environ, {'CODEX_THREAD_ID': REVIEWER}))
        self.bound = {'head': HEAD, 'base': BASE, 'target': 'main', 'title_hash': '1'*64,
                      'body_hash': '2'*64, 'issue_hash': '3'*64, 'feedback_hash': '4'*64,
                      'remote_review_complete': False,
                      'review_retry': {'head': HEAD, 'base': BASE, 'comment_id': 20,
                                       'created_at': at(-31), 'original_ref': 'a'*32+':'+'b'*64}}

    def runtime(self, session, **metadata):
        p = self.home/'sessions/2026/11/05'/f'rollout-test-{session}.jsonl'; p.parent.mkdir(parents=True, exist_ok=True)
        rows = [{'type': 'session_meta', 'payload': dict(id=session, session_id=session, source='vscode', **metadata)},
                {'type': 'turn_context', 'timestamp': at(-0.5),
                 'payload': {'turn_id': TURN, 'model': 'gpt-6.1-sol', 'effort': 'high'}}]
        p.write_text('\n'.join(json.dumps(r) for r in rows)+'\n'); return p

    def approve(self, bound=None):
        return aw.bind_independent_review(self.storage, bound or self.bound,
            dict(report(), session=REVIEWER, model='gpt-6.1-sol', review_binding=bound or self.bound, feedback_hash=(bound or self.bound)['feedback_hash']),
            TURN, [WRITER], self.home)

    def test_actual_origin_full_binding_and_private_evidence_are_required(self):
        r = self.approve()
        self.assertTrue(aw.verify_independent_review(self.storage, r, self.bound, self.home))
        validate_review(r, self.bound, origin_verified=True)
        for key in ['head', 'base', 'target', 'body_hash', 'title_hash', 'issue_hash', 'feedback_hash']:
            changed = dict(self.bound, **{key: 'different'})
            with self.subTest(key=key), self.assertRaises(ValueError):
                aw.verify_independent_review(self.storage, r, changed, self.home)
        with self.assertRaises(ValueError): aw.verify_independent_review(self.storage, dict(r, scope_complete=False), self.bound, self.home)
        with self.assertRaises(ValueError): validate_review(r, self.bound)
        proof = read_review_evidence(self.storage, r['origin_receipt']); self.assertEqual(proof['contributors'], [WRITER])
        path = self.storage/'review-evidence'/(r['origin_receipt'].split(':')[0]+'.json'); path.write_text('{}')
        with self.assertRaises(ValueError): aw.verify_independent_review(self.storage, r, self.bound, self.home)

    def test_import_requires_authored_full_binding_and_approval(self):
        r = dict(report(), session=REVIEWER, model='gpt-6.1-sol', review_binding=self.bound, feedback_hash=self.bound['feedback_hash'])
        for change in ({'review_binding': None}, {'feedback_hash': 'unrelated'},
                       {'verdict': 'changes_requested', 'findings': ['defect']}, {'verdict': 'blocked'}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                aw.bind_independent_review(self.storage, self.bound, dict(r, **change), TURN, [WRITER], self.home)

    def test_self_fork_missing_actual_origin_wrong_settings_and_future_turn_rejected(self):
        r = dict(report(), session=REVIEWER, model='gpt-6.1-sol', review_binding=self.bound, feedback_hash=self.bound['feedback_hash'])
        for who in ([], [REVIEWER], ['unknown'], [WRITER, WRITER]):
            with self.subTest(who=who), self.assertRaises(ValueError):
                aw.bind_independent_review(self.storage, self.bound, r, TURN, who, self.home)
        self.runtime(REVIEWER, forked_from_id=WRITER)
        with self.assertRaises(ValueError): self.approve()
        p = self.runtime(REVIEWER); p.write_text(p.read_text().replace('"high"', '"low"'))
        with self.assertRaises(ValueError): self.approve()
        p = self.runtime(REVIEWER); p.write_text(p.read_text().replace(at(-0.5), at(1)))
        with self.assertRaises(ValueError): self.approve()
        self.runtime(REVIEWER)
        with patch.dict(os.environ, {'CODEX_THREAD_ID': WRITER}), self.assertRaises(ValueError): self.approve()

    def test_actual_turn_before_deadline_and_private_report_cannot_be_imported(self):
        p = self.runtime(REVIEWER); p.write_text(p.read_text().replace(at(-0.5), at(-2)))
        with self.assertRaises(ValueError): self.approve()
        self.runtime(REVIEWER)
        r = dict(report(), session=REVIEWER, model='gpt-6.1-sol', review_binding=self.bound, feedback_hash=self.bound['feedback_hash'],
                 evidence=str(Path.home()/'private-evidence.log'))
        with self.assertRaises(ValueError):
            aw.bind_independent_review(self.storage,self.bound,r,TURN,[WRITER],self.home)
        with patch.object(aw.subprocess,'Popen') as launch, self.assertRaises(aw.WorkerSettingsError):
            aw.run_worker('review',self.root,self.bound,self.root/'unused')
        launch.assert_not_called()

    def test_observation_is_actual_api_and_racing_metadata_is_not_retained(self):
        gh = Mock(); gh.pr.return_value = pr_data(); gh.issue.return_value = {'body':'scope'}
        gh.comments.return_value = [dashboard()]; gh.pages.return_value = []
        loop = al.Loop(gh,self.storage)
        ref = loop.prepare_review_retry(pr_data())['review_retry_original']
        value = read_review_evidence(self.storage,ref)
        self.assertEqual(value['comments'],[dashboard()]); self.assertEqual(value['binding'],original()['binding'])
        gh.comment.assert_not_called(); gh.merge.assert_not_called(); gh.status.assert_not_called()
        gh.pr.return_value = dict(pr_data(),title='changed')
        before = list((self.storage/'review-evidence').iterdir())
        with self.assertRaises(ValueError): loop.prepare_review_retry(pr_data())
        self.assertEqual(before,list((self.storage/'review-evidence').iterdir()))

    def fixture(self):
        fixture = fixtures.LoopTests('runTest'); fixture.setUp(); self.addCleanup(fixture.doCleanups)
        self.enterContext(patch.object(al, 'remote_review_complete', return_value=False))
        fixture.gh.pull['draft'] = False
        h, state, _, _ = fixture.loop.load(36)
        public = al.register(fixture.loop.storage, dict({k:v for k,v in h.items() if k not in ['source','host']}, target='main', owner='fixture-writer'), h['source'], al.HOST)
        fixture.gh.messages[36] = [{'id': 1, 'user': {'login': al.OWNER}, 'body': al.pack(al.HANDOFF_V2, public)},
                                   dashboard(-30, updated=-30), retry()]
        obs = original(fixture.gh.pull, fixture.gh.issue(35))
        fixture.loop.review_retry_comment = 20
        fixture.loop.review_retry_original = retain_review_evidence(fixture.loop.storage, obs)
        self.enterContext(patch.object(al, 'verify_independent_review', side_effect=lambda *a: aw.verify_independent_review(*a, codex_home=self.home)))
        return fixture

    def test_advance_needs_explicit_independent_report_then_normal_ci_and_finish(self):
        f = self.fixture()
        self.assertEqual(f.loop.tick(36)['phase'], 'independent-review-wait')
        f.worker.assert_not_called(); self.assertEqual(f.gh.merges, 0)
        bound, _ = f.loop.bound(f.gh.pr(36), f.gh.issue(35), f.gh.comments(36))
        r = aw.bind_independent_review(f.loop.storage, bound,
              dict(report(), session=REVIEWER, model='gpt-6.1-sol', review_binding=bound, feedback_hash=bound['feedback_hash']), TURN, [WRITER], self.home)
        f.loop.accept_review(36, r)
        self.assertEqual(f.loop.tick(36)['phase'], 'done'); self.assertEqual(f.gh.merges, 1)
        self.assertEqual(f.gh.issues[35]['state'], 'closed'); f.loop.cleanup.assert_called_once()

    def test_finish_refuses_late_feedback_and_accept_never_resumes_paused_owner(self):
        f = self.fixture(); h,state,cid,_ = f.loop.load(36)
        state['paused'] = True; f.loop.save(f.gh.pr(36),state,cid)
        with self.assertRaises(ValueError): f.loop.accept_review(36,{})
        h,state,cid,comments = f.loop.load(36); self.assertTrue(state['paused']); state['paused'] = False
        bound,_ = f.loop.bound(f.gh.pr(36),f.gh.issue(35),comments)
        r = aw.bind_independent_review(f.loop.storage,bound,dict(report(),session=REVIEWER,model='gpt-6.1-sol',review_binding=bound,feedback_hash=bound['feedback_hash']),TURN,[WRITER],self.home)
        state.update(binding=bound,review=r,acceptance={'allowed':True,'mode':'tooling','cases':0}); f.gh.pull.update(merged=True,state='closed')
        f.gh.comment(36,'new substantive feedback')
        with self.assertRaises(ValueError): f.loop.finish(f.gh.pr(36),h,state,cid)
        f.loop.cleanup.assert_not_called(); self.assertEqual(f.gh.issues[35]['state'],'open')

    def test_snapshot_metadata_mismatch_and_forged_postmerge_origin_block(self):
        f = self.fixture(); p=f.gh.pr(36); issue=f.gh.issue(35)
        for key in ['base','target','body','issue']:
            q=copy.deepcopy(p); i=copy.deepcopy(issue)
            if key=='base': q['base']['sha']='f'*40
            elif key=='target': q['base']['ref']='develop'
            elif key=='body': q['body']+='\nchanged'
            else: i['body']+='\nchanged'
            with self.subTest(key=key), self.assertRaises(ValueError): f.loop.bound(q,i,f.gh.comments(36))
        h,state,cid,comments=f.loop.load(36); bound,_=f.loop.bound(p,issue,comments)
        r=aw.bind_independent_review(f.loop.storage,bound,dict(report(),session=REVIEWER,model='gpt-6.1-sol',review_binding=bound,feedback_hash=bound['feedback_hash']),TURN,[WRITER],self.home)
        state.update(binding=bound,review=dict(r,evidence='unbound claim'),acceptance={'allowed':True,'mode':'tooling'})
        f.gh.pull.update(merged=True,state='closed')
        with self.assertRaises(ValueError): f.loop.finish(f.gh.pr(36),h,state,cid)
        f.loop.cleanup.assert_not_called()

if __name__ == '__main__':
    unittest.main()
