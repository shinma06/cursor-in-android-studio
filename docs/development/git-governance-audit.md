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
| Information disclosure | 個人情報・認証情報・非公開情報がGitHubのソース/履歴・Issue/PR・添付/配布物等に存在していないか、下の[情報公開監査](#個人情報非公開情報の監査)で確認する。 |
| Context | 適用されるAGENTS/CLAUDE、instructions、README/CONTRIBUTING（存在するもの）、architecture/workflow、Issue/PR template、roadmap、Git文書を実GitHub状態と照合。歴史記録と現在の指示を区別する。[開発コンテキストの共通規則](../architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件)で用途/言語/形式/正本/読込条件/証拠を判定し、作成・更新・レビュー入口から同じ正本への到達を確認する。原証拠等の正当な例外を言語違反にしない。 |

全ページを取得する。既存の[終了時照合](github-projects.md#issue終了時の整合確認)・[branch照合](pr-automation.md#ブランチ残存の判定と完了確認)とCase/受入の正本を再利用し、別のclose/削除判定を作らない。

未登録Issue、設定漏れ、closedなのにProject未完了、完了Milestoneの未達Issue、obsoleteなdependency、orphan/merged branch増加、Issue/PR追跡不能、重複Issue、Status矛盾は異常候補。2か所への手入力、多数の作成時設定、使われないfield、Label/fieldやIssue/Milestone/Projectの責務重複、例外や「ルールのルール」の増加も点検する。既に理由付きで保留されたものを新しい欠陥と数え直さない。

## 個人情報・非公開情報の監査

定期監査では、[公開前確認の情報区分・判定基準](github-workflow.md#個人情報非公開情報の公開前確認)を使い、GitHub上に個人情報や公開してはいけない情報が存在していないか確認する。Openだけでなくclosed/mergedの履歴も含め、公開承認・実データ/合成値・現在の閲覧範囲を照合する。漏えいの疑いがある場合は次回のmerge閾値まで待たず、対象情報の再公開/配布を止めて本節の確認・対応へ進む。

| 対象 | 確認範囲 |
| --- | --- |
| ソースとGit履歴 | main/developだけでなくremote branch/tag・取得可能なPR refsの現在と履歴。削除/rename済みファイル、設定/fixture/ログ、commit message・author/committerのname/email、branch/tag名。ローカル作業treeだけで不存在を判定しない。 |
| Issue/PR（MR相当） | Open/closed/mergedのtitle/body、コメント、レビュー/inline comment、差分、編集履歴、添付画像/動画/ファイルとメタデータ。 |
| 実行・配布・その他 | Actionsログ/summary/artifact、Release本文/asset（ZIP等の内部も含む）、Wiki、Discussions、Projectの内容、Milestone/label/repositoryの説明、GitHub Pages/Packages等の公開物・メタデータ。対象repoから参照する関連GitHubリソースも確認範囲へ記録する。 |

一覧取得はページングを最後まで行い、本文・履歴・添付/バイナリの内容も確認する。初回は取得可能な既存範囲を確認し、以後は前回の確認範囲/時点を起点に追加・更新分と未確認分、前回の検出箇所の残存を照合する。差分や更新履歴の完全性を確認できない対象は対象全体を再確認する。存在しない/無効な機能はその根拠を記録し、権限不足・削除/期限切れ・API制約で取得できない内容は未確認として分ける。

利用可能な[GitHub Secret scanning](https://docs.github.com/en/code-security/concepts/secret-security/secret-scanning)のalertと既存scannerを補助に使う。alertなしや文字列検索の一致なしだけで、個人情報・業務情報・画像/バイナリを含む全範囲の不存在を証明したことにしない。監査用の新しい必須CI gateや常駐監視を追加しない。

結果は同じ監査Issueへ、確認日時、ref/SHA・リソース種別と範囲、検出の有無、未確認範囲/理由、是正・保留の担当と次の操作を記録する。公開報告は問題の種類・影響と安全な識別子に限定する。秘密の値・個人情報・rawログ・秘密を含むURL/検索語・添付を転載せず、漏えい箇所へ誘導する詳細や原証拠は承認済みの非公開保管先で扱う。主要範囲が未確認なら「漏えいなし」やHEALTHY/監査完了にせず、baselineを進めない。

検出時は対象の投稿・push・配布を止め、maintainerへ非公開経路で引き継ぐ。認証情報は[GitHub公式の削除手順](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)に従い失効・交換を最優先にし、本文/ファイルを削除しただけで解決済みにしない。Git履歴・PR refs/差分・編集履歴・添付・artifact/asset・キャッシュ・fork等の残存と再混入を確認する。取得/制御できないコピーの回収を保証せず、必要なGitHub Support対応を含め担当・残リスクを残す。通常修正は既存Issue/専用PRで進め、資格情報操作や削除は既存権限/承認範囲に従う。履歴改変・force push・保護変更・不可逆な削除を監査の名目で自動実行せず、具体的な対応案を準備して不足する承認だけを確認する。無関係な開発は一律停止しない。

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

上は書式例。Issueでは外側のtext fenceを付けず、marker直後にJSON fenceを置く。実際のUTC日時はISO 8601で記録する。全8領域（情報公開を含む）の照合と是正/保留理由・owner・次操作を記録し、必要なレビュー/PR統合・引継ぎを終えて監査Issueをcloseしたレコードだけを採用する。途中・API失敗・主要範囲未確認の監査でbaselineを進めない。未達QAを監査完了でpassへ変えない。

`audited_through`は実際に確認したsnapshot境界で、完了時刻まで勝手に延ばさない。監査中にmergeされた未確認PRは次回countへ残す。完了によってその境界からのcountを0に戻し、それ以降のmergeは失わない。初回に記録がなければ初回監査を実施し、過去の部分監査を完了baselineと推定しない。

meaningful countはmain/developへmergeされたPRの番号を一意に数え、branch側やre-runを加算しない。単純な依存/機械更新で管理構造に影響しないとレビューしたPRだけ、PR本文に `Governance-count: exclude - 具体的な理由` を記録して除外できる。bot名やdocs-onlyだけでは除外しない。閾値は既定10、小規模5/高頻度20等へ変える場合は監査Issueへ理由を残し、完了レコードの値を更新する。

カレンダー監査、常駐監視、新しい必須CI gateは追加しない。担当が既存作業の区切りで発火を判定する。実行速度・品質の改善率は未測定であり、文書/CLI/机上評価の成功から実運用効果を断定しない。
