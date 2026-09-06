---
name: finish-work
description: Finish a repository task through tests, independent review, GUI evidence, GitHub PR merge and Issue handoff.
---

# Finish work

Read `docs/development/github-workflow.md`; for GUI, also `docs/development/gui-coordination.md`.

1. Run `./gradlew test`; buildPlugin for packaging or GUI builds. Run changed tooling tests
   (`scripts/workflow` and/or `scripts/loop`). Preserve unrelated edits.
2. Push only your Issue branch; update the PR/Issue. Read `docs/development/pr-automation.md`.
   Stop your writer and enroll the clean worktree using the trusted-main `agent_loop.py enroll`
   with explicit scope/owner/parent and --writer-stopped. Use --close-issue only for full Issue acceptance.
3. The scheduled coordinator starts independent read-only reviewers and bounded workspace-write
   fixers, invalidates old approvals on changes, and requires Agent review + test + PR policy.
   GUI cases remain queued for the lease-holding GPT/human operator. Never invent GUI evidence.
4. Do not concurrently fix, merge, close Issues or delete the enrolled worktree yourself. Read its
   GitHub state. The coordinator owns HEAD-guarded merge, scoped Issue/parent updates and verified
   remote/local branch cleanup, retrying cleanup after interruptions.
5. If the coordinator reports blocked, inspect the concrete cause and preserve edits. Resume only
   after resolving it and confirming no worker remains. Do not reset budgets automatically each tick.
