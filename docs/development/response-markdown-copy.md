# 応答のMarkdown原文コピー

#41。各assistant応答の下に「コピー」を置き、クリック時点でそのバブルに渡された最新のMarkdown全文をIDEのclipboardへ送る。表示HTMLからの逆変換、trim、改行変換、コードフェンス除去は行わない。本文選択のコピー、コードブロック操作、#45の会話全体exportとは別の操作である。

| 応答状態 | 操作 |
| --- | --- |
| 本文が空文字 | ボタン無効。clipboardを変更しない |
| 生成中 | クリック時点の全文。未完成のMarkdownもそのままコピー |
| 完了・停止・失敗・中断 | そのバブルに残っている本文をコピー。後続の応答を参照しない |
| コピー成功 | 「コピー済み」。本文が更新されたら「コピー」に戻る |
| clipboard例外・読戻し不一致 | 「コピー失敗」。同じボタンで再試行。例外の生情報は表示しない |

空白だけの本文は空文字と区別してそのままコピーする。成功表示はタイマーで消さず、その本文をコピーした状態として保持する。同一本文の再配送では保持し、本文更新で解除する。各ボタンはTabで到達でき、フォーカス中のSpace/Enterで作動する。会話送信のキー割当は変更しない。

実装は `AssistantMessageBubble` 内の原文Stringと操作に限る。既存の `setAssistantText` → `setContent` は全文置換なので、append推測や別の会話モデルは追加しない。復元されたassistant本文も同じ経路を通る。print/ACPのwire正規化より後の本文が原文の境界であり、受信前の欠落や正規化を復元する機能ではない。raw HTMLのエスケープ表示は維持する。

clipboardはプラットフォームの `CopyPasteManager.setContents(StringSelection)` と文字列の読戻しで確認する。操作はSwingのEDT上で完結する。project/serviceの保持、タイマー、背景ジョブ、永続化は追加しない。タブごとの配送と終了済みrunの除外は既存のtoken検査を使用する。DB・Android Lifecycle・Contextの変更はない。

2026-09-12に公式資料を照合した。[Cursor IDE Agent](https://cursor.com/docs/agent/overview)のsidepaneを対象とし、[Shared transcripts](https://cursor.com/help/ai-features/shared-transcripts)の会話全体を共有するリンクとは分ける。[JetBrains AI Assistant](https://www.jetbrains.com/help/ai-assistant/chat-mode.html)はコードスニペットのclipboard・適用・ファイル作成を提供するため、コピー自体を独自機能とは呼ばない。今回の上積みは本Pluginの既存の応答本文へ、生成中を含む応答単位の原文コピーを直接接続すること。これらの資料だけでは競合の応答全体コピーの厳密な原文保持・途中更新仕様を断定できない。ACP/MCPの新しいtoolやprovider要求は不要で、[IDEのclipboard API](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/openapi/ide/CopyPasteManager.java)を利用する。IDE統合/MCPの既存能力を非対応とは扱わない。

`AssistantMessageCopyTest` は原文の完全一致、空/更新/訂正、失敗と再試行、別バブルの分離、200px幅の折返しと操作欄、キーの割当を検証する。既存 `MessageTextPaneTest` のHTML更新・折返しも維持する。実OS clipboard、フォーカス移動、実Agentの終了/失敗、保存履歴、別タブの操作は [Case41](../verification/changes/issue-41.json) の固定build GUI受入へ分ける。#45/#47/#215の停止差分にこのバブルの変更はなく、共有Controller/Composer/timelineの変更は追加しない。
