# Plugin ZIPの配布

<a id="現在の状態と非有効化の境界"></a>

## 現在の状態と切替条件

[#572](https://github.com/shinma06/cursor-in-android-studio/issues/572)で採用した配布定義を、[#599](https://github.com/shinma06/cursor-in-android-studio/issues/599)で **developの単一early → 固定正式候補Artifact → 受入・main promotion → 同じbytesの正式Release** のguard付きreadinessへ接続します。現在は切替準備段階で、初回earlyの実公開・実workflow切替は未了です。以下は採用するsourceの動作と実切替の条件です。設定・run・公開source/hashの実結果は#599で確認し、source採用だけを稼働成功と扱いません。製品source/build設定・既存Case・原失敗・main/developの機能差は変更しません。

| 入口 | 採用するsourceのreadinessと境界 |
| --- | --- |
| `delivery.yml` | main参照のCI完了通知をdevelop early / main promotedへ、手動通知をstableへ渡す。未知event/branchはCLI呼出し前に拒否。既存のsource/Case/check/bytes/権限検査を維持 |
| `release-candidate.yml` | main参照の手動入口のみ。repository権限はread-only、標準buildと候補Artifact保存を定義する。Release/tag公開は行わない |
| `release_delivery.py` / `release_candidate.py publish` | `ENABLED = True`で既存guardを通る入口を準備する。正式versionの人間による受入前決定と具体的な公開scopeは作業記録へ残す。構文・Case検査の成功だけではその根拠にならない |
| 新しいRC Release保存 | `release_candidate.py store`は書込み前に拒否する。保存済みRCは読取り可能なまま保全 |
| 既存`branch-zip.yml` / `branch_zip.py` | 旧sourceと全既存資産/URLを保全し、NEW CLIへ差し替えない。実切替時にworkflowを停止し、既存runをdrainする。disableは実行中runのcancelではない |
| 既存CI | テスト・標準ZIP・manifest・互換性検査とArtifactを従来どおり扱う。構文検査だけ限定queue互換処理を通す。公開やNEW経路のdispatchは追加しない |

実切替はPMが最終main/developと他のproduct push停止を固定してから、担当1名で次の順序を満たします。現在はこの操作を開始していません。

1. 全関連namespace・所有claim・OLD/NEWの実workflow状態と実行中/待機run・遅着し得るmain CI/manual stable・候補入力を有限に照合する。未割当の正式公開へ到達し得る入力、所有不明や結果不明があれば、その操作を止めて解消する。通常main通知のno-opや未merge PRの拒否例だけで全入力を安全としない。
2. NEWを停止保持し、OLD workflowを全branchに対して停止、実行済み/待機中runを所有を保ってdrainする。全guarded readinessをmainと必要なdevelop同期へ通常レビューで採用してから、初回earlyの固定tagを作る。earlyだけを先に採用して後からworkflowを変えない。
3. 正当な最終develop CIがまだ実行中で、enable後のcompletionと排他を記録できる場合はその自然な通知を使う。過去completionの再送は仮定せず、dummy変更・rerun・新dispatchを作らない。成立しない場合はNEW停止/drain・単一operator・固定run/source/同一bytesを確認し、既存local early CLIを使って実公開をreadbackした後にNEWをenableする。
4. 取得したCIのZIPを再build/再圧縮せず、source/run/hash/size、固定tag、本文、全asset、再downloadのbytesと公開URLを読み戻す。初回early1回を実観察し、正式version選択・候補実生成・正式公開は今回行わない。全readinessは将来の個別公開scopeや人間のversion決定を代行しない。

実writeの結果不明は既存の限定readbackで確認し、盲目的に再試行しません。旧資産/URL・元FAIL・他owner・PAUSEDを保持します。workflow/tokenの既存権限guardを解除せず、同じtokenの再実行・環境変数・新secretを回避策にしません。

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

以下は**個別のversion・候補生成・公開scopeを確定した後に使う手順**です。#599ではreadinessだけを準備し、実行しません。正式versionは人間が受入前に決め、その判断と公開scopeを作業記録へ残します。例示のversionを採番したり、SNAPSHOT受入後にZIP内versionを書き換えたりしません。

### 1. sourceと正式versionを固定し、一度だけ生成する

候補workflowはレビュー済みmainのtoolingを隔離して使い、cleanな固定sourceを標準root Gradleで `clean test buildPlugin verifyPluginStructure` します。version/source/build引数・設定hash・実JDKをbuild前に `inputs.json` へ固定します。通常はdevelopに包含された候補、main限定候補は[既存の事前計画](main-scoped-release.md)と `scope_issue` を使います。別候補・現行計画不適合・未知JDK/SDKは拒否します。

現行Rabbit/JVM25・class major69・Platform262の内部identity、標準ZIP・同梱JAR・manifest/hash/sizeを確認し、同じZIPへ既存Rabbit Verifierを実施します。製品build設定やpolicyをこの定義で更新しません。main/developや旧Quailの差は各固定sourceの事実として保持します。

公開候補bundleは標準ZIP・`manifest.json`・`inputs.json`・`compatibility-rabbit1.zip`・`bundle.json` の5ファイルです。完走・全class・依存・report hashとZIP hashを既存validatorで照合します。raw logのパス・host・秘密は公開せず、検査済み資料だけをbundle化します。GUI結果は既存verification JSONが正本で、候補Artifactに未観察のpassを書きません。

### 2. 固定候補Artifactを取得し、同じZIPを受け入れる

保存先は `candidate-<ZIPのSHA256>` Actions Artifact、保持は90日です。Release/tagを候補保管に使いません。保持期限はあるため、受入担当は同じbundleを別途保全します。取得は成功した同一repository・main・手動candidate workflowのID/path/run/source、mainへの包含、期限・transport digestと全ファイルhashを照合します。ここでmainへ包含されるworkflow sourceと、manifestに固定した未mergeのdevelop/承認済みmain限定候補sourceは別です。取得処理は後者の40桁SHAをoriginから取得してから、trusted inspectorで同じsourceのpolicy/build-inputとbundleを検査します。source側の実行コードをimportせず、offline validator自体にはfetchを追加しません。保全bundleの正式公開では、先に取得する実promotion mergeの履歴を使い、既存のscope/受入・同一ZIP条件を再確認します。

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

Artifact期限切れの場合も、受入で固定した同じbundleがあれば次の定義で再検証できます。実行には元のpromotionと具体的な復旧scopeが必要で、#599の初回early試行では使いません。

```bash
python3 scripts/workflow/release_delivery.py stable \
  --promotion-pr "$PROMOTION_PR" --directory "$RC" --sha256 "$HASH" --retained-bundle
```

同一bytesのローカル保全は別候補の代用品ではありません。期限切れを再buildで補完しません。

<a id="5-公開済みの説明だけを訂正する"></a>

### 既存の正式説明を訂正する

既存の正式版の説明訂正は `release_candidate.py publish --update-notes` を使い、通常再実行で既存説明を無断変更しません。公開時に受け入れた同じbundle・元hash・promotion PRと、mainでレビュー済みのrelease-notes blockを照合します。変更できるのは説明だけで、tag・candidate/source/version/hash・全assetは同じままです。欠落/異なるbytes・未統合の説明は拒否します。この入口のreadinessは説明訂正の許可ではなく、#599の初回early試行では使いません。

旧RCを使った正式版は元RCを `fetch --tag` で取得します。NEWのcandidate Artifactが現存する場合は、その固定runを `fetch --run` で指定します。`fetch` は `--tag` / `--run` のどちらか一方だけが必須です。次の取得例は該当する一方だけを使います。

```bash
# 旧RC: 元のRC tag・受入済みZIP hash・未使用の出力先を指定する。
python3 scripts/workflow/release_candidate.py fetch \
  --tag "$RC_TAG" --sha256 "$HASH" --directory "$RC"
# NEW: 同じ受入済み候補の実在する固定run・hash・未使用の出力先を指定する。
python3 scripts/workflow/release_candidate.py fetch \
  --run "$CANDIDATE_RUN" --sha256 "$HASH" --directory "$RC"
```

取得済みbundle、またはArtifact期限切れ前に保全した同じ受入済みbundleがある場合は、既存directoryを直接再検証します。次の指定では新たなfetchを要求せず、期限切れを再build・再圧縮・別候補で補完しません。元のpromotion PRと正確なZIP hashが必要です。

```bash
python3 scripts/workflow/release_candidate.py publish \
  --update-notes --retained-bundle --directory "$RC" --sha256 "$HASH" \
  --promotion-pr "$PROMOTION_PR"
```

## 排他・権限・復旧

新workflowは全channelを `plugin-release-delivery` groupで直列化し、`queue: max` と `cancel-in-progress: false` を定義します。実行中A、待機中の正式配布Bへearly Cが後着しても、BをCで置き換えず両方を待機させます。待機は最大100runで、上限を超えた追加runは取消しになります。待機を開始した順の処理で、event発生順そのものは保証しません。取消し/失敗を完了扱いにせず、正式版は同じpromotion PRの明示復旧、earlyは元製品CI再実行などから現在HEADを再解決します。これはGitHub native queueの定義であり、readinessの合成検査とliveの実動作を区別します。切替準備時点ではlive未試行です。ローカルCLIはActions lockを共有しないため、上記の切替時に担当1名・実run終了/排他を確認し、workflowと並行実行しません。[GitHubのqueue仕様](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency#example-queueing-multiple-pending-runs)

固定actionlint 1.7.12の[parser](https://github.com/rhysd/actionlint/blob/v1.7.12/parse.go#L756)はこのnative属性を未対応です。既存の固定version/checksumは維持し、[actionlint_check.py](../../scripts/workflow/actionlint_check.py)で当該deliveryの固定concurrency節を厳密に検査します。group・`queue: max`・`cancel-in-progress: false`が揃う1節だけを認め、そのqueue行だけをlint入力上の空行にして残りを[公式stdin入口](https://github.com/rhysd/actionlint/blob/v1.7.12/docs/usage.md#actionlint-command)で検査します。他workflowは原文で全検査し、他の構文・式・未知keyや重複・誤値をignoreしません。repository内のworkflow自体は変更せず、行番号も保持します。固定linterがnative対応した版へ通常更新できた時点でこの限定処理を外します。前回の未対応診断は履歴として保持し、queue定義を有効化scopeへ先送りしません。

publishはレビュー済みmain、buildはwrite tokenのない別runnerです。GitHub Actionsで固定targetと現在default branchのworkflowが異なる場合、標準GITHUB_TOKENでは必要なWorkflows writeを得られないので、最初の書込みと本文/公開切替前に停止します。取得不能やその他の権限エラーも失敗です。この定義はApp/secret/variable名や回避信号を追加しません。認証管理と実際に許された復旧経路は有効化担当が解決し、同じtokenの再実行で権限が増えると扱いません。

失敗時はdraft・旧正常ZIP・新しい検証済みZIP・原失敗を保全します。Release/tag削除や保護回避で成功に見せません。結果不明の書込みは実状態を照合してから担当1名で再開し、同じ入力の再照合と新候補を区別します。実公開URL・source/version/hash/size・run/PRと残条件は既存Issue/PRへ残し、ローカルpath/host/認証情報を公開しません。

## 旧配布・既存証拠の保全

旧publisherの重複選択はplan/publishで共通です。同tagに公開済み1件があればそれを選び、同tagのdraftと既存asset/URLは変更しません。公開済みが複数なら停止します。planは本文markerが指す一意のuploaded ZIPを比較元に使い、保全assetが複数あることだけで再buildしません。

全件draftの場合は最新IDの候補を再利用します。集合全体のmarker・branch/source・名前・prerelease・mutableを検証し、古いdraftが空で、新ZIPが公開されてdigest/size/sourceを再読確認でき、同じHEADが維持される場合だけ、その呼出しで照合した古いdraftを削除します。所有不明・immutable・保護資産を持つdraftは書込み前に停止します。upload/PATCH失敗・HEAD移動・再読不一致では先行削除しません。後処理が途中失敗した後は既存の公開済み＋draftとして保全し、元失敗と残る退役を担当者へ引き継ぎます。再試行だけを理由にmixedのdraftを削除しません。 全件draftのPATCH失敗後も、同一source名の完了済みassetは有効なdigest/sizeを取得して再利用します。再buildのタイムスタンプ等でローカルZIPのbytesが変わっても既存uploadを置き換えず、公開後は既存assetのdigest/sizeで読み戻します。不完全・未知identityのassetは保全して停止します。 選択したdraftが1件でも、公開対象は空／正当な同SHA完了ZIP／所有済み空starterに限ります。全assetを専用一覧で取得し、公開直前に対象Releaseの所有・公開状態とasset全件・upload identity・HEADを再照合するため、公開直前の読み戻しで未知assetの並行追加を検出したdraftは公開せず停止します。GitHubの外部手動編集まで原子的にlockする保証ではありません。

通常退役は開始時に公開済みsingle Releaseの正規前版が1 assetだけの場合に限定し、新ZIPの公開・再読後、その固定前版だけを照合して整理します。開始時から履歴/未知assetが複数ある場合は、draftが後から見えなくなってもそれらを保持します。all-draftでは他名assetを整理しません。既存orphan cleanupの単一asset契約は維持します。 通常の前版退役に限って、新uploadの既存`label`へ退役可能な各前版identityのhashを記録します。PATCH失敗や後処理失敗の再試行は、この持続的な根拠が現在の前版と一致する場合だけ退役でき、個数だけで保護履歴を通常前版と推定しません。本文切替後の前版退役が失敗した場合も、その根拠が一致して残っていればplanが未完了の通常retryを隠しません。PATCH失敗後に次の製品SHAへ進んでも、正常経路から証明できる未完了前版だけの根拠を引き継ぎ、検証・公開後の各DELETEと再読で部分失敗から再開します。保護履歴・未知assetには根拠を発行しません。mixedで既存の通常退役根拠が残っていた場合は同じReleaseの過去ZIPも含むlabelから根拠を解除して公開し、後からdraftが消えても保護履歴を退役しません。labelはGitHub上の表示にも使われる既存metadataであり、根拠がない/変わった場合は保全・照合へ戻します。同source markerを持つdraftの空starterだけは既存の部分upload再試行を許可し、未知の部分資産は停止します。mixedに残した履歴assetは単一asset用のorphan cleanup対象にしません。保管コピーの存在は元URL削除の許可ではありません。この工具修正のdevelop採用は、default mainを使う毎時経路への到達や、#599のNEW有効化・#579の元FAIL解消の実観察を意味しません。通常main-tooling適用と実到達のreadbackは別途必要です。

<a id="削除済み作業ブランチの掃除219"></a>

branch別prerelease・毎時巡回/cleanupのsourceは旧接続として保全し、実workflowの停止は上記の切替手順で照合します。既存branch ZIPの実sourceは従来どおり本文/asset名で識別します。旧RC・正式版・過去QAリンクをearlyへ書き換えず、古いtag/assetの削除・移行は実施しません。旧RCの `fetch --tag` は固定source当時のQuail/Rabbit policy・全資料を読めるまま維持します。

[変更前の配布手順](https://github.com/shinma06/cursor-in-android-studio/blob/66d9735bee4d23fcec2fcb096af1f329ae514cf4/docs/development/plugin-zip-delivery.md)は、旧branch配布/cleanup・旧runtime/bootstrap・初回#127の観察・旧RC保存/取得・既存説明訂正の履歴と、切替前の旧接続を理解する参照先です。現在のNEW経路の有効化指示として実行しません。独自hook・他writer・PAUSED job・未解放claimを保全します。

根拠: [GitHub Release API](https://docs.github.com/en/rest/releases/releases)、[Artifact API](https://docs.github.com/en/rest/actions/artifacts)、[workflow_runの信頼境界](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_run)、[concurrency](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)、[JetBrains標準buildPlugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#build-plugin)。採用元の固定版・採用/適合/既存同等/非適用と、実装・合成検査・live未実施をIssue/PRで区別します。
