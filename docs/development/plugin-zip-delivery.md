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
