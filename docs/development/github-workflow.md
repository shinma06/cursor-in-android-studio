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

未解決コードレビュー指摘やテスト失敗をdevelopへ通す方針ではありません。製品failには専用修正Issue/branch/PRが必要です。Agentと人間の適切な実観察はどちらも有効ですが、未確認をpassに変更しません。

[今回の確認一覧](../verification/current.md) / [正本JSONと固定候補の手順](../verification/README.md)が人間の入口です。長大な既存MV matrixは履歴・詳細であり、新候補の結果を二重編集しません。

## 正本と役割

[GitHub Work Management Rules](work-management.md)を作成・triage・完了時に適用します。Issueは具体作業、Projectは全体管理、Milestoneは到達目標、native Relationshipは依存/分解。Standaloneに架空の親や依存を要求しません。

Issueは目的・受入・担当・依存・次の操作、PRは差分・固定HEAD/base・レビュー・CI・統合判断の正本です。
QA JSONはCaseと候補結果、生成Markdownは閲覧用です。過去runのpassは別buildを保証しません。

開発client/providerだけで職務を固定しません。必要な能力・実際に利用できるtool・許可範囲・claimに、下記のユーザー指定モデル条件を合わせて担当を選びます。GPT/Codex・Claude・Cursorの併用は必須ではなく、clientが利用できるだけでは担当資格になりません。

| 役割 | 入力 → 出力・完了責任 | 兼任・独立性 |
| --- | --- | --- |
| PM/進行役 | Issue/Project、既存担当/実行handle、依存・負荷・承認 → 優先順位、有限の枠、割当、blocker/次操作、成果回収と最終readback。登録だけで進行済みにしない | 統合管理を兼任可。ソース実装・指摘修正・独立レビューを兼任しない |
| 実装・指摘修正担当 | claim、scope、base、受入/指摘 → 1 Issue・1 branch・1 worktree・1 writerで変更、必要テスト、Case、固定HEAD/base、clean/停止と引継ぎ。修正後は新HEADの再レビューへ戻す | 調査・CLI検証、権限内のbase同期/統合操作を兼任可。PMおよび自分の差分の独立レビュアーと分離 |
| 独立レビュアー | 固定HEAD/baseの全差分、Issue/Case、指摘 → reviewed SHA/base、受入判定、findings/disposition、未確認と証拠。変更があれば旧承認を流用しない | 実装者/PMと別の独立top-level session。レビュー中はread-only、ソース変更・GUI操作をしない。同じproviderでも可、同じsessionの自己レビューは不可 |
| 統合担当 | writer停止、固定版レビュー、依存順、現行baseと必須checks → 通常mergeによる同期を実装担当へ戻すか権限内で実施、必要テスト/非force push/再レビュー、順序付きmerge、QA・Project・cleanupの回収 | PMは統合条件とGitHub操作を管理可。競合解消・ソース修正は実装担当へ戻す。同期/修正したsessionはその差分を独立レビューしない |
| GUI QA担当 | 未達Case、識別build、能力/許可とhost lease → 実観察、pass/fail/環境blocked/未実施、証拠とlease解放・次担当。製品failは修正Issueへ | 指定sessionまたは明示引継ぎ済み人間。host/OS sessionごとに単一operator。実装兼任でも独立コードレビューの代替にならない |

### 役割別モデルと実設定の確認（#527）

2026-10-05のユーザー指定。性能の一般順位ではなく、現ラインナップで本プロジェクトが許可する割当範囲です。

| 役割 | 許可範囲・通常割当 |
| --- | --- |
| PM・独立レビュー | GPT-6 Astra High以上。通常 `gpt-6-astra` / `high`、必要理由があれば同モデルの対応する上位effort |
| 実装・指摘修正・調査等（AgentのGUI QAを含む） | GPT-6.1 Sol High以上〜GPT-6 Astra High以下。通常 `gpt-6.1-sol` / `high`、難度/失敗に根拠があれば `gpt-6-astra` / `high`へ変更。範囲内の別設定もPMが根拠と利用可否を確認する |

