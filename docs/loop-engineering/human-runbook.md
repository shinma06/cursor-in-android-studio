# 人間向け：ループ開発の始め方

修正内容を開発担当へ伝えると、Issue・専用worktree・PRを使って進めます。GPT/Codex・Claude・Cursorのいずれも担当でき、[共通の役割条件](../development/github-workflow.md#正本と役割)に従います。独立レビューは別session、GUIは[予約手順](../development/gui-coordination.md)に従う対応可能な担当へ割り当てます。

## 最初の準備

1. 選んだ開発クライアントでリポジトリを開き、編集・検証に必要なtoolと権限を確認します。すべてのproviderへのログインは不要です。
2. GUI試験をする場合だけ、実画面を操作・観察できる環境と必要なOS権限を確認します。対応toolがなければ対応可能な担当または人間へ引き継ぎます。Computer Use必須Caseは人間の直接操作だけでは合格にできません。
3. 製品のCursor IDE/CLIへ送信するCaseでは、製品試験用のPro/Teams認証を確認します。IDEのログインだけでCLIも使えるとは仮定しません。認証やOS許可の不足は人間へ引き継ぎます。
4. buildする場合は[現行の開発・検証手順](../../CLAUDE.md#開発検証の入口)と[ZIP識別手順](../development/plugin-zip-delivery.md)に従います。JDK 21と固定SDKが標準です。`platformPath`の指定やSDK変更は通常準備に含めず、ローカルSDKを明示使用する場合だけ現行手順を確認します。

GUI試験の編集先は[生成したfixture](evidence.md)の`.loop-runs/<run-id>/cursor`と`plugin`です。同内容から始まる独立Gitリポジトリで、OS sandboxではありません。製品の試験入力はfixtureに限定し、プラグイン本体の修正は実装担当のIssue worktreeで行います。

## 開発担当へ送る依頼

以下の番号・担当を置き換えます。Agentへ渡す部分は英語です。

```text
Advance one loop for Issue #<number> under docs/loop-engineering/README.md.
Assign roles using the shared capability, permission and ownership rules in docs/development/github-workflow.md.
Writer: <session>; independent reviewer: <different session or pending>; GUI operator: <capable authorized session or pending>.
Follow docs/development/codex-execution-policy.md; this request does not authorize child agents.
Choose the acceptance Cases and related MV IDs. Reproduce, fix, review and recheck within 45 minutes, 3 fixes and 8 sends to the product under test.
Limit product test inputs to newly generated fixtures; implement plugin fixes in the claimed Issue worktree.
Use a GUI lease and identified build. If GUI tools are unavailable, hand off GUI work and continue independent implementation or CLI checks; retain blocked/unverified results.
Preserve Computer Use-required Cases. Record evidence and the next action, and report in Japanese.
```

対象が未定なら[Project](https://github.com/users/shinma06/projects/2)とopen Issueの優先度・依存・claimから着手可能な1件を選びます。旧Issue #1や過去の優先順位は現在の作業指示にしません。

## 作業中と結果の確認

- GUI操作中は対象ウィンドウを同時に触らず、人間が操作を引き継ぐ間はAgentを停止します。ログイン・OS許可・CAPTCHAを終えたら再開を伝えます。秘密情報はチャットに貼りません。
- 期待する表示や動作を伝え、実装は担当範囲内で進めます。レビュー担当の利用上限で止まっても、独立して進められる作業は継続できます。課金プランを自動変更しません。
- 報告のIssue・対象SHA・[Case結果](../verification/README.md)・実行記録を確認します。passはそのbuildと操作範囲の確認済み、failは製品不具合、blockedは環境等で確認不能です。単体テストやレビュー成功だけではGUI合格になりません。

使いやすさを自分で確認する場合は、同じfixtureと手順で行い、実際の確認者・経路を記録します。全Caseを人間が再実行する義務はありません。Computer Use必須条件は保持します。

## 中断・再開

「ここで止めて、Issueに引継ぎを書いて」で担当が実行中処理・変更・最後の状態・次の操作を整理します。「Issue #番号の引継ぎから再開して」で、所有者・HEAD・未完了担当・GUI状態を再確認します。

自分でIDEを起動する場合も、[evidence.md](evidence.md)でbuildとfixtureを用意し、GUI担当と調整してleaseを取得してから`./gradlew runIde`を実行します。通常IDEへのZIP install・再起動も同じ予約とbuild識別が必要です。
