"""GUI registration and owned cleanup preserve evidence without a reservation record."""
import copy
from contextlib import nullcontext, redirect_stdout, redirect_stderr
import io
import json
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import agent_loop as al
import test_agent_loop as fixtures
from test_agent_loop import HEAD, BASE


class GuiCoordinationTests(unittest.TestCase):
    def setUp(self):
        self.f = fixtures.LoopTests('runTest'); self.f.setUp(); self.addCleanup(self.f.doCleanups)
        self.legacy = self.f.loop.storage/'old-gui-lease.json'
        self.evidence = self.f.loop.storage/'gui-evidence.json'
        # Any accidental consultation of the retired module must fail before touching a real host record.
        self.retired = SimpleNamespace(locked=Mock(side_effect=AssertionError('retired reservation read')),
                                       transition=Mock(side_effect=AssertionError('retired reservation write')))
        self.enterContext(patch.dict(sys.modules, {'gui_lease': self.retired}))
        self.valid = {'head': HEAD, 'base': BASE, 'artifact_sha256': 'e'*64,
                      'run': 'fixture-run', 'observer': 'assigned-session',
                      'loaded_identity': 'observed loaded JAR hash', 'evidence_url': 'tool:session-id/turn-id',
                      'processes_stopped': True,
                      'cases': [{'id': 'MV-001', 'status': 'pass', 'observation': 'expected result observed'}]}

    def command(self, evidence, operator='assigned-session'):
        self.evidence.write_text(json.dumps(evidence))
        with patch.object(al, 'Loop', return_value=self.f.loop), \
                patch.object(al, 'coordinator_lock', return_value=nullcontext()), \
                patch.object(sys, 'argv', ['agent_loop.py', 'gui', '--pr', '36',
                                        '--evidence', str(self.evidence), '--operator', operator]), \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            return al.main()

    def old_record(self, value):
        self.legacy.unlink(missing_ok=True)
        if value is not None: self.legacy.write_text(value)
        return self.legacy.read_bytes() if self.legacy.exists() else None

    def assert_old_unchanged(self, before):
        self.assertEqual(self.legacy.read_bytes() if self.legacy.exists() else None, before)
        self.retired.locked.assert_not_called(); self.retired.transition.assert_not_called()

    def test_missing_stale_corrupt_and_unrelated_reservations_do_not_gate_gui(self):
        for old in (None, '{"expires_at":0}', '{', '{"owner":"other","expires_at":9999999999}'):
            with self.subTest(old=old):
                before = self.old_record(old)
                self.assertEqual(self.command(self.valid), 0)
                state, _ = al.unpack(self.f.gh.comments(36), al.STATE)
                self.assertEqual(state['gui'], self.valid)
                self.assert_old_unchanged(before)
        self.f.worker.assert_not_called()

    def test_evidence_and_assignment_mismatches_refuse_without_state_change(self):
        invalid = [dict(self.valid, head='f'*40), dict(self.valid, base='f'*40),
                   dict(self.valid, artifact_sha256='invalid'), dict(self.valid, observer='other-session'),
                   dict(self.valid, loaded_identity=''), dict(self.valid, processes_stopped=False),
                   dict(self.valid, cases=[]), dict(self.valid, cases=[{'id':'MV-001','status':'pass','observation':''}]),
                   dict(self.valid, cases=[{'id':'MV-001','status':'fail','observation':'wrong result'}])]
        before = copy.deepcopy(self.f.gh.messages)
        for evidence in invalid:
            with self.subTest(evidence=evidence):
                self.assertEqual(self.command(evidence), 1)
                self.assertEqual(self.f.gh.messages, before)
        self.assertEqual(self.command(self.valid, operator=' '), 1)
        self.assertEqual(self.f.gh.messages, before); self.f.worker.assert_not_called()

    def test_registering_evidence_never_resumes_a_paused_operation(self):
        self.f.gh.comment(36, al.pack(al.STATE, {'phase':'gui-queued','paused':True}))
        self.assertEqual(self.command(self.valid), 0)
        state, _ = al.unpack(self.f.gh.comments(36), al.STATE)
        self.assertIs(state['paused'], True); self.f.worker.assert_not_called()

    def test_owned_cleanup_ignores_old_record_and_does_not_write_it(self):
        f = self.f
        for old in (None, '{"expires_at":0}', '{', '{"owner":"other","expires_at":9999999999}'):
            with self.subTest(old=old):
                f.checkout.mkdir(parents=True, exist_ok=True)
                before = self.old_record(old)
                h = dict(f.fixture_handoff, source=str(f.loop.storage/'absent-original-source'))
                with patch.object(al, 'command', return_value=''):
                    al.Loop.cleanup(f.loop, f.gh.pr(36), h)
                self.assertFalse(f.checkout.exists())
                self.assert_old_unchanged(before)
        f.worker.assert_not_called()

    def test_cleanup_keeps_protected_refs_live_workers_and_changed_checkout(self):
        f = self.f
        for branch in ('main', 'master', 'develop'):
            with self.subTest(branch=branch), patch.object(al, 'command') as command:
                with self.assertRaisesRegex(ValueError, 'Persistent branches'):
                    al.Loop.cleanup(f.loop, f.gh.pr(36), dict(f.fixture_handoff, branch=branch))
                command.assert_not_called()
        worker = f.checkout.parent/'worker.json'; worker.write_text('{"pid":null}')
        with self.assertRaisesRegex(ValueError, 'Worker state remains'):
            al.Loop.cleanup(f.loop, f.gh.pr(36), f.fixture_handoff)
        self.assertTrue(worker.exists()); worker.unlink()
        f.local_head = 'f'*40
        with self.assertRaisesRegex(ValueError, 'Managed checkout changed'):
            al.Loop.cleanup(f.loop, f.gh.pr(36), f.fixture_handoff)
        self.assertTrue(f.checkout.exists())


if __name__ == '__main__':
    unittest.main()
