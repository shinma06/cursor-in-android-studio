# #284 Cursor診断用Request IDの公開取得契約

調査日: 2026-09-12。基準: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。
対象: [#284](https://github.com/shinma06/cursor-in-android-studio/issues/284)、親 [#25](https://github.com/shinma06/cursor-in-android-studio/issues/25)。

## 採否

**printで正常終了した最新turnの `result.request_id` は製品採用候補にできる。ACPの構造化取得は保留する。** CLI公式の省略可能fieldを2回の合成正常応答で観測した。会話IDは同じでもRequest IDは変わった。これは全失敗経路・全generation・Cursor IDEメニューとの値一致を証明するものではない。

製品コードは変更しない。PMが採用する場合は別Issueでparser→turn/tab所有→明示copy→GUI受入を実装する。メニューだけ、代用ID、ACPからprintへの自動fallbackは追加しない。有限調査完了と親#25・#154 M03の製品同等UX・GUI/main完了は別に判定する。

## 値の意味と公開経路

[Cursor診断案内](https://cursor.com/help/troubleshooting/reporting-bugs) はRequest IDをrequestごとのbackend調査用識別子とし、対象会話のメニューからCopy Request IDを選ぶ手順を示す。Privacy Modeはrequest時点の記録に作用し、後の設定変更で過去の可視性は変わらない。今回は設定を変更せず、問い合わせも送信しない。

| 面 | 公開根拠/観測 | 今回の結論 |
| --- | --- | --- |
| Cursor IDE内panel | 上記診断案内と [固定#154 M03](https://github.com/shinma06/cursor-in-android-studio/blob/4ba17ebefc9f340a29cce014f8a43d657144d8e5/docs/research/cursor-agent-menu-ux-2026-09-10.md) | 固定Cursor 3.19.19/commit `6496ea8a068aebfcd21990e70ff522e9abf10c80` は対象会話の最新generationの診断IDを使う。vendor action名は公開通信APIではない。今回実GUI/値一致未確認 |
| interactive CLI | [slash資料](https://cursor.com/docs/cli/reference/slash-commands) の `/copy-request-id` は最後のrequest IDをclipboardへコピー、`/copy-conversation-id` は別操作 | 対話操作は存在。値をstdoutへ返す純粋な取得APIと仮定しない。今回clipboard操作0 |
| print JSON / stream-json | [公式出力仕様](https://cursor.com/docs/cli/reference/output-format) はterminal resultの `request_id` をoptionalとして掲載、`session_id` は別field | stream-jsonで取得を観測。省略は正常な入力として扱う。JSONモード自体は今回未実測 |
| ACP | [Cursor ACP資料](https://cursor.com/docs/cli/acp) と下記schema。既存fresh広告に `copy-request-id` あり | 広告の説明はclipboard操作。診断IDを返す構造化field/専用取得methodは未確認。実行せず保留 |

[公式Forumの2026-07-06回答](https://forum.cursor.com/t/deterministic-edittoolcall-json-parse-failure-on-cursor-agent-when-anthropic-tool-use-is-served-from-bedrock-vertex-path-succeeds-identically/164927/5) は、診断案内へリンクしつつJSON responseの `request_id` を提示して報告を求めている。これはprint fieldを診断情報として扱う補助根拠である。回答内の実IDを本資料へ転載しない。任意のHTTP header、エラー本文の `reqId`、モデル自身が書いたUUIDを同じ供給元へ拡張しない。

### 混同しない識別子

| 識別子 | 役割 | Copy Request IDへの扱い |
| --- | --- | --- |
| print `result.request_id` | Cursor CLIがその結果に供給したrequest ID | 検証した取得元。成功turn限定の最小採用候補 |
| print `session_id` / ACP `sessionId` | provider会話の継続 | 代用しない。今回2turnで同一 |
| ACP JSON-RPC `id` / `$defs.RequestId` | RPC要求と応答の照合 | 代用しない。文字列/数値/nullを許すRPC型であり診断IDの型ではない |
| `toolCallId` / print `call_id` | tool進捗/完了の対応 | 代用しない |
| `model_call_id` | print partial outputのflush識別 | 診断generation IDと仮定しない |
| Plugin tab/conversation/turn ID、controller generation | ローカルのUI所有/遅着拒否 | 対応付けには使うがコピー値にしない |

## 再利用した広告とschema

[#278固定記録](https://github.com/shinma06/cursor-in-android-studio/blob/7e1a5142afc70bbc585f8270db4b3257c9cd3d86/docs/research/issue-278-midturn-contract.md) の同日・同版取得済みhelp/fresh session広告を再利用した。name=`copy-request-id`、description=`Copy the last request ID to clipboard` の1entryを確認。これはcommandの発見であり、実行時の返却値・成功・対象generation・clipboard副作用の実測ではない。[ACP slash仕様](https://agentclientprotocol.com/protocol/v1/slash-commands) は広告commandを通常promptへ含める経路を定めるが、任意commandが診断値を返す保証ではない。今回の実clipboard禁止範囲を守り、回避用のclipboard取得/復元も行わない。

- CLI版 `2026.09.10-fd3934a`。root help SHA-256 `be0388f7e15063e8c147ea5759e789a7f2ac87f95727dd596e1d8253e32b378b`、ACP help `9b55e20ac13686f2854b5fa7da3fd03a007be1b3c5cc9ba434f53596c45993af`。
- [schema固定commit](https://github.com/agentclientprotocol/agent-client-protocol/tree/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1) は `bcb9d7ea13adc0b47e906c82f3d692d495a6fa34`。stable SHA-256 `caf62ff962ada396878372ced11efb2c6764e59d90919a38583c319948931a42`、unstable `bf7d01218c4fc330b4f08840bda168dca5525951cdaf2dc32e58b9a5b4a8eb75`。
- stable `PromptResponse` は `stopReason,_meta`、unstableは `stopReason,usage,_meta`。両方の `SessionNotification` は `sessionId,update,_meta`。診断ID fieldを確認できなかった。
- schemaに存在する `RequestId`、`ElicitationRequestScope.requestId`、`CancelRequestNotification.requestId` は明示的にJSON-RPC要求の照合/範囲/取消に用いる。名前検索のhitをCursor診断IDの対応と誤読しない。未知の `_meta` 値にも意味を仮定しない。

## 有限のprint観測

既存#278の著者合成print結果では `request_id` が空でないstring、`session_id` と異なることを値非表示で確認した。次に新しい空fixtureで **2 promptのみ** 実行した。既存の私的会話や#146原本は使っていない。

手順: CLI版を再確認し、`-p --output-format stream-json --stream-partial-output --trust --mode ask --model 'composer-2.5[fast=true]'` で正常応答を得る。1回目は新規、2回目は1回目の `session_id` をメモリ内で `--resume` に渡す。promptは「この合成検査では ID284_1（2回目はID284_2）だけ返す。ファイル/ツール/外部データ/委譲/繰り返し実行は使わない」。各90秒、timeout時はowned process groupへTERM→5秒後必要ならKILLで上限を設けた。今回timeout/停止は発生しなかった。

| 観測 | P1 新規 | P2 同会話resume |
| --- | --- | --- |
| exit / terminal result数 / `is_error` | 0 / 1 / false | 0 / 1 / false |
| 指定回答 | 一致 | 一致 |
| `request_id` | 存在、string、空でない、UUID形状 | 存在、string、空でない、UUID形状 |
| request IDとsession ID | 異なる | 異なる |
| 非result eventの `request_id` | 0 | 0 |
| event数: system/user/thinking/assistant/result | 1 / 1 / 3 / 4 / 1 | 1 / 1 / 3 / 3 / 1 |
| tool event / stderr bytes / non-JSON行 | 0 / 0 / 0 | 0 / 0 / 0 |

P1/P2で **session IDは同一、Request IDは異なる**。fixture内ファイル0。取得helperはIDとraw応答をディスクへ保存せず、メモリ内で比較後、上表の型/有無/同一性だけを残した。CLI/provider自身の会話保存挙動は変更・削除していない。実IDを公開しないため、公開表の独立reviewはraw独立再生やbackend照会ではない。

2回ともUUID形状だったことをproviderの必須形式・長さ・永続性の規約へ一般化しない。複数model call、retry、失敗、Stop、別tab、IDEのCopy Request IDとの直接照合は未実測。新規provider接続はこのprint2回だけ、追加ACP接続/GUI/clipboard/feedback/認証設定変更は0。失敗field探索を目的にエラーを起こさない。

## 製品に渡す最小契約（未実装）

### 取得・型・既存経路

[StreamJsonParser](../../src/main/kotlin/com/cursoragent/parser/StreamJsonParser.kt) の `StreamEvent.Result` は現在request IDを保持していない。[AgentProcessService](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt) はResultを本文/usage/sessionへ渡し、実際のprocess終了で [AgentRun](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt) を終端化する。最小案はこの既存経路へ明示的なnullable `String` fieldを足すことであり、RPC clientや診断registryを新設することではない。

[提案する入力条件] 採用元はprintの構造化terminal `result.request_id` のみ。JSON stringであること、空/空白のみ/前後空白/制御文字を含まないこと、UTF-8で1,024 bytes以下を検査し、不正ならそのfieldだけ未取得扱いにして本文・usage・正常終了を失わせない。1,024 bytesはクライアント側の上限案でありprovider仕様ではない。数値/boolean/object/arrayをstringへ変換しない。UUID限定regex、短縮、大小文字変換、trimによる修正をしない。原文の受理済みIDだけをコピーする。

### turnとtabの寿命

[SessionTabs](../../src/main/kotlin/com/cursoragent/session/SessionTabs.kt) の `SessionRunToken` / `accepts` と [AgentUiController](../../src/main/kotlin/com/cursoragent/ui/AgentUiController.kt) のgeneration、[AgentTurnListenerFactory](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt) のEDT再検査を再利用する。providerのgeneration IDとPluginのgeneration連番を同一視しない。

| 状態/操作 | 最小案の表示・保持・copy条件 |
| --- | --- |
| 空会話/保存本文だけの再表示/ACP | ID未取得。コピー不可、理由をtooltip/メニュー内に表示。過去IDを推測/復元しない |
| 新しいturnの受理 | 当該tabの前turn IDを現在のコピー対象から外す。取得中でコピー不可。AのIDをBの新しい応答の診断IDとして残さない |
| 有効Result到着 | 所有token/generation/session対応を確認しそのturn内で仮保持。Resultだけではcopyを有効化しない |
| 正常な物理終了 | 現在token・停止なし・成功Result・exit 0をEDTで再確認し、finishTurnによるtoken無効化より前に当該tabへ確定。最新の完了応答のIDとしてcopy可能 |
| optional欠損/不正/起動失敗/通信失敗/Stop/未確定 | copy不可。前turnの値にfallbackしない。現在の成功turn以外の診断取得は別受入で拡張する |
| 次turn/閉じたtabへの遅着 | `accepts`/generation/disposedを再検査し破棄。現在選択tabへ流し込まない |
| tab選択変更/並べ替え | 値はtab IDに属する。操作時の選択tabとsnapshotを確認し、メニューを開いた後で選択が変わったら旧対象をコピーしない |
| resume | 新しいローカルturnとして上記を繰り返す。同じprovider sessionでも新request IDになる観測に対応 |
| 明示copy | 有効な選択tabの確定IDのみ `CopyPasteManager` へ。無効時/取消/失敗時に空文字や代用品で既存clipboardを上書きしない |

1turn内で矛盾する複数の有効Result/異なるIDを受けた場合は、最新を推測して採用せずそのturnをコピー不可とする。同じResultの再送は重複として扱える。これらは防御側の提案であり今回のprovider観測ではない。

初期案は開いているtabのメモリだけに置き、履歴・export・生ログ・通常通知にIDを自動追加しない。閉じて再表示した過去会話のコピーは初期対象外と明示する。将来履歴対応が必要なら#44/#45の保存契約へ別途追加する。clipboard APIのSDK選定は固定#154を再利用し、供給元の契約とは分ける。

## 競合と直接統合の上積み

Cursor IDE内panelの診断ID取得は既存能力であり、本Pluginの独自機能ではない。JetBrains AI Assistant + Cursor ACPは [Get ACP LogsとMCP受け渡し](https://www.jetbrains.com/help/ai-assistant/acp.html) があり、IntelliJ integration + [MCP Serverの利用可能tool](https://www.jetbrains.com/help/idea/mcp-server.html) を含む構成を比較対象にする。ログ取得APIやIDEのclipboard APIだけでCursor backend診断IDが供給されるとは限らず、確認した公開MCP一覧に専用取得契約は見つけられなかった。競合全体の取得不能とは断定しない。

[設計上の上積み候補] Pluginの対象tab/応答と、#28のbuild/接続診断を混同せず選択できること。Cursor応答の問題はCursor Request ID、Pluginの問題はPlugin版/再現手順という異なる情報を示す。ログ・本文・IDを自動送信しない。#41本文copy、#45export、#66会話名を今回の診断IDの代わりにしない。

## 採用後の受入と再評価条件

以下は製品Issueへ渡す必要Caseで、今回passにしない。固定buildでGUIを確認する。

| ID | 必要な確認 | 今回の状態 |
| --- | --- | --- |
| Q1 | 正常printで省略可能IDを取得し明示copy。2回目resumeは新しいID、session IDをコピーしない | provider正常2回のみ観測、製品/clipboard未実施 |
| Q2 | absent/null/number/bool/object/array/空/空白/control/過長/非UUIDの有効opaque string。field不正で本文や他fieldを失わない | 製品テスト未実施 |
| Q3 | 失敗/Stop/未確定/Result後異常exit、矛盾Resultで旧IDが残らずcopy不可 | 未実測・未実装 |
| Q4 | A/B tab、並べ替え/切替/close、次turnと遅着、メニュー中切替。対象誤り/他tabの上書き/clipboard破壊なし | GUI未実施 |
| Q5 | 保存本文再表示とACPは理由付き無効。単なるRPC ID、モデル本文内UUID、一般HTTP IDでは有効化しない | 製品未実装 |
| Q6 | Cursor ACPが診断値を返す公式field/副作用のない取得契約を提供したら、取得元・相関・Stop/再開・欠損を新しい合成fixtureで固定 | 専用供給契約未確認 |

Q6は既存copy commandの広告だけでは成立しない。実clipboard操作を含めて後日調べるなら、その操作を含む担当範囲/GUI leaseで改めて受入する。今回の禁止範囲を理由に迂回して値を拾わない。#146未公開fixture/owner/公開承認待ちと#66保留を維持する。

## 検証と完了境界

変更はこの文書と [GUI不要宣言](../verification/changes/issue-284.json) のみ。公開表は型・有無・同一性に限り、raw応答/実IDなし。共通Change Impact選択に従い、文書リンク・表と合成投影・help/schema hash・既存コード境界・既存Case不変を検証する。固定独立review/CI/停止SHAはPRとIssueのhandoffを正本にする。採用候補はPMへ引継ぎ、製品Issueの作成・統合・QA/main・cleanupはPMの管理とする。
