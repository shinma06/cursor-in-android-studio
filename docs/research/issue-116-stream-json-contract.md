# #116 print stream-json delta契約の互換性調査

2026-09-12 / 調査base: `f1d84cbc4006f76805fda22904a91fa1b490c841`（#230統合後）。

**結論: metadataによる増分/flush識別を採用すべき差異を、正常終了した実wireで再現した。**
現行parserはmetadataを捨て、文字列heuristicが正当な反復を消し、flushを再追加する。
本PRは製品を変更せず、修正Issueへ再現条件を渡す。GUI合格・ACPの実wire合格ではない。

## 公開仕様と観測の区分

[Cursor公式Output format](https://cursor.com/docs/cli/reference/output-format)を2026-09-12に確認した。
partial有効時だけ、assistantの追加本文を次のように判別する。

| timestamp_ms | model_call_id | 意味 / 処理 |
| --- | --- | --- |
| あり | なし | 追加本文。順序通り追加 |
| あり | あり | tool直前の重複flush。追加しない |
| なし | なし | 最終重複flush。追加しない |

成功resultは全文、異常時はresultなしで終了し得る。同資料のthinking抑制の記述と異なり、
今回の3runにはthinkingイベントがあった。仕様文だけでparserの防御処理を削除しない。
helpはpartial flagを広告するがschema version/capability negotiationを示さず、採取initにも
本文契約のversion/capabilityフィールドはなかった。全CLI版に存在しないとの証明ではない。

[Cursor ACP](https://cursor.com/docs/cli/acp)はJSON-RPCの別transportであり、
[ACP prompt turn](https://agentclientprotocol.com/protocol/v1/prompt-turn)のmessage chunkを
このprint metadata表へ変換して扱わない。#116は既存補助経路の互換修正のための調査で、
新しい競合優位機能やCursor IDE / JetBrains AI Assistant + Cursor ACP + IDE/MCP構成との
UX同等性を実証するものではない。新規能力はACP Firstの優先順位を維持する。

## 現行の責務と情報が失われる場所

```text
print stdout → StreamJsonParser → StreamEvent.AssistantDelta(textだけ)
  → AgentProcessService.onAssistantDelta(text)
  → AgentTurnListenerFactory / TurnAssistantText.printDelta
  → AssistantChunkDeduper.dedupe（全文またはnull）
  → ChatTimelinePanel.setAssistantText（現在bubbleを置換）
```

- `StreamJsonParser`はassistantの`timestamp_ms` / `model_call_id`を保持しない。
  したがって後段にflushと増分を正しく分ける情報がない。空/欠損/不正metadataの違いも失う。
- `AssistantChunkDeduper`はprefix拡張を全文再送と見なし、既存文字列に含まれるchunkを捨てる。
  改行を含む、または24文字以上の非包含chunkは置換する。時刻やtool境界は見ない。
- `TurnAssistantText`はturn単位で保持され、print tool開始/終了でbufferを区切らない。
  戻り値は全文であり、appendへ変更してはならない。ACPは別bufferで正当な反復を追加する。
- 成功resultは`printStarted == false`の場合だけ補完する。既に壊れた本文を最終resultが
  自動修復する契約ではない。error resultはserviceのエラー経路へ進み、本文fallbackにしない。
  ResultはOS process終了でもない。
- thinkingはstatus、Unknownは無視。tab/turn tokenと停止/破棄をEDT実行時に再確認する。
  今回はソースと既存`AgentTurnDispatchTest`を照合した。実IDEの配送/表示を観察してはいない。

## 実測: 3runで終了

CLI `2026.09.10-fd3934a` / init model `Auto` / `--mode ask` / permission `default`。
`--model`は省略。Autoの裏側のモデルIDはwireから特定できず、モデル固有保証にしない。
使い捨てディレクトリの`alpha.txt`と`beta.txt`だけを対象にし、各runは新規会話、90秒上限。
既存会話・認証情報・#146の未公開fixtureは読まず、login/logout・GUI・MCP操作は行っていない。
完全なprompt/実行条件/公開projectionは[fixture README](../../src/test/resources/issue-116/README.md)。

| run | partial | 結果 | 観測 |
| --- | --- | --- | --- |
| plain | true | exit 1、resultなし | 40 delta。正当な同文反復を含む。CLIが反復検知で途中終了 |
| tools | true | exit 0、success result | 12 delta、2 tool前flush、1最終flush、2回のread started/completed |
| nonpartial | false | exit 0、success result | 2 tool前形と1最終形の完全メッセージ。partial規則なら全消去してしまう |

複数deltaが同一timestampを持つ。timestamp値による重複排除も不可。
正常runの`result`と、metadataで選んだdeltaを単純連結した本文は完全一致した。

```text
期待 / result: START-A\nBETWEEN-BDONE-C 46
現行経路:      START-A\nBETWEENBETWEEN-BDONEC 46DONE-C 46
```

`\n`は1改行。他の区切りを独自追加しない。現行では既出`-`を捨て、tool前の
`BETWEEN-B`と最終`DONE-C 46`を重ねて追加する。改行chunkによる一時置換も起きる。
合成promptの指示文が理想通りかどうかではなく、**受信した成功本文/resultと製品再生の差**で判定した。

途中終了したplainの40 deltaの連結と、現行最終値`\nred red blue`も一致しない。
このrunに12行の正常応答・最終flush・resultを補ってはならない。CLIが実際に送らなかった
文字の不足と、pluginが受信済みchunkを消す不具合を区別する。

## 既存fixtureと合成回帰

`stream-json-fixtures/04_plain_question_success.jsonl`（旧CLI `2026.09.02-c22c1a3`、
記録model `Composer 2.5 Fast`、README上partial有効）はtimestamp付き`OK`、metadataなし`OK`、
result `OK`。今回の分類と整合し、現行再生も`OK`1回。ただしtool前・複数境界・訂正再送まで
旧版全体が合格した証拠にはならない。02/03はcompleted toolのみ、01は失敗途中で、本文契約の補強に使えない。

[Issue116PrintContractTest](../../src/test/kotlin/com/cursoragent/ui/Issue116PrintContractTest.kt)は実parserと
#230の本文stateを使い、5 testsで以下を固定する。壊れた現行値を明示するcharacterizationであり、
製品合格のassertionではない。修正Issueでは期待正常値へ更新する。

| 種別 | 確認内容 | 判定の限界 |
| --- | --- | --- |
| 実測projection | toolsの正常resultと公式delta連結の一致、現行表示差 | 実IDEは未観察。tool payload試験でもない |
| 実測projection | plainの同文反復とresult不在、現行表示差 | CLI反復停止。成功fixtureにはしない |
| 実測projection | nonpartialをpartial規則で消さない必要性 | pluginは現在常にpartial有効。比較経路のみ |
| 合成metadata | 欠損、数値timestamp、両方あり、null、空文字、object/array、IDのみ | 全てtext-onlyに潰れる現状態。異常形の実発生ではない |
| 合成と旧fixture | 累積再送、空本文、未知/不正JSON、thinking分離、成功result-only、error非本文、旧OK | service配線はソース照合。error UI/終了/全tabは既存テストとQAへ |

## 採否・適用条件と最小修正境界

**採用: printの増分/flush情報を失う前に正規化する。** `timestamp_ms`を一意IDとしたり、
同じ文字列を重複とみなしたりしない。全文置換のUI出力とACP分離は維持する。

| 条件 | 決定 |
| --- | --- |
| print + partial有効 + 検証したproducer契約 | 3分類を利用し、deltaだけ順に追加。tool前/最終flushを重ねない |
| partial無効 | 同じmetadata形でも完全メッセージ。partial skipを適用しない |
| 旧/未検証producer、metadata不正・判別不能 | 既存heuristicを互換fallbackとして残す。正確性保証とはしない。欠損を即flush扱いする全版一律切替は不採用 |
| resultのみ / error / process終了 | 本文未開始fallbackとエラー分離、物理終了の既存契約を保つ |
| ACP | 既存の明示delta/boundary経路を保持しprint heuristicへ通さない |

確認した版とpartial設定が採用根拠であり、「この版以上なら全て同じ」という境界は未証明。
metadataの欠損だけでは旧メッセージと最終flushを完全に識別できず、1イベントごとに
新方式/旧方式を行き来すると混線する。修正Issueでturn単位の選択と判別不能時の退避を
テストする。新しい汎用event bus、版別registry、保存schemaの作り直しは本調査から要求しない。

最小範囲は`StreamJsonParser/StreamEvent`での情報保持 → `AgentProcessService`の配送 →
print本文正規化。現`TurnAssistantText`の全文出力と#44の保存/ID基盤へ二重正規化を加えない。
#230を差戻したり#44にwire調査を担わせたりしない。今回の具体不具合の専用IssueはPMが分離する。
既存検索は`dedup`、`stream-json`、`文字 欠落`をopen/allで確認し、#116/#246等以外に同じ実wire
欠落・重複を修正する専用Issueは見つからなかった（検索範囲内の結果）。

## GUI Case案と受入の引継ぎ

調査変更自体はGUI不要のため`issue-116.json`のcasesは空とし、製品表示は既存
[QA #246 / TEXT-CONTRACTS](https://github.com/shinma06/cursor-in-android-studio/issues/246)と
専用修正Issueで追跡する。以下は**GPT pending / human pending**のCase案であり、pass履歴ではない。

- 前提: 修正を含む固定候補SHA・ZIP hash・loaded JAR・CLI版/設定を照合し、指定GUI lease担当が
  disposable fixtureを使う。既存`TEXT-CONTRACTS`とtab/Stop Caseの準備を再利用する。
- 本文: tools projectionを制御再生し、途中と最終の文字列を照合。期待は
  `START-A\nBETWEEN-BDONE-C 46`。重複flushは増やさず、`-`/改行/同文反復を消さない。
- 終端: 成功result-onlyを一度表示し、error-onlyは本文にしない。plain途中終了は
  受信済み本文を保ちエラーと区別。未受信の12行を成功扱いしない。
- 分離: 本文/thinking/toolを混ぜず、A応答中のB選択、AのStop/close、同tab次turn後の
  遅着で別tab/旧tokenへ追加しない。ACP反復/boundaryも既存Caseで回帰する。
- 記録: build識別と観察者/証拠を正本JSONへ残す。failは修正Issue/PR/再確認buildへ、
  未実施はpendingのまま。今回のJUnit成功をこのGUI passへ転記しない。

#116の5受入は、(1)版/公開model/partialと全flush種別、(2)反復/再送/欠損/Unknown/終端、
(3)metadata喪失と全文置換、(4)上記採否/fallbackと修正分離、(5)このCase案で対応する。
未観測: Auto内部model、全CLI版/全model、実wireのnull/不正metadata・訂正再送・result-only/error-result、
断片的なOS read境界、固定build GUI。未観測条件を作るための追加推論は行わない。

次の有効な順序は、PMによる修正Issue/共有境界の確定 → metadata対応と再生正常化 → #246の固定build確認。
#117/#118/#10/#149等の独立調査を#116全体へ依存させない。
