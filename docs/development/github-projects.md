# GitHub Projectsを開発の案内図として使う

[開発マップ](https://github.com/users/shinma06/projects/2)は、人間とエージェントが過去・現在・次・その後を復元する入口です。
[開発ルール](github-workflow.md)、[作業管理の責務分離](work-management.md)、Issue/PR、[QA JSON](../verification/README.md)の役割を維持します。Projectは詳細の正本を置き換えません。

## まず見る場所

Project概要の4行で主要な経緯・現在の作業・次の節目・依存順を確認し、次のViewから対象Issueへ進みます。

| View | 表示する既存Issue | 判断すること |
|---|---|---|
| Now — 進行中 | openかつstatus:in-progress/review、親trackingを除く | 誰が何を進めているか。担当と停止状態はclaim/PRを読む |
| Next — 着手候補 | openかつstatus:ready、親trackingを除く | 次に着手できる候補。全件を直後の実施予定とは扱わない |
| Later — 依存待ち・保留 | openかつstatus:blocked/deferred | 何を待ち、何が揃えば再開できるか |
| Past — 完了履歴 | closed | 完了日とIssue/PRから、進めた順序と判断の経緯を辿る |
| 全体 — 親子と全Issue | 全Issue | 親子関係、横断QA、各Viewに出ない親trackingを確認する |

順序の根拠は親/対象Issueの依存と優先度です。概要には主要な順序だけを記載し、未確定の順序を確約しません。独立作業は共有ファイルと担当を確認して並行できます。Pastの初期行順は完了日の古い順で、後続の完了もClosed列から確認できます。

## 既存Statusの対応

既存LabelsとStatusを使います。Priorityはpriorityラベルの表示、Milestone/ParentはGitHub標準フィールド、関係の判定済み表示は[Relationship Status](work-management.md#relationshipの判定)を使い、値の正本を二重化しません。

| Issueの状態 | Project Status |
|---|---|
| open / status:ready、blocked、deferred | Todo |
| open / status:in-progress、review | In Progress |
| closed / status:done | Done |

Todoは着手可能だけを意味しません。ViewはIssueのstatusラベルで絞るため、依存待ちをNextに表示しません。PRの状態だけでIssueのラベルを推定変更せず、担当の受入と引継ぎ記録を照合します。
実装IssueのDoneはGUI合格/main反映済みを意味しません。develop統合後のQA引継ぎ、固定候補全体のpromotion gateは従来どおりです。元Issueと専用QA Issueを両方Projectに残します。

## 開発に合わせて更新する

作業担当または進行役が、既存のIssue更新と同じ区切りで必要な差分だけを反映します。

1. 作成・着手時: [作成/triage確認](work-management.md#issue作成triageの確認)を行い、対象IssueをProjectへ登録し、Milestone・native関係・Relationship Status・既存Status/Priority表示を確認する。親やQAの追加漏れも確認する。
2. 引継ぎ・blocked・完了時: Issueを先に更新し、Project Statusを合わせる。QA分離時はQAを追加し、元IssueをPastに残す。PRはLinked pull requestsから辿り、重複カードを増やさない。
3. 主要な計画変更時: 理由と依存・次操作をIssueへ残し、Project概要の該当行と必要な行順だけ更新する。古い判断はIssue履歴に残す。
4. 更新後: Projectを再取得し、対象Issueの登録・Status・View条件・リンクと、概要のNow/Nextが現状に合うことを確認する。

ViewのIssueタイトル・ラベル・open/closedはGitHub上のIssueを参照しますが、新規Issueの追加とProject Status・概要・手動の行順は自動同期ではありません。本整備では新しい自動化を追加しません。既存coordinatorが作ったQAも引継ぎ時に確認します。API障害や権限不足ならIssueへ未反映項目と再試行条件を残し、成功したことにしません。

新しいセッション・中断後はProject概要 → Now/Next/Later・対象Milestone → 必要な親/対象Issueの全コメント/PR → 必要なQAを確認します。既存計画を起点に差分を復元し、claimを無断で引き継ぎません。Projectにアクセスできない場合は対象Issue/Milestone/PRを使って復元し、Project未照合を明記します。

## Issue終了時の整合確認

2026-09-10 / #168。Issue close、QA引継ぎ、Projectへの反映を別々に確認します。PMはcoordinatorのdoneを受け、今回の元Issueと新規/再利用QAを対象に次を照合します。全Issueの監査を毎PRに課しません。

1. **正本**: 元Issueの全受入・最新claim・固定PR/mergeを照合し、残る実装は分割先へ、GUI/mainはQAへ引き継いだことを双方向link/readbackで確認する。open/closedとstatusラベルを一致させる。
2. **親の現行表示**: closedの子を未実装として残さず、完了した実装と残る実装/QAのIssueを別行にする。名前だけでチェックを付けず、行全体の受入を確認する。親全体/QAを子PRだけでcloseしない。日付付きの履歴は保持し、現在の次操作と明確に分ける。
3. **Project**: 元IssueとQAの登録・Status、Milestone、native関係と両端のRelationship Statusを再取得して正本と一致させる。主要な進捗/計画変更なら親の現在地とProject概要も更新する。再試行はURL/Issue番号で既存項目を再利用し、カードを重複作成しない。
4. **終了記録**: 元Issueへ確認済みの対象と結果を残す。失敗/未反映は具体的な対象・PM担当・再試行条件を残し、次の引継ぎで回収する。Project表示の未反映と実装受入の未完了を混同しない。

棚卸しを依頼された場合は、全ページのIssue/PRとProjectを取得し、閉じ忘れ、QA重複/引継ぎ漏れ、closedとラベルの不一致、親の古い分割先、Project未登録/Status不一致を調べる。日数、PR merge、全子closedのいずれかだけで自動closeしない。未達受入・GUI/main待ち・有効claim・明示保留は残す。重複なら受入と担当を正本へ引き継いでからcloseし、Issueは永久削除しない。

## 設定と履歴

2026-09-09 / #131で、項目0件・未関連付けだった非公開Project #2を再利用しました。既定13フィールドとStatusの名称を保存し、空のView 1をNowへ変更、Next/Later/Past/全体を追加しました。非公開の共有範囲、closed Project #1、Issue構造/粒度、ラベル名、ブランチ戦略、PR gate、テンプレート、既存自動化とPAUSED状態は維持します。

過去のUI差分計画にある「今回Projectを新設しない」は当時のスコープとして保存します。今回のユーザー依頼では空の既存Projectを案内図として利用し、親#1の進行管理を補います。

API操作にはread:project（読み取り）/project（更新）権限が必要です。認証情報やローカルパスを公開Issue/PRへ貼りません。
操作仕様: [Projects API](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-api-to-manage-projects)、[Viewのフィルター](https://docs.github.com/en/issues/planning-and-tracking-with-projects/customizing-views-in-your-project/filtering-projects)。
