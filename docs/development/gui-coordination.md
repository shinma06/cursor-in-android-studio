# 並列タスクと単一デスクトップのGUI検証

> 2026-09-07 / #83: developはテスト・独立レビュー・Case追跡で統合可能（GUI pending/blocked/failを保持）。mainは固定候補全体の必要Case pass後のみ。区切り単位の入口は [確認マトリクス](../verification/README.md)。過去MV/runは履歴であり新候補のpassへ転記しない。


[開発規約](github-workflow.md)で割り当てた操作者は、GUI試験を含め**観察・入力とも `agent-desktop` を第一選択**とする。利用版の `skills get desktop`、`skills get platform` と対象commandの `--help` で対応機能を確認する。clientが許す経路・実際のOS権限・対象surfaceは個別に確認し、導入やhelpの取得だけで操作可能とは判定しない。[公式の操作手順](https://github.com/vercel-labs/agent-desktop/blob/d3dc190afbf34cd2377252398ddd5c30efbd093a/skills/agent-desktop/SKILL.md)

1. 対象IDEのPID・profile・ロードbuild・fixtureからwindowを特定し、新しいsnapshotのrefで対象を指定する。画面配置・描画を確かめるCaseでは実画像を観察し、部分的なAX treeを画面全体の証拠にしない。
2. 入力後は対象窓と結果が現れる別窓・dialogも新しく観察し、期待する値や画面と照合する。exit 0、配送済み、結果不明だけでは成功にしない。結果不明の保存・送信を別経路からも再送しない。
3. 未導入、対象選択不可、必要な描画の取得不可、入力未対応など具体的な限界と実施範囲を記録し、その操作に合うPeekabooまたは利用中agentの純正Computer Useを選ぶ。fallback間の固定順位はない。未試行・接続失敗と実拒否を区別し、認証・実拒否・本人停止は迂回しない。
4. 明示されたCase・環境のlease条件や純正経路の指定を維持し、実施経路と未達を記録する。使える経路がなければ、指定担当または許される範囲の人間へ必要な操作だけを引き継ぐ。Computer Use必須Caseを手操作でpassにしない。

## 操作単位の共有状態

確認・入力とも、対象と**結果先**のPID・window・アプリ状態・fixtureを所有し、他担当の状態と証拠に干渉しないと確認できる対象限定の背景操作だけを並行してよい。headless、別worktree・別window、対象指定や終了コードだけでは独立性を示せない。例えば独立fixtureの背景読取りと、共通IDEで設定dialogを開く操作は区別する。

フォーカス・物理入力・clipboard・display、IDE起動／再起動・profile・plugin、ADB/device/daemon、trace・録画・fixtureを共有する操作や、観察を含め影響が具体的に不明な区間は既存leaseを使う。排他／範囲不明の予約があればownerと対象・副作用を合意するまで重ねない。実際に人間が使う場合は競合する操作を止め、本人の停止と引継ぎを優先する。旧coordinatorのGUI証拠登録が要求する有効lease、cleanupのlease空き確認などの機械的guardはそのまま従う。

## 依頼と順序

実装担当はGUI依頼をIssueへ `gui-queued` として記録し、PRへリンクする。進行役は同一hostのqueue・既存予約・対象と独立性を照合し、共有区間を1件ずつ割り当てる。
CASとARBがこの共有hostで実際に競合する場合だけ、先行ownerが安全な区切りで保全・通常復帰・返却した後、次の枠は準備済みARBを優先する。予約の奪取、実行中の割込み、未準備の先取りは行わない。別hostへこの優先順を暗黙に適用しない。CAS内の順序は障害/復元安全性→依存を解除するタスク→受付順。同じビルドの複数ケースをまとめてよいが、Case/Issueごとの結果を残す。
担当の能力・許可は[共通の役割条件](github-workflow.md#正本と役割)で確認する。GUI toolがなければ対応可能な担当へ引き継ぎ、未確保ならGUIのみblockedとする。
queueに入れただけでは占有しない。GUI待ち中も別worktreeの実装、テスト、レビューを継続する。

```text
GUI request: queued
Issue / PR / implementation owner / GUI operator session:
Target apps:
Source HEAD / base SHA / artifact ID / ZIP SHA-256:
MV IDs / initial state / steps / expected result:
Fixture ID / allowed writes / CLI mode:
Run ID / budget (default 45min, 3 fixes, 8 sends to the product under test):
Dependencies / priority reason / next action:
```

host/OS user・絶対パス・lease tokenはprivate local実行記録にだけ保存する。既存registryの参照がある場合は公開可能なopaque IDを使えるが、新規registryは必須にしない。送信予算の保存キー`max_cursor_sends`は互換性のため維持する。

## ホスト共通の排他

`python3 scripts/workflow/gui_lease.py`はmacOS/Linuxの標準Pythonで動く。
既定の `/tmp/cursor-in-android-studio-gui-<uid>` を同一host/OSユーザーのCAS・ARBの全worktree/cloneで共用。
`--state-dir`はテスト専用。タスクごとに違う保存先を指定して並行操作してはいけない。
`flock`で予約更新を直列化し、UUID tokenで古い担当のrenew/releaseを拒否する。
これは協調するエージェント向けの予約であり、OSの入力や無視するプロセスを強制遮断するものではない。
GitHubのqueueは全員が読める担当記録であり、排他区間はローカルleaseも揃ってから操作する。優先順や操作単位の独立性はこのスクリプトでは強制されない。

```bash
python3 scripts/workflow/gui_lease.py status
python3 scripts/workflow/gui_lease.py acquire --owner gui-session-31-a --issue 31 --run run-31-a --head <full-SHA> --minutes 45
# 返されたtokenはprivate local実行記録へ保存。Issueへ公開しない
python3 scripts/workflow/gui_lease.py renew --token <token> --minutes 15
python3 scripts/workflow/gui_lease.py release --token <token>
```

1. 割当/queueと人間が操作中でないことを確認し、acquire。既存予約があれば失敗するのでGUIへ触らない。
2. 公開可能なowner ID/run ID/開始/期限をIssueへ記録して `gui-running` にする。投稿失敗時は操作せずrelease。
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
人間がログイン/OS権限を操作する間は予約を保持し、Agent操作者は操作停止。人間の完了連絡後に画面とtokenを再確認する。

## 証拠・統合の扱い

Case結果はsource HEAD、artifact hash、ロード実体、fixture、run ID、観察者、観察内容、共有可能な証拠と結び付ける。
対象の画面を取得できず許可された代替でも必要な観察を満たせない場合や、認証が必要な場合は該当GUIをblockedとする。CLI成功・レビュー成功・mergeでGUI passに変えない。
別PRがmainへ入っても起動中IDEが更新されたとは解釈しない。PR差分が変われば担当が影響を再評価し必要ケースを再queue。
必須GUIを待つPRだけが待機する。共通IDEに触れない独立PRは通常通り進める。
既存の[loop手順](../loop-engineering/README.md)、[ビルド証拠形式](../loop-engineering/evidence.md)と併用。

## 検証目的と継続

割当は[責任と最小検証](../verification/responsibility.md)から保証→既存証拠→不足事実→手段を決める。十分なAPI/CLI/FILE/実render経路を先に確認し、残るUI/OS/glue事実だけにGUIを使う。既存の本人委任内でfreeな共有leaseは都度確認なしに取得し、競合は許可された担当間で順序/停止/引継ぎを調整する。未試行や接続不良をtool拒否にせず、実拒否/認証は別経路で迂回しない。

導入/通常再起動は実IDE/profile/所有停止・退避・復元・固定ZIP/JARと実ロードの同一性を保つ。公式local ZIP配置で目的を満たす場合はPlugins画面の探索を一律の試験にせず、対象の有効なCase/明示実施経路と自製品設定・About/actionの実接続を維持する。ARB固有の3 JAR数や停止/OFF条件を本製品へコピーせず、実ZIPの全JARを照合する。本規約は既存Caseを再分類/再試験する指示ではない。
