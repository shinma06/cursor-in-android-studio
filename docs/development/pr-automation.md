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

実行用handoffはv2だけを選びます。旧HANDOFFv1をparse/選択せず、新しいv1コメントも有効v2を上書きしません。最新v2の欠落/不正を旧v1・旧世代・archiveで補いません。旧handoff原文はread-onlyの履歴/保管で扱い、STATEv1・native-session-v1・Caseのgpt/human・release markerは別の現用protocolとして維持します。
既存marker/owner/sourceは自動移行しません。公開進捗ではhost/sourceを除去し、例外のraw診断はprivate localログへ保存、公開側はopaque診断IDだけを示します。

## 工程

```text
queued → reviewing → reviewed
                   ├ changes_requested → fixing → publishing → reviewing
                   ├ blocked → 停止/具体的原因を確認
                   └ approved → acceptance待ち / CI待ち → merging → cleanup → done
```

reviewerはread-onlyの独立session、fixerはworkspace-writeで指定ファイルのみです。現行CLI workerはreview=`gpt-6.1-sol`/`high`、fix=`gpt-6.1-sol`/`high`を明示し、個人の既定model/effortを流用しません。`--json`の`thread.started`→終端`turn.completed`を、同じsessionのCLI保存記録の`session_meta`（id/root session/source=exec/checkout）と`turn_context`（全記録のmodel/effort/checkout）へ結び、reportへ保存します。JSONモードでは人間向け起動headerを要求しません。設定の実証を残すため`--ephemeral`は使わず、`CODEX_HOME/sessions/YYYY/MM/DD/rollout-*-<session>.jsonl`の一致する1原本だけを読みます。raw原本・prompt・診断はprivateに保全し、公開しません。未確認/不一致/利用不可・保存形式変更なら成果採用を停止し、モデルの自己申告は代替にしません。旧レビューに設定証拠がなければ再利用せず停止し、PMが適格な独立担当へ戻します。設定を確認できないfixerのdirty差分は保持して停止し、通常tick/resumeだけでcommit/pushへ自動回復しません。`fixing`は未確認で復旧対象外です。workerが正常終了して実設定照合と履歴確認を終えた後だけ`publishing`を保存します。timeout・通信/起動/結果読込等の例外とcontroller停止/再起動でも、この境界より前の差分は採用しません。PMが実装担当へ差分確認・適格設定での再実行/検証を戻し、独立再レビューを行います。現在の担当とは別の独立top-level実行かどうかの確認は、CLI引数だけでは証明できません。GitHub token/設定MCP/GUI権限を渡しません。

