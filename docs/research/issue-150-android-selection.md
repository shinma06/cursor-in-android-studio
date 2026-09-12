# #150 Android選択対象の統合: 資料・固定SDKの事前照合

2026-09-12 / 調査base `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。
対象Issue: [#150](https://github.com/shinma06/cursor-in-android-studio/issues/150)、親 [#141](https://github.com/shinma06/cursor-in-android-studio/issues/141)。

**固定SDKに必要な読取APIは存在する。ただし直接APIの優位性、最強MCP構成の実動、最初の採用機能は未判定。**
本変更の完了範囲は資料・descriptor・class署名の照合、compile-only確認、および[実比較手順](issue-150-comparison.md)の作成。
実IDE操作、install/restart、認証変更、MCP公開、ADB起動は行っていない。製品source・共有architectureは変更しない。
#150全体の受入は残るため、この文書PRだけでIssueをcloseせず、採用実装Issueも作成しない。

## 1. 証拠の強さと固定対象

「資料契約」は公式文書の説明、「SDK確認」は下記artifactのdescriptor/公開class署名とコンパイル、
「実測」は実IDE・実Agentでの対象一致を指す。本調査で実測に達した項目はない。
Javaの`public`、クラスの存在、注釈の不在は、第三者plugin向けの安定API保証ではない。

| 対象 | 読み取った値 | この確認の限界 |
|---|---|---|
| Android Studio | `AI-261.26222.65.2614.16204760`、data directory名 `AndroidStudio2026.1.4`、macOS aarch64、minimum Java 21 | installed artifact。起動中IDE/buildの同一性は未確認 |
| Android plugin | `org.jetbrains.android`、version `261.26222.65.2614.16204760`、since/until `261.26222.65` | root plugin descriptor。enabled/loadedは未確認 |
| AI Assistant | `com.intellij.ml.llm`、version `261.26222.129`、since `261.26222.58`、until `261.26222.*` | 宣言範囲は対象buildに合う。Android StudioでのACP動作保証ではない |
| Cursor CLI | `2026.09.10-fd3934a` | 直前の#118 probeで確認した版。#150実比較前に再固定が必要 |
| Android SDK | installed platform `android-37.0`、build-tools `36.0.0`、platform-tools `37.0.1` | directory/source.propertiesのみ。AGP互換、device API/接続状態は未確認 |
| 実比較用project/model | 未固定 | 現repositoryはIDE plugin。Android fixture、AGP/Gradle、明示model IDは比較開始条件 |

SHA-256（パスはartifact内の相対名。ローカル利用者名・配置パスを公開しない）:

| Artifact | SHA-256 |
|---|---|
| IDE `Resources/product-info.json` | `6bff65ee73fa02f2502cf2a0cfeda21eddf9315580bba331e1f50e2efd8ec336` |
| IDE `plugins/android/lib/android.jar` | `d90db820776a070f3e31f59cd543af6051923a5e9b77522766341353e5190209` |
| IDE `plugins/android/lib/android-common.jar` | `e2d190adc8d0dae0b6fe40dbaf4c77ae10e899e58f1a9f80641a7cc6cd498ad4` |
| IDE `plugins/android/lib/sdk-tools.jar` | `287a95fa96adf88bcdec3f6d79e235691404f0e64eff8fecf43664c87925a7bd` |
| AI Assistant `lib/ml-llm.jar` | `47e714db4a23edbf0107d922a0f9053fbc47347dbe7c4f05b5367ae5e76ddabf` |

IDEのbundled pluginと上記data directoryに対応する通常のuser plugin directory内のJARをread-only走査し、
69個のroot `META-INF/plugin.xml`を取得した。この範囲に`com.intellij.mcpServer`は見つからなかった。
別config/plugin配置やruntime catalog全体の不存在を示すものではない。
本Research taskの利用可能tool metadataにもIntelliJ/Android/ADB/Logcat用connectorはなかった。
IDEの接続情報・認証・ユーザーMCP設定は読まず、接続も試していない。

## 2. API境界: まず既存機能を使う

IntelliJ Platform標準のDocument/PSI、Execution、DebuggerとAndroid plugin実装を分ける。
Android連携は対象buildに合うSDKと`org.jetbrains.android`依存が必要であり、任意のJARを同梱して解決しない。
同期要求には公式FAQが`GradleSyncInvoker.requestProjectSync`を案内している。[Android Studio plugin development](https://plugins.jetbrains.com/docs/intellij/android-studio.html)

| 領域 | 固定SDKで確認した読取経路・配置 | 公開/内部境界、fallbackと比較点 |
|---|---|---|
| Document / PSI | Platform `FileDocumentManager.getCachedDocument(VirtualFile)`、`PsiDocumentManager.getCachedPsiFile(Document)` | 標準API。null、未commit PSI、index作業中を扱う。未保存Documentとdiskを区別。既存MCPのfile/symbol/inspection取得を先に比較し、差がなければ再実装しない |
| Android module / model | `android-common.jar`: `AndroidFacet.getInstance(Module)`。`android.jar`: `GradleAndroidModel.getSelectedVariantName()`、`getAndroidProject()` | Android固有のpublic署名。model取得・寿命・sync境界はruntime未検証。旧名`AndroidModuleModel`を前提にしない。GradleテキストからIDE選択値を推定しない |
| Variant / sync | `GradleSyncState.getInstance(Project)`、`isSyncInProgress()`、`lastSyncFailed()`、`isSyncNeeded()`。`BuildVariantUpdater.updateSelectedBuildVariant(Module,String)` | 前者は状態読取。後者は`gradle.variant.view`実装領域の変更操作で、安定extension保証は未確認。sync/Variant変更は読取権限に混ぜない。既存MCP/IDE操作で済む範囲を比較し、不足時のみ固定版adapterを検討 |
| Run / execution | Platform `RunManager.getSelectedConfiguration()`、`ExecutionTargetManager.getActiveTarget(Project)`、`ExecutionManager.getRunningProcesses()`。`ExecutionEnvironment`のtarget/execution ID | 選択configuration・実行target・既に動くprocessは別。Android側`AndroidRunConfiguration`/`AndroidExecutionTarget.getRunningDevices()`も照合。既存run configuration/MCPを優先し、新規runnerやshell組立てを作らない |
| Device / ADB | `DeviceProvisionerService.getDeviceProvisioner()`、`DeviceFutures.getIfReady()`、`AndroidExecutionTarget.getRunningDevices()`。`sdk-tools.jar`: `AndroidDebugBridge.getDevices()` | provisioned/connected/selected/実行済deviceは同義ではない。public署名だけでは選択toolbar追従を証明できない。IDEの既存bridgeを再利用する候補とし、取得のために新bridgeやserver再起動をしない。CLI fallbackはserialを明示し、offline/unauthorizedを判定 |
| Logcat / process | `LogcatService.readLogcat(serial, AndroidApiLevel, Duration, maxHistoryEntries)`、`LogcatHeader`のapplication ID/PID/timestamp。`ProcessNameMonitor.getProcessNames(serial,pid)` | Android実装API。読取引数だけではpackage/PID隔離を保証しない。収集範囲と返却範囲を限定し、device+package+PID+起動区間で再検証。`clearLogcat`は変更操作。CLIは既存MCP/IDE経路が不足する場合の限定fallback |
| Debugger | Platform `XDebuggerManager.getCurrentSession()`/`getDebugSessions()`、`XDebugSession.isSuspended()`/`getCurrentStackFrame()` | suspended session/実行ID/frame寿命を確認して限定読取。evaluateはmethod実行等を伴い得るので読取扱いしない。既存Debugger MCPのstatus/stack/valuesを比較対象に含める |

Documentは必要時に取り直し、長期保持でIDEの回収を妨げない。PSI/modelは適切なread action、UI更新はEDT、
待機・接続・ビルドはUI thread外で行う設計候補とする。2026.1ではUI入力handlerの暗黙lockを前提にしない。
本調査は実装していないため、Lifecycle/dispose・競合・漏洩防止のruntime確認は後続Caseに残す。
[Documents](https://plugins.jetbrains.com/docs/intellij/documents.html)、[PSI](https://plugins.jetbrains.com/docs/intellij/psi.html)、[Threading model](https://plugins.jetbrains.com/docs/intellij/threading-model.html)、[2026 API changes](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html)

run configurationと実行processを分け、標準Execution機構を使う。[Execution](https://plugins.jetbrains.com/docs/intellij/execution.html)
ADB fallbackでは明示serial、Logcatでは時刻・filter・bufferの意味を固定し、全device/全履歴をAgentへ渡さない。
[ADB](https://developer.android.com/tools/adb)、[Logcat](https://developer.android.com/tools/logcat)

`GradleAndroidModel`/`GradleSyncState`/`BuildVariantUpdater`/`DeviceProvisionerService`/`LogcatService`の
class bytecodeでは`ApiStatus`注釈を検出しなかったが、安定性の根拠にはしない。後続実装を採用する場合は、
対象SDK compile、Plugin Verifier、descriptor/classloader、および固定IDEでの実動を別々に確認する。
Platformのみで成り立つ機能はAndroid型を参照しない場所へ置き、Android依存をoptionalにする場合は
`optional="true"`と専用`config-file`でクラスのロード境界も分離する。対象をAndroid Studio専用にするかは既存product方針に従い、今回依存を変更しない。
[Plugin dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)、[Verifier failure levels](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-types.html)

## 3. 比較相手はAI Assistant + Cursor ACP + IDE + MCP全体

公式Cursor経路はAI Assistantを使うJetBrains統合とACP。対応の一般説明を、この固定Android Studioと
pluginの組合せの成功証拠へ読み替えない。[Cursor JetBrains](https://cursor.com/docs/integrations/jetbrains)、[Cursor ACP](https://cursor.com/docs/cli/acp)
AI AssistantはACP agentへのcustom MCPおよびIntelliJ MCPの引渡しを説明している。
[AI Assistant ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)

| 構成 | 資料/installed artifactで確認できたこと | 実比較前に必要な確認 |
|---|---|---|
| AI Assistant ACP | `intellij.ml.llm.agents.acp.xml`が存在。ACP有効registry keyの既定値はtrue | enabled状態、Cursorログイン済既存session、明示model、同一CLI/fixtureでのACP会話 |
| IDE MCP橋渡し | `intellij.ml.llm.mcp.embedded.xml`と`intellij.ml.llm.agents.acp.embeddedMcp.xml`が存在し`com.intellij.mcpServer`へ依存。前者server keyの既定値はfalse | 依存plugin/moduleが実際に解決するか、server設定・公開範囲・ACP受渡し・tool実動。既定値は現在の設定値ではない |
| IntelliJ MCP Server | 公式IDEA資料にmodule/dependency、inspection/symbol、build/run/terminal toolsがある | このAndroid Studioに対応するplugin版と有効化可否。`tools/list`だけでなくExposed Tools/router-only経路まで照合 |
| Debugger MCP toolset | 公式IDEA資料にstatus/stack/frame values/control/evaluateがある | 対象IDEへのtoolset導入・ロード可否とAndroid Kotlin/JVM debuggerでの実動。IDEAでのbundled記述をAndroid Studioへ一般化しない |
| その他の利用可能MCP | 実際のcatalog未取得 | Android/device/log経路を含む既存toolを列挙し、権限と実動を確認して同じ比較側へ加える |

上表のAI Assistant埋込descriptorは`visibility="internal"`であり、競合の構成要素を示す。
本pluginがそれら実装へ直接依存してよいという意味ではない。
Gemini内のMCP client/libraryの存在も、CursorへIDE操作を公開するserverの証拠ではない。
[Gemini MCP client](https://developer.android.com/studio/gemini/add-mcp-server)

IDEAの公開tool説明は参考catalogであり、このIDEでの能力表ではない。Router-onlyは直接catalogに現れないため、
不在判定はrouterを含めて行う。Debuggerは既存機能として扱う。
[IntelliJ MCP Server / Exposed Tools / Debugger tools](https://www.jetbrains.com/help/idea/mcp-server.html)

現状のblocked条件は「固定Android Studio + AI Assistant `261.26222.129` + 解決済MCP Server/toolset +
Cursor ACP + 同一model/project/device」の実動・catalogが未確認であること。
install、GUI lease、認証/公開設定が必要なら指定operatorへ引き継ぎ、Research判断で変更しない。
#152のGUI待ちは本資料作成を止めないが、比較の成功として代用もしない。

## 4. 直接読取とAgentに渡すtoolを分ける

直接読取はIDE内で現在の対象を検証・表示する候補。Agent向けtoolは外へ返す入力・権限・出力契約が追加で必要になる。
既存MCPが正しく返せる場合はそれを使い、未達を同条件で再現した範囲だけ新toolを検討する。
以下は**比較で検査する契約案**であり、実装済み機能でも新APIの採用決定でもない。

| 項目 | 比較時の判定基準 |
|---|---|
| 対象 | project/module/Variant、sync世代、run configuration/execution ID、device、package/PID/起動区間を必要な操作ごとに固定。開始後に選択が変わったら再検証し、古い結果を新しい対象の結果として表示しない |
| 権限 | snapshot読取とsync/Variant変更/build/run/stop/device操作/evaluateを分離。許可範囲を超える操作は提案まで。shellやevaluateを無害な読取と分類しない |
| 入力 | project内の対象と解決済module/Variant/deviceに限定。対象未選択・複数・消失は日本語で理由を返し、自動で別対象へfallbackしない |
| 待機/取消 | 比較fixtureでは読取5秒、build/run/syncは120秒を上限案として固定しtimeoutを記録。取消・project close・device切断後は結果を採用せず、所有する待機/購読だけを解放。IDE全体や他runを停止しない |
| 量/鮮度 | Logcatは直近5秒・最大100件・UTF-8 32 KiB、超過は明示。snapshotは取得時刻/世代、実行結果はexecution IDを保持。API自体が保証しないfilterは返却前に検査 |
| privacy | fixtureの許可したmarker・相対path・device aliasだけ公開。実serial、user path、認証、全環境変数、任意の変数値・ログを公開しない。情報欠落を空成功にしない |

上限値は製品推奨値ではなく比較を再現可能にする試験条件。先に既存toolで設定可否・実際の打切りと取消を測る。

## 5. compile-onlyの再現範囲

対象IDE付属JBRの`javac -proc:none`で、下記ソースをIDE `lib`、Android/Java/Kotlin plugin `lib`配下の
JARをclasspathにしてコンパイルした。**exit 0、診断出力なし**。生成classは実行もIDEへのロードもしていない。
この広いclasspathは署名の存在確認専用であり、製品の依存設定・クラスローダ・null安全性・選択正確性の試験ではない。
privateなprobe出力を公開せず、再現用入力をここに載せる。Reviewerによるraw調査と著者の実行記録を混同しない。

```java
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerManager;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService;
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.logcat.service.LogcatService;
import com.android.tools.idea.logcat.message.LogcatHeader;
import com.android.sdklib.AndroidApiLevel;
import com.android.ddmlib.AndroidDebugBridge;
import java.time.Duration;

