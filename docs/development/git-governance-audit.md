# Git Governance Audit

2026-09-11 / #195。Git管理体系が現在の[Mission](../project-mission.md)と開発に適しているかを点検する。既存ルールに従うこと自体を目的にせず、Agentが長期に一貫して運用できる最小限かつ十分な体系を保つ。問題がなければ変更しない。

## 実施するタイミング

| 種類 | 条件と扱い |
| --- | --- |
| Hard | 主要Milestone完了、High Impact Change、Git管理方式の大幅変更、STRUCTURAL PROBLEMの兆候では実施する。 |
| Count | 前回監査後のmeaningful mergeが10件で実施する。PRとそのbranchを二重に数えない。 |
| Soft | Issue急増、stale Issue/branch増加、Gitルールやfield追加、Agentのルール選択の迷い・例外頻発では必要性を評価する。ルール変更時の重複・矛盾確認は省略しない。 |

High Impactは、architecture、技術stack、CLI→ACP等のintegration、目的/scope、主要data model/subsystem、plugin構造、workflow/branch/Issue/Project/Milestone/release戦略、CI/CDの大幅変更を含む。「既存IssueやProject Contextの前提が古くなるか」がYESなら該当する。merge数を理由に延期しない。

主要Milestone完了時は、完了/残存Issue・次Milestone・Project・branch・roadmap・ルールを一緒に確認する。現在の6つの到達目標は主要Milestoneとして扱う。新規Milestoneの重要度は到達条件で判断する。

開始時と統合・終了の区切りで、既存のIssue/PR確認に次の読み取りを組み込む。同じ未変更snapshotを繰り返し取得せず、毎PRで全監査を行わない。

```bash
python3 scripts/workflow/governance_audit.py
```

コマンドは最新の完了監査、merge数、以後のPR、完了Milestoneを取得するだけで、Issue・branch・設定を変更しない。`due_reasons`が空でもHigh Impactや異常がない証明にはならない。担当は今回の変更内容と下記の兆候を判断する。API失敗は未確認として既存Issueへ担当・次の確認を残し、監査完了/HEALTHYとしない。

実施対象なら具体的な監査Issueを検索・再利用または作成し、同じ発火を重複起票しない。複数条件は1回の監査にまとめる。監査自身の是正・ルール導入もその監査内で照合し、それだけを理由に別監査を再帰起票しない。影響範囲と実施時点を記録し、方針変更なら設計時に点検、完了時に実状態を照合する。安全性・所有権の問題は即時に対象操作を止めるが、無関係な開発やGUI待ちを一律に止める新しいmerge gateにはしない。

## 点検と最初の報告

最初に簡潔な結果を監査Issueへ記録し、安全な修正後に同じ報告を更新する。確認できた事実だけを問題として挙げ、推測は未確認として分ける。

- **Governance Health**: HEALTHY / MINOR ISSUES / NEEDS CLEANUP / STRUCTURAL PROBLEM。
- **Detected Problems**: 根拠のIssue/PR/ref/文書、起きる誤判断、確認範囲と未確認範囲。
- **Recommended Changes**: Critical（安全・整合を壊す）、Recommended（確認した更新漏れ/不要負担）、Optional（必要性が未成立）。
- **Rule Changes**: KEEP / SIMPLIFY / MERGE / AUTOMATE / REMOVE / REWRITE。該当なしも明記し、分類を埋めるために変更を作らない。

| 対象 | 最低限確認する内容 |
| --- | --- |
| Issue | Openの必要性、実装/受入/QAの残り、重複・過大/過小な分割、title/body、obsoleteな作業、parent/sub-issue・blocked by/blockingの実態。 |
| Milestone | 未設定の理由、到達目標への適合、完了扱いとOpenの矛盾、次目標、粒度、Phase等との役割重複、close可否。分類タグにしない。 |
| Project | 全Issue登録、実際のStatus/Priority、fieldの利用・意思決定上の必要性、Labelとの二重正本、View条件、概要/roadmapの現在地。標準の表示fieldを増えすぎたcustom fieldと混同しない。 |
| Branch | remote実ref/local branch/tracking ref/worktreeを区別し、merged/stale・命名・Issue対応・担当・未保存成果物・mainと統合先developとの差を照合。経過日数やbehindだけで不要としない。 |
| PR | Issue対応、merge後の元Issue/QA/Project、template・必須条件の目的と現実の負担。テスト/レビュー/受入を未確認のまま緩和しない。 |
| Rule | 各現行ルールを必要性・実際の利用・重複/矛盾・自動化可能性で上の6分類へ。既存だからKEEPとしない。[Change Impact](change-impact.md)の分類と消費側の一致、古いpath/新runtime resource、不要な重いCI、条件の重複、required checksとskipの整合も点検する。 |
| Context | 適用されるAGENTS/CLAUDE、instructions、README/CONTRIBUTING（存在するもの）、architecture/workflow、Issue/PR template、roadmap、Git文書を実GitHub状態と照合。歴史記録と現在の指示を区別する。 |

