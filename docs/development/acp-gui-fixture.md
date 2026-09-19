# 合成ACP / print fixtureをGUI試験へ接続する（#288・#310）

#24のcontext payloadと予約登録後のsnapshotを、既存ACP fakeのpermissionシナリオで観測するためのローカル準備です。実Cursor/providerへの接続、IDE設定の自動変更は行いません。合成通信成功と製品GUI・実provider・元固定ZIPのPASSは別です。

既存fakeのpermissionを既定値として維持し、#310で下表の有限ACP/printシナリオを追加しています。新protocolや汎用シナリオ言語、Controller/EDT停止はありません。今回の検査はheadlessのみで、後日のGUI接続には担当者・lease・固定buildの確認が必要です。

## 操作者向けサマリ

| 項目 | 手順 | 期待値 |
| --- | --- | --- |
| 準備 | #24原素材から別の使い捨てworkspaceを作り、合成データ専用のmarkerを置く。adapter準備コマンドを実行 | 新しいrun dirに固定launcher/config/capture dirができる。元資料は変更しない |
| 接続 | 後日、指定GUI担当が固定build/lease確認後にlauncherを実行ファイルとして指定し、新規ACP会話を開く | 起動は `launcher acp` のみ。違うroot/引数/marker/固定ソースでは失敗し実agentへfallbackしない |
| 保留 | 合成promptの受信後、合成permissionカードへまだ回答しない | 先行runを保留する間に予約登録と別draftの準備を観察できる。実GUIでは未検証 |
| 配送 | 予約一覧を開いてpauseし、元文/参照を変更。先行カードへ回答後、予約を明示再開 | captureでOLD明示selection/開始時NEW参照を照合。厳密なEDT競合窓の試験ではない |

## 準備

Python 3.9以上を使用します。prepareに使ったPythonの絶対pathと実行ファイルSHA-256をlauncher/configへ固定します。既存fakeのpathとSHA-256、adapterのpath/SHA-256、workspace実pathとmarker hashも固定します。ソース変更後や別の復元先では新しいrunを準備します。

workspaceは合成データ専用と確認した新規コピーを使います。そこへ `ACP_SYNTHETIC_FIXTURE.json` を次の形式で作成してください。workspace値はそのディレクトリのrealpathです。markerは内容を自動監査した証明ではなく、操作者による合成データ範囲の明示です。私的会話/実ファイルを含む場所へ置かないでください。

```json
{"schema": 1, "purpose": "synthetic-acp-fixture", "workspace": "/absolute/synthetic-workspace"}
```

repository rootから実行します。出力先の親dirは先に用意し、出力先自体は未作成にします。captureをFile/Folder contextへ混入させないため、出力はworkspace外に置きます。

```bash
python3 scripts/loop/acp_fixture.py --workspace /absolute/synthetic-workspace --output /absolute/new-run
```

`new-run/agent-fixture` がPluginへ渡す実行ファイルです。準備はIDEやユーザー設定を操作しません。元の1284素材を保管した場所へ直接marker/configを追加せず、実行用コピーを使います。

## 固定契約と記録

launcherは固定Pythonでadapterを実行し、marker/cwd/固定fileを照合して指定した既存fake/replayをexecします。ACP側のPlugin引数は `acp` 一つだけ。print側は下記の固定起動契約と合成 `--version` のみ。選択していないtransport・任意metadata・追加引数を拒否し、環境変数のprovider資格情報を子へ継承しません。実Cursor/providerの検索・fallbackはありません。

`capture/wire-<PID>.jsonl` に1プロセス1ファイルで送受信を記録します。新規排他作成・権限0600で、PID再利用時にも既存記録へ追記しません。共有workspaceの `wire.jsonl` はcapture実行では作りません。従来の単体Harnessは追加引数なしのまま、従来どおり受信のみの `wire.jsonl` を使います。

ACP各行は `direction`（in/out）、`pid`、`monotonic_ns`、`rpc_id`、`session_id`、元JSONの `payload` です。session IDがpayloadにない応答ではnullとし、同じPID・RPC ID・方向で要求と対応付けます。単調時刻はその実行中の順序確認用で、日時ではありません。outはstdout書込み直前の観測であり、IDEでの受信・描画成功を保証しません。stdoutはprotocol専用です。子へ環境変数を継承せず、認証値や全環境を記録しません。payload自体は保存するため、明示した合成入力だけを送信してください。

permissionシナリオは選択の許否にかかわらず合成end_turnを返します。取消応答（outcome: cancelled）にはcancelledを返し、その後のsession/cancelで二重終端を返しません。「今回は拒否」で正常終了するこのfixtureの挙動を、実providerの拒否挙動と扱わないでください。Stop/cancelは別の取消経路です。正常配送の試験にcancelや子process releaseを代用しません。

