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

If you're a fresh agent with zero context on this repo: read this whole file, then
`docs/cursor-agent-plugin-requirements.md`, then the open GitHub issues, in that order, before
touching any code — the "Current implementation status" and "Verified CLI behavior" sections below
exist specifically so you don't have to re-derive them by reading every source file.

## What this is

An Android Studio (IntelliJ Platform) plugin that reproduces Cursor IDE's Agent tab as a native
tool window. It works by shelling out to the `cursor-agent` CLI (`agent -p --output-format
stream-json`) as a subprocess and rendering the JSON-Lines event stream in a custom Swing/JBUI
chat UI. The CLI is treated as an opaque black box — no protocol/API integration, just process
control and defensive JSON parsing. This is referred to as "方式B" (native UI approach) in
`docs/cursor-agent-plugin-requirements.md`, which is the full requirements/design doc and the
source of truth for feature scope and rationale — check it before adding features.

## Current blocker (check this before picking a task)

The verification account used so far is Cursor **Free tier**, which hits `resource_exhausted` on
any prompt that actually reaches the model (plain chat, file edits, tool calls) — confirmed
persistent, not transient, across repeated retries. `--list-models` and `agent mcp
list/enable/disable` are unaffected (they're local metadata operations, not chat turns — see
"Verified CLI behavior" below). This blocks GitHub issues **#6 (M4, diff Apply/Reject)**, **#7 (M5,
tool-call/shell output display)**, and the image-attachment half of **#10 (M8)** — don't start
those without either a working paid-tier/quota-recovered account to verify against, or a clear
signal from whoever's running the session that they have one. Everything else is unblocked; issues
**#11** and **#12** are explicitly ranked cheapest-first for exactly this reason. This paragraph is
the single most load-bearing fact for "what can I actually work on right now" — it used to live
only in GitHub issue #1, which an onboarding dry-run (issue #13) found easy to miss if you assume
this file is self-contained.

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
  sealed interface (`SessionInit`, `AssistantDelta`, `ThinkingDelta`, `ToolCall`, `Result`,
  `Unknown`). Must stay defensive: unrecognized `type` values or malformed lines become `Unknown`
  or are silently dropped rather than throwing, because the CLI's stream-json schema is explicitly
  unstable across `cursor-agent` versions (see requirements doc §7, §11).
- **`parser/AssistantChunkDeduper`** — guesses whether `AssistantDelta` events are incremental or
  cumulative/repeated and dedupes against a running buffer before appending to the UI.
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
  (`appendAssistantText`, `showStatus`, etc.) that the controller drives.

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
- **Still unverified** (blocked on a `resource_exhausted` quota error on the Free-tier account used
  for the spike — retry once quota/plan allows): file-edit event shape and force ON/OFF write
  timing (blocks F-30/F-31/F-40 design confirmation), tool-call result payload shape (F-32),
  `--list-models`/`agent mcp list` output format (F-21/F-70), image-attachment support (F-60).

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
- **Known backlog, not yet addressed**: `AgentUiController` is growing into a god-object (prompt
  building, listener orchestration, checkpoint/mention/past-chats/model-loading all in one class);
  no Settings/Preferences page exists for `agentExecutablePath` (only reachable by hand-editing the
  persisted XML); tool-window-close process cleanup relies on project-level `Disposable` only,
  unverified against the requirements doc's separate "on tool window close" wording (§7).
- **Requirements doc**: was missing several real Cursor Agent-panel/CLI capabilities entirely —
  see `docs/cursor-agent-plugin-requirements.md` §6.2/§6.3/§6.6/§6.9 for what got added (`@Branch`,
  `@Chats`, the 3-way permission model + `--auto-review`, worktrees, subagents/custom modes as
  open questions) and what got corrected (F-60 image-attach support is contradicted between the CLI
  changelog and the parameters reference — don't assume a flag name until verified live; F-22's old
  binary force framing undersold the real approval model, now F-24).

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

**Not yet implemented**: diff preview/Apply/Reject (F-30/F-31, blocked on the M0 write-timing
spike), `@Terminal` mention (F-13 — **not blocked**, just unimplemented; see below), and multimodal
input (F-60/F-61). `McpServersDialog` shows `agent mcp list`'s raw output rather than a parsed
table — its format was never verified against a populated `.cursor/mcp.json` (no MCP servers were
configured on the machine this was built on).

**F-13 (`@Terminal` mention) API verified, 2026-09**: an earlier pass marked this "verified" in
GitHub issue #2's checklist without actually doing the check — caught by an onboarding dry-run (see
GitHub issue #13) that found the claim unsupported. Actually checked now, via context7 against
`plugins.jetbrains.com/docs/intellij/embedded-terminal.html`: `TerminalView.DATA_KEY` (from a
`DataContext`) or `TerminalToolWindowTabsManager.getTabs()` gets terminal tabs, and
`TerminalView.outputModels` (`TerminalOutputModel`, regular/alternative buffers) or
`TerminalBlocksModel` (per-command blocks with working dir + exit code) exposes the text — this is
the Reworked Terminal API, default since platform 2025.2, which covers this plugin's `sinceBuild
261`. Feasible; just not implemented yet (tracked in GitHub issue #12). Exact text-extraction method
signatures weren't in the doc snippets returned — expect to need to browse the actual platform
sources during implementation.

**Needs manual `./gradlew runIde` verification, not yet done**: the `EditorTextField` Enter-to-send
+ Shift+Enter-for-newline keybinding (`ComposerPanel`'s `registerCustomShortcutSet` on plain ENTER),
and the `@` mention popup's positioning/focus behavior (`MentionPopupController`). Neither is
unit-testable (Swing/editor keyboard-event routing and popup UI), and no live IDE session has
exercised them yet.
