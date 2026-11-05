"""Terminal history cannot acquire an execution source; current merge custody can finish."""
import copy
from contextlib import nullcontext, redirect_stdout, redirect_stderr
import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch

import agent_loop as al
import test_agent_loop as fixtures

LEGACY = '<!-- agent-loop-handoff:v1 -->'


class TerminalExecutionTests(unittest.TestCase):
    def setUp(self):
        self.f = fixtures.LoopTests('runTest'); self.f.setUp()
        self.addCleanup(self.f.doCleanups)

    def files(self):
        return {str(p): p.read_bytes() for p in self.f.loop.storage.rglob('*') if p.is_file()}

    def command(self, *args):
        output, error = io.StringIO(), io.StringIO()
        with patch.object(al, 'Loop', return_value=self.f.loop), \
                patch.object(al, 'coordinator_lock', return_value=nullcontext()), \
                patch.object(sys, 'argv', ['agent_loop.py', *args]), \
                redirect_stdout(output), redirect_stderr(error):
            result = al.main()
        return result, output.getvalue(), error.getvalue()

    def test_closed_and_merged_requests_refuse_before_records_or_execution(self):
        f = self.f
        for merged in (False, True):
            f.gh.pull.update(state='closed', merged=merged)
            f.gh.messages[36] = [{'id': 1, 'user': {'login': al.OWNER},
                                  'body': LEGACY + '\nmalformed private history'}]
            before = self.files(); messages = copy.deepcopy(f.gh.messages)
            with patch.object(al, 'resolve', side_effect=AssertionError('must not resolve')) as resolve:
                self.assertEqual(f.loop.tick(36)['phase'], 'terminal-nonexecution')
                with self.assertRaises(ValueError): f.loop.load(36)
                self.assertEqual(self.command('tick', '--pr', '36')[0], 0)
                for command in [('resume', '--pr', '36', '--reason', 'not authority'),
                                ('gui', '--pr', '36', '--evidence', '/absent', '--lease-token', 'x'),
                                ('accept-review', '--pr', '36', '--evidence', '/absent'),
                                ('rebind-target', '--pr', '36', '--writer-stopped'),
                                ('prepare-review-retry', '--pr', '36'),
                                ('enroll', '--pr', '36', '--source', str(f.checkout),
                                 '--owner', 'fixture', '--scope', 'src/', '--writer-stopped')]:
                    with self.subTest(merged=merged, command=command):
                        self.assertEqual(self.command(*command)[0], 1)
                resolve.assert_not_called()
            self.assertEqual(before, self.files()); self.assertEqual(messages, f.gh.messages)
        f.worker.assert_not_called(); f.loop.source_safe.assert_not_called()
        self.assertEqual(f.gh.merges, 0); self.assertEqual(f.gh.statuses, [])

    def test_terminal_request_does_not_create_runtime_storage(self):
        f = self.f; f.gh.pull.update(state='closed', merged=True)
        storage = f.loop.storage/'absent-runtime'
        loop = al.Loop(f.gh, storage, f.worker)
        with patch.object(al, 'resolve') as resolve:
            self.assertEqual(loop.tick(36)['phase'], 'terminal-nonexecution')
            with self.assertRaises(ValueError): loop.load(36)
            loop.feedback(f.gh.pr(36))
            resolve.assert_not_called()
        self.assertFalse(storage.exists()); f.worker.assert_not_called()

    def test_feedback_and_scan_read_history_without_invalid_registry_or_state(self):
        f = self.f; f.gh.pull.update(state='closed', merged=True)
        f.gh.messages[36] += [{'id': 10, 'user': {'login': al.OWNER}, 'body': al.STATE + '\ninvalid'},
                              {'id': 11, 'user': {'login': al.OWNER}, 'body': al.HANDOFF_V2 + '\ninvalid'}]
        (f.loop.storage/'attempts.json').write_text('invalid historical attempts')
        before = self.files()
        with patch.object(al, 'resolve', side_effect=AssertionError('must not resolve')) as resolve, \
                patch.object(f.gh, 'pages', return_value=[f.gh.pr(36)]):
            self.assertEqual(self.command('feedback', '--pr', '36')[0], 0)
            code, output, _ = self.command('scan')
            self.assertEqual(code, 0)
            self.assertEqual(json.loads(output), {'managed_prs': [], 'terminal_prs': [36], 'invalid_records': []})
            resolve.assert_not_called()
        self.assertEqual(before, self.files()); f.worker.assert_not_called()

    def test_paused_prs_remain_visible_without_starving_automatic_tick(self):
        f = self.f; prs = []
        for number in range(36, 40):
            pr = copy.deepcopy(f.gh.pull); pr['number'] = number
            pr['head']['ref'] = f'codex/{number-1}-fixture'
            pr['body'] = pr['body'].replace('Issue: #35', f'Issue: #{number-1}')
            prs.append(pr)
        states = {number: {'phase': 'blocked', 'paused': number < 39} for number in range(36, 40)}
        attempts = f.loop.storage/'attempts.json'
        attempts.write_text(json.dumps({str(n): n for n in range(36, 40)}))
        before = self.files(); messages = copy.deepcopy(f.gh.messages)
        with patch.object(f.gh, 'pages', return_value=prs), \
                patch.object(f.gh, 'pr', side_effect=lambda n: copy.deepcopy(prs[n-36])), \
                patch.object(f.loop, 'load', side_effect=lambda n, pr: (
                    {'host': al.HOST}, copy.deepcopy(states[n]), None, [])), \
                patch.object(f.loop, 'tick', return_value={'phase': 'reviewed'}) as tick:
            for _ in range(2):
                self.assertEqual(self.command('tick')[0], 0)
            self.assertEqual([c.args[0] for c in tick.call_args_list], [39, 39])
            states[39]['paused'] = True; tick.reset_mock()
            code, output, _ = self.command('tick')
            self.assertEqual((code, json.loads(output)), (0, [])); tick.assert_not_called()
            code, output, _ = self.command('scan')
            self.assertEqual(code, 0)
            self.assertEqual(json.loads(output)['managed_prs'], [36, 37, 38, 39])
        self.assertEqual(before, self.files()); self.assertEqual(messages, f.gh.messages)
        f.worker.assert_not_called()

    def test_v1_is_never_selected_and_reopened_missing_v2_cannot_fall_back(self):
        f = self.f; original = copy.deepcopy(f.gh.messages[36][0])
        f.gh.messages[36] = [{'id': 99, 'user': {'login': al.OWNER},
                              'body': LEGACY + '\nnot parsed even if malformed'}]
        self.assertIsNone(f.loop.load(36)[0])
        self.assertEqual(f.loop.tick(36)['phase'], 'unmanaged')
        f.gh.messages[36].insert(0, original)
        self.assertEqual(f.loop.load(36)[0]['version'], 2)
        f.gh.comment(36, al.pack(al.STATE, {'phase': 'done'}))
        before = self.files(); messages = copy.deepcopy(f.gh.messages)
        with self.assertRaisesRegex(ValueError, 'explicit owner recovery'): f.loop.tick(36)
        self.assertEqual(before, self.files()); self.assertEqual(messages, f.gh.messages)
        f.gh.messages[36].pop()
        for p in (f.loop.storage/'registry').iterdir(): p.unlink()
        before = self.files()
        with self.assertRaises(ValueError): f.loop.load(36)
        with self.assertRaises(ValueError): f.loop.tick(36)
        self.assertEqual(before, self.files()); f.worker.assert_not_called()

    def pending_merge(self):
        f = self.f
        f.loop.tick(36); f.gh.fail_after_merge = True; f.loop.tick(36)
        self.assertEqual(f.gh.merges, 1)
        return f

    def test_restart_continues_only_the_recorded_live_merge_and_returns_claim(self):
        f = self.pending_merge()
        self.assertEqual(f.loop.tick(36)['phase'], 'terminal-nonexecution')
        restarted = al.Loop(f.gh, f.loop.storage, f.worker)
        restarted.source_safe = f.loop.source_safe; restarted.cleanup = f.loop.cleanup
        self.assertEqual(restarted.continue_finish(36)['phase'], 'done')
        self.assertEqual(f.gh.issues[35]['state'], 'closed')
        self.assertEqual(f.gh.merges, 1); self.assertEqual(f.worker.call_count, 1)
        self.assertIn('Claim released', al.unpack(f.gh.comments(36), al.STATE)[0]['next'])

    def test_historical_phase_or_paused_bad_binding_never_authorizes_finish(self):
        f = self.pending_merge(); state, cid = al.unpack(f.gh.comments(36), al.STATE)
        variants = [dict(state, merge_intent=None), dict(state, paused=True),
                    dict(state, phase='done'), dict(state, phase='blocked'),
                    dict(state, binding=[]), dict(state, expected_head='f'*40),
                    dict(state, merge_intent=dict(state['merge_intent'], registry_id='f'*32))]
        for invalid in variants:
            f.gh.comment(36, al.pack(al.STATE, invalid), cid)
            before = self.files(); messages = copy.deepcopy(f.gh.messages)
            with self.subTest(state=invalid), patch.object(al, 'resolve') as resolve:
                with self.assertRaises(ValueError): f.loop.continue_finish(36)
                resolve.assert_not_called()
            self.assertEqual(before, self.files()); self.assertEqual(messages, f.gh.messages)
        self.assertEqual(f.worker.call_count, 1); self.assertEqual(f.gh.merges, 1)

    def test_pending_finish_revalidates_feedback_and_preserves_live_worker(self):
        f = self.pending_merge(); before = copy.deepcopy(f.gh.messages)
        worker = f.loop.storage/'pr-36/worker.json'; worker.parent.mkdir(exist_ok=True)
        worker.write_text('{"pid": null}')
        with self.assertRaisesRegex(ValueError, 'Worker state remains'): f.loop.continue_finish(36)
        self.assertEqual(f.gh.messages, before); self.assertEqual(worker.read_text(), '{"pid": null}')
        worker.unlink(); f.gh.comment(36, 'new substantive feedback')
        before = copy.deepcopy(f.gh.messages)
        with self.assertRaisesRegex(ValueError, 'binding changed'): f.loop.continue_finish(36)
        self.assertEqual(f.gh.messages, before); self.assertEqual(f.gh.issues[35]['state'], 'open')

    def test_merge_response_rechecks_latest_custody_without_stale_state_writes(self):
        for change in ('paused', 'owner', 'registry', 'paused-response-error'):
            with self.subTest(change=change):
                f = fixtures.LoopTests('runTest'); f.setUp(); self.addCleanup(f.doCleanups)
                f.loop.tick(36)
                merge = f.gh.merge; observed = {}

                def changed_merge(pr):
                    merge(pr)
                    if change.startswith('paused'):
                        state, cid = al.unpack(f.gh.comments(36), al.STATE)
                        f.gh.comment(36, al.pack(al.STATE, dict(state, paused=True, next='owner pause')), cid)
                    elif change == 'owner':
                        f.set_handoff(dict(f.fixture_handoff, owner='replacement-owner'))
                    else:
                        for p in (f.loop.storage/'registry').iterdir(): p.unlink()
                    observed['messages'] = copy.deepcopy(f.gh.messages)
                    observed['files'] = {str(p): p.read_bytes() for p in f.loop.storage.rglob('*') if p.is_file()}
                    if change == 'paused-response-error':
                        raise RuntimeError('response lost after owner paused')

                f.gh.merge = changed_merge
                result = f.loop.tick(36)
                self.assertEqual(result['phase'], 'postmerge-hold')
                self.assertEqual(f.gh.messages, observed['messages'])
                self.assertEqual({str(p): p.read_bytes() for p in f.loop.storage.rglob('*') if p.is_file()}, observed['files'])
                self.assertEqual(f.gh.issues[35]['state'], 'open')
                f.loop.cleanup.assert_not_called()
                self.assertEqual(f.gh.merges, 1); self.assertEqual(f.worker.call_count, 1)


if __name__ == '__main__':
    unittest.main()
