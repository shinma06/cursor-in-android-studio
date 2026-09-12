# GitHub Work Management Rules

2026-09-10 / #171。ユーザー指定の役割分担をIssue作成・triage・引継ぎへ適用する。[開発フロー](github-workflow.md)のclaim・PR・受入・安全なcleanupは維持する。

## 責務と正本

| 仕組み | 管理すること | 判断基準 |
| --- | --- | --- |
| Issue | 実行・検討・修正・実装する具体的な作業と受入 | 可能な限り単一の明確な目的。実装、修正、リファクタ、調査、技術検証、文書、移行、設計変更、テスト改善等 |
| Project | 全体の状態・優先順位・ロードマップ・横断情報 | 全Issueを適切なProjectへ登録。個々の作業の目的や到達目標を置き換えない |
| Milestone | 到達目標・リリース・開発段階 | Issueの完了がどの成果地点に寄与するか。単なる技術領域/作業種別タグにしない |
| native Relationship | Issue間の実際の依存と階層 | 開始/完了に必要な先行Issueはblocked by/blocking、実際の作業分解はparent/sub-issue |

巨大なテーマや長期方向性そのものを1つのIssueにしない。全体の計画はProject、到達目標はMilestoneへ置く。有限の受入を持つ大きな仕事は具体的な親Issueとsub-issueへ分解できる。全体の分類箱を作るために万能の親へ接続しない。旧全体ロードマップ#1は判断履歴として参照し、新しい作業の親・現在の進行管理の正本にはしない。

## Milestoneの選択

Issueには原則Milestoneを設定する。名称だけでなく到達条件と現在の対象scopeを確認し、適切な既存目標を再利用する。候補はACP migration、Agent Panel MVP、Cursor feature parity、Android integration phase、合意済みリリース等であり、これらを無条件に新設する指示ではない。

適切な目標がなければ新設の必要性を検討し、到達条件を定める。無関係な目標・架空のリリース版/期日を割り当てない。採否未定などで決められない場合は、Issueに未設定理由・判断担当・次の判断条件を残す。未設定を黙認したtriage完了にはしない。完了した履歴も適合する目標へ割り当てられるが、現在の未達受入やGUI/main待ちを過去の完了へ取り込まない。

## Relationshipの判定

すべてのIssueで、作成時またはtriage時に親子・依存を必ず確認する。存在する関係は本文の番号参照だけで終えず、GitHubのnative Relationshipへ設定する。

- Bの開始または完了にAの完了が必要なら、BをAでblocked byにする。blockingは逆向きに確認する。単なる関連・推奨順・同じMilestoneは依存ではない。
- 大きな作業の分割先はsub-issueを優先する。同じ分解を形式的なdependencyで重ねない。別の仕事との真の依存が併存するときだけ両方を使う。
- 先行Issueの一部Caseだけが条件の場合、Issue全体の完了待ちへ拡大しない。部分受入/環境/承認条件を本文に明示し、独立した作業として分ける必要があるときだけ分割する。
- 親・子・blocked by・blockingがいずれも存在しないIssueは正常なStandalone。分類のための架空の関係や、未判定を隠す関係は作らない。

Projectの`Relationship Status`は`Standalone` / `Has Relationship`の2値で、未判定は空欄。これはtriage済みかを示す表示で、関係の具体的な正本はnative Relationshipとする。関係を追加/解除したら両端を再取得し、子を持つ親もHas Relationshipへ更新する。空欄をStandaloneと推定しない。

## Issue作成・triageの確認

1. 具体的な目的、範囲と受入が明確か。大きすぎれば実際の分解を行う。
2. 適切なProjectを選び、Issueを登録したか。登録漏れを放置しない。
3. 適切なMilestoneがあるか。未決なら理由・担当・判断条件を記録したか。
4. 親/sub-issueの必要性を確認し、必要なnative関係を設定したか。
5. blocked by/blockingの必要性を確認し、真の依存を設定したか。
6. 関係がない場合は意図的なStandaloneと判定し、Relationship Statusを設定したか。
7. ProjectのStatusとPriority表示が正本に合うか。更新を再取得して確かめたか。

現Projectは[開発マップ #2](https://github.com/users/shinma06/projects/2)。既存type/priority/statusラベルはPR gateとの互換性を維持する。Priorityは既存priorityラベルをProjectのLabelsで表示し、別の手入力Priorityを増やさない。Project Statusは[既存の対応](github-projects.md#既存statusの対応)に従う派生表示とし、ラベルと独立に判断・編集しない。Milestone/ParentはGitHub標準フィールドを表示する。Phase/Workstream/Target等は必要性が出たものだけ追加し、同じ情報の正本を二重化しない。

## 引継ぎと終了

QAへの分離もIssue作成として扱う。元Issueから分離した残受入のQAはnative sub-issueとし、同じ到達目標を継承する。既存QAの別Milestone/親を無断で上書きせず、矛盾は担当が照合する。未設定理由を含むMilestone/関係判定、Project登録と両端のRelationship StatusをPMが確認する。

coordinatorの`--parent`は実在する親があるときだけ指定し、Standaloneは省略する。旧enrollmentの親/owner/sourceを一括書換えしない。Milestoneを全体の完了地点として扱い、子PRや元Issueの実装完了だけでMilestone・親・QAを完了にしない。

[終了時の整合確認](github-projects.md#issue終了時の整合確認)で元Issue・QA・親/依存・Project・Milestoneをreadbackする。API障害時は未反映対象・担当・再試行条件を記録し、未確認を成功にしない。各受入のclose判定、GUI/mainの分離、未保存成果物と担当の保全は従来どおり。

## GitHub公式仕様

- [Milestone](https://docs.github.com/en/issues/using-labels-and-milestones-to-track-work/about-milestones)
- [Issue dependencies](https://docs.github.com/en/rest/issues/issue-dependencies)
- [Sub-issues](https://docs.github.com/en/rest/issues/sub-issues)
- [Projects API](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-api-to-manage-projects)
