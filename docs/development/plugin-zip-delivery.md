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

`verifyPluginStructure` も調査したが、既存の日本語descriptionが「先頭に40文字以上のLatin文字」を求める検査で失敗する。このMarketplace向けメタデータ条件を満たすために既存ブランチのソースを変更せず、配布は標準 `buildPlugin` と実ZIP/IDEの確認で検証する。Marketplace公開は今回の対象外。

## 方式の選択

| 候補 | 判断 |
| --- | --- |
| Actions Artifactだけ | 保持期限があるため不採用 |
| commit別Release / GitへのZIPコミット | 不要な履歴が増えるため不採用 |
| ブランチ別Release asset | 期限に依存せず、同じページで最新ZIPを取得できるため採用 |

根拠: [JetBrains標準buildPlugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#build-plugin)、[ZIPからのインストール](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)、[GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)、[Actionsイベントとschedule](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)。

初回確認（#127）: [push実行](https://github.com/shinma06/cursor-in-android-studio/actions/runs/34348315165) で標準ZIPの生成・Release公開が成功。取得したZIPのCRC、プラグインID `com.cursoragent.plugin` と依存JAR同梱を確認した。公開後の更新・全ブランチ移行の結果は [Issue #127](https://github.com/shinma06/cursor-in-android-studio/issues/127) に記録する。
