---
name: finish-work
description: Validate an Issue PR and hand off integration, QA, and owned-resource cleanup against its acceptance criteria; for review-only requests, return the fixed-diff assessment without writer actions.
---

# Finish work

Use the [GitHub workflow](../../../docs/development/github-workflow.md) for validation/progression and [PR automation](../../../docs/development/pr-automation.md) for handoff. A review-only request returns the fixed-diff/acceptance assessment; do not perform writer/coordinator operations.

**Verifiable result:** Run `python3 scripts/workflow/change_impact.py --run-tests` on the fixed Issue diff; record selected checks and skip reasons. Explicit packaging/GUI builds also require `buildPlugin`. Do not expand successful validation of an unchanged diff without a reason; changes, failures or unresolved concerns justify relevant checks. Preserve required CI/hooks, fix relevant failures and push only the owned branch.

**Independent assessment:** A separate session reviews HEAD/base and Issue acceptance, distinguishing scope_complete, issue_complete and GUI need. Unresolved code findings or required-test failures block both targets. Additional sessions follow the [Codex execution policy](../../../docs/development/codex-execution-policy.md). Do not equate Mission/ACP First policy with implementation/live success. For development-context creation, updates, review or audit, apply the [single context policy](../../../docs/architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件): trace old requirements, loading routes and justified evidence/language exceptions. Report static checks, actual client/model behavior and resource measurements separately.

**Handoff:** Stop the writer and confirm clean state before trusted-main v2 enrollment. The coordinator then owns fixes/re-review/four required checks/guarded merge. PM verifies actual execution; if stopped, record the next owner and concrete blocker. Registration does not prove progress. Preserve existing owner/source and PAUSED heartbeat. Limit the #83 bootstrap exception to its documented procedure.

**Integration and closure:** Develop can squash with all Cases tracked, retaining GUI pending/blocked/fail (product failures need fix Issues). Transfer all Cases/main tracking to QA idempotently, read back bidirectional links, then close an implementation Issue whose acceptance is complete. Main promotion requires every commit/required Case of the [fixed candidate](../../../docs/verification/README.md) to pass on one identified build, followed by merge commit. Do not substitute old partial passes or changes beyond permitted promotion JSON. Never close tracking/research parents or QA merely because a child PR merged.

**Remaining work:** Follow [Work Management](../../../docs/development/work-management.md) to read back origin/QA Project, Milestone, native relationships and human instructions. Use `--parent` only for a real parent. Clean up only owned, stopped, clean resources; verify remote/local/tracking refs and worktrees are gone, or record retention reason/owner/resumption condition. Never delete main/master/develop; preserve GUI lease, unsaved work and existing owners. Merge success alone is not completion.
