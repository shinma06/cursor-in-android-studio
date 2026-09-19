# #116: controlled print captures (2026-09-12)

These are **field projections of three real CLI runs**, not raw transcripts and not
synthetic wire sequences. Product base: `f1d84cbc4006f76805fda22904a91fa1b490c841`.
CLI `2026.09.10-fd3934a`; `system/init.model` = `Auto` in all three runs.
No explicit `--model` was supplied; Auto's underlying model is not exposed by these
captures. Mode `ask`, permissionMode `default`, no `--force` or `--auto-review`.
No account change, resume, GUI, MCP call, subagent, or repository access was used.
The only tool calls observed were the requested two built-in file reads.

| File | Partial | Exit | Raw events / projected lines | Assistant kinds (delta / pre-tool flush / final flush) |
| --- | --- | --- | --- | --- |
| `plain.jsonl` | true | 1 | 58 / 57 | 40 / 0 / 0 |
| `tools.jsonl` | true | 0 | 42 / 41 | 12 / 2 / 1 |
| `nonpartial.jsonl` | false | 0 | 27 / 26 | 0 / 2 / 1 (shapes only; these are new complete messages) |

`plain` stopped with `NonRetriableError: Agent Looping Detected` on stderr, without
a result event. It proves received repeated deltas, **not** a successful 12-line response.
`tools` and `nonpartial` have empty stderr and a success result. No run hit the
90-second timeout. Timestamp ties are preserved: a timestamp is not an event ID.

## Reproduction inputs

Use a new disposable directory containing only `alpha.txt` (`ALPHA=17\n`) and
`beta.txt` (`BETA=29\n`), where `\n` means a newline. Use the existing authenticated
CLI; do not inspect previous sessions or change accounts. Three new conversations
were run sequentially with a 90-second bound, stdout/stderr directed to private files:

```text
agent -p --output-format stream-json --stream-partial-output --trust --mode ask <prompt>
```

For `nonpartial`, omit `--stream-partial-output`; all other arguments and the tools
prompt stay the same. Do not run these during tests: model output/chunk boundaries
are nondeterministic and inference consumes usage. The committed replay is offline.

`plain` prompt (exact):

```text
This is an isolated stream rendering test. Do not use any tools, subagents, files, network, or MCP. Reply with exactly 12 lines. Each line must be: red red red blue blue blue. No introduction or code fence.
```

`tools` / `nonpartial` prompt (exact):

```text
This is an isolated output rendering test. Use only the built-in read file tool and only alpha.txt and beta.txt in the current directory. Do not use shell, network, MCP, subagents, or other files. First say START-A then read alpha.txt. After that tool finishes say BETWEEN-B then read beta.txt in a separate tool call. After both reads say DONE-C and the sum of ALPHA and BETA. Keep all text very short.
```

## Projection and privacy

- Preserve event order, `type`, `subtype`, `timestamp_ms`, assistant `message`
  (including exact whitespace/chunk boundaries), result text and `is_error`, init model.
- Replace model-call IDs and tool-call IDs with stable per-run aliases, preserving
  equality and field presence. Reduce read args to the verified basename
  `alpha.txt` / `beta.txt`; omit read results and all other tool fields.
- Omit the single user echo (prompt above), session/request IDs, cwd, authentication
  source, durations, usage, and extra fields. Keep thinking event kind/order but
  remove its text. No raw thinking or existing private conversation is published.
- Therefore these files are evidence of **text and metadata classification only**,
  not full tool payloads, thinking content, process timing, or privacy capabilities.
  An omitted text field is a projection decision, not evidence that the CLI omitted it.
- Raw captures stay private. The older public `stream-json-fixtures` are reused in
  place; #146's owner and unpublished ACP fixtures were neither read nor changed.

Synthetic metadata, malformed input, cumulative resend, result-only/error cases live
in `Issue116PrintContractTest`. They are explicitly separate from these captures.