割当前・新規/再開・役割変更・指摘修正・統合/GUI待ちからの再開で、PMはrequested model/effortと実際のsession設定・直近turn/実行記録を照合する。prompt中の自己申告、clientの既定値、旧turnの設定だけで確認済みにしない。役割を兼ねる場合は双方のモデル条件を満たす。モデル追加/廃止時はユーザー指定範囲を再評価し、旧順位を別モデルへ移さない。

設定不明・不一致・利用不可なら当該担当の実行/成果採用を停止し、原因・確認担当・解除条件を記録して独立可能な仕事へ進む。範囲外への黙ったfallback、設定未確認のpass、writer所有の付け替えを行わない。実行後のreadback不一致では未公開差分を保持し、修正/再実行と独立レビューを経るまでcommit/pushへ進めない。
claim/引継ぎ/レビューの既存記録に、公開可能な担当参照・role・requested/actual model/effort・確認根拠・scope・状態・次操作を残す。rawログや実session ID/host/pathはprivate記録へ、公開側は必要な要約だけにする。新しいモデル台帳や必須checkを増やさない。[Codex実行規約](codex-execution-policy.md)のAstra子Agent禁止と独立top-level条件、上位の権限制約を優先する。

GUI toolがない担当は実装・CLI検証を進め、GUIだけを対応可能な担当へ引き継ぎます。対応する実行経路が確保できなければGUIはblockedです。`required_execution: computer_use` は人間の直接操作で代替できません。Cursor IDEとプラグインを比較するfixture課題は製品の試験であり、開発担当としてのCursorをfixture専任にする規則ではありません。

上の兼任範囲でも単一writer/GUI leaseを守ります。担当資格と自動連携の実装済み範囲は別です。[PR自動進行](pr-automation.md)の実際の起動経路を確認し、全クライアントにGUI操作やcoordinator接続があるとは仮定しません。#135のAstra実行制限も維持します。
モデル名ではなく公開可能なsession IDで識別します。公開Issue/PRへhost名、ローカル絶対パス、token、private rawログを出しません。

### 担当と継続

PMは以下の順で自律的に編成を再評価する。ユーザーから委任済みの割当/再利用は再質問しないが、委任から外部送信・破壊操作等の新しい許可は作らない。「最適」はこの根拠付き判断であり、未測定の速度/費用/品質改善や数学的最適性を主張しない。

1. **照合**: 開始/再開、成果固定、レビュー終了、統合、失敗、承認/質問/GUI待ち、資源解放で、Issue/PR/Projectと既存owner/source/registry、session/job handle、成果物HEAD/baseを読む。担当不在、停止、結果未回収を区別する。timeout/無応答は停止やclaim解放の証明にならない。
2. **回収と再利用**: 未回収結果と固定PRのレビュー/修正/統合を先に回収する。同じscopeへ二重割当しない。既存の適格な空き担当を優先して再利用し、model/effort・独立性・所有を再確認する。旧writerの停止/解放または明示再割当なしに交代しない。
3. **有限の枠**: ready件数、難度/責務、依存、共有UI/ファイル、レビュー滞留とCPU/メモリ/重いbuild/GUIの実負荷から、今回の上限と理由を既存の作業記録へ残す。初期上限はPM/統合管理1、実装/修正1、read-only独立レビュー最大2、共有UI writer1、重いbuild1、host GUI operator1。全枠の常時起動は不要。PMは独立scopeと負荷の余裕を確認した場合だけ有限の上限を改め、未知負荷では増やさない。GUI leaseの排他は増枠不可。
4. **次の割当**: ブロックされない依存先の解消と固定版のレビュー/修正/統合を優先し、同優先度なら既存待ち順を使う。必要な役割・model/effort・scope・成果・次担当を渡して実handleを確認する。空いた担当へ独立した次作業を渡し、readyがなければ待機/終了へ戻す。追加sessionには合理的理由が必要。実装だけを増やさず、同じ共有コードのwriterと重いbuildを直列にする。
5. **状態の報告**: GUI失敗/環境blocked、承認待ち、質問待ちは発生時にユーザーへ通知し、既存Issue/PRのblocker・担当・必要な解除条件・次操作を更新する。次回報告にも既報blockerの現在状態（継続/解消/移譲）を含める。独立作業へ切替え、何もなければ解除条件を明示して待機する。理由のない再試行や無限の増員をしない。

