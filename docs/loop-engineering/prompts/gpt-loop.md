# GPTへの開始/再開依頼

現在の設計は [Project Mission](../../project-mission.md) / [ACP First](../../architecture/cursor-integration.md)、実行体制は [Codex実行規約](../../development/codex-execution-policy.md)を優先する。以下の依頼例は子Agent生成の許可ではない。追加担当は独立top-level Sessionとして確認し、既存の担当・許可範囲を維持する。

以下の<>を対象に置き換える。説明は人間向け、code blockはAgent専用。新規/更新時は[コンテキスト規則](../../architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件)を適用する。

```text
Advance one loop for Issue #<number> under docs/loop-engineering/README.md.
GPT is the lead, main integration owner and sole Computer Use operator.
Request an independent review from Claude Pro; have Cursor Pro perform the verification task through the GUI.
Follow docs/development/codex-execution-policy.md: additional participants must be independent top-level sessions, not child agents when GPT-6 Astra is the lead. Preserve existing owners and authorized scope.
Follow docs/development/github-workflow.md with an Issue claim, dedicated branch/worktree and PR. Never commit/push directly to main.
Do not operate the GUI, runIde or install until you obtain the reservation under docs/development/gui-coordination.md.
Check the Issue claim and existing changes; fix the target MV ID and expected outcome first.
Cursor may edit only the fixture created for this loop. GPT makes plugin implementation changes in the development repository.
Budget: 45 minutes, 3 fixes and 8 Cursor sends. Do not switch to API billing.
Record actual screens and file results. After each fix, repeat the same operation on an identified new build.
Retrieval failures are blocked; never mark unobserved behavior pass. At completion update Issue/QA and leave the next action.
When creating/updating/reviewing/auditing development context, apply docs/architecture/knowledge.md's context policy.
Report progress, evidence and handoffs in Japanese.
```

再開なら `Resume from the latest handoff for Issue #<number>. Recheck HEAD and the current GUI state.` も加える。
