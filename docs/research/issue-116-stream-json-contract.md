# #116 print stream-json delta契約の互換性調査

調査中（2026-09-12）。対象base: `f1d84cbc4006f76805fda22904a91fa1b490c841`。
製品コードは変更しない。#230のprint全文置換とACP増分の分離を前提に、
公開仕様・既存公開fixture・制御した新規promptの実測・合成回帰を別々に記録する。

## 確認済みの境界

- [公式output-format](https://cursor.com/docs/cli/reference/output-format) はpartial有効時のassistantをmetadataで分類する。
  `timestamp_ms`あり/`model_call_id`なしは増分、両方ありはtool前の重複flush、両方なしは最終重複flush。
- 現行`StreamJsonParser`の`AssistantDelta`はtextだけを保持する。metadataはこの境界で失われる。
- `AgentProcessService` → `TurnAssistantText.printDelta` → `AssistantChunkDeduper.dedupe`の戻り値は全文。
  `timeline.setAssistantText`へ置換として渡す。ACP本文は独立した追加経路であり、このheuristicを通さない。
- `TurnAssistantText.printFallback`は本文未開始の成功resultだけを補う。errorは本文fallbackではない。
- installed CLI: `2026.09.10-fd3934a`。旧fixtureは`2026.09.02-c22c1a3`であり、同じ版の実測とは扱わない。

## 残る調査

制御された通常delta/tool前/最終flush/複数tool境界の採取、反復と欠損metadata等の再現、
採用条件・旧CLI fallback・製品修正の切り分けを追記する。実IDE表示は未検証。
