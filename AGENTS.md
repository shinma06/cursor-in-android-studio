# Cursor in Android Studio agent contract

AGENTS.md is canonical. CLAUDE.md and Cursor rules are entrypoints; both client Skill paths follow the same contract.

<a id="最上位ミッションとacp-first2026-09-09--134"></a>

## Mission and ACP First

Use [Project Mission](docs/project-mission.md) as the highest project criterion and [ACP First](docs/architecture/cursor-integration.md) for integration design. Reproduce the functionality, workflows, feedback and IDE integration of Cursor's **in-IDE Agent panel**, not the standalone Agents Window or a pixel-perfect copy. The goal includes deep understanding and operation of Android IDE, build, device and execution environments.

Start from the owned guarantee, fixed existing evidence and missing decision fact. Consult current official Cursor/JetBrains/ACP/IDE/MCP sources only where needed for the actual feature or integration choice; no every-feature full competitor survey. Preserve the panel experience and deeper Android integration goal, distinguish shipped facts from design and unverified claims, and do not label existing competitor capabilities unique.

When a new integration contract is actually needed, consider ACP standard, Cursor ACP extensions, IDE APIs, MCP and CLI against the missing guarantee; reuse the existing sufficient path first. Keep accurate direct IDE APIs, CLI-only operations and justified fallbacks. Map structured sessions, tool progress, permissions, questions, Plan/Todo, cancellation and context/usage into the UI; ACP is not just a chat API. Verify availability and separate design policy from shipped implementation. Do not rebuild Cursor's internal agent; separate Cursor-specific, ACP, IDE, MCP, CLI and UI responsibilities.

<a id="github-first-collaboration-2026-09-06-31"></a>
<a id="変更作業の共通契約"></a>

## Shared change contract

Every code, documentation or configuration change needs an Issue, ownership claim, dedicated Issue-numbered branch/worktree and PR, even without a Git request. Read-only advice/review needs no new Issue. Never commit/push directly to main/develop. Preserve unrelated work and unreleased claims. Normally start from origin/develop; GUI-not-required main tooling must meet the [workflow](docs/development/github-workflow.md) conditions.