## 制約と片付け

外部fakeが止められるのはprompt受信後です。通常受理直後/context構築前と、ticket作成済みactual dispatch直前を停止する機構はありません。queue一覧pauseで作れる広い窓とexact raceを分け、後者は#24へ未達として残します。

後日のGUI接続前に、元の実行ファイル設定・transport設定を控えます。実行終了時は、このrunで起動した会話/所有processだけを終了し、終端とPIDを確認して記録を保管します。担当者が元設定へ戻し、合成会話を実provider会話として再利用しないでください。headlessではstdinを閉じて終了を待ち、タイムアウト時もその所有PIDだけを停止します。他のIDE/processを停止しません。captureは合成内容のローカル証拠として保存し、自動公開しません。復元先を変えたときはlauncher/configを流用せず、ソース/markerを照合して新runを生成します。原素材は削除しません。

#258の停止済み実commit `86934b13a81243b2772daf6a07f20dbf2ad6f95a` を通常mergeして祖先を保持しています。元developは `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。実行時は当該checkoutのHEAD、launch.json、capture、使用したPlugin ZIPのhashを一緒に記録します。ソースが変われば新しいlauncherを生成し、製品の差分がある候補は新しい固定buildで確認します。2026-09-19: #261の統合保留は終了し、現在は通常のIssue PR統合手順に従います。製品build/GUIの受入は既存#24 Caseに残し、本toolingのCLI成功で転記しません。

## headless回帰

```bash
python3 -m unittest discover -s scripts/loop -p test_acp_fixture.py
```

この検査は一時的な合成workspaceだけを使用し、終了後に削除します。Pluginと同じ `launcher acp` 起動、initialize/session-new、permission保留、拒否回答後のend_turn、cancel/終了、同rootの2プロセス分離、誤引数/root/marker/固定hashの拒否、既存ログ非上書きを確認します。IDEは起動せず、永続記録が必要なGUI試験とは別です。


## #310の有限シナリオと製品への接続

prepareに `--scenario <name>` を追加するだけで、同じ固定launcherを生成します。指定なしは従来のpermissionです。旧#288の生成済みlauncher/configは変更せず保全し、#310の新sourceでは新しいrun directoryを準備します。launch.jsonにはscenario、Python/adapter/fake/marker、printの場合は既存02/03入力のSHA-256が入ります。prepare後に固定ファイルが変わると起動を拒否します。

`--version`以外の実起動ごとに `capture/control-<PID>/` を排他作成します。releaseの目印は**そのPIDのcontrol directory**へ置き、workspace直下や別runのdirectoryへ置きません。stdout/stderrにユーザー環境をダンプしません。captureは合成prompt/argvを含むので、既存の通常会話を再開せず、新規の合成会話だけを接続します。

| シナリオ | 入力・期待する観測 / QA | 解放と終了 |
|---|---|---|
| permission（既定） | #24/#105/#152/#160/#182のpermission保留。質問への回答前後に広い操作時間を作る | 実permission回答でend_turn、session/cancelでcancelled。回答IDは各要求で更新し、前turnの遅い回答を次turnへ流用しない |
| normal / eof / bad-config | #152/#246: はい×2の本文、prompt直後EOF、指定modeへ反映されないconfig応答。bad-configはAgent側の設定不一致拒否を観測する材料 | normalはend_turn。eofはprocess終了。bad-configは呼出側の拒否後stdinを閉じる |
| cancel / child | #152/#239: running表示後の取消。childは所有子processとそのstdout保持も含む | session/cancelへすぐ回答。child-ready/child.pidで対象を記録し、同controlのrelease-childで子を終える。Plugin Stopが子を止めた結果と、操作者が解放した結果を区別 |
| commands-delayed | #152 START/SETTINGS: new-readyからsession/new応答まで保留。context構築前を固定したことにはならない | 同controlのrelease-newをnew-readyから10秒未満で作成。操作先を事前に準備し、期限を逃したら新規run/会話でやり直す。stdin loopを塞がず、cancel/EOFで待機を終了。session/new前のcancelには架空のprompt応答を返さない。catalog置換はreplace-commandsで解放 |
| events | #152/#239/#246/#47: thought→tool開始→部分content/locations更新→completed→配列消去→本文。既存AcpProtocolTestの形を再利用 | events-ready後にrelease-events。cancelで保留送出を打ち切る。開始済みの通信とEDT待ちcallbackは外部fixtureでは厳密に停止できない |
| questions / plan | #152 REQUESTS: 既存cursor/ask_question・cursor/create_planの有限要求 | 実カードの回答を待つ。回答結果によらず合成end_turn、取消はcancelled。実providerが選択を解釈する挙動は再現しない |
| print-usage / print-missing / print-partial | #106 TOKEN-PANEL-HIERARCHY-1・#105: 28791/141/5748/0、usage全欠損、outputTokens=0のみ。欠損を0へ読み替えない | Result後に正常終了。旧usageが既にEDT待ちの厳密な瞬間は固定不可 |
| print-repeat / print-result-only | #246 TEXT-CONTRACTS・#49/#132: はい×2・emoji×2の増分とflush、またはassistant eventなしのResultだけ | Result後に終了。合成版は#254の2026.09.10-fd3934aを返す。実CLI版の観測証拠ではない |
| print-tools | #40 MV021–023/025/029/030・#47/#102/#105/#132: tool前flush、既存02編集/03shellのstarted→completed→同call重複、tool後本文 | **eventだけを再生しdisk編集・shell実行はしない**。既存02/03形式を有限置換し、編集先は合成root/a.txt、前後はhello\n/world\n |
| print-error / print-abnormal / print-hold | #104/#246/#49: is_error、stderr＋exit7、改行付きResultを受信した後の物理終了待ち（#289の既存実process素材） | holdはstdout上のResult受信と生PIDを確認後、同controlのreleaseで終了。result-writtenだけではクライアントの受信証拠にしない。Stopは所有processを停止。Result到着/書込完了を終了扱いにしない |

