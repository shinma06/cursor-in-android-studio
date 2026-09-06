# 開発を始める

Gitに言及がない依頼にも、[GitHub開発規約](docs/development/github-workflow.md)を適用する。
Issueとclaimを確認し、専用branch/worktreeで変更し、PRでレビュー・CIを通して統合する。mainへの直接commit/pushは禁止。

- 全担当: [AGENTS.md](AGENTS.md) → 要件 → Issue #1/対象Issue → 開発規約
- 初回: `bash scripts/workflow/bootstrap.sh`
- GUI担当: [GUI予約・引継ぎ](docs/development/gui-coordination.md) → [loop手順](docs/loop-engineering/README.md)
- 最低限の検証: `./gradlew test`。運用スクリプト変更は `python3 -m unittest discover -s scripts/workflow -p 'test_*.py'` も実行。

2026-09-06、public化後にmainのサーバー保護を有効化済み（ruleset `main-pr-required`）。
PRと最新baseに対する `test` / `PR policy` 成功が必須。main削除・force pushは禁止、bypassなし。
詳細と設定の確認手順は開発規約に記載。
