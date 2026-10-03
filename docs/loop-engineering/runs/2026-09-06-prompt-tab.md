# PROMPT-TAB-1 — 入力欄のTabフォーカス移動

Issue #42の限定修正。送信方式の設定・保存・IME全体の改修は含まない。

GrowingPromptFieldの各createEditorでembedded form扱いにしてIDEのTab編集を無効化し、
editor.contentComponentのローカルtraversal keysにTab/Shift+Tabを追加する。
複数行入力、貼り付けたタブ、Enter/Shift+Enter、他のコードエディタ、IDE全体の設定は変更しない。
EditorTextFieldは入力の有効/無効変更でEditorを再生成するため、初回だけではなくcreateEditorで設定する。

実装確認: インストール済みAndroid Studio SDKのTabAction.Handlerはembedded editorで無効になる。
[JetBrainsのEditorImpl](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/openapi/editor/impl/EditorImpl.java)
もembedded editorを外側のフォーカス巡回へ接続する。実際のplatformキーdispatch/IMEは実機確認が必要。

## 固定ビルドGUI（未実施）

host lease・HEAD/base/ZIP SHA-256・ロードJAR/PID・使い捨てfixtureを照合。
現在の人間GUI予約を上書きしない。Ask、最大1通常送信、10分。

1. 日本語を含む複数行の未送信入力でTabを押す。本文/選択を変更せず、入力外の次の操作へフォーカス。
   Shift+Tabで逆方向へ移動。空欄・文字選択中でも入力/削除が起きない。
2. Shift+Enterで改行、日本語IMEの確定で誤送信なし。`@`候補を開き候補の選択/取消が機能する。
   ポップアップがフォーカスを持つ間に入力欄側へTabの処理を強制しない。
3. 短い通常応答を1回完了し、入力欄が再び有効になってからTab/Shift+Tabを再確認。
4. 通常のファイル編集ではTabの字下げが従来どおり動く。
5. 実行なし/入力空/ダイアログなし・元のmode/modelへ復帰。

Tests/CI/独立レビュー/GUI結果は固定HEADとともにIssue/PRへ記録する。GUI: pending。
