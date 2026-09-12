# GitHubを起点にした開発と二段階統合

2026-09-07 / #83。ユーザー方針が従来の「mainのみ・GUI完了まで全merge待ち」を上書きします。
変更は文書・設定を含め、Issue → claim → 専用branch/worktree → Draft PR → テスト/独立レビュー → target別gate → GitHub merge → 残条件更新で進めます。読み取りだけの相談・レビューは新Issue不要です。

開発Agentの追加・委譲前に[Codex実行規約](codex-execution-policy.md)を確認します。メインモデルがGPT-6 AstraならSubagentの生成・委譲は禁止し、合理的理由のある独立top-level Session / Thread間連携と通常のToolを使います。Astra以外には本規約による禁止を適用しません。

## 統合条件

| 対象 | 必須条件 | GUI未実施/環境blocked/製品fail | merge方式 |
|---|---|---|---|
| develop | 必要テスト・独立コードレビュー・Case JSONと修正/再確認追跡 | 状態を残して統合可 | squash |
| mainへのpromotion | 固定develop候補の全commit・全必要Caseを同じbuildでpass、必要テスト・独立レビュー | 1件でも残れば不可 | merge commit |
| mainへのGUI不要tooling | docs/scripts/CI/agent入口だけの差分、具体的理由とCLI検証、独立レビュー | 製品変更の逃げ道にしない | squash |

未解決コードレビュー指摘やテスト失敗をdevelopへ通す方針ではありません。製品failには専用修正Issue/branch/PRが必要です。GPTと人間の適切な実観察はどちらも有効ですが、未確認をpassに変更しません。

[今回の確認一覧](../verification/current.md) / [正本JSONと固定候補の手順](../verification/README.md)が人間の入口です。長大な既存MV matrixは履歴・詳細であり、新候補の結果を二重編集しません。

## 正本と役割

[GitHub Work Management Rules](work-management.md)を作成・triage・完了時に適用します。Issueは具体作業、Projectは全体管理、Milestoneは到達目標、native Relationshipは依存/分解。Standaloneに架空の親や依存を要求しません。

Issueは目的・受入・担当・依存・次の操作、PRは差分・固定HEAD/base・レビュー・CI・統合判断の正本です。
QA JSONはCaseと候補結果、生成Markdownは閲覧用です。過去runのpassは別buildを保証しません。

PM/進行役は統合順・claim・GitHub設定を管理します。実装担当は1 Issue・1 branch・1 worktree・1 writerです。
独立レビュアーは別sessionで固定SHAを読み、GUIは操作しません。GUI担当はhost/OS sessionに1名の指定GPT、または人間引継ぎです。
モデル名ではなく公開可能なsession IDで識別します。公開Issue/PRへhost名、ローカル絶対パス、token、private rawログを出しません。

## 参照する範囲と進行判断

対象Issue/全コメント、関連PR、Projectの担当範囲と対象Milestone、作業tree/baseは開始・引継ぎ時に確認する。技術資料は下表で選ぶ。既に読んだ同一版は再利用し、baseや対象要件の変更・矛盾・不足がある部分を読み直す。必須の受入/所有/承認照合を省く意味ではない。

| 変更・判断 | 参照する正本 |
| --- | --- |
| 新機能・連携方式の選定 | Mission、ACP First、該当要件、最新の公式能力比較 |
| 製品コード・保存・イベント境界 | 該当要件と現行実装/保存/イベント契約、呼出元と呼出先 |
| docs・workflow・設定 | 変更する規約と消費側。製品に関係しない修辞修正で全設計を読み直さない |
| 検証・配布 | Change Impact、対象Case。build/配布/GUIを実施する場合だけ対応runbook |
| Session追加・引継ぎ・merge・cleanup | Codex実行規約、PR automation、Work Managementの該当工程 |

依頼の完了を、成果物・必要検証・統合先・残QA/cleanupで具体化する。依頼済み範囲の実装、原因修正、必要テスト、レビュー準備は初版で止めず進める。テスト/レビューの追加・再実行は変更・失敗・未解決懸念に対応させ、同じ入力の成功確認をPMと各担当で重複させない。自動gate/hookの必要実行と、HEAD/base/target/本文/Issue条件/feedback変更時の承認失効は維持する。

### 承認と人間待ち

