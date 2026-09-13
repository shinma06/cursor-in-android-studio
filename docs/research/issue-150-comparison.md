# #150 Android選択対象: 制御fixtureと実比較手順

2026-09-12。関連: [SDK/能力境界と未達一覧](issue-150-android-selection.md)、
[T17/T18](acp-feature-migration-2026-09-09.md)、[GUI coordination](../development/gui-coordination.md)、
[検証台帳](../verification/README.md)。

**以下は未実施のfixture仕様・比較Case。アプリ、device、GUI、MCPの動作passを示さない。**
現PRは資料のみで`gui_required=false`。#150全体に必要な下記GUI Caseを免除する意味ではない。
実行前に指定operatorが固定候補/Case IDをJSON台帳へ登録し、生成物を二重編集しない。

## 1. 開始条件と比較する二つの経路

共通条件を固定し、AはAI Assistant + Cursor ACP + IDE integration + IntelliJ MCP + 利用可能な関連MCPの最強構成、
BはCursor in Android Studioの固定build + 同じCursor ACP/同じ関連MCPとする。
BのAndroid直接読取が未実装なら「未実装」と記録する。API表だけでBのpassを作らない。
必要な計測probeを追加する場合は別途scopeと副作用をレビューし、製品実装の先行採用にしない。

指定GUI operatorが次のlockを埋める。未取得は`unknown`、構成不能は組合せ・理由・次の担当を記録して`blocked`にする。

| Lock | 記録する値/成立条件 |
|---|---|
| IDE/plugin | 実際に起動するIDE full build、Android plugin/AI Assistant/MCP Server/Debugger MCP toolset版、enabled/load状態。基準候補はSDK調査の`AI-261.26222.65.2614.16204760`。変更時はAPI照合をやり直す |
| Agent | CLI full version、ACP protocol、明示model ID、同じ権限・MCP公開範囲。Autoでmodelを混在させない。既存の認証を使い公開記録にはplan種別のみ |
| MCP | endpointはprivateに保持。公開記録はserver/toolset名・版、direct catalogとrouter-only catalog、input schema/権限・実動結果。片側だけ無効化して優位性を作らない |
| Fixture/build | project commit、AGP/Gradle/Kotlin/JDK/compileSDK/build-tools、2 APK系列のhash。現時点で組合せ未固定。GUI operatorが対象IDE対応のEmpty Views Activityを基に作り、両経路で同じcommitを使う |
| Device | D1/D2の2台を同時に認識。実serial→alias対応はprivate。公開値はalias/API/ABI/emulatorまたは物理/device状態。両方で選んだminSdk以上、APK実行可、ADB認証済み。今は2台存在未確認 |
| GUI/evidence | host-wide lease、operator、固定plugin ZIP/hash、開始時刻、Case ID、証拠保存先/公開投影方針を記録。通常の利用projectは使わない |

compatible MCP/toolsetが対象Android Studioで使えない場合は、その正確な版の組合せをblockedとして残す。
別IDE/buildが必要なら別比較行を追加し、同条件比較と混ぜない。現行資料はIDEAのtool存在を示すだけでAndroid Studio互換を保証しない。

## 2. fixtureの具体仕様

使い捨てproject名は`issue150-selection-fixture`。2つのAndroid application moduleを持ち、Composeを使わず
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
fixture作成後にsyncと両module/両Variantのbuildが成功する基準commitを保存し、4 APKのhashを記録する。
AGP等のversion lockと実際の生成コードをまだ作成していないため、ここまでを「fixture実装済み」としない。

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

各CaseはA/Bで同じfixture状態から開始する。主にT17がeditor/selection/model、T18がexecution/device/log/debugger。
「既存toolなし」はdirect/router双方と権限を確認した場合のみ記録する。未実装、環境blocked、timeout、製品failを分ける。

