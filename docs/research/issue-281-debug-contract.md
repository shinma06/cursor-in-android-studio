# #281 Debugモードの公開契約と計測の寿命

調査日: 2026-09-12。基準実装: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。

## 暫定判断

公式CLIの `/debug` とIDE内Debug workflowは存在する。一方、既存のinstalled helpとACP広告では専用の非TTY起動契約を確認できていない。通常Agentへの不具合相談をDebugモード成功に数えず、公開契約・観測・未確認を分けて固定する。

## 調査範囲

- #278の同日・同CLI版のhelp、fresh ACP sessionのmodes/configOptions/commands広告を再利用する。
- 仮説・計測・再現・ログ・修正・再確認・計測削除の各段階と、既存prompt/run/Stop/Diff/Revertの境界を照合する。
- 専用公開非TTY/ACP経路が確認できた場合のみ合成fixtureで有限実測する。
- #146初期wire、#150 Android debugging、#278実行中入力は再実測しない。GUI・製品コード変更なし。

調査・独立レビュー未完。製品採用、GUI受入、親#25完了とは別に判定する。
