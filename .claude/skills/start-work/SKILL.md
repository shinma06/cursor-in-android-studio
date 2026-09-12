---
name: start-work
description: Start an Issue-scoped worktree and develop PR with acceptance tracking.
---

# Start work

Read AGENTS.md, docs/project-mission.md, docs/architecture/cursor-integration.md, docs/development/codex-execution-policy.md, docs/development/github-workflow.md, requirements, the Project roadmap and relevant Milestone, target Issue/comments and open PRs.
ACP First is the design policy; the shipped transport is still print/stream-json. Check ACP/IDE/MCP before adding CLI-based state inference, and preserve existing transport evidence.
Use the title and exclusive type/priority/status labels from github-workflow.md (#96); closed means status:done. Every change needs Issue search, a unique ownership claim, isolated Issue-numbered branch/worktree and Draft PR.
Read-only advice/review needs no Issue. Preserve unrelated work and unreleased claims.

1. Inspect status/worktrees, fetch origin and compare the intended base. Normal work starts from origin/develop.
   Only documented GUI-not-required tooling/main bootstrap starts from origin/main. Clear initial upstream and run bootstrap.sh.
2. Apply docs/development/work-management.md: concrete Issue, Project registration, Milestone selection, real native relationships or intentional Standalone, and Project Status/Priority/Relationship Status readback. Claim files, owner, base SHA, dependencies, separate reviewer, GUI need and next action. Store local paths/host privately.
3. Add docs/verification/changes/issue-N.json with every required Case, Japanese steps/expectations, GPT/human status and fix/recheck tracking.
   Set Integration and Verification in the PR template. GUI pending/blocked/fail does not prevent develop once tests/review pass.
4. Create a Draft PR early. GUI work still requires the designated operator's host lease; never invent pass or convert environment errors to product fail.
5. Read finish-work and pr-automation.md. Stop the writer before new opaque-registry enrollment. Never run old leaking enroll.
   Existing enrolled owners and PAUSED heartbeat stay unchanged unless explicitly coordinated. Routine authorization persists.
