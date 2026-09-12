"""Synthetic local ACP server; no Cursor, account, network or project edits."""
import json
import os
import pathlib
import subprocess
import sys
import threading

scenario, root = sys.argv[1], pathlib.Path(sys.argv[2])
config = [
    {"id": "mode", "type": "select", "currentValue": "agent", "options": [
        {"value": value, "name": value} for value in ["agent", "ask", "plan"]]},
    {"id": "model", "type": "select", "currentValue": "default[]", "options": [
        {"value": "default[]", "name": "Auto"}, {"value": "small", "name": "Small"}]},
]
prompt_id = None
pending = None
child = None

wire_lock = threading.Lock()

def send(message):
    with wire_lock:
        print(json.dumps({"jsonrpc": "2.0", **message}, ensure_ascii=False), flush=True)

def response(identifier, result):
    send({"id": identifier, "result": result})

def command_updates():
    import time
    update(sessionUpdate="available_commands_update", availableCommands=[{"name": "Mixed-日本語", "description": "synthetic only", "input": {"hint": "東京 alpha beta"}}])
    while not (root / "replace-commands").exists():
        time.sleep(.01)
    send({"method": "session/update", "params": {"sessionId": "foreign-session", "update": {"sessionUpdate": "available_commands_update", "availableCommands": [{"name": "foreign", "description": "wrong tab"}]}}})
    update(sessionUpdate="available_commands_update", availableCommands=[])
    update(sessionUpdate="available_commands_update", availableCommands=[{"name": "invalid", "description": 8}])
    update(sessionUpdate="available_commands_update", availableCommands=[{"name": "replacement", "description": "new catalog"}])

def update(**value):
    send({"method": "session/update", "params": {"sessionId": "session-one", "update": value}})

for line in sys.stdin:
    request = json.loads(line)
    with (root / "wire.jsonl").open("a") as output:
        output.write(json.dumps(request) + "\n")
    method = request.get("method")
    if method == "initialize":
        assert request["params"]["clientCapabilities"] == {"fs": {"readTextFile": False, "writeTextFile": False}, "terminal": False}
        response(request["id"], {"protocolVersion": 1.5 if scenario == "version-fraction" else "1" if scenario == "version-string" else 1})
    elif method == "session/new":
        assert request["params"]["cwd"] == str(root.resolve())
        if scenario == "commands-delayed":
            import time
            while not (root / "release-new").exists():
                time.sleep(.01)
        response(request["id"], {"sessionId": "session-one", "configOptions": config})
        if scenario in ("commands", "commands-delayed"):
            threading.Thread(target=command_updates, daemon=True).start()
    elif method == "session/set_config_option":
        if scenario != "bad-config":
            for option in config:
                if option["id"] == request["params"]["configId"]:
                    option["currentValue"] = request["params"]["value"]
        response(request["id"], {"configOptions": config})
    elif method == "session/prompt":
        assert request["params"]["sessionId"] == "session-one"
        prompt_id = request["id"]
        if scenario == "eof":
            sys.exit(0)
        elif scenario in ("cancel", "child"):
            if scenario == "child":
                child = subprocess.Popen([sys.executable, "-c", "import pathlib,sys,time; p=pathlib.Path(sys.argv[1]); (p/'child-ready').touch();\nwhile not (p/'release-child').exists(): time.sleep(.01)", str(root)], stdin=subprocess.DEVNULL)
            if child is not None:
                threading.Thread(target=child.wait, daemon=True).start()
            update(sessionUpdate="agent_message_chunk", content={"type": "text", "text": "running"})
        elif scenario.startswith("task-"):
            # Synthetic adaptation of #118's public projection; requests remain unsupported.
            send({"method": "cursor/task", "params": {"toolCallId": "missing", "model": "ignore-before-tool"}})
            update(sessionUpdate="tool_call", toolCallId="task-one", kind="other", status="pending", rawInput={"_toolName": "task", "description": "synthetic child", "subagentType": {"custom": {"name": "reader"}}})
            update(sessionUpdate="tool_call_update", toolCallId="task-one", status="in_progress")
            if scenario != "task-stop":
                update(sessionUpdate="tool_call_update", toolCallId="task-one", status="failed" if scenario == "task-failed" else "completed", rawOutput={"durationMs": 12, "isBackground": False})
                send({"method": "cursor/task", "params": {"sessionId": "foreign-session", "toolCallId": "task-one", "agentId": "foreign"}})
                send({"method": "cursor/task", "params": {"toolCallId": "unknown", "agentId": "unknown"}})
                send({"method": "cursor/task", "params": {"agentId": "missing-id"}})
                metadata = {"toolCallId": "task-one", "agentId": "reported-child", "model": "default", "durationMs": "12"}
                if scenario == "task-notify":
                    send({"method": "cursor/task", "params": metadata})
                    send({"method": "cursor/task", "params": metadata})
                    response(prompt_id, {"stopReason": "end_turn"})
                    send({"method": "cursor/task", "params": {"toolCallId": "task-one", "agentId": "after-terminal"}})
                else:
                    pending = 7001
                    send({"id": pending, "method": "cursor/task", "params": metadata})
        elif scenario in ("permission", "unknown"):
            pending = 0
            params = {"sessionId": "session-one", "toolCall": {"toolCallId": "opaque\ncall", "title": "Synthetic", "rawInput": {"command": "synthetic-command"}}, "options": [{"optionId": "allow", "name": "Allow", "kind": "allow_once"}, {"optionId": "reject", "name": "Reject", "kind": "reject_once"}]}
            send({"id": pending, "method": "session/request_permission" if scenario == "permission" else "fs/read_text_file", "params": params})
        else:
            for text in ["はい", "はい"]:
                update(sessionUpdate="agent_message_chunk", content={"type": "text", "text": text})
            response(prompt_id, {"stopReason": scenario if scenario in ("refusal", "max_tokens", "max_turn_requests", "cancelled") else "end_turn"})
    elif method == "session/cancel":
        response(prompt_id, {"stopReason": "cancelled"})
        (root / "cancel-response").touch()
        if scenario == "task-stop":
            send({"method": "cursor/task", "params": {"toolCallId": "task-one", "agentId": "after-stop"}})
    elif "method" not in request and request.get("id") == pending:
        if scenario == "unknown" or scenario in ("task-request", "task-failed", "task-late-standard"):
            assert request["error"]["code"] == -32601
        response(prompt_id, {"stopReason": scenario if scenario in ("refusal", "max_tokens", "max_turn_requests", "cancelled") else "end_turn"})
        if scenario == "task-late-standard":
            update(sessionUpdate="tool_call_update", toolCallId="task-one", status="in_progress")
