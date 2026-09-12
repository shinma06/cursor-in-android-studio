# 運用スクリプトとビルドの境界

#232のsource基準: develop `2e3006f3a52efa7808ac36c19a56d4888550f3d0`。trusted-mainは `400f181da7c1bb9046bb458020fbbda795385bc0`。#223 / PR #226でmain側toolingをdevelopへ同期済み。developの変更が既定branchのscheduleやtrusted-mainへ適用済みとは扱わない。

[arc42の実行・配置・横断観点](https://arc42.org/overview/)を既存入口へ対応させる。新CLI基盤・shared util・別repositoryは作らず、実行入口/判断/外部操作を呼出しで区別する。

## 4つの代表経路

| 経路 / 入口・入力 | 判断・出力 / 副作用の開始点 | 失敗時と実行主体 |
| --- | --- | --- |
| 読取監査 / [governance_audit.py](../../scripts/workflow/governance_audit.py) main。GitHubの全page Issues/PRs/Milestones | GitHub.pagesのGET→latest_audit/status→JSON。完了監査の署名marker/ownerと時刻を検証。importするGitHub/OWNER/REPOはagent_loopから再利用。CLI実行はgh subprocessの読取を伴うがIssue/registry更新なし | API失敗/不正baselineは例外で未確認。人間/PMがHigh Impactを判断する。importだけならghもLoopも呼ばない |
| PR受入/merge / [agent_loop.py](../../scripts/workflow/agent_loop.py) enroll/tick。固定PR/Issue/Case、HEAD/base、停止したwriterのopaque登録 | Loop生成でgit common-dir読取・local storage作成。enrollでprivate registryと公開handoff登録。tickはagent_policy/verification/Change Impactを使い、限定review/fix、選択テスト・通常push・独立承認binding・最新CI/受入→GitHub merge→cleanup | trusted-main coordinatorが1tickずつ所有。変更HEAD/base/受入で承認失効。失敗はbounded retry/blocked、private診断を公開しない。merge済み再開はcleanupへ。writer停止なし/他担当branchを採用しない |
| QA引継ぎ / [qa_handoff.py](../../scripts/workflow/qa_handoff.py) handoff。確認済みdevelop merge、元Issue、固定Case JSON | metadata/validate_change→既存QA marker検索→必要時作成→Milestone/native親子・全Case・main tracking・[qa_document](../../scripts/workflow/qa_document.py)手順書・両端linkのreadback。関数自身は元Issueをcloseしない | coordinatorだけが引継ぎ成功後の最新受入を再確認してclose/cleanup。通信応答消失はmarkerで再利用、重複/closed QA/人間改変/リンク不一致なら元Issueを開いたまま保持。Project終了時照合はPMが別途行う |
| ZIP生成/配布 / [branch-zip.yml](../../.github/workflows/branch-zip.yml)→[branch_zip.py](../../scripts/workflow/branch_zip.py) plan/build/publish。live branch HEAD、実build済みSHA、標準ZIP | planはGitHub GET＋Git差分のChange Impactでmatrixを作る。GITHUB_STEP_SUMMARY指定時のみplan結果をfile追記。buildは固定matrix.shaをcheckoutしGradle ZIP生成/一時artifact転送。publishだけcontents:writeで最新HEAD・digest/size確認後Releaseを更新 | publisherはplanと同じworkflow revision、build sourceと分離。branch移動/削除なら公開をskip。新asset upload→metadata更新→旧asset削除の順。upload失敗は旧ZIPを保持し再試行。tagは配布識別子でsource SHAの証拠ではない。削除branchの残存Release自動cleanupは#219延期中 |

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

ZIPはsource SHAで命名して標準生成物をそのまま転送する。Release本文のsource/asset hashと実際にロードしたJARは別々に照合する。Knowledge skip時の古い正常ZIPを新HEADの成果物へ改名しない。今回の変更はテストと非実行文書/Case JSONのみで、build入力・製品resource・配布経路を変えないため新ZIP生成は不要。#231で生成したZIPを#232の実buildと呼ばない。

## 隔離確認と次回レビュー

repository rootから `python3 -m unittest discover -s scripts/workflow -p 'test_*.py'`。コミットしたIssue差分の標準確認は `python3 scripts/workflow/change_impact.py --base origin/develop --run-tests`。既存loopテストも分類に従う。

| 確認 | 実在テスト / 保証の限界 |
| --- | --- |
| import | test_governance_auditの `test_four_entry_imports_have_no_external_side_effects`。空の一時cwd・moduleごとのfresh Python・`-B`でimportし、Python audit hookで書込open/FS変更/network/process起動を拒否。推移importも通る。通常CPython APIでの回帰検出で、悪意あるnativeコードのsandboxではない。importの外で明示実行するCLI全体が無副作用という意味ではない |
| 読取監査 | `test_cli_uses_read_only_paginated_endpoints_and_propagates_failure`、baseline/閾値/外部owner等。fake APIで全page読取と失敗伝播を確認 |
| 受入/merge | test_agent_loopの `test_review_fix_rereview_merge_issue_cleanup`、`test_base_moves_before_merge_clears_old_approval`、`test_cleanup_failure_is_retried_without_merge_or_review` 等。fake GitHub/workerと一時Git repoで本番mergeなし |
| QA引継ぎ | test_qa_handoffの `test_success_and_retry_reuse_full_snapshot`、`test_failed_link_never_closes_or_cleans_up`、`test_changed_acceptance_during_transfer_never_closes` 等。fake外部更新、実payload/readback判断を試す |
| ZIP / 分類 | test_branch_zipの `test_failed_upload_or_metadata_keeps_previous_zip_and_retries`、`test_head_moves_during_upload_preserves_previous_metadata`、`test_runtime_build_and_unknown_require_zip_but_tests_and_tooling_do_not`。test_change_impact/test_pre_pushはrename/delete/mode/mixed/unknownと一時Gitの実hookも検証。実GitHub upload試験ではない |

新しい入口・import・依存・分類path・生成入力を変更するwriterと独立reviewerが、4経路の影響行、副作用の開始点、trusted revision、失敗時保持、classifierと実消費側を同じPRで確認する。既存[Governance Audit](git-governance-audit.md)からこの境界へ辿れる。新CI gate/field/監視は追加しない。今回の合成回帰成功から本番の速度/事故率改善は断定しない。
