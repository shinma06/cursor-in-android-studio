# 現行実装の責務と境界

2026-09-12 / #228照合。ソース基準は develop `4d1514d8fa6c020d41ad9c0205b9ea24268bef57`。元の#142説明へ#147のACP接続が追加された後のコードを対象とする。設計方針は別文書、GUI/mainの結果は各QAが正本。更新時の根拠と履歴の扱いは[知識の正本](knowledge.md)。

[Project Mission](../project-mission.md) / [ACP First](cursor-integration.md) が設計方針。本書は現在のprint経路と#147のACP接続を説明する。ACPの固定build GUI合格を意味しない。機能別のACP契約・移行設計は [#115](https://github.com/shinma06/cursor-in-android-studio/issues/115)、全体順序は [#141](https://github.com/shinma06/cursor-in-android-studio/issues/141)で扱う。

## 所有関係

| 所有者 | 現在の責務・寿命 |
|---|---|
| `AgentToolWindowRootPanel` | `SessionTabs` とtab ID別のview/controllerを所有。CardLayoutで切替、同じviewの本文・入力・caret・scrollを保持。閉じると該当controllerをdispose |
| `SessionTabs` | 同期化されたメモリ内状態。plugin tab UUID、nullable chat ID、draft/mode/model/title、run tokenを保持。プロセスやSwing、ディスク保存を持たない |
| `AgentUiController` | **タブごと**のactive run/token/generation。送信準備、context/checkpoint、listenerとUI、停止・復元を調整。選択中タブへイベントを転送しない |
| `AgentProcessService` | **projectごと**の複数`AgentRun`集合、sessionの復元元記録、復元排他。printプロセス構築とstream解析・配送、tab ID別ACP接続。新しい別タブ送信で既存runをkillしない |
| `AgentRun` | 準備を含む**1回の要求**。停止要求・listener・終了の一度だけの配送と遅いprocess attachを管理。会話全体でもOSプロセスそのものでもない |
| `AgentTurnListenerFactory` | print callbackとtyped ACP eventからタブUIへのbridge。EDT上でtoken/generation/dispose/停止を再照合。tool card、session metadata、usage、終端表示を調整 |

対応ソース: [root](../../src/main/kotlin/com/cursoragent/ui/AgentToolWindowRootPanel.kt)、[状態](../../src/main/kotlin/com/cursoragent/session/SessionTabs.kt)、[controller](../../src/main/kotlin/com/cursoragent/ui/AgentUiController.kt)、[service](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt)、[run](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt)、[listener](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt)。

## 入力文書と候補popupの境界

