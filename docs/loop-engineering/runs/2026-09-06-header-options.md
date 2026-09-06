# 上部固定UIのチャット設定（#55）

Owner: gpt-header-options-20260906。親 #19 / #1。Base: e0569a579780ea343e9f1dae135a6ed810f69c94。
Case: HEADER-OPTIONS-1。実装・自動検証済み、GUI pending。ユーザー提供の3画像は配置指定の参考であり、このbuildのGUI証拠ではない。

入力欄内の「…」を上部Agent行の新規チャット・履歴の右隣へ移した。
パネルはtriggerの下に4px相当の間隔を空け、右揃えで開く。画面端では左右を収め、下側の高さが足りなければスクロールを使う。
同じtriggerのtoggle、Escape、外側クリック、IDE非アクティブ化、header非表示で取り消す。
「プラグイン設定…」は「設定」へ変更。Composerのrunning通知を接続し、要約の実行中無効化と送信直前のガードを維持。
権限・sandbox・worktreeの保存値/CLI引数、MCP/設定dialogへのcallbackは変更していない。

#50との境界合意によりSelectorPopupControllerは編集せず、ヘッダー専用popupを使用する。共有モデルUIの変更・先行merge依存はない。

## 自動検証

`JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test buildPlugin`。
配置テストで右揃え、下方向、負座標の画面、狭い画面、高さ制限とtrigger非重複を検証。
既存設定パネル試験で保存値の維持、設定actionのclose→dialog順、running中の要約無効化を確認。
これらは実IDEのpopup取消や実際の表示の合格を意味しない。

## GUI依頼

固定HEAD/ZIP/hash/PRは #55 のhandoffコメント。指定GPT進行役がlease取得後、disposable fixtureとロード実体を確認する。
Run: 20260906-header-options-r1。予算15分、CLI送信0回。必要な設定操作は値を控えて最後に復元する。

1. 入力欄に「…」がなく、上部Agent行の「＋」「履歴」の右隣にあることを確認。
2. 「…」を開く。triggerを隠さず下側に展開し、画面右端に収まることを確認。
3. 縦/横に狭い画面で全項目がscroll経由で選択可能か確認。
4. 同じtrigger、Escape、外側余白クリック、IDE非アクティブ化、toolwindowを隠す操作で閉じる。再度開けることを確認。
5. 操作の確認/実行範囲/作業場所の子選択popupが開き、親が意図せず閉じないことを確認。選択値を復元。
6. 「設定」で既存設定dialogへ移動し、MCP導線も確認。要約の実送信はしない。
7. 入力・設定・fixtureを復帰し、起動した処理を停止または明示引継ぎ。識別したZIP/JAR、観察結果と証跡をIssueへ記録。

未実施はpending/blockedのまま記録し、過去MVのpassを流用しない。独立レビュー、CI、必要GUIを自動進行役へ委譲する。
