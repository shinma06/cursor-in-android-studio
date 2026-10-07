# 限定候補の現行実装と境界

この文書は[#562](https://github.com/shinma06/cursor-in-android-studio/issues/562)のmain限定候補を説明する。実装の基準は元C `ebac56fe441c08a6c869b8be3b1aa906a887c14e` と、#552/553の送信準備時の局所PCE取消修正。元Cの[固定実装記録](https://github.com/shinma06/cursor-in-android-studio/blob/ebac56fe441c08a6c869b8be3b1aa906a887c14e/docs/architecture/current-implementation.md)を根拠とし、最新developの追加機能へ読み替えない。

運用規約は現main `66d9735bee4d23fcec2fcb096af1f329ae514cf4` の[CLAUDE.md](../../CLAUDE.md)と[開発手順](../development/github-workflow.md)を保持する。[trusted scope](../verification/scopes/issue-562.md)の234path・通常ファイル削除7件・175 Case・395原本出現・環境binding10件が対象。ソース・JVM/CLI成功は実GUI受入ではなく、175件は新固定buildで未受入。既存正式版0.1.0の配布物・過去観察も別の証拠として保持する。

## 所有と実行経路

| 所有者 | 責務と寿命 |
| --- | --- |
| [AgentToolWindowRootPanel](../../src/main/kotlin/com/cursoragent/ui/AgentToolWindowRootPanel.kt) | SessionTabsとtab別view/controllerを所有。CardLayoutで切り替え、入力・caret・scrollを保持。closeで対象controllerを破棄 |
| [SessionTabs](../../src/main/kotlin/com/cursoragent/session/SessionTabs.kt) | 同期化されたメモリ内のtab UUID、chat ID、draft/mode/model/title、run token。processやディスク保存は持たない |
| [AgentUiController](../../src/main/kotlin/com/cursoragent/ui/AgentUiController.kt) | tab別の送信準備、context/checkpoint、Stop、復元、本文保存。遅いcallbackを選択中の別tabへ転送しない |
| [AgentProcessService](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt) | project別の複数run、準備・復元排他、print process、tab別ACP接続。別tabの送信で既存runをkillしない |
| [AgentRun](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt) | 準備を含む1要求の取消、listener、遅いprocess attachと終了の一度だけの配送 |
| [AgentTurnListenerFactory](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt) | print/typed ACP eventをEDTへ渡し、token/generation/dispose/停止を再確認。会話の表示・保存・終端を調整 |

既定printは`-p --output-format stream-json --stream-partial-output --trust`。要求ごとにworkspace/settings/promptを固定し、editor/VFS/任意TerminalをEDTで読み、Git・checkpoint・process起動をbackgroundで行う。`Result`はusage/session/errorの入力であり、OS終了の代替ではない。printの版別delta/flushと旧・未知版のheuristic fallbackをACPへ流用しない。

旧独自header/設定popupは[ToolWindowChatActions](../../src/main/kotlin/com/cursoragent/ui/header/ToolWindowChatActions.kt)と[ToolWindowFactory](../../src/main/kotlin/com/cursoragent/toolwindow/CursorAgentToolWindowFactory.kt)の標準アクションへ移る。操作の確認・実行範囲・作業場所、要約、MCP、設定への導線を保持し、updateと実行時に選択tab・実行中・disposeを再確認する。旧popup固有の位置計算は廃止し、IDE標準menuとselectorの既存幾何回帰を用いる。

## 準備取消・停止・復元

[局所PCE修正](https://github.com/shinma06/cursor-in-android-studio/pull/553)は元Cのcontrollerへ必要な準備境界とhelperだけを適用する。`ProcessCanceledException`を通常エラーへ変換せず再throwし、準備済みreservationがある場合はそのreservationを解放してから再throwする。通常の準備例外は従来の失敗表示を維持する。[PromptPreparationTest](../../src/test/kotlin/com/cursoragent/ui/PromptPreparationTest.kt)と[AgentSettingsBoundaryTest](../../src/test/kotlin/com/cursoragent/service/AgentSettingsBoundaryTest.kt)が取消伝播・同期listener取消・通常失敗の回帰を持つ。

Rabbitの非同期VFS保存は、外部I/Oを行う送信準備とcheckpoint復元の前にbackground/write action外で`ManagingFS.flushPendingUpdates()`を待つ。待機失敗と待機中のStopを確認し、未保存Documentの自動保存や後続編集の固定を保証しない。VFS内だけのfile Revertには追加待機を入れない。

Stopは対象runだけを停止し、停止通知後に完了する。tab closeはtoken/listenerを無効化して対象runを停止、content/project終了は各所有runを停止する。停止表示と物理終了は別であり、準備・processが書き込み得る間はproject共通の[WorkspaceOperationGate](../../src/main/kotlin/com/cursoragent/service/WorkspaceOperationGate.kt)が復元を拒否する。停止中の旧processは復元を妨げるが、他tabの準備を一律拒否しない。

[RestorePolicy](../../src/main/kotlin/com/cursoragent/service/RestoreTarget.kt)と[FileRevertOperation](../../src/main/kotlin/com/cursoragent/service/FileRevertOperation.kt)は、実root/来歴、symlink/traversal、後続編集、未保存editorを照合する。ISOLATED・来歴不明・root不一致は拒否する。checkpointは元から未追跡の本文や、作成後stageされた新規fileの完全復元を提供しない。標準printの即時編集は防げず、Diff/Revertは適用後の操作。

## ACP・入力・表示

[AcpSession](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt)はproject serviceがtab別に所有する。新しい会話の初回送信前に明示選択し、既存認証で`agent acp`、initialize → session/new → config確認 → promptへ進む。自動loginはしない。mode/modelはserverのselect configOptionsを使い、printのcatalog/flagsを流用しない。未対応の設定を黙って無視せず送信前に拒否する。

JSON-RPCは上限付きframe/queue、typed tool/text/requestを扱う。permissionはserver提示optionと検証したcommand/path/diffまたは有限MCP対象に結び付け、未知・不正・追加引数を許可しない。質問とPlanは既存の有限返答を使用し、一要求への重複返答とStop/close後の返答を防ぐ。client fs/terminalと未実装elicitationは広告しない。Agent自身の即時編集やACP diff閲覧と、安全なRevertは別の能力。

ACP cancel送信/prompt応答だけで復元を解放しない。観測childのPID/開始時刻と終端を照合し、切断・識別不能・残存child等は不確定としてprojectの新規準備/復元を拒否する。観測外の任意detachは保証しない。通常診断へprovider本文・生error・pathを出さず、既存の明示opt-in私的診断の境界を保つ。詳細は元Cの[固定ACP実装説明](https://github.com/shinma06/cursor-in-android-studio/blob/ebac56fe441c08a6c869b8be3b1aa906a887c14e/docs/architecture/current-implementation.md#acp接続147)を参照する。

ComposerはDocumentを先行生成してlistenerを実Documentへ登録し、`@`/`/`選択のtrigger消費をIDE command内で行う。非Undo境界と選択後のUndo/Redoを区別する。context/画像/予約入力は要求ごとのsnapshotを保持し、次draftへ混入させない。画像は元Cの1枚添付経路、`@Docs`/`@Web`はhint、手動browserはAgent操作/会話共有へ未接続。後続#211/#212の追加機能は対象外。

進捗・経過時間・tool開閉・背景通知は会話とturnへ帰属させ、EDTでstale callbackを拒否する。未知toolは有限なliteral表示とし、実行開始を推測しない。通知へcommand/raw errorを載せず、Markdownのraw HTMLを実行しない。

## 登録・設定・保存互換

[plugin.xml](../../src/main/resources/META-INF/plugin.xml)の`com.cursoragent.plugin`、`Cursor Agent` tool-window/notification ID、application/project serviceを保持する。削除する未使用`CursorAgentPlugin`は登録entryではない。TerminalとJCEFは別descriptorの任意依存で、不在時の`LinkageError`を境界で扱い、チャット登録とbrowser回復画面を保つ。

[AgentSettingsState](../../src/main/kotlin/com/cursoragent/settings/AgentSettingsState.kt)の`CursorAgentSettings`/`cursor-agent-settings.xml`、permission/sandbox/worktree enumとCLI mappingを維持する。ToolWindow menuの共有設定は選択時に保存し、次回準備に適用する。Settings画面の未適用編集はcancelで破棄し、明示モデル取得以外のreset/診断操作でCLIを起動しない。

[ChatHistoryState](../../src/main/kotlin/com/cursoragent/settings/ChatHistoryState.kt)の旧XMLはmetadataのまま保持する。新しい[Conversation](../../src/main/kotlin/com/cursoragent/history/Conversation.kt)/[ConversationStore](../../src/main/kotlin/com/cursoragent/history/ConversationStore.kt)は表示本文を別JSONへatomic保存し、破損/future versionを上書きしない。入力context・wire・認証・実行可能な操作は本文保存へ混入させない。本文閲覧、print provider resume、Revertは別の能力で、print resumeは既知の一致する実root、ACP provider resumeは未提供。実IDE再起動・ロード互換は未受入。

## ビルド・検証・運用

[build.gradle.kts](../../build.gradle.kts)はRabbit 1 `androidStudio("2026.2.1.8")` / `AI-262.9437.185.2621.16467767`を標準取得する。Gradle/toolchain/JVM targetは25、同梱stdlibを使用し製品ZIPへ重複同梱しない。local SDKは`-PuseLocalPlatform=true -PplatformPath=...`の両指定時だけ使用し、`verifyBuildSdk`がfull build不一致・確認不能を拒否する。

互換性はtrusted planに固定した[既存policy](../../scripts/workflow/plugin_compatibility.json)と全class/依存/report hashで確認する。providerの固定checksum・SDK/JBR・OS/CPUを既存wrapperで照合し、未知API/reportへ無条件例外を追加しない。局所PCEによるflushの生成callsite変更は計画の固定policyで扱い、APIの追加や後続developのpolicyを持ち込まない。[標準ZIP配布](../development/plugin-zip-delivery.md)と[限定main受入](../development/main-scoped-release.md)を使用する。

`python3 scripts/workflow/change_impact.py --run-tests`、`./gradlew test buildPlugin verifyPluginStructure`、同一ZIPのseal/identity/VerifierをCLI入口とする。独立Swing probeは[既存tool](../../scripts/gui-fixture/README.md)で別artifactとして固定し、Plugin ZIPと区別する。JVMテスト・fake ACP server・probe生成は実IDE/実provider成功を代替しない。

Issue/claim/専用branchと通常hook、writer停止/clean、別session固定レビュー、4必須checks、PM承認済み内容の継続Git公開許可は現main規約を維持する。許可の詳細は[共有正本](../development/github-workflow.md#git-publication-permission)に従う。GUI/install/restart/runIdeはhost leaseと指定担当の権限を要し、正式version/tag/Release・provider/auth・保護証拠の公開は別の境界。#146の非公開証拠・旧#485資源・他owner/registry/PAUSEDを変更しない。

175 Caseの観察状態は[Verification JSON](../verification/README.md)と#562の固定promotionが正本で、この文書へpassを複製しない。GUI受入とmain統合は未完了。
