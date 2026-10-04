#!/usr/bin/env python3
"""Bounded #146 protocol experiment. Raw output stays in a private temp directory.

This is an opt-in probe, never part of CI or the product transport.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import queue
import signal
import subprocess
import tempfile
import threading
import time


class Probe:
    def __init__(self, agent, arguments=(), output_parent=None):
        self.directory = Path(tempfile.mkdtemp(prefix="acp146-", dir=output_parent))
        self.created_at = datetime.now(timezone.utc).isoformat()
        self.root = self.directory / "workspace"
        self.root.mkdir()
        self.root = self.root.resolve()
        (self.root / "input.txt").write_text("ACP fixture: amber cedar\n")
        subprocess.run(["git", "init", "-q", str(self.root)], check=True, timeout=5)
        self.events = []
        self.known_children = set()
        self.tool_state = {}
        self.tick_script = None
        self.incoming = queue.Queue()
        self.started = time.monotonic()
        self.next_id = 0
        self.stderr = (self.directory / "stderr.txt").open("wb")
        # Use existing login, never an inherited API-key or custom-endpoint override.
        environment = {key: value for key, value in os.environ.items()
                       if key not in {"CURSOR_API_KEY", "CURSOR_AUTH_TOKEN", "CURSOR_API_ENDPOINT"}}
        self.process = subprocess.Popen(
            [agent, *arguments, "acp"], cwd=self.root, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=self.stderr, start_new_session=True, env=environment,
        )
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()

    def record(self, direction, frame):
        self.events.append({"seconds": round(time.monotonic() - self.started, 3),
                            "direction": direction, "frame": frame})
        # ponytail: short fixed captures only; append JSONL if long recordings are needed.
        (self.directory / "wire.json").write_text(json.dumps(self.events, indent=2))

    def _read(self):
        while True:
            line = self.process.stdout.readline(1024 * 1024 + 1)
            if not line:
                self.incoming.put(None)
                return
            if len(line) > 1024 * 1024:
                self.incoming.put({"probe_error": "oversized frame"})
                return
            try:
                self.incoming.put(json.loads(line))
            except (ValueError, UnicodeError):
                self.incoming.put({"probe_error": "non-JSON stdout"})
                return

    def send(self, method, params, request=True):
        frame = {"jsonrpc": "2.0", "method": method, "params": params}
        if request:
            self.next_id += 1
            frame["id"] = self.next_id
        self.write(frame)
        return frame.get("id")

    def write(self, frame):
        self.record("client", frame)
        self.process.stdin.write((json.dumps(frame) + "\n").encode())
        self.process.stdin.flush()

    def receive(self, timeout):
        try:
            frame = self.incoming.get(timeout=max(0, timeout))
        except queue.Empty:
            raise TimeoutError("response deadline") from None
        self.record("agent", frame)
        if frame is None:
            raise EOFError("ACP stdout closed")
        if "probe_error" in frame:
            raise RuntimeError(frame["probe_error"])
        return frame

    def wait(self, request_id, seconds=20):
        deadline = time.monotonic() + seconds
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("response deadline")
            frame = self.receive(remaining)
            if frame.get("id") == request_id and "method" not in frame:
                return frame
            if "id" in frame and "method" in frame:
                # Metadata cannot authorize tools. Preserve the request for inspection.
                self.write({"jsonrpc": "2.0", "id": frame["id"],
                            "error": {"code": -32601, "message": "Probe callback not enabled"}})

    def call(self, method, params, seconds=20):
        return self.wait(self.send(method, params), seconds)

    def prompt(self, session, text, scenario):
        request_id = self.send("session/prompt", {"sessionId": session,
            "prompt": [{"type": "text", "text": text}]})
        deadline = time.monotonic() + 75
        cancel_at = time.monotonic() + 3 if scenario == "cancel-text" else None
        if scenario == "cancel-child":
            cancel_at = time.monotonic() + 30
        cancelled = False
        while True:
            now = time.monotonic()
            if now >= deadline:
                raise TimeoutError("response deadline")
            if scenario == "cancel-child" and self.tick_count() >= 2 and not cancelled:
                cancel_at = now
            if not cancelled and cancel_at is not None and now >= cancel_at:
                self.snapshot("before_cancel")
                self.send("session/cancel", {"sessionId": session}, request=False)
                cancelled = True
                deadline = min(deadline, now + 15)
            try:
                frame = self.receive(min(deadline - now, 0.2))
            except TimeoutError:
                if time.monotonic() >= deadline:
                    raise
                continue
            if frame.get("id") == request_id and "method" not in frame:
                self.snapshot("prompt_response")
                if scenario == "cancel-child":
                    time.sleep(1)
                    self.snapshot("one_second_after_response")
                return frame
            update = frame.get("params", {}).get("update", {})
            if update.get("sessionUpdate") in ("tool_call", "tool_call_update"):
                self.tool_state.setdefault(update["toolCallId"], {}).update(update)
            if "id" not in frame or "method" not in frame:
                continue
            method, params = frame["method"], frame.get("params", {})
            result = None
            if method == "session/request_permission":
                if cancelled:
                    result = {"outcome": {"outcome": "cancelled"}}
                elif scenario in ("permission-cancel", "cancel-child-permission"):
                    self.record("probe", {"permission_wait_seconds": 1})
                    time.sleep(1)
                    self.send("session/cancel", {"sessionId": session}, request=False)
                    cancelled = True
                    deadline = min(deadline, time.monotonic() + 15)
                    result = {"outcome": {"outcome": "cancelled"}}
                else:
                    allowed = self.can_allow(params, session, scenario)
                    desired = "allow_once" if allowed else "reject_once"
                    option = next((o for o in params.get("options", []) if o.get("kind") == desired), None)
                    result = {"outcome": {"outcome": "selected", "optionId": option["optionId"]}} if option else {"outcome": {"outcome": "cancelled"}}
            elif method == "cursor/ask_question":
                result = {"outcome": {"outcome": "answered", "answers": [
                    {"questionId": q["id"], "selectedOptionIds": [q["options"][0]["id"]]}
                    for q in params.get("questions", []) if q.get("options")]}}
            elif method == "cursor/create_plan":
                result = {"outcome": {"outcome": "rejected", "reason": "Protocol fixture only; no implementation"}}
            reply = {"jsonrpc": "2.0", "id": frame["id"]}
            if result is None:
                reply["error"] = {"code": -32601, "message": "Probe callback not enabled"}
            else:
                reply["result"] = result
            self.write(reply)

    def can_allow(self, params, session, scenario):
        if params.get("sessionId") != session:
            return False
        incoming = params.get("toolCall", {})
        tool = {**self.tool_state.get(incoming.get("toolCallId"), {}), **incoming}
        if scenario == "cancel-child" and tool.get("kind") == "execute":
            path = self.root / "tick.py"
            return (tool.get("rawInput", {}).get("command") == "python3 tick.py"
                    and self.tick_script is not None and not path.is_symlink()
                    and path.is_file() and path.read_text() == self.tick_script)
        locations = tool.get("locations", [])
        path = self.root / "input.txt"
        return (scenario == "edit" and tool.get("kind") in ("edit", "read") and bool(locations)
                and path.is_file() and not path.is_symlink()
                and all(Path(item.get("path", "")).resolve() == path for item in locations))

    def tick_count(self):
        path = self.root / "ticks.txt"
        return len(path.read_text().splitlines()) if path.is_file() else 0

    def snapshot(self, stage):
        try:
            output = subprocess.run(["ps", "-axo", "pid=,ppid=,pgid=,comm="],
                                    capture_output=True, text=True, check=True, timeout=2).stdout
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
            self.record("probe", {"stage": stage, "ticks": self.tick_count(),
                "processes": None, "process_observation": "unavailable"})
            return
        rows = []
        for line in output.splitlines():
            parts = line.strip().split(None, 3)
            if len(parts) == 4:
                rows.append((int(parts[0]), int(parts[1]), int(parts[2]), Path(parts[3]).name))
        family = {self.process.pid} | self.known_children
        for _ in rows:
            expanded = family | {pid for pid, ppid, group, _ in rows
                                 if ppid in family or group == self.process.pid}
            if expanded == family:
                break
            family = expanded
        self.known_children = family - {self.process.pid}
        self.record("probe", {"stage": stage, "ticks": self.tick_count(),
            "processes": [{"pid": pid, "ppid": ppid, "group": group, "executable": name}
                          for pid, ppid, group, name in rows if pid in family]})

    def close(self):
        self.record("probe", {"before_shutdown_exit": self.process.poll()})
        try:
            self.process.stdin.close()
        except BrokenPipeError:
            pass
        try:
            self.process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            self.record("probe", {"shutdown_signal": "SIGTERM"})
            os.killpg(self.process.pid, signal.SIGTERM)
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.record("probe", {"shutdown_signal": "SIGKILL"})
                os.killpg(self.process.pid, signal.SIGKILL)
                self.process.wait(timeout=3)
        self.reader.join(timeout=1)
        if not self.reader.is_alive():
            self.process.stdout.close()
        self.record("probe", {"stdout_reader_still_open": self.reader.is_alive()})
        while not self.incoming.empty():
            self.record("agent", self.incoming.get_nowait())
        self.stderr.close()
        self.record("probe", {"exit_code": self.process.returncode})
        self.snapshot("after_shutdown")
        (self.directory / "wire.json").write_text(json.dumps(self.events, indent=2))
        print(json.dumps({"private_output": str(self.directory),
                          "frames": len(self.events), "exit": self.process.returncode}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--agent", required=True)
    parser.add_argument("--output-parent", type=Path, required=True,
                        help="Existing private directory outside the repository; raw output is not public.")
    parser.add_argument("--scenario", choices=["metadata", "text", "edit", "extensions", "cancel-text", "cancel-child", "cancel-child-permission"], default="metadata")
    parser.add_argument("--profile", choices=["default", "ask-sandbox"], default="default")
    args = parser.parse_args()
    if args.profile != "default" and args.scenario != "metadata":
        parser.error("non-default profile is metadata-only")
    arguments = ("--mode", "ask", "--sandbox", "enabled") if args.profile == "ask-sandbox" else ()
    if not args.output_parent.is_dir():
        parser.error("output parent must be an existing private directory")
    probe = Probe(args.agent, arguments, args.output_parent)
    try:
        probe.record("probe", {"created_at": probe.created_at,
                               "scenario": args.scenario, "profile": args.profile,
                               "arguments": list(arguments) + ["acp"]})
        initialized = probe.call("initialize", {"protocolVersion": 1,
            "clientCapabilities": {"fs": {"readTextFile": False, "writeTextFile": False},
                                   "terminal": False},
            "clientInfo": {"name": "acp146-controlled-probe", "version": "1"}})
        if "error" in initialized:
            raise RuntimeError("initialize failed; no authentication change attempted")
        created = probe.call("session/new", {"cwd": str(probe.root), "mcpServers": []})
        if "error" in created:
            raise RuntimeError("new session failed; no authentication change attempted")
        if args.scenario == "text":
            session = created["result"]["sessionId"]
            config = {option.get("category"): option for option in created["result"]["configOptions"]}
            for option, value in ((config["mode"], "ask"), (config["model"], config["model"]["currentValue"])):
                if value not in [choice["value"] for choice in option["options"]]:
                    raise RuntimeError("requested config value was not advertised")
                config_id = option["id"]
                response = probe.call("session/set_config_option", {
                    "sessionId": session, "configId": config_id, "value": value})
                readback = {option["id"]: option["currentValue"] for option in
                            response.get("result", {}).get("configOptions", [])}
                if "error" in response or readback.get(config_id) != value:
                    raise RuntimeError("advertised config did not confirm the requested value")
            for prompt in (
                "Protocol-only text fixture. Do not use tools, MCP, files, shell, or subagents. Reply with exactly: amber amber cedar",
                "Protocol-only text fixture, same session. Do not use tools. Repeat exactly the three words you replied with in the previous turn.",
            ):
                response = probe.call("session/prompt", {"sessionId": session,
                    "prompt": [{"type": "text", "text": prompt}]}, seconds=90)
                if "error" in response:
                    break
        elif args.scenario in ("edit", "extensions", "cancel-text", "cancel-child", "cancel-child-permission"):
            session = created["result"]["sessionId"]
            if args.scenario in ("cancel-child", "cancel-child-permission"):
                (probe.root / "tick.py").write_text(
                    "from pathlib import Path\nimport time\n"
                    "with Path('ticks.txt').open('w') as stream:\n"
                    "    for index in range(40):\n"
                    "        stream.write(str(index) + '\\n')\n"
                    "        stream.flush()\n"
                    "        time.sleep(0.2)\n")
                probe.tick_script = (probe.root / "tick.py").read_text()
            prompts = {
                "cancel-child": "ACP cancellation fixture only: run exactly python3 tick.py in the current workspace once. This finite script writes only ticks.txt for eight seconds. Do not read or edit files, use other commands, MCP, web, or subagents. If cancelled, stop and do not retry.",
                "edit": "This is a small ACP protocol test, not a development task. Read only input.txt and replace its content with exactly ACP fixture: cedar amber followed by a newline. Only input.txt may be read or written. Do not use subagents, MCP, shell, web, or other files. Report done briefly; no other action.",
                "extensions": "This is a small ACP protocol test, not a development task. Use the multiple-choice question tool once with options amber and cedar. After the answer, use the plan tool once to propose reading input.txt; do not execute the plan or read the file. If either tool is unavailable, say so and stop. Do not edit files, run shell, access web or MCP, or use subagents.",
                "cancel-text": "Protocol-only cancellation fixture. Do not use any tools, files, shell, MCP, or subagents. Write the numbers 1 through 300, each on its own line.",
            }
            prompt_key = "cancel-child" if args.scenario == "cancel-child-permission" else args.scenario
            probe.prompt(session, prompts[prompt_key], args.scenario)
            probe.record("probe", {"input_after": (probe.root / "input.txt").read_text(),
                "resident_after_prompt": probe.process.poll() is None})
    finally:
        probe.close()


if __name__ == "__main__":
    main()
