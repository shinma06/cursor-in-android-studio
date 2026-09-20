"""Synthetic local ACP server; no Cursor, account, network or project edits."""
import json
import os
import pathlib
import subprocess
import sys
import threading
import time

scenario, root = sys.argv[1], pathlib.Path(sys.argv[2])
capture = None
control = root
if len(sys.argv) != 3:
    try:
        if len(sys.argv) != 5 or sys.argv[3] != "--capture" or scenario not in ("permission", "normal", "eof", "cancel", "child", "bad-config", "commands-delayed", "events", "questions", "plan"):
            raise ValueError("Unknown capture scenario")
        root = root.resolve(strict=True)
        marker = root / "ACP_SYNTHETIC_FIXTURE.json"
        value = json.loads(marker.read_text(encoding="utf-8"))
        if marker.is_symlink() or not isinstance(value, dict) or type(value.get("schema")) is not int or value != {
            "schema": 1, "purpose": "synthetic-acp-fixture", "workspace": str(root),
        } or pathlib.Path.cwd().resolve() != root:
            raise ValueError("An explicit synthetic root is required")
        destination = pathlib.Path(sys.argv[4])
        if destination.is_symlink() or not destination.is_dir() or destination.resolve().is_relative_to(root):
            raise ValueError("Capture must be outside the synthetic root")
        # Exclusive creation also refuses PID reuse; never append another run's log.
        descriptor = os.open(destination / f"wire-{os.getpid()}.jsonl", os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        capture = os.fdopen(descriptor, "w", encoding="utf-8")
        control = destination / f"control-{os.getpid()}"
        control.mkdir(mode=0o700)
    except (ValueError, OSError, TypeError):
        print("Synthetic ACP capture refused: check arguments, marker, root and output.", file=sys.stderr)
        sys.exit(64)
config = [
    {"id": "mode", "type": "select", "currentValue": "agent", "options": [
        {"value": value, "name": value} for value in ["agent", "ask", "plan"]]},
    {"id": "model", "type": "select", "currentValue": "default[]", "options": [
        {"value": "default[]", "name": "Auto"}, {"value": "small", "name": "Small"}]},
]
prompt_id = None
pending = None
next_request_id = 0
child = None
cancelled = threading.Event()
closed = threading.Event()

wire_lock = threading.RLock()

def record(direction, payload):
    if capture is not None:
        capture.write(json.dumps({
            "direction": direction, "pid": os.getpid(), "monotonic_ns": time.monotonic_ns(),
            "rpc_id": payload.get("id"),
            "session_id": payload.get("params", {}).get("sessionId") or payload.get("result", {}).get("sessionId"),
            "payload": payload,
        }, ensure_ascii=False) + "\n")
        capture.flush()

def send(message):
    payload = {"jsonrpc": "2.0", **message}
    with wire_lock:
        record("out", payload)
        print(json.dumps(payload, ensure_ascii=False), flush=True)

def response(identifier, result):
    send({"id": identifier, "result": result})

def command_updates():
    import time
    update(sessionUpdate="available_commands_update", availableCommands=[{"name": "Mixed-日本語", "description": "synthetic only", "input": {"hint": "東京 alpha beta"}}])
    while not (control / "replace-commands").exists():
        if closed.wait(.01):
            return
    send({"method": "session/update", "params": {"sessionId": "foreign-session", "update": {"sessionUpdate": "available_commands_update", "availableCommands": [{"name": "foreign", "description": "wrong tab"}]}}})
    update(sessionUpdate="available_commands_update", availableCommands=[])
    update(sessionUpdate="available_commands_update", availableCommands=[{"name": "invalid", "description": 8}])
    update(sessionUpdate="available_commands_update", availableCommands=[{"name": "replacement", "description": "new catalog"}])

def update(**value):
    send({"method": "session/update", "params": {"sessionId": "session-one", "update": value}})

def new_session(identifier):
    if scenario == "commands-delayed":
        (control / "new-ready").touch()
        deadline = time.monotonic() + 10  # Below AcpSession session/new timeout (20 seconds).
        while True:
            if closed.wait(.01):
                return
            with wire_lock:
                if cancelled.is_set():
                    return
                if time.monotonic() >= deadline:
                    send({"id": identifier, "error": {"code": -32000, "message": "Synthetic release timeout"}})
                    return
                if (control / "release-new").exists():
                    break
    # Cancellation acknowledgement and response/worker creation have one ordering.
    with wire_lock:
        if cancelled.is_set() or closed.is_set():
            return
        response(identifier, {"sessionId": "session-one", "configOptions": config})
        if scenario in ("commands", "commands-delayed"):
            threading.Thread(target=command_updates, daemon=True).start()


def finish(reason="end_turn", identifier=None):
    global prompt_id, pending
    if prompt_id is not None and (identifier is None or identifier == prompt_id):
        identifier = prompt_id
        prompt_id, pending = None, None
        response(identifier, {"stopReason": reason})


def tool_events(identifier, stop):
    # Exact partial-field/array-replacement shapes reused from AcpProtocolTest.
    update(sessionUpdate="agent_thought_chunk", content={"type": "text", "text": "合成の説明"})
    update(sessionUpdate="tool_call", toolCallId="qa-tool", title="合成変更", status="in_progress", rawInput={"path": str(root / "a.txt")})
    (control / "events-ready").touch()
    deadline = time.monotonic() + 300
    while not (control / "release-events").exists():
        if stop.is_set() or closed.wait(.01):
            return
        if time.monotonic() >= deadline:
            finish("cancelled", identifier)
            return
    if stop.is_set() or prompt_id != identifier:
        return
    update(sessionUpdate="tool_call_update", toolCallId="qa-tool", content=[
        {"type": "diff", "path": str(root / "a.txt"), "oldText": "before", "newText": "after"},
        {"type": "content", "content": {"type": "text", "text": "synthetic result"}}], locations=[{"path": str(root / "a.txt")}])
    update(sessionUpdate="tool_call_update", toolCallId="qa-tool", status="completed")
    update(sessionUpdate="tool_call_update", toolCallId="qa-tool", content=[], locations=[])
    update(sessionUpdate="agent_message_chunk", content={"type": "text", "text": "はいはい😀😀"})
    finish(identifier=identifier)


for line in sys.stdin:
    request = json.loads(line)
    if capture is None:
        with (root / "wire.jsonl").open("a") as output:
            output.write(json.dumps(request) + "\n")
    else:
        with wire_lock:
            record("in", request)
    method = request.get("method")
    if method == "initialize":
        assert request["params"]["clientCapabilities"] == {"fs": {"readTextFile": False, "writeTextFile": False}, "terminal": False}
        initialized = {"protocolVersion": 1.5 if scenario == "version-fraction" else "1" if scenario == "version-string" else 1}
        if scenario.startswith("image-"):
            initialized["agentCapabilities"] = {"promptCapabilities": {"image": {"image-true": True, "image-false": False, "image-string": "true"}[scenario]}}
        response(request["id"], initialized)
    elif method == "session/new":
        assert request["params"]["cwd"] == str(root.resolve())
        cancelled.clear()
        if scenario == "commands-delayed":
            threading.Thread(target=new_session, args=(request["id"],), daemon=True).start()
        else:
            new_session(request["id"])
    elif method == "session/set_config_option":
        if scenario != "bad-config":
            for option in config:
                if option["id"] == request["params"]["configId"]:
                    option["currentValue"] = request["params"]["value"]
        response(request["id"], {"configOptions": config})
    elif method == "session/prompt":
        assert request["params"]["sessionId"] == "session-one"
        prompt_id = request["id"]
        cancelled = threading.Event()
        pending = None
        if scenario == "eof":
            sys.exit(0)
        elif scenario in ("cancel", "child"):
            if scenario == "child":
                child = subprocess.Popen([sys.executable, "-c", "import pathlib,sys,time; p=pathlib.Path(sys.argv[1]); (p/'child-ready').touch();\nwhile not (p/'release-child').exists(): time.sleep(.01)", str(control)], stdin=subprocess.DEVNULL, env={})
                (control / "child.pid").write_text(str(child.pid))
            if child is not None:
                threading.Thread(target=child.wait, daemon=True).start()
            update(sessionUpdate="agent_message_chunk", content={"type": "text", "text": "running"})
        elif scenario.startswith("content-"):
            send({"method": "session/update", "params": {"sessionId": "foreign-session", "update": {"sessionUpdate": "agent_message_chunk", "content": {"type": "image", "mimeType": "foreign", "data": "x"}}}})
            update(sessionUpdate="agent_message_chunk", content={"type": "text", "text": "before"})
            update(sessionUpdate="agent_message_chunk", content={"type": "image", "mimeType": "image/png", "data": "synthetic"})
            update(sessionUpdate="agent_message_chunk", content={"type": "text", "text": "after"})
            update(sessionUpdate="tool_call", toolCallId="media", status="completed", content=[None, {"type": "content", "content": {"type": "audio", "mimeType": "audio/wav", "data": "synthetic"}}])
            if scenario != "content-stop":
                finish()
                update(sessionUpdate="agent_message_chunk", content={"type": "image", "mimeType": "late", "data": "x"})
        elif scenario.startswith("task-"):
            # Synthetic adaptation of #118's public projection; requests remain unsupported.
            send({"method": "cursor/task", "params": {"toolCallId": "missing", "model": "ignore-before-tool"}})
            update(sessionUpdate="tool_call", toolCallId="task-one", kind="other", status="pending", rawInput={"_toolName": "task", "description": "synthetic child", "subagentType": {"custom": {"name": "reader"}}})
            update(sessionUpdate="tool_call_update", toolCallId="task-one", status="in_progress")
            if scenario.startswith("task-background"):
                update(sessionUpdate="tool_call_update", toolCallId="task-one", status="completed", rawOutput={"isBackground": True})
                update(sessionUpdate="tool_call_update", toolCallId="task-one", status="completed", rawOutput={"isBackground": None})
                (root / "background-ready").touch()
                if scenario != "task-background-stop":
                    finish("cancelled" if scenario == "task-background-cancelled" else "end_turn")
            elif scenario != "task-stop":
                update(sessionUpdate="tool_call_update", toolCallId="task-one", status="failed" if scenario == "task-failed" else "completed", rawOutput={"durationMs": 12, "isBackground": False})
                if scenario == "task-reopened":
                    update(sessionUpdate="tool_call_update", toolCallId="task-one", status="in_progress")
                send({"method": "cursor/task", "params": {"sessionId": "foreign-session", "toolCallId": "task-one", "agentId": "foreign"}})
                send({"method": "cursor/task", "params": {"toolCallId": "unknown", "agentId": "unknown"}})
                send({"method": "cursor/task", "params": {"agentId": "missing-id"}})
                metadata = {"toolCallId": "task-one", "agentId": "reported-child", "model": "default", "durationMs": "12"}
                if scenario == "task-notify":
                    send({"method": "cursor/task", "params": metadata})
                    send({"method": "cursor/task", "params": metadata})
                    finish()
                    send({"method": "cursor/task", "params": {"toolCallId": "task-one", "agentId": "after-terminal"}})
                else:
                    pending = 7001
                    send({"id": pending, "method": "cursor/task", "params": metadata})
        elif scenario == "events":
            threading.Thread(target=tool_events, args=(prompt_id, cancelled), daemon=True).start()
        elif scenario in ("questions", "plan"):
            pending = next_request_id
            next_request_id += 1
            params = {"sessionId": "session-one", "toolCallId": "q" if scenario == "questions" else "p"}
            if scenario == "questions":
                params["questions"] = [{"id": "a", "prompt": "選択", "options": [{"id": "yes", "label": "はい"}, {"id": "no", "label": "いいえ"}], "allowMultiple": False}]
            else:
                params["plan"] = "# Plan"
            send({"id": pending, "method": "cursor/ask_question" if scenario == "questions" else "cursor/create_plan", "params": params})
        elif scenario in ("permission", "unknown"):
            pending = next_request_id
            next_request_id += 1
            params = {"sessionId": "session-one", "toolCall": {"toolCallId": "opaque\ncall", "title": "Synthetic", "rawInput": {"command": "synthetic-command"}}, "options": [{"optionId": "allow", "name": "Allow", "kind": "allow_once"}, {"optionId": "reject", "name": "Reject", "kind": "reject_once"}]}
            send({"id": pending, "method": "session/request_permission" if scenario == "permission" else "fs/read_text_file", "params": params})
        else:
            for text in ["はい", "はい"]:
                update(sessionUpdate="agent_message_chunk", content={"type": "text", "text": text})
            finish(scenario if scenario in ("refusal", "max_tokens", "max_turn_requests", "cancelled") else "end_turn")
    elif method == "session/cancel":
        with wire_lock:
            cancelled.set()
            finish("cancelled")
            (control / "cancel-response").touch()
        if scenario == "content-stop":
            update(sessionUpdate="agent_message_chunk", content={"type": "image", "mimeType": "after-stop", "data": "x"})
        if scenario == "task-stop":
            send({"method": "cursor/task", "params": {"toolCallId": "task-one", "agentId": "after-stop"}})
    elif "method" not in request and pending is not None and request.get("id") == pending:
        if scenario == "unknown" or scenario in ("task-request", "task-failed", "task-late-standard", "task-reopened"):
            assert request["error"]["code"] == -32601
        if request.get("result") == {"outcome": {"outcome": "cancelled"}}:
            cancelled.set()
            finish("cancelled")
        else:
            finish(scenario if scenario in ("refusal", "max_tokens", "max_turn_requests", "cancelled") else "end_turn")
        if scenario == "task-late-standard":
            update(sessionUpdate="tool_call_update", toolCallId="task-one", status="in_progress")

closed.set()
cancelled.set()
