---
name: finish-work
description: Wrap up work on a GitHub issue in this repo — verify tests pass, sync and push, update the issue checklist with a closing comment, and update CLAUDE.md/the requirements doc if the implementation status changed. Use once implementation work here is functionally complete, before ending the session or considering the task done.
---

# Finish work on an issue (cursor-agent-plugin)

Full context on why each step matters is in `CLAUDE.md`'s multi-agent rules; this skill is the
mechanical checklist for the "finishing" half.

## Steps

1. **Verify yourself, don't rely only on the pre-push hook**: run `./gradlew test` (add
   `./gradlew buildPlugin` too if you touched build config, `plugin.xml`, or packaging) and actually
   look at the result before moving on.
2. **Sync before pushing**: `git pull` if meaningful time passed since you last synced — another
   agent may have landed work in the meantime.
3. **Commit and push.** If the push is rejected as non-fast-forward, stop and reconcile (re-read
   what changed, merge/rebase) — never force-push over it.
4. **Update the GitHub issue**: check off the items you completed on its checklist, and leave a
   comment summarizing what changed and referencing the commit.
5. **Update ground-truth docs if they're now stale** — this is the step most likely to get skipped
   under time pressure, and it's the one that makes the next session (agent or human) able to trust
   these files instead of re-deriving everything from source:
   - `CLAUDE.md`'s "Current implementation status" and "Verified CLI behavior" sections, if what you
     built or learned changes either.
   - `docs/cursor-agent-plugin-requirements.md`, if a Feature ID's status or a `[要検証]` item
     resolved.
   - Watch for hardcoded facts that can silently drift (e.g. an issue-number range, a "not yet
     implemented" claim about something you just implemented) — this has already happened twice in
     this repo's history (see GitHub issue #13), so don't assume it won't happen again.
6. **If this closes out an entire milestone**: update the tracking issue's (#1) own checklist too,
   not just the child issue.
