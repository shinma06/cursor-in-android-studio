# stream-json fixtures

Raw `agent -p --output-format stream-json --stream-partial-output --trust` output captured from a
live `cursor-agent` CLI (`2026.09.02-c22c1a3`), for use in parser unit tests (M0/M4/M5).

Each file is JSON Lines — one event object per line, verbatim from the CLI (paths sanitized where noted).

These fixtures cover the print transport only, not ACP JSON-RPC or GUI acceptance.
The tool fixtures capture completed payloads; started-field assumptions still need a live capture.

## Files

- `01_plain_question.jsonl` — partial Free-tier capture (`resource_exhausted` before assistant).
- `02_edit_completed.jsonl` — single completed `editToolCall` with diff metadata (Teams plan).
- `03_shell_completed.jsonl` — single completed `shellToolCall` with stdout (Teams plan).
- `04_plain_question_success.jsonl` — full happy path: init, thinking, assistant, result (Teams plan).
