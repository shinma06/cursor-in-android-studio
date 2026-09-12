# 合成ACP fixtureをGUI試験へ接続する（#288）

#24のcontext payloadと予約登録後のsnapshotを、既存ACP fakeのpermissionシナリオで観測するためのローカル準備です。実Cursor/providerへの接続、IDE設定の自動変更は行いません。合成通信成功と製品GUI・実provider・元固定ZIPのPASSは別です。

現在のDraftではadapterと拒否境界の検査が先行しています。#258の固定STOP・所有引継ぎ・実commit通常merge後にfakeの分離記録を追加するまで、GUI接続に使わないでください。新サーバー、独自release、Controller/EDT停止は追加しません。

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

分離captureは#258引継ぎ後の実装待ちです。既存Harnessの引数とwire.jsonl形式を維持し、合成marker確認済みの明示capture実行だけPID別にdirection/単調時刻/RPCとsession識別/JSON payloadを記録する予定です。stdoutはprotocol専用とし、認証値や全環境を記録しません。

permissionシナリオは回答の許否にかかわらず合成end_turnを返します。「今回は拒否」で正常終了するこのfixtureの挙動を、実providerの拒否挙動と扱わないでください。Stop/cancelは別の取消経路です。正常配送の試験にcancelや子process releaseを代用しません。

## 制約と片付け

外部fakeが止められるのはprompt受信後です。通常受理直後/context構築前と、ticket作成済みactual dispatch直前を停止する機構はありません。queue一覧pauseで作れる広い窓とexact raceを分け、後者は#24へ未達として残します。

実行終了時は、このrunで起動した会話/所有processだけを終了し、終端とPIDを確認して記録を保管します。他のIDE/processを停止しません。captureは合成内容のローカル証拠として保存し、自動公開しません。復元先を変えたときはlauncher/configを流用せず、ソース/markerを照合して新runを生成します。原素材は削除しません。

#258の停止済み実commitを通常mergeして祖先を保持します。#261統合保留中はenroll/mergeしません。製品build/GUIの受入は既存#24 Caseに残し、本toolingのCLI成功で転記しません。
