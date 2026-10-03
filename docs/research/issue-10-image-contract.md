# #10 画像添付のACP入力・補助print経路

2026-09-12 / base `9979266b4e99e7d4dc46689dac1f61f33f09b88a` / 調査PR [#275](https://github.com/shinma06/cursor-in-android-studio/pull/275)。

## 採用判断

ACP標準の画像bytes入力を採用し、製品UIは [#277](https://github.com/shinma06/cursor-in-android-studio/issues/277) に分離する。
固定CLI `2026.09.10-fd3934a`、`composer-2.5[fast=true]`、Askで合成画像3枚の色/形18マスをすべて識別した。
`image: true`という広告だけでなく、ファイル名に答えを含めない画像を入力して確認した結果である。
これはCursorの画像入力能力の実測であり、本Pluginの添付UI成功ではない。

printのprompt内path参照も成立したが、自動fallbackには採用しない。日本語pathをtool引数で別の文字へ変更した失敗、
未追跡tempがISOLATEDへ自動コピーされない失敗があり、保存場所・読取完了・削除時期がモデルのtool実行に依存する。
ACP/printの切替で画像を黙って落としたり別経路へ再送したりしない。

## 根拠と比較

- [ACP v1 Content](https://agentclientprotocol.com/protocol/v1/content) はprompt内画像にimage capabilityを要求し、`type: image`、base64の`data`、`mimeType`を定義する。`uri`は任意。
  固定一次schemaは [bcb9d7eのschema/v1/schema.json](https://github.com/agentclientprotocol/agent-client-protocol/blob/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1/schema.json)、SHA256 `caf62ff962ada396878372ced11efb2c6764e59d90919a38583c319948931a42`。PromptCapabilities.imageの既定値はfalse。
- [Cursor ACP](https://cursor.com/docs/cli/acp) の `agent acp` を使用。プロトコル版1とJSON-RPC 2.0を区別する。clientのfs/terminal能力はfalse、mcpServersは空、未対応requestには成功を返さない。全7 ACP turnでclient callback requestは0、stderrは0 bytes。
- [Cursor headless](https://cursor.com/docs/cli/headless) はpromptの相対/絶対画像pathをtoolで読む経路を記載する。`--image`の有無だけでは非対応と判断しない。同ページのファイル編集/force説明は本調査の根拠にしていない。

| 比較対象 | 確認した既存能力 | 本件の位置付け・限界 |
|---|---|---|
| [Cursor公式Agent入力](https://cursor.com/docs/agent/prompting) | 画像のD&Dとclipboard貼付 | IDE内Agent panelの基本操作に合わせる。独立Agents Window専用UIを移植する目的ではない。今回GUIの外観実測なし |
| [JetBrains AI Assistant](https://www.jetbrains.com/help/ai-assistant/chat-mode.html) | Add Image、複数画像、paste/D&D、1画像20 MBまでという説明 | 画像添付は既存能力。これはAI Chatの文書であり、Cursor ACPとの同一版live成功や同じ上限を保証しない |
| [AI Assistant + Cursor ACP + IntelliJ MCP](https://www.jetbrains.com/help/ai-assistant/acp.html) | custom/IntelliJ MCP ServerをAgentへ渡す設定、利用可能toolsを限定する設定 | 最強構成にはIDEの利用可能toolsを含める。今回画像probeはMCPなしで成功。MCP不要という一般論や競合非対応を導かない。対象IDEの全tool実体とGUI同等性は製品受入で照合 |
| 本Pluginの初回採用 | ACP画像1枚、preview/remove、明示的な失敗保持 | 1枚/小容量は初回制約であり競合同等以上を達成済みとはしない。Editor/下書き/実行ticketとの一貫性を狙うが、直接IDE統合による独自の上積みは本調査では立証していない |

## Fixtureと再現方法

[公開投影](issue-10-observations.json)、[4枚の合成PNG](issue-10-images/)、[最小検査](issue-10-check.py)を添付する。
PNGは600×400、白背景、先頭3枚は2行3列の色/形、最後は下部コード付き図形。標準画像libraryで生成し目視した。
ファイル名は識別用のopaque名であり、正解JSON・生成コード・投影はAgentのworkspace外に置いた。
ユーザー画像、私的履歴、#146原本、GUI、認証/課金設定の変更は使用していない。
公開投影はauthorがrawを照合して許可したfieldだけを抽出したもの。session/tool/blob ID、絶対host path、思考本文、base64、時刻を掲載しない。
Reviewerが公開投影を検査してもrawを独立閲覧したことにはならない。

再実測する場合は別の空fixture rootにPNGだけをコピーし、既存履歴を列挙せず今回生成したsession IDだけを再開する。
各呼出しの上限は120秒、timeout時はその呼出しのprocess groupを停止する。provider拒否を回避する再試行はしない。

1. ACP: `agent acp`を起動し、`initialize(protocolVersion=1)`→`session/new(cwd=fixture,mcpServers=[])`。
   `promptCapabilities.image == true`と`loadSession == true`を取得する。`session/set_config_option`で`mode=ask`、
   `model=composer-2.5[fast=true]`を設定し、返却configOptions.currentValueを確認する。
2. `session/prompt`のprompt配列へ下記のtextとimageを入れる。画像のURI/pathは送らずbytesだけを送る。
   後の2枚はbase64作成後、送信前にfixture内コピーを削除する。3回とも全6マスを正解JSONと照合する。
3. 別sessionでコード画像を送信前に同様に削除し「今は内容やコードを説明せずACKだけ」と指示する。
   process終了→新processで`session/load`→画像を再送せずコードを問う。さらに空の別rootで同じsessionをloadする。
4. 別sessionへ`base64("not a png")`を`image/png`として1回だけ送る。見えなければUNAVAILABLEと指示し、
   RPCエラー/stopReason/回答を別々に記録する。
5. print: `agent -p --output-format stream-json --stream-partial-output --trust --mode ask --model 'composer-2.5[fast=true]'`に
   同じ識別指示とJSON引用した画像pathを1つのprompt引数として渡す。shell展開しない。
   `--resume`はこのfixtureの新規IDのみ。ISOLATEDではPNG3枚だけをcommitしたremoteなしGitを作り、
   `--worktree i10-7c4e --skip-worktree-setup`を指定し、system/init.cwdと実readToolCall.pathの一致を確認する。
   未追跡コピーは`8f261b0d.png`（内容は3枚目）を使用した。

識別指示の中心部分:

```text
画像は2行3列です。左上から行順に6マスの色と形を読み、JSONだけで
{"cells":[["color","shape"],...]} と答えてください。
colorはred/blue/green/yellow、shapeはcircle/square/triangle。
ファイル名や以前の回答から推測せず、画像が見えなければ {"unavailable":true}。
```

ACP wireの構造例（値は説明用。公開投影と違ってliveそのものではない）:

```json
{"jsonrpc":"2.0","id":10,"method":"session/prompt","params":{"sessionId":"<new-fixture-session>","prompt":[{"type":"text","text":"<識別指示>"},{"type":"image","mimeType":"image/png","data":"<base64 of fixture PNG>"}]}}
```

## 実測結果

すべて上記の単一CLI版/modelでの観測。全モデル、写真/OCR一般、全platform、長期保持の保証ではない。

| Case | 入力/条件 | 結果と証明できる範囲 |
|---|---|---|
| A1–A3 | 同一ACP sessionへ3枚、A2/A3は日本語・空白pathのコピーをencode後削除 | 全18マス一致、すべてend_turn。image blockにpath/URIを含まないのでAgentの画像ファイル再読取に依存しない |
| A4 seed | 別ACP session、コード画像をencode後削除、ACKのみ要求 | 「画像を受け取りました。ACK」、end_turn。author照合でseedの回答/思考にコード文字列なし |
| A5 resume | processを新しくし同rootでload、画像なしでコード質問 | R8Q2、end_turn。元コピー削除/新接続後にも画像情報を利用できた。保存されたbytes自体や内部表現は不明 |
| A6 other-root | 空の別rootでA4のsessionを再load | R8Q2、end_turn。ただしA5の文字回答も既に履歴にあるため、別rootで画像そのものを再読取した独立証拠にはしない |
| A7 invalid | PNGではないbytes、mimeType=image/png | UNAVAILABLE、end_turn、RPCエラーなし。画像のdecode失敗が構造化拒否になる保証は得られなかった |
| P1 normal | 日本語・空白を含む相対path、1枚目 | read成功3707 bytes、6マス一致 |
| P2 resume | P1をresume、日本語・空白を含む絶対path、2枚目 | read成功3206 bytes、6マス一致 |
| P3 missing | P2の画像を削除、同sessionで現在のbytesを要求 | readToolCall error=File not found、unavailable。過去の正解を再利用しなかった |
| P4 ISOLATED tracked | commit済み1枚目の相対path | init.cwdとread pathが分離root、6マス一致。単にflagが受理されたという判定ではない |
| P5 other-root | P1を空rootからresume、同じ相対path | read欠損後に同root内globも実行、unavailable。検索禁止というpromptでもtool探索を完全に抑止できるわけではない。空fixture外のデータは扱っていない |
| P6 ISOLATED untracked | 元rootだけにある未追跡コピーの相対path | 分離rootでFile not found、unavailable。添付tempが自動で移るとは扱わない |
| P7 ISOLATED absolute | 存在する元rootの未追跡コピーを絶対path指定 | tool引数で「資料」を「资料」へ変更しFile not found、unavailable。Unicode pathの変形が失敗原因。正しい絶対pathのroot外アクセス可否は未判定であり、拒否/非対応と断定しない |

print 7回ともprocess exit=0、result.is_error=false、stderr=0。欠損を回答した正常終了と画像理解成功を区別する。
P4/P6/P7のsetup行はJSONではない（各1行）。その後のJSON tool/resultを別に検査した。
print読取成功のdataBlobIdは不透明なIDで、base64の画像内容や保持期間の証明には使わない。

## 現行製品への接続境界

baseの[GrowingPromptField](../../src/main/kotlin/com/cursoragent/ui/composer/GrowingPromptField.kt)は`EditorTextField`/`EditorEx`である。
汎用Swing TransferHandlerを全体へ置き換える設計にせず、対象editorだけのpaste/drop導線を選び、既存テキスト/IME/Undo/mention/slashを保つ。
[ComposerPanel](../../src/main/kotlin/com/cursoragent/ui/composer/ComposerPanel.kt)は`onSend(String)`で、submitは非空textだけを許可する。
[AcpSession](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt)の現行promptはtext blockだけ。
画像capability保持と不変の添付snapshotを、既存PreparedAgentTurn/Controller/sessionの流れへ最小限に追加する必要がある。

[AcpJsonRpc](../../src/main/kotlin/com/cursoragent/acp/AcpJsonRpc.kt)の送受信上限は1 MiB。
送信ではJSONと改行をUTF-8にしたbytesが上限を超えると**接続自体をfail**する。
base64は`4 * ceil(n / 3)` bytesになるため、PNGのサイズだけの検査ではtext/contextとの合計超過を防げない。
初回は上限を一律に引き上げず、実serializerの完全な送信frameをqueueへ入れる前に検査して下書きを保持する。
受信は改行前のline bufferを制限するため、送信上限と厳密な数え方は異なる。大きな履歴再生frameも独立して受入する。
本probeは製品AcpJsonRpcを通しておらず、製品の大画像/境界対応は未実装である。

## #277の最小UX契約（未実装）

1. **取得**: D&DのファイルPNG/JPEG、clipboardのimage flavorを対象に1枚。複数/非対応は理由を表示し、既存添付を勝手に置換しない。
   decode前に圧縮bytes、寸法、画素数を検査し、壊れた画像/権限不足/読取途中の変更をエラーにする。
   初回client policyは元ファイル10 MiB、各辺4096、合計800万画素、正規化PNG512 KiB以下。provider上限とは別であり、
   これを超える入力は差替えを案内する。縮小・切抜き・品質低下を黙って行わない。JPEG/clipboard成功は後続製品Caseで検証する。
2. **snapshot**: 読み取った内容を正規化したPNGへ固定し、project外の所有tempへ保存する。元ファイルを変更しない。
   symlinkの参照先を元データとして読む場合も、削除するのは所有tempだけ。tempのbasenameに元path/個人情報を含めない。
   ファイル読取/decodeはEDT外、UI更新は既存run/tab/dispose境界を確認してEDTへ戻す。遅着で別draftを上書きしない。
3. **確認**: 添付がある時だけ入力欄内にthumbnailを示す。preview/removeをkeyboard操作でき、代替ラベルとtooltipを日本語で付ける。
   通常のplaceholderと入力下の常設説明を増やさない。previewは送信snapshotと同じ内容。元画像の削除/後編集で変化しない。
4. **送信**: textまたは画像があれば送信可能。`image == true`を確認し、完全frameを検査して1回だけ送る。
   capability未知/false・print選択なら添付を保持して理由を示す。imageを落としてtextだけ送らない。
   送信中はsnapshotを固定し、同じ画像を別tab/sessionへ流用しない。接続/モデル変更時も能力と宛先を再確認する。
5. **失敗/取消**: 読取・能力・サイズ・通信・provider失敗は本文/画像を保持し、原因と再試行操作を示す。
   Stopや通信切断は既に送信された可能性を残し自動再送しない。`end_turn`は正常完了であって画像理解の保証ではない。
   正常応答が「読めない」だった場合にも再添付可能な導線を保ち、モデル本文から構造化通信エラーを捏造しない。

| 状態/操作 | 所有temp・snapshotの保持/削除 |
|---|---|
| decode/保存前に失敗 | 部分tempだけ削除、元画像/既存draftは維持 |
| 未送信draft・別tabへ移動・能力が不明/false | 所有tempを保持。tab移動だけで削除しない |
| 未送信remove/明示的なdraft破棄 | 対応する所有tempとメモリsnapshotを削除。他添付/元画像に触らない |
| in-flight | 不変snapshotを保持。経過時間だけで回収しない |
| 通信/provider失敗・Stop | 再試行用に保持。requestの到達不明と未送信を分け、自動再送しない |
| 正常end_turn | draftを解除し所有tempを削除。表示用thumbnailは会話UI寿命のメモリだけに保持。再度送る時は再添付 |
| project dispose | 実行停止/参照解放と整合して所有tempを削除。下書きを保存する場合は再添付が必要な印だけを保存 |
| 異常終了後の再起動 | 所有markerと停止ownerを確認できる残骸だけ回収。不明/他の生存ownerのファイルは削除せず理由を残す |
| 会話保存/再開 | base64やtemp絶対pathを本文/rawログへ保存しない。復元できない画像preview/再送には「再添付が必要」と表示 |

**local削除でprovider履歴を消したとは言わない。** A5は元コピー削除後も画像情報を利用できた。
履歴内部の画像bytes/派生表現、providerの保持期間/削除API、長期resume、履歴圧縮後の保持は未検証。
print補助経路を将来採用するなら、読取完了前にtempを消さず、error/Stop/再読取に必要な所有権を維持する設計が別途必要。
P7を修正する目的の無制限retryやpath書換え回避は本調査では行わない。

## 後続の受入と終了条件

#10は入力契約・有限probe・UX定義と採用Issue化まで。以下は全て**製品GUI pending**で#277が持つ。
固定buildで手順/期待結果/GPT・人間状態をCase化し、未達GUI/mainを専用QAへ引き継ぐ。

| Case候補 | 操作 | 期待結果 |
|---|---|---|
| I1 | PNG/JPEGをD&D、clipboard画像をpaste、通常text/IME/Undoも操作 | 1枚のsnapshot、既存入力を保持 |
| I2 | thumbnailをkeyboardでpreview/remove、画像のみ送信 | preview一致、削除後は送らない、空textでも送信 |
| I3 | 日本語/空白path、取込後に元画像を編集/削除 | snapshot不変、元画像を削除しない |
| I4 | 破損/権限不足/過大寸法/bytes/複数/非対応形式 | 接続を壊さず原因表示、既存draftを保持 |
| I5 | 完全frameが1 MiB境界/超過、長いcontext、大きい履歴再生 | 正確な事前検査、受信側も安全に失敗、本文回帰なし |
| I6 | capability false/未知、transport/model/tab変更、遅着 | 黙示drop/fallbackなし、宛先を混ぜない |
| I7 | 送信中Stop/通信切断/provider error/通常UNAVAILABLE | 成否を区別、再試行可能、自動二重送信なし |
| I8 | remove/成功/dispose/異常終了/reopen/保存再開 | 保持表どおりの所有tempだけを回収、再添付案内、remote削除を偽装しない |

CLI/ACP実測は全14 turnで終了。自作fixture専用ISOLATED worktreeとbranchはclean/固定HEADを確認して削除済み。
rawと元fixtureはauthorのprivate一時領域にのみ保持（owner: research、再確認後または#10レビュー/引継ぎ終了時に回収）。
他のworktree、既存セッション、認証、GUIに変更なし。製品画像UI・tool結果画像・音声#99は本PRに含めない。
