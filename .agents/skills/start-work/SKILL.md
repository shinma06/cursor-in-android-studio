---
name: start-work
description: Start a repository change with a GitHub Issue claim, isolated worktree and PR workflow, including GUI queue routing.
---

# Start work

Read `AGENTS.md` and `docs/development/github-workflow.md`. Apply this even when Git is not
mentioned. Read-only review/advice does not require a new Issue.

1. Read requirements, #1, target Issue/comments and open PRs. Inspect status/worktrees, fetch
   origin and compare main. Preserve local edits. Search before creating a task Issue.
2. Claim with unique session owner, base SHA, files, dependencies, reviewer and GUI need.
   Unreleased claims never expire just by elapsed time; use the workflow's conflict rules.
3. Create/reuse only your own Issue-numbered branch and separate worktree. Clear the initial
   origin/main upstream; run `bash scripts/workflow/bootstrap.sh`. Never edit/commit on main.
4. Implement assigned scope and open a Draft PR after the first meaningful push. Update Issue
   at scope changes and handoffs. Parallel writers each need their own task/worktree.
5. GUI required: read `docs/development/gui-coordination.md`, queue a fixed-build request,
   and let the designated GPT operator acquire the shared host lease before any desktop use,
   runIde, installation or restart. Read `docs/loop-engineering/README.md` for cases/evidence.
   GUI busy/blocked does not stop independent implementation, tests or review.

After creating the PR, read `docs/development/pr-automation.md` and hand off the clean worktree
with `agent_loop.py enroll --writer-stopped`. Do not keep editing after handoff.

Use `finish-work` for tests, review, PR merge and Issue completion. Existing user authorization
applies; do not ask again for routine work within scope.
