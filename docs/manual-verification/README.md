# GUI動作確認

> 2026-09-07 / #83: developはテスト・独立レビュー・Case追跡で統合可能（GUI pending/blocked/failを保持）。mainは固定候補全体の必要Case pass後のみ。区切り単位の入口は [確認マトリクス](../verification/README.md)。過去MV/runは履歴であり新候補のpassへ転記しない。


[matrix.md](matrix.md)を受入条件の正本とする。GPTがComputer Useで実画面を操作して確認し、人間が必要な部分を補完する。単体テストやCLI試験だけではGUIのpassにならない。

運用・準備は[ループ手順](../loop-engineering/README.md)、人間向けには[開始手順](../loop-engineering/human-runbook.md)、記録形式は[evidence.md](../loop-engineering/evidence.md)を参照。

| 状態 | 意味 |
|---|---|
| pending | 未確認。修正後も再確認まではpending |
| pass | 対象ビルドで実画面の期待結果を確認し証拠を記録 |
| fail | 観察した結果が期待と違う。Issueと再現条件を記録 |
| blocked | 画面取得、ログイン、環境などの問題で確認できない |

旧`merged`はGit上の統合状態でありGUI検証状態ではない。今後は使用せず、未確認ならpendingを保つ。archiveへの移動もマージだけを理由にせず、完了/廃止の根拠を残す。

確認者は`GPT / Computer Use`、`human`など実際の担当を記す。対象ブランチだけでなくrunのSHA・インストール実体・証跡をリンクする。表示のみの旧SNAPSHOT確認を、新しいビルドの送信/編集QAへ拡張解釈しない。
