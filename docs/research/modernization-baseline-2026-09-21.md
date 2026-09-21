# Platform 261 モダン化の対象と更新前baseline

2026-09-21 / [Phase 1 #388](https://github.com/shinma06/cursor-in-android-studio/issues/388)。
方針の正本は [#387](https://github.com/shinma06/cursor-in-android-studio/issues/387)。
本記録は更新前の調査であり、更新後の互換性・正式RCの受入ではない。

## 対象の決定と配布物の確認

ユーザー決定は「Platform 261系（2026.1系）を対象にする」。Quail 1 Patch 2という当初候補を、
同系統の最初のStableであるQuail 1初版へ変更した。compile SDKは初版を固定し、最新Stableを上端の検証対象にする。

| 項目 | 下限・compile SDK | 最新Stableの検証対象 |
| --- | --- | --- |
| Android Studio | Quail 1 / 2026.1.1 初版Stable | Quail 4 / 2026.1.4 Patch 1 |
| 配布version | 2026.1.1.8 | 2026.1.4.8 |
| Full build | AI-261.23567.138.2611.15503007 | AI-261.26222.65.2614.16379836 |
| 同梱JBR実測（macOS ARM64） | 21.0.10+-117844308-b1163.108 | 25.0.3+-15898627-b508.16 |
| IDE提供Kotlin stdlib実測 | 2.3.20 | 2.3.20 |

根拠は [公式一覧](https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html)と
[配布XML](https://jb.gg/android-studio-releases-list.xml)。下限DMGを公式URLから取得しSHA-256を照合、
両実IDEの `Resources/product-info.json`、同梱JBR `release`、`lib/util-8.jar` の
`kotlin.KotlinVersion.CURRENT` を確認した。上端は既存インストールを照合したため、配布DMG自体のhash照合は未実施。
Linux/Windows配布物のJBR実測・GUI結果をmacOSの結果から推定しない。

- 下限Mac ARM: [公式DMG](https://edgedl.me.gvt1.com/android/studio/install/2026.1.1.8/android-studio-quail1-mac_arm.dmg)
  / SHA-256 `85e17dfddf23b42d4ad9f8749d566657e752ec2f2432c3d49f4d83a0fa1fdf48`（取得実体一致）。
- 下限Linux: [公式tar.gz](https://edgedl.me.gvt1.com/android/studio/ide-zips/2026.1.1.8/android-studio-quail1-linux.tar.gz)
  / SHA-256 `0c1fa4ba3cfabd07e48a90e00a5fa2ea2a82cd5870833dada5b0290c817d83d3`（XML値、今回未取得）。
- 上端Mac ARM: [公式DMG](https://edgedl.me.gvt1.com/android/studio/install/2026.1.4.8/android-studio-quail4-patch1-mac_arm.dmg)
  / SHA-256 `1a3393306007f90cbf2ee73e45beb809ab09aa406c2f61ee9fe81b4ef511c066`（XML値、今回未取得）。

metadata候補は `sinceBuild=261.23567.138`、`untilBuild=261.*`。
前者は初版StableのPlatform build、後者は261系の上限を表す。初版より前のEAPや262以降を対応済みにせず、
READMEの正式対象は261系のStableと明記する。metadataだけでは配布channelを制限できない。
両端の試験を中間の全patch個別実測とは表示しない。
[build範囲の公式仕様](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html)に従い、Phase 2で生成descriptorも確認する。

## Phase 2へ渡すstable構成

これは採用・検証する候補の固定であり、組合せの実測合格ではない。

| 要素 | baseline | 第一候補・判断 |
| --- | --- | --- |
| Gradle Wrapper | 8.13 | 9.7.1。最新stable patchを優先するがKGP公式の完全サポート上限9.7.0との差を明記し、Phase 2で検証。問題時は9.7.0を評価する。 |
| Kotlin Gradle Plugin | 2.3.0 | 2.4.20。stdlibはIDEの2.3.20を使い、languageVersion/apiVersionは2.3に固定して生成API・両IDE linkageを検証する。 |
| IntelliJ Platform Gradle Plugin | 2.10.5 | 2.19.0。Gradle 9以降の要件、runtime除外・Verifier runtime選択の変更を確認する。 |
| Foojay resolver | 0.9.0 | 1.0.0。Java 17以上、Gradle 9準備を含むstableへ更新する。 |
| Gson | 2.11.0 | 2.14.0。ConversationStoreの既存JSON保存/読込とACP/print parserテストを維持する。 |
| commonmark | 0.30.0 | 0.30.0維持。調査時点のstableから上げる必要なし。 |
| JUnit Jupiter | 5.11.0 | 5.14.4。JUnit 5を維持し、整合するPlatform launcherをtestRuntimeOnlyへ明示する。 |
| Gradle runtime / Kotlin toolchain / JVM target | JBR 21.0.11 / 21 / 21 | JDK 21 / 21 / 21を維持。IDE JBR 25対応のためにbytecodeを25へ上げない。 |

版と互換範囲の根拠:
[Gradle releases](https://gradle.org/releases/)、[Gradle Java互換表](https://docs.gradle.org/current/userguide/compatibility.html)、
[Kotlin Gradle互換表](https://kotlinlang.org/docs/gradle-configure-project.html)、
[Platform Plugin 2.19.0](https://github.com/JetBrains/intellij-platform-gradle-plugin/releases/tag/2.19.0)、
[Foojay 1.0.0](https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention/1.0.0)、
[Gson 2.14.0](https://github.com/google/gson/releases/tag/gson-parent-2.14.0)、
[commonmark releases](https://github.com/commonmark/commonmark-java/releases)、
[JUnit 5.14.4](https://docs.junit.org/5.14.4/release-notes.html)。
Gradle 9.7.1をKGPの「完全サポート範囲内」とは記載しない。

stdlibは `kotlin.stdlib.default.dependency=false` を明示し、compile/test classpathとZIPを確認する。
Platform Plugin 2.19.0の除外処理に加え、settings plugin使用時の既定変更もあるため、既定値だけを根拠にしない。
IDEの最古stdlibより新しいAPIを使わず、coroutinesも同梱しない。
[JetBrainsのKotlin方針](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html)と両IDE実測2.3.20が根拠。
compiler 2.4.20の採用可否とruntime 2.3.20の互換性は別判定であり、言語/API指定だけで実測を省略しない。

Gradle 9はテスト基盤の暗黙依存を削除するため、JupiterとPlatform launcherをBOM等の既存の標準手段で整合する。
既存Javaテストを削除したり、本体へJava production code/source setを追加したりしない。
[Gradle移行資料](https://docs.gradle.org/current/userguide/upgrading_version_8.html)を参照する。

## 更新前の固定baseline

| 項目 | 記録 |
| --- | --- |
| Source | `8cbdc4ea6ef5a7dfe0c9596e9e48ea09d5ba6e43`、専用worktreeの未変更・clean状態 |
| 記録日時 | 2026-09-21T01:33:38Z |
| Plugin version | 0.1.0-SNAPSHOT（正式候補ではない） |
| Compile SDK | Quail 4 Patch 1 / AI-261.26222.65.2614.16379836（既存local方式） |
| ZIP SHA-256 | `eefa900aa7b109d67843a1d12a9e02a7df63076a3ebaf2693bbb378d694c7d86` |
| 実行 | `clean test buildPlugin --console=plain`、成功、445 tests / failure 0 / error 0 / skipped 0 |
| 本体bytecode | 427 classすべてmajor 65（Java 21） |
| 内部build identity | source.commitは上記SHA、source.state=clean |

下限SDKへ変更する前のbaselineであり、「最古SDKでcompileできた」証拠にはしない。
既存CI/branch ZIPのQuail 3 Patch 1（2026.1.3.8）とも異なるため、三者の差をPhase 2/3で解消する。

ZIP lib: `gson-2.11.0.jar`、`kotlin-stdlib-2.3.0.jar`、`annotations-13.0.jar`、
`error_prone_annotations-2.27.0.jar`、`commonmark-0.30.0.jar`、
`cursor-in-android-studio-0.1.0-SNAPSHOT.jar`、同versionのsearchableOptions JAR。
`buildSearchableOptions`は既存通り無効。本体のbytecode結果を全依存JARの全classへ一般化しない。

## baselineのVerifierとGUI

公式 [Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) 1.410を使用。
同じZIPを変更せず両IDEへ渡し、各IDEの同梱JBRを明示指定した。
**両方ともdescriptor description検査で停止し、Scheduled verifications=0。API互換性は未検証**。
CLIの正常終了だけで成功と判定してはいけない。

- 分類: 既存metadataとMarketplace検査の不一致。診断は「description must start with Latin characters and have at least 40 characters」。
  現descriptorは短い英語名と日本語説明の混在。GitHub直接インストールのload failureとは分ける。
- Phase 2: 日本語の意味を保持した十分な英語概要を先頭へ足す等の最小修正を候補にし、生成descriptorで再確認。
  baseline ZIPの改変・再圧縮や問題の一括muteで、同一ZIPが合格したことにしない。
- 両SDKでproduct layoutが参照する一部JARの欠落警告も出た。今回API検査が始まっていないため、
  Phase 3は必要依存の解決と実verdict生成を確認し、警告だけで安全/不適合のどちらとも断定しない。
- build時にはProcessAdapter等の既存API非推奨、テストの不要な `!!`、Gradle 9向け非推奨まとめが出た。
  `help --warning-mode all`ではGradle警告を再現せず、実test/build実行時の詳細はPhase 2で採取する。

GUIは `baseline-388-r1`、指定operator `codex-388-modernization-phase1`、host共通lease下で実施。
使い捨てproject・IDE別config/system/pluginsを使い、通常プロファイルを上書きしない。
ZIPと配置した全JARのhash、起動ログのIDE/JBR/custom plugin、実画面を照合する。
結果の正本は [Case JSON](../verification/changes/issue-388.json) と [#388の結果コメント](https://github.com/shinma06/cursor-in-android-studio/issues/388#issuecomment-5754419513)。
両IDE × Terminal有効/無効の4条件でloadとprint送受信smokeがpass。Cursor CLIは2026.09.18-9a7762b。
Terminal無効時はGemini/App Links Assistant等の同梱pluginから依存警告が出たが、本Pluginは送受信を完了した。
配置全JARの一致とfixtureマーカー不変を再照合し、所有IDEを終了、GUI leaseとDMG mountを解放済み。
送受信smokeは既定print経路の無変更・ツール不要の短い回答であり、ACP・Diff/Revert・全既存機能の受入ではない。
最終RCは #392で全必要Caseを改めて同一候補に対して確認する。

## 後続への具体的な引継ぎ

1. [#389](https://github.com/shinma06/cursor-in-android-studio/issues/389): 上記候補を最古SDKで実測。
   `androidStudio("2026.1.1.8")` のresolutionを再検証して、成功ならCI手動downloadを整理する。
   明示local overrideは残せるが、正式buildで実full buildが期待値と違う/不明なら失敗させる。
   Wrapperを標準手順で更新し公式distribution checksumを固定。stdlib・JUnit・保存互換性・optional Terminal・hooksを維持する。
2. [#390](https://github.com/shinma06/cursor-in-android-studio/issues/390): buildした同一ZIPを両IDE/JBRへ渡す。
   description修正後の実API結果、依存不明・verdict欠落・skippedの失敗扱い、Change Impactとrequired check名を確認する。
3. [#391](https://github.com/shinma06/cursor-in-android-studio/issues/391): 正式version→commit/develop統合→source固定→ZIP生成を既存配布経路へ追加。
   不変RCを保持し、branch prereleaseの更新・cleanupと分け、公開assetは再buildせずhashをreadbackする。
4. [#394](https://github.com/shinma06/cursor-in-android-studio/issues/394): High Impact設計時監査を記録。
   最新完了#251以降のmeaningful mergeは調査時0件。設計確認を全体の完了監査にはせず、#393統合時に同Issueで全7領域を照合する。

source/hash/IDE版/公式配布元は公開できる。ローカル絶対パス、host、lease token、未加工ログ・画面はprivate evidenceに保持する。
JVM 21/25という違いだけで成果物を分割する根拠は現時点でなく、単一ZIPを維持する。
