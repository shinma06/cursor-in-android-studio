# GUI動作確認

> 2026-09-07 / #83: developはテスト・独立レビュー・Case追跡で統合可能（GUI pending/blocked/failを保持）。mainは固定候補全体の必要Case pass後のみ。区切り単位の入口は [確認マトリクス](../verification/README.md)。過去MV/runは履歴であり新候補のpassへ転記しない。


現行の受入条件・候補結果は[Case JSON](../verification/README.md)を正本とし、[matrix.md](matrix.md)は過去のMV詳細・観察記録として保持する。担当は[共通の能力・許可・所有条件](../development/github-workflow.md#正本と役割)で選び、leaseを取得した指定担当または引き継いだ人間が実画面を確認する。Computer Use必須Caseは人間の直接操作で代替しない。単体テストやCLI試験だけではGUIのpassにならない。

運用・準備は[ループ手順](../loop-engineering/README.md)、人間向けには[開始手順](../loop-engineering/human-runbook.md)、記録形式は[evidence.md](../loop-engineering/evidence.md)を参照。

| 状態 | 意味 |
|---|---|
| pending | 未確認。修正後も再確認まではpending |
| pass | 対象ビルドで実画面の期待結果を確認し証拠を記録 |
| fail | 観察した結果が期待と違う。Issueと再現条件を記録 |
| blocked | 画面取得、ログイン、環境などの問題で確認できない |

旧`merged`はGit上の統合状態でありGUI検証状態ではない。今後は使用せず、未確認ならpendingを保つ。archiveへの移動もマージだけを理由にせず、完了/廃止の根拠を残す。

確認者は公開可能な担当session IDまたは人間の識別子と、実施経路を記す。過去の担当名・結果は書き換えない。対象ブランチだけでなくrunのSHA・インストール実体・証跡をリンクする。表示のみの旧SNAPSHOT確認を、新しいビルドの送信/編集QAへ拡張解釈しない。
