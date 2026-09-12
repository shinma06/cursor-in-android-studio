# ヘッダー寸法の訂正（#210）

QA [#191](https://github.com/shinma06/cursor-in-android-studio/issues/191) の `TAB-HEADER-DIVIDER` は、**幅108 logical pxと、対象IDEの既存ネイティブ縦寸法の維持**を確認する。
今回のSDK/設定では108×36。製品を38pxへ広げる変更は不要。人間の操作・hoverの既存OKを維持し、本訂正のための再撮影・追加操作は要求しない。

| 項目 | 確認内容 | 期待値 |
| --- | --- | --- |
| 寸法の根拠 | 下記のSDK設定と既存QC証拠を照合 | 今回のtoolbarは108×36。38pxを一律の期待値にしない |
| 既存の試験結果 | #191の操作・hover・背景の証拠を読む | 実施済み範囲のOKを維持し、未実施へ広げない |
| 最終判定 | 本訂正PRと#191の参照をPMが照合 | GUI結果とmain反映を別々に記録する |

## 訂正理由と対象環境

元要件 [#183](https://github.com/shinma06/cursor-in-android-studio/issues/183) / [#189](https://github.com/shinma06/cursor-in-android-studio/issues/189) / [#192](https://github.com/shinma06/cursor-in-android-studio/issues/192) は幅と既存の縦寸法・クリック領域を維持する要求で、高さ38pxへの変更要求はない。

`SessionTabStripTest` の108×38はテスト用JPanelへ与えた寸法であり、ネイティブtoolbarの測定結果ではない。隔線・配置の検証としての価値は保持し、製品の高さを証明する根拠には使わない。

対象 `AI-261.26222.65.2614.16204760`、IDE scale1.0、Monitor scale2.0、Zoom100%、Compact OFFでは、Factoryのminimum22×34と実SDKのborder Insets(1,2,1,2)からbutton26×36、4個と右余白4でtoolbar108×36となる。この値は当該環境の算出値で、他SDK/設定へ普遍的な固定値として適用しない。

根拠は [QCの画像・SDK照合](https://github.com/shinma06/cursor-in-android-studio/issues/191#issuecomment-5643219574) と [PM判断](https://github.com/shinma06/cursor-in-android-studio/issues/191#issuecomment-5643240089)。画像のglyph間隔/帯高さは計算と整合するが、丸角とcropがあり、厳密なライブbounds測定ではない。36と38の差を丸め誤差として許容したのではない。

## 固定Caseとpromotionの参照

- 旧PR #190の固定merge `4c1cbebddc22e07200d794066677608f25af9380` のJSONと観察履歴は変更しない。最新の `changes/issue-189.json` は寸法手順と根拠リンクだけを訂正し、Case ID・結果・履歴は維持する。
- promotionは引き続き旧固定mergeから `189:TAB-HEADER-DIVIDER` を収集する。最新JSONへ参照をすり替えたり、Caseを除外したりしない。
- 当該Caseの結果を記録する際は、`evidence` が指すQAコメントに、元Case・本訂正PRの固定commit・PM判断・対象候補の観察を併記する。`reason` で寸法基準を本訂正に従って評価したことを明記する。元の38px記述に対して根拠なくpassを付けない。
- 訂正は候補/buildの同一性要件を免除しない。過去の観察source `34235165e4f34b22c5200542bfec758cd0ff256a` を、訂正や他変更を含む後続candidateの観察に書き換えない。全候補の必要Case・build・実施範囲を通常のgateで確認する。
- 今回はpromotion候補/結果を新規作成しない。GUI passやmain反映済みとも宣言しない。本PR統合後、PMがQAの現行手順リンクと上記証拠を照合する。