全ページを取得する。既存の[終了時照合](github-projects.md#issue終了時の整合確認)・[branch照合](pr-automation.md#ブランチ残存の判定と完了確認)とCase/受入の正本を再利用し、別のclose/削除判定を作らない。

未登録Issue、設定漏れ、closedなのにProject未完了、完了Milestoneの未達Issue、obsoleteなdependency、orphan/merged branch増加、Issue/PR追跡不能、重複Issue、Status矛盾は異常候補。2か所への手入力、多数の作成時設定、使われないfield、Label/fieldやIssue/Milestone/Projectの責務重複、例外や「ルールのルール」の増加も点検する。既に理由付きで保留されたものを新しい欠陥と数え直さない。

## 修正の順序と境界

1. 既存ルールで解決する。
2. 既存ルールを単純化・修正・統合する。
3. 既存automationで処理するか、実証した繰り返し負担にだけ最小の自動化を足す。
4. GitHub標準機能/Project Fieldの利用を検討する。二重正本は作らない。
5. それでも不足するときだけルールを追加し、代わりに削除/統合できる記述を確認する。

安全で明確なStatus/Milestone/Context修正、受入完了Issueのclose、obsoleteな関係解除、不要branch整理は直接進め、前後をreadbackする。作業中branch・未解放claim・未保存データ・公開承認待ち・main/developは保全する。大量close、field大規模削除、主要ルール削除、workflow/Issue体系/Milestone/branch戦略変更はMissionと影響を照合して具体的な提案にし、既存の権限境界を越えない。

closeを日数、merge、全子closedだけで判定しない。残受入を正本へ移してから重複をcloseし、Issueの永久削除はしない。未達GUI/mainは既存QAへ残す。監査のためだけのrename・reorganization・Issue再構成は行わない。

## Trigger Stateの正本

各回の監査Issue本文だけに、結果と次の1レコードを置く。既存coordinatorの`OWNER`（本repositoryのmaintainer）で作成したIssueだけを正本として読み、外部投稿者のmarkerは解析前に除外する。新しいProject Field、常設のtracking Issue、別ファイルの台帳、手入力のmerge counterは作らない。初回は[#195](https://github.com/shinma06/cursor-in-android-studio/issues/195)。次回は具体的な監査Issueを作り、完了レコードの`audited_through`が最新のものをコマンドが選ぶ。

````text
<!-- governance-audit-state:v1 -->
```json
{
  "status": "completed",
  "audited_through": "取得・照合済み範囲のUTC時刻",
  "completed_at": "監査完了のUTC時刻",
  "health": "MINOR ISSUES",
  "merge_threshold": 10,
  "audited_milestones": [1, 2, 3, 4, 5, 6],
  "last_high_impact_change": "確認した変更のIssue/PRと要点、なければnull"
}
```
````

上は書式例。Issueでは外側のtext fenceを付けず、marker直後にJSON fenceを置く。実際のUTC日時はISO 8601で記録する。全7領域の照合と是正/保留理由・owner・次操作を記録し、必要なレビュー/PR統合・引継ぎを終えて監査Issueをcloseしたレコードだけを採用する。途中・API失敗・主要範囲未確認の監査でbaselineを進めない。未達QAを監査完了でpassへ変えない。

`audited_through`は実際に確認したsnapshot境界で、完了時刻まで勝手に延ばさない。監査中にmergeされた未確認PRは次回countへ残す。完了によってその境界からのcountを0に戻し、それ以降のmergeは失わない。初回に記録がなければ初回監査を実施し、過去の部分監査を完了baselineと推定しない。

meaningful countはmain/developへmergeされたPRの番号を一意に数え、branch側やre-runを加算しない。単純な依存/機械更新で管理構造に影響しないとレビューしたPRだけ、PR本文に `Governance-count: exclude - 具体的な理由` を記録して除外できる。bot名やdocs-onlyだけでは除外しない。閾値は既定10、小規模5/高頻度20等へ変える場合は監査Issueへ理由を残し、完了レコードの値を更新する。

カレンダー監査、常駐監視、新しい必須CI gateは追加しない。担当が既存作業の区切りで発火を判定する。実行速度・品質の改善率は未測定であり、文書/CLI/机上評価の成功から実運用効果を断定しない。
