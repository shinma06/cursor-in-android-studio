# PRの自動レビュー・修正・統合

trusted mainの `agent_loop.py` が、明示登録された同一repository/maintainerのIssue PRだけを処理します。
#83以降はdevelopとmainの[target別gate](github-workflow.md)を使います。既存heartbeatはPAUSEDのままです。設定変更だけで再開しません。

## 引継ぎ

専用worktreeでテスト・push・Draft PRを作成し、**元writerの編集/commit/push/GUIを停止してから**登録します。

```bash
python3 /path/to/trusted-main/scripts/workflow/agent_loop.py enroll \
  --pr 123 --source /path/to/issue-worktree --owner gpt-issue-session \
  --scope scripts/workflow/ docs/verification/ --parent 1 --writer-stopped
```

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

reviewerはread-onlyの独立session、fixerはworkspace-writeで指定ファイルのみです。GitHub token/設定MCP/GUI権限を渡しません。
GPT-6 Astraがメインの場合は[Codex実行規約](codex-execution-policy.md)に従い、reviewer/fixerを現在のAgentの子として生成・委譲してはいけません。独立top-level Sessionとの連携かどうかを実行構造で確認し、workerという名称や自動化スクリプト経由を禁止回避の根拠にしません。
GUI未実施/環境blocked/製品failだけを理由にdevelopのコード承認を拒否しません。コード不具合、必要テスト失敗、Case追跡不足は修正対象です。
最大3 PRを交代制に処理し、worker 10分、fix 3回、review 8回、通信等失敗3回で停止します。GUI待ちPRだけで全体を止めません。

coordinatorはtarget refをAPIから取得し、fetchと再照合します。HEAD/base/target/本文/Issue条件/feedback変更は承認を失効させます。
base同期は通常merge → テスト → 非force push → 独立再レビューです。未解決会話とstrict baseはGitHub保護も検査します。
`test` / `PR policy` / `Acceptance gate`成功と独立レビューを読み戻し、merge直前に受入を再検証して `Agent review` を発行します。

- develop: 必要Case JSONと次の操作、製品failなら修正Issueが必須。GUI passは要求せずsquash merge。
- main tooling: GUI不要の理由/CLI検証、許可されたtoolingパスだけ。
- main promotion: `main..candidate` の全commitがmerge済みdevelop squash PRへ対応し、各PRの固定merge SHAに保存された全Caseが同じ候補/buildでpass。候補後の差分は2つの許可JSONだけ。merge commit専用。

[確認結果の入力と候補固定](../verification/README.md)を参照してください。1 Caseだけのpassは全候補の合格ではありません。
GUI lease付きの旧 `gui` 証拠登録は旧記録/互換用途に残しますが、promotionは候補の`promotion.results`を使用し、PR HEADの再GUIを要求しません。
GUI操作自体は現在も指定GPT/人間のlease下で行います。状態のblockedと製品failを区別し、未実施はpassへ変えません。

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

GitHub mergeを読み戻します。developでは元実装Issue単位の `<!-- issue-qa-handoff:v1 origin=N -->` をQA本文から全件取得で照合し、存在すれば再利用します。複数一致は停止します。元Issue/PR/merge SHA/固定mergeのCase JSON全文（GUI不要の場合もmain反映追跡）をQAへ保存してreadbackし、元Issueコメントの `<!-- issue-qa-link:v1 origin=N qa=Q -->` も読み戻してからstatus:doneでcloseします。API失敗やreadback不一致ではcloseせず、再試行で既存QAを再利用します。親チェック/QA受入は完了にしません。既存QA本文は置換せず引継ぎコメントを追加します。
mainで全受入済みのpromotion Issueは新candidate証拠を含む受入判定でclose可能です。元の機能/QA IssuesはPMが残条件を個別照合します。
remote branchはmerge対象HEADと一致、localは登録時HEADと一致・他worktree未使用・clean・worker停止・GUI lease空きの場合だけ削除します。
**main/master/developはどのcleanup経路でも削除しません。** `--force-with-lease`は一致確認付きIssue branch削除だけの限定使用です。
cleanup中断は次tickで再試行し、merge成功だけで状態を消しません。

```bash
python3 scripts/workflow/agent_loop.py cleanup-branches
python3 scripts/workflow/agent_loop.py cleanup-branches --apply
```

#83導入PRは [bootstrap手順](github-workflow.md#83の一回限りのbootstrap)に従い、旧enrollを使わずPMへ引継ぎます。
通常運用で必須gateを省略する手動経路は作りません。

## 検証

`python3 -m unittest discover -s scripts/workflow -p 'test_*.py'` はtarget変更、固定候補全範囲、古いbuild拒否、Case漏れ、
GUI failのdevelop許可/main拒否、privacy registry、旧v1、QA引継ぎ後の元実装Issue close/失敗時open保持、main/develop cleanup禁止、review/fix/再レビューと再開を検証します。

- [GitHub commit statuses](https://docs.github.com/en/rest/commits/statuses)
- [GitHub branch refs](https://docs.github.com/en/rest/git/refs#get-a-reference)
