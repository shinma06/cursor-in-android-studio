# GitHubを起点にした開発と二段階統合

2026-09-07 / #83。ユーザー方針が従来の「mainのみ・GUI完了まで全merge待ち」を上書きします。
変更は文書・設定を含め、Issue → claim → 専用branch/worktree → Draft PR → テスト/独立レビュー → target別gate → GitHub merge → 残条件更新で進めます。読み取りだけの相談・レビューは新Issue不要です。

## 統合条件

| 対象 | 必須条件 | GUI未実施/環境blocked/製品fail | merge方式 |
|---|---|---|---|
| develop | 必要テスト・独立コードレビュー・Case JSONと修正/再確認追跡 | 状態を残して統合可 | squash |
| mainへのpromotion | 固定develop候補の全commit・全必要Caseを同じbuildでpass、必要テスト・独立レビュー | 1件でも残れば不可 | merge commit |
| mainへのGUI不要tooling | docs/scripts/CI/agent入口だけの差分、具体的理由とCLI検証、独立レビュー | 製品変更の逃げ道にしない | squash |

未解決コードレビュー指摘やテスト失敗をdevelopへ通す方針ではありません。製品failには専用修正Issue/branch/PRが必要です。GPTと人間の適切な実観察はどちらも有効ですが、未確認をpassに変更しません。

[今回の確認一覧](../verification/current.md) / [正本JSONと固定候補の手順](../verification/README.md)が人間の入口です。長大な既存MV matrixは履歴・詳細であり、新候補の結果を二重編集しません。

## 正本と役割

Issueは目的・受入・担当・依存・次の操作、PRは差分・固定HEAD/base・レビュー・CI・統合判断の正本です。
QA JSONはCaseと候補結果、生成Markdownは閲覧用です。過去runのpassは別buildを保証しません。

PM/進行役は統合順・claim・GitHub設定を管理します。実装担当は1 Issue・1 branch・1 worktree・1 writerです。
独立レビュアーは別sessionで固定SHAを読み、GUIは操作しません。GUI担当はhost/OS sessionに1名の指定GPT、または人間引継ぎです。
モデル名ではなく公開可能なsession IDで識別します。公開Issue/PRへhost名、ローカル絶対パス、token、private rawログを出しません。

## 開始

1. AGENTS.md、本文書、要件、#1、対象Issue本文と全コメント、open PRを読む。
2. `git status --short --branch`、`git worktree list`、`git fetch origin`、HEADと意図したbaseの差を確認する。既存編集をpull/stash/resetに巻き込まない。
3. 重複Issueを検索し、必要な専用Issueを作る。親へリンクし、独立した実装やGUI検証を分ける。
4. claimを投稿して読み戻す。未解放claimは時間で失効しない。同一Issueの最小コメントIDの有効claimだけがwriterになる。競合者は開始せず撤回する。複数Issueの共通ファイルはPMが境界/順序を決める。
5. 通常はorigin/developから `<codex|claude|cursor>/<Issue>-<slug>` と専用worktreeを作る。main toolingはorigin/mainから、promotionは固定candidateから作る。初期upstreamを解除し `bash scripts/workflow/bootstrap.sh` を実行する。
6. 最初の意味あるpushでDraft PRを作成する。PR本文はテンプレートに従い、`Issue`、`Integration`、`Verification`、`GUI`、`GUI reason`を記録する。source JSONはGUI不要変更にも必須で、理由・CLI検証を含む。

claim例（パスとhostはprivate local registryだけ）:

```text
status: in-progress
owner: gpt-83-policy-a; issue: #83; parent: #1
base: <full SHA>; target: develop
branch: codex/83-policy; worktree: isolated (local registry)
scope: <files and acceptance>; excluded: <out of scope>
dependencies: <Issue/PR and required state>
reviewer: <separate session or pending>
gui: required / not-required (reason); cases: <JSON path>
next: <concrete action>
```

ローカルではrepository名のmain checkout、`worktrees/cursor-in-android-studio-issue-N`、`GUI-checks`を製品名の親配下へ置きます。
既存worktreeを再利用する場合はowner/HEADを確認し、他担当へ同じディレクトリを渡しません。移動は`git worktree move`、main移動後は`git worktree repair`を使い、未commit状態を照合します。
GitHubへ接続できずclaimを確認できないときは新規実装を開始しません。claim済み専用worktreeでのオフライン実装/テストは継続可能です。

## 実装・レビュー・引継ぎ

- 共通ファイルは担当者だけが編集。依存PRは通常develop統合後に取り込む。stacked PRは依存baseと順序を明記し、base変更後に再レビューする。
- テスト/buildPluginは専用worktreeで実施可能。共有cache削除/他タスクdaemon停止は禁止。runIde、install、再起動は[GUI lease](gui-coordination.md)必須。
- 開始、PR作成、scope変更、review待ち、GUI待ち、blocked、引継ぎ、mergeごとにIssueへ最新HEAD/次の一手を記録する。
- `./gradlew test`と変更したworkflow/loopテストを実行。パッケージ/GUI buildはbuildPluginも実施する。
- 別sessionが固定HEAD/baseの差分と受入を確認し、`reviewer session / reviewed SHA / base / findings / disposition`を記録する。同一GitHubアカウントのApproveだけでは代替しない。
- 重大/中程度のコード指摘を解消し、再レビューする。GUI状態と実装scopeを分け、未実施だけをコード欠陥としない。
- writerを停止して[自動進行役](pr-automation.md)へopaque IDでenrollする。登録後は同じbranchを編集/commit/pushせず、coordinatorに任せる。既存PAUSED heartbeatを勝手に再開しない。

