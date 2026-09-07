---
name: finish-work
description: Validate, independently review and hand off develop/main PRs with complete acceptance coverage.
---

# Finish work

Read docs/development/github-workflow.md, pr-automation.md and docs/verification/README.md.

1. Run ./gradlew test and affected tooling tests; buildPlugin for packaging/GUI builds. Push only the owned Issue branch.
2. Obtain a separate-session review of fixed HEAD/base. Concrete code defects/test failures block both targets.
3. Develop requires a complete Case matrix, not GUI pass. Keep pending/blocked/fail and dedicated fix Issues visible.
   Main promotion requires all candidate commits and all required Cases to pass on one fixed build; GPT/human evidence is valid.
   Result metadata changes do not change the tested candidate. Only permitted promotion JSON files may differ.
4. Stop writer before trusted-main v2 enroll. Coordinator owns fixes/re-review, current test/PR policy/Agent review/Acceptance gate,
   guarded merge, Issue updates and cleanup. Never concurrently edit an enrolled branch.
5. Develop uses squash and keeps acceptance Issues open. Promotion uses merge commit; original QA Issues close only after individual reconciliation.
   Never delete main/master/develop. GUI lease remains required for desktop operations. Preserve unresolved work and owners.
6. #83 bootstrap alone uses its documented current-main CLI/independent-review handoff to PM; no old enroll or automatic heartbeat restart.