| Case / 対応 | 操作 | 期待する判定 | 現在 |
|---|---|---|---|
| C01 / T17 | RedとBlueの同名XML/Kotlinを切替。Redのlabelだけ未保存marker `I150_UNSAVED_RED`へ変更し選択範囲を渡す | module/相対file/範囲/未保存Documentを正確に識別。diskの旧値やBlueと混同しない。保存せず復元し後続を汚さない | pending |
| C02 / T17+T18 | 上記8組を選択、同期安定後に対象snapshotを取得してrun、label/logを照合 | IDE選択module/Variant/device、実application ID、実行device、execution IDの対応一致。候補一覧と選択値を混同しない | pending |
| C03 / T17 | Red/debug/D1の読取開始直後にBlue/release/D2へ切替。Variant更新/sync中と完了後に再読取 | 古い結果を新対象へ貼らない。sync中はstale/更新中を明示し、完了後に新しい世代へ一致 | pending |
| C04 / T17 | fixtureのGradle設定末尾に一時的な構文errorを追加しsync。失敗後に復元し再sync | sync失敗を空module/成功扱いにしない。旧modelなら古い旨を表示。復旧を認識。他project/ユーザー設定は編集しない | pending |
| C05 / T18 | BlueのActivityへ一時的なコンパイルerrorを入れて`I150 Blue` run。復元/build後に起動しcrash buttonを押す | build失敗・起動成功・runtime crashを区別。失敗した実行から別run/deviceの成功を返さず、対象に属するerrorだけ表示 | pending |
| C06 / T18 | D1/D2で起動した状態からD1だけ切断またはemulator停止。D1対象の読取/runを要求し、その後再接続 | offline/disconnectedを日本語で明示。D2へ自動転送しない。再接続後にdevice/process同一性を取り直す | pending |
| C07 / T18 | 同じappをD1/D2で動かし、Red/Blue両方が同じlog tagを出す。対象appを再起動し古いmarkerも残す | device/package/PID/runNonce/起動区間で限定。直近5秒/100件/32 KiBを検査。他app/deviceと旧processの履歴を返さない | pending |
| C08 / T18 | 同一device・同一PIDだが時刻/runNonceが異なる2起動分の合成Logcat入力を用意 | PID一致だけで古い実行へ結合しない。これはPID再利用の境界fixtureであり、OSの実PID再利用を実測した扱いにはしない | pending |
| C09 / T18 | Red/debugとBlue/debugの2 debug sessionを開始しbreakpointで停止。対象sessionのstack/許可marker変数を取得、resume後に旧frameで再要求 | 明示session/execution IDを使う。suspended時だけ値取得、resume後はframe失効。releaseはdebug不可を明示。既存Debugger MCPの能力も測る | pending |
| C10 / T17+T18 | bounded読取中にCancel、fixture project close、またはdevice切断。ログを100件超生成 | 期限/取消/打切りが明示され、古い結果・購読が残らない。他session/processは停止しない。上限を超えるログ/変数は公開しない | pending |

C08は将来の経路が合成入力を受け取れる場合に実施する。受け取れない実IDE/MCP側を未確認のままpassにせず、
実PID再利用未観測として残す。OSへPID再利用を強制するための無制限process生成は不要。
Caseを通すためのbuild変更やprobe追加が必要なら、その差分も固定候補へ含めてから再実行する。

## 4. 比較記録と採用条件

共通prompt例: 「現在IDEで選択しているmodule・Variant・実行先deviceと、その実行の直近ログを確認してください。
対象が不明、切替中、取得不可なら理由を示し、別の対象では代用しないでください。」
失敗/debugger Caseには該当操作だけを追記する。A/Bとも新規sessionで同じprompt、同じ許可範囲を使う。

各行に`Case / fixture commit / IDE+plugin build / model+CLI / route A|B / actual tool+version /
selected target / observed target / status / user操作数 / target訂正回数 / 結果までの時間 /
許可UI / 日本語error・復旧導線 / 証拠 / blocker・owner・next action`を記録する。
実serial/PID等の対応はprivate evidenceで検証し、公開時はaliasへ投影する。取得できない値はunknownとする。
初回setupの操作数と通常turnの操作数を分け、手動で答えを補った操作も数える。

基本8組は各経路で1巡し、採用候補の差が出たCaseだけ順序をA→B/B→Aで入替えて計3回確認する。
時間は中央値と範囲を記録するが、少数試験を一般性能差とは呼ばない。対象誤一致が1件でもあれば優位性は保留する。
機能の存在だけでなく、切替・同期・失敗・取消を含む正確性とユーザーの訂正負担で判断する。

採否は次の順序で決める。

1. 既存MCP/IDE機能で満たせる: 再利用し、同等能力を独自機能と呼ばない。追加実装なしも正常な結果。
2. 同条件で繰り返せる不足があり直接読取で改善する: 対象安全性、既存UI再利用、日本語での判断しやすさを評価し、最小1機能だけ採用Issueへ。
3. 互換環境・device・測定経路が揃わない: exact combinationと次操作をblockedに記録し、優劣/採用を未判定に保つ。

現在は2を支持する実測がなく、採用Issueは作成していない。#150の実受入・GUI・最初の1機能選定は未完了。
