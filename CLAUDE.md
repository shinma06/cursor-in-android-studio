# CLAUDE.md

Development-agent instructions for this repository. `AGENTS.md` remains a symlink to this file, so both entry points have the same contract.

<a id="最上位ミッションとacp-first2026-09-09--134"></a>

## Mission and ACP First

Use [Project Mission](docs/project-mission.md) as the highest project criterion and [ACP First](docs/architecture/cursor-integration.md) for integration design. Reproduce the functionality, workflows, feedback and IDE integration of Cursor's **in-IDE Agent panel**, not the standalone Agents Window or a pixel-perfect copy. The goal includes deep understanding and operation of Android IDE, build, device and execution environments.

For each new feature, compare the latest official Cursor in-IDE panel and the strongest available JetBrains AI Assistant + Cursor ACP + IntelliJ / Android Studio integration + IntelliJ MCP Server + MCP tools configuration. Record equivalent-or-better UX and the additional value of direct IDE integration. Do not call existing competitor capabilities unique features.

Consider ACP standard → Cursor ACP extensions → IDE APIs → MCP → CLI → unstructured output parsing. Keep accurate direct IDE APIs, CLI-only operations and justified fallbacks. Map structured sessions, tool progress, permissions, questions, Plan/Todo, cancellation and context/usage into the UI; ACP is not just a chat API. Verify availability and separate design policy from shipped implementation. Do not rebuild Cursor's internal agent; separate Cursor-specific, ACP, IDE, MCP, CLI and UI responsibilities.

<a id="変更作業の共通契約"></a>

## Shared change contract

Every code, documentation or configuration change needs an Issue, ownership claim, dedicated Issue-numbered branch/worktree and PR, even without a Git request. Read-only advice/review needs no new Issue. Never commit/push directly to main/develop. Preserve unrelated work and unreleased claims. Normally start from origin/develop; GUI-not-required main tooling must meet the [workflow](docs/development/github-workflow.md) conditions.

