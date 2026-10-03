# 応答Markdown画像の自動取得抑止

#295。応答中の `![説明](取得元)` は **`[画像: 説明]（取得元: …）` の文字表示**にする。
説明なしは `[画像]`、空の取得元は `未指定`。従来表示できていた外部画像も自動表示されなくなる。
画像の明示プレビューは今回追加しない。保存やコピーしたMarkdownを別アプリで開いた場合の取得動作は、そのアプリの仕様による。

## 実装と互換性

[共通renderer](../../src/main/kotlin/com/cursoragent/ui/timeline/AssistantMessageBubble.kt) の既存commonmark node rendererへImageを追加する。
Imageの子のText/Code/HTML風文字列を説明へ集め、書式・入れ子を文字化する。改行は空白にする。
説明とdestinationを `HtmlWriter.text` でエスケープし、`img`、取得元へのリンク、イベント処理を生成しない。
inline/reference画像、相対URL、HTTP(S)、file/data、未知・不正schemeも同じ扱い。
画像の外側にユーザーの明示的なMarkdownリンクがある場合は、既存リンク描画を保持する。
raw HTMLのescape、通常のリンク、見出し、コード、リストは既存処理を維持する。

受信本文は `ChatTimelinePanel.setAssistantText` → `AssistantMessageBubble.setContent` → renderer → `MessageTextPane`。
`ChatTimelinePanel.restore` も同じ経路を使う。全量更新・途中訂正・空文字でも古い画像を残さない。
表示時だけ変換するため、`rawMarkdown` のコピーと `ConversationStore` の保存本文は原文を保持する。
#45固定版の `conversationMarkdown` は `message.text` を出力し、rendererを呼ばないことも照合した。
共通Controller、保存形式、通信処理、MessageTextPane自体は変更しない。新規非同期処理・リソース所有・DB処理はない。
#268の文字サイズ・折返しは別の表示設定であり、Image ASTの文字化と原文保持は共存できる。

## 検証の範囲

[MarkdownImageSafetyTest](../../src/test/kotlin/com/cursoragent/ui/timeline/MarkdownImageSafetyTest.kt) は6件。
3件の文字列テストでalt・入れ子・空値・scheme・HTML escape・通常Markdownを確認する。
SwingテストはEDTで実MessageTextPaneをレイアウト・メモリ画像へ描画し、HTMLDocumentのIMG要素とImageViewがないことを確認する。
表示・累積更新・訂正・Copyはdocument専用の `fixture:` URL handlerの読込み0件も照合する。
陽性対照は同じ標準paneに直接 `<img>` を渡し、メモリ内PNGの読込みを観測する。ソケット、外部URL、実ファイルの画像取得やJVM全体のURL factory変更は使わない。
復元テストは一時保存先の原文を読み戻し、実 `ChatTimelinePanel.restore` のpaneにIMG/ImageViewがないことと、復元後も保存・コピー原文が同一であることを確認する。
文字列テスト、隔離Swing試験、実IDEの通信・表示観察は区別し、[固定build Case](../verification/changes/issue-295.json) のGUI結果は未実施として保持する。

根拠は [#290固定調査](https://github.com/shinma06/cursor-in-android-studio/blob/9bb03286ec6beaebd52acca152909ca7a90221b3/docs/research/issue-290-rich-tool-results.md) のW3。
[commonmark 0.30.0の既定Image描画](https://github.com/commonmark/commonmark-java/blob/commonmark-parent-0.30.0/commonmark/src/main/java/org/commonmark/renderer/html/CoreHtmlNodeRenderer.java) と
[Java 21 ImageViewの読込み仕様](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/swing/text/html/ImageView.html) を確認した。
今回の修正を実環境での情報流出の観測結果として扱わない。
