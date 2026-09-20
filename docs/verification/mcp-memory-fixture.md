# QA49の専用Memory server準備（#312）

公式 `@modelcontextprotocol/server-memory@2026.8.31` の既成stdio serverを、合成試験専用のpackage/dataと解決済みlockへ固定します。Pluginの依存変更や独自MCP実装はありません。配置前の設定テンプレートを生成し、実Cursor CLI・GUI・通常設定を操作しません。単体疎通成功はMV-028の一覧/切替/復元のPASSではありません。

## 固定する配布物

- 公式source: `modelcontextprotocol/servers` の `a40bc270fb5ece62673f8a1196f57116d885c5eb`、entry pointは `dist/index.js`。
- npm版: `2026.8.31`、配布integrity: `sha512-ljj/3S4aGjxdNSQWw6gucKKGnTLdBPWxzapyY/MT2tOVyZwvxChvevXSLPwx59nKJAlVpUVW+cOlnVRXRwiqMQ==`。
- `fixtures/mcp-memory/package.json` / `package-lock.json` に95 packageを固定。SDKの範囲指定 `^1.30.0` はlockで `1.30.0` に解決済み。試験中にnpxで取り直しません。
- [公式固定package](https://github.com/modelcontextprotocol/servers/blob/a40bc270fb5ece62673f8a1196f57116d885c5eb/src/memory/package.json)、[公式storage/stdio実装](https://github.com/modelcontextprotocol/servers/blob/a40bc270fb5ece62673f8a1196f57116d885c5eb/src/memory/index.ts)、[README](https://github.com/modelcontextprotocol/servers/blob/a40bc270fb5ece62673f8a1196f57116d885c5eb/src/memory/README.md)を根拠とします。

## 新しい専用資材の準備

既存Nodeとnpm CLIの実ファイルを指定します。両方とも信頼できる導入済み配布物を使い、通常設定を読み込ませるlauncherは指定しません。prepareはNodeの絶対実path・版・SHA-256、npm版、package/lock/entry/依存実ファイルのhashを記録します。Node本体やOS共有libraryは複製せず、そのruntimeが後日同じpath/hashで使用可能であることを実施前条件にします。OS環境まで固定・隔離できたことにはなりません。

```bash
python3 scripts/loop/mcp_memory_fixture.py prepare --root /absolute/new-owned-run --node /absolute/node --npm /absolute/npm/bin/npm-cli.js
python3 scripts/loop/mcp_memory_fixture.py check --root /absolute/new-owned-run
```

rootの親directoryは先に存在し、root自体は未作成である必要があります。既存root/データ/symlinkは再利用しません。生成内容は次のとおりです。

| 内容 | 用途 |
|---|---|
| MARKER.json | 合成試験の所有rootと一意な `qa49-memory-<run-id>` を明示 |
| package/ | 追跡済みmanifest/lockをコピーしてnpm ciした公式配布物。install script無効、global installなし |
| data/ | 新規の空領域。`MEMORY_FILE_PATH`は絶対path `data/memory.jsonl`。未設定の旧memory移行を使わない |
| manifest.json | Node/依存/lock/entry/設定templateのhash、版、必要ファイル一覧。ローカル絶対pathを含むので自動公開しない |
| project-mcp.template.json | 配置前の `mcpServers` 設定。type=stdio、command=固定Node、args=固定entry、env=専用MEMORY_FILE_PATHのみ |
| cache/・空npmrc | 専用npm取得cacheと空user/global設定。通常npmrcや環境の認証値を使わない |
| evidence/ | install.txt、server-stderr.txt、check.json（合成wireと所有PID・終了結果） |

npm ciは追跡lockの公開registry URLとintegrityを使います。prepareに渡す環境は固定NodeのPATHとOS標準pathのみ。server単体checkの環境はMEMORY_FILE_PATHのみで、認証値・通常会話・環境全体を記録しません。Node/entryへの直接起動なのでshell/常駐launcherは不要です。設定templateを `.cursor/mcp.json` へ自動配置しません。

## 単体確認と今回の証拠

checkは固定runtime/package/templateのhashと空dataを照合してから、initialize（protocol 2025-06-18）→notifications/initialized→read_graph({})の3入力だけを送ります。応答は `memory-server / 0.6.3` と `entities: [] / relations: []`。npm版2026.8.31とwireの版0.6.3は別の値なので、wireだけで配布版を判定しません。mutation toolやtools/list、Cursor CLIは呼びません。

stdinを閉じ、所有processのexit0とstdout EOF、data無変更まで確認します。10秒で応答/終了しない場合はその所有PIDだけにTERM→5秒待機→KILLで片付け、正常成功を記録しません。MCPに独自shutdown requestを追加しません。既存証拠へ上書きせず、再測定は新runを準備します。

2026-09-13の所有runで、Node v26.8.2/npm 11.19.1、95 package、3,635実ファイルを固定し、上述の空結果・stdin EOFによるexit0・data無変更を確認しました。実provider/GUI/MV-028未実施です。

- Node SHA-256: `3e401401d90e7f0367b7737a613801242c9a6b2966fb50c2e48eb1c45d701c6a`
- lock SHA-256: `6aa0662facc7be762af9c9c788efab2a55c4e44dbe31ec5020da3b3f77aedd65`
- entry SHA-256: `e6a04d5457d2d7d3a8ba33bc8da891b212358dfe2f43548a5afec4ff259c4729`

この記録は試験資材の単体疎通です。後日のPlugin候補HEAD/ZIP/ロードJARと、当該runのmanifestを指定担当が照合します。package/runtimeの大きな配布物やローカル記録はGitへ含めず、準備手順と固定識別だけを共有します。

## Cursorで実施する前の未確定条件

[Cursor MCP設定](https://cursor.com/docs/mcp)はproject/userの併用、[CLI MCP](https://cursor.com/docs/cli/mcp)は親設定の発見や一覧/enable/disableを説明しています。projectを使い捨てにするだけでは通常のuser/global/親設定を排除できません。通常ユーザー設定を共有しない、許可された試験専用OSユーザー・VM等と、変更してよい設定範囲をPM/GUI担当が先に指定します。HOMEの変更や未確認config-dirオプションを隔離の証拠にしません。

実施担当が確定する値は、対象CLIの版/実path、非TTY一覧形式と出所、CLI本体の認証要否、enableによる承認状態の保存範囲、他projectへの影響、disable時に既存processが終了するか、です。Memory server自体に認証が不要でもCursor本体の認証不要を意味しません。現在の公式説明はinteractive一覧を含むため、古い `id: status` 素材を対象CLIの保証に使いません。未確定の間はtemplateを配置せず、実CLI/list/enable/disableを呼びません。全server/tool自動承認は追加しません。

## MV-028の復元と撤去（後日）

同じID・CLI・project・設定範囲を記録→初期有効状態S0を記録→反対へ切替→更新一覧で照合→S0へ戻して一覧で確認→対象外設定が不変と照合、の順です。S0が無効なら有効→無効、有効なら無効→有効。終了コード0やダイアログcloseを復帰の証拠にしません。

続いて、その試験で起動した所有processの終了を確認→新設した当該設定entryだけを、確定した設定保存先から担当者が撤去→所有記録を保存→専用data/package/cacheを含む所有rootのみを撤去します。S0復帰と新設資材撤去は別工程です。通常設定全体を古いbackupで上書きせず、他server/PIDを操作しません。失敗時は未復帰の項目と所有者をPMへ引き継ぎます。今回の準備ではtemplateを配置しておらず、通常設定の復元操作は不要です。

MV-029のツール行集約は#310/T1の別条件です。2026-09-19: #261の統合凍結は終了済みです。既存GUI待ち・main未反映を維持します。[stdio停止の根拠](https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle#shutdown)に従うserver単体停止と、実Cursorの停止動作の観測を分けます。

境界テスト: `python3 -m unittest discover -s scripts/loop -p test_mcp_memory_fixture.py`。既存root/データ、symlink、runtime/package/template/marker変更をserver起動前に拒否することを確認します。ネットワークや公式serverを再取得しない検査です。

2026-09-20: npm版取得にもinstallと同じ専用cwd・空user/global npmrc・prefix/cacheを適用しました。package内symlinkは内部の実ファイルへの参照だけを許し、リンク先文字列と実体hashをmanifestへ含めます。追加・差し替え・外部/壊れた参照をserver起動前に拒否します。旧manifestを書き換えず、新runをprepareしてください。7件の境界テストと新しい専用runで、3,635実ファイルと2リンク、空graph・stdin EOF exit0・data不変を再確認しました。実Cursor/GUIは未実施です。
