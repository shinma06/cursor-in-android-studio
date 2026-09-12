# #149 ACP/print usage契約の実測調査

2026-09-12 / base `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。
Issue [#149](https://github.com/shinma06/cursor-in-android-studio/issues/149)、親 [#141](https://github.com/shinma06/cursor-in-android-studio/issues/141)、
[設計K10/T09/X06](acp-feature-migration-2026-09-09.md)。

**固定CLIの合成3turnではACP usageは未観測、printは各resultに4種類のcounterを返した。**
現時点ではprintの既存表示を維持し、ACPのcontext占有率・cost・詳細token数を推定しない。
今回の完了範囲は実usage契約の調査・合成境界fixture・後続Case。製品source・共有architectureは変更していない。
既存print表示の実装 [#71](https://github.com/shinma06/cursor-in-android-studio/issues/71) は再claimしない。
#149の製品接続・固定build表示受入は未完了であり、この資料PRだけでcloseしない。

## 1. 三つの契約と集計範囲

公式schemaは2026-09-12にcommit `bcb9d7ea13adc0b47e906c82f3d692d495a6fa34`へ固定した。
wireはACP v1 / JSON-RPC 2.0。schema package版やv2の仕様とは区別する。

| 経路 | 契約上のfield/意味 | 表示への条件 |
|---|---|---|
| stable `session/update` → `update.sessionUpdate="usage_update"` | `used`/`size`: 現在のcontext token量/容量、required uint64。任意`cost.amount`/`cost.currency`: session累積cost/ISO 4217通貨 | 妥当なused/sizeだけ率にする。costは最新snapshotであり、通知ごとに加算しない。account残枠やcategory内訳ではない |
| unstable `session/prompt` response → `usage` | `totalTokens`/`inputTokens`/`outputTokens`必須、`thoughtTokens`/`cachedReadTokens`/`cachedWriteTokens`任意 | stableにはない実験的field。printの`cacheReadTokens`/`cacheWriteTokens`と名前も異なる。構造・scopeを確認するまで既存「直近の応答」へ直結しない |
| print `result.usage` | 実測`inputTokens`/`outputTokens`/`cacheReadTokens`/`cacheWriteTokens` | そのresultで報告されたcounterとして別々に表示。context容量・占有率や料金へ換算せず、入力とcacheの合算も行わない |

stableの定義は[固定stable schema](https://github.com/agentclientprotocol/agent-client-protocol/blob/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1/schema.json)、
unstableは[固定unstable schema](https://github.com/agentclientprotocol/agent-client-protocol/blob/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1/schema.unstable.json)を参照。
後者はPromptResponse/Usage全体ではturnと説明する一方、個別fieldではsession全体・全turn累積と説明している。
**[要検証] 集計範囲にこの曖昧さがあり、名前だけからturn累積・session累積を決めない。**
今回Cursorは当該fieldを返しておらず、実値による解決もできていない。

schema SHA-256: stable `caf62ff962ada396878372ced11efb2c6764e59d90919a38583c319948931a42`、
unstable `bf7d01218c4fc330b4f08840bda168dca5525951cdaf2dc32e58b9a5b4a8eb75`。
公式[Cursor ACP](https://cursor.com/docs/cli/acp)と[print output format](https://cursor.com/docs/cli/reference/output-format)には、
今回確認したページでusage fieldの説明を見つけなかった。これはCursor全体の非対応証明ではなく、実wire確認が必要な理由である。

## 2. 制御した実測と再現条件

新規の空workspaceと新規sessionだけを使い、既存履歴・#146原本・利用者のprojectを読まない。
認証・課金・MCP公開・GUI設定を変更せず、既存CLI認証で実行した。provider制限を迂回する再試行は行っていない。
public投影と合成境界値は[fixture JSON](issue-149-fixtures.json)。実session/request ID、ローカルpath、会話の思考本文は含めない。
rawと公開値の一致、session同一性、全受信frameのusage不在は著者が照合した証拠であり、Reviewerによるraw確認とは区別する。

| 固定条件 | 値 |
|---|---|
| CLI | `2026.09.10-fd3934a`（実行直前の`--version`） |
| mode / client capabilities | ask。ACP client fs read/write=false、terminal=false、`mcpServers=[]`。client tool requestは0件 |
| turn 1–2 | `composer-2.5[fast=true]`。print init表示 `Composer 2.5 Fast` |
| turn 3 | `gpt-5.3-codex[reasoning=medium,fast=false]`。print init表示 `Codex 5.3 Medium` |
| session | ACP内で同じ新規sessionを3turn、print内でも別の同じ新規sessionを3turn。二つのtransportのsessionは別 |
| 観測上限 | 各RPC/print processは120秒。ACPは各prompt response後も2秒受信し、その範囲を記録 |
| 結果 | ACP 3件とも`end_turn`。print 3件ともexit 0 / `is_error=false`。全stderr空、tool callなし、workspaceは終了後も空 |

再現時のACP順序は`initialize` → `session/new` → `session/set_config_option(mode=ask)` →
`session/set_config_option(model=上記turn 1のID)` → prompt 1/2 → model変更 → prompt 3。
設定responseの`configOptions.currentValue`を各回照合する。
CLIの短名`composer-2.5`をACP model設定へ渡した最初のpreflightは、`-32602 Invalid model value`で拒否され、promptは0件だった。
そのrecordを保持して、新sessionの広告済み完全IDで訂正した。未許可modelの利用を強制したものではない。
model一覧の表示名やprint aliasをACP IDと同一視しない。

printは`agent -p --output-format stream-json --stream-partial-output --trust --mode ask --model <完全ID> <prompt>`。
2/3回目だけ、1回目で新規に得たsession IDを`--resume`へ渡す。`--continue`や既存session一覧は使わない。
`--force`/`--auto-review`なし、print initは`permissionMode=default`。下記以外のデータをpromptへ含めない。

- prompt 1: `Reply exactly I149_A. Do not use tools, read files, delegate, or access external data.`
- prompt 2: `Synthetic data: ` + `item0`から`item127`を半角spaceで結合 + `. Reply exactly I149_B. Do not use tools, read files, delegate, or access external data.`
- prompt 3: `Reply exactly I149_C. Do not use tools, read files, delegate, or access external data.`

### 観測値

| 経路/turn | input | output | cacheRead | cacheWrite | context used/size | cost |
|---|---:|---:|---:|---:|---|---|
| ACP a1/a2/a3 | 不明 | 不明 | 不明 | 不明 | 未提供 | 未提供 |
| print p1 | 11696 | 129 | 6720 | 0 | 未提供 | 未提供 |
| print p2 | 398 | 63 | 18528 | 0 | 未提供 | 未提供 |
| print p3（model変更後） | 17497 | 7 | 0 | 0 | 未提供 | 未提供 |

ACPは受信32 frame / 送信8 frame。受信updateはmode 1、available commands 1、session info 1、thought 14、message 7。
`usage_update`は0件、3件のPromptResponseは`stopReason`だけで`usage`を持たない。
「usage frame数0」は「使用token数0」ではない。観測時間外・別CLI/modelでの提供可否は未確認。

printは`result`にmodel fieldがなく、実modelは同じturnの`system/init`で確認した。
同一session内でinput/outputがp1→p2で減少しており、単純なsession累積counterとして扱う根拠はない。
p2ではcacheReadがinputより大きいが、これだけでtoken内訳の加算関係を確定しない。
context容量をmodel ID中の`context=272k`や表示名から補完することも行わない。

## 3. 既存経路と最小の接続先

baseにおけるprint経路は`StreamJsonParser` → `TokenUsage.parse` → `AgentProcessService.onTokenUsage` →
`AgentTurnListenerFactory` → `ContextUsageView/State`。
`TokenUsage`は非負の整数Longだけをfieldごとに受け取り、欠損・不正値をnullにし、0は保持する。
Viewは4行を個別に表示/非表示にし、静的アイコン・開閉・日本語・「直近の応答」を持つ。合計・占有率は作らない。
`beginTurn/reset`のticket、Controllerのturn generation/session token、EDT直前のcurrent/stopped判定で古いcallbackを落とす。

ACP側は`AcpProtocol.update`にusage分岐がなく、`AcpSession`のprompt response処理もstopReasonだけを見る。
今回のframe不在は**probeでも観測したprovider側の条件**であり、この製品parserが落としていることだけを根拠にした結論ではない。
将来接続するならACP境界でtyped snapshotを作り、既存event/表示へ必要部分だけ渡す。汎用計測基盤や新しい課金serviceは不要。
現時点で数値経路を仮実装せず、まず「未提供」の状態・根拠と以下の採用条件を維持する。

| 対象 | 採用条件 / 表示契約 |
|---|---|
| context | 実`usage_update`を固定版で捕捉し、0 ≤ used ≤ size、size > 0、正確な整数範囲・対象一致を確認。`used / size * 100`だけを表示し、不明値を分母の推定や100%へのclampで隠さない |
| numeric range | 既存Long境界へ合わせる候補は最大`2^63-1`。schema uint64で妥当でもこれを超える値はclientの表現範囲外としてunknown。負数・非整数値・文字列・boolを自動変換しない（JSON numberの`1.0`等は正確な整数値なら許可） |
| cost | 通貨・session累積scopeが確認できた最新値だけを表示。0は実値。重複・同一通貨・model変更を含め、snapshot同士を足さない。別通貨の換算やaccount請求額への同一視はしない |
| unstable counters | 任意fieldを個別に扱う。`cachedReadTokens`等はprint parserへそのまま流さない。scopeの曖昧さが解消し実値を確認するまで「直近の応答」やsession合計に分類しない |
| 未提供/不正 | 応答成功は保持し数値だけunknown。将来の既存開閉panel内では「この応答では情報が提供されませんでした」等の日本語を検討。恒常的な警告を入力欄へ追加しない |
| 既存print | 4 counters・開閉・0の表示を維持。ACPの新stateで上書き/合算しない。未提供・不正fieldがあっても正常result本文を捨てない |

## 4. 遅着・model/タブ変更の扱い

取得結果には`tab/session/connection generation/run token/model generation`の所有情報を受信・要求時に固定し、
UI反映直前にも一致を確認する。stop/dispose/new turn/session/model変更で旧結果を復活させない。
これは既存ticketを利用する方向であり、任意のeventに新IDを後付けすれば因果関係が証明できるという提案ではない。

**stable usage_update自体にrun token・model ID・sequenceはない。**
前turn/model由来のwire通知が新しいturnの最中に届いた場合、受信時のactive tokenを付けるだけでは識別できない。
今回usageが一度も届いていないため、モデル設定ackやprompt responseとの順序も未検証。
そのような識別不能の値はunknownを維持し、数値表示の採用を保留する。
実notificationの順序/鮮度契約、または新しいsession/connectionに由来すると確認できる証拠が必要である。
通常のmodel切替で無断に会話を破棄する回避策は採用しない。

printは所有するprocessとresult、unstable responseは対応request IDでrunを固定できる候補だが、
そこからtoken集計scopeまで推定しない。EDTへ予約済みのcallbackがmodel/タブ変更後に来るケースと、
wire自体の由来不明ケースをfixtureで区別する。

## 5. 合成境界fixtureと確認結果

[fixture JSON](issue-149-fixtures.json)はliveのallowlist投影と、38件の**合成期待値**を分ける。
[最小検査](issue-149-check.py)はofflineの契約oracleであり、製品parserやGUIのテストではない。

```bash
python3 docs/research/issue-149-check.py
```

結果: **38件 + NaN/Infinity拒否チェック成功**。contextの0/満杯/size0/欠落/負数/小数/文字列/bool/超過/巨大値、
printの部分不正/明示0/異なるcache field名、costのscope/通貨未確認/欠損/不正、
tab/session/connection/run/model/Stop/由来不明の遅着を含む。
小数の計算はDecimal、context率は試験用に小数1桁へ丸める。製品の表示精度はこの調査で変更しない。
`currency_confirmed`と`origin_known`はfixtureの明示前提であり、受信時刻や文字列の形だけから導出しない。
検査はCurrency registry照合・wire順序保証・製品のcancel処理を実装していない。

既存`TokenUsageTest`/`ContextUsageTest`にはprintの不正値/0/Long範囲、古いticket、開閉の確認がある。
今回はそのコードを変更しない。後続製品差分では既存testへACP受入を追加し、今回のoracleだけで代用しない。

## 6. T09の後続CaseとIssue全体の未達

このPRの`gui_required=false`は文書・合成probeの範囲だけ。下記は全て**未実施**であり、固定候補とともに
[検証台帳](../verification/README.md)へ登録して指定operatorが確認する。既存[QA #106](https://github.com/shinma06/cursor-in-android-studio/issues/106)と
[QA #152](https://github.com/shinma06/cursor-in-android-studio/issues/152)の未達を引き継ぎ、両Issue全closeを調査の開始条件にしない。

| Case | 手順と期待値 | 状態/次操作 |
|---|---|---|
| U01 未提供 | ACPで正常応答後に既存panelを開く。数値0/推定率を出さず、日本語で未提供と分かる | pending: #149製品実装と固定build |
| U02 context境界 | 実frameを元に合成used=0、size0/欠落/不正/巨大値を製品境界へ投入。妥当時だけ率、成功本文を維持 | blocked: 実usage契約の捕捉後に実装/Case固定 |
| U03 print維持 | 上記4 counter、部分欠損と0を表示し開閉・日本語・レイアウトを確認。context/account枠と合算しない | pending: 固定候補でQA#106と照合 |
| U04 cost/unstable | 通貨/scope不明、重複cost、model変更、cachedRead名を投入。誤加算/誤分類をしない | blocked: 実field/scope確認後に製品試験 |
| U05 遅着 | EDT待機中にStop、新turn、model、tab、session、接続を変更して古い結果を配送。値が復活しない | pending: 製品境界/固定buildで確認 |
| U06 wire由来不明 | 新turn/model中に旧usage相当を送る。ローカルactive tokenだけでcurrent判定せずunknownを保つ | blocked: providerの順序/鮮度契約確認。識別根拠なしなら採用しない |

次のResearch条件は、CLI/model/providerのusage提供変更または仕様の集計/順序明確化が確認された時の限定再probe。
定期的な無制限再試行、quota取得、provider内部解析、新規MCP serverは不要。
PMは今回の資料scopeと残る製品scopeを分けて引き継ぐ。実装・固定build・GUI・develop/QA追跡が未完了のため、
**#149全体は未完了**。不足が実測された範囲だけを既存表示へ接続し、使用率・cost対応済みとは表示しない。
