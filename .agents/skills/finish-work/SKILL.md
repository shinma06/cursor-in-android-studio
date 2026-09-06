---
name: finish-work
description: Finish a repository task through tests, independent review, GUI evidence, GitHub PR merge and Issue handoff.
---

# Finish work

Read `docs/development/github-workflow.md`; for GUI, also `docs/development/gui-coordination.md`.

1. Run `./gradlew test`; buildPlugin for packaging or GUI builds. Run changed tooling tests
   (`scripts/workflow` and/or `scripts/loop`). Preserve unrelated edits.
2. Push only your Issue branch; update the PR and Issue. Obtain independent session review
   of the fixed HEAD and record findings/disposition. Claude unavailable means pending, unless
   another independent reviewer actually performs it; never misattribute self-review.
3. Required GUI stays queued until the designated operator has the host lease and an identified
   build/fixture. Record MV IDs, observations, evidence and observer in matrix/run + Issue/PR.
   Capture/auth failure is blocked. Structural checks or CLI success do not establish GUI pass.
4. The integration coordinator fetches/reconciles main, repeats affected checks/review after
   changes, verifies latest test and PR policy success plus GUI acceptance, then merges the PR
   via GitHub with the reviewed HEAD guard. Never local main commit/push, force push or bypass.
5. Update Issue checklist, merge SHA, evidence and next action; close only completed acceptance.
   Release implementation claim and GUI lease only after safe stop/handoff. Remove only your
   own clean, inactive worktree. If blocked, leave PR/Issue and explicit owner/next action open.
