---
name: start-work
description: Begin or resume an Issue-scoped code, docs, or configuration change with ownership and acceptance tracking; not read-only advice or review.
---

# Start work

Read-only advice/review needs no new Issue or branch. For changes, use AGENTS.md and the [workflow's conditional references and decisions](../../../docs/development/github-workflow.md#参照する範囲と進行判断). Reuse already-read unchanged versions. New features need Mission/ACP First, product changes need relevant requirements/design, and operational changes need the corresponding policy. Creating/updating development context also requires the [single context policy](../../../docs/architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件); classify purpose and loading path before choosing language/format/location.

Start when the Issue/comments, related PRs, Project/relevant Milestone, dependencies and acceptance are understood and no writer conflicts exist. Search before creating an Issue. Claim owner, scope, base SHA, dependencies, independent reviewer, GUI need and next action; read back the claim. Unreleased claims never expire with time. Follow [Work Management](../../../docs/development/work-management.md) for Project/labels/native relationships; never invent a parent for Standalone work.

Inspect status/worktrees, fetch with `git fetch --prune origin`, and reconcile the base of the dedicated Issue branch/worktree. Normally use origin/develop; GUI-not-required main tooling follows the workflow conditions. Clear initial upstream and run `bash scripts/workflow/bootstrap.sh`. On resumption first reconcile owner/source/execution handles. The same owner reuses the existing worktree; do not recreate it before checking. For takeover, follow [interruption/resumption](../../../docs/development/github-workflow.md#中断再開): confirm the old writer stopped and the claim was released or explicitly reassigned, then make the new claim and use the new owner's dedicated worktree. Never share the old owner's directory. Do not edit enrolled branches concurrently or automatically change owner/source; follow [PR automation](../../../docs/development/pr-automation.md).

Record all acceptance Cases in `docs/verification/changes/issue-N.json`: Japanese steps/expectations, GPT/human state and fix/recheck tracking. If GUI is unnecessary, record why and the required CLI checks. Create a Draft PR with Integration/Verification on the first meaningful push, then continue implementation/validation. GUI requires the designated operator/lease; never mark unobserved behavior pass.

Start procedures or an initial PR are not completion. Continue to acceptance and [finish-work](../finish-work/SKILL.md). Before additional sessions/independent review, read the [Codex execution policy](../../../docs/development/codex-execution-policy.md).
