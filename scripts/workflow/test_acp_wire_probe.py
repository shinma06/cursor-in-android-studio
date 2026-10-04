"""Synthetic transport checks only; never starts Cursor or makes network requests."""
import json
import hashlib
from pathlib import Path
import shutil
import queue
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

from scripts.probes.acp_wire_probe import Probe
from scripts.probes.export_acp_wire import export


class ProbeTest(unittest.TestCase):
    def test_published_fixture_integrity_and_anonymized_schema(self):
        root = Path(__file__).resolve().parents[2]
        folder = root / "docs/research/fixtures/acp-146"
        manifest = json.loads((folder / "manifest.json").read_text())
        # Captures bind the historical revision, not a future edited probe.
        self.assertRegex(manifest["capture_source_revision"], r"^[0-9a-f]{40}$")
        for name in ("capture_probe", "exporter"):
            self.assertRegex(manifest[name + "_sha256"], r"^[0-9a-f]{64}$")
        self.assertEqual({p.name for p in folder.glob("*.jsonl")}, {f["file"] for f in manifest["fixtures"]})
        total_prompts = 0
        for fixture in manifest["fixtures"]:
            raw = (folder / fixture["file"]).read_bytes()
            self.assertEqual(hashlib.sha256(raw).hexdigest(), fixture["sha256"])
            events = [json.loads(line) for line in raw.decode().splitlines()]
            self.assertEqual(len(events), fixture["published_events"])
            prompts = [e for e in events if (e.get("frame") or {}).get("method") == "session/prompt"]
            self.assertEqual(len(prompts), fixture["prompt_count"])
            total_prompts += len(prompts)
            # Revalidate the retained schema and common identifier guards, without
            # treating this data-integrity check as another live or GUI execution.
            export(events, "/fixture")
        self.assertEqual(len(manifest["fixtures"]), manifest["session_count"])
        self.assertEqual(total_prompts, manifest["prompt_count"])

    def test_export_omits_private_setup_and_preserves_repeated_deltas_and_zero_id(self):
        session = "b115f90f-e1b2-4f99-9300-56d40d0aef6e"
        opaque = "ce14b685-c42c-4092-acdc-22b3828a0f91\nprovider-suffix"
        workspace = "/Users/private-person/fixture"
        def event(frame, direction="agent"):
            return {"seconds": 1, "direction": direction, "frame": frame}
        def update(data):
            return event({"method": "session/update", "params": {"sessionId": session, "update": data}})
        events = [
            event({"method": "initialize", "params": {"clientInfo": {"name": "private-client"}}}, "client"),
            event({"result": {"protocolVersion": 1, "authMethods": ["private-auth"], "agentCapabilities": {}}}),
            update({"sessionUpdate": "available_commands_update", "availableCommands": ["private-command"]}),
            update({"sessionUpdate": "agent_thought_chunk", "content": {"type": "text", "text": "private-thought"}}),
            update({"sessionUpdate": "agent_message_chunk", "content": {"type": "text", "text": "amber"}}),
            update({"sessionUpdate": "agent_message_chunk", "content": {"type": "text", "text": " amber"}}),
            event({"id": 0, "method": "session/request_permission", "params": {
                "sessionId": session, "toolCall": {"toolCallId": opaque, "rawInput": {"path": workspace + "/input.txt"}}}}),
            event({"id": 0, "result": {"outcome": {"outcome": "cancelled"}}}, "client"),
        ]
        before = json.dumps(events)
        text = export(events, workspace)
        for private in (session, opaque, "private-person", "private-client", "private-auth", "private-command", "private-thought"):
            self.assertNotIn(private, text)
        public = [json.loads(line) for line in text.splitlines()]
        updates = [e["frame"].get("params", {}).get("update", {}) for e in public]
        self.assertEqual("".join(u["content"]["text"] for u in updates if u.get("sessionUpdate") == "agent_message_chunk"), "amber amber")
        request = next(e["frame"] for e in public if e["frame"].get("method") == "session/request_permission")
        self.assertEqual(request["id"], 0)
        self.assertIn("\n", request["params"]["toolCall"]["toolCallId"])
        self.assertEqual(request["params"]["toolCall"]["rawInput"]["path"], "/fixture/input.txt")
        self.assertEqual(public[-1]["frame"]["id"], 0)
        self.assertEqual(json.dumps(events), before)

    def test_export_rejects_unreviewed_fields_and_remaining_identifiers(self):
        for frame in ({"privateAccount": "person"}, {"text": "person@example.invalid"},
                      {"text": "/home/person/other-project"}, {"text": "Bearer private-token"}):
            with self.subTest(frame=frame), self.assertRaises(ValueError):
                export([{"seconds": 0, "direction": "agent", "frame": frame}], "/tmp/synthetic-fixture")

    def test_expired_deadline_does_not_drain_queued_notifications(self):
        for method in ("wait", "prompt"):
            with self.subTest(method=method):
                probe = Probe.__new__(Probe)
                probe.incoming = queue.Queue()
                for _ in range(4):
                    probe.incoming.put({"jsonrpc": "2.0", "method": "session/update", "params": {}})
                probe.record = Mock()
                probe.send = Mock(return_value=1)
                times = [0, 1] if method == "wait" else [0, 76]
                with patch("scripts.probes.acp_wire_probe.time.monotonic", side_effect=times):
                    with self.assertRaises(TimeoutError):
                        if method == "wait":
                            probe.wait(1, seconds=0.5)
                        else:
                            probe.prompt("synthetic-session", "synthetic prompt", "edit")
                self.assertEqual(probe.incoming.qsize(), 4)
                probe.record.assert_not_called()

    def test_permission_requires_session_tool_context_and_unchanged_fixture(self):
        with tempfile.TemporaryDirectory() as temporary:
            probe = Probe.__new__(Probe)
            probe.root = Path(temporary).resolve()
            probe.tick_script = "print('finite synthetic fixture')\n"
            path = probe.root / "tick.py"
            path.write_text(probe.tick_script)
            opaque = "synthetic-tool\nsynthetic-provider"
            probe.tool_state = {opaque: {"kind": "execute", "rawInput": {"command": "python3 tick.py"}}}
            params = {"sessionId": "synthetic-session", "toolCall": {"toolCallId": opaque, "status": "pending"}}
            self.assertTrue(probe.can_allow(params, "synthetic-session", "cancel-child"))
            self.assertFalse(probe.can_allow(params, "other-session", "cancel-child"))
            self.assertFalse(probe.can_allow(params, "synthetic-session", "edit"))
            altered = {**params, "toolCall": {**params["toolCall"], "rawInput": {"command": "python3 other.py"}}}
            self.assertFalse(probe.can_allow(altered, "synthetic-session", "cancel-child"))
            path.write_text("print('changed')")
            self.assertFalse(probe.can_allow(params, "synthetic-session", "cancel-child"))
            path.unlink()
            (probe.root / "other.py").write_text(probe.tick_script)
            path.symlink_to(probe.root / "other.py")
            self.assertFalse(probe.can_allow(params, "synthetic-session", "cancel-child"))
            (probe.root / "input.txt").write_text("synthetic")
            edit = {"sessionId": "synthetic-session", "toolCall": {"kind": "edit", "locations": [{"path": str(probe.root / "input.txt")}]}}
            self.assertTrue(probe.can_allow(edit, "synthetic-session", "edit"))
            self.assertFalse(probe.can_allow(edit, "synthetic-session", "extensions"))
            edit["toolCall"]["locations"].append({"path": str(probe.root / "other.py")})
            self.assertFalse(probe.can_allow(edit, "synthetic-session", "edit"))

    def test_permission_id_zero_is_answered_on_cancel(self):
        with tempfile.TemporaryDirectory() as temporary:
            agent = Path(temporary) / "fake_permission.py"
            agent.write_text('import json, signal, sys\nsignal.alarm(5)\nrequest = json.loads(sys.stdin.readline())\nprint(json.dumps({"jsonrpc":"2.0", "id":0, "method":"session/request_permission", "params":{"sessionId":"synthetic-session", "toolCall":{"toolCallId":"synthetic-tool"}, "options":[]}}), flush=True)\ncancel = json.loads(sys.stdin.readline())\nassert cancel["method"] == "session/cancel" and "id" not in cancel\nreply = json.loads(sys.stdin.readline())\nassert reply == {"jsonrpc":"2.0", "id":0, "result":{"outcome":{"outcome":"cancelled"}}}\nprint(json.dumps({"jsonrpc":"2.0", "id":request["id"], "result":{"stopReason":"cancelled"}}), flush=True)\nsys.stdin.read()\n')
            probe = Probe(sys.executable, (str(agent),))
            try:
                result = probe.prompt("synthetic-session", "synthetic fixed prompt", "cancel-child-permission")
                self.assertEqual(result["result"]["stopReason"], "cancelled")
            finally:
                probe.close()
                shutil.rmtree(probe.directory)

    def test_fragmented_frames_bidirectional_ids_timeout_and_eof(self):
        with tempfile.TemporaryDirectory() as temporary:
            agent = Path(temporary) / "fake_agent.py"
            agent.write_text('''import json, sys
request = json.loads(sys.stdin.readline())
# Agent and client request IDs deliberately overlap.
frames = [
    {"jsonrpc":"2.0", "id":request["id"], "method":"synthetic/unknown", "params":{}},
    {"jsonrpc":"2.0", "method":"session/update", "params":{"text":"あ amber amber"}},
    {"jsonrpc":"2.0", "id":request["id"], "result":{"ok":True}},
]
wire = b"".join((json.dumps(f, ensure_ascii=False)+"\\n").encode() for f in frames)
for byte in wire:
    sys.stdout.buffer.write(bytes([byte]))
    sys.stdout.buffer.flush()
reply=json.loads(sys.stdin.readline())
assert reply["error"]["code"] == -32601
# Stay resident until the next input, then exercise EOF.
sys.stdin.readline()
''')
            probe = Probe(sys.executable, (str(agent),))
            try:
                result = probe.call("synthetic/start", {})
                self.assertEqual(result["result"], {"ok": True})
                updates = [e["frame"] for e in probe.events if e["direction"] == "agent"
                           and e["frame"].get("method") == "session/update"]
                self.assertEqual(updates[0]["params"]["text"], "あ amber amber")
                with self.assertRaises(TimeoutError):
                    probe.receive(0.02)
                probe.send("synthetic/exit", {})
                with self.assertRaises(EOFError):
                    probe.receive(2)
            finally:
                probe.close()
                shutil.rmtree(probe.directory)


if __name__ == "__main__":
    unittest.main()
