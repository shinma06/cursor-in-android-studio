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

## 次の固定build観測

指定operatorがhost-wide leaseを取得し、新しい専用profile/fixtureへ診断ZIPを入れる。SHA/ZIP hash/loaded JAR、IDE/CLI/model、permission/root、MCP設定と承認・接続状態を固定して、元条件の**新会話に接続確認1回**。元run07 sessionを再送しない。独立review前の実運用配布はしない。

失敗ならUI送信時刻/会話/turnとprivate診断traceを照合し、最初の失敗行を後始末によるclose行と分ける。`terminal=true` / `phase=await-child-exit` / 残子ありと、prompt中の切断を区別する。必要なprocess対応は指定operatorが所有PIDだけを私的記録へ採取する。成功でも単回の未再現として扱い、run07原因解明や#150比較Case passにしない。回答後の自動再送・強制的な復元はしない。

観測後、所有app/子process停止とfixture復元を確認してleaseを解放し、原因に応じて#440で最小修正または制約を判断する。#150と[QA #152](https://github.com/shinma06/cursor-in-android-studio/issues/152)へ双方向で結果/未確認/main残を引き継ぐ。#146原本・公開承認・既存ownerを維持する。#440は診断PRの統合だけではcloseしない。
