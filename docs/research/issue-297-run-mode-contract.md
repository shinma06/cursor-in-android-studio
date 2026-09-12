# #297 Run Mode・allowlist・承認設定の適用/表示契約

調査日: 2026-09-12。基準実装: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。

公開IDE/CLI/ACPの設定・scope・優先順・適用タイミングと、既存enum/起動/validation/日本語表示を有限に照合する。現公式Run ModesはAuto-review / Allowlist / Run Everythingの3択で、sandboxは別の設定。過去の4択を現在の契約としない。

provider prompt、私的設定読取、認証/allowlist/保護変更、GUI/Browser/clipboard、sandbox解除なし。#146の初期wire/permission実測・未公開原本は対象外。必要な合成入力のみ。採否/最小修正候補/残証拠・Caseと固定独立reviewは未完。
