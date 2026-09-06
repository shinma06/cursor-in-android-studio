# CLIのContext Usage取得調査（#59）

2026-09-06。UI参考はユーザー提供のリング、Context Usageパネル、モードpopupとの重なり画像。
画像の14% / 34.7K / 256Kと分類別値は表示例であり実データではない。

## 確認できたこと

- ローカル `~/.local/bin/agent --version`: `2026.09.02-c22c1a3`。
- 同CLIの`--help`にはセッションのcontext使用率/残量/種別別内訳を出すコマンド・フラグなし。
- 既存の実測fixture `src/test/resources/stream-json-fixtures/04_plain_question_success.jsonl` のterminal resultに
  `usage: {inputTokens: 21022, outputTokens: 31, cacheReadTokens: 8896, cacheWriteTokens: 0}` がある。
  この調査で新たなプロンプト送信はしていない。
- [公式出力仕様](https://cursor.com/docs/cli/reference/output-format)はinit/assistant/tool_call/resultと
  partial outputを説明しているが、context容量・使用割合・分類別usageの取得契約は記載していない。
- [公式パラメータ](https://cursor.com/docs/cli/reference/parameters)とローカルhelpからも同取得経路を確認できない。

## 実装上の意味

`result.usage`の存在とフィールド名は実測に基づく。省略される場合があり、正の値が常に来る保証はない。
非負整数だけ個別に採用し、不正・欠損は取得不可にする。不正usageでもresult本文は失わない。
入力とcacheRead/cacheWriteの包含関係・ターン内の複数モデル呼出しの集計範囲は未確認。
従って、4値を合計せず、直近の応答のCLI報告値としてそのまま表示する。

入力トークン数は現在のcontext占有量や残量を保証しない。モデル名から上限を推測しない。
トークン数の更新はterminal result受信時であり、生成中のリアルタイム使用率は取得できていない。
System prompt / Tool definitions / Rules / Skills / MCP / Subagents / Conversationの配分も取得できていない。
これらを文字数や課金usageから推測しない。CLI内部DBや非公開APIは使用しない。

ユーザーは取得できるトークン数のみを表示し、使用率・内訳は「取得不可」にする方針を明示選択した。
リングは中立な欠けた輪で不明を表す。0%や画像の14%を表示しない。
パネルはcomposerの上に常設可能なSwing部品として組み込み、外側focusで閉じない。
既存のmode/model popupを変更せず、その下の通常contentとして重なりを許す。
新規会話・履歴再開・送信開始・停止で数値を消し、旧turnの遅延callbackを世代番号で拒否する。

## 残る確認

固定buildでの画面受入は `docs/loop-engineering/runs/2026-09-06-context-usage.md` とIssue #59。
将来CLIが容量・内訳を公開した時点で、実測fixtureと意味の確認を追加して実割合表示に接続する。