待ち行列は既存Issue/PR/Projectとcoordinatorの状態を使い、新しい恒久台帳/daemon/heartbeatを作らない。短期の報告表は正本への参照に留める。枠の値はAgent起動の許可ではなく、#135とtool権限・ユーザー承認の範囲内でのみ適用する。

#### 子PRと親PRの順序

子PRを固定 → 独立レビュー → 実装担当の指摘修正/必要検証 → 新HEAD/baseの独立再レビュー → 現行4 checks/会話解決を確認 → 依存順にmergeする。各子のbase変更でも旧承認は失効する。親writerは子の統合を読み戻して最新baseへ通常mergeで同期し、必要テストを実行して親全差分と全受入を固定し直す。親の旧レビューや子の合格だけで親を承認しない。stacked PRは依存base/merge順を明示し、base切替後に同じ再レビューを行う。

#### 有限の運用例

机上評価は実session実行やGUI passと区別し、対象Issue/PRへ結果を残す。実操作は割当/許可と既存gateがある範囲だけで行う。

| 入力/契機 | 再評価後の次操作 |
| --- | --- |
| ready 1→複数、共有UI差分が重なる | 既存writerを再利用、競合分は待ちへ。独立scope/負荷の余裕がなければ増員しない |
| ready 複数→0、担当完了 | 結果を回収・readbackし、担当を待機/終了へ。固定レビュー/QA/cleanupが残れば先に次担当へ |
| 同scopeに有効claim、担当無応答 | 二重割当を停止。同handle/成果を確認し、停止と解放/再割当が揃うまで横取りしない |
| 担当停止または結果未回収 | 既存成果を保持・回収、次担当/解除条件を明示。再開時は役割/モデル/所有を再確認 |
| 子の依存解消、親base更新 | 子のmerge readback→親同期/テスト→親全差分再レビュー。旧承認を失効 |
| 固定PRがレビュー未割当で滞留 | 適格な既存Astra High reviewerを再利用。最大2のread-only枠を検討し、writerを増やす前に回収 |
| 重いbuild実行中/メモリ圧迫 | 次buildを待ちへ。CLI/固定版read-onlyレビューへ切替え、終了/負荷解放で再選択 |
| GUI fail/承認待ち/質問待ち | 即時通知、既報状態を追跡、独立作業へ切替え。依存作業は具体的解除条件付き待ち |
| モデル設定不明/範囲外/利用不可 | 当該役割の実行/成果採用を停止。適格担当の確認済み再利用か設定回復を待ち、黙ったfallbackを拒否 |

## 個人情報・非公開情報の公開前確認

GitHubへpush・投稿・編集・添付・配布する担当は、送信する差分/本文/成果物とそのメタデータに、個人情報や公開してはいけない情報が含まれていないかを送信前に確認する。Issue/PR（MR相当）のtitle/body、コメント・レビュー、画像/動画、Actionsログ/summary/artifact、Release本文/asset、Wiki/Discussions、Project/Milestone等も対象とする。

- **個人情報・環境情報**: 氏名、個人メール、住所、電話番号、顧客/利用者識別子、端末ID、host名、ローカルユーザー名・絶対パス、社内URL/IP等。commit author/committerのname/email、commit message、branch/tag名、画像に映った通知/アカウント、ファイルのメタデータも確認する。
- **認証情報**: token、API key、password、秘密鍵、署名用keystoreと資格情報、cookie/session、接続文字列、`.env`や認証設定、ログ内のAuthorization header等。テスト/fixtureの値も実資格情報でないことを確認する。
- **非公開情報**: 顧客・業務データ、私的な会話/実wire/rawログ、未公開仕様・ソース・契約・内部資料、公開承認待ちの証拠や成果物。既存の#146等の公開承認範囲を維持する。