### printのargvと固定候補

製品と同じ順序で `-p --output-format stream-json --stream-partial-output --trust --workspace <固定root>`、任意の `-w`・`--resume <id>`・`--model <id>`・`--mode ask|plan`・`--auto-review|--force`・`--sandbox enabled|disabled`、最後に合成promptを受け付けます。重複・未知option・異なるworkspace・矛盾するpermissionは拒否します。これらは記録される引数であり、隔離worktree作成や承認・shell処理を実行しません。GUIでは実利用のresume IDやmodel credentialsを入力せず、合成値だけを使います。

存在しないlauncherや実行権限のないpathは、製品側が実agentを自動検出する恐れがあります。GUI担当は準備済みlauncherの存在/実行権限・固定hash・scenario・rootを確認してから設定します。失敗を回避するために本物agentやnpxへ差し替えません。

#310は公開STOP288を通常mergeしたtooling差分です。#254/#289の公開sourceは入力形式の参考として再利用し、製品実装のコピー/再実装や不要な依存mergeはしません。GUI担当はPMが選ぶ統合候補に#254のprint正規化や対象QAの製品変更が含まれることを確認し、候補HEAD/ZIP hash/ロードJARとlaunch.jsonの固定sourceを組にして記録します。準備branch単体に全QAの製品実装が揃っているとは主張しません。

### diskの前後状態・復元と記録

#47の保全素材は元のまま、新規の使い捨てコピーへ展開し、2root/外部sentinel/期待本文の対応を別紙に記録します。print-toolsで既存02形式を観測する場合は、まずコピーのa.txtをhello改行の状態にする→製品checkpoint取得を伴う開始→event完了後に、指定担当がそのコピーだけをworld改行へ変更→Diff/Revertの前に実diskのafter一致を確認する順です。**eventだけの再生成功を実編集成功にしません。** 自動再生が古いafter/未保存/空/欠落を作ったとは扱わず、それぞれ既存#47素材の条件を設定・記録します。eventとdisk更新の厳密な同時刻が必要なCaseは未準備のまま残します。

ACP eventsのdiff本文はbefore/after、最後の明示空配列で消去します。これは部分更新/全置換を確認するシナリオで、上記printのhello/worldやC47のtyped-planと混ぜません。Diff/Revertを操作するならrelease前後のcapture/画面/実ファイル状態を一致させる必要があります。

終わったら所有会話を止め、stdin EOF→所有PID終了を確認、必要時だけそのPIDへTERM→KILL。child.pidがある場合は所有子の終了とpipe EOFまで確認します。GUIの元設定へ戻す→対象試験コピーの変更を元のfixture baselineへ戻す→外部sentinelが不変であることを照合→所有記録を保管、の順で引き継ぎます。元保全資料や他processを削除・停止しません。

release-newは製品のsession/new待ち上限20秒より短い10秒で打ち切ります。events/print-holdは最大300秒です。newのtimeoutは合成error、eventsはcancelled、printはexit64として記録し、通常完了へ読み替えません。stdioの実通信成功、capture out、実IDEでの受信・描画、実providerの動作は別々の証拠です。受理後context前、queue済みEDT callback、復元予約取得直後の内部I窓はこの準備では固定できず、既存Caseに未観測で残します。

2026-09-20追補: print-holdは改行付きResultを生PID中に配送します。最終改行なしResultのEOF処理は別の既存print-result-onlyで確認します。取消応答→session/cancelという実クライアントの順序もcancelledとして扱い、遅延session/newは10秒で終了して製品の20秒timeoutと混同しません。
