# #278 実行中入力とローカルqueueの境界

2026-09-12 / base `9979266b4e99e7d4dc46689dac1f61f33f09b88a` / [PR #280](https://github.com/shinma06/cursor-in-android-studio/pull/280)。

## 結論・採否

本Pluginでは [#48](https://github.com/shinma06/cursor-in-android-studio/issues/48) の**前turn終端後に次turnを送るローカルqueue**を維持する。
ACP/非TTY printへ実行中の入力を送る専用契約は、今回確認した公開資料・固定schema・広告では確定できなかった。
同時`session/prompt`、print stdinへの追記、Stop後の自動再送をsteeringの代用にしない。

`/goal`は新規ACP sessionでbuiltin skillとして広告され、ACP/print各1回の合成promptに正常回答した。
ただし開始/active/paused/complete/再開を示すgoal状態は得られず、永続goalの成立は**未確認**。
広告コマンドの通常呼出しは既存採用 [#258](https://github.com/shinma06/cursor-in-android-studio/issues/258) の範囲で扱えるが、専用goal管理UIを新たに採用する根拠にはしない。
本研究で追加製品Issueは作らず、未確認条件を親 [#25](https://github.com/shinma06/cursor-in-android-studio/issues/25) の有限な再評価条件へ引き継ぐ。
[#97](https://github.com/shinma06/cursor-in-android-studio/issues/97) の送信キー設定・IME受入も停止/変更しない。

## 公開経路の比較（2026-09-12確認）

「対応確認」はここでは**公式仕様の確認**と**live観測**を分記する。未掲載は全版・全経路の非対応を意味しない。

| 対象経路 | queue / steering | side question | 永続goal |
|---|---|---|---|
| Cursor IDE内Agent panelの公式説明 | Enterでqueue、Cmd/Ctrl+Enterで即時入力、直近user messageへ追加する説明あり。今回GUI未確認 | local side chatの公開説明あり。ただし対象IDE版/配置の同等性は未実測 | `/goal`の段階提供説明あり。installed IDE内panelでの対応は未確認 |
| Web / Agents Window | Send now・Enter二回で次のtool境界へ投入、Tabで次turnへqueue。Web提供済み/AW rolloutという公開条件 | local side chatとCloudを混同しない | rollout。今回Cloud/AWを起動していない |
| interactive CLI | Enterでsafe boundaryへsteer、もう一度Enterでinterrupt（2026-08-11 changelog）。公式対応確認、TTY live未実施 | `/btw`はread-onlyの一時overlayで履歴へ残さない（2026-04 changelog）。公式対応確認、TTY live未実施 | `/goal`とCtrl+Cによるpause、idle/headlessをまたぐ継続が文書化。rollout/gated、状態live未確認 |
| 非TTY print | `--print`/出力形式/初期promptは公開。実行中入力のstdin framing/受付/帰属/取消契約は未確認。追記を試していない | `/btw` overlayの非TTY入出力契約は未確認。通常質問文への置換は不採用 | 合成`/goal`1回がexit0/FIVE_278。専用状態eventなし。永続性・pause/resumeは未確認 |
| ACP v1 + Cursor拡張 | 通常`session/prompt`と終端、取消は公開。今回の資料には同一sessionの同時promptをsteeringへ変換する保証なし。専用入力methodは未確認 | `cursor/ask_question`はAgent→clientのblocking質問で、ユーザー起点の`/btw`ではない。side chat専用契約も未確認 | available_commands_updateでgoal広告、通常promptで1回FIVE_278/end_turn。goal状態の標準/拡張method・通知は未確認 |
| Cloud Agents API v1 | `POST /v1/agents/{id}/runs`は次runのfollow-up。既存runがCREATING/RUNNINGなら`409 agent_busy`。このendpointで同時steeringは**非対応と明記** | 今回未確認。公式Side chatsは現在local-onlyと説明 | 今回未確認。Cloud Agent/workersを起動していない |

一次資料:

- [Cursor Agent Overview](https://cursor.com/docs/agent/overview): IDEの既存queue/即時入力と、Web/AW/CLIの新steering・goalの説明を区別する。名称が同じでもキー/提供面を一括移植しない。
- [CLI Changelog](https://cursor.com/docs/cli/changelog): 2026-08-11のsteering/goals、2026-04の`/btw`、2026-07のside question修正を確認。[Slash reference](https://cursor.com/docs/cli/reference/slash-commands)に`/goal`はあるが`/btw`は掲載されておらず、一覧1つの欠落を非対応判定に使わない。
- [CLI Parameters](https://cursor.com/docs/cli/reference/parameters)、[Cursor ACP](https://cursor.com/docs/cli/acp): 非TTY入力flagや専用steering/side/goal状態のCursor拡張は今回確認できない。`agent acp`自体はdefault helpで非表示でも公式経路である。
- [ACP Prompt Turn](https://agentclientprotocol.com/protocol/v1/prompt-turn): 原requestにstopReasonを返すturn、完了後の次prompt、cancel時の原requestの終端を規定。[Architecture](https://agentclientprotocol.com/get-started/architecture)の複数session並行と、同一sessionの同時promptは別。
- [ACP Slash Commands](https://agentclientprotocol.com/protocol/v1/slash-commands): 広告されたcommandを通常promptのtextで呼び出す。独自の`session/goal`等を発明しない。
- [Side chats](https://cursor.com/help/ai-features/side-chats): `/side`は親履歴を参照する**永続する子会話**。自身のtranscriptを持ち、closeはarchive。`/btw`の履歴に残らない質問やAgentが実行するSubagentとも別。記事はlocal-onlyとし、IDE内panelの固定版配置は今回未確認。
- [Cloud API / Create A Run](https://cursor.com/docs/cloud-agent/api/endpoints#create-a-run): 新run・agentId/runIdとbusy拒否を規定。Web UIでsteering可能という説明からAPIの同時入力を推定しない。

比較対象は [JetBrains AI Assistant + ACP](https://www.jetbrains.com/help/ai-assistant/acp.html) に
[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html) と利用可能なIDE toolsを渡した構成まで含める。
[AI Chat](https://www.jetbrains.com/help/ai-assistant/chat-mode.html)の会話/選択contextは既存能力であり独自性としない。
MCP/IDE toolの能力はモデルが操作できる対象を増やすが、ユーザー起点の実行中入力methodや履歴帰属の証拠にはならない。
同じqueue/steering/side/goal UIが競合で可能かは固定IDE/AI Assistant/Agent版のGUI比較が必要で、今回「本Pluginが同等以上」とは結論しない。

## Schema・installedの観測

CLI `2026.09.10-fd3934a`の`--help`と`acp --help`を取得した。初期prompt、print出力、resume等はあるが、
専用mid-turn入力optionは見当たらない。acpのoptionはhelpだけ。非公開実装の逆解析や未知flagの試行はしない。

一次schemaは [bcb9d7e / schema/v1](https://github.com/agentclientprotocol/agent-client-protocol/tree/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1) を固定した。
[公開投影](issue-278-observations.json)にstable/unstableのhashと確認したproperty名を記録する。
両方のPromptRequestはsessionId/prompt/_meta。SessionNotificationはsessionId/update/_metaで、prompt request IDを必須にはしない。
複数promptを並行に送ってもJSON-RPC応答IDだけでは各session/updateのturn帰属を保証できない。
`_meta`の未知値を推定で意味付けせず、将来の公開拡張があれば再照合する。

schemaのgoalという単語はPlan/PlanEntryの一般説明に現れるが、durable goalの状態modelではない。
Agentがclientへ求めるpermission/elicitation/質問と、clientが実行中Agentへ追加するinstructionは方向が違う。
[#146](https://github.com/shinma06/cursor-in-android-studio/issues/146) の初期permission/質問/Plan/cancel wireの再実測・原本閲覧は行っていない。

## 有限probe（追加のモデルpromptは計2回）

全て空の使い捨てroot、新規session、既存認証、合成textのみ。modelは`composer-2.5[fast=true]`、modeはAsk。
ID/実path/思考本文/私的command一覧を公開せず、authorがrawから許可fieldだけ抽出した投影を添付する。
独立Reviewerの公開投影検査とrawの独立再実測は区別する。

| Case | 手順 | 観測と限界 |
|---|---|---|
| CATALOG | initialize(protocolVersion=1, client fs/terminal=false)→session/new(mcpServers=[])、3秒通知を収集、promptなし | loadSession=true、image=true、audio/embeddedContext=false、sessionCapabilities.list。23 commandのうちgoalはbuiltin skill広告、btw/sideは広告なし。これは全CLI slashの不存在証明ではない |
| PRINT-GOAL | `agent -p --output-format stream-json --stream-partial-output --trust --mode ask --model 'composer-2.5[fast=true]'`へ下記promptを1引数で渡す。stdinは閉じ、90秒上限 | FIVE_278、exit0、is_error=false。system1/user1/thinking10/assistant3/result1、tool event0。goal状態eventなし |
| ACP-GOAL | 別新規sessionでgoal広告を確認し、set_config_optionでmode/modelの返却currentValueを確認→通常session/promptで下記textを1回送る。120秒上限 | FIVE_278、end_turn。commands1/mode1/info1/thought14/message3のupdate、client callback request0。goal状態eventなし |

```text
/goal この合成検査の唯一の目標は 2+3 を計算して FIVE_278 だけを返すことです。
返答したら目標は完了です。ツール、ファイル、外部データ、委譲、繰返し実行を使わないでください。
```

両経路ともstderrは0 bytes、終了後workspaceにファイル0。ACPは原promptの終端後に今回のprocessだけ終了させた。
CLI provider側の内部goal登録や「完了」への遷移は公開応答から確認できない。
`/goal`文字列を送れたこと・簡単な答えが正しいこと・end_turnは、永続goalが有効になった証明ではない。
idle継続・中断/再開・process再起動・履歴圧縮は未実測。明示的拒否は今回発生せず、拒否を迂回した試行はない。

TTY steering/btwは公式仕様確認に留めた。今回の採用判断はPluginの構造化入力接続に必要な契約が得られるかであり、
TUI表示の再現だけではACP/printの入出力契約を解決しない。Cloud API、同時ACP prompt、print stdin追記も実行していない。
必要な追加証拠は後述のR1–R5に限定する。無期限の調査や代替Agent機構を追加しない。

## #48と現行コードのID・終端・保存

比較するqueueは [PR #279](https://github.com/shinma06/cursor-in-android-studio/pull/279) の固定
`dd655160e136a44250f990932a3fe77d686f951f`（base9979266、独立レビュー済み・未統合）。
[固定queue仕様](https://github.com/shinma06/cursor-in-android-studio/blob/dd655160e136a44250f990932a3fe77d686f951f/docs/development/prompt-queue.md)
とPromptQueue/Controllerの実配送をread-onlyで確認した。未統合sourceは複製しない。

| 所有対象 | ID/受付の意味 | 完了・取消・履歴の境界 |
|---|---|---|
| 未送信draft | SessionTabのdraft/caret。入力しただけではrunを作らない | 別tabのdraftへ混ぜない。実行中submit無視という既定を勝手にqueue/steerへ変えない |
| #48の明示queue entry | queue固有IDと本文・登録時mode/model。Conversation IDのcontrollerが所有 | dispatch時にgeneration/revision/先頭entry/選択tab・会話/idle/disposeを再確認。未送信は永続化しない |
| 次turn | SessionTabs.beginTurnで新しいSessionRunTokenとturnId。prepareTurnでAgentRunと操作予約を取得 | 既存run中はbeginTurn/Controllerが拒否。queueが始めたturnも通常SavedTurnへ保存。開始前拒否はentryを保持 |
| ACP | tabごとのresident AcpSession + provider sessionId、promptごとのJSON-RPC id | Activeがある間は次sendを拒否。原promptのstopReasonとtool/子process静止を確認して終端。session/updateのsessionIdはrun識別子の代わりにならない |
| print | promptを起動引数に渡すprocess、provider session_id。今回の入力は実行中stdinへ書かない | Resultやtool完了だけで物理終了としない。OS終了をAgentRun.completeへ渡し、意図Stop/errorを優先する |
| 保存 | Plugin Conversation ID、SavedTurn.id、ChatMessage.id。provider IDは別field | recorder.beginでuser本文、assistant/tool要約、finishでturn状態。表示データを保存し未送信queueや実行命令を再開時に実行しない。baseの保存ACP会話は閲覧のみ |

現行source: [SessionTabs](../../src/main/kotlin/com/cursoragent/session/SessionTabs.kt)、
[Controller](../../src/main/kotlin/com/cursoragent/ui/AgentUiController.kt)、[prepareTurn/print](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt)、
[AcpSession](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt)、[AgentRun](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt)、
[TurnSettings/Workspace](../../src/main/kotlin/com/cursoragent/service/TurnWorkspace.kt)、[保存model/recorder](../../src/main/kotlin/com/cursoragent/history/Conversation.kt)。

AgentRunは完了を1回だけ通知し、Stop時は通常本文/error配送を抑える。ACP Stopはsession/cancel通知と既存の取消/静止確認であり、
「方向変更の受付」ではない。旧runを止めて新runを作れば別turnであり、mid-turn成功として表示しない。
JSON-RPC request ID、provider session ID、Plugin turn/message ID、Cloud run ID、診断用Request IDを相互の代用品にしない。

## UI契約と採否

| 操作 | 今回の判断・既存動作 | 再採用に必要なもの |
|---|---|---|
| 実行中に入力 | draftとして保持。勝手に送らない | 既存#97のIME/mention/改行/送信方式の受入 |
| queue登録/一覧/編集/削除/順序 | #48の明示操作を採用済みとして維持。登録は本文/mode/model、auto context/実行設定は次turn開始時snapshot | #48の固定GUI Case。#24の明示添付は別契約としてqueueごとに保持する統合確認 |
| 未登録draftの手動送信 | 残queueをpause、idleになってから通常の次turn | 実行中に通す新しい抜け道を追加しない |
| モデル/mode変更 | 現行runの設定は固定。#48予約の登録時選択と、未登録draftの次回選択を分離 | providerがmid-turn設定変更を保証する契約がない限り現在runへ変更適用と表示しない |
| Stop/失敗 | #48は残queue保持pause。自動再送/次の予約へ進めない | 独立したsteering受付・取消・拒否契約が得られた場合だけ別採否 |
| tab切替/New Chat/履歴移動 | #48元tabのqueueをpause。現在のrunと他tabのrunは維持 | 新操作は宛先tab/session/runを固定し、遅着を別会話へ配送しない |
| 一覧・編集・close確認 | modalに入る前にpause。確認取消でもpause。close/disposeで破棄 | 未送信消失を伝える既存Caseを保つ |
| 即時steer | **不採用（公開接続契約待ち）**。既定EnterやStopを置き換えない | R1/R2 |
| `/btw`一時質問 / `/side`永続子会話 | **専用UI不採用**。通常会話の複製を同じ機能と呼ばない | R3 |
| `/goal` | 広告された通常slashの選択/呼出しは#258へ。**専用永続goal UI不採用** | R4 |
| 独自loop/自動再prompt | goalやsteeringの代替として**不採用** | provider責務を再実装する根拠がなく、今回の有限scope外 |

## 残条件と有限の再評価Case

R1–R5は**未実施**。新しい公開根拠が出た時、親#25から当該範囲だけを再評価する。現在の#48/#97の完了条件には加えない。

| Case | 次に必要な証拠 | 成功/失敗を分ける観測 |
|---|---|---|
| R1 ACP mid-turn | Cursor公式のrequest/notification名、capability、元promptと追加入力のID/更新帰属規則 | 元runが継続し追加入力の受付/拒否を識別。終端と競合/重複/遅着/Stop/tab変更を確認。通常prompt並行投入で代用しない |
| R2 print/TTY | 非TTYの公開入力framingまたは新API、TTYとの版/境界の対応 | TTYは合成作業中の入力が次tool境界に反映された証拠とinterruptの差を確認。TTY文字表示だけでPlugin接続成功とはしない |
| R3 side question/chat | client起点呼出し、主run継続、質問/子会話IDと保存/破棄の契約 | `/btw`回答は親履歴へ混入しない、`/side`は別の永続履歴とarchive。#146のAgent起点質問で代用しない |
| R4 durable goal | goal ID・active/paused/completedと更新通知、pause/resume、保存/再開の公開契約または固定版の広告 | 単発回答/end_turnとgoal完了を分離。複数turn/idle/停止/再接続で同じgoalを確認し、gated拒否は回避しない |
| R5 IDE同等性 | Cursor IDE内panelとJetBrains AI Assistant + Cursor ACP + 実在するIDE MCP/toolsの識別build | キー/下書き/queue/steer/質問/保存の操作Caseを比較。GUI未確認の現段階で優位性を主張しない |

研究成果の終了条件は公開経路・実測/未実測の区分、既存queueとの境界、採否と上記再評価条件の独立確認まで。
製品ソース・GUI・#146原本・認証/設定・既存履歴・Cloud起動は変更していない。
研究PRのdevelop統合後も、標準QA/main引継ぎと親#25の他項目は別の完了確認とする。