Choose development roles by the [shared role/model, capability, permission and ownership conditions](docs/development/github-workflow.md#正本と役割), not by model/provider. Use [start-work](.agents/skills/start-work/SKILL.md) to start/resume and [finish-work](.agents/skills/finish-work/SKILL.md) to validate, hand off and finish. The matching `.claude/skills/` entries have the same contract; Cursor also uses `.cursor/rules/loop-engineering.mdc`. PM must remain separate from implementation/fixes and independent review; verify actual model/effort and reconcile existing assignments before dispatch. Reassess finite capacity and the next task at workflow transitions using the same role policy. Select task-specific references through the [workflow reference table](docs/development/github-workflow.md#参照する範囲と進行判断); reuse already-read unchanged material, revisiting changed, missing or conflicting parts. Keep authorization and resumption decisions in that workflow.

Manage target, bounded effects, preservation and result checks yourself; continue authorized work under [risk-managed continuation](docs/development/github-workflow.md#公開操作の承認範囲と失敗の扱い). Before Git push or PR creation/update, read the [standing Git publication permission](docs/development/github-workflow.md#git-publication-permission).

When creating, updating, reviewing or auditing development context, apply the single [context policy](docs/architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件). It covers purpose, language, format, canonical location, loading conditions and evidence preservation. Human-facing reports remain Japanese. Do not copy the policy into each consumer or change externally managed instructions.

For source/comment/Doc, example, configuration/schema or instruction changes and reviews, use the single [comment/Doc contract](docs/development/comment-doc.md) for meaningful boundary contracts, local why/non-obvious how, affected consumers and protected evidence. Apply it to the actual diff and impact; do not require unrelated full-Doc reads or boilerplate for every symbol.

- [Work Management](docs/development/work-management.md): Issues are concrete work, Project is overall tracking, Milestones are outcomes and native relationships are real dependencies/decomposition. Read back registration, Status/Priority and relationships at creation, triage and closure; never invent a parent for classification.
- [Codex execution policy](docs/development/codex-execution-policy.md): read before adding/delegating agents. GPT-6 Astra must not spawn/delegate child agents; use ordinary tools or justified independent top-level sessions. Other models follow the policy's applicability; #135 is the current authority. This does not constrain the product's Cursor Subagent support.
- [PR automation](docs/development/pr-automation.md): stop the writer and confirm clean state before trusted-main v2 enrollment. Require separate-session fixed HEAD/base review and current successful test / PR policy / Agent review / Acceptance gate. Enrollment is not completion: PM/coordinator tracks actual execution, results and next owner. Do not change existing owner/source/registry or PAUSED heartbeat without coordination.
- [GUI coordination](docs/development/gui-coordination.md): desktop actions, installs, restarts and runIde require the host-wide lease and designated capable, authorized operator or human handoff. Worktrees do not isolate IDE state. Use disposable fixtures and identify the loaded build.
- [Governance audit](docs/development/git-governance-audit.md): check existing triggers at start and integration/closure (major Milestone, High Impact, structural problems, 10 meaningful merges). The audit Issue is authoritative; do not add a full audit per PR or a second ledger.

<a id="develop統合とmain昇格2026-09-07--83ユーザー方針"></a>
<a id="統合と完了"></a>

## Integration and completion

Develop permits squash integration with required tests, independent review and complete Case tracking while GUI is pending, environment-blocked or product-failed. Track product failures in dedicated fix Issues. Original acceptance-NG corrections additionally require every original failed guarantee on the corrected frozen candidate/artifact and separate substantive evidence assessment under verification; the ordinary pending-GUI exception does not waive this. Unresolved code findings or failing required tests block integration. Close an implementation Issue only after all implementation acceptance and actual residual-custody transfer/readback (QA only when Cases remain). Never close tracking/research parents or QA merely because a child PR merged.

Main promotion requires **every commit and required Case** of the frozen combined candidate to be covered by current observations or explicitly reviewed, trusted evidence reuse under [verification](docs/verification/README.md), then a merge commit. Preserve original failures and identities; policy/method changes are not new execution. Docs/tooling may target main with concrete GUI-not-required reasons and CLI validation. [Verification JSON](docs/verification/README.md) is authoritative; do not edit generated Markdown separately or substitute old-build, partial or synthetic passes for full candidate acceptance.

Do not create QA for changes with no Cases; keep main-reflection custody explicit in the existing Issue/PR. Track outstanding main reflection and read back QA/Project/Milestone/native relationships under [Work Management](docs/development/work-management.md). Use [current cases](docs/verification/current.md), [loop protocol](docs/loop-engineering/README.md) and [human runbook](docs/loop-engineering/human-runbook.md) for their respective verification work.

Verify merge and cleanup separately. Follow [branch hygiene](docs/development/pr-automation.md#ブランチ残存の判定と完了確認) for owned, stopped, clean Issue resources and remote/local/tracking refs/worktrees. Never delete main/master/develop. Record retained resources, reason, owner and resumption condition. Never force push or bypass hooks/protection. GitHub is the shared authority; keep local paths, hosts and secrets in the private registry only.

<a id="what-this-is"></a>

## Purpose, evidence and team operation

Use [product responsibility](docs/verification/responsibility.md): guarantee → fixed existing evidence → missing fact → minimum sufficient method. Production regressions/file checks and representative native glue have different purposes. Keep all effective Cases, Computer Use obligations, privacy, data/permission/EDT/disposal/process/storage safeguards and real failures. A different ZIP does not by itself require repeating unaffected successful facts; unknown impact still needs evidence. Never claim a gate exists beyond its documented implementation.

For Android Studio/SDK/EDT/process/build/profile/storage/fixture questions, use the conditional [local knowledge route](docs/development/android-studio.md). Reuse qualified same-platform knowledge with fixed source, applicability and local validation; no mandatory full foreign-repository audit.

For team assignments use [team communication](docs/development/team-communication.md) and [worker brief](docs/development/team-worker-prompt.md). Create roles only for ready work through authorized routes. The human liaison handles event-driven communication, not implementation or monitoring. PM receives substantive results and residual ownership; detailed identity/artifact/coverage checks belong to writer, independent reviewer and actual integrator, with PM checking concrete exceptions.

Before stopping unfinished work, apply [stop reconsideration](docs/development/omil-portable.md#reconsider-before-stopping-unfinished-work). Honor human pauses and actual safety/tool/resource boundaries immediately; continue independent permitted work and report saved/unmet state, cause, resolver and resume conditions. No idle jobs or new resident roles.

## Product identity and implementation references

The product is **Cursor in Android Studio**, repository/artifact slug `cursor-in-android-studio`. Preserve `com.cursoragent.plugin`, persisted state/storage names and internal `Cursor Agent` tool-window/notification IDs for upgrades. Use `PluginBrand.NAME` for runtime labels; historical evidence retains its original names.

This is an Android Studio / IntelliJ Platform client using official Cursor interfaces. Default transport is `agent -p --output-format stream-json` with Swing/JBUI (方式B). Develop also offers explicit ACP selection for new conversations (#147); fixed-build GUI acceptance is tracked in #152. Main remains on print. The [fixed develop implementation record](https://github.com/shinma06/cursor-in-android-studio/blob/23807d4bea07d3fd1b390ef4d1c466b9b36f54ca/docs/architecture/current-implementation.md) describes develop, not shipped main. Read [requirements](docs/cursor-agent-plugin-requirements.md) before adding features.

<a id="最新の能力比較2026-09-08--114"></a>

Use the [capability matrix](docs/research/cursor-agent-capability-matrix-2026-09-08.md) to enter further capability research. Headless image path references are documented; absence of `--image` in help does not prove no image support. Published headless Skills/Subagents capability, plugin UI/event wiring and live acceptance are separate. Preserve observed immediate print edits/post-hoc Revert and the #66 naming hold. Historical non-support claims apply only to their observed version/path.

For responsibility/event/storage changes, consult the relevant source/tests and [integration design](docs/architecture/cursor-integration.md) for the actual branch/SHA. Use [knowledge placement](docs/architecture/knowledge.md) for design decisions and history. Compare old-checkout findings with the latest base and withdraw already-fixed findings.

<a id="uiの言語と見た目の方針"></a>

## Japanese UI policy

The product is intended for Japanese use. When porting Cursor UI, choose language according to what the user must understand to decide:

- Use concise natural Japanese for settings, option explanations, warnings, errors, confirmations and help. Do not import long English explanations unchanged.
- Keep familiar short labels (`Agent`, `Plan`, `Ask`, `Auto`, model names, `MCP`, icons, `@`, `/` and user-specified placeholders). Explain retained English in Japanese tooltips/settings when needed.
- Keep the normal composer simple. Put detailed settings/editing caveats in the chat “…” menu or settings, not permanently beneath the input. Do not widen visual differences with extra warnings.
- Do not translate “standard” into guaranteed prior confirmation. Explain immediate CLI edits and post-hoc Revert accurately.
- Do not translate CLI flags, persisted enums/IDs, model names, paths, raw logs or conversation content. Limit UI language work to changed areas and their adjacent settings flow.

<a id="commands"></a>

## Development and verification

Use [Change Impact](docs/development/change-impact.md) across CI/hooks/coordinator/ZIP generation. On a clean committed HEAD, let the verified pre-push hook run the shared checks; do not duplicate the same manual suite immediately beforehand. Dirty early feedback, new inputs or a distinct purpose still need appropriate checks. Knowledge/metadata-only changes may skip heavy code checks; mixed/runtime/build/test/tooling/unknown changes retain necessary validation. Classify new inputs or bundled resources. Independent review, Acceptance and GUI gates remain required.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
python3 scripts/workflow/change_impact.py --run-tests
./gradlew test          # Required for affected Kotlin/product changes
./gradlew buildPlugin   # Explicit Plugin ZIP build
./gradlew runIde        # Requires the GUI lease
```

Bootstrap/Gradle set `core.hooksPath .githooks`; pre-push checks branch/dirty/fast-forward and the selected tests. Never bypass hooks, use `--no-verify` or disable protection. There is a real JUnit 6 suite under `src/test/kotlin`; old claims of no tests are historical. No lint/static-analysis task is configured; verify the actual build before relying on stale documentation.

Gradle runtime, Kotlin toolchain and JVM target are 25. `androidStudio("2026.2.1.8")` pins Rabbit 1 `AI-262.9437.185.2621.16467767` for local/CI/branch ZIP through the standard resolver. Use local SDK only with explicit `-PuseLocalPlatform=true -PplatformPath=...`; reject a full-build mismatch or unverifiable identity. `platformPath` alone must not change the default SDK. Old 2.10.5 URL failures do not justify disabling the current standard resolver; retain the legacy branch-ZIP fallback only for old branches.

Preserve [main-scoped release](docs/development/main-scoped-release.md) (#408) and [ZIP delivery](docs/development/plugin-zip-delivery.md): fixed candidate, Rabbit/JBR25 and the same ZIP. Historical 0.1.0 evidence retains its Quail/JVM21 scope. Do not import all unreleased develop features into that main candidate.

<a id="current-blocker-check-this-before-picking-a-task"></a>

Use Pro/Teams for development/verification; do not revive the old Free-tier `resource_exhausted` blocker unless Free is explicitly reintroduced. Main F-60 image UI is unimplemented; the documented headless path-reference route needs its live spike in #10. There is no automatic update channel. For qualified local-ZIP placement or Plugins GUI installation, use the current operations guidance, actual profile/ZIP/JAR identity, preservation/recovery and GUI lease; do not require a Plugins-screen search for every test.

<a id="architecture現行方式bの実装構造"></a>

## Main print implementation constraints

- Preserve layering: ToolWindowFactory → root/view panels → AgentUiController and its listener/context/history coordinators → project AgentProcessService → defensive stream parser. View components expose callbacks and do not own CLI logic; settings remain a separate persisted side channel.
- Main owns one foreground AgentRun and tracks stopped runs until preparation and physical process termination finish. Stop and project/tool-window disposal invalidate pending starts and stop their processes; checkpoint restore stays blocked while any tracked run can still write. Reject stale callbacks on EDT. Catch process construction/start failures and report through the plugin error UI. Resolve the configured executable first, otherwise existing candidates/PATH; retain `--resume` chat ID handling.
- Rabbit VFS saves can finish after the save API returns. Flush pending writes off EDT/write actions before Git snapshots, CLI disk reads and checkpoint restoration; on failure, stop that operation. Do not promise to save unsaved Documents or freeze later edits.
- Marshal process callbacks to EDT. Read editor/VFS/Terminal APIs on EDT; run process startup, Git and checkpoints in the background. Preserve the MentionResolver split and avoid moving Terminal `invokeAndWait` into background prompt assembly. Use project.basePath/guessProjectDir, not deprecated baseDir.
- Malformed/unknown stream types and shapes must be harmless. Keep parser and tool-payload `runCatching` boundaries. AssistantChunkDeduper is a heuristic, not a protocol guarantee: return full text and replace via `setAssistantText`, never append full cumulative text. Keep tests without calling them live proof.
- Reconcile tool started/completed rows by callId. Revert only if current file content still matches that edit's produced content. Main's checkpoint store uses project.basePath; isolated CLI worktrees need explicit handling and must not be presumed safely restorable. The previously documented ISOLATED/root mismatch is not fixed by this context change.
- Standard print permissions may apply edits immediately; Diff/Revert is post-hoc, never a prior-approval guarantee. Preserve default `PermissionMode.ASK_EVERY_TIME`, existing enum/XML/storage IDs and the three CLI mappings. Do not silently add `--auto-review` or `--force`.
- Keep Terminal optional via its separate descriptor and protect absent classes with `LinkageError`. Preserve disposal of tool-window content. Do not render raw Markdown HTML; escape inline/block HTML and keep error/secret/private wire out of public logs.
- Keep exact CLI model IDs, ambiguous variants and explicit supported capacities. Do not invent context capacities or unknown alias transitions. Keep the configured placeholder and the #66 title-data hold.
- Main ChatHistoryState stores metadata for session resume, not persisted message bodies. Transcript persistence remains separate in #44; develop's live tab views/ACP findings do not prove main restart/history compatibility. Body replay, provider resume and Revert are separate capabilities.
- Fixed-build GUI acceptance remains in the corresponding Case/QA records. Old installed-build observations, a model's reply and synthetic/CLI/unit success do not establish a new GUI pass. #146 private publication/ownership stays protected.

<a id="verified-cli-behavior-from-a-live-spike-2026-09"></a>

### Verified CLI behavior

The [fixed main CLI record](https://github.com/shinma06/cursor-in-android-studio/blob/67348d75264254e39e964915bfc66a98f227764d/CLAUDE.md#verified-cli-behavior-from-a-live-spike-2026-09) preserves the original versions, fields and examples. Keep this entry for existing source references. `--trust` is needed by the observed headless invocation independently of permissionMode; raw-TTY `agent ls/resume` is not a subprocess transcript API. `agent mcp list/list-tools/enable/disable/login` and local model listing are distinct metadata operations, not prompt turns. Captured completed tool events and inferred started args have different evidence strength. Never generalize old print observations to every transport/version or turn missing `--image` help into proof of no image support.

<a id="2026-09-foundation-review"></a>
<a id="2026-09-pr-18-review-pass"></a>
<a id="current-implementation-status-vs-requirements-doc"></a>

## Historical references

The complete [pre-change main instructions](https://github.com/shinma06/cursor-in-android-studio/blob/67348d75264254e39e964915bfc66a98f227764d/CLAUDE.md) retain the M0 spike, foundation/PR #18 review, F-ID implementation inventory and UI-tuning/MV history. Historical installed SHA/pass/blocker statements remain versioned evidence; do not copy them into new acceptance. Relevant constraints remain above. The requirements document owns feature scope; current progress belongs in concrete Issue/PR/QA, Project and native Milestones, not historical #1.

[Verification JSON](docs/verification/README.md) is authoritative for current Cases; the old manual-verification matrix/run is historical detail and must not be separately updated as a new candidate result. The #29 loop infrastructure does not finish pending product QA. [Knowledge placement](docs/architecture/knowledge.md) records the main port and shared context rules without duplicating the history.
