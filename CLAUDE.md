# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
`AGENTS.md` is a symlink to this file, so any agent reading either name gets identical content.

## Multi-agent collaboration model

This repo is worked on by more than one AI coding agent — Claude Code and Cursor's own
`cursor-agent` (running under a Teams plan, used by the plugin's actual target users) — with **no
fixed role split**. Whichever agent is active at a given moment picks up whatever work is next;
neither has visibility into the other's local session history or scratch state, so **GitHub Issues
on this repo is the shared source of truth for task status**, not any agent-local plan/todo file.
Before starting work, check open issues for the current milestone status; update an issue's
checklist/comments as you complete items instead of only reporting progress back to whichever
human or agent happens to be watching the current session.

## What this is

An Android Studio (IntelliJ Platform) plugin that reproduces Cursor IDE's Agent tab as a native
tool window. It works by shelling out to the `cursor-agent` CLI (`agent -p --output-format
stream-json`) as a subprocess and rendering the JSON-Lines event stream in a custom Swing/JBUI
chat UI. The CLI is treated as an opaque black box — no protocol/API integration, just process
control and defensive JSON parsing. This is referred to as "方式B" (native UI approach) in
`docs/cursor-agent-plugin-requirements.md`, which is the full requirements/design doc and the
source of truth for feature scope and rationale — check it before adding features.

## Commands

```bash
# Gradle itself needs JDK 17+; Kotlin compilation uses a JDK 21 toolchain
# (auto-provisioned via the foojay-resolver plugin, independent of JAVA_HOME).
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"

./gradlew buildPlugin   # produces build/distributions/cursor-agent-plugin-<version>.zip
./gradlew runIde        # launches a sandbox Android Studio instance with the plugin installed
```

There is no test source set and no lint/static-analysis task configured in `build.gradle.kts` —
don't assume `./gradlew test` or a linter exists.

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
- **`ui/AgentUiController.dedupeAssistantChunk`** — the CLI has been observed sending both
  incremental deltas and cumulative/repeated text in `AssistantDelta` events; this dedupes against
  a running buffer before appending to the UI. Don't remove this without checking against a live
  stream capture — it's compensating for real observed CLI behavior, not speculative.
- **`settings/AgentSettingsState`** (application-level `@Service` + `PersistentStateComponent`,
  storage `cursor-agent-settings.xml`) — holds `agentExecutablePath`, `selectedModel`, `mode`
  (`AgentMode`: ASK/AGENT/PLAN, each mapping to a `--mode` CLI value or `null` for default agent
  mode), and `forceEnabled` (maps to `--force`; **must default to false** — this is a deliberate
  safety requirement from the requirements doc, not an oversight, to prevent unattended file
  changes).
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
  is already the user's trust decision, independent of the `forceEnabled` setting (which still
  separately controls `--force`, i.e. auto-approving individual tool calls).
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

## Current implementation status vs. requirements doc

The requirements doc (`docs/cursor-agent-plugin-requirements.md`) defines the full MVP/P2/P3 scope
with feature IDs (F-01, F-02, ...); the live milestone tracker is GitHub issues #1–#10 (see
"Multi-agent collaboration model" above), which is the up-to-date source for what's done.

Implemented: prompt send/stream/history/new-chat (F-01–03, F-05), active-file auto-context (F-15),
mode/force controls (F-20, F-22), workspace pinning (F-51), Markdown rendering for assistant
messages (`AssistantMessageBubble`/`MarkdownRenderer`, commonmark-based, HTML-inline/HTML-block
nodes rendered as escaped text rather than passed through raw — see that file's doc comment for
why), the git-stash-create-based checkpoint/rollback system (F-40–44: `CheckpointService` +
`GitSnapshotStore` + `CheckpointHistoryState`, rollback icon on `UserMessageBubble`), and `@mention`
context injection for files/folders/git-diff/docs/web hints (F-10–12, F-14: `ComposerPanel`'s
`EditorTextField` + `MentionPopupController` + `MentionResolver`).

Session resume (`--resume`) is implemented at the service layer but there's no UI to pick from past
sessions yet (F-50). **Not yet implemented**: `--list-models` dynamic population (F-21, UI stub
exists in `ModelSelector`), diff preview/Apply/Reject (F-30/F-31, blocked on the M0 CLI spike),
`@Terminal` mention (F-13, blocked on IntelliJ Terminal API verification), MCP server listing/
enable-disable (F-70/F-71), and multimodal input (F-60/F-61).

**Needs manual `./gradlew runIde` verification, not yet done**: the `EditorTextField` Enter-to-send
+ Shift+Enter-for-newline keybinding (`ComposerPanel`'s `registerCustomShortcutSet` on plain ENTER),
and the `@` mention popup's positioning/focus behavior (`MentionPopupController`). Neither is
unit-testable (Swing/editor keyboard-event routing and popup UI), and no live IDE session has
exercised them yet.
