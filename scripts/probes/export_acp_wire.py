#!/usr/bin/env python3
"""Export a reviewed #146 capture as an anonymized, deliberately partial JSONL fixture.

No network or provider calls. Inspect the output before publishing; this is not a
general-purpose anonymizer for arbitrary conversation history.
"""
import argparse
import copy
import json
from pathlib import Path
import re


REVIEWED_KEYS = set("""
seconds direction frame jsonrpc id method params result sessionId prompt type text
configId value protocolVersion modes currentModeId availableModes name description
configOptions category currentValue options sessionUpdate update title updatedAt
content toolCallId kind status rawInput path command locations rawOutput oldText
newText toolCall optionId outcome stopReason created_at scenario profile arguments
stage ticks processes pid ppid group executable input_after resident_after_prompt
before_shutdown_exit stdout_reader_still_open exit_code permission_wait_seconds
shutdown_signal process_observation error code message
""".split())


def export(events, workspace):
    """Keep observed frame boundaries and ID relationships; omit private setup/catalogs."""
    workspace = str(Path(workspace).resolve())
    identifiers = {}
    processes = {}

    def replace(value):
        if isinstance(value, dict):
            unknown = set(value) - REVIEWED_KEYS
            if unknown:
                raise ValueError("Unreviewed fields: " + ", ".join(sorted(unknown)))
            answer = {}
            for key, child in value.items():
                if key in ("pid", "ppid", "group"):
                    if type(child) is not int:
                        raise ValueError("Invalid process identifier")
                    answer[key] = processes.setdefault(child, 1000 + len(processes))
                else:
                    answer[key] = replace(child)
            return answer
        if isinstance(value, list):
            return [replace(child) for child in value]
        if isinstance(value, str):
            for original, replacement in sorted(identifiers.items(), key=lambda pair: -len(pair[0])):
                value = value.replace(original, replacement)
            return value.replace(workspace, "/fixture").replace(workspace.replace("/private/", "/", 1), "/fixture")
        return value

    # Collect opaque identities before rewriting references, including late updates.
    def collect(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key in ("sessionId", "toolCallId") and isinstance(child, str):
                    if child not in identifiers:
                        prefix = "session" if key == "sessionId" else "tool"
                        number = len(identifiers) + 1
                        identifiers[child] = "\n".join(f"{prefix}-{number}-{part}" for part in range(child.count("\n") + 1))
                collect(child)
        elif isinstance(value, list):
            for child in value:
                collect(child)

    collect(events)
    published = []
    for original in events:
        event = copy.deepcopy(original)
        frame = event.get("frame") or {}
        if frame.get("method") in ("initialize", "session/new"):
            continue  # No client identity, paths, configured MCP, or capabilities.
        update = frame.get("params", {}).get("update", {})
        if update.get("sessionUpdate") == "available_commands_update":
            continue  # The catalog can contain user-defined commands.
        if update.get("sessionUpdate") == "agent_thought_chunk":
            update["content"] = {"type": "text", "text": "[thought omitted]"}
        result = frame.get("result", {})
        if "protocolVersion" in result:
            frame["result"] = {"protocolVersion": result["protocolVersion"]}
        if "models" in result:
            del result["models"]  # No complete provider/model catalog.
        for option in result.get("configOptions", []):
            option.pop("description", None)
            if option.get("category") == "model":
                selected = [choice for choice in option["options"] if choice.get("value") == option["currentValue"]]
                if len(selected) != 1:
                    raise ValueError("Selected model is not uniquely advertised")
                option["options"] = selected
        published.append(replace(event))

    text = "".join(json.dumps(event, ensure_ascii=False, separators=(",", ":")) + "\n" for event in published)
    # Fail closed on common accidental identifiers; manual inspection is still required.
    forbidden = r"/Users/|/home/|/private/|/var/folders/|[A-Z]:\\|https?://|[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}|\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\b|Bearer |sk-[A-Za-z0-9]{12,}"
    if re.search(forbidden, text, re.IGNORECASE):
        raise ValueError("Possible private value remains; inspect locally")
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path, help="Private acp146-* directory containing wire.json and workspace")
    parser.add_argument("output", type=Path, help="New local candidate JSONL; review it before publication")
    args = parser.parse_args()
    result = export(json.loads((args.capture / "wire.json").read_text()), args.capture / "workspace")
    with args.output.open("x") as stream:
        stream.write(result)


if __name__ == "__main__":
    main()
