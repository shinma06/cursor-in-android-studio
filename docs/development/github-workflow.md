# GitHubを起点にした並列開発

2026-09-06 / #31。**Gitへの言及がない依頼にも適用する。** 旧「PR不要・GPTがmainへpush」は廃止。
変更を伴うタスクは文書・設定・小修正を含め、Issue → claim → 専用branch/worktree → PR → 検証/レビュー → GitHub merge → Issue更新の順で進める。読み取りだけの相談・レビューは新Issue不要。既存Issueの受入条件内なら再利用し、独立した成果や担当には子Issueを作る。ユーザーは毎回Git操作を指示しなくてよい。

## 正本と役割

- Issue: 目的、受入条件、担当セッション、依存、状態、次の操作の正本。#1は全体への入口。
- PR: 差分、対象HEAD、レビュー、CI、統合判断の正本。ローカルメモだけで引き継がない。
- QA matrix/run: 観察と証拠。Issueと相互リンク。過去のpassは現在のビルドを保証しない。
- GitHub Projectsは任意の一覧表示。未導入でもIssueの状態欄で運用し、二重管理しない。

| 役割 | 担当・責任 |
|---|---|
| 進行役 | タスク群につき1セッションをIssueで指定。Issue分割、claim競合、依存順、PR統合を調整 |
| 実装担当 | GPT/Claude等、1 Issue・1 branch・1 worktree・1 writer。自分のbranchへpushしPRを更新 |
| 独立レビュアー | 実装担当と別セッション。固定SHAの差分と受入条件を読み、結果をPRへ記録。GUI操作なし |
| GUI担当 | 同一macOSログインセッションにつきGPT 1セッション。人間への引継ぎ可。予約取得後のみ操作 |
| 人間 | 優先度、ログイン/OS権限、主観判断、契約変更、異常時の占有解除を判断 |

GPTというモデル名だけで担当を識別しない。`gpt-31-20260906-a`のようなセッションIDを使う。
別のGPTタスクも独立担当であり、GUI権限を自動的に共有しない。Cursorは原則fixture上の検証対象。
製品実装の明示的な委譲時だけ専用worktreeとIssueを割り当てる。

## 開始（実装前の必須手順）

1. AGENTS.md、本文書、要件、#1、対象Issue本文/全コメント、open PRを読む。
2. `git status --short --branch`、`git worktree list`、`git fetch origin`、HEADとorigin/mainの差を確認。
   既存編集をpull/stash/resetに巻き込まない。親checkoutは読み取りの入口にする。
3. 重複Issueを検索。対象がなければIssueテンプレートで作成し、親へリンクする。
   GUIのみの検証も追跡する。大きなIssueの別々の実装は子Issueに切り出し、依存先を記載。
4. Issueに下記claimを投稿して読み戻す。未完了claimは時間経過で失効しない。
   同一Issueへ競合投稿した場合、**未解放の最小コメントIDのclaimだけが有効**。他は開始せず撤回コメント。
   複数Issueの同じファイル/API変更は進行役が境界か順序を先に決める。ラベル/assigneeだけをロック扱いしない。
5. fetch済みorigin/mainから専用worktreeを作る。branchは `<agent>/<Issue番号>-<slug>`、agentはcodex/claude/cursor。
   既存branch/worktreeがある場合はownerとHEADを確認して再開し、重複作成しない。
6. `bash scripts/workflow/bootstrap.sh`でhooksを設定。branch/worktree/ownerをclaimへ記録し、実装開始。

例（31は自分のIssue番号へ置換）:

```bash
git fetch origin
git worktree add -b codex/31-github-flow ../cursor-agent-issue-31 origin/main
cd ../cursor-agent-issue-31
git branch --unset-upstream
bash scripts/workflow/bootstrap.sh
```

初回pushは `git push -u origin HEAD`。origin/mainをtask branchのupstreamとして残さない。
branchを変えるだけで同じディレクトリを複数エージェントに渡すことは禁止。
GitHubに接続できずIssue/claimを確認できない場合、新規実装は開始しない。
既にclaim済みの専用worktreeではオフライン実装・テストを継続できるが、公開/統合/完了は復旧後。