この節は2026-09-24 / [PR #405](https://github.com/shinma06/cursor-in-android-studio/pull/405)で更新。先行Document生成とcommand対応の実装基準は`aa83b8f155cde6affe88a90c20467180246213e9`、Undo境界は同PRのレビュー修正。冒頭の全体照合基準とは別の追加実装であり、PRの固定commitとCaseを根拠とする。

Platform 261の`EditorTextField(Project, FileType)`はDocumentを遅延生成する。Document生成前の`addDocumentListener`はfield内の一覧に保持されるが、後の`getDocument()`による生成では実Documentへの接続が行われない。`GrowingPromptField`は従来のPSI-backed Documentを初期化時に取得してから、`CommandInputPanel`と`MentionPopupController`がlistenerを登録する。入力通知を各consumerで複製して回避しない。Quail1/4の実SDKでlazy生成時の接続0件・先行生成時1件を確認した登録境界の再現と、実GUIの候補表示成功は別の証拠である（[#404](https://github.com/shinma06/cursor-in-android-studio/issues/404)）。

候補選択で本文の`@`や`/`を削除するときは、write actionだけでなくIDEのcommand境界も必要。両選択処理は共有の`consumePromptTrigger`で`WriteCommandAction`を使い、既存のdocument stamp・project dispose・候補の有効性確認を保つ。[公式Document規約](https://plugins.jetbrains.com/docs/intellij/documents.html#what-are-the-rules-of-working-with-documents)と、Quail1の固定試験buildで発生した[候補選択例外](https://github.com/shinma06/cursor-in-android-studio/issues/404#issuecomment-5762029921)が根拠。通常Documentへ変更した試行で観測された例外であり、従来のPSI-backed Documentでの再現を確認したとはしない。

添付とコマンド選択は本文とは別の状態で、既存の×ボタンで解除する。triggerを消費するときは`UndoManager.nonundoableActionPerformed(DocumentReference, false)`でその入力Documentに非Undo境界を置く。選択前の本文編集履歴には戻れず、境界でのUndoはIDE標準の「取り消せない変更」案内で拒否される。選択後の本文編集はUndo/Redoでき、他Documentの履歴には作用しない。triggerだけ復活して選択が残る不整合を防ぎ、添付そのもののUndo機能は追加しない。

popupが消えた後の画面だけで「一度も開かなかった」と判定しない。入力直後・候補選択・取消・別アプリへのfocus移動を分け、IME変換中/確定EnterとShift+Enter、入力再生成後も同じ固定buildで確認する。修正後の両IDE GUIと、#205のTab修正を含む統合候補でのmain受入は[Case #404](../verification/changes/issue-404.json)と統合後のQAで未完了として追跡する。旧Phase5 #392は終了済みで再開せず、正式v0.1.0や封印RCを変更しない。

## printの送信から終了まで

1. controllerが入力を保存し、`beginTurn`でtokenとprompt/mode/model/chat IDを固定。同じタブでの重複送信を拒否する。Composerのmode/modelは生成時にアプリ設定からコピーしたタブ別選択値であり、切替のたびに全体設定へ書き戻さない。
2. `captureWorkspace` と `prepareTurn` がroot/worktree/resume、executable・permission・sandbox等を `TurnWorkspace` / `TurnSettings` / `PreparedAgentTurn` に固定。準備予約とrunを作る。後から変更された設定で進行中ターンを組み替えない。
3. EDTでactive editor/selection、VFSのfile/folder、optional Terminal APIを取得。pooled threadで実root解決、checkpointとGit contextを準備し、prompt文字列を組み立てる。準備Futureをrunが保持する設計ではなく、各段階の`run.isActive`確認で停止後の送信を防ぐ。モデル一覧取得のFutureはcontrollerが別管理する。
4. `sendPrompt` がprocess予約を取得して `GeneralCommandLine` / `OSProcessHandler` を作る。現行引数は `-p --output-format stream-json --stream-partial-output --trust` と固定済みworkspace/settings/prompt。構築中の停止後にattachされたprocessも破棄する。
5. stdout断片内の行を `StreamJsonParser` に渡し、stderrはエラー用に蓄積。init/resultで得た最初のsession IDをrun内のresume IDと対応付け、listenerがtokenを確認してtabへ結び付ける。plugin tab ID・chat ID・run tokenを同一視しない。`Result` はusage/session/fallback/errorの入力であり、OS終了の代替ではない。
6. process terminationからrunを一度だけ完了し、process予約を閉じる。listenerはEDTで内容・終端表示を反映して`finishTurn`する。stdoutだけで状態が完結するのではなく、UI操作、token、run、OS終了、復元予約も状態を決める。

[TurnWorkspace](../../src/main/kotlin/com/cursoragent/service/TurnWorkspace.kt) / [PromptContextBuilder](../../src/main/kotlin/com/cursoragent/ui/PromptContextBuilder.kt) / [MentionResolver](../../src/main/kotlin/com/cursoragent/ui/composer/mention/MentionResolver.kt) を参照。context注入は現行prompt文字列経路。`@Docs`/`@Web`はヒントであり、独自検索やMCP server実装ではない。

## 停止・タブclose・project終了

Stopはそのタブのrunへ停止要求を出す。通常Stopでは先にtokenを捨てず、`onStopped`だけ停止済みrunからの終端配送を許して表示後に完了する。以降の通常イベントは抑止する。tab closeは状態を無効化して該当controllerのlistenerをdetachし、runを停止。content終了は全tokenを無効化し各controllerをdispose、project service終了は全runを停止する。

`killActiveProcess()` は現在の名前と異なり全runの停止・detachを行うservice cleanup用メソッド。通常のStopからこれを呼んで他タブまで停止してはいけない。UI上の停止完了と物理process終了は別であり、復元予約は実終了まで保持する。

## 復元の安全性

`WorkspaceOperationGate` はproject共通。複数の準備・processは同時に許可するが、準備開始から各processの実終了まではRevert/checkpoint復元を拒否する。復元中は新規準備を拒否する。停止中の旧processは**復元を妨げるが、他の準備を一律拒否しない**。

`TurnWorkspace` が実rootを固定し、`SessionWorkspaceHistory` がservice寿命内のsession由来を記録する。DEFAULTの既知rootだけ復元可。ISOLATED、再起動後など由来不明のresume、旧XMLのroot/mode欠落は推測せず拒否する。Diffの閲覧は継続可能。

`RestorePolicy` / `FileRevertOperation` はroot一致・symlink/traversal・現在内容・未保存editor変更を確認する。Revertはそのeditのafterと一致する場合だけ戻す。checkpointはGit snapshotとroot/mode/untracked名一覧を記録するが、元から未追跡のファイル本文や作成後にstageされた新規ファイルの完全復元は提供しない。

[接続契約](../development/restore-target-integration.md) / [RestoreTarget](../../src/main/kotlin/com/cursoragent/service/RestoreTarget.kt) / [GitSnapshotStore](../../src/main/kotlin/com/cursoragent/service/GitSnapshotStore.kt) / [CheckpointHistoryState](../../src/main/kotlin/com/cursoragent/settings/CheckpointHistoryState.kt)。ACP移行でも対象の来歴、後続編集保護、取消と実終了の区別を残す。printの即時書込み実測をACPの全操作へ一般化しない。

## イベント・補助CLI・保存

- `StreamEvent` / `ToolCallPayloadParser` は現行printの構造化JSON用。未知/不正入力は防御的に扱う。completed fixtureとstarted payloadの推定を分ける。`PrintAssistantText` は実測版2026.09.10-fd3934aとpartial指定に基づきdelta/flushを分離し全文置換する（#254）。旧/未知版と契約外metadataは従来`AssistantChunkDeduper`のheuristicへ戻す。版取得・EOF・保存境界と保証範囲は[event-contracts](event-contracts.md)を参照。ACPへ流用しない。
- mode/modelとモデルオプションは実CLI IDへ対応。`ModelListParser` / `McpListParser` は補助CLIの表示文字列解析。MCP dialogは既知の`id: status`を整形し、解析不能ならraw表示する。これらはACPモデル設定やMCP tool公開の実装ではない。
- usageはprintの入力値を `TokenUsage` / context usage状態へ反映する現行表示。ACP usage/contextとCursor Todoは未実装。ACPの標準Plan表示・Cursor質問/Plan要求・permission UIは以下の範囲で実装。
- `cursor-agent-chat-history.xml` はproject単位のchat ID/preview/更新時刻のみ。開いたタブの本文はメモリ内、PRINT/ACP本文は[保存契約](conversation-persistence.md)のproject単位JSONへ保存（#44、実IDE再起動QAは別）。`cursor-agent-checkpoints.xml` はsnapshot metadata、`cursor-agent-settings.xml` はアプリ設定。保存ID/enum、`com.cursoragent.plugin`、内部tool-window/notification IDは変更しない。

型・テスト名の維持理由と再評価箇所は [監査記録](../development/project-context-audit.md)。syntheticテスト成功は実装の回帰確認であり、実Cursorのwire採取や新しいbuildのGUI合格には数えない。



## 実行状態・経過時間・ツール詳細・通知（#98）

各会話の上部に準備中→実行中/考え中/ツール実行中→完了/停止/失敗と、送信準備を含むターン全体の経過時間を表示する。`RunStatusPanel`はEDT上の会話所有で単調時計を使い、終端で時間を固定し、Controller破棄でtimerを止める。printのprocess構築後とACPのprompt dispatchを`onStarted`で反映し、Thought受信だけを考え中とする。最後の活動イベントを表示するもので、思考時間・並列tool数・推測した承認待ち・再接続状態を作らない。実際の質問/permissionカードは独立して表示する。旧形式・未知subtype・typed解析失敗のgeneric tool情報は、提供された名前をliteralな会話内カードへ残し「状態未取得」と表示する。開始の根拠にはせず、開始通知も出さない。

Stop直後は終了が未確認なら「停止を確認中」を保ち、物理終了/ACP終端の既存判定で停止または失敗へ進む。`AgentRun`とFactoryのEDT token/generation/dispose再照合を維持し、古いcallbackは新turnの時間や通知を更新しない。経過時間はproviderの計測値ではなく、履歴復元では再計測しない。

printのコマンド出力と長い要約、ACPのtool内容は既定で折り畳み、概要と提供された状態を残す。ボタンはkeyboardで操作でき、同じcall IDの更新でも開閉状態を保持する。printの同じcall IDの完了は同じ行を置換し、完了後のstartedで行や状態を戻さない。ID再利用は次の実turnで分離する。長い本文・空出力・エラー・差分の順序を保持し、raw HTMLを解釈しない。既存の子Task表示とその詳細開閉を維持し、編集カードのDiff/Revertや要求回答controlsは隠さない。

ツール開始通知は背景の会話の各turnに一度だけ送る。初回活動が前景ならそのturnは通知しない。project内の開始通知は最新1件へ置換し、旧turnの終了で別turnの通知を消さない。完了・失敗・停止の通知は各turnで独立し、設定でOFFにできる。通知の「会話を開く」は元のtabを選択する既存導線を使い、閉じたtabを再作成しない。終了とtab/project破棄で所有する開始通知を解放する。通知本文へツールのcommandやAgentのraw errorを載せない。

同一turnのエラーは会話内の詳細と日本語の失敗通知で知らせ、Factoryの重複modal割込みを廃止する。通知OFFでも会話内詳細は残す。他の設定/認証/IDEエラーdialogは変更しない。停止は途中本文が残り、適用済み編集を自動復元しないことを表示する。usage、実exit0後のRequest ID、queue停止/予約配送、非テキスト/子Task終端、Revert前のpauseは既存契約を保持する。

2026-09-13の比較根拠: [Cursor IDE内Agent panel](https://cursor.com/docs/agent/overview)はコード・検索・terminal・編集を統合する。[JetBrains AI Assistant + ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)は外部Agentとcustom/IntelliJ MCP server公開を提供し、[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)には実行構成・ファイル・編集等のtoolsがある。IDE内Agent、tool進捗やIDE操作そのものを独自能力とは呼ばない。本変更は既存能力との同等UXを目指す日本語表示と、Pluginのtab/token・queue・Request ID・復元保護への直接接続を担当する。[要検証] 最新競合の細かな折畳み/通知/経過時間の外観差と本候補の同等以上UXは実機未観測であり、公式資料だけで達成済みと判定しない。受入手順は[issue-98.json](../verification/changes/issue-98.json)、GUI/mainはpending。


## ACP接続（#147）

`AcpSession`をproject serviceがtab ID別に所有する。初回送信時だけ `agent acp` をroot作業ディレクトリで起動し、initialize → session/new → config確認 → promptと進む。既存認証を使い、自動loginはしない。次ターンは同じ接続/provider sessionを使用し、タブのcloseだけなら他接続を終了しない。root/executable変更・不確定切断後の同接続再送は拒否する。旧print履歴と保存XML/enumは変更しない。ACP session IDは旧履歴へ保存しない。

- `AcpJsonRpc`はbackground readerと上限付きwriter queueを分離。改行単位のUTF-8、JSON-RPC request/response/notification、opaque string/numeric ID、null/errorを処理する。1 frameは1 MiB、outbound待ち/inbound requestは各32、1 turnの更新・要求payloadは4 Mi文字、tool/観測childは各512件まで。超過・不正frame・EOFはpendingを解放して接続を終了する。stderrは読み捨て、prompt/key/raw errorを診断へ出さない。
- textは正当な重複を含むdeltaのままEDTへ渡し、message ID変更・thought/tool/requestを境に本文を分ける。readerで全文コピーを蓄積せず、printのdeduper/result補完を使わない。toolは改行を含むopaque IDでupsertし、省略fieldを保持、明示content/locationsを置換する。`completed`は報告状態であり、shell成功や復元可の証明ではない。
- permissionのoption ID/kindはserver提示値だけ使用。command/path/location/diff対象がない場合は許可を無効化する。Cursor質問はIDによる選択・複数選択・skip/cancel、Planは表示・accept/reject/cancelを扱う。返答直前にrun/Stop/closeを再確認し、一要求に一度だけ返信する。送信直前の取消変換を含む実際の返答を保持し、許可/拒否/取消等をカードに残す。未対応requestにはerror、notificationには返信しない。質問の自由文・Plan編集をwire契約として発明しない。
- Client fs read/writeとterminalはfalse、未実装elicitationは広告しない。これはAgent自身のファイル操作を禁止する宣言ではない。標準permission設定でも即時編集が起こり得る。ACP diffは閲覧のみで、正確な復元由来がない変更カードにRevertを提供しない。既存checkpointのroot/後続編集/Git制約は維持する。
- mode/modelはsessionのselect configOptions（確認済みID `mode` / `model`）を使い、set_config_option応答の全listを置換し、最終的な組み合わせを照合してからpromptを送る。初回modelはserver既定、以降は返された一覧のみ。printのmodel catalog/flagsをACPへ流用しない。permissionは標準、sandboxはCLI既定、worktreeはこのprojectのみを許可し、他の設定を無視せず送信前に説明する。

Stopはsession/cancelと未回答requestの取消を送る。**cancel送信・prompt応答だけで復元を解放しない**。応答完了、拒否、長さ/要求回数上限、Agent側取消はtyped終端値で区別し、日本語で表示する。準備予約をprompt終端まで保持し、実行中に観測した子processの終了も確認する。PIDと起動時刻を保持してreparenting/PID再利用を区別し、識別不能・応答なし・childが残る・切断・終端後のtool更新は不確定とする。不確定は対象processの回収を試み、同接続再送とproject寿命中の復元を拒否する。静止したidle接続のみなら復元できる。

観測は25 ms間隔の条件確認であり、短時間に生成・離脱した未観測子processまで保証するものではない。常駐childや任意のdetachを安全に許可したという契約はなく、該当用途は対象外。10秒の期限は「待てば安全」の判定ではなく、不確定へ移す期限。識別できないprocessを推測でkillしない。

検証は [Case JSON](../verification/changes/issue-147.json) のT01〜04/07/10/14/16。`AcpJsonRpcTest`、`AcpProtocolTest`、`AcpSessionTest`、`AgentRequestCardTest`と既存print/tab/restoreテストを実行する。fake serverはテスト専用Python標準ライブラリで、実Cursor・認証・networkを使わない。#146の実wire公開artifactと固定候補のGUIは未完了として区別する。外部providerの旧履歴load/title、高度config、Android/MCP公開は後続Issueの範囲を維持する。画像入力は[ACP画像添付](../development/image-attachment.md)の1枚/snapshot/予約/失敗保持を接続し、実GUI I1–I8は未完了。画像等の受信内容表示とは分ける。

## 制約を検証する入口

| 判断 | 実装 / 回帰確認 | 証明の限界 |
| --- | --- | --- |
| ACP接続実装とGUI受入 | [AcpSession](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt) / [AcpSessionTest](../../src/test/kotlin/com/cursoragent/acp/AcpSessionTest.kt) / [変更Case](../verification/changes/issue-147.json) / [QA #152](https://github.com/shinma06/cursor-in-android-studio/issues/152) | fake serverは合成契約。#146の公開承認待ちwireや固定build GUIを代替しない |
| 取消と物理終了・復元排他 | [AgentRun](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt) / [WorkspaceOperationGateTest](../../src/test/kotlin/com/cursoragent/service/WorkspaceOperationGateTest.kt) / AcpSessionTest | 観測child外の任意detachまでは保証しない。不確定なら復元拒否 |
| root/後続編集/未保存保護 | [RestoreTarget](../../src/main/kotlin/com/cursoragent/service/RestoreTarget.kt) / [FileRevertOperationTest](../../src/test/kotlin/com/cursoragent/service/FileRevertOperationTest.kt) / [RestorePolicyTest](../../src/test/kotlin/com/cursoragent/service/RestorePolicyTest.kt) | 来歴不明、ISOLATED、root外、after不一致、未保存内容は拒否。snapshotの未追跡本文制約は上記参照 |
| 旧履歴の互換 | [ChatHistoryState](../../src/main/kotlin/com/cursoragent/settings/ChatHistoryState.kt) / [PastChatsCoordinator](../../src/main/kotlin/com/cursoragent/ui/PastChatsCoordinator.kt) / [#44](https://github.com/shinma06/cursor-in-android-studio/issues/44) | metadata保持は本文永続化やACP provider resumeの保証ではない。旧metadataと新本文JSONの移行・破損・再開はConversationStoreTestで確認し、実IDE再起動は別QA |
| parser / 表示 | [print fixtureの出所](../../src/test/resources/stream-json-fixtures/README.md) / [AssistantChunkDeduperTest](../../src/test/kotlin/com/cursoragent/parser/AssistantChunkDeduperTest.kt) / [MarkdownRenderer](../../src/main/kotlin/com/cursoragent/ui/timeline/AssistantMessageBubble.kt) | completed採取とstarted推定、heuristicとACP deltaを区別。HTMLはescapeしraw実行しない |
| optional Terminal | [plugin.xml](../../src/main/resources/META-INF/plugin.xml) / [TerminalOutputReader](../../src/main/kotlin/com/cursoragent/ui/composer/mention/TerminalOutputReader.kt) | Terminalなし/無効時に全Pluginをロード不能にせず、Exception/LinkageErrorを扱う。API読取りはEDT |

実行コマンドは `python3 scripts/workflow/change_impact.py --run-tests`、Kotlin対象は `./gradlew test`。追加の横断対応表は#231の担当範囲であり、ここにCaseのpass状態を複製しない。

## ビルドと実行環境

[build.gradle.kts](../../build.gradle.kts)は `androidStudio("2026.1.1.8")` で最古の対応StableであるQuail 1初版を固定取得する。[CI](../../.github/workflows/ci.yml) と [branch ZIP](../../.github/workflows/branch-zip.yml) も新構成のsourceでは同じGradle経路を使う。branch ZIPのschedule/manualが旧構成sourceを扱う場合だけ、tracked gradle.propertiesの有効なplatformPath代入を検出して従来Quail 3 Patch 1の取得・local指定を維持する。Gradle実行・Java/Kotlin toolchain・bytecode targetは21。Gradle 9.7.1 / KGP 2.4.20を使用し、KGP公式の完全サポート上限9.7.0との差は実測結果と区別する。Kotlin language/apiは2.3、stdlibはIDEの2.3.20を使ってZIPへ同梱しない。

`verifyBuildSdk` は解決したproduct-infoのproductCode/full buildを `AI-261.23567.138.2611.15503007` と照合し、compile/resources/sandbox/ZIP生成前に不一致・確認不能を失敗にする。通常IDEや利用者共通の `platformPath` propertyは暗黙に使わない。local SDKが必要なときだけ両propertyを指定する（パスは各自の非公開設定に保持）。

```bash
./gradlew clean test buildPlugin -PuseLocalPlatform=true -PplatformPath="<Quail 1 SDKのルート（macOSはContents）>"
./gradlew buildPlugin -PpluginVersion=0.2.0-rc.1
python3 scripts/workflow/check_build_inputs.py --local-sdk "<Quail 1 SDK>" --wrong-sdk "<別版の有効なSDK>"
```

versionの既定は `gradle.properties` の `pluginVersion`。上の版は入力例であり正式版の決定ではない。正式候補はversionをcommit・develop統合してsourceを固定後に生成する。`-PpluginVersion` の明示入力でも内部plugin.xml、元ZIP名と内包identityのplugin.versionを揃える。source.commit/state、sdk.build、jvm.targetもZIP内へ記録し、ローカルパス・hostは含めない。正式RCの不変保存・公開は #391/#393、最終GUI受入は #392で追跡する。

標準 `buildPlugin` と配布物の識別は [ZIP配布](../development/plugin-zip-delivery.md) が正本。旧2.10.5の `androidStudio()` URL解決失敗は [固定版のCommands](https://github.com/shinma06/cursor-in-android-studio/blob/4d1514d8fa6c020d41ad9c0205b9ea24268bef57/CLAUDE.md#commands) に保全し、新版での結果と混同しない。対象IDE・配布元・checksumと候補選定は [Phase 1記録](../research/modernization-baseline-2026-09-21.md) を参照。

送信前の設定利用可否と実行境界のvalidation、同一snapshotの捕捉/受け渡しは [設定検証の境界](settings-boundary.md)を参照。設定/準備変更のwriterと独立reviewerが早期拒否位置と通信側防御を照合する。

print/ACPの本文・思考・tool・要求返答・終端・usageと、全文置換/メッセージ境界の分離理由は [UIイベント契約](event-contracts.md)を参照。イベント変更のwriterと独立reviewerがwire/表示/未知処理と実経路テストを照合する。

#44の会話/turn/message ID、保存型/責務・来歴、保持削除、元入力と注入context、保存状態は[会話保存契約](conversation-persistence.md)を参照。過去のprovider sessionと本文閲覧・Revertを分ける。

## ACP非テキスト内容（#296）

画像・音声・リソースの有限情報表示、更新/上限/保存互換と未実装previewの境界は[ACP内容の有限表示](acp-content.md)を参照。
