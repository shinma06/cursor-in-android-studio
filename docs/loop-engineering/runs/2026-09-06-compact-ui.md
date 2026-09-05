# UIスケールと選択ボタンの微調整（2026-09-06）

対象: #27、MV-041。ユーザーの3.33.20のスクリーンショットによる、全体サイズ・矢印位置・横長の当たり判定の指摘に対応。
GPT実装/GUI、Claude Pro独立レビュー。45分・修正3回・送信0回の予算、実施2ビルド。

## 変更

- 本文・入力・モデル/モードボタンと候補のフォントをIDEラベルフォントの92%へ縮小。ユーザーのIDE側文字拡大を基準にする。
- 入力の初期高さ58→48、主ボタン28→24、モードアイコン16→14、入力と会話カードの余白を縮小（寸法はJBUIで拡大率に追従）。入力の12表示行上限は維持。
- Unicode文字の⌄を廃止。文字とは別に幾何学的なchevronを描き、垂直中央へ固定。
- SelectorButtonは文字・アイコン・矢印・左右6の余白から幅を決め、その同じ領域を描画。IDE標準ButtonUIの追加幅/最小幅を使わず、余白が横に伸びる問題を修正。狭い場合はラベルを省略し、tooltipの完全名/IDを維持。
- 送信ボタンの文字もUIフォント基準の比率で縮小し、無効時のモードアイコン色を他の文字に合わせる。

CLI引数・モデル/モード値・日本語化方針に変更なし。ButtonUIの入力バインドとJButtonのイベント処理は保持。

## ビルド識別

| run | ソース | ZIP SHA-256 | インストール済みplugin JAR SHA-256 |
|---|---|---|---|
| 20260906-compact-ui-r1 | b58eab5f7b11940c0b85d1841ddad94c4ad4b0ef | 09a37ece7c541ed04adc6b1ffff0ab1823f337b7974178576a71c2d127f34fcb | b5b2c0dc24b290fadb53bfb978f1423f8ced8beeb43b43e9e75314a643b98f50 |
| 20260906-compact-ui-r2 | 2fe07ae91dda48f34b51ca07189119060242fab9 | 0786e67042202927a66c9f3e2ed78487bf9ae24ecdbb0937e927372f259e4f35 | bf302d4dd978100dc537954ec1d48b7ad3e69decf0b30c30a2e31c7193038741 |

Android Studio AI-261.26222.65.2614.16204760。CUAのQuitとlistAppsで停止を確認し、旧pluginのバックアップ後に全JARをZIPと照合して配置。r1は03:40、r2は03:45 JSTに反映して再起動した。実際のGUIは既存の使い捨てfixture `.loop-runs/20260906-issue20-r1/plugin` と `issue20-r3/plugin`。

## GUIで確認した範囲と制約

- r1のbefore.pngとafter-r3-window.pngで同じr3ウィンドウを比較。入力欄の高さ・余白・文字が縮小し、矢印が中央に移動したことを観察。
- r1 model-focus.png、r2 final-composer.pngでは、フォーカス枠がモデル/モードの内容のすぐ外で終わることを観察。余った行幅へボタンが伸びていない。
- r2で未送信の日本語16行をsetValueして、12行までの伸長とスクロールthumbの表示を確認。削除で初期高さに戻った。`long-input.png`、`final-composer.png`とAXを保存。
- 余白クリックはCUA `noWindowsAvailable`。モデルのAXクリックはフォーカスのみとなり、Spaceでの開閉結果を取得できなかった。Raise、同じr3ウィンドウへの切替、接続初期化を試したが、最終版でもScreenCaptureKit -3811が発生。正常に取得できた通常表示を保存して操作QAを停止した。
- 最終状態は空入力、Agent / Composer 2.5。送信・設定変更なし。

したがってMV-041全体は **blocked（外観・入力伸縮は部分確認、余白クリック/メニュー開閉の実機確認が未了）**。当たり判定の自動テストを実クリックの代用にしない。縮小した会話本文は実送信で再検証していない。従来MV-039の包括的受入も未完了のまま。

ローカル証拠は `.loop-runs/20260906-compact-ui-r1/evidence/` と `r2/evidence/`。この共有記録は実施内容の要約であり、未実施の操作をpassとはしていない。

## テスト・独立レビュー

`test buildPlugin`成功、59テスト。追加した2テストは、巨大な最小幅を返すButtonUIを挿してもボタンが膨らまないこと、フォント11/18でも描画された矢印の中心が上下にずれないことを確認。既存の狭幅クリップ・モデル検索・Auto・モード・12行上限のテストも成功。

Claude Pro（claude.ai認証、read-only CLI）のレビューを実施。

- 送信文字のサイズ指定を相対フォント比率へ変更する指摘を採用。
- 無効時のアイコン色が変わらない指摘を採用。レビュアーは「ModeSelectorは無効にならない」としたが、ComposerPanel.setInputEnabledは無効化するため実経路もある。
- EditorTextFieldがfont familyを反映しないという指摘は不採用。対象Android Studioのクラスをjavapし、setupEditorFontがgetFont().getFontName()とgetSize()の両方をschemeへ渡すことを確認。日本語入力の表示も取得した。
- 長いモデル名のメニューは従来からtooltipで完全名を提供する設計。縮小後のメニュー操作GUIは上記のとおり未確認。

次の確認はツール復帰後、最終2fe07aeのモデル/モード開閉・取消と、矢印右余白の実クリック。再インストールは不要。
