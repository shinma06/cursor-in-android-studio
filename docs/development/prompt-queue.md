# 同一会話の予約送信（#48）

実行中に入力しただけの下書きは送らない。「予約に追加」を押した入力だけを、同じtab・Conversation IDの次のturnへ順番に送る。登録済み件数から一覧を開き、本文の編集（保存／キャンセル）、削除、上へ／下へ移動、予約送信の再開を選べる。既定のEnterは実行中には送信しないまま、Stopボタンも維持する。active-turn steering、即時割込み、CLI stdinへの追加入力、予約の永続化は追加しない。

## snapshotと寿命

- 登録時: 元の入力本文、選択中のmode/modelをimmutable entryへ保持。内部の予約IDはSavedTurn IDと別で、実際に送信が始まると既存SessionRunTokenから新しいturn IDを発行する。編集は本文だけを更新しID・mode/modelを保持する。
- 次turnの開始時: 実行ファイル・permission・sandbox・worktree設定を既存TurnSettings/TurnWorkspaceへ固定。ファイル・選択範囲とVFS mentionはEDTで、その送信のGit等のcontextとcheckpointは背景準備で取得する。登録時の古いファイル内容を再利用しない。時点は登録ボタンのtooltipと予約一覧で説明する。
- 継続先は送信開始時の同tab provider ID。IDを取得できなければ予約を保持してpauseし、別の新規CLI会話へ自動送信しない。ACP設定不一致、準備の予約不可も残キューを一時停止する。
- 未登録の下書き・caret・選択mode/modelは予約送信で消さない。実行中のACPによる確定mode/model表示は従来どおり反映する。送った予約本文は既存の通常turnとして保存されるが、未送信予約はcontroller内だけに保持し、履歴再表示・再起動では再送しない。

## 終了と意図の境界

| 操作・結果 | 予約の扱い |
|---|---|
| printのOS終了code 0（記録errorなし）／ACP end_turn | 同じtabのtokenを完了し、EDTへ次の1件を予約する |
| print Resultやtool完了だけ | 進めない。Resultは物理終了ではない |
| Stop、error、非zero exit、ACP refusal/token limit/request limit/cancelled/uncertain | 残りを保持してpause。自動retryしない |
| 別tab選択／New Chat／履歴を開く | 元tabの残りをpause。現在実行中の処理と別tabの並行runは維持 |
| 一覧・編集／checkpoint復元／closeの確認を開く | modal内で終了callbackが来ても自動送信しないよう先にpause。キャンセルでもpauseを保つ |
| 予約送信を再開 | 明示操作として再開。実行中なら成功終端を待ち、idleなら次の1件を開始 |
| 未登録本文の手動送信 | 残予約をpauseして通常送信。終了だけでは予約を再開しない |
| tab close／project dispose | queueを破棄しdialogも閉じる。close確認に未送信予約の消失を含める |

配送ticketはturn世代・queue revision・先頭entryを固定する。実際のinvokeLater時に世代、変更後のrevision、pause、選択tab/Conversation ID、activeRunなし、disposeなしを再照合する。Stop→再開、編集・削除・並べ替え、後続turnに古い配送ticketを流用しない。登録の重複クリックは同じ下書きを二重登録しない（成功登録で入力をclear）。明示的に同じ本文を再入力して登録することは許す。

既存`AgentRun`の単一終端callbackと`AgentTurnListenerFactory`のEDT/token/停止guardを使用する。成功判定だけをcontrollerへ渡し、process/ACP内部の新しい状態機械は作らない。`SessionTabs.beginTurn`は同tabの単一runを保ち、project-wide `tryPrepare`は別tabの並行runを妨げず復元操作と排他する。ACPは既存のprompt終端・tool静止確認後の結果に従う。

開始前の拒否は予約を残す。runが始まった後のcontext例外／起動失敗は通常のuser本文と失敗turnとして残し、当該entryは送信済みとして除く。残りはpauseする。成功しても文脈上の内容品質やtask達成を保証する判定ではない。

## 比較と採用範囲（2026-09-12確認）

[Cursor公式IDE Agent Overview](https://cursor.com/docs/agent/overview)は従来のEnter予約・順番表示・drag並べ替え・完了後の順次処理を説明する。同じ資料のSend now/Enter二回/TabはWebとAgents Window rolloutの説明なので、このPluginのIDE入力へそのまま移さない。今回は専用ボタンによるopt-in登録と上下移動を採用し、キー移行とdrag対応は追加しない。既存の未送信下書きがバージョン更新で急に予約送信されることを防ぐ。

[Cursor ACP](https://cursor.com/docs/cli/acp)のsession/promptと終了結果、[ACP prompt turn](https://agentclientprotocol.com/protocol/v1/prompt-turn)が構造化連携の入口である。順次送信UIの所有はクライアント側に置き、印字出力からの新しい成功推測や非公開steering機構を実装しない。

比較対象は[JetBrains AI Assistant + ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)に[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)と利用可能なIDE操作を加えた構成まで含む。[JetBrains AI Chat](https://www.jetbrains.com/help/ai-assistant/chat-mode.html)の複数会話と入力機能が既にあることを前提とし、IDE内チャット自体を独自性としない。確認した公式資料だけでは同じ予約・停止・snapshot操作の同等性は未検証。本Pluginでの改善は、既存のAndroid Studio context取得・checkpoint・停止・復元排他を次turnでも同じ経路に通す直接接続である。GUIの同等以上UXは固定Caseで実観察する。

## 検証と引継ぎ

`PromptQueueTest`はFIFO・mode/model保持、編集/削除/順序、世代/選択/idle/pause、開始拒否と同期失敗、実AgentRun終端とSessionTabsの並行runを確認する。既存run/ACP/配送/保存テストを併走する。native dialog、未登録draft/caret、IME、狭いpanel、実Agentの連続送信は[Case48](../verification/changes/issue-48.json)へpendingで分ける。

[所有境界](../architecture/README.md)と[Lifecycleの6条件](../verification/lifecycle-contracts.md)に従う。Swing EDT/controller dispose/process終端が対象で、Android Activity、DB、coroutineは使用しない。#44保存schema/ID、#45/#47/#254、モデル取得・設定・共通描画の他Issueを複製しない。統合時にPMが停止済みPRとcontroller接続を再照合する。
