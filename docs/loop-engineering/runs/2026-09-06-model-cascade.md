# モデル選択の左ホバー展開とAuto表示（#50）

- Owner: gpt-50-20260906-cascade
- Issue: #50（親 #19 / #1、性能調査 #53 と連携）
- Base: e0569a579780ea343e9f1dae135a6ed810f69c94
- Reference: ユーザー提供のCursor画像2枚（手動モデル/Auto）。プラグインのGUI証拠ではない。
- Case: MV-model-cascade
- Status: implementation tested; GUI pending。既存MV-038〜044の判定は変更しない。

## 変更

Model行へのホバー、クリック、Leftキーで検索付き子パネルを左側へ展開する。
親オプションはその位置を維持し、画面端では右側、両側が足りなければ画面内へ制限する。
Autoでは親に日本語説明とModel Autoを表示する。子一覧のAutoは通常の選択行であり、手動候補も表示する。
系列名の横にHigh/Fastなど選択派生を薄色で表示し、系列ごとの実CLI ID選択は保持する。
モデル/容量の固定カタログやAdd Modelsの管理バックエンドは追加しない。

親子で外側クリック/フォーカスを共有し、子内の操作で親が閉じないようにする。
子のEscapeは選択せず親へ戻り、親のEscape/同じtrigger/外側クリックで閉じる。
部品破棄時に子popupとグローバルlistenerを解除する。

#53担当から原因候補とテストを引継ぎ、モデル行rendererを再利用、結果/空表示を常設CardLayoutへ変更、
一覧更新イベントをaddAllへ集約した。検索では子popupだけを再packする。

## 自動検証

`JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test buildPlugin` 成功。
81 tests、failure/error/skippedは0。既存deprecated API警告のみ。

- Auto中も候補検索/選択でき、既存manual派生を復元する。
- ホバーで親のオプションを維持し、子を重複作成しない。Escapeは選択値を変更しない。
- Leftキーで子を開く。Context/Effort/Thinking/Fastの元CLI ID解決を維持。
- 左配置、右fallback、狭画面/負座標での範囲内配置。
- #53回帰テスト: 50回の検索/clear後もrenderer子数1→1。
- 20回の検索/無一致/clear後のscrollPane取り外し数0。

これらはEDT上の単体試験であり、実IDEの体感速度・フォーカス・ホバー動線の合格を意味しない。

## GUI依頼（未実施）

対象HEAD/ZIP SHA-256/PRはIssue #50のhandoffコメントで固定する。
指定GPT進行役はホストleaseを取得し、disposable fixtureとロードしたJARを識別してから実施する。
予算45分、修正3回、CLI送信0回。事前のモデル値を記録し、終了時に復元する。

1. 手動モデルを選んだ状態でtriggerを開く。Modelへホバーし、親が動かず左子パネルが出ることを観察。
2. ポインタを左子へ渡し、検索入力、上下キー、Enter、現在行チェック、完全名/ID tooltipを確認。
3. モデルを切り替え、High/Fast等の薄色補足と実際の保存IDを照合。系列を往復して派生を保持する。
4. Autoを選ぶ。親は日本語説明＋Model Autoとなり、左子を再表示するとAutoチェックと手動候補が見える。
5. 子Escapeで親へ戻る。親Escape、外側のフォーカス不能な余白クリック、同じtriggerで両方閉じる。
6. ModelへホバーしたままのEscape、他optionへの移動、Effortページからの戻り、IDE他windowへの移動を確認。
7. 画面端と狭いウィンドウで配置を確認。検索/clear/無一致を反復し、引っ掛かり・停止がないか記録。
8. fixtureの空入力・送信0・設定復元、起動処理停止または明示引継ぎを記録。観察できなければpending/blockedを維持。

独立レビュー、CI、固定ビルドGUIを自動進行役へ引継ぎ、合格後のみmergeする。
