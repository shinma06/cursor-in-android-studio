# PRの自動レビュー・修正・統合

#35で導入。GitHubにPRと引継ぎを保存し、ローカルの定期進行役が再開する。
通常のエージェント依頼は、実装してPRを作った後に以下の引継ぎを行う。
それ以降は人間が毎回「レビュー」「直して」「merge」と指示する必要はない。

## 実行するもの

- 現在のCodex taskに登録するheartbeatが5分ごとに起動し、信頼済みmainの `agent_loop.py tick` を呼ぶ。
- `codex exec`を別プロセスで起動。reviewerはread-only、fixerはworkspace-write。ユーザーのChatGPTログインと選択modelを使う。
  Claude CLIが未ログインの現環境では別Codexセッションがreviewer。Claudeがレビューしたとは記録しない。
- 各PRは1工程ずつ進み、最大3PRを受付時刻による交代制で処理。GUI待ち/停止中PRだけで後続を塞がない。
- 作業中はホスト共通coordinator lock、GUIは別の既存leaseを使う。同一OSユーザー/ホストの進行役は1人。
- mainへのmergeはGitHub APIのHEAD指定と保護ルールを通す。成功前に最新HEAD/base/本文/Issue条件/フィードバックを再確認する。

**実行条件:** Macの電源が入り、Codexアプリが動作し、ネットワークとCLIログインが有効であること。
停止/スリープ中にクラウドからMacを操作する仕組みではない。次の起動で保存済み工程を照合して再開する。
既存のログインを使い、新規APIキー、追加API課金経路、GitHub上のself-hosted runnerは導入しない。
エージェント利用枠は消費する。1worker最大10分、fix最大3回、review最大8回、通信等の失敗は3回まで。

## 実装担当から自動進行役への引継ぎ

Issueのclaimと専用worktreeで実装し、テスト・push・Draft PRを作る。
**元の担当は編集/commit/push/GUIを止めてから**、信頼済みmainのscriptで登録する。
範囲は実際に委譲するファイル/ディレクトリ。以下の番号・パスは対象へ置換する。

```bash
python3 /path/to/main/scripts/workflow/agent_loop.py enroll \
  --pr 36 --source /path/to/issue-worktree --owner gpt-35-a \
  --scope scripts/workflow/ docs/development/ CLAUDE.md \
  --parent 1 --close-issue --writer-stopped
```

`--close-issue`はIssue全体を完了するタスクにだけ指定。部分実装では付けない。
PRは同一repo、作者shinma06、main向け、Issue番号branchと `Issue: #N` が一致し、sourceがclean/HEAD一致している必要がある。
引継ぎmarkerはPR/Issue/HEAD/base/host/旧新owner/source/scope/停止宣言を保存する。
**登録は元claimの当該範囲を自動進行役へ引き渡す宣言**。元担当は再開前に最新状態を読み、勝手にwriterへ戻らない。
Issueの更新は進行役の専用コメントを使うため、受入チェックリストや人間の本文を途中で上書きしない。
GitHubの任意コメント、外部作者のPR、fork、未登録PRはworkerを起動する許可にならない。

## 工程と機械判定

```text
queued → reviewing → reviewed
                   ├ changes_requested → fixing → publishing → reviewing
                   ├ blocked → 停止/通知
                   └ approved → GUI待ち / CI待ち → merging → cleanup → done
```

reviewerは最新diff、Issue条件、委譲範囲、レビューコメントを読み、固定HEAD/baseの構造化JSONで返す。
独立session ID、verdict、scope_complete、issue_complete、gui_required、指摘、根拠を保存。
指摘やscope未完了を含むapprovedは拒否する。fixerの自己申告でレビューOKにしない。
fix後は範囲/HEAD/競合の検査、テスト、commit/pushを進行役が行い、新しいreviewerを起動する。
reviewer/fixerにはGitHub tokenの環境変数、ユーザー設定のMCP、GUI担当権を渡さず、commit/pushも委譲しない。
対象は信頼するmaintainerの登録PRだけ。OS sandboxは機能ごとの制限であり、PR内コードが安全である保証ではない。

`Agent review` commit statusをcoordinatorが発行し、main rulesetの必須チェックにする。
最新HEAD/base/PR本文/Issue本文/人間のフィードバックが変わると判定を失効させる。
レビュー中断時は旧承認を復活させない。CI成功と必要GUI証拠が揃うまでmergeしない。
既存の人間の未解決review threadはGitHub保護がmergeを拒否する。解決の正当性を進行役が確認してから再開する。
同じGitHubアカウントはstatusの発行権限も共有するため、悪意ある同一資格情報の偽装を識別する署名基盤ではない。
未登録の通常PRも、merge前にこの手順で登録して独立レビューを受ける。必須gateを省略する手動経路は作らない。

## GUI待ち

