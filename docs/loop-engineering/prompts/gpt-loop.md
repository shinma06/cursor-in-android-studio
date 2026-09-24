# 開発担当への開始・再開依頼

担当は[共通の役割条件](../../development/github-workflow.md#正本と役割)で選びます。旧ファイル名はリンク互換のため残し、GPT専用にはしません。[Project Mission](../../project-mission.md) / [ACP First](../../architecture/cursor-integration.md)と[Codex実行規約](../../development/codex-execution-policy.md)を維持します。以下の依頼は子Agent生成の許可ではありません。

`<>`を対象に置き換えます。説明は人間向け、code blockはAgent専用です。[コンテキスト規則](../../architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件)を適用します。

```text
Advance one loop for Issue #<number> under docs/loop-engineering/README.md.
Choose roles by available capabilities, tools, authorization and claims under docs/development/github-workflow.md, regardless of model/provider.
Writer: <session>; independent reviewer: <different session or pending>; GUI operator: <capable authorized session or pending>.
The reviewer may use the same provider but must be a different session from the writer. Do not require three providers.
Follow docs/development/codex-execution-policy.md. When GPT-6 Astra is the lead, do not spawn/delegate child agents; preserve existing owners and authorized scope in independent top-level coordination.
Use an Issue claim, dedicated branch/worktree and PR. Never commit/push directly to main/develop.
Do not operate the GUI, runIde or install until you obtain the host-wide lease under docs/development/gui-coordination.md.
If GUI tools are unavailable, hand off GUI work; if no capable operator is available, keep GUI blocked and continue independent non-GUI work. Preserve Computer Use-required Cases.
Check claims and existing changes; fix the target Cases, related MV IDs and expected outcomes first.
Limit product test inputs to the new fixtures. The writer makes plugin changes in the claimed development worktree, regardless of their client.
Budget: 45 minutes, 3 fixes and 8 sends to the product under test. Do not switch billing automatically.
Record actual screens and file results. After a fix, repeat the operation on an identified new build.
Retrieval failures are blocked; never mark unobserved behavior pass. Follow target-specific gates, update Issue/QA and leave the next action.
When creating/updating/reviewing/auditing development context, apply docs/architecture/knowledge.md's context policy.
Report progress, evidence and handoffs in Japanese.
```

再開時は `Resume from the latest handoff for Issue #<number>. Recheck ownership, HEAD and current GUI state.` を加えます。
