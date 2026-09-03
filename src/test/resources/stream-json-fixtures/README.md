# stream-json fixtures

Raw `agent -p --output-format stream-json --stream-partial-output --trust` output captured from a
live `cursor-agent` CLI (`2026.09.02-c22c1a3`), for use in `StreamJsonParserTest` (M0/M1, see
GitHub issue #2). Each file is JSON Lines — one event object per line, verbatim from the CLI.

## Status

- `01_plain_question.jsonl` — **partial**. Captures `system/init`, `user` (echo), `connection`,
  and `retry` events for a trivial prompt, but the run never reached an `assistant`/`result` event:
  it failed with `resource_exhausted` (Free-tier quota) after 3 retries. Useful as a fixture for
  the events it does contain; does not cover the happy path.

Still needed once CLI quota/plan allows a full run: a successful `assistant` + `result` pair, a
file-edit turn with `--force` on and off (for F-30/F-31/F-40 design), and a shell-tool-call turn
(for F-32). Add fixtures here as they're captured — don't hand-write synthetic ones, since the
point is pinning down the CLI's actual (undocumented, version-drifting) schema.