- 現セッションのユーザー指示と既存承認から対象/操作/範囲を照合する。同じ承認範囲の通常作業は再質問しない。PM割当は元依頼の範囲を具体化し、元依頼にない公開/破壊操作への許可を作らない。
- 短い「承認します」は対応する具体的な質問を確認して扱う。別Issueの承認、要約内の承認済み表記、自動goal継続、ローカル承認ファイルだけで対象操作への許可を補完しない。元発言/質問と対象が確認できればその承認を継承する。公開記録には必要な範囲だけを書き、private原文を転記しない。
- 不可逆な削除、サーバー保護変更、非公開データの公開等で既存許可が不足する場合は、依存しない準備/検証を先に完成し、具体的な対象・効果・復元可能性を示して不足分だけを尋ねる。
- 自動承認レビューに拒否された操作は停止する。許可/低リスクの根拠を追加確認できればその根拠を添えて再評価を求める。根拠が増えなければ同じ試行や別担当・別tool経由で迂回せず、拒否された操作と理由を明示して必要な承認を尋ねる。指示改訂やローカル記録は拒否の解除手段ではない。

### 担当と継続

PMは依存と共有ファイルで順序を決め、既存Sessionへ役割・対象Issue・scope境界・成果物・次の引継ぎを渡す。独立性がある実装/調査/レビューは並行し、同じ共有コードのwriterは直列にする。子Agentの可否はCodex実行規約を維持する。

writer停止はそのbranchの編集停止であり、PMの全進行停止ではない。enroll後は実際のcoordinator/session/job handleを確認し、実行・引継ぎ成功を追う。PAUSED heartbeatを再開せず、実行経路がない場合はその制約と次担当を記録する。観測timeoutを終了と判断せず、同じhandleを再確認する。状態更新がない待機を短い間隔で繰り返さず、利用可能なwait/backoffを使う。

一部が人間待ちでも、他の受入に必要な実装・合成fixture・依存取得・CLI検証・レビュー・QA手順を権限内で進める。通常の依存取得を実施する場合は接続先/書込先を確認し、GUI操作・SDK変更・認証変更の許可と混同しない。共通blockerで統合が止まる場合も、完成した成果を保持し独立作業へ進む。完了扱いで隠さず、全ての残経路が人間操作を必要とするかは実状態から判断する。

## 開始

