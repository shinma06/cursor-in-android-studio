# 同一モデルのオプション選択（#27 / MV-044）

2026-09-06、GPTが実装・Computer Use、Claude Proが独立静的レビュー。送信0回。ユーザー画像は参考、以下の実機観察はプラグインの識別済みビルドで行った。

## 変更

- CLIの派生モデルを系列ごとに1行へまとめ、モデルボタン直下にThinking/Fast/Context/Effortを表示。選べる組み合わせがない項目は非表示。
- Modelから系列検索、Effort等は選択ページへ進む。子ウィンドウのフォーカス競合を避け、同じpopup内でページを切り替える。参考画像の横方向カスケードとは異なる。戻る行でオプションへ戻れ、EscapeはIDEのpopup取消で閉じる。
- 検索は系列内すべてのID/ラベルに一致する。検索語で派生を自動選択せず、モデル本体を選び、その後オプションを指定する。
- 保存/実行には元のCLIモデルIDをそのまま使用。他の既知オプションを保持する候補だけを提示。不明/曖昧な派生は独立項目として残す。
- 最初のモデル設定とAutoの手動モデルも記憶し、同じpopup内のモデル往復で保持。選択中行まで一覧をスクロール。

## 対応値の根拠と制限

実CLIの `--help`、`models --help`、`--list-models` を読み取り。取得済み一覧を `src/test/resources/model-list-2026-09-06.txt` に保存。CLIの利用はメタデータ読取りのみ。

現時点の一覧には多くのモデルで1M表記しかなく、複数の明示的容量を持つ同一モデル候補を確認できないためContextは非表示。`[context=1m,...]`形式はCLIヘルプにあるが、300Kなどの対応値を列挙する仕組みはヘルプにない。画像から値を推測しない。Contextの複数容量切替は明示したテスト用一覧でID解決を検証し、実機で利用可能だったとはしていない。

公式の[CLI概要](https://cursor.com/docs/cli/overview)の `--model` 指定も参照。モデル能力の根拠にはローカルCLI一覧を使用し、別途カタログAPIを実装したり認証情報を取得していない。

## ビルドと実機観察

### 初版 0310729

- 74 tests / buildPlugin成功。
- ZIP SHA-256: `cad0f273f7c6c4e087a5d49592defe895646fc4b3d3d2f52b59d90f3a81a9a67`。
- 10:03:38配置、10:03:43起動ログ。Android Studio `AI-261.26222.65.2614.16204760`。
- 元の `gpt-5.6-luna-high` を復元。LunaにFast/Effortのみ表示、Fastオン後もHigh、EffortをExtra Highに変更後もFastオンを保持。
- Model検索「Claude Opus 5」は1候補。選択するとThinking/Fast/Effort、Contextなし。ThinkingをオフにしてもFastオフ/Highを保持。
- Autoオンで一覧を折りたたみ、日本語説明を表示。オフで手動モデルへ戻れる。Lunaへの復帰でも以前のExtra High/Fastを保持。
- 最後にLuna High / Fastオフへ復元。Effortの候補移動後Escapeで閉じてもIDはHighのまま。
- 初期のCUA画像取得でScreenCaptureKit -3811が発生したが、再起動後はpopup/全体画像を取得できた。

### 最終版 1cca572

- 76 tests / 0 failures / 0 errors、buildPlugin成功。
- ZIP SHA-256: `924d153ea64edd639124f036543bbca2fa713bcc9a475e51d39574ef5a8fccad`。
- メインJAR SHA-256: `a46ca50f3bd6b2ff4ae647209b4095a61453e977a75cf77c49e8765951ff73f0`。
- 10:09:28配置、10:09:42起動。旧版を証跡フォルダへバックアップ、新JARと起動ログを照合。
- Luna Highの復元を確認。Mediumに変更→閉じる→再度開く→Composerへ移動（Fastのみ）→Lunaへ戻す、でMedium/Fastオフを保持。修正前の初期値落ちを再現する経路が修正された。
- 最後は `gpt-5.6-luna-high` / Agent / 空入力に戻し、通常画面を画像・AXに保存。プロンプト送信0回。

ローカル証跡: `.loop-runs/20260906-model-options/evidence/` の `luna-options.jpg`、`family-search.jpg`、`opus-options.jpg`、`auto-on.jpg`、`r1-restored.txt`。最終版は `.loop-runs/20260906-model-options-r2/evidence/` の `install.json`、`startup.txt`、`roundtrip.jpg`、`roundtrip.txt`、`final-restored.txt`、`final-restored.jpg`。

## 独立レビューと判定

Claude Proの読取り専用レビューは、最初に選択されていた派生がrememberedへ未登録で、別モデル往復後に初期派生へ落ちる点を指摘。採用して通常/Auto開始の回帰テストを追加し、最終版で実機往復も確認。その他の論理欠陥の指摘なし。CLIの送信経路・権限・チェックポイントには変更なし。

MV-044の今回のUI範囲はpass。Context実機切替は候補未提供、画像と同じ横カスケードは未実装。過去のMV-039/041/042、各パネル幅の網羅・外側focus取消などはこの結果から合格へ更新しない。