// Compile-only signatures: never loaded or invoked in an IDE.
class SdkSurfaceProbe {
    static void surface(Project project, VirtualFile file, GradleAndroidModel model,
                        DeviceProvisionerService devices, AndroidExecutionTarget target,
                        LogcatService logcat, LogcatHeader header, AndroidApiLevel api,
                        AndroidDebugBridge bridge) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
        model.getSelectedVariantName();
        GradleSyncState.getInstance(project).isSyncInProgress();
        GradleSyncState.getInstance(project).lastSyncFailed();
        RunManager.getInstance(project).getSelectedConfiguration();
        ExecutionManager.getInstance(project).getRunningProcesses();
        ExecutionTargetManager.getActiveTarget(project);
        devices.getDeviceProvisioner();
        target.getRunningDevices();
        bridge.getDevices();
        logcat.readLogcat("fixture-device", api, Duration.ofSeconds(5), 100);
        header.getPid();
        header.getTimestamp();
        header.getApplicationId();
        XDebuggerManager.getInstance(project).getCurrentSession();
    }
}
```

## 6. #150受入の現在地

| Issue受入 | 本PRで用意したもの | 残作業・判定 |
|---|---|---|
| 固定SDKのAPI/public/internal/optional/fallback | §1–2、compile-only | module取得・classloader・各状態の実動は未確認 |
| 最強IDE/MCP構成の同条件比較 | §3、比較手順の環境lock | **blocked**: compatible server/toolset・catalog・実会話未確認 |
| 2module/2Variant/2device、切替・sync・失敗・PID時刻 | 具体fixture仕様と未実施Case | **pending**: fixture build、2device確保、GUI lease、全Case実行 |
| 直接読取とAgent toolの分離 | §4の試験契約 | 既存toolの充足/不足を実測し、必要時のみ新toolへ絞る |
| 最初の1機能選定 | T17/T18に対する比較・採否基準 | **pending**: 効果差未測定。採用実装Issueなし |
| XML View/ViewBinding、GUI未達の明記 | fixtureの指定、全Case未実施 | Compose/DOMで代用しない。#150をcloseしない |

次の担当は指定GUI operatorが比較手順の環境lockを埋め、固定候補・Caseを検証台帳へ登録して実行する。
Researchはその公開証拠を比較し、差がなければ追加実装なし、差があれば最小の1機能だけ選定する。