claim例:

```text
status: in-progress
owner: gpt-31-20260906-a; issue: #31; parent: #1
base: <full origin/main SHA>
branch: codex/31-github-flow
worktree: <absolute path>; host: <host ID>
scope: <files/modules and acceptance>; excluded: <out of scope>
dependencies: none / #number and required state
reviewer: <separate session or pending assignment>
gui: not-required / required (MV IDs); operator: unassigned until reserved
next: <concrete next step>; budget: <task/GUI limit>
```

## 並列実装と更新

- 共通ファイル（CLAUDE.md、matrix、build.gradle.kts等）は必要な担当だけが編集。別タスクの結果はIssueで渡し、まとめる担当を指定。
- 依存PRが未mergeなら独立部分を先行するか待つ。原則、依存PRがmainに入ってから取り込む。
  stacked PRが必要ならbase PR/順序を明記し、親merge後baseをmainへ変更しCI/レビューをやり直す。
- 共有branchに複数writerを置かない。共同実装は子Issue/別worktree/別PR。
- テスト/buildPluginは各worktreeで実施可。Gradleの他タスクのdaemon停止や共有キャッシュ削除は禁止。
- `runIde`、IDE再起動、plugin配置、設定変更はGUI占有が必要。ビルド成功後に起動中IDEへ自動配置しない。
- 開始、PR作成、範囲変更、review待ち、GUI待ち、blocked、引継ぎ、merge、完了ごとにIssueへ記録。
  同じ内容を毎ターン投稿せず、最新HEAD/PR/次の一手がGitHubから復元できるようにする。

状態欄は `ready → in-progress → review → gui-queued → gui-running → ready-to-merge → done`。
GUI不要ならreviewからready-to-mergeへ。`blocked`には原因、解除担当、再開点が必須。
コードレビュー状態とGUI状態は別欄にし、GUI待ちを開発全体の停止と解釈しない。

## PR・レビュー・統合

最初の意味あるcommitをpushしたらDraft PRを作り、Issueへリンクする。小修正でも省略しない。
PR本文はテンプレートを使用。`Issue: #N`は実在する同リポジトリのopen Issue、branchの番号と一致。
GUI未確認は `GUI: required`、不要は `GUI: not-required` と具体的理由を記載。
GUI必要PRは原則検証passまでDraft/待機。見た目・機能・権限・復元の変更を安易にGUI不要にしない。

1. `./gradlew test`と変更した運用スクリプトのテストを実行。パッケージ変更やGUIビルドはbuildPlugin。
2. 別セッションが固定HEADと差分をレビュー。重大/中程度の未解決指摘を修正し、変更後の再レビュー範囲を記録。
   同じGitHubアカウントを使うエージェントの自己PRへのApproveは独立承認の代替にならない。
   `reviewer session / reviewed SHA / findings / disposition`をPRコメントへ記録。セルフレビューと区別。
3. GUI担当が[GUI手順](gui-coordination.md)で予約/実行。証拠と対象HEADをPRへ渡す。
4. 進行役がfetchし、main更新をtask branchへ通常merge（公開履歴のrebase/force push禁止）。競合は担当と調整。
   main取り込みやコード更新後はテストを再実行し、GUI対象の挙動/依存/資材が変わればGUI再検証。
   docsのみの追記で証拠を再利用する場合もレビュー担当が影響なしの理由と旧/新SHAを記録。
5. 最新HEADの `test` / `PR policy` がsuccess、未解決レビューなし、独立レビューとGUI判定を確認してReadyにする。
   CI未実行/権限・予算停止はblocked。ローカル成功だけでCI成功としない。
6. 進行役1セッションだけが `gh pr merge <N> --squash --match-head-commit <reviewed HEAD>` で統合。
   直前にbase SHAも読み直し、動いていたら4へ戻る。保護未導入時、この確認はサーバーの原子的base固定ではない。
   mainへのローカルcommit/push、管理者bypass、hook無効化、`--no-verify`は禁止。
