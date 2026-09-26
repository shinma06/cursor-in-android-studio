# #150 Android選択対象: 制御fixtureと実比較手順

初版2026-09-12、統合時更新2026-09-20、新候補への切替2026-09-26。関連: [SDK/能力境界と未達一覧](issue-150-android-selection.md)、
[T17/T18](acp-feature-migration-2026-09-09.md)、[GUI coordination](../development/gui-coordination.md)、
[検証台帳](../verification/README.md)。

**旧fixtureは保全先不明。PMが新候補で全比較をやり直す案を採用したため、[新fixture](../verification/fixtures/android-selection/README.md)を実行対象とする。旧候補の復元・hash一致は主張しない。C-C01の未保存XMLは3観測で不一致2・正答1。#438で間欠的な不一致を追跡する。Aの保存済み対照は選択文字列不一致、BのACP接続準備は回答後の終了不確定（#440）。他29件の正式Caseは未実施。三経路の比較pass・優位性は示さない。**
旧PR #265の資料scopeは`gui_required=false`だったが、新候補の比較Caseは経路別に[正本JSON](../verification/changes/issue-150.json)へ登録する。
Case IDは登録済み。指定operatorが実行ごとの固定候補・構成・結果をJSON台帳へ記録し、生成物を二重編集しない。

## 1. 開始条件と比較する三つの経路

共通fixture・目的・許可範囲を固定し、次の三経路を別々に記録する。

| 経路 | 固定する構成 | 主な比較対象 |
|---|---|---|
| A | AI Assistant + Cursor ACP + IDE integration + IntelliJ MCP + 利用可能な関連MCPの最強構成 | JetBrains上で既にできる操作と訂正負担 |
| B | Cursor in Android Studioの固定build + Aと同じCursor ACP/関連MCP | Aに対する直接IDE統合の差 |
| C | 最新安定版Cursor **IDE内Agent panel** + 利用可能なIDE integration/MCP tools | 本プロジェクトの機能・操作フロー・フィードバックの基準 |

