# Claude Proへの独立レビュー依頼

現在の設計は [Project Mission](../../project-mission.md) / [ACP First](../../architecture/cursor-integration.md)、実行体制は [Codex実行規約](../../development/codex-execution-policy.md)を優先する。以下の依頼例は子Agent生成の許可ではない。追加担当は独立top-level Sessionとして確認し、既存の担当・許可範囲を維持する。

GPTは<>を埋め、必要な差分と根拠を添える。説明は人間向け、code blockはAgent専用。開発コンテキストのレビューでは[共通規則](../../architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件)の該当箇所も資料へ添える（tool無効のreviewerへ自力取得を要求しない）。Claudeアプリの**新しい会話**、またはPro認証済みClaude Codeを使う。他プロジェクトの会話に混ぜない。

```text
You are the independent reviewer for Cursor in Android Studio. GPT coordinates and integrates the work.
Target Issue: #<number>; target SHA: <SHA>; scope: <files>.
Expected UX/acceptance: <criteria>.
Before/after facts and GUI evidence: <records, or explicitly unverified>.
Review concrete defects, regressions and verification gaps using only the supplied material below.
Do not edit source, execute commands, operate a GUI, commit/push or post Issue comments.
Report in Japanese: severity, location, reproduction conditions, impact and proposed fix.
Label unsupported conclusions as hypotheses. Never mark GUI tests you did not perform as passed.
Even if no defect is found, state the verification limits.
For development-context reviews/audits, apply the relevant supplied excerpt of docs/architecture/knowledge.md's context policy. Missing evidence remains unverified; do not expand your access or scope to obtain it.
<diff, relevant code, evidence and applicable policy excerpt>
```

CLIで使う場合は`claude --help`で現在のフラグを確認する。例: GPTがレビュー資料をローカルファイルにまとめ、`claude -p --tools '' --output-format text < review-input.txt > review-output.txt`。ツールを無効にして資料レビューに限定する。CLIがsandbox内で未ログインを返した場合は、通常ターミナルで`claude auth status`を確認して認証の有無と実行環境の制限を切り分ける。認証ファイルをコピーしたり読み出したりしない。

担当を実装に変える場合は、GPTが別途Issueにclaim・別checkout・対象ファイル・基点SHA・テスト・専用branch/PRを指定する。mainへの直接commit/pushは禁止し、docs/development/github-workflow.mdに従う。既存レビュー依頼を実装権限として扱わない。
