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

def send(message):
    print(json.dumps({"jsonrpc": "2.0", **message}, ensure_ascii=False), flush=True)

def response(identifier, result):
    send({"id": identifier, "result": result})

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
        response(request["id"], {"sessionId": "session-one", "configOptions": config})
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
    elif "method" not in request and request.get("id") == pending:
        if scenario == "unknown":
            assert request["error"]["code"] == -32601
        response(prompt_id, {"stopReason": scenario if scenario in ("refusal", "max_tokens", "max_turn_requests", "cancelled") else "end_turn"})
