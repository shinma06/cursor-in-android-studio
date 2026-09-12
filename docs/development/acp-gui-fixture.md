# 合成ACP fixtureをGUI試験へ接続する（#288）

#24のcontext payloadと予約登録後のsnapshotを、既存ACP fakeのpermissionシナリオで観測するためのローカル準備です。実Cursor/providerへの接続、IDE設定の自動変更は行いません。合成通信成功と製品GUI・実provider・元固定ZIPのPASSは別です。

既存fakeのpermissionシナリオへ接続します。新サーバー、独自release、Controller/EDT停止は追加しません。今回実施するのはheadless検査のみで、後日のGUI接続には担当者・lease・固定buildの確認が必要です。

## 操作者向けサマリ

| 項目 | 手順 | 期待値 |
| --- | --- | --- |
| 準備 | #24原素材から別の使い捨てworkspaceを作り、合成データ専用のmarkerを置く。adapter準備コマンドを実行 | 新しいrun dirに固定launcher/config/capture dirができる。元資料は変更しない |
| 接続 | 後日、指定GUI担当が固定build/lease確認後にlauncherを実行ファイルとして指定し、新規ACP会話を開く | 起動は `launcher acp` のみ。違うroot/引数/marker/固定ソースでは失敗し実agentへfallbackしない |
| 保留 | 合成promptの受信後、合成permissionカードへまだ回答しない | 先行runを保留する間に予約登録と別draftの準備を観察できる。実GUIでは未検証 |
| 配送 | 予約一覧を開いてpauseし、元文/参照を変更。先行カードへ回答後、予約を明示再開 | captureでOLD明示selection/開始時NEW参照を照合。厳密なEDT競合窓の試験ではない |

## 準備

Python 3.9以上を使用します。prepareに使ったPythonの絶対pathをlauncherへ固定します。既存fakeのpathとSHA-256、adapterのpath/SHA-256、workspace実pathとmarker hashも固定します。ソース変更後や別の復元先では新しいrunを準備します。

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

launcherは固定Pythonでadapterを実行し、marker/cwd/固定fileを照合して既存fakeのpermissionをexecします。受け付けるPlugin引数は `acp` 一つだけ。metadata/print/追加引数は拒否し、環境変数のprovider資格情報を子へ継承しません。実Cursor/providerの検索・fallbackはありません。

`capture/wire-<PID>.jsonl` に1プロセス1ファイルで送受信を記録します。新規排他作成・権限0600で、PID再利用時にも既存記録へ追記しません。共有workspaceの `wire.jsonl` はcapture実行では作りません。従来の単体Harnessは追加引数なしのまま、従来どおり受信のみの `wire.jsonl` を使います。

各行は `direction`（in/out）、`pid`、`monotonic_ns`、`rpc_id`、`session_id`、元JSONの `payload` です。session IDがpayloadにない応答ではnullとし、同じPID・RPC ID・方向で要求と対応付けます。単調時刻はその実行中の順序確認用で、日時ではありません。outはstdout書込み直前の観測であり、IDEでの受信・描画成功を保証しません。stdoutはprotocol専用です。子へ環境変数を継承せず、認証値や全環境を記録しません。payload自体は保存するため、明示した合成入力だけを送信してください。

permissionシナリオは回答の許否にかかわらず合成end_turnを返します。「今回は拒否」で正常終了するこのfixtureの挙動を、実providerの拒否挙動と扱わないでください。Stop/cancelは別の取消経路です。正常配送の試験にcancelや子process releaseを代用しません。

## 制約と片付け

外部fakeが止められるのはprompt受信後です。通常受理直後/context構築前と、ticket作成済みactual dispatch直前を停止する機構はありません。queue一覧pauseで作れる広い窓とexact raceを分け、後者は#24へ未達として残します。

後日のGUI接続前に、元の実行ファイル設定・transport設定を控えます。実行終了時は、このrunで起動した会話/所有processだけを終了し、終端とPIDを確認して記録を保管します。担当者が元設定へ戻し、合成会話を実provider会話として再利用しないでください。headlessではstdinを閉じて終了を待ち、タイムアウト時もその所有PIDだけを停止します。他のIDE/processを停止しません。captureは合成内容のローカル証拠として保存し、自動公開しません。復元先を変えたときはlauncher/configを流用せず、ソース/markerを照合して新runを生成します。原素材は削除しません。

#258の停止済み実commit `86934b13a81243b2772daf6a07f20dbf2ad6f95a` を通常mergeして祖先を保持しています。元developは `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。実行時は当該checkoutのHEAD、launch.json、capture、使用したPlugin ZIPのhashを一緒に記録します。ソースが変われば新しいlauncherを生成し、製品の差分がある候補は新しい固定buildで確認します。#261統合保留中はenroll/mergeしません。製品build/GUIの受入は既存#24 Caseに残し、本toolingのCLI成功で転記しません。

## headless回帰

```bash
python3 -m unittest discover -s scripts/loop -p test_acp_fixture.py
```

この検査は一時的な合成workspaceだけを使用し、終了後に削除します。Pluginと同じ `launcher acp` 起動、initialize/session-new、permission保留、拒否回答後のend_turn、cancel/終了、同rootの2プロセス分離、誤引数/root/marker/固定hashの拒否、既存ログ非上書きを確認します。IDEは起動せず、永続記録が必要なGUI試験とは別です。
