# #440 ACP終了不確定の切分け

2026-09-26。[Issue #440](https://github.com/shinma06/cursor-in-android-studio/issues/440)の原因調査・再開時に読む記録。source基準はdevelop `0663203b447f7fecc5bc2fbc9a1266e8d1a001e4`。調査状態・担当・固定build結果の正本はIssue/PRと[Case JSON](../verification/changes/issue-440.json)。本資料は原因確定や修正完了の宣言ではない。

## 確認できたこと

| 観測 | 条件と結果 | 証明しない範囲 |
| --- | --- | --- |
| #150 run07 B接続確認 | 製品HEAD `5444c1c148bdc7a1b7b15fab143fa41826e8b711`、ZIP SHA-256 `00b81fde4f7040609b9c5460710d3baa0c6f896e23563cf6e313ceaddd84a80e`、Android Studio `AI-261.26222.65.2614.16379836`、公式管理CLI `2026.09.02-c22c1a3`、`composer-2.5[fast=true]`。回答「接続確認」後、経過約14秒で終了不確定。保存会話はACP/1 turn/failed | prompt-end、生wire、直前の子process状態・切断理由は未採取。製品/CLI/環境の原因帰属は未確定。再送せず専用app/ACP停止、fixture元18ファイル一致をoperatorが記録 |
| 空root、直接ACP制御 | 新しい使い捨てGit root、新provider session。同CLI/model、製品と同じfs/terminal=false、session/newのmcpServers=[]。合成の接続確認を1回。assistant delta→end_turn、12秒接続維持、stdin closeでexit 0 | Python制御は製品runner/IDEの試験ではない。run07とroot・環境が異なる |
| 空root、製品AcpSession | 同CLI/model、JVM 21のJUnitから既存runnerを呼び、新sessionへ同じ合成promptを1回。end_turn→COMPLETED、復元gateを解放、close後process停止。一時診断以外の終了判定は変更なし | GUI/loaded ZIPは通していない。run07のIDE環境・project設定・タイミングを完全再現していない |
| 合成MCP設定あり | 外部IDEへ接続せずtoolsを持たないstdio serverを専用rootのmcp.jsonへ配置。prompt0のsession/newでも、製品runnerの新sessionからの1送信でもserver起動なし。送信はend_turn→COMPLETED、close後停止 | 起動していないのでMCP常駐の対照試験は不成立。MCP原因説の反証にも成功証拠にも使わない。実fixtureのMCPや承認設定は変更していない |

追加provider送信は計3、すべて新sessionで各1回。initialize/session-newのみの確認は2回（空root/合成MCP root）、送信0。元の失敗会話の再送・load/resume、GUI、実IDEのMCP起動、認証/hook設定変更は0。実験用のstdout診断と実CLIを呼ぶ一時JUnitは製品・通常テストへ含めない。原記録と対応ID/時刻は私的証拠に保持し、path・host・provider session・raw内容は公開しない。

## 原因候補と未解決条件

run07と本source基準でAcpSession/ProcessTree/起動経路の差分はない。製品はpromptのstopReasonを受理しても、観測した子processの終了を最大10秒確認する。残子・識別不能・切断等は不確定となり、同会話再送とproject寿命中の復元を拒否する。約14秒という時間だけで、この待機timeoutだったとは確定できない。

- [仮説] 元fixtureにはstdioのIDE MCP設定があり、空rootにはない。常駐子が終了待ちに影響した可能性はあるが、run07でその子が起動/残存した証拠はない。[Cursor ACP](https://cursor.com/docs/cli/acp)はproject/userのmcp.jsonを利用し、server承認が必要と説明する（9/26確認）。未承認の合成設定だけで同条件とは扱わない。
- [仮説] 直接probeでは既存hook由来の短命子も観測した。run07の遅延・残存・識別失敗の原因とは確定できず、hookの無効化/改変は行っていない。
- 切断、未知/不正応答、子の識別やサンプリング時の競合も、旧buildが原因を記録していないため除外できない。providerエラーやOS状態を推測して成功判定を緩めない。

## 採用する最小診断

[AcpSession](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt)は、成功した静止、turn内失敗、接続close、cancel timeoutでローカル診断を出す。runごとの無作為traceは診断行の相関専用で、tab/chat/provider IDや保存IDには使わない。

記録は `source`、`phase`（preparing/prompt/await-child-exit）、終端受理の有無、型付け済みoutcome、Stop状態、接続/親processの生存、観測子数/生存子数とする。close理由は[AcpJsonRpc](../../src/main/kotlin/com/cursoragent/acp/AcpJsonRpc.kt)が生成する固定client messageだけ。本文、raw wire、providerのerror message/data、root、実行コマンド、PID、認証情報、例外本文/stackは渡さない。子数はその瞬間の参考値で、未知ならunknownとし、追加の終了判定には使わない。

診断callbackの失敗は既存の取消・cleanup・復元gateへ影響させない。timeout、子監視、終端判定、再送/復元禁止、保存形式を変更しない。これは原因を採取するための差分であり、run07の障害を直した差分ではない。

合成回帰は[AcpSessionTest](../../src/test/kotlin/com/cursoragent/acp/AcpSessionTest.kt)の正常2turn、EOF、残子取消を再利用し、秘密を含むprovider errorと壊れた診断callbackでも、不確定・復元拒否・所有process回収を保つことを追加確認する。fake serverの結果を実Cursor/GUI合格へ転記しない。

## 非公開のprocess対応診断

通常ログだけでは、同じCLI・同じIDE親の複数ACPと会話を対応付けられない。候補一覧取得の `prepare()` も送信前に接続を作るため、出生時刻だけを会話の確証にしない。

専用IDEのJVM system property `cursor.agent.acp.privateDiagnosticsDir` に、operatorが事前作成した**絶対パス・POSIX 0700の専用directory**を指定した場合だけ、[AcpPrivateDiagnostics](../../src/main/kotlin/com/cursoragent/acp/AcpPrivateDiagnostics.kt)へ相関記録を出す。通常は無効。IDEの `idea.log` や会話本文、provider通信には出さない。repository/共有・同期folderへは指定せず、property/実パスと生成ファイルはprivate実行記録だけで扱う。POSIX権限を確認できない環境は記録しない。

- 接続process作成時: `event=process-started`、ローカルtab UUID・保存conversation UUID、PID、OS process開始時刻、記録時刻。これはOS起動の記録で、initialize/session-new成功の証拠ではない。
- prompt dispatch時: `event=prompt-dispatch`、同じ対応情報と既存終了診断のtrace UUID。dispatchは送出を試みる境界であり、providerの受信確認ではない。provider session ID、本文、コマンド、root、wire、例外は渡さない。開始時刻がnullならPID再利用を除外できないため対応確定に使わない。
- 各記録は新規0600 JSONファイル。既存ファイルへ追記/上書きせず、symlink directoryは拒否する。IDE寿命あたり最大128回の記録試行で停止し、operatorが必要な記録の存在・項目を確認する。欠落を成功や接続不在と推測しない。
- 診断は固定値とローカル識別子だけ。記録エラーは黙って棄却し、既存の終端判定/timeout/取消/再送/復元gateを変更しない。採取後はpropertyを外し、private証跡はoperatorが保全/整理する。

新会話の接続準備後、process-started記録からtab/conversationとPID+開始時刻を対応付けてから送信する。送信後はprompt-dispatchのtraceを通常終了診断へ結び、OS側の所有process時系列と照合する。これはメニュー操作失敗の修正でも、run13の残子3件の原因確定でもない。callback故障時の正常2turn・既存の不確定保護と、保存先権限/上限/項目制限を合成テストで確認する。実IDEは固定ZIPで別途検証する。

## 非公開の失敗snapshot

run17では最初の失敗が `phase=prompt / terminal=false` で、既存相関記録には子孫の個別識別情報がなかった。`terminal=false` は応答未受信だけでなく、stopReasonや未完了toolの検証失敗でも成立する。外側の逐次process観測とも同時刻とは限らず、子数だけで所属や原因を断定できない。

同じopt-in先へ、送信後のturn失敗・接続close・cancel timeoutのうち最初に観測した1件を `event=first-failure` として追加する。`site` は呼出箇所、`category` は固定enumの例外分類（本文/stack/クラス名なし）、`resultStage` は NOT_RECEIVED / STOP_REASON / TOOL_STATE / ACCEPTED、`terminal` はその時点の受理状態。NOT_RECEIVEDは成功result callbackの未開始を示し、RPC errorや接続失敗も含む。並行する失敗は最初に記録権を取ったobserverを残すため、根本原因や全threadの厳密な時間順を保証しない。

`children` は既存監視が保持する最大512件だけを、追加探索・削除せず採取する。各項目はPIDと保持中の開始時刻、同じ開始時刻のprocessが生存するか（true/false/null）、取得できた現在のparent PID/開始時刻。nullは取得不能、alive=falseは終了またはPID再利用による不一致であり、現在の別個体を同じ子と扱わない。parentはalive=trueかつ識別を再確認できたときだけ採取し、nullを「親なし」の確証にしない。全体取得不能はchildren=null、空集合とは区別する。逐次読取りなので同時snapshotではなく、記録時刻も出生時刻とは異なる。

通常ログは変更せず、0700保存先・0600新規ファイル・128記録試行の上限を共有する。診断は当該経路のcleanup開始前に試みるが、並行する別経路のcleanupを止めない。診断失敗を伝播させず、結果を終了/timeout/再送/復元判定に使わない。`AcpSessionTest` の合成RPC error・不正stopReason・未完了tool・残子取消と、`AcpPrivateDiagnosticsTest` の項目/権限/上限で検証する。原因確定・実IDE受入は別途必要。

## run23で特定した終了障害と残る制約

2026-09-27。[観測引継ぎ](https://github.com/shinma06/cursor-in-android-studio/issues/440#issuecomment-5853026750)の固定製品sourceは `5799f28a429d8919d33d677ab4bfadad422895cb`、CI ZIP SHA-256は `2ea6978236a866f62b64447f1e93d6b12d87337be7dd296ae25d4e22cbb9d3f0`。operatorの配置・ロード照合と、保全済みprivate相関JSON/OS時系列を照合した。診断と終了判定のsourceは本調査base `fcbdc6af37c7c10be13cebec581999cf38918c45` でも同じ。管理CLIは `2026.09.02-c22c1a3`。Bは接続先の既定modelを選び、接続後の設定表示は `composer-2.5[fast=true]`。provider内部のモデルは未検証。

### 確定した順序

1. 新規B会話のprocess-startedとprompt-dispatch、first-failureは、tab/conversation・ACP親PID/OS開始時刻・traceが一致した。PID単体やIDE直下の出生順による推定ではない。
2. prompt-dispatchの約78〜79秒前から、ACP直下にJetBrains MCP stdio bridgeとCursor worker server、そのworker配下にTypeScript language server関連2件が存在した。合計4件は送信直前・直後のOS観測にもあり、first-failureの保持個体/開始時刻/親関係と一致した。これらをturn中に新しく起動した処理とは扱わない。
3. MCP許可要求にRejectを1回返し、回答を受信。first-failureは `site=CHILD_EXIT / category=ACP / resultStage=ACCEPTED / terminal=true`。同traceの通常診断は `phase=await-child-exit / outcome=COMPLETED / stopped=false / connectionClosed=false / processAlive=true / observedChildren=4 / liveChildren=4`。provider終端を受理した後も4件が生存していた。
4. その後、client自身の接続close診断が続き、次の外側OS観測では当該親と4件が消失した。接続closeが最初の失敗原因だったという順序ではない。外側観測の該当約161秒間の最大間隔は0.747秒で、短命子・サンプル間の挙動・消失の厳密な瞬間は保証しない。終了時のfixture18ファイル一致と全所有処理停止はoperatorの記録を保持する。

**直接の阻害条件は、送信前からの常駐子4件にも全退出を要求する製品の静止判定である。** `AcpProcessTree.awaitQuiet()` は子が残れば正常なprompt endだけでは完了させない。10秒期限による失敗と整合するが、現診断のcategory=ACPは例外理由を分離せず、終端受信の厳密時刻も記録しないため、期限到達と一時的な個体照会失敗の最終的な識別まではできない。先行run07/13/17の原因をこの結果から遡及確定しない。Rejectが常駐子を作った証拠もなく、実際に4件は送信前に存在した。許可UIの問題は別の[#447](https://github.com/shinma06/cursor-in-android-studio/issues/447)。

[ACPのprompt turn](https://agentclientprotocol.com/protocol/v1/prompt-turn)のstopReasonはturnの終端を表す。[Cursor ACP](https://cursor.com/docs/cli/acp)はproject/user MCP設定の利用をサポートする。いずれも、この製品が必要とする全OS子孫の退出を示す信号ではない（2026-09-27確認）。従ってCLIのACP仕様違反とは断定せず、製品の安全契約と常駐構成の不整合として扱う。

### 最小案の評価と採否

| 案 | 評価・採否 |
| --- | --- |
| 送信前baselineの個体、process名、MCP種別を終了待ちから除外する | 不採用。出生時点は分かっても既存worker/bridgeがturn中の仕事を受けたか・停止したかは分からない。OSのsleep状態もアプリの静止保証ではない。待機期限を延ばしても常駐という条件は変わらない |
| prompt-end受理後、専有ACP接続と観測子を終了させてからturn完了にする | 単回の終了方式として検討可能だが現状の最小修正では採用しない。`stopProcess()` はこの接続の観測子と親を対象とするが、成功を返すAPIではなく失敗はprojectを不確定にする。`onClosed` はquiescent前の接続closeを不確定にする。同会話の次turnは同じresident sessionを再利用する契約で、終了後の `session/load` / `resume` は未実装。単回を完了にしても2turn目を失う。外部IDEへ渡ったMCP処理の終了をbridgeの消失だけで証明することもできない。採用には意図したcloseと障害closeの区別、停止結果/子回収、会話継続と別tab非干渉の固定build受入が必要 |
| turn完了・次prompt受付とrestore可否を分ける | 将来の設計候補。既に `WorkspaceOperationGate.tryPrepare()` と `tryRestore()` は分かれているが、`AcpSession` は物理静止を確認できない接続を再利用しない。継続には「終端は受理済み、物理静止は未確認」という別の状態・UI/保存/取消/遅着契約が必要。不確定runを成功に置き換えたり、既存失敗会話へ再送したりする変更は行わない |

本変更は製品runtime・timeout・復元gate・再送禁止を変更しない。合成 `resident-child` はsession/new時に子を起動し、prepare完了後・prompt前に生存を確認してから正常end_turnを返す。`AcpSessionTest` でCHILD_EXIT/ACCEPTED、子の同一個体・生存、COMPLETED診断と不確定outcomeの区別、cleanup、同接続再送拒否、project復元拒否を確認する。これは制約の再現テストであり実CLIやGUIの合格ではない。

#150のMCPをそろえたB経路は依然として利用可能性の阻害があり、B全Caseの同条件比較を完了できる根拠はない。ただし個別Case未実施をすべてfailとせず、正式結果は#150の正本で管理する。MCPを外す/printへ切り替える対照実験は別条件であり、B ACP＋MCPの代替合格にはならない。A/Cの独立比較は継続可能。Bの次操作は上記候補の安全契約をPMが選定し、必要な実装・回帰/固定build検証を割り当てること。#440を閉じず、#150/#152の全既存Case・main未達を保持する。private相関証拠は指定operator/PMがアクセス管理し、PID・絶対パス・command・wireを公開しない。

## 次の固定build観測

指定operatorがhost-wide leaseを取得し、新しい専用profile/fixtureへ診断ZIPを入れる。SHA/ZIP hash/loaded JAR、IDE/CLI/model、permission/root、MCP設定と承認・接続状態を固定して、元条件の**新会話に接続確認1回**。元run07 sessionを再送しない。独立review前の実運用配布はしない。

失敗ならUI送信時刻/会話/turnとprivate診断traceを照合し、最初の失敗行を後始末によるclose行と分ける。`terminal=true` / `phase=await-child-exit` / 残子ありと、prompt中の切断を区別する。必要なprocess対応は指定operatorが所有PIDだけを私的記録へ採取する。成功でも単回の未再現として扱い、run07原因解明や#150比較Case passにしない。回答後の自動再送・強制的な復元はしない。

観測後、所有app/子process停止とfixture復元を確認してleaseを解放し、原因に応じて#440で最小修正または制約を判断する。#150と[QA #152](https://github.com/shinma06/cursor-in-android-studio/issues/152)へ双方向で結果/未確認/main残を引き継ぐ。#146原本・公開承認・既存ownerを維持する。#440は診断PRの統合だけではcloseしない。


## 終端と新規送信の安全条件（2026-09-27）

この節はbase `d83711454c7dcbf8cc23be0d93c20324003dea8d` に対する再検討。正常な回答と全toolの終端だけで常駐子を除外する案は採用しない。[ACP prompt turn](https://agentclientprotocol.com/protocol/v1/prompt-turn)はprompt完了後の次promptを認めるが、OS子孫や外部IDEの静止を保証しない。[tool calls](https://agentclientprotocol.com/protocol/v1/tool-calls)のcompletedはtool呼出しの状態で、独立processの全退出証明ではない（9/27確認）。

管理CLI `2026.09.02-c22c1a3` の配布コードでは、`handlePrompt` が `processPrompt` の復帰をend_turnへ、`toolCallCompleted` をcompletedへ写す。常駐子/外部IDE作業の静止確認はこの変換にはない。これは当該版の静的確認であり、全内部経路の実行検証ではない。run25でも実MCP返却と回答が得られた後に残子1件で終了不確定となったが、first-failure snapshotは採取できていない。[run25観測](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5853640982)の範囲を広げない。

反例はAcpSessionTestの `completed tool can leave a writer and uncertainty blocks another prepared tab before dispatch`。一時fixture内の実Python childをturn中に起動し、execute toolのcompletedとend_turnを送った**後**にchildの書込みを解放する。回答終端はACCEPTEDでもファイルが変わり、childは生存する。これはCursorが必ず同じ動作をする証拠ではなく、提案条件だけでは排除できない状態である。送信前から生存するchildの既存回帰も維持する。tool名や出生時刻をread-only/常駐の安全証明へ転用しない。

| 境界 | 判断と修正 |
| --- | --- |
| 回答表示 | 受信済み回答/tool結果は表示できる。実行成功・静止・Case全項目passとは分ける |
| 同会話の次送信 | 不確定後の拒否を維持。end_turnだけで復帰させず、失敗promptを再送しない |
| 別会話/transportの準備 | 旧tryPrepareは不確定でも通り、metadata接続はgate外だった。同じ作業領域へ別writerを開始できる安全上の欠陥のため、不確定後はproject共通gateで拒否する |
| 準備済みturn | ACPの接続開始・initialize/session-new/prompt dispatch、printのprocess起動予約で再確認。別tabがsession/new待ちの間に不確定化する回帰を通す。開始受理済み処理への一括Stopは追加しない |
| 復元 | project寿命中の禁止を維持。接続closeや所有child回収後も解除しない |

この修正は常駐MCPを伴う複数turnの実現ではない。外部IDEのbuild/run、MCPが返却済みでも継続する仕事、未観測detachはACPのprocess treeに入らない可能性がある。共通gateは**検出済み不確定後**の開始を防ぎ、あらゆる外部仕事を検出/停止する契約ではない。安全な継続には実行所有者からの停止/静止確認が必要で、現行ACPの終端値だけからは構築できない。

### B比較を再開する条件別手順

1. #150指定operatorがleaseと固定ZIP/loaded identityを確保し、同じCaseのA/BでIDE・CLI/model・fixture・MCP接続先/承認・未保存状態等を照合する。差がある項目を同条件と推測しない。失敗snapshot用0700 directoryと相関記録の存在を送信前に確認する。
2. 専用IDE/project寿命ごとに、新しいB会話へ**1 promptだけ**送る。Caseが要求する実MCP返却、tool対象、回答、IDE操作を各々記録する。終了不確定でも採取できた項目の証拠は残すが、全Case passへ繰り上げない。複数turnを要する項目は継続不能として記録し、fresh sessionを同会話2turnの代用にしない。
3. 診断・UI終端と最初の失敗を対応付け、未実行手順はblocked、実施して期待を満たさなかった手順はfailとして#150のownerが判定する。この資料から#150のCase状態を一括変更しない。
4. 所有ACP/childに加え、起動した外部IDEのbuild/run等も担当operatorが終了確認する。確認できない処理があれば次runとfixture復元を止め、所有者へ引き継ぐ。未知processを推測でkillしない。完全cleanup後にfixture基準へ戻し、専用IDE/projectを新しくして次Caseを行う。tabを増やすだけ、再起動するだけではcleanupの証明にならない。

新規送信拒否の固定build GUI Caseは[issue-440.json](../verification/changes/issue-440.json)へ追加し、#150/#152へ移管する。#440の常駐継続問題・従来の診断GUI未達・main反映は残る。反例の合成passを実Cursor/GUI受入へ転記しない。
