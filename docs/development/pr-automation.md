# PRの自動レビュー・修正・統合

trusted mainの `agent_loop.py` が、明示登録された同一repository/maintainerのIssue PRだけを処理します。
#83以降はdevelopとmainの[target別gate](github-workflow.md)を使います。既存heartbeatはPAUSEDのままです。設定変更だけで再開しません。

役割の選定・実モデル/effortの照合・有限の枠/待ち行列の再評価は[共通の担当条件](github-workflow.md#正本と役割)に従います。現行の`agent_worker.py`はCodex用の起動実装です。Claude/Cursor担当を自動起動・接続するadapterがあるとは扱いません。別の独立top-level sessionでレビューを行う場合も、固定HEAD/baseの証拠と既存gateを通してPM/coordinatorへ引き継ぎます。

## 引継ぎ

専用worktreeでテスト・push・Draft PRを作成し、**元writerの編集/commit/push/GUIを停止してから**登録します。

```bash
python3 /path/to/trusted-main/scripts/workflow/agent_loop.py enroll \
  --pr 123 --source /path/to/issue-worktree --owner implementation-session \
  --scope scripts/workflow/ docs/verification/ --writer-stopped
```

`--parent`は[実際の作業分解の親](work-management.md)があるときだけ指定し、Standaloneは省略します。#1を既定の親にしません。

mainの`--close-issue`は全Issue受入完了時だけ指定します。developのfeature/bug/maintenanceは実装受入完了とQA引継ぎ成功後に元Issueをcloseします。reviewのissue_completeはdevelopでは未実装受入が残らないこと（明示的な別Issueへの分離を含む）、mainでは全受入完了を意味します。
PRのIssue番号、作者/repository、main/develop target、Integration/Verification metadata、clean source/HEAD一致を検査します。
公開marker `agent-loop-handoff:v2` にはopaque `registry_id`、PR/Issue/HEAD/base/target、scope、owner、停止宣言だけを保存します。
source絶対パスとhostはprivate `.git/agent-loop/registry/` に0600で保存し、公開record全体のdigestで結び付けます。
別host、registry欠落/改変/権限不整合はfail-closed。勝手なowner/source取り替えやregistry再生成はしません。原ownerの復旧確認が必要です。

旧v1は同一hostの既存登録を読み取る互換性を維持しますが、新規登録では使いません。v2があるのにregistryを解決できない場合、v1へfallbackしません。
既存marker/owner/sourceは自動移行しません。公開進捗ではhost/sourceを除去し、例外のraw診断はprivate localログへ保存、公開側はopaque診断IDだけを示します。

## 工程

```text
queued → reviewing → reviewed
                   ├ changes_requested → fixing → publishing → reviewing
                   ├ blocked → 停止/具体的原因を確認
                   └ approved → acceptance待ち / CI待ち → merging → cleanup → done
```

reviewerはread-onlyの独立session、fixerはworkspace-writeで指定ファイルのみです。現行CLI workerはreview=`gpt-6.1-sol`/`high`、fix=`gpt-6.1-sol`/`high`を明示し、個人の既定model/effortを流用しません。CLI起動headerの実設定と`thread.started`→終端`turn.completed`の実originを別streamで照合してreportへ保存し、未確認/不一致/利用不可なら成果採用を停止します。旧レビューに設定証拠がなければ再利用せず停止し、PMが適格な独立担当へ戻します。起動headerの形式変更も未確認として停止し、モデルの自己申告は代替にしません。設定を確認できないfixerのdirty差分は保持して停止し、通常tick/resumeだけでcommit/pushへ自動回復しません。`fixing`は未確認で復旧対象外です。workerが正常終了して実設定照合と履歴確認を終えた後だけ`publishing`を保存します。timeout・通信/起動/結果読込等の例外とcontroller停止/再起動でも、この境界より前の差分は採用しません。PMが実装担当へ差分確認・適格設定での再実行/検証を戻し、独立再レビューを行います。現在の担当とは別の独立top-level実行かどうかの確認は、CLI引数だけでは証明できません。GitHub token/設定MCP/GUI権限を渡しません。
GPT-6 Astraがメインの場合は[Codex実行規約](codex-execution-policy.md)に従い、reviewer/fixerを現在のAgentの子として生成・委譲してはいけません。独立top-level Sessionとの連携かどうかを実行構造で確認し、workerという名称や自動化スクリプト経由を禁止回避の根拠にしません。
GUI未実施/環境blocked/製品failだけを理由にdevelopのコード承認を拒否しません。コード不具合、必要テスト失敗、Case追跡不足は修正対象です。
PMが選んだ有限の対象を、既存coordinatorは最大3 PRで交代制に処理し、worker 10分、fix 3回、review 8回、通信等失敗3回で停止します。GUI待ちPRだけで全体を止めません。この3 PR上限はsessionやbuildの同時枠ではなく、PMの実負荷判断とGUI leaseを代替しません。

coordinatorはtarget refをAPIから取得し、fetchと再照合します。HEAD/base/target/題名/本文/Issue条件/feedback変更は承認を失効させます。
base同期は通常merge → [共通Change Impact](change-impact.md)の必要テスト → 非force push → 独立再レビューです。知識のみのskipも判定結果を残し、dirty/remote HEAD/受入の照合は省略しません。未解決会話とstrict baseはGitHub保護も検査します。
`test` / `PR policy` / `Acceptance gate`成功と独立レビューを読み戻し、merge直前に受入を再検証して `Agent review` を発行します。

- develop: 必要Case JSONと次の操作、製品failなら修正Issueが必須。GUI passは要求せずsquash merge。
- main tooling: GUI不要の理由/CLI検証、許可されたtoolingパスだけ。
- main promotion: `main..candidate` の全commitをmerge済みdevelop PRへ完全一意に対応付ける。通常はsquash結果、既存main同期mergeは[厳密なDAG照合](../verification/README.md#既存main同期mergeの履歴照合250)を通す。各PRの固定merge SHAに保存された全Caseが同じ候補/buildでpass。候補後の直接編集は2つの許可JSONだけ。後続mainの文書/toolingは[限定同期検証](../verification/README.md#候補固定後のmain同期533)を通す。merge commit専用。

[確認結果の入力と候補固定](../verification/README.md)を参照してください。1 Caseだけのpassは全候補の合格ではありません。
GUI lease付きの旧 `gui` 証拠登録は旧記録/互換用途に残しますが、promotionは候補の`promotion.results`を使用し、PR HEADの再GUIを要求しません。
GUI操作自体は現在も能力・許可を確認した指定担当/人間のlease下で行います。状態のblockedと製品failを区別し、未実施はpassへ変えません。

## 対象branch変更・再開

GitHubでPRのtarget/metadataを変更しただけでは既存enrollmentを自動で移しません。既存登録はunmanagedとして停止します。
PMが停止・clean・同一HEAD/branch/Issueを確認した場合だけ、trusted scriptで明示更新できます。

```bash
python3 scripts/workflow/agent_loop.py rebind-target --pr 123 --writer-stopped
python3 scripts/workflow/agent_loop.py scan
python3 scripts/workflow/agent_loop.py tick --pr 123
python3 scripts/workflow/agent_loop.py resume --pr 123 --reason '停止原因の解消とworker停止を確認'
```

`rebind-target`は担当関係/source/expected HEADを継承し、公開owner表記をホストを含まないagent-loopへ正規化します。scopeの移譲や外部pushの採用には使いません。worker記録が残る/dirty/HEAD不一致なら拒否します。
旧承認/GUIを失効させ、新targetをv2 registryへ保存します。PAUSED状態やheartbeatを勝手に解除しません。

承認済みディレクトリ移転で旧sourceが消えた場合、同じwriterの移転先を明示します。旧sourceの不存在を黙認して再登録しません。

```bash
python3 scripts/workflow/agent_loop.py rebind-target --pr 123 --writer-stopped \
  --source /path/to/relocated-issue-worktree \
  --migration-record /path/to/private-directory-migration.json
```

移転先は同じrepositoryの登録linked worktreeで、元の登録branch/HEAD・clean状態を保つ必要があります。
旧パスが存在する場合は同じ実体だけ許可します。不存在の場合はverified移転記録のold/new、kind=worktree、moved=trueと、
before/afterのbranch・HEAD・空status・現在と同じinodeを照合します。移転記録はPMが実体移転時に確認したprivate JSONを使い、
この操作のために別worktreeを同一と見なす記録を作りません。sourceは元の登録HEAD、managed checkoutはexpected HEADのまま別々に照合します。
移転先もprivate registryだけに保存し、公開markerへパスを含めません。registry喪失の復旧、別writerの採用、外部pushの取り込みには使えません。

registry喪失、旧writer再開、予期しないcommitやdirty内容は保持して停止します。reset/stashで捨てず、通信切断後のpublishは記録済みSHAだけを再送します。

## 完了とcleanup

GitHub mergeを読み戻します。developでは固定mergeのCaseを読み、残CaseがあるときだけQAを作成/再利用します。新規QAはcanonical `test: #N 日本語の要約`、再利用は旧`[試験] #N `と新形式の元番号を厳密に照合します。重複・closed・所有/目標の矛盾は上書きせず戻します。元Case JSONの全文コピーをQAへ増やさず、固定merge/pathと全必要Case ID・手順/期待/次操作の正本を示し、native親子・内容と元Issue backlinkをreadbackしてから元実装Issueを閉じます。CaseなしならQAを作らず、元IssueとPRへPMのmain反映保管・未確認と次操作を記録し、両端readbackします。既存QAがあるno-Cases変更は自動処理せずownerへ戻します。API失敗/不一致ではcloseしません。既存QA本文/原失敗・履歴は保持します。
mainで全受入済みのpromotion Issueは新candidate証拠を含む受入判定でclose可能です。元の機能/QA IssuesはPMが残条件を個別照合します。
remote branchはmerge対象HEADと一致、localは登録時HEADと一致・他worktree未使用・clean・worker停止・GUI lease空きの場合だけ削除します。
**main/master/developはどのcleanup経路でも削除しません。** `--force-with-lease`は一致確認付きIssue branch削除だけの限定使用です。
cleanup中断は次tickで再試行し、merge成功だけで状態を消しません。

`done`の次操作はPMへの[Issue/QA/親/Project整合確認](github-projects.md#issue終了時の整合確認)を含みます。coordinatorはProjectへ自動追加せず、developのQA分離で親全体の受入を自動チェックしません。PMは元IssueとQAのProject登録・Status、親の現行参照/分割先、主要変更時の概要を確認して、読み戻し結果または未反映対象・担当・再試行条件を元Issueへ記録します。Project障害だけで受入完了済みIssueを開き直したり、未達QAを閉じたりしません。

```bash
python3 scripts/workflow/agent_loop.py cleanup-branches
python3 scripts/workflow/agent_loop.py cleanup-branches --apply
```

#83導入PRは [bootstrap手順](github-workflow.md#83の一回限りのbootstrap)に従い、旧enrollを使わずPMへ引継ぎます。
通常運用で必須gateを省略する手動経路は作りません。

## ブランチ残存の判定と完了確認

2026-09-10 / #165。更新日時や`[gone]`だけで実ブランチを削除しない。調査では次を区別する。

| 対象 | 判定と処置 |
| --- | --- |
| `origin/*`のremote-tracking ref | `git ls-remote --heads origin`と照合。remoteで削除済みなら固有commitと実PR/所有を確認し、必要な保全・実復元後にだけpruneする。実branch/worktreeの削除ではない。 |
| remote/localの実branch | PRのmerged/closed、固定HEAD、取り込み先、claim、dirty/未追跡、worktreeでの使用を確認する。squashは`--merged`だけで判定せずPR HEADも照合する。 |
| detached worktree | app/reviewer/GUI fixtureの用途・担当・未保存データを別に確認する。branch一覧にないことや古さだけでは削除しない。 |

通常fetchは`git fetch --no-prune origin`とし、自動prune設定も上書きします。tracking-only refが最後の未統合commitかもしれないため、`[gone]`だけで削除しません。`--prune-tags`は使いません。mergedとclosed-unmerged/withdrawn/supersededを分け、後者は元指摘・replacement・所有/停止・全refs/dirty/untracked/必要ignored成果物を確認します。退役する固有内容は削除範囲外の既存archive/snapshot/bundleに不変保管し、hash照合だけでなく使い捨て復元先へのfetch/checkout・固定tree/必要ファイル照合まで行います。QA/再開/復旧に必要な資源は残し、owner・理由・再開条件を元Issue/PRへ記録します。新しい保管frameworkや一括purgeは作りません。

PM/coordinatorはmerge後の区切りで以下を確認し、Issue/PRへ結果を残す。

1. **統合**: PRのmerge SHA、受入、必要なQA引継ぎを読み戻す。
2. **後片付け**: 既存の所有確認付きcleanupを実行し、保全済みtracking refの限定整理後にremote/localの対象branch、`origin/<branch>`、登録source/managed checkoutの残存を確認する。終了報告は削除済み/保留を分ける。既存`cleanup-branches`は補助監査であり、使用中worktree・remote実ref・未登録/旧形式・mergedでないPR等をすべて整理する仕組みではない。
3. **保留**: branch、固定HEAD、残る資源の種類、理由、owner、次の操作/再開条件を元Issue/PRへ記録する。ローカル絶対パスはprivate registryだけに残す。GUI leaseで中断した場合、元coordinatorまたは明示引継ぎ先が解放確認後に既存登録の停止理由を照合し、通常のresume/cleanup手順を再開する。Issueがclosedでもこの後片付け責務は消えない。停止理由不明の自動再開、owner/sourceの付け替え、heartbeatの勝手な再開はしない。

mergedでなくclosed/supersededになったPR、PRのない旧形式branch、未追跡ファイルを持つworktreeは、自動cleanupへ無理に登録し直さない。代替PR/取り込み証拠、担当解放、未保存データの保全と削除範囲を確認してから別途判断する。`main/master/develop`、有効claim、未公開成果物、GUI証拠は保護し、件数ゼロのために削除しない。

## 検証

`python3 -m unittest discover -s scripts/workflow -p 'test_*.py'` はtarget変更、固定候補全範囲、古いbuild拒否、Case漏れ、
GUI failのdevelop許可/main拒否、privacy registry、旧v1、QA引継ぎ後の元実装Issue close/失敗時open保持、main/develop cleanup禁止、review/fix/再レビューと再開を検証します。

- [GitHub commit statuses](https://docs.github.com/en/rest/commits/statuses)
- [GitHub branch refs](https://docs.github.com/en/rest/git/refs#get-a-reference)

## 機械検査と実originの限界

現在のCLI adapterは実起動headerとJSON event envelopeからmodel/effortとoriginを読む。生成したreport名・role名をsession identityにしない。実originはprivateに保持し、公開進捗はfingerprintで示す。これはtrusted local runtimeを前提とし、敵対的なhostを認証する機能ではない。手動の独立報告は統合担当が実担当sessionから原本を取得し、固定HEAD/base/題名/Issue/全feedback、原証拠と実origin/model/独立性を照合して既存Agent reviewへ結ぶ。公開fingerprint/非空文字列だけで実証や独立性を確認済みにしない。

証拠certificateを登録する場合、Acceptanceは原記録・履歴・正確なresult/Case/保証/参照の構造を検査する。Agent reviewはそのrevisionと指定された実独立reviewer、実質assessmentを要求する。ARBのprivate origin/repair adapterは本repoに移植していないので、それがあると主張しない。自動経路で必要な実origin/原本評価を取得できなければ当該経路を止め、許可された独立top-level評価と統合担当へ戻す。CLI workerが独立top-levelかは従来どおり別途確認が必要。

ready化に伴うprovider reviewと独立レビューは別に扱う。統合担当はcurrent HEADのprovider完了・全review本文/inline/返信/Issue/PR conversation・resolved/outdatedも取得し、指摘IDと元commit、固定版での修正/検査又は根拠付き非該当を確認する。空結果・旧版・reaction・経過時間・CIだけで完了にしない。coordinatorは既存Codex botのcurrent-HEAD Completed summaryを検査し、全PR/Issue conversation・inline/review・解決threadをbindingへ含める。欠落/Running/旧版/未知形式は待機する。他providerへの自動切替adapterはなく、未完了の依頼を消して別経路へ逃がさない。merge直前・merge後・close前にも取得し、後着指摘は原Issue又は専用修正へ残責任を戻す。新しい常駐監視は作らない。
