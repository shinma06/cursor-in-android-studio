# Plugin ZIPの配布

## 現在の状態と非有効化の境界

[#572](https://github.com/shinma06/cursor-in-android-studio/issues/572)は、配布を **developの単一early → 固定正式候補Artifact → 受入・main promotion → 同じbytesの正式Release** へ統一する定義です。実有効化・運用試行・旧配布物の移行は含みません。製品はroot GradleのAndroid Studioプラグインです。製品source/build設定・既存Case・原失敗・main/developの機能差を変更しません。

| 入口 | この定義を採用した時の状態 |
| --- | --- |
| `delivery.yml` | CI完了通知と手動入口を定義するが、全jobは固定 `false && main参照`。checkout・token利用・公開・削除に到達しない |
| `release-candidate.yml` | 手動入口のみ。全jobは同じliteral false。build・Artifact uploadにも到達しない |
| `release_delivery.py` / `release_candidate.py publish` | `ENABLED = False`で外部操作前に拒否する。環境変数・secret・既存eventでは解除できない |
| 新しいRC Release保存 | `release_candidate.py store`は書込み前に拒否する。保存済みRCは読取り可能なまま保全 |
| 既存`branch-zip.yml` / `branch_zip.py` | **従来の配布・毎時巡回/cleanupを変更せず残す**。NEW CLIを呼ばない。これは有効化前の接続保全であり、恒久的な別配布方針ではない |
| 既存CI | テスト・標準ZIP・manifest・互換性検査とArtifactを従来どおり扱う。構文検査だけ限定queue互換処理を通す。公開やNEW経路のdispatchは追加しない |

従って、この定義のPR push/merge、遅着・pendingのCI通知、旧毎時eventからNEW経路の公開・削除・dispatch・移行は起動しません。**repository全体の旧公開処理が止まるという意味ではありません。** 旧branch配布は既存条件で引き続き実行し得ます。既存workflowの無効化・pending取消し、variables/secrets設定、App登録、権限付与、旧接続の撤去は今回実施しません。実APIで確認したtrigger/状態・確認不能項目と採用元の固定版はIssue/PRに記録します。

有効化担当は別の許可範囲で、旧自動接続と実行中runを照合し、所有を保って切替手順・認証・排他・停止/復旧条件を確定します。その後にコード上のgateとworkflowを通常レビューで変更します。値だけの設定で有効化したり、旧scheduleへNEW CLIを差し替えたりしません。develop側の共通運用同期は#573です。mainの定義採用だけで、develop CIやlive運用の切替済みと扱いません。

## 正式版0.1.0

[正式Release](https://github.com/shinma06/cursor-in-android-studio/releases/tag/v0.1.0)の `cursor-in-android-studio-0.1.0.zip` を取得し、Settings → Plugins → ⚙ → Install Plugin from Disk... で選択して再起動します。Source code (zip)はインストール用ではありません。

SHA-256は `0a404a9b20a22a8de24cbeecbb34ff568ec661310d1c54c4d0e7afbf5ffeeb70`。Quail 1/JBR21・Quail 4 Patch 1/JBR25の4Caseを確認した同じZIPを公開済みです。[正式版の最終報告](../releases/0.1.0.md)に対応範囲・source/tag・依存・残warningを記載しています。この履歴・公開物を新定義へ移行、再検証、再公開しません。

## 試験と配布生成を分ける

既存[Change Impact](change-impact.md)の `gradle_test` / `tooling_test` が試験の要否、`plugin_zip` が標準ZIPの生成要否です。新しい同義keyや別classifierは追加しません。runtime/resources・build設定・wrapper・依存・生成helper・未知入力は生成対象です。docs/test-onlyは必要な検査を残し、配布のための追加build/uploadを要求しません。試験自体がarchiveを必要とする場合、その試験依存は維持します。

CIの `compatibility-input` は標準 `build/distributions/*.zip` と `manifest.json` を14日保持します。publisherは同じrepositoryの**識別済み成功push CI**、現行workflow ID/path、元run/source、完全なArtifact一覧、期限・transport digestとsize、ZIP/manifest内部のsource/version/clean状態を照合します。別run・PR Artifact・期限切れ・欠落・未知の入力/履歴・不完全な必須checkを、古いZIPや成功へ置き換えません。ZIPを展開して実行したり、再build/再圧縮したりしません。

運用docs/test-onlyの通知では、過去Artifact探索、Releaseの読取り/書込み、marker更新をせず終了します。元CIの成功とGUI受入は別の事実です。

## 単一develop earlyの定義

配布先は1つの `early` prereleaseです。asset名は `cursor-in-android-studio-<ZIPのSHA256>.zip`。`early` tagは保管場所の固定識別子で、force移動しません。実製品sourceは本文とZIP内部の40桁SHAです。branch HEADが文書だけ進んでも、古いZIPを新sourceへ改名しません。

製品commit AのCI通知より先にdocs commit BがHEADになった場合、Aの通知は現在develop HEADを固定し、第一親履歴の各成功CI・merged develop PR・4必須checksと生成入力不変を確認してAのZIPを選べます。Bのdocs-only通知自体はAの公開を代行しません。新製品CがあればCの成功CI/Artifactが必要です。未完了・失敗・期限切れ・欠落を越えてAへ戻りません。探索は最大100commitで、範囲内に確認できなければ停止します。開始時・履歴解決中・本文切替直前・cleanup時に現在developを再確認します。

公開順序は次のとおりです。

1. 上限内のprivate一時ファイルへZIPのbytesを固定し、hash/size/内部identityを再確認する。
2. 所有marker、Release・固定tag・assetのidentityと権限上の既知条件を確認する。新規はdraftにする。
3. 新hash名のZIPをuploadし、metadataと再downloadのhash/sizeを確認する。
4. develop/Releaseを再照合して本文を切り替え、公開状態・本文・tag・最新ZIPを読戻す。
5. その後だけ、同じearly内で名前/hash/digest/state/sizeが一致する所有旧ZIPを整理する。各削除直前と最後にもdevelop・Release・tag・全asset identityを確認する。

本文切替前の失敗では旧本文と旧ZIPを残します。切替後のcleanup失敗では新しい検証済みZIPと残存旧ZIPを保持し、失敗として記録します。同一製品の再実行は本文のrun/develop_headを変えず、検証と所有残存の整理だけ行います。形式の異なるassetは保全し、所有形式でもidentity不明/不一致なら停止します。正式Releaseや旧branch/RCはこのcleanupに含めません。

結果不明のupload/PATCH/DELETEを盲目的に再試行しません。Release・asset一覧・tagを読戻し、失敗/不明を残して担当へ渡します。読戻し自体が失敗しても成功にしません。固定targetを持つdraftはmainが進んでもそのtargetを保ちます。外部の手動編集・tag移動・branch変更まで原子的にlockできる保証はありません。

## 正式候補RCと同一ZIPの公開

以下は**有効化後に使用する定義**です。#572の実装・検査では実行しません。正式versionは人間が受入前に決めます。例示のversionを採番したり、SNAPSHOT受入後にZIP内versionを書き換えたりしません。

### 1. sourceと正式versionを固定し、一度だけ生成する

候補workflowはレビュー済みmainのtoolingを隔離して使い、cleanな固定sourceを標準root Gradleで `clean test buildPlugin verifyPluginStructure` します。version/source/build引数・設定hash・実JDKをbuild前に `inputs.json` へ固定します。通常はdevelopに包含された候補、main限定候補は[既存の事前計画](main-scoped-release.md)と `scope_issue` を使います。別候補・現行計画不適合・未知JDK/SDKは拒否します。

現行Rabbit/JVM25・class major69・Platform262の内部identity、標準ZIP・同梱JAR・manifest/hash/sizeを確認し、同じZIPへ既存Rabbit Verifierを実施します。製品build設定やpolicyをこの定義で更新しません。main/developや旧Quailの差は各固定sourceの事実として保持します。

公開候補bundleは標準ZIP・`manifest.json`・`inputs.json`・`compatibility-rabbit1.zip`・`bundle.json` の5ファイルです。完走・全class・依存・report hashとZIP hashを既存validatorで照合します。raw logのパス・host・秘密は公開せず、検査済み資料だけをbundle化します。GUI結果は既存verification JSONが正本で、候補Artifactに未観察のpassを書きません。

### 2. 固定候補Artifactを取得し、同じZIPを受け入れる

保存先は `candidate-<ZIPのSHA256>` Actions Artifact、保持は90日です。Release/tagを候補保管に使いません。保持期限はあるため、受入担当は同じbundleを別途保全します。取得は成功した同一repository・main・手動candidate workflowのID/path/run/source、mainへの包含、期限・transport digestと全ファイルhashを照合します。

```bash
# CANDIDATE_RUN / HASH / RCは実在する固定run・承認対象hash・未使用の出力先。
python3 scripts/workflow/release_candidate.py fetch \
  --run "$CANDIDATE_RUN" --sha256 "$HASH" --directory "$RC"
```

GUI担当はこのZIPを使い、host lease・ロードJAR照合と、固定候補の全必要Caseまたはverificationで明示認定された既存証拠を評価します。新しいZIPや新しい方法だけでは、過去の原失敗・未達・実行義務を消しません。全commit/Caseを既存[promotion gate](../verification/README.md#固定候補からmainへ)で確認します。必要QAを保全し、空CaseのためのQAは作りません。

### 3. 保護付きmain promotionの後、同じbytesを正式公開する

正式説明は `docs/releases/<version>.md` の既存release-notes blockへ日本語4節（主な変更・対応環境・インストール・制約・詳細）を用意し、通常PRの独立レビューでmainへ反映します。公開処理は取得済みのmain SHAから説明を読み、欠落・未完成・別version/repositoryのリンク・非対応Markdownを最初の書込み前に拒否します。既存 `validate_notes` が要求する見出しは `## 制約・詳細` です。機械検査は説明内容の正しさを保証しません。

```bash
python3 scripts/workflow/release_delivery.py stable --promotion-pr "$PROMOTION_PR"
```

既存のpromotion validatorで実mergeの2親、mainへの包含、固定candidate/hash、全必要Caseと4必須checkを再確認します。正式tag `v<version>` はpromotion merge、実build sourceはmanifestのcandidateです。candidate Artifactから同じbundleを取得し、一時領域へ固定して再検証し、正式Releaseへ保存します。build/依存再resolve・再圧縮・version変更は行いません。CIのmain通知は該当する一意なpromotionだけを扱い、tooling-only commitでは公開しません。

正式Releaseは同じtag/commit/identity/assets/bytesだけ再照合可能です。異なるbytes・公開済み不完全Releaseを自動修復/置換しません。asset削除やtag移動は実装しません。`make_latest: false` を維持し、古い版の復旧でlatestを巻き戻しません。latestの切替やrepository全体のimmutability設定は追加しません。後者はmutable earlyにも影響するため、有効化とは別に判断が必要です。

Artifact期限切れの場合も、受入で固定した同じbundleがあれば次の定義で再検証できます。CLI自体は現在無効です。

```bash
python3 scripts/workflow/release_delivery.py stable \
  --promotion-pr "$PROMOTION_PR" --directory "$RC" --sha256 "$HASH" --retained-bundle
```

同一bytesのローカル保全は別候補の代用品ではありません。期限切れを再buildで補完しません。

<a id="5-公開済みの説明だけを訂正する"></a>

### 既存の正式説明を訂正する

既存の正式版の説明訂正は `release_candidate.py publish --update-notes` の元RC・identity・全asset照合を保持し、通常再実行で既存説明を無断変更しません。元RCの取得は `fetch --tag` を使います。取得した同じbundle、元hash・promotion PRと、mainでレビュー済みのrelease-notes blockを指定します。変更できるのは説明だけで、tag・candidate/source/version/hash・全assetは同じままです。欠落/異なるbytes・未統合の説明は拒否します。この入口も現在はgateで無効です。

## 排他・権限・復旧

新workflowは全channelを `plugin-release-delivery` groupで直列化し、`queue: max` と `cancel-in-progress: false` を定義します。実行中A、待機中の正式配布Bへearly Cが後着しても、BをCで置き換えず両方を待機させます。待機は最大100runで、上限を超えた追加runは取消しになります。待機を開始した順の処理で、event発生順そのものは保証しません。取消し/失敗を完了扱いにせず、正式版は同じpromotion PRの明示復旧、earlyは元製品CI再実行などから現在HEADを再解決します。これはGitHub native queueの定義であり、現在は全jobが無効のため実動作を試行していません。ローカルCLIはActions lockを共有しません。有効化時に担当1名・実run終了/排他を確認し、workflowと並行実行しない手順を確定します。[GitHubのqueue仕様](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency#example-queueing-multiple-pending-runs)

固定actionlint 1.7.12の[parser](https://github.com/rhysd/actionlint/blob/v1.7.12/parse.go#L756)はこのnative属性を未対応です。既存の固定version/checksumは維持し、[actionlint_check.py](../../scripts/workflow/actionlint_check.py)で当該deliveryの固定concurrency節を厳密に検査します。group・`queue: max`・`cancel-in-progress: false`が揃う1節だけを認め、そのqueue行だけをlint入力上の空行にして残りを[公式stdin入口](https://github.com/rhysd/actionlint/blob/v1.7.12/docs/usage.md#actionlint-command)で検査します。他workflowは原文で全検査し、他の構文・式・未知keyや重複・誤値をignoreしません。repository内のworkflow自体は変更せず、行番号も保持します。固定linterがnative対応した版へ通常更新できた時点でこの限定処理を外します。前回の未対応診断は履歴として保持し、queue定義を有効化scopeへ先送りしません。

publishはレビュー済みmain、buildはwrite tokenのない別runnerです。GitHub Actionsで固定targetと現在default branchのworkflowが異なる場合、標準GITHUB_TOKENでは必要なWorkflows writeを得られないので、最初の書込みと本文/公開切替前に停止します。取得不能やその他の権限エラーも失敗です。この定義はApp/secret/variable名や回避信号を追加しません。認証管理と実際に許された復旧経路は有効化担当が解決し、同じtokenの再実行で権限が増えると扱いません。

失敗時はdraft・旧正常ZIP・新しい検証済みZIP・原失敗を保全します。Release/tag削除や保護回避で成功に見せません。結果不明の書込みは実状態を照合してから担当1名で再開し、同じ入力の再照合と新候補を区別します。実公開URL・source/version/hash/size・run/PRと残条件は既存Issue/PRへ残し、ローカルpath/host/認証情報を公開しません。

## 旧配布・既存証拠の保全

<a id="削除済み作業ブランチの掃除219"></a>

branch別prerelease・毎時巡回/cleanupは切替前の旧接続として残っています。既存branch ZIPの実sourceは従来どおり本文/asset名で識別します。旧RC・正式版・過去QAリンクをearlyへ書き換えず、古いtag/assetの削除・移行は実施しません。旧RCの `fetch --tag` は固定source当時のQuail/Rabbit policy・全資料を読めるまま維持します。

[変更前の配布手順](https://github.com/shinma06/cursor-in-android-studio/blob/66d9735bee4d23fcec2fcb096af1f329ae514cf4/docs/development/plugin-zip-delivery.md)は、旧branch配布/cleanup・旧runtime/bootstrap・初回#127の観察・旧RC保存/取得・既存説明訂正の履歴と、切替前の旧接続を理解する参照先です。現在のNEW経路の有効化指示として実行しません。独自hook・他writer・PAUSED job・未解放claimを保全します。

根拠: [GitHub Release API](https://docs.github.com/en/rest/releases/releases)、[Artifact API](https://docs.github.com/en/rest/actions/artifacts)、[workflow_runの信頼境界](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_run)、[concurrency](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)、[JetBrains標準buildPlugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#build-plugin)。採用元の固定版・採用/適合/既存同等/非適用と、実装・合成検査・live未実施をIssue/PRで区別します。