CLI workerはGUIを操作しない。`gui-queued`ならheartbeatのGPT進行役がIssue/MV/fixtureを読み、
[GUI手順](gui-coordination.md)でleaseを取得して実際に観察する。他のGUI担当が占有中なら待ち、別PRを進める。
実行不能ならそのPRだけblockedを記録し、根拠のないpassやGUI不要判定を作らない。
観察完了後、起動した処理を停止/安全に引継ぎ、lease保持中に次のJSONを登録する。

```json
{
  "head": "40桁の対象HEAD",
  "base": "40桁の対象base",
  "artifact_sha256": "64桁のZIPハッシュ",
  "run": "run ID",
  "observer": "leaseのownerと同じセッションID",
  "loaded_identity": "実際にロードしたJAR/ビルドを確認した証拠",
  "evidence_url": "共有可能な証跡URL",
  "processes_stopped": true,
  "cases": [{"id": "MV-xxx", "status": "pass", "observation": "実際の観察内容"}]
}
```

```bash
python3 scripts/workflow/agent_loop.py gui --pr 36 --evidence /tmp/gui-result.json --lease-token <token>
```

scriptはHEAD/base、lease token/owner/期限、Caseのpass、必須証拠欄を検査する。
観察の真偽はGPT/human担当が責任を持つ。登録後にleaseをreleaseし、次tickがmerge可否を判定する。
GUIの実行範囲/受入は元Issueに従い、他Issueの未確認ケースまで合格にしない。

## 中断・再開

正本はPRの `agent-loop-state:v1` コメントとIssueの専用進捗コメント。
ローカル `.git/agent-loop/` はmanaged clone、worker PID、ログ、処理順の実行状態で、独立したbacklogではない。
再起動時にGitHubのmerged状態、remote/local HEAD、dirty、worker groupを読み戻す。
push/merge直後の通信切断でも同じ修正やmergeを繰り返さず後工程へ進む。
子processはtimeout時にgroupごと停止。PID記録前のクラッシュは自動横取りせず、進行役が残存処理を確認する。

```bash
python3 scripts/workflow/agent_loop.py scan
python3 scripts/workflow/agent_loop.py tick --pr 36
python3 scripts/workflow/agent_loop.py resume --pr 36 --reason '停止/競合/CI原因を確認し、次の試行を許可'
```

上限到達/範囲外編集/外部push/旧担当の再開/不正状態は、そのPRを停止して理由を通知。
`resume`は実際の障害解消・子process停止確認後に進行役が実行する。回数を毎tick無条件でリセットしない。
進行役が作成して記録したpublish HEADだけを自動再送する。workerの履歴変更や、commit直後・記録前の中断は通常のresumeだけではpublishしない。進行役が差分と由来を確認して修復する。
worktreeのdirty内容や新commitをreset/stashで消して復旧しない。旧sourceに変更があれば保持し、担当と調整する。

## Issue更新・branch削除

merge後はIssueへmerge SHA、残条件、claim解放を記録する。
`--close-issue`かつ独立reviewerがIssue全体の完了を確認し、現在のIssue本文が承認時と一致する場合だけcloseする。
部分完了・条件追加時はopenを維持。親は対象Issue専用のチェック行だけ更新し、複数Issueの集約行は勝手に完了にしない。
GitHubのdelete_branch_on_mergeも有効にする。coordinatorは削除漏れを再確認し、remote tipがmerge対象HEADと一致した場合だけ削除。
remote削除の `--force-with-lease=<ref>:<sha>` は**一致確認付き削除だけ**の限定使用。履歴のforce更新には使わない。
local branchは登録時HEADと一致し、他worktreeで使われていないものだけcompare-and-deleteする。
source/managed checkoutのclean、worker停止、GUI lease空きも削除前に確認し、削除中はGUI予約更新との排他を保持。
後片付けに失敗したらcleanup段階を残し、次回再試行する。merge成功だけで処理を消さない。

```bash
python3 scripts/workflow/agent_loop.py cleanup-branches          # 既存の完了branchを監査
python3 scripts/workflow/agent_loop.py cleanup-branches --apply  # remote削除済み・merged PR HEAD一致・未使用local refだけ削除
```

古いbranchでも未merge・新commitあり・worktree使用中なら保持する。日時だけで役目を終えたとは判断しない。

## 検証と一次情報

`python3 -m unittest discover -s scripts/workflow -p 'test_*.py'`で、対象選別、古い承認失効、GUI/CI gate、
指摘→修正→再レビュー→merge、Issue条件追加、merge直後の切断、cleanup再試行を検証する。
実際のCLI reviewer起動とGitHub statusは導入PRで確認する。

- [Codex non-interactive mode](https://learn.chatgpt.com/docs/non-interactive-mode): CLIの構造化出力と保存済み認証。
- [Scheduled tasks](https://learn.chatgpt.com/docs/automations?surface=app): ローカル自動処理の起動条件。
- [GitHub commit statuses](https://docs.github.com/en/rest/commits/statuses): HEAD単位の必須status。
