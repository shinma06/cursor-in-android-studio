# #457 共通準備の受領条件

#150の後続調査 #458–#461 が同じ条件から開始するための入口。比較方法と30 Caseの正本は
[比較手順](issue-150-comparison.md)と[issue-150.json](../verification/changes/issue-150.json)を維持する。
本書は準備の識別と再開条件であり、C01–C10の結果を複製しない。#457はA/B-C01とA/B/C-C02の5 Caseだけを所有し、C-C01は[#438](https://github.com/shinma06/cursor-in-android-studio/issues/438)の証拠を参照する。

## 固定する入力

| 項目 | 固定値・根拠 |
|---|---|
| fixture | `issue150-selection-fixture-v2`、source `5b2353f56d6b3f0539cfcb47ca4dc2b71646c965`、[現行manifest](../verification/fixtures/android-selection-candidate.json) |
| source archive | SHA256 `12fb8bc9249a745ab44d0421657289a959ffcca6f5f504badb0702fb5bdb8018`。保全archiveの18 fileを固定Git内容・GUI用コピーと照合 |
| 4 APK | 現行manifestのappRed/appBlue × debug/release。保全4件と、D1/D2内の各4件のbytesを照合する。package一覧だけでは受領完了にしない |
| build再現 | [fixture README](../verification/fixtures/android-selection/README.md)。Gradle9.3.1 / AGP9.1.1 / JDK21、compileSDK37.0 / Build Tools36.0.0、XML/ViewBinding。再buildのbytesが異なれば別候補 |
| 製品ZIP | #456 source `36f5e4b9282f64ca28042fe04411d044243f1bd6`、SHA256 `de4a9eb227276ddc069bd50c1a6d9730dcb6c86c353a7722079ba8162f89a906`。develop統合SHAと実build SHAを混同しない |
| 実行IDE | Android Studio Quail 4 Patch 1 `AI-261.26222.65.2614.16379836`。製品build用Quail 1 SDKとは別 |
| A / MCP | AI Assistant `261.26222.135`、MCP Server `261.26222.30`。既存27 direct toolの記録・9 module読取を再利用するが、全tool実動や選択対象取得の証明にはしない |
| A/B Agent | 管理CLI `2026.09.02-c22c1a3`、ACP protocol1。Aの保存modelは`composer-2.5[fast=true]`、UIは`composer-2.5`。Bの初回既定は別に実接続で確認し、Aの値から推定しない |
| C | Cursor `3.22.7` stable / `37076c6c3f9e253c0fa2305197e45befd13a2260` の既存専用profile。IDE内Agent panel・Composer 2.5 Fast・MCP接続の現在値を送信前に再確認。今回未起動なら過去値と明記 |
| device | D1/D2はAPI37・arm64-v8aの専用emulator。serial、connection ID、AVDとaliasの対応はprivate記録。toolbar名だけで現接続への選択を確定しない |
| 権限 | 専用fixture/profileのみ。MCP Brave mode OFF、command/対象確認あり。A/Bのnative MCP受渡しと専用登録server経由を別に記録。Shellを含む未許可要求は拒否し、その介入も操作数に含める |

旧candidate `55a4e28a05c2756a36046f395cc6d1991cdfa58a` は保全先不明の履歴を維持する。
上記v2は既に採用済みの別候補であり、旧原本の復元や同一性を主張しない。保全場所・署名鍵・endpoint・raw wireはprivate handoffだけに置く。

## 実行構成と開始snapshot

規定構成は `I150 Red → issue150-selection-fixture-v2.appRed` と
`I150 Blue → issue150-selection-fixture-v2.appBlue`。variantは構成名から推測せずBuild Variantsで読む。

1. Find Actionの`Edit Configurations…`を開く。左treeの行クリックだけでは選択が変わらない場合、treeへフォーカスを置きUp/Downで移動する。
2. **NameとModuleの両方**を読み、対象を選択してOK。toolbarの構成名と`Run '構成名'`を再取得する。名前の変更だけではmodule対応の証明にならない。
3. Build Variantsの画像でappRed/appBlueそれぞれの値を確認する。Java AXの行名と子セル名が食い違う場合はAXだけで判定しない。
4. 専用IDEの正規SDK設定UIで、projectのplatform/build-toolsとAVD system imageが同じSDK根から解決することを確認する。local.propertiesの変更だけではIDEが元設定で再生成し得る。Sync後にも参照先を読み、Device ManagerのMissing system imageが残れば実行前条件はblocked。Syncの終了記録と成功/警告を読み、進捗表示が消えただけで成功にしない。
5. 通常のdevice選択が操作できない場合、登録action `Select Multiple Devices...`のdialogでD1のみを選ぶ経路がある。固定Quail4の実装では1台でもtoolbarは`Multiple Devices (1)`となる。再表示したdialogのD1-onlyとtoolbarを併せて読む。構成名ごとに選択を保持し、dropdownとdialogの保存値は別なので、構成/mode切替後に再確認する。この代替経路は今回GUI未成立。
6. toolbarのdevice名、現在の接続先、実application ID/device/execution ID/label/logを結ぶ。保存済みdevice connection IDが古い可能性、赤い警告表示、未確定の選択があれば実行前条件はblocked。

