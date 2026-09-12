# #284 Cursor診断用Request IDの公開取得契約

調査日: 2026-09-12。基準: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。

## 調査範囲

公式print outputの省略可能な `result.request_id`、ACP広告の `copy-request-id`、固定#154 M03の最新generationの診断IDを区別する。session/chat ID、JSON-RPC id、toolCallIdで代用しない。

既存#278同版help/catalog/合成printを再利用。公式に支持されたprint取得経路だけを必要最小の合成会話で照合する。実GUI/clipboard/feedback送信/認証設定変更なし。#146未公開fixture/owner/承認待ち、#66保留を維持。

調査・固定レビュー未完。製品採否、親#25、GUI/main完了は別に扱う。
