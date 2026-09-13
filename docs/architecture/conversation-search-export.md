# 保存会話の検索とMarkdown出力（#45）

## 範囲と配置

[保存契約 #44](conversation-persistence.md)のConversation/SavedTurn/ChatMessage、原文、安定IDを再利用する。旧XML・v1 schema・保持上限・保存workerは変更しない。新しいDB/index、provider履歴取得、ACP/printの正規化は追加しない。#254の修正は別PRであり、本変更は保存/表示された本文を忠実に扱う。

| 変更前 | 変更後・配置 | 境界 |
| --- | --- | --- |
| PastChatsCoordinatorの履歴chooser | 同じload/削除経路からHistorySearchDialogへ渡す | projectの読込workerと破損件数を再利用。dialogのsnapshotをcloseで解放 |
| 暗黙の一覧選択だけ | 明示検索欄、0件表示、最初の一致箇所の原文抜粋 | ConversationTranscriptの文字列検索をworker実行。保存型へ検索情報を追加しない |
| export未接続 | header「会話を書き出す…」と履歴選択の「Markdown出力…」 | controllerがEDT上のimmutable snapshotを返し、TranscriptExportがIDE保存dialog/通知/Openを所有 |
| timelineへの検索移動なし | 安定message IDを現在snapshotで再解決して本文bubbleへ移動 | user/assistantだけの順序をrestore/live回帰で照合。tab再生成・run停止・草稿変更なし |

新規ファイルは既存history/ui配下、既存ファイルは移動しない。UIから保存/検索の純粋処理を分け、IDE dialogをhistoryへ持ち込まない。新依存、登録XML、resource、Gradle、共通workflowの変更は不要。Change Impactは既存runtime/test分類を使う。

## 検索・移動

タイトルは正式なprovider会話名ではなく既存の冒頭previewである（#66待ちを維持）。ラベルとtooltipに「タイトル（冒頭文）・元入力・応答」を明示。user/assistantの元テキストを大文字小文字を区別しないliteral検索で調べ、会話ごとの最初の一致箇所を表示する。tool/error、注入context、providerの未保存履歴は本文検索に含めない。空検索は全件、0件は明示、旧metadataはタイトルのみ検索でき本文なしと表示する。読込不能件数は形式不明/破損としてファイルを保全する。

150ms後に背景workerで検索し、IME変換中は確定を待つ。query変更は旧worker取消と世代更新を行い、EDT反映でも世代/IME/close/project disposeを確認する。dialogは開いた時点の保存snapshotを使い、再表示で最新をロードする。結果を開く際は削除済み会話を拒否し、既存会話tabがあればそのまま選択する。

検索hitはChatMessage.idで保持し、移動直前の現在snapshotからqueryが残っているmessageを再解決する。user/assistantのID順とbubble数が一致しない場合、別の行へ推定移動せず再検索を案内する。print全文置換、ACP message境界、tool/statusの混在はHistoryNavigationTestで実timelineとRecorderを使って確認。移動後は次の送信まで末尾への自動スクロールを止め、入力欄の草稿/IMEを変更しない。一致messageまでの移動と抜粋であり、会話内Cmd/Ctrl+Fの全一致ハイライト/次前移動は本Issue外。

## 出力・失敗

選択時点のConversationを保存dialogより前に固定する。実行中の応答が後から伸びたり、選択tabが切り替わっても出力対象を変えない。元prompt/assistantは原文のMarkdownと順序を維持し、toolは既存の安全な状態要約、errorは既存固定文だけを出す。turn状態を記載し、実行中は「この時点までの内容」と示す。未送信draft、root/provider ID、credential設定、env、raw wire、注入contextを新しく出力へ追加しない。利用者自身が原文に記した情報まで除去したとの保証はしない。

ユーザーがIDE標準保存dialogで保存先を選んだ時だけUTF-8出力する。空/旧metadata-onlyは本文なし案内、取消は無書込み。保存先と同じdirectoryの一時fileを書き終えてからatomic replaceし、未対応/失敗時は旧fileを先に削除しない。symlinkの宛先は拒否。成功は通知の「開く」でIDEのファイルとして表示、失敗は固定日本語で案内し生例外を出さない。自動アップロード・公開linkはない。履歴には保存済みsnapshot、headerには現在の選択会話snapshotを使うため、保存失敗中でもheaderから取得済み本文を書き出せる。

## 公式比較とACP First

2026-09-12再確認。[Cursor Conversation search](https://cursor.com/help/ai-features/conversation-search)はAgents Windowの横断検索と会話内検索を記述しており、IDE内panelに同じ横断UIがある根拠として混同しない。IDE内panelのExport Transcriptは[固定vendor action調査 M02](../research/cursor-agent-menu-ux-2026-09-10.md)の選択会話→保存dialog→取消/失敗→通知/Openを採用する。公開資料・配布コード読取・実GUI未確認を区別する。

[JetBrains AI Chatの公式手順](https://www.jetbrains.com/help/ai-assistant/chat-mode.html#view-chat-history)はprojectごとの履歴・名前検索を備え、会話内の全一致検索はChat modeのみと記載する。ACP Agentまで同じ検索範囲だとは推定しない。[Cursor ACP](https://cursor.com/docs/integrations/jetbrains)と[IDE integration / IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)を含めて比較する。履歴の存在を独自機能と呼ばない。IDE/MCPはファイルや実行環境を操作できるが、本pluginの保存snapshot検索/出力保証とは別の能力である。上積みはAndroid Studio内でproject分離と既存tab/草稿/runを保ったまま直接保存・ファイル表示する接続であり、競合以上のUXを実測したとは宣言しない。

[ACP session setup](https://agentclientprotocol.com/protocol/v1/session-setup)のload/resumeはcapabilityとprovider接続を要する。本Issueは外部履歴取得でなく#44のローカル保存データ操作なので、先に既存保存/IDE APIsを使う。未検証のACP extensionsやCLI文字列解析で検索/出力機能を発明せず、外部session履歴接続は既存#115/#146/#141に残す。

## 検証

HistorySearchTest（literal/日本語/emoji/旧metadata/0件）、ConversationTranscriptTest（原文snapshot/順序/実file出力/旧file保全/破損隔離）、HistoryNavigationTest（実timeline/Recorderのprint・ACP・stale hit・非選択tab）、ToolWindowChatActionsTest（実行中/選択tab変更/dispose）を実施する。IME実入力、保存dialog取消/失敗、通知/Open、focus/スクロールは固定buildの[Case45](../verification/changes/issue-45.json)でGPT/human pendingとしてQAへ渡す。unit成功でGUI passにしない。