最初はRed/debug/D1の同条件1組をA/B/C別に記録する。その後2 module × 2 variant × 2 deviceの8組、差が出たCaseの順序反復へ進む。準備の手動操作、通常turn、訂正、復元の操作数と時間を分離する。

## Bの開始と終了

BのACPは**未送信チャット単位**。正規経路は製品パネルの「… → 接続方法 → ACP」。
親メニューの`接続方法: ACP`/checkedと、選択後の日本語status・`ACPの既定モデル`表示を読み戻す。
メニュー再取得が操作環境上できない場合は、同じ未送信tabに対する人間の選択報告・選択直後status・既定モデル表示・run/buildを照合し、メニュー未readbackの制約を残す。statusだけを別tabや過去会話へ転用しない。
Settings/XML/Run Configurationの書換えでtransport選択を代用しない。別のAI ChatのCursor選択はBの証拠ではない。

#440/#456の不確定終了後は同projectの新規chat/printへ変えても再送しない。
専用project寿命ごと1promptとし、外部IDE作業・子孫processを含む完全cleanupを終えてから次の寿命を開始する。
この条件の単回成功を同会話2turn成功と呼ばない。ACP選択だけでもmetadata接続を起動し得るため、送信0でも終了確認が必要。

## 復元と受領

- 原本archive/4 APK/鍵を上書きしない。GUI用コピーのsource18 fileと、保存した実行構成の対応を区別して保全する。
- C01は変更前disk値・editor選択範囲・送信直前/許可前のdiskを独立確認する。diskまでmarkerへ変われば未保存条件喪失であり、識別pass/failを付けない。
- 終了時は入力空・Agent/Run/Debug停止・dialogなしを確認し、今回所有するIDE/CLI/MCP/AVDだけを停止する。外部IDE作業・子孫processも確認する。
- source18 fileをarchiveへ戻した後にbytes一致を検証する。未保存editorの復元とdisk復元は別に確認する。leaseは安全な停止後に自分のtokenでreleaseする。
- 後続ownerは固定source/4 APK/ZIPと5 JAR、構成→module/variant/device、model/MCP/権限、復元手順、未達・次担当を読み戻す。PMが部分受領を記録してから準備依存を解除する。#457全5 Caseの完了は解除の条件にしない。

## 今回の確認範囲

[i457-baseline-20260927-01](https://github.com/shinma06/cursor-in-android-studio/issues/457#issuecomment-5854769408)、2026-09-27。
source archive/Git/GUI用コピー18/18、保全APK4/4、端末内APK8/8、配置/実ロードJAR5/5が一致した。
Edit Configurationsで両module対応を読み、規定I150 Red/Blueへ設定。toolbarはI150 Red、画面の両moduleはdebug(default)、Syncは`BUILD SUCCESSFUL in 12s`（SDK XML版の警告1）。

人間のD1-only選択後はtoolbar `Multiple Devices (1)`を確認した。再表示試行後はDROPDOWNのD1へ変化したため、過去dialogチェックを現在選択の証拠にしない。
Device ManagerはD1/D2のMissing system imageを表示した。IDEのproject SDKはplatform/build-toolsを持つ通常SDK、AVDはsystem imageを持つ専用SDKを参照していた。
専用fixture内でSDK参照を揃える可逆修復1回を試し、人間のSync後18:42 / `BUILD SUCCESSFUL in 461ms`を確認したが、IDEがlocal.propertiesを通常SDKへ再生成し警告が残った。ADBの2台device状態とAPK8件一致は、IDEのlive選択/実行一致を証明しない。

Bは人間が同じ未送信tabでACPを選択し、直後の日本語statusと`ACPの既定モデル`表示をreadbackした。メニューchecked値は再取得できず、初回接続のmodel実値は未確認。
「その他の操作」、RunのSelect Device、Find Actionは今回のCua操作で安定してpopupを取得できず、座標操作も`noWindowsAvailable`で不成立。これは操作環境のblockedであり製品能力failではない。
Agent送信0・Run/Debug0。A/B-C01、A/B/C-C02は未実測で、29 blocked / C-C01 fail / human全30 pending / main不可を維持する。

終了時に入力空・応答未開始・Runなし・dialogなしを確認。所有12 processを追跡してlive残存0、local.propertiesを元bytesへ復元し今回の専用SDK参照2件のみ除去、source18一致、lease freeを確認した。
規定構成と固定#456 pluginは停止済み専用profileに保全する。保持理由・owner・再開条件と詳細場所はprivate handoffに置く。
次枠は正規SDK設定UIとSync後保持・警告解消・live deviceを確認し、各経路の現在model/MCP/権限を照合する。
共通準備の部分受領は未完了で、PM受領前に後続の準備依存を解除しない。準備操作/人間介入/SDK修復の時間を実測turnの指標に合算しない。