Use [start-work](.agents/skills/start-work/SKILL.md) to start/resume and [finish-work](.agents/skills/finish-work/SKILL.md) to validate, hand off and finish. The matching `.claude/skills/` entries have the same contract; Cursor also uses `.cursor/rules/loop-engineering.mdc`. Select task-specific references through the [workflow reference table](docs/development/github-workflow.md#参照する範囲と進行判断); reuse already-read unchanged material, revisiting changed, missing or conflicting parts. Keep authorization and resumption decisions in that workflow.

When creating, updating, reviewing or auditing development context, apply the single [context policy](docs/architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件). It covers purpose, language, format, canonical location, loading conditions and evidence preservation. Human-facing reports remain Japanese. Do not copy the policy into each consumer or change externally managed instructions.

- [Work Management](docs/development/work-management.md): Issues are concrete work, Project is overall tracking, Milestones are outcomes and native relationships are real dependencies/decomposition. Read back registration, Status/Priority and relationships at creation, triage and closure; never invent a parent for classification.
- [Codex execution policy](docs/development/codex-execution-policy.md): read before adding/delegating agents. GPT-6 Astra must not spawn/delegate child agents; use ordinary tools or justified independent top-level sessions. Other models follow the policy's applicability; #135 is the current authority. This does not constrain the product's Cursor Subagent support.
- [PR automation](docs/development/pr-automation.md): stop the writer and confirm clean state before trusted-main v2 enrollment. Require separate-session fixed HEAD/base review and current successful test / PR policy / Agent review / Acceptance gate. Enrollment is not completion: PM/coordinator tracks actual execution, results and next owner. Do not change existing owner/source/registry or PAUSED heartbeat without coordination.
- [GUI coordination](docs/development/gui-coordination.md): desktop actions, installs, restarts and runIde require the host-wide lease and designated GPT operator or human handoff. Worktrees do not isolate IDE state. Use disposable fixtures and identify the loaded build.
- [Governance audit](docs/development/git-governance-audit.md): check existing triggers at start and integration/closure (major Milestone, High Impact, structural problems, 10 meaningful merges). The audit Issue is authoritative; do not add a full audit per PR or a second ledger.

<a id="統合と完了"></a>

## Integration and completion

Develop permits squash integration with required tests, independent review and complete Case tracking while GUI is pending, environment-blocked or product-failed. Track product failures in dedicated fix Issues. Unresolved code findings or failing required tests block integration. Close an implementation Issue only after all implementation acceptance and bidirectional QA transfer/readback. Never close tracking/research parents or QA merely because a child PR merged.

Main promotion requires **every commit and required Case** of the fixed develop candidate to pass on the same identified build, then a merge commit. Docs/tooling may target main with concrete GUI-not-required reasons and CLI validation. [Verification JSON](docs/verification/README.md) is authoritative; do not edit generated Markdown separately or substitute old-build, partial or synthetic passes for full candidate acceptance.

Every open QA needs a body link to [human QA instructions](docs/verification/human-qa.md), prerequisites/steps/expectations/recording instructions and Project #2 QA display readback. Track outstanding main reflection even without GUI. Use [current cases](docs/verification/current.md), [loop protocol](docs/loop-engineering/README.md) and [human runbook](docs/loop-engineering/human-runbook.md) for their respective verification work.

Verify merge and cleanup separately. Follow [branch hygiene](docs/development/pr-automation.md#ブランチ残存の判定と完了確認) for owned, stopped, clean Issue resources and remote/local/tracking refs/worktrees. Never delete main/master/develop. Record retained resources, reason, owner and resumption condition. Never force push or bypass hooks/protection. GitHub is the shared authority; keep local paths, hosts and secrets in the private registry only.

<a id="ponytailfull--128"></a>

## Ponytail full

For implementation, audits and reviews, use [Ponytail](https://github.com/DietrichGebert/ponytail) full: understand callers, callees and requirements before choosing necessity → existing code → standard library → native capability → installed dependency → direct implementation → minimum new code. Do not delete abstractions merely for brevity; verify references, registration, storage compatibility and tests.

Preserve trust-boundary validation, authentication/authorization, type safety, data integrity/loss prevention, error handling, accessibility, concurrency, necessary logs and explicit requirements. Never weaken existing tests. Keep code when no safe simplification exists; do not perform large rewrites, bulk formatting, add dependencies or switch to ultra automatically. This policy applies without plugin/hook loading and must survive independent-session handoffs. Existing GitHub/GUI/approval rules and the execution policy still apply. See [installation and audit evidence](docs/development/ponytail.md) when needed.

<a id="what-this-is"></a>

## Product identity and implementation references

The product is **Cursor in Android Studio**, repository/artifact slug `cursor-in-android-studio`. Preserve `com.cursoragent.plugin`, persisted state/storage names and internal `Cursor Agent` tool-window/notification IDs for upgrades. Use `PluginBrand.NAME` for runtime labels; historical evidence retains its original names.

This is an Android Studio / IntelliJ Platform client using official Cursor interfaces. Default transport is `agent -p --output-format stream-json` with Swing/JBUI (方式B). Develop also offers explicit ACP selection for new conversations (#147); fixed-build GUI acceptance is tracked in #152. Check [current implementation](docs/architecture/current-implementation.md) for scope/limits and [requirements](docs/cursor-agent-plugin-requirements.md) before adding features.

<a id="最新の能力比較2026-09-08--114"></a>

Use the [capability matrix](docs/research/cursor-agent-capability-matrix-2026-09-08.md) to enter further capability research. Headless image path references are documented; absence of `--image` in help does not prove no image support. Published headless Skills/Subagents capability, plugin UI/event wiring and live acceptance are separate. Preserve observed immediate print edits/post-hoc Revert and the #66 naming hold. Historical non-support claims apply only to their observed version/path.

For responsibility/event/storage changes, consult the relevant [architecture](docs/architecture/README.md) and current implementation sections. Use [knowledge placement](docs/architecture/knowledge.md) for design decisions and history. Compare old-checkout findings with the latest base and withdraw already-fixed findings.

<a id="uiの言語と見た目の方針"></a>

## Japanese UI policy

The product is intended for Japanese use. When porting Cursor UI, choose language according to what the user must understand to decide:

- Use concise natural Japanese for settings, option explanations, warnings, errors, confirmations and help. Do not import long English explanations unchanged.
- Keep familiar short labels (`Agent`, `Plan`, `Ask`, `Auto`, model names, `MCP`, icons, `@`, `/` and user-specified placeholders). Explain retained English in Japanese tooltips/settings when needed.
- Keep the normal composer simple. Put detailed settings/editing caveats in the chat “…” menu or settings, not permanently beneath the input. Do not widen visual differences with extra warnings.
- Do not translate “standard” into guaranteed prior confirmation. Explain immediate CLI edits and post-hoc Revert accurately.
- Do not translate CLI flags, persisted enums/IDs, model names, paths, raw logs or conversation content. Limit UI language work to changed areas and their adjacent settings flow.

<a id="開発検証の入口"></a>

## Development and verification

Use [Change Impact](docs/development/change-impact.md), shared by CI/hooks/coordinator/ZIP generation. Before pushing, run `python3 scripts/workflow/change_impact.py --run-tests`. Preserve mixed/unknown validation, explicit builds/GUI checks, independent review and Acceptance gate; never use `--no-verify` or disable protection.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
python3 scripts/workflow/change_impact.py --run-tests
./gradlew test          # Product/Kotlin test changes
./gradlew buildPlugin   # Explicit standard Plugin ZIP build
./gradlew runIde        # Requires GUI lease
```

Gradle runtime, Kotlin toolchain and JVM target are 21. `androidStudio("2026.1.1.8")` pins the initial Quail 1 SDK for local/CI/branch ZIP. Local SDK requires explicit `-PuseLocalPlatform=true -PplatformPath=...`; full-build mismatch or inability to verify must fail. See [build details](docs/architecture/current-implementation.md#ビルドと実行環境) for SDK resolution and historical workarounds. Bootstrap/Gradle configure `.githooks`; pre-push checks branch/dirty/fast-forward before selected tests.

Use Pro/Teams for development/verification. Do not revive the old Free-tier `resource_exhausted` blocker unless Free is explicitly reintroduced. Manual install is Settings → Plugins → ⚙ → Install Plugin from Disk, select ZIP, restart. Follow [ZIP/build identity](docs/development/plugin-zip-delivery.md) and GUI lease.

<a id="変更時に保持する制約"></a>

## Constraints to preserve when changing the product

- Tab UUID, chat ID, run token, provider session and OS process have distinct lifetimes. Route to the owning tab; recheck token/generation/disposal on EDT. Stop targets one run; `killActiveProcess()` is all-run cleanup.
- Read editor/VFS/Terminal APIs on EDT; run Git/checkpoint/process startup in the background. Discard late startup after Stop. Keep Terminal optional registration and `LinkageError` protection.
- Print Result and ACP prompt end do not imply physical termination. Exclude preparation/execution from restore; reject uncertain ACP restoration for the project lifetime. Never guess restoration for ISOLATED/unknown roots, outside-root paths, subsequent edits or unsaved content.
- Print can edit immediately with standard permission. Diff/Revert is post-hoc. ACP permissions are not a guarantee of prior approval for all writes; do not offer Revert for ACP diffs without provenance.
- Print deduplication is a full-text replacement heuristic; do not apply it to valid repeated ACP deltas. Parse malformed/unknown wire defensively. Captured completed events and inferred started events have different evidence strength.
- Select ACP only before a new conversation's first send. Use fixed settings; do not silently ignore unsupported settings. Preserve exactly-once replies, cancellation/refusal/disconnection and no automatic resend of failed prompts.
- Preserve XML/enums, default `PermissionMode.ASK_EVERY_TIME`, Plugin ID and internal window/notification IDs. Legacy print history XML is metadata-only. PRINT/ACP bodies use project JSON under [conversation persistence](docs/architecture/conversation-persistence.md) (#44). Real IDE restart remains QA #259; compatibility with old print IDs for ACP is not guaranteed. Body display, provider resume and Revert are separate decisions.
- Keep actual CLI/provider model IDs; do not invent unknown aliases or context capacities. Preserve `New Agent` and #66 naming hold. Separate official image/Skills/subagent capabilities, UI implementation and live acceptance.
- Never render raw Markdown HTML directly. Never move secrets, raw errors or private wire into public logs/permanent instructions. Preserve #146 publication approval and ownership.
- Case JSON is acceptance/evidence authority. Old MV/run/build and synthetic success do not establish new fixed-build GUI passes. Transfer unverified main/GUI work to existing QA.

Reasons, source/tests and unknowns belong in current implementation. Permanent knowledge must support future decisions with clear applicability and traceable evidence/limits; session chronology and fix reports belong in Issue/PR records.

<a id="履歴の参照"></a>

## Historical references

The full old spike, foundation review, PR #18 and UI-tuning history remains in the [fixed pre-change version](https://github.com/shinma06/cursor-in-android-studio/blob/4d1514d8fa6c020d41ad9c0205b9ea24268bef57/CLAUDE.md), not as current instructions. [Knowledge placement](docs/architecture/knowledge.md) records preservation/movement/replacement and superseded decisions. Do not create another history copy.

### Verified CLI behavior

Keep this entry for the existing `AgentNotificationService` comment. Versioned immediate-edit evidence is in the [fixed CLI record](https://github.com/shinma06/cursor-in-android-studio/blob/4d1514d8fa6c020d41ad9c0205b9ea24268bef57/CLAUDE.md#verified-cli-behavior-from-a-live-spike-2026-09); current applicability is in [current implementation](docs/architecture/current-implementation.md#イベント補助cli保存). Do not generalize it to ACP or claim new-build verification.