JSONイベント・保存省略flagは[公式CLI説明](https://developers.openai.com/codex/noninteractive)と[CLI reference](https://developers.openai.com/codex/cli/reference)、保存記録の形式/配置は[Codex 0.163.0 protocol](https://github.com/openai/codex/blob/rust-v0.163.0/codex-rs/protocol/src/protocol.rs#L3245)と[rollout recorder](https://github.com/openai/codex/blob/rust-v0.163.0/codex-rs/rollout/src/recorder.rs#L1744)に基づきます。別版の互換性や実workerでの稼働確認は、この静的適合と合成検証からは主張しません。
GPT-6 Astraがメインの場合は[Codex実行規約](codex-execution-policy.md)に従い、reviewer/fixerを現在のAgentの子として生成・委譲してはいけません。独立top-level Sessionとの連携かどうかを実行構造で確認し、workerという名称や自動化スクリプト経由を禁止回避の根拠にしません。
GUI未実施/環境blocked/製品failだけを理由にdevelopのコード承認を拒否しません。コード不具合、必要テスト失敗、Case追跡不足は修正対象です。
PMが選んだ有限の対象を、既存coordinatorは最大3 PRで交代制に処理し、worker 10分、fix 3回、review 8回、通信等失敗3回で停止します。GUI待ちPRだけで全体を止めません。この3 PR上限はsessionやbuildの同時枠ではなく、PMの実負荷判断と実対象/副作用のGUI調整を代替しません。

coordinatorはtarget refをAPIから取得し、fetchと再照合します。HEAD/base/target/題名/本文/Issue条件/feedback変更は承認を失効させます。
base同期は通常merge → [共通Change Impact](change-impact.md)の必要テスト → 非force push → 独立再レビューです。知識のみのskipも判定結果を残し、dirty/remote HEAD/受入の照合は省略しません。未解決会話とstrict baseはGitHub保護も検査します。
`test` / `PR policy` / `Acceptance gate`成功と独立レビューを読み戻し、merge直前に受入を再検証して `Agent review` を発行します。

- develop: 必要Case JSONと次の操作、製品failなら修正Issueが必須。GUI passは要求せずsquash merge。
- main tooling: GUI不要の理由/CLI検証、許可されたtoolingパスだけ。
- main promotion: `main..candidate` の全commitをmerge済みdevelop PRへ完全一意に対応付ける。通常はsquash結果、既存main同期mergeは[厳密なDAG照合](../verification/README.md#既存main同期mergeの履歴照合250)を通す。各PRの固定merge SHAに保存された全Caseが同じ候補/buildでpass。候補後の直接編集は2つの許可JSONだけ。後続mainの文書/toolingは[限定同期検証](../verification/README.md#候補固定後のmain同期533)を通す。merge commit専用。

[確認結果の入力と候補固定](../verification/README.md)を参照してください。1 Caseだけのpassは全候補の合格ではありません。
既存の `gui` 証拠登録は、現在の実assignmentで指定された担当を `--operator` へ渡し、既存evidenceの `observer` と照合します。予約tokenや旧共有leaseは要求/読取りしません。文字列一致はorigin認証ではなく、登録者が実担当/原証拠を照合します。promotionは候補の`promotion.results`を使用し、PR HEADの再GUIを要求しません。
GUI操作は能力・許可を確認した指定担当/人間が[現行GUI調整](gui-coordination.md)に従って行います。予約制は廃止され、実対象/結果先/副作用の具体的な競合だけを担当間で調整します。状態のblockedと製品failを区別し、未実施はpassへ変えません。

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

## 終了済みPRの非実行と継続境界

通常のload/tick/resume/gui/accept-review/enroll/rebindは、trusted own-repository PR APIのOPEN状態を先に確認します。closed/mergedはregistry/source/worker・state/attempt/claim変更前に拒否し、退役receiptや古い壊れたhandoff/STATEを読んで拒否理由を作りません。明示tickは `terminal-nonexecution`、scanは `terminal_prs` として示し、旧phaseをdoneへ正規化しません。feedbackはAPI原文をread-onlyで取得し、終了済みSTATEや実行sourceを解決しません。再OPENしたPRでもmissing/invalid v2はfail-closedで、元ownerの明示復旧へ戻します。

scanは管理下のPAUSEDも表示しますが、引数なしの自動tickは選択対象からPAUSEDを除き、他の稼働可能なPRへ最大3枠を回します。PAUSEDのSTATE/attemptを更新して順番を送ったり、自動resumeしたりしません。

通常のmerge直後も、最新のv2/STATE/registryを専用継続で再照合してから、同じtick内でQA/custody/cleanup/claim返却へ進みます。そのmerge前に既存STATEへ、当該registry_idと承認head/baseに結び付く `merge_intent` を保存します。merge応答の喪失や継続資格の変更は `postmerge-hold` とし、古いSTATEで最新PAUSED/ownerを上書きしません。中断後は元owner/coordinatorが次の専用継続を使います。

```bash
python3 scripts/workflow/agent_loop.py continue-finish --pr N
```

これは通常closed実行の迂回ではありません。confirmed merge、現在のv2/host/owner/停止・expected head、承認/review/Acceptance/full bindingとpending merge意図、merging/cleanup・非PAUSED、元sourceとworker不在を照合してから、既存finishの再取得・QA・cleanup・claim返却を継続します。phaseだけ・古いclosed・PAUSED・不明owner/原stateは継続資格にならず、失敗時も実資源や元FAILを保持します。古い中断がこの操作bindingを持たなければ、markerを後付けせず、元owner/PMの既存割当で残処理とcustodyを個別照合します。実資源の削除許可・原世代保管・actual retirement receiptは、非実行拒否とは別の操作責務です。

## 完了とcleanup

GitHub mergeを読み戻します。developでは固定mergeのCaseを読み、残CaseがあるときだけQAを作成/再利用します。新規QAはcanonical `test: #N 日本語の要約`、再利用は旧`[試験] #N `と新形式の元番号を厳密に照合します。重複・closed・所有/目標の矛盾は上書きせず戻します。元Case JSONの全文コピーをQAへ増やさず、固定merge/pathと全必要Case ID・手順/期待/次操作の正本を示し、native親子・内容と元Issue backlinkをreadbackしてから元実装Issueを閉じます。CaseなしならQAを作らず、元IssueとPRへPMのmain反映保管・未確認と次操作を記録し、両端readbackします。既存QAがあるno-Cases変更は自動処理せずownerへ戻します。API失敗/不一致ではcloseしません。既存QA本文/原失敗・履歴は保持します。
mainで全受入済みのpromotion Issueは新candidate証拠を含む受入判定でclose可能です。元の機能/QA IssuesはPMが残条件を個別照合します。
remote branchはmerge対象HEADと一致、localは登録時HEADと一致・他worktree未使用・clean・worker停止と実対象の所有/利用を確認した場合だけ削除します。GUI leaseの有無/期限/空きを条件にせず、旧共有記録は変更しません。
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
3. **保留**: branch、固定HEAD、残る資源の種類、理由、owner、次の操作/再開条件を元Issue/PRへ記録する。ローカル絶対パスはprivate registryだけに残す。旧GUI lease理由で中断した場合も、元coordinatorまたは明示引継ぎ先が残る実際の停止理由/所有/利用を照合して通常のresume/cleanup手順を再開する。旧予約の解放を待たず、PAUSEDを自動解除しない。Issueがclosedでもこの後片付け責務は消えない。停止理由不明の自動再開、owner/sourceの付け替え、heartbeatの勝手な再開はしない。

mergedでなくclosed/supersededになったPR、PRのない旧形式branch、未追跡ファイルを持つworktreeは、自動cleanupへ無理に登録し直さない。代替PR/取り込み証拠、担当解放、未保存データの保全と削除範囲を確認してから別途判断する。`main/master/develop`、有効claim、未公開成果物、GUI証拠は保護し、件数ゼロのために削除しない。

## exact旧名refの限定退役

通常のpush・同名Issuebranch cleanup条件は維持します。別の旧名headを退役する場合だけ、現在のclean専用Issuebranchから、採用済みmainと同じguard/pre-push hookを保って次を使います。実処置前に対象ownerが停止・不要・権限・必要原本/実復元と現在の割当を確認します。

```bash
python3 scripts/workflow/git_guard.py retire-ref --packet /private/one-target-binding.json
```

一操作packetは0600のowned regular fileとし、schema=1、own repository、canonical git_common_dir、現在のoperation_branch/operation_headとtrusted_main、remote/remote_url、exact refs/heads refとexpected_oid、元custody_ownerと一致するowner、writer_stopped/unused=true、当該Issueの実assignment URL、既存manifestのpath/hashを固定します。manifestの同じremote-tracking ref/expected_OID、force_flags=false、保管bundleのhash・含有HEADとactual_restore_HEAD_treeを照合します。manifestやBooleanの存在だけで実資格を認定する認証機能ではなく、権限と原本内容の実質評価は担当者が保持します。

wrapperは追加force指定を受けず、当該呼出しだけのpacket/hashを子hookへ渡し、通常の `git push remote :exactref` を1targetだけ実行します。manifest/bundleの必須hashは64桁の小文字hex文字列で照合し、null・欠落・不正値を削除前に拒否します。[pre-pushの実remote名/locationとadvertised old OID](https://git-scm.com/docs/githooks#_pre_push)をpacketへ照合し、mixed/multi/update・保護branch・他namespace・現在の別Issuebranch・未知repo/remote/原本/改変hookを拒否します。hook後のremote変更はGit serverのold-ID比較で拒否し、成功後は検証済みpush URLそのものへ同refの不在を読み戻します。fetch先mirrorの不在は削除先の証明にしません。結果不明・再作成時は無条件再試行せず保持します。恒久allowlistやforce/API迂回はなく、このsource変更で実legacy refを削除しません。

## 検証

`python3 -m unittest discover -s scripts/workflow -p 'test_*.py'` はtarget変更、固定候補全範囲、古いbuild拒否、Case漏れ、
GUI failのdevelop許可/main拒否、privacy registry、v2限定/旧v1拒否、terminal非実行と限定finish継続、QA引継ぎ後の元実装Issue close/失敗時open保持、main/develop cleanup禁止、review/fix/再レビューと再開を検証します。

- [GitHub commit statuses](https://docs.github.com/en/rest/commits/statuses)
- [GitHub branch refs](https://docs.github.com/en/rest/git/refs#get-a-reference)

## 機械検査と実originの限界

現在のCLI adapterはJSON event envelopeと同じsessionのCLI保存記録からmodel/effortとoriginを読む。生成したreport名・role名をsession identityにしない。実originはprivateに保持し、公開進捗はfingerprintで示す。これはtrusted local runtimeを前提とし、敵対的なhostを認証する機能ではない。手動の独立報告は統合担当が実担当sessionから原本を取得し、固定HEAD/base/題名/Issue/全feedback、原証拠と実origin/model/独立性を照合して既存Agent reviewへ結ぶ。公開fingerprint/非空文字列だけで実証や独立性を確認済みにしない。

証拠certificateを登録する場合、Acceptanceは原記録・履歴・正確なresult/Case/保証/参照の構造を検査する。Agent reviewはそのrevisionと指定された実独立reviewer、実質assessmentを要求する。ARBのprivate origin/repair adapterは本repoに移植していないので、それがあると主張しない。自動経路で必要な実origin/原本評価を取得できなければ当該経路を止め、許可された独立top-level評価と統合担当へ戻す。CLI workerが独立top-levelかは従来どおり別途確認が必要。

ready化に伴うprovider reviewと独立レビューは別に扱う。通常は完了を待ち、応答停滞だけは[限定進行](#provider-stall)で待機条件を判定する。統合担当はcurrent HEADのprovider完了・全review本文/inline/返信/Issue/PR conversation・resolved/outdatedも取得し、指摘IDと元commit、固定版での修正/検査又は根拠付き非該当を確認する。空結果・旧版・reaction・経過時間・CIだけで完了にしない。coordinatorは既存Codex botのcurrent-HEAD Completed summaryを検査し、全PR/Issue conversation・inline/review・解決threadをbindingへ含める。欠落/Running/旧版/未知形式は待機する。他providerへの自動切替adapterはなく、未完了の依頼を消して別経路へ逃がさない。merge直前・merge後・close前にも取得し、後着指摘は原Issue又は専用修正へ残責任を戻す。新しい常駐監視は作らない。


<a id="provider-stall"></a>

## provider応答停滞時の限定進行

[ARB採用元PR385](https://github.com/shinma06/android-replay-buffer/commit/8beed9c87b2432c34a074b792719f187d66799d1)をCASの現Codex summary consumerへ適合する。通常のCompleted経路を維持し、他provider/native方式・ARBのprivate repair adapterや製品Caseは移植しない。これはGitHub保護設定・4checks・strict base・Case/Acceptance・所有/権限を緩める経路ではない。

実current HEADの必要レビューが停滞した場合、理由を既存Issue/PRに記録し、**再依頼前**にtrusted mainの `agent_loop.py prepare-review-retry --pr N` で生API観測を1件保管する。既存private storage内のimmutable `review-evidence` は元コメント・inline/review・観測時刻と同repo/PR/HEAD/base/target/題名/本文/Issueをopaque ID＋内容hashで結ぶ。enroll/owner変更・再依頼・承認・worker起動を行わない。元APIを保存しただけでは元義務の適格性や30分例外を認定しない。

次に担当が同じ40桁HEADを明記し、通常PRコメント `@codex review <full HEAD>` と `再依頼: <実際の理由>` で**一度だけ**明示再依頼する。実comment IDとserver `created_at`を読み戻す。本文内時刻・edited時刻・古い依頼からの待ち時間は起点にしない。自動投稿・繰返し・別serviceへの切替はしない。観測参照を紛失/改変した場合、再依頼後のsnapshotを再依頼前の原本へ見せかけない。

`agent_loop.py --review-retry-comment <実ID> --review-retry-original <保管参照> feedback --pr N` は全conversation/inline/review/解決・outdated threadを再取得する。現summaryの機械状態と表示HEAD/開始時刻、再依頼前の保存原本にある実Running/Queued又は未編集のmaintainer同HEAD依頼、既知Completedとの開始/充足関係を同じ時系列で検査する。元義務の開始は保存原本だけから認定し、再依頼後に更新された現dashboardの古い表示開始時刻で原本の欠落を補わない。既知Completedは両snapshotから照合し続ける。同dashboardの上書きは保存原本から照合し、弱い旧snapshot・異HEAD/別依頼・編集/未来/不正時刻・複数再依頼・既に完了した元義務・未知形式/実failure/具体応答や指摘は限定進行を通さない。別方式/securityの稼働義務も無視しない。新HEAD/base/metadataには旧観測を流用しない。

その**新しい実再依頼のserver created_atから30分**後も必要応答がなければ、`review_wait_satisfied`だけがtrueになり得る。`remote_review_complete`はfalseのまま。時間は待機資格に過ぎず、Agent review成功やmerge許可ではない。CASにはARBのorigin adapterがないため、既存の別top-level手動評価を次の明示経路で結び、停滞を理由にCLI子worker/代替actorを自動起動しない。

1. 30分後のfresh feedback/bindingと元API観測を、PMが許可した既存独立reviewerへ渡す。実writer/設計・証拠寄与者の完全なsession一覧・原本と実モデル/effort/役割の独立性、全指摘の修正/非該当・未解決なしを従来どおり実質評価する。fingerprintや時刻だけで独立性/一覧の完全性を推定しない。
2. reviewerは30分後の当該turnで、通常報告にrawの自分のsession、`feedback_hash`、全 `review_binding`（feedback出力のbindingを完全一致）、実model/effortと根拠・評価を記録する。元報告は保管し、公開可能な根拠参照を使う。実 `CODEX_THREAD_ID` と元Codex `session_meta`/当該 `turn_context` を照合できる現在のCodex top-level runtimeだけがこのadapterの対象。未対応/不明originを同等と偽らない。
3. reviewer自身が `agent_worker.py --report <private-report> --binding <private-binding> --storage <既存storage> --turn <実turn ID> --contributor <実writer session> [--contributor <他寄与者>] --output <新private-output>` を実行する。モデルは起動しない。実runtime/非fork・設定・別session・30分後turn・全binding・feedbackを検査し、元報告/原runtimeをprivate receiptへ結ぶ。公開版のsessionはfingerprint、receiptはopaque ID＋hash。完全な寄与者/役割の判定自体は統合担当の原本照合責務で、敵対的hostの認証機能ではない。
4. 統合担当は同じ再依頼option付きの `agent_loop.py ... accept-review --pr N --evidence <新private-output>` で既存v2 handoffへ読み込む。停止/owner/source/target・実origin/内容hash・全bindingを再検査する。元writerのsourceは登録時HEADのまま `source_safe()` で確認し、取込対象はcoordinatorが追跡する `expected_head`（未更新なら登録HEAD）と照合する。元handoff/registryを更新してこの区別を回避しない。既存PAUSED・worker残存・v2欠落をresume/enrollやv1 fallbackで回避しない。通常tickでも同じ参照と独立原本を検査し、CI/strict base/Acceptanceが揃った場合だけ通常統合する。

再取得・merge直前・merge後/close前も同じ判定と独立receiptを使う。source/base/target/題名/本文/Issue/feedback/再依頼の変化は旧評価を失効させ、後着応答/指摘は通常の完了又は原Issue/修正担当へ戻す。原証拠・未知・元FAILを残し、snapshotの追加で未観測をPASSにしない。今回の実装検査は隔離fixtureであり、実案件の30分例外発動を示さない。main先行後のdevelop同期と既存registry/旧形式の実移行は#624/#623に残し、既存実データをこのsource変更で切り替えない。