7. merge SHA/CI/GUI/残条件をIssueへ記録。受入完了時のみclose、親チェック更新、claim解放。
   `Closes #N`は全受入完了時だけ。部分実装は `Refs #N`。
   GUI延期の例外は人間の明示判断と追跡Issueが必要で、元の受入は未完了のまま。担当が勝手にGUI不要へ変更しない。
8. cleanでプロセスのない自分のworktreeだけ削除。共有mainは必要な担当がclean確認後`git pull --ff-only`。
   他担当のworktree/branchを整理しない。取り消しもrevert PRで行う。

## 中断・再開・障害

引継ぎコメント: owner、状態、branch、HEAD、PR、未commitファイル、テスト、GUI予約token/状態、次の操作、依存、claim継続/解放。
新担当は旧ownerの解放または人間/進行役による明示再割当の後にclaimする。
再割当コメントは旧claimのコメントID、停止確認、新ownerを指定し、`supersedes claim <ID>` と明記する。
これを旧claimの解放と扱い、最小ID判定から除外する。古いownerは再開前にこの記録を確認して停止する。
応答がないだけで編集やGUI操作を奪わない。独立した別Issueは進められる。
誤ってmainにcommitした場合はpushせず、SHAを保全するbranchを作り、専用worktree/PRへ移す。
他者の変更を含むmainのresetは自動実行しない。漏えい情報は公開Issue/ログへ貼らない。

## 自動チェックの範囲とGitHub設定

- pre-commit: main/master/detached HEAD、Issue番号なしbranchのcommitを拒否。
- pre-push: 宛先refのmain/masterへのpush/削除を拒否。現在のHEAD以外、dirty状態、non-fast-forwardのpushも拒否し、検証資材の不一致を防ぐ。
- PR policy: branch/Issue番号、Issue実在/open、GUI欄と理由をチェック。証拠の真偽やレビューの質までは判定しない。
- CI: pull_requestのコードを最小read権限で実行。PR本文をshellへ展開しない。`pull_request_target`でPRコードを実行しない。

**実測（2026-09-06）:** private/User所有、adminあり。rulesets/branch protectionともHTTP 403でプラン変更を要求。
サーバー側の強制保護は未導入。hooksは迂回可能、Actionsは保護なしではmergeを阻止できない。
他clone/Web/APIまで禁止するにはGitHub側保護が必要。現在は規約＋hooks＋CI＋進行役による確認の運用。

導入可能になったら管理者が `.github/main-ruleset.json` をレビューしてREST APIで作成する。
先に既存rulesetを一覧確認し、同名があればIDで更新（重複作成しない）。適用後GETでactive/main対象/required checksを確認。
設定: main削除/force push禁止、PR必須、test/PR policy必須、最新base必須、会話解決必須、bypassなし。
同一アカウント運用のためrequired approvalsは0、独立セッションレビューは規約で必須。
別のwrite権限を持つレビュー用アカウントを確保したら1以上へ変更する。
CODEOWNERSは実際のwrite権限保持者へのレビュー要求であり、エージェント識別や自動承認ではない。
個人所有なので架空teamや組織共通.githubは作らない。Dependabot/CodeQL/secret scanningやreusable workflowは専用Issueで検討。

## 選定理由・一次情報

短命branchとPRを使うGitHub Flowを採用。長期develop/release branchは現状の単一main配布には導入しない。
worktreeはcheckoutを分けるが、同一デスクトップやIDE設定を隔離しないため、GUIは別の排他が必要。

- [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow): branch、PR、レビュー、統合。
- [Git worktree](https://git-scm.com/docs/git-worktree): checkout分離と共有メタデータ。
- [GitHub Issues](https://docs.github.com/en/issues): タスクを追跡する入口。
- [Rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets): 利用プランと強制ルール。
- [CODEOWNERS](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners): 所有者の権限とレビュー要求。
- [Actions secure use](https://docs.github.com/en/actions/reference/security/secure-use): 最小権限と信頼できない入力。
