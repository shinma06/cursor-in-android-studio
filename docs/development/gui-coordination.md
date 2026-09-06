# 並列タスクと単一デスクトップのGUI検証

[開発規約](github-workflow.md)のGUI部分。コードのwriterはworktreeごと、GUI操作者はホスト/OSログインセッションごとに1人。
別worktree・別IDEウィンドウでもフォーカス、ダイアログ、plugin設定、クリップボードが共有される。
Computer Useの画面読み取りも他タスクのフォーカスを動かし得るため予約対象。CLIの読み取り・単体テスト・buildPluginは予約不要。

## 依頼と順序

実装担当は対象Issueへ `gui-queued` と次の依頼票を投稿し、PRへリンク。進行役が同じホストのqueueをIssue検索で確認し、1件ずつ割り当てる。
順序は障害/復元安全性→依存を解除するタスク→受付順。同じビルドの複数ケースをまとめてよいが、Case/Issueごとの結果を残す。
queueに入れただけでは占有しない。GUI待ち中も別worktreeの実装、テスト、レビューを継続する。

```text
GUI request: queued
Issue / PR / implementation owner / GUI operator session:
Host / OS user / target apps:
Source HEAD / base SHA / artifact absolute path / ZIP SHA-256:
MV IDs / initial state / steps / expected result:
Fixture paths / allowed writes / CLI mode and account tier:
Run ID / budget (default 45min, 3 fixes, 8 Cursor sends):
Dependencies / priority reason / next action:
```

## ホスト共通の排他

`python3 scripts/workflow/gui_lease.py`はmacOS/Linuxの標準Pythonで動く。
既定の `/tmp/cursor-in-android-studio-gui-<uid>` を同一OSユーザーの全worktree/cloneで共用。
`--state-dir`はテスト専用。タスクごとに違う保存先を指定して並行操作してはいけない。
`flock`で予約更新を直列化し、UUID tokenで古い担当のrenew/releaseを拒否する。
これは協調するエージェント向けの予約であり、OSの入力や無視するプロセスを強制遮断するものではない。
GitHubのqueueは全員が読める正本、ローカルleaseは当該ホストの実行権。片方だけではGUI操作しない。

```bash
python3 scripts/workflow/gui_lease.py status
python3 scripts/workflow/gui_lease.py acquire --owner gpt-31-a --issue 31 --run run-31-a --head <full-SHA> --minutes 45
# 返されたtokenをIssueの開始コメントと手元の実行記録へ保存
python3 scripts/workflow/gui_lease.py renew --token <token> --minutes 15
python3 scripts/workflow/gui_lease.py release --token <token>
```

1. 割当/queueと人間が操作中でないことを確認し、acquire。既存予約があれば失敗するのでGUIへ触らない。
2. owner/token/ホスト/開始/期限をIssueへ記録して `gui-running` にする。投稿失敗時は操作せずrelease。
3. **最初の操作前と、インストール/起動/再起動/送信/復元など状態変更の直前**にstatusでtokenと期限を再確認。
   期限切れでは操作を止める。renewは同じownerが現在の状態を確認して予算内で行う。予算を勝手に延長しない。
4. 実画面でアプリ・fixtureマーカー・runを確認。対象HEADから作ったZIPのhash、配置したJAR/ロード実体、PID/設定ディレクトリも照合。
   `0.1.0-SNAPSHOT`表示や最後にbuildしたSHAだけで同一性を決めない。
5. fixtureのみで実行し、証跡を記録。修正が必要なら停止・引継ぎ・release後、実装担当へ返す。
   GUI担当が他taskのソースbranchを切り替えたり、ビルドを上書きしたりしない。
6. 終了時、未送信入力/実行中agent/ダイアログ/fixture差分/IDE PIDとロードビルドを確認する。
   自分が起動した処理だけ安全に停止するか、次担当へ明示引継ぎ。他タスクのIDE/agentを一括killしない。
7. IssueへCase別pass/fail/blockedと証拠、最終状態、次の操作を投稿。操作が終わった後token付きrelease。
   結果の投稿に失敗してもGUIを安全に止めてreleaseし、ローカル証拠から接続復旧後に投稿する。

## 期限切れ・クラッシュ・人間への引継ぎ

期限は所有権の自動移譲ではない。**期限切れでも新規acquireは拒否**する。
元担当または進行役/人間が、元セッションの中断とアプリ/処理の停止または引継ぎを確認してから、現在tokenでreleaseする。
その根拠をIssueへ記録し、新担当が新tokenでacquireする。期限やPIDだけを根拠に自動削除しない。
旧担当が応答せず、停止状態をGUIでしか確認できない場合は、進行役が旧エージェントの実行停止を確認し、
人間に対象画面と処理の停止確認を引き継ぐ。人間の確認中は全エージェントがGUI操作を止め、旧予約を保持する。
人間の確認結果をIssueへ記録した後にrelease/acquireする。人間の確認もできない間はGUIのみblockedとする。
state破損時はfail closed（操作停止）。ファイルを消して復帰したことにせず、全操作者停止確認後に管理者が記録を保全し復旧する。
OS再起動や/tmp消失後も、GitHubのgui-runningと起動中アプリの確認が必要。
人間がログイン/OS権限を操作する間は予約を保持し、GPTは操作停止。人間の完了連絡後に画面とtokenを再確認する。

## 証拠・統合の扱い

Case結果はsource HEAD、artifact hash、ロード実体、fixture、run ID、観察者、観察内容、共有可能な証拠と結び付ける。
画面取得失敗や認証障害はblocked。CLI成功・レビュー成功・mergeでGUI passに変えない。
別PRがmainへ入っても起動中IDEが更新されたとは解釈しない。PR差分が変われば担当が影響を再評価し必要ケースを再queue。
必須GUIを待つPRだけが待機する。共通IDEに触れない独立PRは通常通り進める。
既存の[loop手順](../loop-engineering/README.md)、[ビルド証拠形式](../loop-engineering/evidence.md)と併用。
