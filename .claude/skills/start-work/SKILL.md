---
name: start-work
description: Begin work on a GitHub issue in this repo, following the multi-agent coordination rules in CLAUDE.md (sync, check for conflicting in-progress work from another agent, claim the issue). Use this before starting any implementation work here, whether the user names a specific issue or just says something like "keep going" / "pick up where things left off."
---

# Start work on an issue (cursor-agent-plugin)

This repo is worked on by more than one AI agent (Claude Code and Cursor's own agent) with no
fixed roles and no branch protection — everything lands on `main` directly. Skipping coordination
here means redoing someone else's work or reverting their fix without realizing it. Full rules are
in `CLAUDE.md`; this skill is the mechanical checklist for the "starting" half of them.

## Steps

1. **Check for uncommitted local work first**: `git status`. If there's anything there that isn't
   yours to discard, stop and ask the user rather than pulling over it.
2. **Sync**: `git pull` (or `git fetch` and diff `HEAD` against `origin/main`). Another agent may
   have pushed since you last synced.
3. **Read `CLAUDE.md`'s "Current blocker" section** before picking a task — don't start work that's
   already documented as blocked (e.g. the Free-tier CLI quota issue, if still current).
4. **Find the target issue** if the user didn't name one: `gh issue view 1` for the tracking issue's
   live checklist of open milestones, prioritizing whatever it says is unblocked.
5. **Check for a conflicting claim**: `gh issue view <number> --comments`. If there's a "Starting
   work" (or similar) comment newer than the issue's last real update, with no matching
   completion/closing comment after it, someone else may be actively on this issue right now — stop
   and tell the user instead of duplicating the work.
6. **Claim it**: `gh issue comment <number> --body "Starting work (Claude Code)."` — this is the
   only coordination signal that exists (issues carry no labels/assignees), so it's not optional.

Then proceed with the actual implementation work as normal. When it's done, use the `finish-work`
skill.
