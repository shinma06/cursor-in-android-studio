# 開発を始める

Gitに言及がない依頼にも、[GitHub開発規約](docs/development/github-workflow.md)を適用する。
Issueとclaimを確認し、専用branch/worktreeで変更し、PRでレビュー・CIを通して統合する。mainへの直接commit/pushは禁止。

コード・コメント・Doc、例や設定の変更では[コメント・Docの契約](docs/development/comment-doc.md)を読み、必要な境界契約と局所の理由、影響する呼出し元/先・tests・help/例・参照を揃えます。説明の数や全既存実装への適用を目的にしません。

- 全担当: [AGENTS.md](AGENTS.md) → 要件 → Issue #1/対象Issue → 開発規約
- 初回: `bash scripts/workflow/bootstrap.sh`
- GUI担当: [GUI予約・引継ぎ](docs/development/gui-coordination.md) → [loop手順](docs/loop-engineering/README.md)
- 変更の検証: [CI影響規則](docs/development/ci.md)に従い、clean committed HEADで `python3 scripts/workflow/change_impact.py --run-tests`。製品変更はGradle test、tooling変更はworkflow/loop suite等を既存classifierが選択します。

2026-09-06、public化後にmainのサーバー保護を有効化済み（ruleset `main-pr-required`）。
PRと最新baseに対する `test` / `PR policy` 成功が必須。main削除・force pushは禁止、bypassなし。
詳細と設定の確認手順は開発規約に記載。