## 統合・完了

進行役はtarget更新を通常mergeし、テスト・非force push・独立再レビューを行います。HEAD/base/target/本文/Issue条件/feedback変更は承認を失効させます。
最新 `test` / `PR policy` / `Agent review` / `Acceptance gate` がsuccess、会話解決済みであることを確認します。CI未実行・失敗をローカル成功で代替しません。

developではCase不足を拒否しますが、GUI passを要求しません。未実施/blocked/failと次の操作を保ち、`Refs #N`を使います。
coordinatorはdevelop mergeでIssueをcloseせず、親チェックも完了にしません。検証担当は区切りで一覧をまとめて実施します。

mainは[固定候補手順](../verification/README.md)で範囲全体を確認します。候補後に許す差分はpromotion JSONとそのpromotion IssueのCase JSONだけです。
一部Caseだけのpassや未確認製品commitをQA文書変更へ偽装することはgateが拒否します。main先行tooling/前回QA記録は候補固定前に専用develop同期PRへ取り込みます。
promotionはmerge commitに限定し、GitHub APIのHEAD指定とstrict baseを通します。merge直前にmain/develop refを再取得します。
main/developへの直接commit/push、admin bypass、hook無効化、force push、`--no-verify`は禁止です。

merge SHA・CI・Case結果・残条件をIssueへ記録し、受入を個別に満たす範囲だけcloseします。promotion Issue完了でも元の機能/QA Issueを一括closeしません。
cleanupは自分のclean/停止確認済みIssue branch/worktreeのみ。main/master/developはremote/localとも削除しません。他担当の変更/branchを整理しません。

## 中断・再開

引継ぎはowner、状態、branch/target、HEAD、PR、未commitの有無、テスト、Case状態、次の操作、依存、claim継続/解放を公開可能な範囲で記録します。
新担当は旧ownerの解放またはPM/人間の明示再割当の後にclaimします。再割当は旧コメントID、停止確認、新ownerを記録し、`supersedes claim <ID>`と明記します。
応答がないだけで横取りしません。誤ってmain/developへcommitした場合はpushせずSHAをbranchで保全し、専用PRへ移します。自動resetは禁止です。

## 自動gateとGitHub設定

- hooks: Issue branch以外のcommit、main/master/developへのpush/削除、別branch/dirty/非fast-forwardのpushを拒否。
- PR policy: target/Integration、実在open Issue、branch番号、GUI理由、Case JSONパスを検査。developの自動close文言を拒否。
- Acceptance gate: eventの遅延し得るbase.shaを信用せず、許可された最新base branchからcheckoutし、コードHEADと現在refを照合する。trusted baseのコードでPRのJSONをデータとして読み、developはCase追跡、main toolingはパスと理由、promotionは固定候補の全commit/Case/build/観察を検査。
- Agent review: coordinatorが独立sessionの最新固定HEAD/baseレビューとtarget別受入を照合。PR内コードから自己承認しない。

設定案の正本は `.github/main-ruleset.json` / `.github/develop-ruleset.json`。実設定の反映はPM担当です。
mainの既存ruleset IDは22368189。両branchともPR必須、上記4 checks、strict base、会話解決、削除/force禁止、bypassなし、同一アカウント運用のapproval count 0を維持します。
mainはsquash/mergeを許可し、promotionだけコードgateがmerge専用を選びます。developはsquashだけ許可します。
GitHubが補う既定値と指定値の差を区別し、GET rulesetとbranches effective rulesで反映を確認します。

### #83の一回限りのbootstrap

このPRだけは旧mainからのGUI不要tooling変更です。製品src/build設定に変更なし、workflow/loop/Gradleテストと固定HEAD/baseの独立レビューをPMへ渡します。
旧enrollはhost/sourceを公開するため使用しません。writer停止後、PMが独立レビューに基づくAgent reviewを記録し、現行3必須checksを確認して通常main PR mergeします。
新Acceptance workflowは、**#83専用branch・main向け・実checkout HEADが旧base `7eb2b9d446d2f2cac21fff293c076358fc667721`**と一致するときだけbootstrapを明示して成功します。
この例外はGUI不要を自動認定するものではなく、新validatorはCLIテストと独立レビューで検証します。merge後のbaseでは例外は使えません。
PMは導入main SHAからdevelopを作成し、両rulesetへAcceptance gateを追加してGET照合します。その後に通常PRをretargetします。
既存enrollmentは自動変更しません。PMがwriter/worker停止・cleanを確認した後のみ `rebind-target` でtargetを明示更新し、古いレビューを失効させます。既存heartbeatはPAUSEDのままです。

## 一次情報

- [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow)
- [Git worktree](https://git-scm.com/docs/git-worktree)
- [Rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)
- [Actions secure use](https://docs.github.com/en/actions/reference/security/secure-use)
