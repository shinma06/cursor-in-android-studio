# Post-merge checklist (PR #18)

Use after merging https://github.com/shinma-postas/cursor-agent-plugin/pull/18

## GitHub

- [ ] Close PR #16 and #17 (superseded by #18)
- [ ] Issue #6 (M4) — check off F-30/F-31, closing comment
- [ ] Issue #7 (M5) — check off F-32, closing comment
- [ ] Issue #5 (M3) — check off F-13 if MV-013/014 pass
- [ ] Issue #11 (backlog) — check off controller decomposition, settings, tool-window cleanup
- [ ] Issue #12 (M9) — check off F-16, F-23, F-52, notifications; note F-17 scoped out
- [ ] Issue #1 — update milestone checklist and remove stale Free-tier blocker paragraph

## Manual verification

- [ ] Human QA: [`matrix.md`](../matrix.md) MV-001–028 (`Status` / `Verified by` / `Date`)
- [ ] After QA: set rows to `merged` or move to [`archive/`](../archive/)

## Local

```bash
git checkout main && git pull
./gradlew test
```
