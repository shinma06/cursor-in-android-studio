# CIの入力・必要検査・配布を分ける

対象の実行系はroot GradleのAndroid Studioプラグインです。ARBのPython CLI互換検査・`plugin/`経路・完了markerは適用しません。現在のCIはPR候補、main/develop統合後、明示された手動全量を検査し、Issue branchのpushだけでは起動しません。この既存event境界を維持します。

[共通classifier](../../scripts/workflow/change_impact.py)をhook/CI/coordinator/ZIP判定で共用し、Issue全差分・rename/delete/mode・未知/履歴不足を安全側に扱います。`test` / `PR policy` / `Acceptance gate` / `Agent review`は維持し、workflow全体のpath filterや偽のskip-successを追加しません。PRのmerge候補とbranch HEADは異なる入力です。取消しは同じPR/refだけ、最新版の結果を確認し、失敗/取消しを隠しません。

有効なpre-push hookが同じclean committed HEAD・入力・環境・目的の必須suiteを実行する場合、直前に同じ手動suiteを重ねません。hookの有効性を確認し、実行後の結果を残します。dirty中の早期feedback、新変更、外部条件/検証目的の違い、hook未装備には適用しません。結果cacheやreceipt skipを導入する規則ではなく、必須hook/CIは実行します。

試験の必要性と配布物の生成/保存は別です。現classifierはtooling-onlyではPython suite、test-onlyではGradle testを選び、配布ZIPを作りません。build/runtime/unknownや明示パッケージは標準build/構造検査と正しい固定SHAのZIPを必要とします。実archiveが試験入力ならその生成は残し、配布・公開と混同しません。秘密を含まないread-only CI、trusted-baseの受入/policy、固定artifactの同一性と失敗伝播を維持します。

CIの起動・選択・runner/cache・toolchain・build/test command・fixture・artifact入力を変える場合、writerは影響する回数/待ち/検査/保存と守る保証を既存Issue/PRへ示します。実行影響なし、同条件の既存証拠再利用、有限比較が必要のいずれかを理由付きで選び、必要な通常検査/実結果・失敗/取消し・未測定・復旧ownerを残します。独立reviewerは入力・保証・証拠内容と限界を確認します。新入力/環境や実遅延・失敗のときだけ関係する判断を更新し、毎変更の全matrix、追加benchmark、常駐job、別台帳は作りません。

run/attempt/head/event、job/stepの実時刻と結論を区別します。queueを含む待ち、jobの実行時間、CPU、人の負担、実請求は別です。取得できない値は未測定とし、秒数を課金額や一般的な改善率へ換算しません。退行は所有branchで通常revertし、正常/失敗入力を隔離して必要検査と独立レビューへ戻します。

この運用変更の正常系/故障系CLI回帰は#571で検証します。新運用を実案件で試すこと、配布jobの有効化や既存成果物/QAの移行は別scopeで、効果は未実測です。
