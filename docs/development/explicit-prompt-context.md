# Add to Chatと明示context（#24）

## 固定依存と比較基準

integration targetはdevelop。依存は[PR279 / #48](https://github.com/shinma06/cursor-in-android-studio/pull/279)の固定`dd655160e136a44250f990932a3fe77d686f951f`。Bの独立approved・全CI成功・clean/正式writer停止を確認し、実commitを通常mergeで祖先へ取り込んだ。#24固有レビューの比較baseも同SHAとする。developの`9979266b4e99e7d4dc46689dac1f61f33f09b88a`からの全候補検証とは区別する。

#48がdevelopへ統合されるまで#24をenroll/ready/mergeしない。統合後はorigin/developを通常mergeで同期し、実target baseに対する最終差分・Case・CI・独立レビューを取り直す。#43/#215の停止branchは編集しない。B98はtimeline/notification/AgentTurnListenerFactoryを担当し、#24のController/Composer/Root/Queueと同時編集しない。

## 公式仕様と保証範囲（2026-09-12確認）

Cursorの現行IDE AgentではFiles/Folders、Terminals、Chats、Git diffs、Browserを別のcontextとして案内する。既知の対象にはmention、対象が不明ならAgent側検索を使う構成。[Cursor Prompting](https://cursor.com/docs/agent/prompting)

比較対象のJetBrains構成は、AI ChatでCursorをACP registryから導入し、Cursor認証・公開モデル選択・Agentのファイル/terminal能力を使う公式経路とする。独自pluginのprint/ACP実装と、公式clientが提供するUI/能力を混同しない。[Cursor JetBrains](https://cursor.com/docs/integrations/jetbrains)、[JetBrains ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)

JetBrains AI AssistantのChat modeには自動context、手動添付、検索、選択添付、previewがあるが、これらすべてをCursor ACPで同じように利用できるとは公式説明から断定しない。比較時はChat modeと外部Agentを分ける。[JetBrains context](https://www.jetbrains.com/help/ai-assistant/chat-mode.html)、[AI Chat](https://www.jetbrains.com/help/ai-assistant/ai-chat.html)

| このpluginの項目 | 実際に送る内容 |
| --- | --- |
| 選択Add to Chat | 追加時のpath/範囲/本文を固定。送信/予約登録前に元documentの変更を検知し、再追加/削除を促す |
| 自動context | 利用者が有効/無効を選び、現在のfile/selectionを表示。実turn開始時に再取得 |
| File | 参照pathを固定し、実turn開始時の本文を添付。エディターの未保存変更を含む |
| Folder | 直下の名前一覧。配下全本文を再帰添付しない |
| Git diff / Branch | 既存git差分を背景処理で取得。Branchの既存base解決を維持 |
| Terminal | 既存末尾出力。無効/未起動/取得不能はplaceholderで説明 |
| Docs / Web | tool利用hintのみ。検索・取得実行済みと表示しない |

画像/binary本文、Skills258、Chats本文取得、Browserの実データ添付、意味検索の再現は別scope。queryごとにproject全体を探索し、一致したテキストfile/folderの表示だけを最大500件に制限する。空queryの先頭500件に含まれないfileも、名前で検索すれば候補になる。候補収集はpooled thread + read action、検索/取消はUIで可能。loading/取得失敗/0件/多数を区別する。旧#5はopenで既存観察と未確認が混在しており、過去passを本候補へ転記しない。

## データと送信時点

PromptContextDraftをComposerごとに持ち、明示selection/referenceを本文文字列から分離する。日本語・空白pathも参照全体を保持し、手入力@tokenだけは従来のbest effort互換。明示selectionは同file/rangeの再追加で更新、referenceは同kind/pathを一意にする。自動selectionとの同一範囲・同一本文や、実際の全file本文に含まれる同じ範囲を二重注入しない。fileが変わって旧selectionを含まなくなった場合は、固定した明示selectionを落とさない。同じ範囲でも本文が変わった自動selectionは別に残し、送信本文でも明示snapshotと開始時の自動selectionを識別する。

通常送信はrun受理前にsnapshotを検証し、受理後に明示添付を下書きから取り除く。準備中に次の下書きを変えてもsnapshotへ混入しない。予約送信は登録時にselection本文・明示参照のidentity・自動context有効設定を項目へ保持する。自動contextと参照先の内容は#48の実turn開始時に取り込む。予約のtext編集/順序変更でも添付は同じ項目に残り、contextボタンで固定selectionと参照をpreviewできる。別draftのchipを予約送信で消したり読み直したりしない。

Add to ChatはEditorPopupMenuと入力欄近くの「選択を追加」から実行できる。既存chatの送り先はfocus変更前に捕捉し、閉じたViewへの追加を無視する。明示rowはpreview/変更/削除、自動contextは別の表示とtoggleを持つ。明示添付だけ残った下書きもtab close時の破棄確認に含める。

## 検証・残す実機確認

自動テストでsnapshotの不変性、tab分離、重複/古いselection、空白path、予約text編集/dispatch、候補loading中検索/0件/500件/矢印EnterEscape/IME合成中抑制を確認する。popupの破棄で収集Futureをcancelし、遅着はquery世代・破棄・project・表示を再確認する。元の入力が編集されたpopupは閉じて誤置換を防ぐ。context表示のtimerは非表示/破棄で停止する。

GUI/実CLI/インストール/再起動は未実施。[Case24](../verification/changes/issue-24.json)と[手動マトリクス](../manual-verification/matrix.md)へ新規pendingを追加し、#5の旧結果は変更しない。Dark/Light × 通常/Compact、狭幅、実IME/EnterEscape、Terminal無効、文書変更とtab/queue競合を固定source/ZIP/installed全JAR/起動ロード実体で確認する。Android Lifecycle/Context/DB/coroutineはSwing/IDE処理なので対象外。EDT・所有権・キャンセル・失敗経路は上記で扱う。
