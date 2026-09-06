# 開発を始める

Gitに言及がない依頼にも、[GitHub開発規約](docs/development/github-workflow.md)を適用する。
Issueとclaimを確認し、専用branch/worktreeで変更し、PRでレビュー・CIを通して統合する。mainへの直接commit/pushは禁止。

- 全担当: [AGENTS.md](AGENTS.md) → 要件 → Issue #1/対象Issue → 開発規約
- 初回: `bash scripts/workflow/bootstrap.sh`
- GUI担当: [GUI予約・引継ぎ](docs/development/gui-coordination.md) → [loop手順](docs/loop-engineering/README.md)
- 最低限の検証: `./gradlew test`。運用スクリプト変更は `python3 -m unittest discover -s scripts/workflow -p 'test_*.py'` も実行。

現在のGitHubプランではprivateリポジトリのサーバー保護が403で利用不可。
hooks/CIがあってもGitHub側の強制保護は未導入である点と、導入手順は開発規約に記載。
