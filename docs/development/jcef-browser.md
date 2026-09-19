# Android Studioの手動ブラウザー: JCEFの導入と再試験

2026-09-12 / 修正 [#208](https://github.com/shinma06/cursor-in-android-studio/issues/208)、GUI/main受入 [#164](https://github.com/shinma06/cursor-in-android-studio/issues/164)。実装は [ManualBrowserPanel](../../src/main/kotlin/com/cursoragent/ui/browser/ManualBrowserPanel.kt)、確認手順は [4 Case](../verification/changes/issue-208.json)。

## 確認した互換範囲と限界

JetBrainsはAndroid Studio向けに、native JCEFを提供する実験的な **Web Browser (JCEF)** とmodule ID `com.intellij.modules.jcef` を公開している。[公式発表](https://platform.jetbrains.com/t/experimental-jcef-web-browser-api-support-for-android-studio/4117)

| 対象/配布物 | 2026-09-12の静的確認 |
| --- | --- |
| 対象IDE | Quail 4 2026.1.4、`AI-261.26222.65.2614.16204760`。手元SDKのproduct-infoと#208の試験環境識別が一致 |
| 実行JBR | bundled `25.0.3+-15898627-b508.16`、Darwin/aarch64。JBRのMODULESにjcefなし。IDEのJCEF API JARだけではnative実体を提供しない |
| 公式provider | JetBrains、`com.intellij.modules.jcef`、Marketplace ID `31360` |
| 対象向け版 | `261.22158.414-mac-arm64`、update `1028047`。`since-build=261.22158` / `until-build=261.*` に対象buildを含む |
| IDE互換表示 | Android Studio **Quail 1 2026.1.1 — Quail 4 2026.1.4**。API metadataとZIP内descriptorで261範囲を照合 |
| OS/CPU | ZIP descriptorが`com.intellij.modules.os.mac`と`com.intellij.modules.arch.arm64`を要求。他OS/CPU用の版を代用しない |
| native提供 | ZIP内に`libjcef.dylib`、`Chromium Embedded Framework`等があり、`com.intellij.cefNativeBundleProvider`へprovider実装を登録 |
| 実行結果 | **未確認**。配布宣言との一致は、対象JBRでのnativeロード・描画・復旧成功を証明しない |

配布根拠: [公式版ページ](https://plugins.jetbrains.com/plugin/31360-web-browser-jcef-/versions/stable/1028047)、[公式Marketplace API](https://plugins.jetbrains.com/api/plugins/31360/updates?size=100)。調査用ZIPはCRC確認済み、SHA256 `923cf9706b7cdc863c4a48bba06d69f50af3ab818c164a67e3e2bcd9691fb64e`。これはproviderのhashであり、このプロジェクトのPlugin ZIPのhashではない。ZIPを読み取っただけで、インストール・native実行・JBR変更はしていない。

[要検証] provider descriptorにはJBRの完全な版やmacOSの最低版の動作保証はない。実機担当がOS版、起動JBR、provider版と有効状態を記録して試験する。最新版が263系でも、261系IDEへそのまま適用しない。

## 利用者の導入・回復手順

1. ブラウザーの利用不可画面で「プラグイン設定を開く」を選ぶ。Pluginsの「Marketplace」で **Web Browser (JCEF)** を検索し、提供元が **JetBrains** で、IDE・OS・CPUに対応した版であることを確認して導入する。導入済みなら「Installed」で有効状態を確認する。
2. IDEが案内する再起動を行い、チャットの︙から「ブラウザーを開く…」を再度選ぶ。導入だけで必ず復旧するとは扱わない。
3. 対応版が見つからない場合は、回復画面の「公式配布ページを外部ブラウザーで開く」で互換範囲を確認する。対応版を導入できない構成では内蔵ブラウザーを利用できない。ネットワーク障害による検索失敗と非互換は区別する。
4. 導入済みでも失敗する場合は、IDEのログ、起動JBRとproviderの互換性を確認する。初期化例外とnative linkage失敗はログにも記録する。独自JBRへの差し替え、registry/security設定変更による強制有効化はこの実装の回復手段に含めない。

画面の表示だけでは設定や外部ページを開かない。設定ボタンはIDE標準のPlugins設定を開くだけで、install/enable/restartを自動実行しない。公式ページボタンは固定の配布URLのみを明示操作で開く。URL欄に入力したページを外部ブラウザーへ逃がす処理は追加していない。

## 依存と製品の境界

`plugin.xml`はJCEFを **optional** として宣言する。provider不在/disabledでplugin全体のロードを止めないのが選択理由。必須依存は導入条件を強められるが、ブラウザー以外のチャットまで利用不可にするため採用しない。任意依存は未導入時の自動installを保証しない。[SDKの任意依存仕様](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#optional-plugin-dependencies)

追加descriptor `cursor-agent-jcef.xml`は空で、Browser ToolWindowと回復画面は引き続き主descriptorに登録する。利用APIは既存IDE SDK内にあり、provider固有クラスを直接参照しない。GradleでOS固有providerを強制取得・同梱せず、native発見はIDE標準に任せる。対象SDKの`JBCefApp`にはnative bundle providerの探索経路があることを静的照合した。

実行時の`JBCefApp.isSupported()`確認を維持する。false時と起動例外時はURL入力/移動を無効化し、日本語の回復画面を表示。成功時は従来どおり`about:blank`から開始する。入力URLのHTTP(S)/認証情報/host/port境界、popup制限、project別contentとDisposer、終了後callbackの抑止は維持する。[SDKの対応判定と所有権](https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html)

新規回復操作もproject/panel終了後は実行しない。回復画面の初期focusは無効なURL欄ではなく設定ボタン。Android Activity/構成変更、coroutine、Context、DBはこのSwing/JCEF変更では使用しない。

## 実装検証とGUI引継ぎ

CLIではnativeを起動しない回復UIの明示操作、optional依存/無条件登録、既存URL境界、全体テスト、Plugin ZIP内descriptorを確認する。これは実IDEのprovider不在時のチャットloadや、導入後のnative/描画の代替証拠ではない。

指定GUI担当がlease下で修正source・Plugin ZIP SHA256・installed全JAR・起動ロード実体を固定し、#164へ修正PRと4 Caseを双方向引継ぎする。

| Case | 再試験の要点 |
| --- | --- |
| BROWSER-ENTRY-NAVIGATION | provider導入後の入口/about:blank/URL/履歴/閉じて再表示 |
| BROWSER-URL-BOUNDARY | 有効URLと拒否、redirect/popup、無許可の外部起動なし |
| BROWSER-ERROR-SUPPORT | 未導入/disabledでもチャットload、日本語回復導線、設定と公式ページ、導入後の描画、接続/初期化失敗 |
| BROWSER-LIFECYCLE-PROJECT | content/project終了、遅延callback、他project分離 |

現時点の4 Caseは修正候補では**pending / 未実施**。#164の既存の製品NGをpassへ上書きしない。到達できない詳細手順は未実施のまま残す。developの実装受入後もGUI/main受入の#164を閉じない。#203のBrowserアイコン系列も未実施を維持し、利用可能な固定buildで再開する。チャット/ACP/モデル等の別試験を一律blockedにしない。
