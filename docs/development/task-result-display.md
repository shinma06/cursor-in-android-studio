# 子Taskの状態と結果表示

#263。子Taskの情報は既存の親会話内のツール行へ表示する。ACP標準toolを主経路にし、printの`taskToolCall`とCursor拡張の異なる契約を保持する。
手動spawn/resume、子の全文取得、独自の監視、背景実行の完了保証は追加しない。

## 受信と所属

[AgentTask](../../src/main/kotlin/com/cursoragent/service/AgentTask.kt) は名前・要約・モデル・ID・時間・前景/背景・提供された結果/エラーだけを持つ。
raw prompt、任意のraw JSON、推定したモデル・usageは保持しない。[TaskPayload](../../src/main/kotlin/com/cursoragent/parser/TaskPayload.kt) で型と上限を検査する。

- **ACP標準:** session/updateのsessionIdと同一turnのtoolCallIdで更新。rawInputの`_toolName=task`でTaskを識別し、既存status/content/locationsの更新を再利用する。
  TaskのrawInput/rawOutputは提供時に対応する全項目を置換、欠損は保持、明示null/不正型は対応項目を未取得にする。実終了確認は最新のwire statusを使い、完了後に再進行が来れば未終了として扱う。子failedの表示保持とは分離する。
- **cursor/task:** 同じ接続の有効prompt・既存Taskまたはkind=otherのtoolCallIdに限って補足情報を付ける。明示的な別session、未知ID、Stop後、終端後は無視する。
  標準toolより先に来た補足は保留キューを作らず破棄する。標準toolが後から来ても架空の結果で補わない。
  前turnで使ったIDの再利用は、遅着との区別ができないため補足を結合しない。接続内の履歴は4,096 IDまで保持し、上限後は補足の結合を停止する。標準tool表示は継続する。
  requestは既存の未対応エラー`-32601`を一度返し、notificationには返信しない。入力カード・成功応答・子実行は生成しない。
- **print:** 確定済みの親sessionとcall_idを照合し、同一行を更新。初期化前/別session/欠損・不正IDはTaskへ配送しない。
  `result.error`を子失敗として扱い、親のresult.successやexit 0と区別する。successとerrorの両方がある場合は失敗を優先し、矛盾する成功結果を使用しない。
  `conversationSteps[].assistantMessage.text`だけを取得できた子の結果として表示し、完全transcriptと呼ばない。

要求のagentId、resume ID、成功結果のagentIdは別項目。cursor/task.params.agentIdは公式の再開引数説明と実測順序の解釈が一致しないため、**「cursor/taskのagent ID（用途未確認）」**として別欄にする。再開やリンクに転用しない。
IDは文字列かつ非空、2,048文字以内を要求し、切り詰めて照合しない。名前などの表示文字列は2,048文字、エラーは8,192文字、結果の各textは32,768文字/先頭128 steps/合計65,536文字まで。文字数による省略には「表示上限」を付ける。
durationMsは数字だけの整数number/整数string、0〜31,536,000,000 ms（365日）に限定する。小数・指数表記・負値・bool・overflow・上限超過は未取得。所要時間を開始時刻・親の経過時間・usageへ加算しない。

## 表示・保存・終端

[TaskToolCard](../../src/main/kotlin/com/cursoragent/ui/timeline/TaskToolCard.kt) は既存timeline内の安定した1行。更新時も詳細の開閉と行位置を保つ。本文・エラーはJTextAreaの文字表示でHTML/画像/リンクを実行しない。
標準content/locationsがあれば既存StructuredToolCardと差分操作を詳細内で再利用する。未取得の子本文、モデル、usage、内部進捗を推定しない。
背景開始のtool完了は「背景実行（子の終了は未確認）」と表示する。親Stop/失敗/完了で未完了の子は「終了を確認できません」へ移し、子の失敗表示は親成功で上書きしない。完了後に再び進行中が届いた場合は過去の結果を保持しつつ「終了を確認できません」と表示し、最新wireが未終了なら既存の不確定終了/復元拒否へ進む。

[Factory](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt) は既存run token/EDT/Stop/dispose検査の内側で配送し、子ごとの開始通知を増やさず既存の親完了/エラー通知を使う。
#98の未実装APIを仮定しない。将来の共通折畳み・通知整理では、この安定した行と親通知の経路を接続対象にする。
保存は「ツール: 子Task（確認できた状態）」相当の許可された要約のみ。Taskの指示・名前・任意エラー・結果全文・ID・モデルを無条件保存しない。復元は既存の静止した要約行で、再実行や再接続をしない。
#254のprint本文全量更新、#44の保存形式、ACP不確定終了時の復元禁止、#48の親turn成功条件は変更しない。

## 根拠・比較・検証範囲

[調査#118の固定契約](https://github.com/shinma06/cursor-in-android-studio/blob/44ce1defbf092d514f6d203c7488c134602cba37/docs/research/issue-118-subagent-contract.md) と公開投影を再利用する。
[test fixture](../../src/test/resources/task/issue-118-projection.json) は同固定版のJSON投影2配列の値を保持し、テストでprintの既知tool_call envelopeだけを復元する。新たなprovider実行・認証・原本取得はしていない。
2026-09-12に [Cursor Subagents](https://cursor.com/docs/subagents)、[Cursor ACP](https://cursor.com/docs/cli/acp)、[ACP tool calls](https://agentclientprotocol.com/protocol/v1/tool-calls)、[JetBrains ACP](https://www.jetbrains.com/help/ai-assistant/acp.html) を再確認した。
Cursor IDE内panelの委譲・親への結果返却、JetBrains AI Assistant + Cursor ACP + configured MCP + IntelliJ MCP Serverの既存連携能力を比較対象にする。子実行やtool詳細そのものを独自機能と呼ばない。本変更の対象は親会話・タブ・停止・保存に整合した結果表示であり、競合GUIの同等以上UXは未受入。

TaskProtocol/TaskPayloadテストは公開投影・型・置換・成否とIDの境界。AcpSessionTestのfake serverはrequest/notification・別session・Stop/終端・再利用IDを確認する。
TaskToolCard/TaskDispatchテストは同一行更新・展開・安全な保存/復元・run/タブ/EDT所有を確認する。既存ACP/print/queueの回帰も通常JUnitに含む。
これらの合成試験は実IDEの表示・キーボード・通知・live停止観測を代替しない。[Case263](../verification/changes/issue-263.json)はpendingで保持する。
背景子の自然完了、親Stop後の子の物理停止、再開成功・context保持、全モデル/全子型・子usage・完全transcriptは未観測。再開拒否を回避する追加試行をしない。
