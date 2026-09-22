# ブランチごとの最新Plugin ZIP

[GitHub Releases](https://github.com/shinma06/cursor-in-android-studio/releases) の **Plugin ZIP — ブランチ名** を開き、Assets の `cursor-in-android-studio-<実buildの40桁SHA>.zip` を取得する。
Settings → Plugins → ⚙ → Install Plugin from Disk... でそのまま選択できる。GitHubが自動生成する **Source code (zip)** はソース一式であり、インストール用ではない。

## 保存と更新

`push / 定期確認 → 共通Change Impact → 必要な場合だけbuildPlugin → ブランチ専用ReleaseのZIP更新`

[Change Impact](change-impact.md)で生成不要なら既存ZIPを保持する。知識だけの新規branchはReleaseを作らない。最後に実buildしたSHAと現branch HEADは異なり得るため、本文/asset名のSHAで対象を識別する。古いZIPを新SHAへ改名しない。

- 生成はJetBrains Gradle Plugin標準の `buildPlugin`。元の出力は `build/distributions/<project名>-<version>.zip`。公開時は名前だけ変えてコピーし、内容は再圧縮・加工しない。旧ブランチのproject名もそのままビルドできる。
- 1ブランチに可変prereleaseを1件。タグは `branch-zip-<UTF-8ブランチ名のSHA-256全64桁>`。`/`、日本語、大文字小文字の異なる名前でも分離する。人間はハッシュではなくReleaseのタイトルで選ぶ。
- Release作成・更新の `target_commitish` はrepository APIの `default_branch` を使う（`main` 固定ではない）。新規タグは作成時の既定ブランチを参照し、既存タグは旧方式で作成したものも移動・force更新しない。ソースブランチ固有のworkflow変更を含むSHAへのタグ作成で、GITHUB_TOKENの権限不足になることを避ける。**ZIPのソースはRelease本文とZIP名のHEAD SHA**を参照する。タグ/Source codeリンクはZIPのソースや最新ソースの表示には使わない。
- 新ZIPのアップロード成功後に本文を更新し、旧assetを削除する。通常は1 ZIPのみ。中断時に2件残る場合は次回で整理する。失敗した新ビルドのために最後の正常なZIPは削除しない。
- ブランチ別に公開を直列化し、公開前に現在のHEADを照合する。古いビルドは巻き戻し公開しない。
- Release assetにActions Artifactの保持期限はない。ブランチに長期間pushしなくても残る。定期処理は公開済みZIPの延命・再ビルドには使わない。
- buildは読取権限のみのrunner、publishは別runner。Actions Artifactは両者間の**1日限りの転送**だけで、配布先ではない。PRイベントではZIPを作らない。
- `build/` はGit管理対象外。ZIPのコミットやcommit別のReleaseを追加する必要はない。

新構成のsourceはGradleが固定Quail 1 SDKを取得する。旧構成branchの復旧では、checkoutしたgradle.propertiesに有効なplatformPath代入がある場合だけ従来のQuail 3 Patch 1を取得・指定する。コメント行だけの例は旧構成と判定せず、新構成には旧SDKを渡さない。

## 既存ブランチと復旧

新workflowを持つすべてのブランチでpushに反応する。ブランチ名の固定フィルターはない。
新workflowを持たない既存ブランチ、およびGITHUB_TOKEN経由のpush/mergeは、既定ブランチで毎時実行する**生存ブランチのHEADだけ**の確認で補完する。全commit走査やsource保存タグは廃止した。

手動復旧: Actions → **Branch Plugin ZIP** → Run workflow。新workflowを含む既定ブランチを選ぶ。`branch` が空なら全生存ブランチ、入力すればその1本のHEADを確認する。共通判定で必要なHEADをビルドする。`force_build`を有効にすれば、知識変更を含む現HEADのZIPも明示生成できる。

生成不要と判定したHEAD、CI待ち・ビルド失敗の間は新HEADのZIPは存在しない。Release本文のSHAとブランチHEADを照合し、失敗原因を直して再実行する。GitHubのscheduleは遅延し得るほか、公開repositoryでは60日無活動で停止するため必要なら再有効化する。公開済みZIPには影響しない。
ブランチが256本を超えて同時に未公開の場合はActionsのmatrix制限で明示停止するので、手動でbranchを指定する。

### 削除済み作業ブランチの掃除（#219）

既定ブランチの毎時scheduleは、削除済み作業ブランチの管理対象Release・Assets・専用タグを掃除する。手動では `cleanup` がfalseなら候補/保留理由をJob summaryに出すdry-runのみ、trueなら実行する。`branch` 指定は配布と掃除の両方をその名前に限定する。pushでは掃除しないため、イベント取りこぼしも次の定期/手動実行で回収する。

管理対象はタグの完全なbranch名SHA-256、本文の単一branch/sha marker、配布タイトル、mutable prerelease、正常な単一ZIPの名前/状態/サイズを照合する。main/master/developと現在のdefault branch、現存ブランチは更新日によらず保護する。識別不能な履歴、draft、タグ単独残存、タグ欠落Releaseは保留し、名前のprefixだけでは削除しない。API/認証失敗は処理失敗であり、不存在の証拠にしない。

cleanupとpublishは同じtagのActions concurrency groupを共有する。lock取得後に候補のRelease ID/ソースSHA/識別情報を再照合し、削除直前にも全branch APIを取得する。Release（Assetsを含む）削除後、branch再作成・Release再作成・tag object変更がなければタグを削除し、両方の不存在をreadbackする。

GitHubの複数APIは原子的ではなく、人間のbranch再作成や直接Release操作をlockできない。観測した再作成/変更は保留し、生存branchの配布は次の通常plan/publishで復旧する。最終確認直後の外部変更まで無競合と保証しない。pending jobが別runに置換された場合も次のschedule/manualで再照合する。

途中失敗はActionsを失敗として残し、削除前のRelease ID/branch/ソースSHA/tag objectのreceiptと次操作をJob summaryへ記録する。Release削除失敗は次回も候補を再検証できる。Release削除後のtag削除失敗は**タグ単独の保留**となり、自動再試行で推測削除しない。PMが当該runのreceiptと新鮮なbranch/ref情報を照合して残存を引き継ぐ。過去QAの配布リンクは履歴のため書き換えない。

初回は `GITHUB_REPOSITORY=owner/repo python3 scripts/workflow/branch_zip.py cleanup-plan` でread-only一覧を記録する。実削除は同じ排他を使う既定branchのActionsで行い、ローカルCLIをpublishと並行実行しない。

根拠: [Release API](https://docs.github.com/en/rest/releases/releases)、[Git reference API](https://docs.github.com/en/rest/git/refs)、[Actions concurrency](https://docs.github.com/en/actions/concepts/workflows-and-actions/concurrency)。

## 旧運用からの移行

以前は全履歴のSHA別Release、`plugin-source-*` 保持タグ、通知workflow、ローカルcacheとcheckout/pull同期runtimeを使っていた。現在の要件には不要なため削除した。pre-pushは変更保護とテストだけを行う。
旧runtime導入済みのworktreeは、この変更を取り込んだIssueブランチで一度 `bash scripts/workflow/bootstrap.sh` を実行すると `.githooks` へ戻る。独自のhook設定は勝手に上書きしない。`.git/plugin-zip` のcache/runtimeは旧worktreeでの利用がなくなってから削除でき、配布には使わない。

初回移行では全生存ブランチのReleaseを確認してから、旧 `plugin-build-<SHA>` Releaseと同名タグ、`plugin-source-<SHA>` タグを限定削除する。過去のIssue/QA証拠は書き換えず、削除済み配布物のリンクは履歴として扱う。

旧baselineの `verifyPluginStructure` は日本語descriptionのLatin文字条件で停止した（[Phase 1記録](../research/modernization-baseline-2026-09-21.md)）。#389では日本語説明を保持して英語概要を先頭へ追加する。構造検査の成功と両IDEのAPI/GUI互換性は別判定で、後者は #390/#392で確認する。Marketplace公開は対象外。

## 方式の選択

| 候補 | 判断 |
| --- | --- |
| Actions Artifactだけ | 保持期限があるため不採用 |
| commit別Release / GitへのZIPコミット | 不要な履歴が増えるため不採用 |
| ブランチ別Release asset | 期限に依存せず、同じページで最新ZIPを取得できるため採用 |

根拠: [JetBrains標準buildPlugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#build-plugin)、[ZIPからのインストール](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)、[GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)、[Actionsイベントとschedule](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)。

初回確認（#127）: [push実行](https://github.com/shinma06/cursor-in-android-studio/actions/runs/34348315165) で標準ZIPの生成・Release公開が成功。取得したZIPのCRC、プラグインID `com.cursoragent.plugin` と依存JAR同梱を確認した。公開後の更新・全ブランチ移行の結果は [Issue #127](https://github.com/shinma06/cursor-in-android-studio/issues/127) に記録する。

## 正式候補RCと同一ZIPの公開

開発用の可変branch prereleaseとは別に、`plugin-rc-<正式version>-<ZIPのSHA256>`という専用prereleaseへ候補を保存する。`release_candidate.py`は既存assetの上書き・削除を実装しない。途中失敗は同じbytesのdraftだけ再開でき、異なるbytes/metadataや公開済み不完全Releaseは停止する。管理者の直接編集・削除までGitHub側で禁止する方式ではない。候補を使っている間は手動削除しない。

GitHubのrelease immutabilityはrepository/organization単位で今後のReleaseへ適用され、可変branch配布と両立しないため、ここでは有効化しない。強制ロックが必要になったら配布先分離を先に設計する。[公式の設定範囲](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/establish-provenance-and-integrity/prevent-release-changes)

正式RCと正式Releaseにはbranch-zip markerを付けず、既存cleanupは削除対象にしない。Actions artifactは転送用のまま。専用RC Releaseに保持期限は設けず、全検証・公開後も元候補を保持する。失敗時は既存RCを更新せず、新しい入力・source・hashの候補を作り、必要検証をやり直す。

### 1. versionとsourceを決め、一度だけ生成する

正式versionはユーザーが決める。以下の変数は実値を担当が設定する（例示の架空versionを採番しない）。必要な実装・文書をdevelopへ統合してからsource SHAを固定する。cleanな専用候補checkoutをそのSHAに置き、JDK 21を指定する。RC作業ディレクトリはcheckoutの外に置く。GitHub公開用のwrite tokenをbuildへ渡さず、通常のbuild権限と公開権限を分ける。

```bash
# VERSION=承認済みの正式version、SOURCE=固定developの40桁SHA、RC=未使用の外部ディレクトリ
python3 scripts/workflow/release_candidate.py build \
  --source "$SOURCE" --version "$VERSION" --directory "$RC"
```

処理はversion/source/build引数・設定ファイルhash・実JDKを先に`inputs.json`へ記録し、`clean test buildPlugin verifyPluginStructure`を一度実行する。local SDK overrideを無効にし、最古SDKの実full build、JVM21、内部version、clean sourceを照合する。Plugin ZIPは再圧縮せずコピーし、manifestにhash/size/内部identity、inputsに同梱JAR一覧を残す。依存の宣言版は固定sourceのbuild/settings/Wrapperとそれらのhashへ対応付ける。既存出力ディレクトリへ再buildする操作は拒否する。

### 2. 同じZIPを両IDEで検証し、RCとして保存する

`HASH`には生成時に表示されたSHA256を固定する。`Q1`/`Q4`は未使用の検証出力ディレクトリ、`SDK_CACHE`は公式SDK取得用ディレクトリ。

```bash
python3 scripts/workflow/plugin_compatibility.py verify --target quail1 \
  --archive "$RC/cursor-in-android-studio-$VERSION.zip" --manifest "$RC/manifest.json" \
  --sha256 "$HASH" --output "$Q1" --sdk-cache "$SDK_CACHE/quail1"
python3 scripts/workflow/plugin_compatibility.py verify --target quail4 \
  --archive "$RC/cursor-in-android-studio-$VERSION.zip" --manifest "$RC/manifest.json" \
  --sha256 "$HASH" --output "$Q4" --sdk-cache "$SDK_CACHE/quail4"
python3 scripts/workflow/release_candidate.py bundle --directory "$RC" \
  --sha256 "$HASH" --quail1 "$Q1" --quail4 "$Q4"
```

API検証は固定Quail 1 `AI-261.23567.138.2611.15503007` / JBR 21.0.10とQuail 4 `AI-261.26222.65.2614.16379836` / JBR 25.0.3。Phase 3と同じVerifier・限定optional例外・report判定を使う。片方の失敗、全クラス未完、別hash、詳細欠落を拒否する。公開bundleはZIP・manifest・入力と両方の検証レポート。SDKパスを含むraw logはローカルに保持し、公開するログは検査済み完了行の抜粋のみ。GUI結果は既存promotion.jsonが正本で、未実施の結果をRCへ書き込まない。

公開担当は上のbundleを受け取り、レビュー済みtoolingから次を実行する。`GITHUB_REPOSITORY=shinma06/cursor-in-android-studio`と必要なGitHub権限を公開段階だけで設定する。同じ候補の公開操作は担当を1名にし、並行実行しない。

```bash
python3 scripts/workflow/release_candidate.py store --directory "$RC" --sha256 "$HASH"
```

RC tagは保存場所の識別子で、権限を増やさず作成できるよう既定branchへ置く。**RC ZIPのsourceはmanifestの固定SHA**であり、RC tagのSource code ZIPではない。正式tagは後述のmain promotion mergeへ対応付ける。これはGITHUB_TOKENでdevelop側のworkflow変更を含むcommitへtagを作る権限上の制約も避ける。[Release APIの権限](https://docs.github.com/en/rest/releases/releases#create-a-release)

### 3. 保存したRCを取得して全GUIを確認する

保存時のRC tagと、別途固定した`HASH`を指定し、新しい出力ディレクトリへ取得する。Git履歴は固定sourceを含む完全なfetchが必要。

```bash
python3 scripts/workflow/release_candidate.py fetch \
  --tag "$RC_TAG" --sha256 "$HASH" --directory "$DOWNLOADED_RC"
```

取得処理は6 assetsと全hash、内部version/source/SDK、両Verifier結果を照合する。GUI担当はこのZIPをインストールし、host共通leaseとロードJAR照合の後、固定候補の**全必要Case**を確認する。対象両IDE・Terminal有効/無効も#397/#392のCaseへ記録する。Case結果には同じcandidate/hashを使い、過去buildのpassを転用しない。[正式promotion手順](../verification/README.md#固定候補からmainへ)

### 4. main昇格後、同じbytesを正式公開する

Phase 6担当は通常のpromotion PRをmerge commitでmainへ統合してから、次を実行する。Phase 4ではこの実公開を行わない。

```bash
python3 scripts/workflow/release_candidate.py publish --directory "$DOWNLOADED_RC" \
  --sha256 "$HASH" --promotion-pr "$PROMOTION_PR"
```

既存promotion validatorで全commit・全必要Caseを再確認し、mergeの実親、mainへの包含、candidate/hash、4必須checkを照合する。不足・別候補ならRelease作成前に失敗する。保管RCをもう一度取得し、`v<version>`をそのmain mergeへ、asset名を`cursor-in-android-studio-<version>.zip`へ対応付ける。ZIP内部versionは初めから正式値のまま。Gradleの再実行・再圧縮・version書換えはしない。公開後に全assetをdownloadして実hashを照合する。同じ正式tagの別commitや別assetは拒否する。

公開済みでもdownload/readbackに失敗したら完了扱いにしない。同じ入力で再照合し、異なるデータなら保持して調査する。RC/正式Releaseの削除やGitHub保護設定変更を復旧手段にしない。公開記録にはsource/version/full build/JBR/hash/公式URL/PRを残し、ローカル絶対パス・host・秘密は記録しない。

## Phase 6の公開準備

2026-09-22 / [#406](https://github.com/shinma06/cursor-in-android-studio/issues/406)。以下は公開説明と最終報告の準備であり、正式Releaseの完了報告ではない。追加GUI・固定候補の全Case試験・main昇格・正式公開は [#393のTODO](https://github.com/shinma06/cursor-in-android-studio/issues/393) に残す。準備文書だけの完了で親IssueやMilestoneを閉じない。

### 更新内容と確認範囲

変更前は [Phase 1 baseline](../research/modernization-baseline-2026-09-21.md)、更新後は固定source [`28e9c441e8eb7824cb3e193361221f715b929eb2`](https://github.com/shinma06/cursor-in-android-studio/tree/28e9c441e8eb7824cb3e193361221f715b929eb2) の宣言と保存RCの入力記録に基づく。将来の最新版を示す表ではない。選定時の公式資料・互換範囲はbaselineのリンクを参照し、ビルド成功を公式保証へ読み替えない。

| 項目 | 変更前 | 更新後 |
| --- | --- | --- |
| Gradle Wrapper | 8.13 | 9.7.1 |
| Kotlin Gradle Plugin | 2.3.0 | 2.4.20、language/API 2.3 |
| IntelliJ Platform Gradle Plugin | 2.10.5 | 2.19.0 |
| Foojay resolver | 0.9.0 | 1.0.0 |
| Gson | 2.11.0 | 2.14.0 |
| commonmark | 0.30.0 | 0.30.0（維持） |
| JUnit | 5.11.0 | BOM/Jupiter 5.14.4とPlatform launcher |
| build実行JDK / toolchain / JVM target | 21 / 21 / 21 | 21 / 21 / 21（維持） |
| compile SDK | 環境依存のlocal SDK | Quail 1初版2026.1.1.8のfull build固定 |
| Kotlin stdlib | 2.3.0をZIPへ同梱 | 同梱せずIDE提供2.3.20を使用 |

保存RCのlibは本体・searchableOptions・Gson 2.14.0・commonmark 0.30.0・error_prone_annotations 2.48.0。旧annotations 13.0とKotlin stdlibは含まれず、coroutinesも同梱しない。実一覧はRCの `inputs.json` を正本とし、新候補では再照合する。明示local SDKは既定経路にせず、固定full buildと不一致・確認不能なら失敗させる。

対象方針はAndroid Studio StableのPlatform 261系。検証端点はQuail 1初版 `2026.1.1.8 / AI-261.23567.138.2611.15503007 / JBR 21.0.10` とQuail 4 Patch 1 `2026.1.4.8 / AI-261.26222.65.2614.16379836 / JBR 25.0.3`。メタデータの `sinceBuild=261.23567.138 / untilBuild=261.*` は、この系統の全IDE・全OSで実測済みという意味ではない。単一のJVM target 21 ZIPを両JBRで検証する。

[#392の保存RC](https://github.com/shinma06/cursor-in-android-studio/issues/392) はversion `0.1.0`、sourceは上記固定SHA、ZIP SHA-256は `e0c52cd6e60f254810dd43f2f5903b9f918485945ad2edc4583914fde36cbcb4`。445 JUnit、構造検査、Verifier 1.410による両IDEの427本体クラスのAPI検査は成功した。実IDEのInstall from Disk・再起動・ロード照合とTerminal有効/無効の部分確認はあるが、Tabの製品failと多数の未確認Caseがあり、全受入は未完了である。このRCを正式assetとして公開しない。

残る制約は次のとおり。検証結果を新sourceの結果へ転用しない。

- Gradle 9.7.1は選定時のKGP完全サポート上限9.7.0との差がある。実ビルド成功と公式の完全サポート範囲は分けて記録する。
- Verifierの任意依存例外は [固定policy](../../scripts/workflow/plugin_compatibility.json) のJCEF・XPathView・Python・IDEA Community・trainingの5件と対象full buildだけ。必須依存や新しい未解決依存を許容しない。非推奨・experimental・internal APIと既知ログwarningの根拠も同policyへ結び付け、対象拡張時に再評価する。
- 入力のTabは [#205](https://github.com/shinma06/cursor-in-android-studio/issues/205)、`@`候補の通知・文書変更は [#404](https://github.com/shinma06/cursor-in-android-studio/issues/404) で修正と再確認を追跡する。候補の開閉が観測中のフォーカス移動でも変わり得る知見を含め、修正コードの成功を実IDE合格としない。
- ACPの既存受入・公開承認待ち [#146](https://github.com/shinma06/cursor-in-android-studio/issues/146) は別ownerのまま。今回の文書作業から解除しない。

### 公開説明の下書き

以下は受入完了後、実際の最終候補・正式Release URLへ対応付けて使う文案。未記入の参照や未達条件を残したまま正式公開済みの説明へ切り替えない。公開ツールが生成するRelease本文の識別marker/JSONは保持し、文案で置換しない。

> Cursor in Android Studio 0.1.0は、Android Studio 2026.1系（Platform 261系）向けにビルド構成と配布方法を更新しました。確認済みのIDE・JBRと試験範囲は最終検証記録を参照してください。JVM target 21の単一ZIPを、検証時と同一の内容で配布します。プラグインIDと設定・履歴の保存先は維持しています。
>
> 正式ReleaseのAssetsから `cursor-in-android-studio-0.1.0.zip` を取得し、掲載されたSHA-256と照合してください。自動生成されたSource code (zip)はインストール用ではありません。Settings → Plugins → ⚙ → Install Plugin from Disk...でZIPを選択し、IDEを再起動します。

文案に添える確定情報は、正式Release URL、最終検証記録URL、確認済みIDE/OS/JBR、未確認範囲・残制約、ZIP SHA-256。承認済みversionは0.1.0だが、新候補のsource/hashや正式URLはまだ確定しない。正式tag `v0.1.0` はmain promotionのmerge commitを指し、ZIPのbuild sourceはmanifestの固定develop SHAを指す。この違いを公開記録へ残す。

### 再開時の順序と残TODO

1. **修正と残条件を照合する。** #205/#404の修正・独立レビュー・develop統合、#392の直接範囲、#146等の必要Caseの前提を確認する。現RCの結果やPR #402をそのまま合格へ変更しない。
2. **新候補を固定する。** 必要な実装・文書の統合後に、version/source/対象IDE/全commit・全必要Case一覧を固定する。変更したsourceは新RCとして上の手順1〜3で生成・保存・取得し、同一ZIPで必要検証をやり直す。旧RCは上書きしない。試験中の後続develop変更はこの固定候補へ自動追加しない。
3. **受入後にmainへ昇格する。** 同一candidate/hashの全必要Case・互換性・独立レビューと最新4必須checkを照合し、通常のpromotion PRをmerge commitで統合する。未達ならここで待つ。追加GUI・全Case試験の再開自体は今回の作業外。
4. **検証済みZIPを公開し、取得して照合する。** 上の手順4を使う。公開判定の試行として `publish` を実行しない（dry-runではない）。再build・再resolve・再圧縮・version編集はせず、正式assetの全hashを保存RCと照合する。
5. **最終報告と後片付けを行う。** source/version/tag/main merge/asset/hash、依存・lib・JDK/SDK/JBR、Verifier/GUI/全Case、公式範囲と実測・残warningを最終候補の記録で確定する。READMEを実公開状態へ更新し、QAのmain包含を個別照合する。最終監査は [#394](https://github.com/shinma06/cursor-in-android-studio/issues/394) を再利用し、owned clean停止済み資源のcleanupと残存理由・担当・再開条件、Project/native関係をreadbackしてから親/Milestoneの完了を判断する。

公開担当は再開時にclaimで確定する。既存owner・保存証拠・未merge branchとPAUSED coordinatorを維持し、準備PRの登録だけで自動統合・公開が進んだと扱わない。
