# Claude Proへの独立レビュー依頼

現在の設計は [Project Mission](../../project-mission.md) / [ACP First](../../architecture/cursor-integration.md)、実行体制は [Codex実行規約](../../development/codex-execution-policy.md)を優先する。以下の依頼例は子Agent生成の許可ではない。追加担当は独立top-level Sessionとして確認し、既存の担当・許可範囲を維持する。

GPTは<>を埋め、必要な差分と根拠を添える。Claudeアプリの**新しい会話**、またはPro認証済みClaude Codeを使う。他プロジェクトの会話に混ぜない。

```text
あなたはcursor-agent-pluginの独立レビュアーです。進行役・統合担当はGPTです。
対象Issue: #<番号>、対象SHA: <SHA>、変更範囲: <ファイル>。
期待するUX/受入条件: <条件>。
変更前後の事実とGUI証拠: <記録、または未確認と明記>。
以下の資料だけを根拠に、実害のある不具合・回帰・検証の抜けをレビューしてください。
ソース編集、コマンド実行、GUI操作、commit/push、Issueコメントは行わないでください。
出力は重要度・箇所・再現条件・影響・提案としてください。
証拠不足は仮説と明示し、実施していないGUIテストを合格にしないでください。
問題が見つからなくても検証の限界を記載してください。
<差分・関連コード・証跡>
```

CLIで使う場合は`claude --help`で現在のフラグを確認する。例: GPTがレビュー資料をローカルファイルにまとめ、`claude -p --tools '' --output-format text < review-input.txt > review-output.txt`。ツールを無効にして資料レビューに限定する。CLIがsandbox内で未ログインを返した場合は、通常ターミナルで`claude auth status`を確認して認証の有無と実行環境の制限を切り分ける。認証ファイルをコピーしたり読み出したりしない。

担当を実装に変える場合は、GPTが別途Issueにclaim・別checkout・対象ファイル・基点SHA・テスト・専用branch/PRを指定する。mainへの直接commit/pushは禁止し、docs/development/github-workflow.mdに従う。既存レビュー依頼を実装権限として扱わない。
