---
name: finish-work
description: Finish assigned Cursor in Android Studio work with tests, evidence-backed GUI status, GPT integration, and Issue handoff.
---

# Finish work

Read `AGENTS.md` and `docs/loop-engineering/README.md` for the role contract.

1. Run `./gradlew test`; also `buildPlugin` for packaging/build changes and GUI test builds.
   If loop tooling changed, run `python3 -m unittest discover -s scripts/loop -p 'test_*.py'`.
2. For affected GUI acceptance, record target build, MV IDs, actual observations, evidence,
   and observer in `docs/manual-verification/matrix.md` and the run record. Computer Use is
   preferred; human observation is valid. Capture/auth failures are blocked, not product fail.
   `loop.py check` checks evidence structure only; GPT evaluates truth and scope.
3. Obtain Claude's independent review when available and record GPT's disposition. If blocked
   by login/quota, state that review is pending; do not attribute GPT's self-review to Claude.
4. GPT fetches/reconciles origin/main, commits and pushes assigned changes. Preserve unrelated
   local edits. Never force push or bypass failing tests. Claude/Cursor delegates return their
   bounded result/commit to GPT and do not independently push main.
5. GPT updates the target Issue checklist with only completed criteria and leaves a closing or
   handoff comment with commit, run summary, remaining blockers, and next action. Update #1 if
   a milestone completed. Landing code alone does not close pending GUI acceptance.
6. Update CLAUDE.md/AGENTS.md and requirements when implementation status or verified behavior
   changed. Keep historical evidence intact; archive QA rows only with an explicit completion
   or obsolescence reason, never just because a branch was merged.
