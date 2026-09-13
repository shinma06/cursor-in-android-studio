---
name: start-work
description: Begin or resume an Issue-scoped code, docs, or configuration change with ownership and acceptance tracking.
---

# Start work

読み取りだけの助言・レビューには新Issue/branchを作らない。変更作業ではAGENTS.mdの共通契約と[GitHub workflow](../../../docs/development/github-workflow.md#参照する範囲と進行判断)を使う。確認済み同一資料の再読は不要。新機能はMission/ACP First、製品変更は要件と該当設計、運用変更は対応する運用文書を確認する。

開始できる状態は、対象Issue/コメント・関連PR・Project/対象Milestone・依存と受入が分かり、他writerと競合しないこと。Issue検索後、owner、scope、base SHA、依存、独立reviewer、GUI要否、次操作をclaimしreadbackする。未解放claimは時間で失効しない。Project/label/native関係は[Work Management](../../../docs/development/work-management.md)に従い、Standaloneに架空の親を付けない。

作業場所はstatus/worktreesと`git fetch --prune origin`後のbaseを照合した専用Issue branch/worktree。通常はorigin/develop、GUI不要main toolingはworkflowの条件で選ぶ。初期upstreamを解除し`bash scripts/workflow/bootstrap.sh`を実行する。再開時は既存owner/source/実行handleを先に照合し、別worktreeへの作り直しやenrolled branchへの並行編集をしない。

`docs/verification/changes/issue-N.json`に受入全体のCase（日本語手順/期待結果、GPT/human状態、修正/再確認）を記録する。GUI不要なら理由と必要CLI検証を記載する。最初の意味あるpushでIntegration/Verification付きDraft PRを作り、実装・検証を続ける。GUIは指定operator/leaseが必要、未観察をpassにしない。

開始手続きや初版PRで作業完了とせず、依頼の受入を満たすまで進めて[finish-work](../finish-work/SKILL.md)へ渡す。追加Session/独立reviewの前に[Codex実行規約](../../../docs/development/codex-execution-policy.md)を確認する。