Cは独立Agents Window/Cloud/CLIの観測で代替しない。公式の入口は[Cursor Agent](https://cursor.com/docs/agent/overview)、
[MCP](https://cursor.com/docs/mcp)。実行時に版と実画面を固定し、AのACP画面をCの証拠として流用しない。
Cで利用できる最も強い関連tool構成を確認し、A/Bと共有できない構成・モデル版は差として記録する。
CにAndroid Studioの選択/run状態が必要なCaseでは同じ固定Android Studioへ接続する経路と権限を記録し、
取得できないときは人間による対象の伝達/切替も操作数へ含める。推測した対象を取得成功にしない。
C01の未保存Documentは各経路のeditorを対象とし、Cursor/Android Studioのどちらで選択した値かを記録する。
BのAndroid直接読取が未実装なら「未実装」と記録する。API表だけでBのpassを作らない。
必要な計測probeを追加する場合は別途scopeと副作用をレビューし、製品実装の先行採用にしない。

指定GUI operatorが次のlockを埋める。未取得は`unknown`、構成不能は組合せ・理由・次の担当を記録して`blocked`にする。

| Lock | 記録する値/成立条件 |
|---|---|
| IDE/plugin | 実際に起動するIDE full build、Android plugin/AI Assistant/MCP Server/Debugger MCP toolset版、enabled/load状態。9/12のSDK照合対象は`AI-261.26222.65.2614.16204760`。実比較の対象build/hashを再固定し、変更時はAPI照合をやり直す |
| Agent | A/Bは同じCLI full version・ACP protocol・明示model ID。CはCursor full version/commit・channel・実行日・panel/モード・model ID・関連extension版を別記録し、内部Agent版が非公開ならunknown。同じmodelを選べない場合は比較条件差を明示。Autoで混在させず、既存認証の公開値はplan種別のみ |
| MCP | endpointはprivateに保持。公開記録はserver/toolset名・版、direct catalogとrouter-only catalog、input schema/権限・実動結果。片側だけ無効化して優位性を作らない |
| Fixture/build | 下記§2の新fixture source/4 APKを使用。新候補のversion lock、sourceとAPKのhashを照合し、各経路へ同じ固定候補の専用コピーを渡す。旧候補の準備記録を同一性の証拠にしない |
| Device | D1/D2の2台を同時に認識。実serial→alias対応はprivate。公開値はalias/API/ABI/emulatorまたは物理/device状態。両方で選んだminSdk以上、APK実行可、ADB認証済み。D1/D2の起動と4 APK各2台へのinstallは準備で確認済み。Case内の実行対象一致は未確認 |
| GUI/evidence | host-wide lease、operator、固定plugin ZIP/hash、開始時刻、Case ID、証拠保存先/公開投影方針を記録。通常の利用projectは使わない |

2026-09-26の対応候補（Quail 4のinstall/loadと限定したMCP直接読取は下記追記、Quail 1は対応宣言のみ）:

| 実行IDE候補 | AI Assistant | MCP Server |
|---|---|---|
| Quail 4 | [261.26222.135](https://plugins.jetbrains.com/plugin/22282-jetbrains-ai-assistant/versions/stable/1177320) | [261.26222.30](https://plugins.jetbrains.com/plugin/26071-mcp-server/versions/stable/1088193) |
| Quail 1 | [261.23567.214](https://plugins.jetbrains.com/plugin/22282-jetbrains-ai-assistant/versions/stable/1177317) | [261.23567.174](https://plugins.jetbrains.com/plugin/26071-mcp-server/versions/stable/1034317) |

A/Bは同じ実行IDEへ揃える。製品ZIPの標準Quail 1/JDK21 buildと、実行IDE/JBRは別々に識別する。
Quail 1だけで比較した結果をQuail 4へ一般化しない。実行前に対応版と利用可能toolsetを再確認し、
2026.2の公式Helpにあるtoolが261系にもあるとは推定しない。

compatible MCP/toolsetが対象Android Studioで使えない場合は、その正確な版の組合せをblockedとして残す。
別IDE/buildが必要なら別比較行を追加し、同条件比較と混ぜない。現行資料はIDEAのtool存在を示すだけでAndroid Studio互換を保証しない。

### 2026-09-26の環境確認と再開条件

記録は[専用IDE/MCP run](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5845508768)と
[経路C・代替操作 run](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5845576094)、
[本人setup後のreadback](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5845647045)、
[描画停止とACP準備の切分け](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5845686708)、
[復旧・認証後の版差とMCP切断](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5845833740)、
[IDE生成SSE設定とSDK再照合](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5845915201)、
[A/B同版化の既存ランチャー確認](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5845952081)、
[run終了と未実施条件](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5846021374)。
fixture sourceはcandidate manifest、製品buildは`5444c1c148bdc7a1b7b15fab143fa41826e8b711`、
ZIP SHA-256は`00b81fde4f7040609b9c5460710d3baa0c6f896e23563cf6e313ceaddd84a80e`。配置JAR照合後に専用IDEで起動した。
先行setup runはprompt0。後続runではAの接続確認と本人の挨拶の計2送信が完了したが、Case本体は未実施。
この段階では全30件が環境`blocked`だった。後続run06でC-C01のみ単回実施し`fail`へ更新した。全human欄は`pending`を維持する。

| 確認対象 | 実観察と限界 |
|---|---|
| A/Bの環境 | Quail 4 Patch 1 `AI-261.26222.65.2614.16379836`、AI Assistant `261.26222.135`、MCP Server `261.26222.30`を専用profileでload。run07でBもA管理CLIを指定しACP/model応答を確認したが、Bの正常終了とMCP同条件は未成立 |
| D1/D2 | API37・arm64-v8aの専用emulator2台。起動完了と4 APK各2台へのinstallは準備証拠。run/debug/log対象一致を確認した証拠ではない |
| MCPの実接続 | 実ユーザー承認後に有効化、Brave modeは無効。initializeとdirect catalog27 toolを取得。再起動後のinitializeも成功しfixture限定のproject設定を準備。Agentによるtool利用とrouter/追加toolsetの全体は未確認 |
| MCPの直接読取 | `get_project_modules`はmodule名/type、`get_run_configurations`はAndroid設定2件と`supportsDynamicLaunchOverrides=false`、`get_all_open_file_paths`はactive/open fileを返した。候補一覧と選択module/Variant/deviceは別で、Agent経路のCase passではない |
| CLI/ACP/model | 単独CLI `2026.09.23-86fc751`の直接ACP probeはinitialize/session-new成功（protocol1、mode3候補、model40候補、prompt0、初期`composer-2.5[fast=true]`）。一方、Aが実際に起動したregistry管理CLIは`2026.09.02-c22c1a3`、UI表示は`composer-2.5`。本人認証後の接続確認は成功。run07でBの設定をこの管理CLIへ合わせACP/model応答を確認したが、Aのmodel option、B正常終了、共通MCPの照合は残る |
| Cの入口 | Cursor `3.22.7` stable、commit `37076c6c3f9e253c0fa2305197e45befd13a2260`。本人ログイン・IDE別ウィンドウ起動後のrenderer停止は専用アプリ1回の再起動で復旧し、IDE内Agent panelとGrok 4.7 / High / 256K / Fastを確認。公式downloadは3.22 Latest、更新確認は更新なしだったが、配布commitとの完全一致は未確定 |
| CからのMCP | HTTP接続は27 toolを表示した後、`roots/list`応答のrootに`file://`がないというJSON-RPC errorが発生。これは空白入りproject pathの例外とは別。最初のSSE URL変更ではHTTP409/再接続上限。その後、IDEのCopy SSE Configが生成した`type:sse`を含む設定でSSE接続を確認したが、同じroot URI error（HTTP400）でfailedへ遷移した。Copy Stdio Configを保存して専用Cursorを再起動したが、画面操作の不整合で有効化を確認できなかった。再起動後ログは対象serverのnone → disconnectedのみで、Stdio起動や互換性失敗の証拠ではない。Agentからのread成功は未確認 |

空白を含むproject pathではMCPの3読取が`URISyntaxException`となり、空白なしの同一sourceコピーで3件とも成功した。
[JetBrains公式](https://www.jetbrains.com/help/mps/mps-projectional-agent-toolkit.html)はMPS向け説明で同例外をplatform制限IJPL-236112として記載する。
Android Studioでの観察は上記runの別証拠であり、MPS資料のみから互換性を推定しない。
Cursorの専用user-data pathはIPC socketの長さ制限にも注意する。起動成功と対象process引数を確認してからCuaを接続する。
長いpathで起動失敗した後にCuaが通常instanceを開いた観察は除外し、編集・送信なしで終了した。

[IDEAの公式Debugger説明](https://www.jetbrains.com/help/idea/agentic-debugging.html)ではdirect toolsは2026.1.3以降、router modeは2026.2以降。
今回のdirect catalogにdebuggerがないことだけでAndroid Studioでの利用不能とは判定せず、追加toolsetの互換性・load・公開設定を再開時に確認する。

| 経路 | 現在の阻害条件 | 次ownerと具体操作 |
|---|---|---|
| A | 管理CLIとcomposer-2.5表示を確認。保存済みRed対照で選択文字列不一致。MCP受渡し・正式未保存Caseは未確認 | GUI担当がMCP構成とmodel optionを固定し、選択文字列の再確認と正式C01を実施する |
| B | A管理CLIへの設定保存とACP/model応答は確認。接続確認の回答後に終了不確定、MCP同条件未確認 | #440で原因・終了契約を特定。再送せず、固定buildの再確認後に正式Caseへ戻す |
| C | HTTP/公式SSEはroot URI error、Stdioは有効化未確認。MCP不要のC-C01はrun06/run07で不一致2・正答1 | 次GUI担当が新queue/leaseで#438の再確認とA/B比較を行う。C02以降はStdioとAgent readを別途確認し、独自proxyで比較条件を変えない |

[JetBrains公式ACP設定](https://www.jetbrains.com/help/ai-assistant/acp.html)のCustom Agentは`~/.jetbrains/acp.json`を使用する。
導入済み261版の`AcpConfigurationLoader.getConfigFilePath()`も`user.home/.jetbrains/acp.json`を返し、専用IDE config directoryへの切替は確認できなかった。
ユーザー共通設定の変更や内部registry packageの差替えは行わない。A管理版の公式`cursor-agent`ランチャーはそのまま起動でき、promptなしのACP initialize/session-newでprotocol1・40モデル・初期`composer-2.5[fast=true]`を確認した。run07ではBの既存CLIパス設定にこのランチャーを保存し、ACP初回のmodel応答を確認した。設定一致と接続応答は、B物理終了の確認・MCP同条件成立とは分ける。最新単独CLIおよびC本体との版差は別に残る。
MCPの[Roots仕様](https://modelcontextprotocol.io/specification/2025-06-18/client/roots)は`file://` URIを要求する。
今回のSSE errorはCursorの`roots/list`応答とこの要件の不一致を示す。endpoint/project pathへ形式を付け足す独自変換は行わない。

人間操作の前に新queue/leaseと専用profile/fixture windowを確認し、人間の操作中はAgentのGUI操作を止める。
Aの追加install/認証/同意や新OS権限要求は既存承認へ混ぜず、該当地点で止める。人間による補助操作はsetupの操作数へ数える。
先行run `i150-compare-20260926-04` は環境修復3回・接続確認2送信で終了した。CuaのAX treeと画面の不一致、`elementHasNoFrame`、clipboard timeout/画面取得errorにより、MCP不要のC01も未保存editor操作・Agent送信へ進めなかった。これは比較対象製品の失敗ではなく、操作環境のblockedとする。
所有IDE・Cursor・ACP Agentの停止を確認し、期限前にleaseを解放済み。元fixture 18ファイルは変更なし。追加の人間操作依頼は残っていない。当時は全Case未実施であり、#150/QA #380を閉じない。

既存権限のSystem Events/AX経路も[run05で再確認](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5846203318)した。専用Studio/Cursorのprocessは認識されvisible=trueだが、window一覧は双方0件。Studioのwindow 1読取はinvalid index（-1719）。同時期にCuaではfixture画面を取得できたため、権限不足・アプリ未起動・製品failのいずれとも断定しない。実行元はChatGPT.app内のCodexCLIからosascript/System Eventsであり、Terminal.appの権限状態とは同一視しない。OS権限変更・Case操作・送信は行わず、所有2アプリ停止・lease解放済み。この制限はrun05のSystem Events経路の観察に限定する。

### C-C01の初回観測（run06）と適用限界

[run06の実測・復元記録](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5846466736)では、既存権限の直接AX APIで専用appを前面化するとwindow取得・標準UI操作が可能になった。OS権限変更はしていない。System Eventsのwindow 0件から全操作経路の利用不能を推定しない。操作経路の詳細と修復履歴はIssue記録、原UI証跡はprivateに保持する。

| 条件・結果 | 実観測 |
|---|---|
| 固定構成 | Cursor 3.22.7 stable / commit `37076c6c3f9e253c0fa2305197e45befd13a2260`、IDE内Agent、明示`Composer 2.5 Fast`。新fixture source `5b2353f56d6b3f0539cfcb47ca4dc2b71646c965`。他のIDE/plugin固定値は上記環境lockを維持 |
| 入力 | Red/Blueの同名Kotlin/XMLを切替。Red XMLの12行目labelだけ標準Replaceで`I150_UNSAVED_RED`へ変更し、editor全体と元diskを照合。16文字を選択してCmd+Lで`activity_main.xml (12)`を添付。質問に期待markerを含めずmodule/path/範囲・文字列とeditor/diskの区別を要求、保存/変更/コマンド実行は禁止 |
| 正しく返した部分 | module `appRed`、相対path `appRed/src/main/res/layout/activity_main.xml`、12行目、未保存marker |
| 期待値不一致 | diskも`I150_UNSAVED_RED`で「差はありません」と回答。独立読取のdiskは`I150 appRed`のまま。回答は行全体を示し、選択16文字の範囲との区別も未確認 |
| 計測と限界 | 1送信、UI表示Worked for 10s。通常操作数・訂正回数・総時間は準備修復と分離計測できていない。3反復・順序条件・A/B同条件実測は未完了。tool表示にRead/Exploredはあるが、内部toolがbufferを読むかは未確認 |
| 復元・次の確認 | editorをRevertし元diskとの一致、元18ファイル無変更を確認。所有アプリ/関連ACP停止・lease解放済み。[#438](https://github.com/shinma06/cursor-in-android-studio/issues/438)で再現性・回避・比較への反映を追跡 |

この初回観測は経路Cの期待値不一致で、自製品Bの不具合やCursor全版の仕様ではない。以下のrun07再確認を含め、全指標の測定完了・三経路の優位性・機能採用を意味しない。MCP StdioのAgent利用は未検証のままで、MCP不要の観測から他Caseへ成功を転用しない。

### run07の再確認・対照と接続準備

[3回目の結果](https://github.com/shinma06/cursor-in-android-studio/issues/438#issuecomment-5846920076)、[A/B追加観測](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5846971572)、[終了記録](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5846989864)を公開証拠とする。原UI・保存会話・ログはprivateでoperatorが保全。raw wireや内部tool返却内容は公開証拠に含めない。

Cの3観測はすべて上記固定Cursor/fixture、新規会話、明示Composer 2.5 Fast、同名4ファイルの切替、Red XMLの12行目16文字、`activity_main.xml (12)`添付。各回でeditor全文が元diskのlabelだけを未保存markerに置換した内容と一致し、diskは旧値のままと照合した。質問は次の同文で、期待markerを含めない。

> 現在選択したXMLについて、module、project相対path、選択範囲の行番号・正確な文字列を示してください。editorの未保存内容とdisk保存済み内容に差がある場合は区別してください。別moduleの同名fileを代用せず、保存・変更・コマンド実行は行わないでください。

| 観測 | 送信確認 / 回答・復元確認（UTC） | editor / diskの回答 | UI時間・探索表示 | 操作・訂正の計測限界 |
|---|---|---|---|---|
| run06・第1回 | 12:44:27 / 12:47:22 | 同じ未保存markerと誤回答 | 10s、Read activity_main.xml | 通常操作数・準備訂正数・総時間未計測。回答への訂正送信0 |
| run07・第2回 | 13:51:28 / 13:52:49 | 同じ未保存markerと誤回答 | 10s、Read activity_main.xml, activity_main.xml | 準備に操作再試行あり、回数を完全計測できていない。回答への訂正送信0 |
| run07・第3回 | 14:06:57 / 14:08:05 | 未保存marker / I150 appRedを正しく区別 | 27s、Explored activity_main.xml / settings.gradle.kts, 3 searches | ファイル切替4操作は時刻記録。置換・選択の失敗再試行と添付審査待ちを含み総数・通常時間未計測。回答への訂正送信0 |

上記終了時刻は観察・復元確認時点であり応答生成終了の精密計時ではない。UI時間も準備込み総時間ではない。新規会話で初期model表示がGrokへ戻る場合はComposerを再選択し、送信前にreadbackした。探索表示の違いは観測事実に留め、正答の原因や一般的発生率は推定しない。**不一致2・正答1のためC-C01のfailを維持するが、常時再現とは扱わない。** 各回のeditor復元・元18ファイル一致を確認。正式なA→B→Cの各3回ローテーションは未開始。

| 別条件の観測 | 入力・結果 | 限界・次の確認 |
|---|---|---|
| C・保存済みRed対照 | 新規会話、同文、Composer 2.5 Fast。保存済みlabel 11文字を選択・12行添付。module/path/行・文字列・差なしに正答。14:08:59準備開始、14:10:00送信、14:10:41確認（UTC）。UI8s | 送信まで9操作（新規1、選択1、添付1、model3、入力2、送信1）、入力再試行1を含む。formal C01の未保存条件と混ぜない |
| A・保存済みRed対照 | AI AssistantのCursor ACP、管理CLI 2026.09.02-c22c1a3、composer-2.5表示、IDE context enabled。保存済みRedの11文字を選択し同文送信。14:14:31送信、14:16:34確認、UI30s。module/path/12行目は正解、選択を「スペース3個＋android:」と誤回答 | 回答のUTF-16範囲418..429を元fileで照合するとI150 appRed（12行23列から11文字）。native editor選択とも一致。1観測・MCP同条件未成立・正式未保存条件なしのためA-C01はblockedを維持 |
| B・ACP接続準備 | 同じ管理CLIの設定保存後、ACPへ切替。初回の読取/変更/コマンドを禁止した接続確認を14:11:38に1回送信。composer-2.5[fast=true]と「接続確認」を受信し、UI約14秒で実行終了不確定のエラー。保存turnもfailed | stopReason・切断理由・B物理終了の瞬間は未採取、原因帰属未確定。再送なし。独立調査[#440](https://github.com/shinma06/cursor-in-android-studio/issues/440)とACP QA #152へ。正式B-C01は未実施でblocked |

run07は送信5/8で終了。専用Studio/Cursorを通常UIで閉じ、記録した子processと管理ACPの残存なし、fixture元18ファイル一致、GUI lease解放を確認した。Blue対照は未実施。A/B/Cの最強MCP構成・同条件比較、他29正式Case、人間確認、採用判断、main反映は引き続き未完了。

## 2. 準備済みfixtureの受取と具体仕様

新候補は[保全・再実行手順](../verification/fixtures/android-selection/README.md)と同居sourceを正本とする。
固定済みのsource commit/tree・source archive/4 APKのSHA256と静的検証結果は[新候補manifest](../verification/fixtures/android-selection-candidate.json)を参照する。
2026-09-26に旧保全先不明を確認し、[新候補案](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5844771037)をPMが採用した。
旧原本の受領は未解決履歴として保持するが、新比較の開始条件は**新source/4 APK/lock/再実行手順の受領照合**へ変更する。
新候補を固定後、A/B/Cすべての8選択組・C01–C10を最初から確認する。旧成功の移植や一部経路だけの差替えはしない。

<details>
<summary>旧候補の準備・受領blocked履歴（新比較の固定値として使わない）</summary>

[PMの準備完了記録](https://github.com/shinma06/cursor-in-android-studio/issues/150#issuecomment-5645727837)を正本参照とする。
baselineは`55a4e28a05c2756a36046f395cc6d1991cdfa58a`、AGP `9.1.1` / Gradle `9.3.1`。
最終commitのoffline4assemble・manifest/package/debuggable/permissionなし・署名照合は準備時の記録であり、
IDE import/sync・Run configuration・D1/D2・C01〜C10の実行成功ではない。

| APK | 準備済みartifactのSHA-256 |
|---|---|
| appRed / debug | `dae5ffd17aece514b722acbe8b643ac945f462eb9bd48a9f221e255d14c9e456` |
| appRed / release | `718267261acef072a653e093f66625cd97afc7dd16c31ae5b40012145854c8aa` |
| appBlue / debug | `26b6211c32a224f5954aeb5b16d6139395135a2a032c4999bbc2f743cf15f9a2` |
| appBlue / release | `5830acfbc56672f0d81a84dbefebb54530e378bf856de555956cadee803eae20` |

2026-09-20のPM照合では当時の一時原本は現存せず、保全コピーの所在と現物は未確認。
PMが受領先を確定するまでは受領blockedとし、準備時点の成功記録を現在の現物一致へ読み替えない。
指定GUI担当は準備記録に対応するsource・4 APK・version lock・依存証明・再実行手順を受け取り、
sourceの`git rev-parse HEAD`/clean状態と全APKのSHA-256を照合してから専用コピーを使う。
受取場所はprivate handoffで確認し、手元にない場合はPMをownerとする受取待ちとしてblockedにする。
不一致を新規projectの作成で埋めない。再buildでAPK hashが変わる場合も、原因・入力差・新hashを別候補として固定し、
全経路の比較をその同一候補からやり直す。基準source/artifactは上書きしない。

</details>

以下の仕様・受入は新候補にも維持する。


新しい使い捨てproject名は`issue150-selection-fixture-v2`。2つのAndroid application moduleを持ち、Composeを使わず
各moduleで`buildFeatures.viewBinding = true`。XML `activity_main.xml`は縦方向に次のViewだけを置く。

- `TextView @+id/targetLabel`: `module|variant|applicationId|runNonce|pid`を表示。
- `Button @+id/emitMarker`: `観測ログを出す`。`I150_TARGET` tagへ同じ識別情報と単調増加counterを1件出す。
- `Button @+id/crashNow`: `この実行だけ失敗させる`。押すと`IllegalStateException("I150_EXPECTED_CRASH:" + runNonce)`を投げる。

Kotlin Activityは`ActivityMainBinding.inflate(layoutInflater)`のrootを表示する。
`runNonce`は各process開始時に生成するUUID、PIDは`android.os.Process.myPid()`。
button listener内のmarker生成行にdebugger用breakpointを置く。Logcat/UIの期待値は実際のpackage/PIDと照合する。
fixtureに個人データ・ネットワーク・DB・外部アカウントは不要。DBクエリ/認証の受入対象はない。

| Module | namespace/base application ID | Variantと区別 |
|---|---|---|
| `:appRed` | `dev.example.issue150.red` | `debug`: suffix `.debug`、`release`: suffixなし |
| `:appBlue` | `dev.example.issue150.blue` | `debug`: suffix `.debug`、`release`: suffixなし |

両moduleは同じ`MainActivity.kt`/`activity_main.xml`というファイル名を持ち、module名だけで中身を区別する。
`targetLabel`のmoduleは固定文字列、variant/application IDは生成された`BuildConfig`を使う
（必要ならfixtureだけで`buildFeatures.buildConfig = true`）。debug/releaseの2 Variantとし、flavorは追加しない。
releaseは非debuggableのまま、使い捨てfixtureのdebug signingを明示して2deviceへの起動比較を可能にする。
配布用署名・鍵の利用は不要。debuggerの成功Caseはdebugのみ、releaseへのdebug要求は明示的な未対応を期待する。

run configurationを`I150 Red`/`I150 Blue`として各moduleに固定する。
新source/4 APKはfixtureの保全手順で照合し、IDE import/syncとrun configurationの設定は未確認として実行担当へ残す。
新候補のbuild成功も実IDE/実deviceの成功へ読み替えない。

基本選択8組はすべて行う:

| Case key | Module | Variant | Device | 期待application ID |
|---|---|---|---|---|
| R-d-1 | appRed | debug | D1 | dev.example.issue150.red.debug |
| R-d-2 | appRed | debug | D2 | dev.example.issue150.red.debug |
| R-r-1 | appRed | release | D1 | dev.example.issue150.red |
| R-r-2 | appRed | release | D2 | dev.example.issue150.red |
| B-d-1 | appBlue | debug | D1 | dev.example.issue150.blue.debug |
| B-d-2 | appBlue | debug | D2 | dev.example.issue150.blue.debug |
| B-r-1 | appBlue | release | D1 | dev.example.issue150.blue |
| B-r-2 | appBlue | release | D2 | dev.example.issue150.blue |

## 3. 操作・期待値・現在の状態

各CaseはA/B/Cで同じfixture状態から開始する。正本JSONのIDは`A-C01`〜`C-C10`の30件で、経路間の部分成功をまとめてpassにしない。主にT17がeditor/selection/model、T18がexecution/device/log/debugger。
「既存toolなし」はdirect/router双方と権限を確認した場合のみ記録する。未実装、環境blocked、timeout、製品failを分ける。

| Case / 対応 | 操作 | 期待する判定 | 現在 |
|---|---|---|---|
| C01 / T17 | RedとBlueの同名XML/Kotlinを切替。Redのlabelだけ未保存marker `I150_UNSAVED_RED`へ変更し選択範囲を渡す | module/相対file/範囲/未保存Documentを正確に識別。diskの旧値やBlueと混同しない。保存せず復元し後続を汚さない | A: 同条件未成立、保存済み対照で不一致。B: 接続準備の終了不確定#440でblocked。C: fail維持（不一致2・正答1、#438） |
| C02 / T17+T18 | 上記8組を選択、同期安定後に対象snapshotを取得してrun、label/logを照合 | IDE選択module/Variant/device、実application ID、実行device、execution IDの対応一致。候補一覧と選択値を混同しない | 環境blocked（上記再開条件） |
| C03 / T17 | Red/debug/D1の読取開始直後にBlue/release/D2へ切替。Variant更新/sync中と完了後に再読取 | 古い結果を新対象へ貼らない。sync中はstale/更新中を明示し、完了後に新しい世代へ一致 | 環境blocked（上記再開条件） |
| C04 / T17 | fixtureのGradle設定末尾に一時的な構文errorを追加しsync。失敗後に復元し再sync | sync失敗を空module/成功扱いにしない。旧modelなら古い旨を表示。復旧を認識。他project/ユーザー設定は編集しない | 環境blocked（上記再開条件） |
| C05 / T18 | BlueのActivityへ一時的なコンパイルerrorを入れて`I150 Blue` run。復元/build後に起動しcrash buttonを押す | build失敗・起動成功・runtime crashを区別。失敗した実行から別run/deviceの成功を返さず、対象に属するerrorだけ表示 | 環境blocked（上記再開条件） |
| C06 / T18 | D1/D2で起動した状態からD1だけ切断またはemulator停止。D1対象の読取/runを要求し、その後再接続 | offline/disconnectedを日本語で明示。D2へ自動転送しない。再接続後にdevice/process同一性を取り直す | 環境blocked（上記再開条件） |
| C07 / T18 | 同じappをD1/D2で動かし、Red/Blue両方が同じlog tagを出す。対象appを再起動し古いmarkerも残す | device/package/PID/runNonce/起動区間で限定。直近5秒/100件/32 KiBを検査。他app/deviceと旧processの履歴を返さない | 環境blocked（上記再開条件） |
| C08 / T18 | 同一device・同一PIDだが時刻/runNonceが異なる2起動分の合成Logcat入力を用意 | PID一致だけで古い実行へ結合しない。これはPID再利用の境界fixtureであり、OSの実PID再利用を実測した扱いにはしない | 環境blocked（上記再開条件） |
| C09 / T18 | Red/debugとBlue/debugの2 debug sessionを開始しbreakpointで停止。対象sessionのstack/許可marker変数を取得、resume後に旧frameで再要求 | 明示session/execution IDを使う。suspended時だけ値取得、resume後はframe失効。releaseはdebug不可を明示。既存Debugger MCPの能力も測る | 環境blocked（上記再開条件） |
| C10 / T17+T18 | bounded読取中にCancel、fixture project close、またはdevice切断。ログを100件超生成 | 期限/取消/打切りが明示され、古い結果・購読が残らない。他session/processは停止しない。上限を超えるログ/変数は公開しない | 環境blocked（上記再開条件） |

C08は将来の経路が合成入力を受け取れる場合に実施する。受け取れない実IDE/MCP側を未確認のままpassにせず、
実PID再利用未観測として残す。OSへPID再利用を強制するための無制限process生成は不要。
Caseを通すためのbuild変更やprobe追加が必要なら、その差分も固定候補へ含めてから再実行する。

## 4. 比較記録と採用条件

共通prompt例: 「比較fixtureを開いたAndroid Studioで選択しているmodule・Variant・実行先deviceと、その実行の直近ログを確認してください。
対象が不明、切替中、取得不可なら理由を示し、別の対象では代用しないでください。」
失敗/debugger Caseには該当操作だけを追記する。A/B/Cとも新規sessionで同じprompt、同じ許可範囲を使う。
CはIDE内panelの実画面・tool履歴を独立保存し、CaseごとにCの操作/判断/復帰導線とBの対応を記録する。
全Caseは各経路でpendingから開始する。未実施・構成blocked・実測した未対応を分け、Cが未測定ならCursor同等以上の判定は保留する。

各行に`Case / fixture commit / IDE+plugin build / model+CLI / route A|B|C / panel evidence / actual tool+version /
selected target / observed target / status / user操作数 / target訂正回数 / 結果までの時間 /
許可UI / 日本語error・復旧導線 / 証拠 / blocker・owner・next action`を記録する。
実serial/PID等の対応はprivate evidenceで検証し、公開時はaliasへ投影する。取得できない値はunknownとする。
初回setupの操作数と通常turnの操作数を分け、手動で答えを補った操作も数える。

基本8組は各経路で1巡し、採用候補の差が出たCaseだけA→B→C/B→C→A/C→A→Bと順序を回して計3回確認する。
時間は中央値と範囲を記録するが、少数試験を一般性能差とは呼ばない。対象誤一致が1件でもあれば優位性は保留する。
機能の存在だけでなく、切替・同期・失敗・取消を含む正確性とユーザーの訂正負担で判断する。
A対Bの上積みとC対Bの機能/操作フロー/フィードバックの同等以上を別々に判定し、一方の結果で他方を代用しない。

採否は次の順序で決める。

1. 既存MCP/IDE機能で満たせる: 再利用し、同等能力を独自機能と呼ばない。追加実装なしも正常な結果。
2. 同条件で繰り返せる不足があり直接読取で改善する: 対象安全性、既存UI再利用、日本語での判断しやすさを評価し、最小1機能だけ採用Issueへ。
3. 互換環境・device・測定経路が揃わない: exact combinationと次操作をblockedに記録し、優劣/採用を未判定に保つ。

現在は2を支持する実測がなく、採用Issueは作成していない。#150の実受入・GUI・最初の1機能選定は未完了。
