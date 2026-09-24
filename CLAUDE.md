# CLAUDE.md

Development-agent instructions for this repository. `AGENTS.md` remains a symlink to this file, so both entry points have the same contract.

<a id="最上位ミッションとacp-first2026-09-09--134"></a>

## Mission and ACP First

Use [Project Mission](docs/project-mission.md) as the highest project criterion and [ACP First](docs/architecture/cursor-integration.md) for integration design. Reproduce the functionality, workflows, feedback and IDE integration of Cursor's **in-IDE Agent panel**, not the standalone Agents Window or a pixel-perfect copy. The goal includes deep understanding and operation of Android IDE, build, device and execution environments.

For each new feature, compare the latest official Cursor in-IDE panel and the strongest available JetBrains AI Assistant + Cursor ACP + IntelliJ / Android Studio integration + IntelliJ MCP Server + MCP tools configuration. Record equivalent-or-better UX and the additional value of direct IDE integration. Do not call existing competitor capabilities unique features.

Consider ACP standard → Cursor ACP extensions → IDE APIs → MCP → CLI → unstructured output parsing. Keep accurate direct IDE APIs, CLI-only operations and justified fallbacks. Map structured sessions, tool progress, permissions, questions, Plan/Todo, cancellation and context/usage into the UI; ACP is not just a chat API. Verify availability and separate design policy from shipped implementation. Do not rebuild Cursor's internal agent; separate Cursor-specific, ACP, IDE, MCP, CLI and UI responsibilities.

<a id="github-first-collaboration-2026-09-06-31"></a>
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

<a id="develop統合とmain昇格2026-09-07--83ユーザー方針"></a>
<a id="統合と完了"></a>

## Integration and completion

Develop permits squash integration with required tests, independent review and complete Case tracking while GUI is pending, environment-blocked or product-failed. Track product failures in dedicated fix Issues. Unresolved code findings or failing required tests block integration. Close an implementation Issue only after all implementation acceptance and bidirectional QA transfer/readback. Never close tracking/research parents or QA merely because a child PR merged.

Main promotion requires **every commit and required Case** of the fixed develop candidate to pass on the same identified build, then a merge commit. Docs/tooling may target main with concrete GUI-not-required reasons and CLI validation. [Verification JSON](docs/verification/README.md) is authoritative; do not edit generated Markdown separately or substitute old-build, partial or synthetic passes for full candidate acceptance.

Track outstanding main reflection even without GUI and read back QA/Project/Milestone/native relationships under [Work Management](docs/development/work-management.md). Use [current cases](docs/verification/current.md), [loop protocol](docs/loop-engineering/README.md) and [human runbook](docs/loop-engineering/human-runbook.md) for their respective verification work.

Verify merge and cleanup separately. Follow [branch hygiene](docs/development/pr-automation.md#ブランチ残存の判定と完了確認) for owned, stopped, clean Issue resources and remote/local/tracking refs/worktrees. Never delete main/master/develop. Record retained resources, reason, owner and resumption condition. Never force push or bypass hooks/protection. GitHub is the shared authority; keep local paths, hosts and secrets in the private registry only.

<a id="what-this-is"></a>

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

Use [Change Impact](docs/development/change-impact.md) across CI/hooks/coordinator/ZIP generation. Run the shared command before pushing. Knowledge/metadata-only changes may skip heavy code checks; mixed/runtime/build/test/tooling/unknown changes retain necessary validation. Classify new inputs or bundled resources. Independent review, Acceptance and GUI gates remain required.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
python3 scripts/workflow/change_impact.py --run-tests
./gradlew test          # Required for affected Kotlin/product changes
./gradlew buildPlugin   # Explicit Plugin ZIP build
./gradlew runIde        # Requires the GUI lease
```

Bootstrap/Gradle set `core.hooksPath .githooks`; pre-push checks branch/dirty/fast-forward and the selected tests. Never bypass hooks, use `--no-verify` or disable protection. There is a real JUnit5 suite under `src/test/kotlin`; old claims of no tests are historical. No lint/static-analysis task is configured; verify the actual build before relying on stale documentation.

Gradle runtime, Kotlin toolchain and JVM target are 21. `androidStudio("2026.1.1.8")` pins Quail 1 `AI-261.23567.138.2611.15503007` for local/CI/branch ZIP through the standard resolver. Use local SDK only with explicit `-PuseLocalPlatform=true -PplatformPath=...`; reject a full-build mismatch or unverifiable identity. `platformPath` alone must not change the default SDK. Old 2.10.5 URL failures do not justify disabling the current standard resolver; retain the legacy branch-ZIP fallback only for old branches.

Preserve [main-scoped release](docs/development/main-scoped-release.md) (#408) and [ZIP delivery](docs/development/plugin-zip-delivery.md): fixed candidate, both IDE/JBRs and the same ZIP. Do not import all unreleased develop features into that main candidate.

<a id="current-blocker-check-this-before-picking-a-task"></a>

Use Pro/Teams for development/verification; do not revive the old Free-tier `resource_exhausted` blocker unless Free is explicitly reintroduced. Main F-60 image UI is unimplemented; the documented headless path-reference route needs its live spike in #10. Manual install is Settings → Plugins → ⚙ → Install Plugin from Disk, select ZIP, restart; there is no automatic update channel. Observe the GUI lease.

<a id="architecture現行方式bの実装構造"></a>

## Main print implementation constraints

- Preserve layering: ToolWindowFactory → root/view panels → AgentUiController and its listener/context/history coordinators → project AgentProcessService → defensive stream parser. View components expose callbacks and do not own CLI logic; settings remain a separate persisted side channel.
- Main owns one active OSProcessHandler; starting a prompt replaces the in-flight process. Stop and project/tool-window disposal must terminate it. Catch process construction/start failures and report through the plugin error UI. Resolve the configured executable first, otherwise existing candidates/PATH; retain `--resume` chat ID handling.
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
