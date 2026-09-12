# #290 Web・Browser・MCP rich resultの受信/表示契約

調査日: 2026-09-12。基準実装: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。

## 範囲

Web Search/Fetch、Browser操作、画像生成、MCP結果について、engine能力とACP/print受信・表示を分ける。text/image/audio/resource link/embedded resource/構造化データ、partial update、media参照と明示操作の条件を有限に確定する。

既存公開schema/fixtureと基準parser/rendererを優先する。#118/#263 Task、#10/#277入力画像、#157/#208手動browser、#150実IDE、#146未公開fixtureを重複調査しない。GUI Browser/IDE起動、MCP/認証設定、clipboard、私的データの変更なし。

調査・固定独立review未完。製品採否・GUI・親#25・main受入を区別する。
