# 会話本文の表示設定（#268）

Settings → Cursor in Android Studio の「会話本文の文字サイズ」と「コードの長い行を折り返す」で変更する。
Applyで全projectの表示中の会話へ反映し、非表示/新規/保存から再表示した会話も最新値を使う。Cancel/resetは適用前の変更を破棄する。

文字サイズは標準（保存値0）、8〜36 ptの整数。標準は従来のAgentUiMetrics.textFont（IDE UI labelの0.92倍）を保持し、IDE表示文字・拡大率に追従する。
指定値もJBUI.Fonts.label(size)でIDE user scaleを適用する。画面DPIを独自に二重適用しない。旧XMLの欠損は標準、範囲外の整数はsetterで標準へ戻す。
コード折返しは既定false、欠損もfalse。通常editor/composer/ツールカードの文字設定や密度・配色は変更しない。

MessageTextPaneは表示中だけApplication MessageBusへ接続し、removeNotifyで切断、再表示で設定を再取得する。
背景配送はEDTへ移し、遅着時に同じ接続かを確認する。設定は本文documentを再生成しない。折返し・native UI更新でviewを再構築し、caretのdot/markを復元する。
Swing標準HTMLFactoryがPRE用に作るLineViewは折り返さないため、有効時だけ既存ParagraphViewへ切替え、空白なしの長いコードも幅内に収める。
既存commonmarkと画像非取得rendererを保持し、HTMLやMarkdownへ改行・タグを書き込んで折り返さない。
本文ごとのnative JScrollPaneが長い行の横移動を提供する。通常wheelは外側の会話へ配送し、Shift+wheelは本文の横移動に使う。
HTML stylesheetは各paneに分離し、他の本文やIDEの描画へルールを漏らさない。

元message.text・rawMarkdown・保存/出力経路とrun/controllerは変更しない。原文コピー、選択方向、既存の画像自動取得防止を回帰テストする。
UIやcontrollerに送信/再開を追加しない。Context/Viewの寿命はSwing接続で扱う。DB/Android Lifecycle/coroutine/外部設定変更は本変更の経路にない。

## 比較・根拠と未確認範囲

2026-09-13に [Cursor IDEのテーマと文字設定](https://cursor.com/help/customization/themes)、[JetBrains AI Chat](https://www.jetbrains.com/help/ai-assistant/ai-chat.html)、[IntelliJ typography](https://plugins.jetbrains.com/docs/intellij/typography.html)、[IDE editor fontとUI fontの区別](https://www.jetbrains.com/help/idea/settings-editor-font.html) を確認した。
Cursor資料はIDE/editor/terminalの設定を説明しており、会話コード折返しの同等設定まで保証しない。JetBrains AI Assistant + Cursor ACP + configured MCP + IntelliJ MCP Serverの連携構成も比較対象とし、既存chat/IDE表示調整自体を独自機能と呼ばない。
今回の改善は、このplugin内の会話原文・選択・タブ/run所有を保つ日本語の表示設定である。同等以上の操作性はGUI未受入であり、ドキュメント確認だけで達成としない。
[JBUI実装](https://github.com/JetBrains/intellij-community/blob/master/platform/util/ui/src/com/intellij/util/ui/JBUI.java) と [JDK21 HTMLEditorKit](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/share/classes/javax/swing/text/html/HTMLEditorKit.java) の標準機構を使い、新規依存・独自テーマ基盤を追加しない。

[Case268](../verification/changes/issue-268.json) は旧設定/保存・既存/新規・日本語/狭幅/コード・Dark/Light/高DPI/通常Compact・実行中の選択と原文を固定buildで確認する。全てGPT/人間pending。JUnitは実IDEの見た目や入力操作のpassを代替しない。
依存は#97と#295（#41を含む）を通常mergeした固定base。#28診断・#215focus・#300permission説明・B #263Taskの停止成果を編集せず、必要な設定導線と共通表示の最終統合はPMへ引き継ぐ。
