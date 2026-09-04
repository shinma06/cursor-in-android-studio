# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
`AGENTS.md` is a symlink to this file, so any agent reading either name gets identical content.

## Multi-agent collaboration model

This repo is worked on by more than one AI coding agent — Claude Code and Cursor's own
`cursor-agent` (running under a Teams plan, used by the plugin's actual target users) — with **no
fixed role split**. Whichever agent is active at a given moment picks up whatever work is next;
neither has visibility into the other's local session history or scratch state, so **GitHub Issues
on this repo is the shared source of truth for task status**, not any agent-local plan/todo file.
There's also no branch-protection/PR workflow here — everything lands on `main` directly, which
matters because two agents can be live on this repo at the same time. Concretely, every session
should:

1. **Before starting work**: check the tracking issue (#1) and the specific milestone issue for
   current status, then `git pull` (or `git fetch` + check `git log origin/main` against local
   HEAD) — another agent may have pushed since your last sync, and starting from a stale HEAD is
   how you end up redoing work or reverting someone else's fix without realizing it.
2. **Claim the work before doing it**: leave a short comment on the issue (e.g. "Starting M4") so
   a concurrent session doesn't pick up the same issue. This is the only coordination signal that
   exists — issues currently carry no labels/assignees, so a comment is the actual mechanism, not
   just a nicety.
3. **Before pushing**: `git pull` again if meaningful time has passed since step 1. If a push is
   rejected as non-fast-forward, that means someone else landed work in the meantime — stop and
   reconcile (merge/rebase, re-read what changed) rather than force-pushing over it.
4. **After finishing**: update the issue's checklist and leave a closing comment, and update
   `CLAUDE.md`/the requirements doc if what you built changes the "current implementation status"
   or "verified CLI behavior" sections — those are read as ground truth by the next session
   (agent or human), so a stale claim there is actively worse than no claim at all.
5. **When the change needs human-only verification** (runIde, Swing UI, OS notifications, etc.):
   add or update rows in [`docs/manual-verification/matrix.md`](docs/manual-verification/matrix.md)
   instead of dumping a long QA checklist only in chat. See [`.cursor/rules/manual-verification.mdc`](.cursor/rules/manual-verification.mdc).

If you're a fresh agent with zero context on this repo: read this whole file, then
`docs/cursor-agent-plugin-requirements.md`, then the open GitHub issues, in that order, before
touching any code — the "Current implementation status" and "Verified CLI behavior" sections below
exist specifically so you don't have to re-derive them by reading every source file.

`.claude/skills/start-work` and `.claude/skills/finish-work` encode steps 1–2 and 3–4 above as
Claude Code skills, and `.claude/settings.json` allowlists some read-only commands this repo's
work pattern uses often. Both are Claude-Code-specific (Cursor's agent can't read `.claude/`) — if
you're Cursor's agent, the numbered rules above are the same information, just not automated for
you.

## What this is

An Android Studio (IntelliJ Platform) plugin that reproduces Cursor IDE's Agent tab as a native
tool window. It works by shelling out to the `cursor-agent` CLI (`agent -p --output-format
stream-json`) as a subprocess and rendering the JSON-Lines event stream in a custom Swing/JBUI
chat UI. The CLI is treated as an opaque black box — no protocol/API integration, just process
control and defensive JSON parsing. This is referred to as "方式B" (native UI approach) in
`docs/cursor-agent-plugin-requirements.md`, which is the full requirements/design doc and the
source of truth for feature scope and rationale — check it before adding features.

## Current blocker (check this before picking a task)

The verification account used for the **original M0 spike** was Cursor **Free tier**, which hit
`resource_exhausted` on chat turns. That blocker is **resolved for Teams-plan sessions** — verified
2026-09-04 with `~/.local/bin/agent` logged in as a Teams account: plain chat, file edits, and
shell tool calls all succeed and produce rich `tool_call` events (`readToolCall`/`editToolCall`/
`shellToolCall` with `subtype` `started`/`completed`).

**Critical CLI behavior for M4 design (verified live):** in headless subprocess mode, file edits
are **applied immediately by the CLI even without `--force`** (`permissionMode: default`). The
plugin cannot intercept writes before they happen — F-30/F-31 are implemented as **post-hoc diff
view + Revert** (restore `beforeFullFileContent` from the completed `editToolCall` event, or use
checkpoints), not pre-apply approval gating.

Still out of scope until CLI support appears: F-60 image attachment (`agent --help` has no image flag, verified 2026-09-04).

## Commands

```bash
# Gradle itself needs JDK 17+; Kotlin compilation uses a JDK 21 toolchain
# (auto-provisioned via the foojay-resolver plugin, independent of JAVA_HOME).
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"

./gradlew buildPlugin   # produces build/distributions/cursor-agent-plugin-<version>.zip
./gradlew runIde        # launches a sandbox Android Studio instance with the plugin installed
./gradlew test          # runs the JUnit5 unit tests under src/test/kotlin — run this before every push
```

**This is enforced automatically, not just a convention**: any `./gradlew <task>` invocation configures
`git config core.hooksPath .githooks` (see the top of `build.gradle.kts`), and `.githooks/pre-push`
runs `./gradlew test` and blocks the push if it fails. This is deliberately git-level rather than a
Claude-Code-specific hook, so it applies no matter which agent (or human) is pushing. Don't rely on
it as your only check, though — run `./gradlew test` yourself before pushing so you find out about a
failure before the hook does, and never reach for `git push --no-verify` to route around a real
failure (it exists for genuine edge cases, not for skipping a red test).

**Second, independent safety net: GitHub Actions CI** (`.github/workflows/ci.yml`) runs the test
suite on every push/PR against `main`, so a `--no-verify` push (or any push from an environment
where the local hook never got installed) still gets caught. It resolves the IntelliJ platform
dependency differently than local dev does — worth knowing before touching either file:

- **Local dev** uses `local(providers.gradleProperty("platformPath"))` in `build.gradle.kts`,
  pointing at a real Android Studio install on the machine (`gradle.properties`).
- **CI** downloads Android Studio directly in a workflow step (cached by version+codename) and
  passes the extracted path in as `-PplatformPath`, reusing that exact same `local()` code path
  rather than a separate resolution mechanism.
- **Why not the plugin's own `androidStudio()` dependency helper**, which exists for exactly this
  case: it constructs a broken download URL as of `org.jetbrains.intellij.platform` v2.10.5.
  Verified by hand — the real artifact (`android-studio-<codename>-linux.tar.gz`) exists and
  downloads fine over plain HTTP at
  `https://redirector.gvt1.com/edgedl/android/studio/ide-zips/<version>/android-studio-<codename>-linux.tar.gz`
  (codename, e.g. `quail3-patch1`, not the version string, in the filename — full list at
  https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html), but Gradle's own
  resolution 404s on it regardless of version tried, with or without a `google()` repository added.
  Upgrading the plugin past 2.10.5 to check for a fix wasn't attempted beyond 2.18.1, which requires
  Gradle 9 (this project is on 8.13) — a bigger, separate migration, not attempted here. If a future
  change wants to revisit `androidStudio()` instead of the direct-download workaround, that Gradle
  9 migration is the prerequisite, not just a version bump in `plugins {}`.
- If `ci.yml`'s `ANDROID_STUDIO_VERSION`/`ANDROID_STUDIO_CODENAME` ever need to move to a newer
  release, look both values up together from the releases-list page above — the codename doesn't
  follow an obvious pattern from the version number alone (e.g. version `2026.1.4.7`'s codename is
  `quail4`, not `quail4-patch1`, while `2026.1.3.8`'s is `quail3-patch1`).

**Correction (2026-09, found by an onboarding dry-run — see GitHub issue #13)**: this section used
to say no test source set exists. That was true when it was written but has been stale since M1
(commit `f60c7f6`) added `src/test/kotlin` and JUnit5 wiring in `build.gradle.kts`. There is now a
real test suite (`GitSnapshotStoreTest`, `MarkdownRendererTest`, `MentionTokenExtractorTest`,
`ModelListParserTest`, `AssistantChunkDeduperTest`, `AgentSettingsStateTest`) — run `./gradlew test`
and keep it green. There is still no lint/static-analysis task configured. If you're reading a
stale copy of this file (cached context, an old checkout), don't trust either claim — run
`./gradlew test` yourself to check, and if this note itself looks wrong, the code is more likely to
be right than a doc that says "don't assume X exists."

`gradle.properties` sets `platformPath`, which must point at a local Android Studio install
(`.../Android Studio.app/Contents`) for `buildPlugin`/`runIde` to resolve the platform SDK. This
is machine-specific; see the two example paths already commented in that file (brew cask default
vs. Caskroom versioned path).

Manual install (no auto-update channel): Settings → Plugins → ⚙ → Install Plugin from Disk →
select the built zip → restart IDE.

## Architecture

Layered, unidirectional: `ToolWindowFactory` → root panel → `AgentUiController` (mediator) →
`AgentProcessService` (subprocess + stream parsing) → `StreamJsonParser`. Settings are a separate
persisted side-channel read by both the service and the UI.

- **`toolwindow/CursorAgentToolWindowFactory`** — registers the "Cursor Agent" right-anchored
  tool window (see `plugin.xml`), instantiates `AgentToolWindowRootPanel`.
- **`ui/AgentToolWindowRootPanel`** — wires together `AgentHeaderBar` (NORTH), `ChatTimelinePanel`
  (CENTER), `ComposerPanel` (SOUTH), and constructs the `AgentUiController` that owns the wiring
  between them.
- **`ui/AgentUiController`** — the only class that talks to `AgentProcessService`. On send: pushes
  the user bubble, prepends active-file/selection context (`buildActiveFileContext`, via
  `FileEditorManager`), and registers an `AgentProcessListener` per request. All UI mutation from
  the listener callbacks is marshaled onto the EDT via `runOnEdt` — the service invokes callbacks
  from process-output threads, so never touch Swing state directly in a listener override.
- **`service/AgentProcessService`** (project-level `@Service`) — builds the `GeneralCommandLine`
  for `agent`, owns the single `OSProcessHandler` at a time (`activeHandler`, an `AtomicReference`;
  a new prompt kills any in-flight process first), and tracks `chatId` across turns to pass
  `--resume`. `resolveAgentExecutable` searches a fixed candidate list
  (`/usr/local/bin/agent`, `/opt/homebrew/bin/agent`, `~/.local/bin/agent`, then bare `agent` on
  `PATH`) unless a path is explicitly configured in settings. `dispose()` kills the process —
  this is load-bearing for not leaking zombie CLI processes when the tool window/project closes.
- **`parser/StreamJsonParser`** — line-oriented (JSON Lines), maps each line to a `StreamEvent`
  sealed interface (`SessionInit`, `AssistantDelta`, `ThinkingDelta`, `ToolCall`, `ToolCallStarted`,
  `ToolCallCompleted`, `Result`, `Unknown`). Must stay defensive: unrecognized `type` values or
  malformed lines become `Unknown` or are silently dropped rather than throwing, because the CLI's
  stream-json schema is explicitly unstable across `cursor-agent` versions (see requirements doc
  §7, §11). Both `mapEvent()` (in `parseLine`) and `ToolCallPayloadParser.parse()` are wrapped in
  `runCatching` for this reason — a 2026-09 review found the original `tool_call` parsing did an
  unguarded `JsonElement.asJsonObject` cast that could throw and abort the rest of that output
  chunk; keep new event-shape parsing similarly defensive rather than trusting the shape.
- **`parser/AssistantChunkDeduper`** — guesses whether `AssistantDelta` events are incremental or
  cumulative/repeated against a running buffer, and returns the **full** text to display (never a
  fragment) — `AgentTurnListenerFactory.onAssistantDelta` calls `timeline.setAssistantText(full)`
  (replace), not append. **This return-full-text contract is load-bearing**: the original PR #18
  implementation returned a fragment for the "cumulative resend" case while the caller appended it,
  which duplicated/garbled the assistant bubble whenever the CLI resent a corrected message — fixed
  in the 2026-09 review pass below. Don't reintroduce an append-based caller without also reverting
  `dedupe()` to return fragments consistently.
  **Correction (2026-09 codebase review, see GitHub issue #2)**: this was inherited from the
  original pre-existing code with no verification behind it, and CLAUDE.md previously
  (incorrectly) described it as based on "observed" CLI behavior — the M0 spike never actually
  got a real `assistant` event before hitting `resource_exhausted`. Treat it as an unverified
  guess sitting on the critical path of chat rendering, not a confirmed fact. `AssistantChunkDeduperTest`
  pins down its current behavior so a future change is at least deliberate, not a spec for
  correctness.
- **`settings/AgentSettingsState`** (application-level `@Service` + `PersistentStateComponent`,
  storage `cursor-agent-settings.xml`) — holds `agentExecutablePath`, `selectedModel`, `mode`
  (`AgentMode`: ASK/AGENT/PLAN, each mapping to a `--mode` CLI value or `null` for default agent
  mode), and `permissionMode` (`PermissionMode`: ASK_EVERY_TIME/AUTO_REVIEW/RUN_EVERYTHING, mapping
  to no flag / `--auto-review` / `--force`). **Must default to `ASK_EVERY_TIME`** — a deliberate
  safety requirement from the requirements doc, not an oversight, to prevent unattended file
  changes. This replaced an earlier binary `forceEnabled` toggle once research showed the CLI's
  real approval model is 3-way (requirements doc F-22/F-24, 2026-09).
- **`ui/composer/`, `ui/header/`, `ui/timeline/`** — plain Swing/JBUI view components with no CLI
  knowledge; they expose callbacks (`onSend`, `onNewChat`) and mutation methods
  (`setAssistantText`, `showStatus`, etc.) that the controller drives. `ChatTimelinePanel` tracks
  in-progress tool-call rows by `callId` (`activeToolCallRows`) so a `completed` event's card
  replaces the `started` row instead of leaving a stale duplicate — don't add a tool-call row type
  without going through that map, or it'll orphan rows the same way the pre-fix code did (2026-09
  PR #18 review, see below).

## Verified CLI behavior (from a live spike, 2026-09)

`docs/cursor-agent-plugin-requirements.md` §13 lists several `[要検証]` unknowns about the real
`cursor-agent` CLI. These are now confirmed against a live install (`agent --version` →
`2026.09.02-c22c1a3`):

- **`--trust` is required on every invocation.** A workspace the CLI hasn't seen before blocks on
  an interactive "Workspace Trust Required" prompt with no TTY to answer it, so any subprocess run
  (like this plugin's) fails outright unless `--trust`/`--yolo`/`-f` is passed. `AgentProcessService
  .buildCommandLine` now always passes `--trust` unconditionally — opening the project in the IDE
  is already the user's trust decision, independent of `permissionMode` (which still separately
  controls `--auto-review`/`--force`, i.e. how much tool-call approval is auto-granted).
- **`agent ls` / `agent resume` (past-session picker) are Ink-based interactive TUIs that require
  raw-mode TTY** — they hard-fail (`Raw mode is not supported`) when run through a plain
  subprocess pipe like `OSProcessHandler`/`GeneralCommandLine`. F-50 (past chats list) cannot shell
  out to these; it must track `chatId`s itself (plugin-side persistent state), which is what's
  planned.
- **`agent mcp` has real subcommands**: `list`, `list-tools <id>`, `enable <id>`, `disable <id>`,
  `login <id>`. F-71 (MCP enable/disable) doesn't need to hand-edit `.cursor/mcp.json` — shell out
  to `agent mcp enable`/`disable` instead.
- **Confirmed `system`/`init` event fields**: `type`, `subtype`, `apiKeySource`, `cwd`,
  `session_id`, `model`, `permissionMode` — matches what `StreamJsonParser.SessionInit` already
  reads. Additional event `type`s observed and currently (harmlessly) falling into
  `StreamEvent.Unknown`: `"user"` (echoes the sent prompt back), `"connection"` (subtype
  `reconnecting`/`reconnected`), `"retry"` (subtype `starting`) — these are connection-retry
  telemetry, safe to keep ignoring.
- **CLI flags verified 2026-09-04**: `--sandbox enabled|disabled`, `-w/--worktree` (no `--image` in `--help`).
- **Scoped out (CLI, 2026-09-04)**: F-17 `@Chats` (no non-TTY transcript API; same TTY constraint as `agent ls`); F-60 image attach (no `--image` flag in `--help`).
- **Verified 2026-09-04 (Teams plan)**: `assistant` events under `--stream-partial-output` mix
  incremental fragments and cumulative resends; `tool_call` uses `subtype` `started`/`completed`
  with nested `readToolCall`/`editToolCall`/`shellToolCall` payloads. Completed `editToolCall`
  includes `beforeFullFileContent`, `afterFullFileContent`, `diffString`, line counts. Completed
  `shellToolCall` includes `stdout`/`stderr`/`interleavedOutput`/`exitCode`. File writes happen
  immediately in headless mode even with default permission mode (no `--force`).
  **Caveat**: only the `completed` shape was actually captured in a live fixture
  (`src/test/resources/stream-json-fixtures/`); `ToolCallPayloadParser`'s handling of the
  `started` subtype (reading `args.path`/`args.command`) is inferred from that, not
  independently confirmed against a captured `started` event — don't treat it as equally
  verified until one is captured.

## 2026-09 foundation review

Before building further, the pre-existing codebase (the original `6b6eb2f` commit, before any of
this work) got a dedicated architecture review, and the requirements doc got a comprehensive
web-research pass against Cursor's actual current Agent panel/CLI capabilities — see GitHub issue
#2 for the full findings. Highlights:

- **Fixed**: `AgentProcessService.sendPrompt` didn't catch `OSProcessHandler` construction failures
  (e.g. the `agent` executable missing entirely) — this threw a raw platform exception instead of
  going through the plugin's own error UI. Now wrapped, routes to `listener.onError`.
- **Fixed**: process start + checkpoint snapshot + `@git-diff` resolution used to run synchronously
  on the EDT (`sendPrompt`, `CheckpointService`, `MentionResolver`). All of that is now on a pooled
  thread; `MentionResolver` is split into `buildFileAndFolderContext` (EDT-safe VFS reads) and
  `buildShellBackedContext` (spawns `git`, call off-EDT) for this reason — don't merge them back
  without keeping that split.
- **Fixed**: deprecated `project.baseDir` replaced with `project.basePath`/`guessProjectDir(project)`
  throughout.
- **Fixed**: no way to cancel a hung/long-running turn — `ComposerPanel`'s send button now doubles
  as a Stop button while a turn is running (`setRunning`/`onStop`), calling
  `AgentProcessService.killActiveProcess()`.
- **Corrected, not fixed** (can't fix without live CLI data): `AssistantChunkDeduper`'s dedup
  heuristic was never actually verified against real `assistant` events — CLAUDE.md previously
  overstated this as "observed" behavior; see that class's doc comment.
- **Known backlog, not yet addressed**: further `AgentUiController` decomposition is largely done —
  `AgentTurnListenerFactory`, `PromptContextBuilder`, and `PastChatsCoordinator` now own the per-turn
  listener, prompt assembly, and past-chats popup (#11, 2026-09). Settings page and tool-window-close
  cleanup are done.
  F-23 sandbox: basic `--sandbox enabled|disabled` toggle in Composer ⋯ menu (`SandboxMode`).
  F-52 worktree: basic `-w` toggle (`WorktreeMode.ISOLATED`); changes land under `~/.cursor/worktrees/`.
  **Known gap**: `CheckpointService`/`GitSnapshotStore` (F-40–44) is hard-wired to `project.basePath`
  and has no awareness of `WorktreeMode.ISOLATED` — when it's on, the CLI's edits land in the
  isolated worktree, not `project.basePath`, so checkpoint rollback silently stops matching what
  the agent actually changed. Not yet fixed; needs either disabling checkpoint rollback while
  isolated-worktree mode is active, or pointing `GitSnapshotStore` at the worktree path.
- **Requirements doc**: was missing several real Cursor Agent-panel/CLI capabilities entirely —
  see `docs/cursor-agent-plugin-requirements.md` §6.2/§6.3/§6.6/§6.9 for what got added (`@Branch`,
  `@Chats`, the 3-way permission model + `--auto-review`, worktrees, subagents/custom modes as
  open questions) and what got corrected (F-60 image-attach support is contradicted between the CLI
  changelog and the parameters reference — don't assume a flag name until verified live; F-22's old
  binary force framing undersold the real approval model, now F-24).

## 2026-09 PR #18 review pass

PR #18 (the M4/M5/M9/backlog consolidation described below) got a multi-angle code review before
merge (4 finder passes + manual verification), which found and fixed several real bugs on paths it
added — all fixed on top of the original PR before it landed on `main`:

- `AssistantChunkDeduper` returning a fragment for the "cumulative resend" case while the caller
  appended it — garbled/duplicated the assistant bubble. Fixed by making `dedupe()` always return
  the full text and the caller always replace (`setAssistantText`), not append. See that class's
  entry in "Architecture" above.
- `ChatTimelinePanel` never reconciling a tool call's `started` row with its `completed` card —
  every tool call left a permanent duplicate row. Fixed via `activeToolCallRows` (keyed by
  `callId`). See that class's entry in "Architecture" above.
- `ToolCallPayloadParser` doing an unguarded JSON cast that could throw and abort parsing of the
  rest of an output chunk — violated the parser package's own defensive-parsing rule. Fixed with
  `runCatching`, plus a second safety net around `mapEvent()` in `StreamJsonParser.parseLine`.
- `DiffViewerHelper.revertFileContent` clobbering the file unconditionally — reverting a stale
  `FileEditCard` (the file changed again since, by a later agent edit or the user) silently
  discarded that newer content. Fixed: it now refuses (returns `false`) unless the file's current
  content still matches what that specific edit produced.
- `plugin.xml` declaring `org.jetbrains.plugins.terminal` as a required dependency — disabling the
  bundled Terminal plugin would fail the *entire* Cursor Agent plugin to load over one `@terminal`
  mention feature. Fixed: `optional="true"` + `cursor-agent-terminal.xml`, and
  `TerminalOutputReader` now also catches `LinkageError` (a disabled/absent Terminal plugin throws
  that, not a plain `Exception`, when its classes are referenced).
- `@terminal` mention resolution ran on the background prompt-assembly thread and blocked it on
  `invokeAndWait` back onto the EDT (the Terminal API needs the EDT) — reintroducing the EDT
  dependency `MentionResolver`'s file/folder-vs-git split exists to avoid. Fixed by moving
  `@terminal` into `buildFileAndFolderContext` (the EDT-run half) instead of
  `buildShellBackedContext` (the background half, now git-only).
- `AgentNotificationService`'s tool-call notification text claimed "approval may be required,"
  contradicting this same PR's own finding that the headless CLI applies edits immediately.
  Reworded (code and Settings page checkbox label).

Not fixed in this pass (documented instead, since each needs a larger design call, not a
mechanical fix): the `WorktreeMode.ISOLATED`/`CheckpointService` mismatch (see "Known gap" above);
tool-call notifications still fire once per call with no debouncing; `ToolCallPayloadParser`'s
`started`-subtype field reads are still unverified against a captured live event (see "Verified
CLI behavior" above). `./gradlew test` was green throughout (40 tests after this pass, 3 new:
`StreamJsonParserTest`'s malformed-input case, two `ToolCallPayloadParserTest` defensive-parsing
cases).

## Current implementation status vs. requirements doc

The requirements doc (`docs/cursor-agent-plugin-requirements.md`) defines the full MVP/P2/P3 scope
with feature IDs (F-01, F-02, ...); the live milestone tracker is **the tracking issue's own
checklist and its child issues** (GitHub issue #1 — see "Multi-agent collaboration model" above),
which is the up-to-date source for what's done. Deliberately not naming a specific issue-number
range here: issue #1's checklist has drifted out of sync with a hardcoded range in this file at
least once already (this file said "#1–#10" after #11/#12/#13 already existed) — read #1 itself
rather than trusting a number written into this doc at some point in the past.

Implemented: prompt send/stream/history/new-chat (F-01–03, F-05), active-file auto-context (F-15),
mode control (F-20), the 3-way permission model (F-22/F-24 redesign: `PermissionMode`, a Stop
button on the composer while a turn is running), workspace pinning (F-51), Markdown rendering for assistant
messages (`AssistantMessageBubble`/`MarkdownRenderer`, commonmark-based, HTML-inline/HTML-block
nodes rendered as escaped text rather than passed through raw — see that file's doc comment for
why), the git-stash-create-based checkpoint/rollback system (F-40–44: `CheckpointService` +
`GitSnapshotStore` + `CheckpointHistoryState`, rollback icon on `UserMessageBubble`), `@mention`
context injection for files/folders/git-diff/docs/web hints (F-10–12, F-14: `ComposerPanel`'s
`EditorTextField` + `MentionPopupController` + `MentionResolver`), context compression (F-06: an
overflow-menu item that just sends `/summarize`), dynamic model selection (F-21:
`AgentProcessService.listModels()` + `ModelListParser`, loaded async on tool-window open), and MCP
server listing/enable/disable (F-70/F-71: `McpServersDialog`, `agent mcp list/enable/disable`).

**Important finding that unblocked F-21/F-70/F-71**: `--list-models` and `agent mcp list/enable/
disable` are local metadata operations, not chat turns — they don't consume the same
per-conversation quota `sendPrompt` does, confirmed by running them successfully while the Free-tier
account used for the M0 spike was still hitting `resource_exhausted` on actual prompts. They run
synchronously via `ExecUtil.execAndGetOutput` (`AgentProcessService.runAgentCommandSync`), not
through the streaming `OSProcessHandler` path `sendPrompt` uses.

Past-chats tracking (F-50) is also implemented: `ChatHistoryState` records `(chatId,
firstPromptPreview, lastUpdatedMs)` on every turn (since `agent ls`/`agent resume` need a raw TTY
and can't be shelled out to — see the "Verified CLI behavior" section), and `AgentHeaderBar`'s
history button opens a popup to resume one. Resuming only continues the *session* for the next
turn — the CLI has no way to hand back a past session's transcript, so the timeline is cleared
rather than replayed; this is a known, permanent limitation rather than a TODO.

**Implemented (2026-09, M4/M5)**: tool-call timeline cards (F-32: read/edit/shell started +
completed), file-edit cards with IDE Diff Viewer + Revert (F-30/F-31 as post-hoc model — CLI
auto-applies edits in headless mode), `ToolCallPayloadParser` + stream-json fixtures from live CLI.
F-70 MCP list uses `McpListParser` (`id: status` per line, verified 2026-09-04).

**Implemented (2026-09, M9/backlog)**: desktop notifications on turn complete/tool-call start
(`AgentNotificationService`, settings toggles in **Settings → Tools → Cursor Agent**), F-13
`@terminal` mention (`TerminalOutputReader` via Reworked Terminal API), F-16 `@branch` mention
(`BranchDiffBuilder`), settings page for `agentExecutablePath` and notification prefs
(`AgentSettingsConfigurable`), tool-window-close process cleanup (`Disposer` on tool window
`Content`).

**F-13 (`@Terminal` mention)**: implemented via `TerminalToolWindowTabsManager` +
`TerminalView.outputModels` (Reworked Terminal API). Requires an open Terminal tool window tab;
returns a helpful placeholder if none is available.

**Manual verification (runIde, Swing UI)**: tracked in
[`docs/manual-verification/matrix.md`](docs/manual-verification/matrix.md) — Enter/Shift+Enter,
`@` popup, diff cards, notifications, etc. Agents must keep that file current; humans fill in
`Status` / `Verified by` / `Date` after `./gradlew runIde`.
