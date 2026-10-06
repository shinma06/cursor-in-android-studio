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

コマンドは最新の完了監査、merge数、以後のPR、完了Milestoneを取得するだけで、Issue・branch・設定を変更しない。`due_reasons`が空でもHigh Impactや異常がない証明にはならない。担当は今回の変更内容と下記の兆候を判断する。API失敗は取得不能範囲として記録し、未確認を確認済みにしない。限定監査の終了可否は下の範囲・リスクに基づく条件で判断する。

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
| Information disclosure | [公開前確認の3区分](github-workflow.md#個人情報非公開情報の公開前確認)で保護対象と通常情報を分け、下の[情報公開監査](#個人情報非公開情報の監査)で有限の範囲と残リスクを確認する。 |
| Context | 適用されるAGENTS/CLAUDE、instructions、README/CONTRIBUTING（存在するもの）、architecture/workflow、Issue/PR template、roadmap、Git文書を実GitHub状態と照合。歴史記録と現在の指示を区別する。[開発コンテキストの共通規則](../architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件)で用途/言語/形式/正本/読込条件/証拠を判定し、作成・更新・レビュー入口から同じ正本への到達を確認する。原証拠等の正当な例外を言語違反にしない。 |

管理状態の一覧は、監査Issueで定めた対象の全ページを取得する。情報公開の本文・履歴・バイナリは下節の有限な範囲を使う。既存の[終了時照合](github-projects.md#issue終了時の整合確認)・[branch照合](pr-automation.md#ブランチ残存の判定と完了確認)とCase/受入の正本を再利用し、別のclose/削除判定を作らない。

未登録Issue、設定漏れ、closedなのにProject未完了、完了Milestoneの未達Issue、obsoleteなdependency、orphan/merged branch増加、Issue/PR追跡不能、重複Issue、Status矛盾は異常候補。2か所への手入力、多数の作成時設定、使われないfield、Label/fieldやIssue/Milestone/Projectの責務重複、例外や「ルールのルール」の増加も点検する。既に理由付きで保留されたものを新しい欠陥と数え直さない。

## 個人情報・非公開情報の監査

分類の正本は[公開前確認の3区分](github-workflow.md#個人情報非公開情報の公開前確認)。監査は公開予定の差分、前回以降の追加・更新分、具体的な疑いがある箇所から始め、対象・目的・確認上限を監査Issueへ記録する。通常の著者情報や無害な識別子の検出だけでは投稿・開発を止めない。保護対象の漏えいを疑う具体的な根拠があれば、次のmerge閾値を待たず、その情報の再公開/配布を止めて確認する。

| 対象 | 疑いと目的に応じて選ぶ範囲 |
| --- | --- |
| ソースとGit履歴 | 関係するref/SHA、差分・設定/fixture/ログ・commit metadata。疑いが削除/rename済みファイルや過去refへ及ぶ場合だけ該当履歴へ広げる。ローカルtreeだけで公開全域の不存在を判定しない。 |
| Issue/PR（MR相当） | 関係するtitle/body、コメント/レビュー、差分、編集履歴、添付とメタデータ。closed/mergedも具体的な疑いがあれば対象に含める。 |
| 実行・配布・その他 | 関係するActionsログ/artifact、Release本文/asset、Wiki/Discussions、Project等の公開内容。疑いが配布物内にあればその内容も確認し、関連リソースを辿る理由と上限を記録する。 |

選んだ範囲の一覧はページングを完了し、目的に必要な本文・履歴・添付内容を確認する。範囲拡大には情報の種類、具体的なリスク、期待する判断/是正効果、費用の見込みと有用な次操作を示す。初回、低リスク検出、権限不足、古いcache/更新履歴の完全性不明だけで全履歴・全バイナリの再読解や関連先への再帰走査を自動追加しない。取得不能内容は理由とリスクを分けて残し、危険な漏えいを疑う根拠があれば当該確認・対応を継続する。

利用可能な[GitHub Secret scanning](https://docs.github.com/en/code-security/concepts/secret-security/secret-scanning)のalertと既存scannerを補助に使う。alertなしや文字列検索の一致なしだけで、個人情報・業務情報・画像/バイナリを含む全範囲の不存在を証明したことにしない。監査用の新しい必須CI gateや常駐監視を追加しない。

結果は同じ監査Issueへ、確認日時、ref/SHA・範囲、検出と分類、未確認範囲/理由、是正または残存受容/見送りの理由・担当・再評価条件を一度記録する。公開報告は問題の種類・影響と安全な参照に限定し、保護対象の値・raw証拠やその所在へ誘導する詳細は承認済みの非公開保管先で扱う。

軽微なものは権限内で現行内容を低コストに訂正するか、保持/残存受容を記録して終了できる。新しい実質的な根拠がなければ同じ事項を再起票・再承認・無限待機へ戻さない。限定監査は、定めた範囲の確認とリスク別の処置/受容・引継ぎが済み、強い危険のある未解決事項がなければ完了できる。範囲外/取得不能を全域の「漏えいなし」や確認済みへ変えず、healthは確認範囲に対する評価と明記する。高リスクの未解決漏えいや、その判断に不可欠な証拠不足は完了を妨げ、ownerと有用な次操作または解除条件を残す。

保護対象の漏えいはmaintainerへ非公開経路で引き継ぎ、認証秘密の失効・交換を優先する。[GitHub公式の削除手順](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)に照らし、失効後も残る危険と履歴削除の効果・費用を評価する。削除だけで解決済みにせず、具体的に関係する履歴/配布物/copyと再混入を確認し、制御不能なcopyの回収を保証しない。通常修正は既存Issue/専用PRで進め、資格情報操作や削除は既存権限/承認範囲に従う。履歴改変・force push・保護変更・不可逆な削除を自動実行せず、必要な場合だけ具体案と不足する承認を確認する。無関係な開発は一律停止しない。

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

上は書式例。Issueでは外側のtext fenceを付けず、marker直後にJSON fenceを置く。実際のUTC日時はISO 8601で記録する。全8領域（情報公開を含む）について、定めた範囲の照合・処置/受容・未確認理由・owner・再評価条件を記録し、必要なレビュー/PR統合・引継ぎを終えて監査Issueをcloseしたレコードだけを採用する。途中や高リスクの未解決事項・不可欠な証拠不足ではbaselineを進めない。受容済みの低リスク残存/取得不能範囲だけで完了を禁止せず、全域安全の保証にも使わない。未達QAを監査完了でpassへ変えない。

`audited_through`は実際に確認したsnapshot境界で、完了時刻まで勝手に延ばさない。監査中にmergeされた未確認PRは次回countへ残す。完了によってその境界からのcountを0に戻し、それ以降のmergeは失わない。初回に記録がなければ初回監査を実施し、過去の部分監査を完了baselineと推定しない。

meaningful countはmain/developへmergeされたPRの番号を一意に数え、branch側やre-runを加算しない。単純な依存/機械更新で管理構造に影響しないとレビューしたPRだけ、PR本文に `Governance-count: exclude - 具体的な理由` を記録して除外できる。bot名やdocs-onlyだけでは除外しない。閾値は既定10、小規模5/高頻度20等へ変える場合は監査Issueへ理由を残し、完了レコードの値を更新する。

カレンダー監査、常駐監視、新しい必須CI gateは追加しない。担当が既存作業の区切りで発火を判定する。実行速度・品質の改善率は未測定であり、文書/CLI/机上評価の成功から実運用効果を断定しない。
