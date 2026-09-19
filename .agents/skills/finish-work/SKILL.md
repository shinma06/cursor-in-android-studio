---
name: finish-work
description: Validate an Issue PR and hand off integration, QA, and owned-resource cleanup against its acceptance criteria.
---

# Finish work

[GitHub workflow](../../../docs/development/github-workflow.md)の検証/進行判断と、引継ぎ時の[PR automation](../../../docs/development/pr-automation.md)を使う。独立レビューだけの依頼では固定差分と受入の判定を返し、writer/coordinatorの操作を代行しない。

**検証可能な成果:** 固定Issue差分へ`python3 scripts/workflow/change_impact.py --run-tests`を実行し、選択された検証/skip理由を記録する。明示packaging/GUI用buildは`buildPlugin`も行う。既に成功した同じ差分の検証を根拠なく増やさず、変更・失敗・未解決懸念があれば該当確認を追加する。必須CIやhookを省略せず、影響する失敗を修正して所有branchだけをpushする。

**独立判定:** 別sessionでHEAD/baseとIssue受入を照合し、scope_complete/issue_complete/GUI要否を分ける。未解決コード指摘・必要テスト失敗は両targetを止める。追加Sessionは[Codex実行規約](../../../docs/development/codex-execution-policy.md)に従う。Mission/ACP Firstの設計方針を実装/live成功と混同しない。

**引継ぎ:** writer停止・clean後にtrusted-mainのv2 enrollへ渡す。以後の修正/再レビュー/4必須check/guarded mergeはcoordinator所有。PMは実行が起きたかを確認し、停止中なら次担当・具体的阻害条件を記録する。登録済みを自動進行済みとせず、既存owner/sourceやPAUSED heartbeatは変えない。#83 bootstrapはその専用手順だけに限定する。

**統合と終了:** developは全Case追跡があればGUI pending/blocked/failを保持してsquash統合可（製品failは修正Issueへ）。QAへ全Case/main追跡を冪等に移し、双方向link/readback後に受入完了の元実装Issueをcloseする。main promotionは[固定候補全体](../../../docs/verification/README.md)の全commit/全必要Caseが同一識別buildでpassした後にmerge commit。許可されたpromotion JSON以外の変更や過去の部分passで代替しない。子PRだけで親tracking/research/QAをcloseしない。

**残務:** [Work Management](../../../docs/development/work-management.md)に従い元Issue/QAのProject・Milestone・native関係と人間向け手順をreadback。実親だけを`--parent`で指定する。所有する停止済みclean資源をcleanupしremote/local/tracking ref・worktreeの消失、または残る理由/owner/再開条件を照合する。main/master/developは削除せず、GUI lease・未保存作業・既存ownerを保全する。merge成功だけで終了しない。
