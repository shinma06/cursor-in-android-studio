---
name: start-work
description: Begin implementation in cursor-agent-plugin by syncing, checking Issue claims, and assigning the GPT-led GUI loop scope.
---

# Start work

Read `AGENTS.md` and `docs/loop-engineering/README.md`. GPT owns coordination, integration,
and Computer Use; Claude reviews or handles a bounded delegation; Cursor performs GUI fixture tasks.

1. Inspect `git status`. Preserve existing edits. Fetch and compare HEAD with origin/main;
   don't pull over local work. Ask only when ownership/overlap prevents safe progress.
2. Read Issue #1, the target Issue, and comments. Do not duplicate an unfinished claim.
3. Claim implementation scope with owner, base SHA, assigned files, reviewer, GUI operator,
   and budget. A reviewer receiving a bounded read-only request need not post a new claim.
4. For GUI work, select MV IDs and acceptance before execution. Create a new run with
   `python3 scripts/loop/loop.py prepare <run-id> --issue <number> --case plugin:MV-001 --case cursor:MV-001`.
   Replace the example MV IDs with the chosen acceptance cases.
   Use disposable Cursor/plugin fixtures; record actual build identity before product QA.
5. Stay within existing user authorization. Claude implementation uses a separate checkout
   and returns commits to GPT. Only one GUI operator at a time.

Use `finish-work` when the assigned work is functionally complete. Pending GUI acceptance
is not completed by passing unit tests or merging code.
