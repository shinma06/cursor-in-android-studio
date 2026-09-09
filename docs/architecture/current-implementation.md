# 現行実装の責務と境界

2026-09-09 / #142。ソース基準は develop `71c316c9eba125f505759fb093edf445168bee48`、方針・運用文書は main `2c47f6249299852ddce3d9542ebbc2dc52fb5b92` を通常mergeして同期した。

[Project Mission](../project-mission.md) / [ACP First](cursor-integration.md) が設計方針。本書は現在動くprint経路の説明であり、ACP client実装やGUI合格の宣言ではない。機能別のACP契約・移行設計は [#115](https://github.com/shinma06/cursor-in-android-studio/issues/115)、全体順序は [#141](https://github.com/shinma06/cursor-in-android-studio/issues/141)で扱う。

## 所有関係

| 所有者 | 現在の責務・寿命 |
|---|---|
| `AgentToolWindowRootPanel` | `SessionTabs` とtab ID別のview/controllerを所有。CardLayoutで切替、同じviewの本文・入力・caret・scrollを保持。閉じると該当controllerをdispose |
| `SessionTabs` | 同期化されたメモリ内状態。plugin tab UUID、nullable chat ID、draft/mode/model/title、run tokenを保持。プロセスやSwing、ディスク保存を持たない |
| `AgentUiController` | **タブごと**のactive run/token/generation。送信準備、context/checkpoint、listenerとUI、停止・復元を調整。選択中タブへイベントを転送しない |
| `AgentProcessService` | **projectごと**の複数`AgentRun`集合、sessionの復元元記録、復元排他。printプロセス構築とstream解析・配送。新しい別タブ送信で既存runをkillしない |
| `AgentRun` | 準備を含む**1回の要求**。停止要求・listener・終了の一度だけの配送と遅いprocess attachを管理。会話全体でもOSプロセスそのものでもない |
| `AgentTurnListenerFactory` | 現行printイベントからタブUIへのbridge。EDT上でtoken/generation/dispose/停止を再照合。tool card、session metadata、usage、終端表示を調整 |

対応ソース: [root](../../src/main/kotlin/com/cursoragent/ui/AgentToolWindowRootPanel.kt)、[状態](../../src/main/kotlin/com/cursoragent/session/SessionTabs.kt)、[controller](../../src/main/kotlin/com/cursoragent/ui/AgentUiController.kt)、[service](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt)、[run](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt)、[listener](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt)。

## 送信から終了まで

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

- `StreamEvent` / `ToolCallPayloadParser` は現行printの構造化JSON用。未知/不正入力は防御的に扱う。completed fixtureとstarted payloadの推定を分ける。`AssistantChunkDeduper` は増分/累積混在へのheuristicで、常に全文を返して置換する契約。全出力への正しさやACP chunk処理への流用は未検証。
- mode/modelとモデルオプションは実CLI IDへ対応。`ModelListParser` / `McpListParser` は補助CLIの表示文字列解析。MCP dialogは既知の`id: status`を整形し、解析不能ならraw表示する。これらはACPモデル設定やMCP tool公開の実装ではない。
- usageはprintの入力値を `TokenUsage` / context usage状態へ反映する現行表示。ACPのcontext/usage契約、Plan/Todo/質問/permissionの往復UIは実装済みにしない。
- `cursor-agent-chat-history.xml` はproject単位のchat ID/preview/更新時刻のみ。開いたタブの本文はメモリ内、再起動後の本文保存は未実装（#44）。`cursor-agent-checkpoints.xml` はsnapshot metadata、`cursor-agent-settings.xml` はアプリ設定。保存ID/enum、`com.cursoragent.plugin`、内部tool-window/notification IDは変更しない。

型・テスト名の維持理由と再評価箇所は [監査記録](../development/project-context-audit.md)。テスト成功は現行契約の回帰確認であり、ACP対応や新しいbuildのGUI合格には数えない。