1. AGENTS.mdと本節の共通契約を確認し、上の参照表から作業に必要な正本を選ぶ。対象Issue全コメント・関連PR・Project/対象Milestoneを確認し、旧#1は必要な判断履歴だけ参照する。
2. `git status --short --branch`、`git worktree list`、`git fetch --prune origin`、HEADと意図したbaseの差を確認する。既存編集をpull/stash/resetに巻き込まない。
3. [Governance Audit](git-governance-audit.md)のread-only statusと今回の影響から発火条件を判断する。対象なら監査Issueへまとめ、通常のPRごとに全監査しない。重複Issueを検索し、必要な具体作業のIssueを作る。[作成/triage確認](work-management.md#issue作成triageの確認)に従いProjectへ追加、Milestoneを選定、実際の親子/依存をnative設定する。関係なしはStandaloneと明示し、独立した実装やGUI検証を分ける。
4. claimを投稿して読み戻す。未解放claimは時間で失効しない。同一Issueの最小コメントIDの有効claimだけがwriterになる。競合者は開始せず撤回する。複数Issueの共通ファイルはPMが境界/順序を決める。
5. 通常はorigin/developから `<codex|claude|cursor>/<Issue>-<slug>` と専用worktreeを作る。main toolingはorigin/mainから、promotionは固定candidateから作る。初期upstreamを解除し `bash scripts/workflow/bootstrap.sh` を実行する。
6. 最初の意味あるpushでDraft PRを作成する。PR本文はテンプレートに従い、`Issue`、`Integration`、`Verification`、`GUI`、`GUI reason`を記録する。source JSONはGUI不要変更にも必須で、理由・CLI検証を含む。

claim例（パスとhostはprivate local registryだけ）:

```text
status: in-progress
owner: gpt-issue-session; issue: #N; parent: <actual parent or none>
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
- [Change Impact](change-impact.md)の共通コマンドで必要テストを実行する。Knowledge/Metadataだけは重いコード検証をskipし、混在/unknownは必要な検証を維持。パッケージ/GUI buildの明示要求はbuildPluginも実施する。
- 別sessionが固定HEAD/baseの差分と受入を確認し、`reviewer session / reviewed SHA / base / findings / disposition`を記録する。同一GitHubアカウントのApproveだけでは代替しない。
- 重大/中程度のコード指摘を解消し、再レビューする。GUI状態と実装scopeを分け、未実施だけをコード欠陥としない。
- writerを停止して[自動進行役](pr-automation.md)へopaque IDでenrollする。登録後は同じbranchを編集/commit/pushせず、coordinatorに任せる。既存PAUSED heartbeatを勝手に再開しない。

## 統合・完了

進行役はtarget更新を通常mergeし、テスト・非force push・独立再レビューを行います。HEAD/base/target/本文/Issue条件/feedback変更は承認を失効させます。
最新 `test` / `PR policy` / `Agent review` / `Acceptance gate` がsuccess、会話解決済みであることを確認します。CI未実行・失敗をローカル成功で代替しません。

developではCase不足を拒否しますが、GUI passを要求しません。未実施/blocked/failと次の操作を保ち、`Refs #N`を使います。
coordinatorは実装受入完了を確認後、QA Issueを先に作成/再利用し、双方向linkと全Caseのreadbackを確認して元実装Issueをstatus:doneでcloseします。GUI不要でもmain未反映分はQAのmain反映マトリクスへ引き継ぎます。失敗時はcloseせず再試行します。親tracking/researchやQA自体は子PRだけでcloseしません。検証担当は区切りで一覧をまとめて実施します。

mainは[固定候補手順](../verification/README.md)で範囲全体を確認します。候補後に許す差分はpromotion JSONとそのpromotion IssueのCase JSONだけです。
一部Caseだけのpassや未確認製品commitをQA文書変更へ偽装することはgateが拒否します。main先行tooling/前回QA記録は候補固定前に専用develop同期PRへ取り込みます。
promotionはmerge commitに限定し、GitHub APIのHEAD指定とstrict baseを通します。merge直前にmain/develop refを再取得します。
main/developへの直接commit/push、admin bypass、hook無効化、force push、`--no-verify`は禁止です。

統合・終了時も[Governance Audit](git-governance-audit.md)のcount/主要変更/Milestone完了を判断する。
merge SHA・CI・Case結果・残条件をIssueへ記録し、受入を個別に満たす範囲だけcloseします。promotion Issue完了でも元の機能/QA Issueを一括closeしません。
PMは[Issue終了時の整合確認](github-projects.md#issue終了時の整合確認)で元Issue・QA・親の現行表示・Projectを読み戻します。coordinatorのdoneはProject同期の完了ではありません。未反映は対象・担当・再試行条件を元Issueへ残します。
cleanupは自分のclean/停止確認済みIssue branch/worktreeのみ。main/master/developはremote/localとも削除しません。他担当の変更/branchを整理しません。
merge/Issue closeとcleanup完了を分け、[ブランチ残存の判定と完了確認](pr-automation.md#ブランチ残存の判定と完了確認)に従って実ref・追跡ref・worktreeを照合します。残す場合は理由・担当・次の操作を引き継ぎます。

## 中断・再開

引継ぎはowner、状態、branch/target、HEAD、PR、未commitの有無、テスト、Case状態、次の操作、依存、claim継続/解放を公開可能な範囲で記録します。
新担当は旧ownerの解放またはPM/人間の明示再割当の後にclaimします。再割当は旧コメントID、停止確認、新ownerを記録し、`supersedes claim <ID>`と明記します。
応答がないだけで横取りしません。誤ってmain/developへcommitした場合はpushせずSHAをbranchで保全し、専用PRへ移します。自動resetは禁止です。

## 自動gateとGitHub設定

- push前は[共通Change Impact](change-impact.md)が選んだテストを実行する。required `test` は常に起動し、安全なskip判断もsuccessと理由を記録する。ZIPはpush後の [Branch Plugin ZIP](plugin-zip-delivery.md) が同じ分類で必要なときに標準 `buildPlugin` で生成し、ブランチごとのReleaseへ最後に実buildした1件を保存する。ローカルのZIP生成・取得・cacheはpush/checkout hookでは行わない。
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

## Issue命名・分類（2026-09-08 / #96）

題名はtypeに対応する `[機能]` / `[修正]` / `[調査]` / `[試験]` / `[運用]` / `[追跡]` と簡潔な要約。QAは `[試験] #元Issue番号 要約`。優先度は題名に重ねません。
必須labelは各軸ちょうど1つ: `type:feature|bug|research|qa|maintenance|tracking`、`priority:P0|P1|P2`、`status:ready|in-progress|review|blocked|deferred|done`。closedはdone、openはdone以外。本文に受入・依存・次操作を記載し、PR policyが命名と3軸を検査します。
元実装に未実装受入が残る場合は別実装Issueに分離・linkしてから実装完了を判定します。単にPR scopeが済んだだけではcloseしません。元IssueのcloseはGUI pass/main反映済みを意味しません。QA Caseと固定候補promotion gateは従来どおり維持します。