情報の用途・公開承認・対象repository/resourceの閲覧範囲を照合する。private repositoryのソース/投稿/添付にも秘密や不要な個人情報を保存しない。明示的に公開承認された著者情報等や実データを含まない合成値は、根拠を確認して扱い、一律に漏えい扱いしない。ただし本規約が公開記録への記載を禁止するhost/ローカル絶対パス/token/private rawログは維持する。不明なら当該情報の送信を保留し、安全な残作業を進める。

必要な説明は伏字化・合成データ・repository相対パスで置き換え、原本は承認済みの非公開保管先だけに残す。[GitHub noreply email](https://docs.github.com/ja/account-and-profile/reference/email-addresses-reference)等の公開可能なcommit identityを確認する。`.gitignore`だけに依存せず、staged diff、commit metadata、送信内容と生成物を実際に確認する。

既存GitHub上の情報は[定期監査の情報公開確認](git-governance-audit.md#個人情報非公開情報の監査)で点検する。漏えい候補を見つけた場合も同節の報告・対応境界を使い、公開Issue/PRへ問題の値やraw証拠を転載しない。

## 参照する範囲と進行判断

対象Issue/全コメント、関連PR、Projectの担当範囲と対象Milestone、作業tree/baseは開始・引継ぎ時に確認する。技術資料は下表で選ぶ。既に読んだ同一版は再利用し、baseや対象要件の変更・矛盾・不足がある部分を読み直す。必須の受入/所有/承認照合を省く意味ではない。

| 変更・判断 | 参照する正本 |
| --- | --- |
| 新機能・連携方式の選定 | Mission、ACP First、該当要件、最新の公式能力比較 |
| 製品コード・保存・イベント境界 | 該当要件と現行実装/保存/イベント契約、呼出元と呼出先 |
| docs・workflow・設定 | 変更する規約と消費側。製品に関係しない修辞修正で全設計を読み直さない |
| 開発コンテキストの新規作成・更新・レビュー・監査 | [知識の正本の共通規則](../architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件)。用途/言語/形式/配置/読込条件/証拠と正当な例外を照合し、製品契約を変更しない |
| 検証・配布 | Change Impact、対象Case。build/配布/GUIを実施する場合だけ対応runbook |
| Session追加・引継ぎ・merge・cleanup | Codex実行規約、PR automation、Work Managementの該当工程 |

依頼の完了を、成果物・必要検証・統合先・残QA/cleanupで具体化する。依頼済み範囲の実装、原因修正、必要テスト、レビュー準備は初版で止めず進める。テスト/レビューの追加・再実行は変更・失敗・未解決懸念に対応させ、同じ入力の成功確認をPMと各担当で重複させない。自動gate/hookの必要実行と、HEAD/base/target/本文/Issue条件/feedback変更時の承認失効は維持する。

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
owner: implementation-session; issue: #N; parent: <actual parent or none>
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

mainは[固定候補手順](../verification/README.md)で範囲全体を確認します。候補後の直接編集はpromotion JSONとそのpromotion IssueのCase JSONだけです。後続main文書/toolingの同期は[限定検証](../verification/README.md#候補固定後のmain同期533)を通し、製品・build・Caseの固定条件を維持します。
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

## main起点の限定候補（#408）

モダン化だけをmainへ取り込むユーザー承認済み経路は[限定取り込み手順](main-scoped-release.md)に従う。trusted mainに先行保存した計画の全必要Caseを同一候補/ZIPで確認する。develop全体のpromotion、GUI不要tooling、4必須checksと独立レビューは維持する。
