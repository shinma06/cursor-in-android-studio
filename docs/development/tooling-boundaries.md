# 運用スクリプトとビルドの境界

#249の同期基準: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`、main `041a58255f2f432b290bb6956333c064677d803e`。mainへ導入済みの配布cleanup（#219 / PR #248）とpromotion履歴照合（#250 / PR #253）を含む。#232の境界整理と前回同期#223 / PR #226は履歴として保持する。developへの反映と既定branchのschedule・trusted-mainでの実行を区別する。

[arc42の実行・配置・横断観点](https://arc42.org/overview/)を既存入口へ対応させる。新CLI基盤・shared util・別repositoryは作らず、実行入口/判断/外部操作を呼出しで区別する。

## 4つの入口と代表経路

| 経路 / 入口・入力 | 判断・出力 / 副作用の開始点 | 失敗時と実行主体 |
| --- | --- | --- |
| 読取監査 / [governance_audit.py](../../scripts/workflow/governance_audit.py) main。GitHubの全page Issues/PRs/Milestones | GitHub.pagesのGET→latest_audit/status→JSON。完了監査の識別marker・Issue投稿ownerと時刻を検証。importするGitHub/OWNER/REPOはagent_loopから再利用。CLI実行はgh subprocessの読取を伴うがIssue/registry更新なし | API失敗/不正baselineは例外で未確認。人間/PMがHigh Impactを判断する。importだけならghもLoopも呼ばない |
| PR受入/merge / [agent_loop.py](../../scripts/workflow/agent_loop.py) enroll/tick。固定PR/Issue/Case、HEAD/base、停止したwriterのopaque登録 | Loop生成でgit common-dir読取・local storage作成。enrollでprivate registryと公開handoff登録。tickはagent_policy/verification/Change Impactを使い、限定review/fix、選択テスト・通常push・独立承認binding・最新CI/受入→GitHub merge→cleanup | trusted-main coordinatorが1tickずつ所有。変更HEAD/base/受入で承認失効。失敗はbounded retry/blocked、private診断を公開しない。merge済み再開はcleanupへ。writer停止なし/他担当branchを採用しない |
| QA引継ぎ / [qa_handoff.py](../../scripts/workflow/qa_handoff.py) handoff。確認済みdevelop merge、元Issue、固定Case JSON | metadata/validate_change→既存QA marker検索→必要時作成→Milestone/native親子・全Case・main tracking・[qa_document](../../scripts/workflow/qa_document.py)手順書・両端linkのreadback。関数自身は元Issueをcloseしない | coordinatorだけが引継ぎ成功後の最新受入を再確認してclose/cleanup。通信応答消失はmarkerで再利用、重複/closed QA/人間改変/リンク不一致なら元Issueを開いたまま保持。Project終了時照合はPMが別途行う |
| ZIP生成/配布 / [branch-zip.yml](../../.github/workflows/branch-zip.yml)→[branch_zip.py](../../scripts/workflow/branch_zip.py) plan/build/publish。live branch HEAD、実build済みSHA、標準ZIP | planはGitHub GET＋Git差分のChange Impactでmatrixを作る。GITHUB_STEP_SUMMARY指定時のみplan結果をfile追記。buildは固定matrix.shaをcheckoutしGradle ZIP生成/一時artifact転送。publishだけcontents:writeで最新HEADを確認。新uploadは応答のdigest/sizeを照合し、同SHAの既存uploaded・size>0 assetは再照合せず再利用してReleaseを更新 | publisherはplanと同じworkflow revision、build sourceと分離。branch移動/削除なら公開をskip。新asset upload→metadata更新→旧asset削除の順。upload失敗は旧ZIPを保持し再試行。tagは配布識別子でsource SHAの証拠ではない。削除済み作業branchの配布物は次のcleanup経路で扱う |
| 削除branchの配布cleanup / 同じ[branch-zip.yml](../../.github/workflows/branch-zip.yml)→[branch_zip.py](../../scripts/workflow/branch_zip.py) cleanup-plan/cleanup。既定branchのschedule/manual、branch名・Release ID・ソースSHA | cleanup-planは全branch/Release/refをGETして候補と保留理由を出す。cleanupだけcontents:write。publishと同じtagのconcurrency group取得後に再plan・識別・生存branchを再確認し、receiptを出してRelease/Assets→変更のないtagを削除、両方の不存在をreadback | 毎時scheduleは実行、manualはcleanup=falseならdry-run、trueなら実行。pushは削除しない。main/master/develop/default・生存branch、識別不能/draft/immutable/タグ単独/タグ欠落を保護。API失敗を不存在扱いせず、途中失敗・再作成・readback不一致は下記の保持/再確認へ |

GitHub公開状態はIssue/PR/Case/Release。host/source・privateログ・worker PIDはlocal registry側で、公開handoffはopaque ID。既存enrollment/owner/PAUSED heartbeatをこの整理で変更しない。Astraが主担当なら子Agent生成は禁止で、既存独立top-level sessionのレビューなど[実行規約](codex-execution-policy.md)内の手段を使う。`run_worker`をimportできることは実行許可ではない。

## 変更前 → 目標 → 適用配置

| 既存配置 | 目標と実際 / 維持理由 |
| --- | --- |
| `scripts/workflow/{governance_audit,agent_loop,qa_handoff,branch_zip}.py` | 4入口を維持。監査は既存GitHubを再利用し、トップレベルは定義/定数のみ。ROOTのpath解決とHOSTのローカルhostname読取はあるがネットワーク接続/registry初期化なし。不要な副作用が再現しないので専用clientへ分割しない |
| `agent_policy.py` / `verification.py` / `issue_schema.py` / `change_impact.py` | 判断の既存正本を維持。知識文書に新しいpath allowlistやmerge判定を実装しない |
| `handoff_registry.py` / `agent_worker.py` / `qa_document.py` | 副作用は明示呼出し時。登録・worker・手順書の責務とprivate/public境界を維持 |
| 同directoryの `test_*.py` | 4経路の既存fake/temp repoテストを再利用。test_governance_auditへimport隔離チェック1件を追加 |
| `.githooks/` / `.github/workflows/` / Gradle | 呼出し位置と既存分類を維持。import/package/resource/test検出設定の移動なし |

ファイル移動はなし。副作用のないimportを小さい回帰チェックで維持する方が、client/registry分割や新依存を追加するより直接的である。#227の全体入口/#228の正本表へ接続し、#229/#230の製品境界には触れない。

## 実行分類から成果物まで

分類・必要テスト/ZIP判断は [Change Impact](change-impact.md) と [実装](../../scripts/workflow/change_impact.py)が正本。runtime/resource、build、test、tooling、knowledge、metadata、unknownを集合で扱い、unknown/不完全履歴は全検証へ倒す。renameの旧/新path・delete・mode変更も差分入力に含む。知識＋製品を文書だけとしてskipしない。

pre-push、CI、coordinatorの検証、branch ZIP planは同じclassifierを呼ぶ。`--run-tests`は選択テストを実行し、plugin_zip要否の出力それ自体はZIP生成ではない。製品/build変更なら標準 `./gradlew buildPlugin` または既存ZIP workflowで生成する。手動GUI/buildの明示要求はGit差分skipの対象ではない。

ローカルはgradle.propertiesのplatformPathで実Android Studio SDKを指定する。CI/ZIP runnerはworkflowに固定されたversion/codenameを取得/cacheし、`-PplatformPath`として同じGradle local()経路へ渡す。Kotlin/JDKやSDKの新仕様をこの文書で推定せず、実build/公式release資料の照合は既存[現行実装](../architecture/current-implementation.md)と配布手順から行う。

ZIPはsource SHAで命名して標準生成物をそのまま転送する。Release本文はsource SHA・asset名・markerを記録し、hashは含まない。新upload応答のasset API digest/size照合と、同SHA既存assetの再利用（内容の再照合なし）、実際にロードしたJARの確認は別である。Knowledge skip時の古い正常ZIPを新HEADの成果物へ改名しない。#249は既存toolingの同期と文書/Case JSON更新で、製品/build入力は変えない。固定Issue差分の共通分類に従い、新ZIP生成の要否を判断する。過去の#231 ZIPや#232のimport試験を別候補の実build/GUI合格と呼ばない。

## cleanupの権限と失敗時保持

管理対象はbranch名から算出した完全tag、単一marker、配布タイトル、mutable prerelease、単一の正常ZIPを照合する。古さやprefixだけで削除せず、識別不能/draft/tag-onlyは保持する。実行元は既定branchのworkflow revisionで、build対象branchの任意コードへ削除権限を渡さない。ローカルのcleanup CLIをpublishと並行実行しない。

削除前にRelease ID・branch・ソースSHA・tag objectのreceiptをJob summaryへ残す。Release削除失敗は次回planで候補を再検証できる。Release削除後のtag削除失敗はtag-onlyとして保留し、PMがそのrunのreceiptと新鮮なbranch/refを照合して次操作を判断する。最終readback不一致は成功に変えず、receiptを保存したままdry-runと実APIを再確認する。

初回実行の[run 34684252525](https://github.com/shinma06/cursor-in-android-studio/actions/runs/34684252525)では6jobが削除直後のreadback不一致で失敗した。receiptと新鮮なAPIを照合して対象35 Release/Assets/tagの不存在・main/develop保持を確認し、失敗jobだけのattempt2は成功した。原因をAPI反映遅延と断定せず、初回失敗と再確認を履歴として残す。[#219の実確認記録](https://github.com/shinma06/cursor-in-android-studio/issues/219#issuecomment-5644876636)

publishとの共通tag排他はActions内の公開/cleanupを直列化する。人間や外部処理によるbranch/Release再作成までlockする保証はない。観測した再作成・tag変更は保留し、生存branchは通常plan/publishで配布を復旧する。設定・既存enrollment/owner・PAUSED heartbeatはこの同期から変更しない。詳しい手順は[配布cleanup](plugin-zip-delivery.md#削除済み作業ブランチの掃除219)を参照する。

## 隔離確認と次回レビュー

repository rootから `python3 -m unittest discover -s scripts/workflow -p 'test_*.py'`。コミットしたIssue差分の標準確認は `python3 scripts/workflow/change_impact.py --base origin/develop --run-tests`。既存loopテストも分類に従う。

| 確認 | 実在テスト / 保証の限界 |
| --- | --- |
| import | test_governance_auditの `test_four_entry_imports_have_no_external_side_effects`。空の一時cwd・moduleごとのfresh Python・`-B`でimportし、Python audit hookで書込open/FS変更/network/process起動を拒否。推移importも通る。通常CPython APIでの回帰検出で、悪意あるnativeコードのsandboxではない。importの外で明示実行するCLI全体が無副作用という意味ではない |
| 読取監査 | `test_cli_uses_read_only_paginated_endpoints_and_propagates_failure`、baseline/閾値/外部owner等。fake APIで全page読取と失敗伝播を確認 |
| 受入/merge | test_agent_loopの `test_review_fix_rereview_merge_issue_cleanup`、`test_base_moves_before_merge_clears_old_approval`、`test_cleanup_failure_is_retried_without_merge_or_review` 等。fake GitHub/workerと一時Git repoで本番mergeなし |
| QA引継ぎ | test_qa_handoffの `test_success_and_retry_reuse_full_snapshot`、`test_failed_link_never_closes_or_cleans_up`、`test_changed_acceptance_during_transfer_never_closes` 等。fake外部更新、実payload/readback判断を試す |
| ZIP / 分類 | test_branch_zipの `test_failed_upload_or_metadata_keeps_previous_zip_and_retries`、`test_head_moves_during_upload_preserves_previous_metadata`、`test_runtime_build_and_unknown_require_zip_but_tests_and_tooling_do_not`。test_change_impact/test_pre_pushはrename/delete/mode/mixed/unknownと一時Gitの実hookも検証。実GitHub upload試験ではない |
| 配布cleanup | test_branch_zipの `test_cleanup_protects_identity_failures_and_integration_branches`、`test_cleanup_rechecks_candidate_id_sha_and_branch_after_lock`、`test_cleanup_partial_failure_retries_release_but_holds_tag_only`、`test_workflow_cleanup_is_default_branch_only_and_shares_publish_lock`。fake APIで識別・権限/排他・失敗時保持を確認。実削除の結果は#219のreceipt/readbackを別証拠として扱う |

新しい入口・import・依存・分類path・生成入力を変更するwriterと独立reviewerが、対象経路の影響行、副作用の開始点、trusted revision、失敗時保持、classifierと実消費側を同じPRで確認する。既存[Governance Audit](git-governance-audit.md)からこの境界へ辿れる。新CI gate/field/監視は追加しない。今回の合成回帰成功から本番の速度/事故率改善は断定しない。
