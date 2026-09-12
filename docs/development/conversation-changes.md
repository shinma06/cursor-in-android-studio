# 会話・ターンの変更一覧（#47）

チャットの「… → ファイルの変更…」から、開いた時点までに受信したファイル編集を会話全体／ターンで絞り、IDEのDiff、既存の事後Revert、元の会話へ戻る操作につなぐ。現在のディスク全体やGitの未commit差分を列挙する機能ではない。shell経由など全文差分のない編集は含まれず、Revert後も受信記録として残る。

## データと境界

`AgentUiController`が会話ごとに`ConversationChanges`を保持し、既存listenerのEDT・token・停止guard通過後だけ更新する。保存済みConversation IDと実行tokenのturn IDを使い、provider call ID・正規化path・固定RestoreTarget・transportで重複を更新する。遅延した同一callの再通知は後続編集より後ろへ並べ替えない。異なるrootやtransportは別行にする。pathは固定rootに対して構文的に正規化し、symlinkの別名まで同一ファイルと推測しない。

- printは既存parserのcompleted fileEditの前後全文のみ。会話／ターン内の最初のbeforeと最後のafterをDiff表示する。途中のafter→beforeが連続しない、全文欠落、root不明・ISOLATEDは集約Revert不可。
- ACPは既存のmerged `AgentToolContent.Diff`を利用する。statusと内容更新を置換し、明示的に消えたDiffを除く。新規ファイルのoldText欠落は表示上の空文字。ACPは閲覧だけで、statusや保存IDからRevert権限を作らない。pending/failedのDiffも報告内容であり適用成功を意味しない。
- 空のafterは有効な内容。欠落したafterと混同しない。ファイルがすでに削除されていれば既存の実ファイル確認でRevertを拒否し、再作成はしない。新規ファイルを元の「存在しない」状態に戻す削除操作も追加しない。
- 一覧は固定snapshot。Revertは元controllerが生存し、選択tab、turnGeneration、観測内容が開いた時点と一致することを予約取得後と書込み境界で確認する。不一致なら開き直しを案内する。
- 個別カードと一覧は同じ`DiffViewerHelper.revertObservedEdit`→projectの`tryRestore`→`RestorePolicy`→`FileRevertOperation`を使う。準備／実行／停止／他の復元との排他、実root・worktree設定、外部path／symlink／.git除外、未保存変更、現在内容==afterを維持する。
- raw before/afterは開いているcontroller内だけ。#44の保存JSON、安全なtool要約、本文、stable IDを変更せず、履歴再表示では復元用データを再構築しない。controller終了でdialogも閉じる。「会話へ戻る」は捕捉したtabを選ぶだけで、送信・再開・復元・新規tabを起こさない。

## 比較と選定（2026-09-12確認）

[Cursor公式IDE Agent panel](https://cursor.com/help/ai-features/agent)は編集の即時適用、Diffからの確認・拒否、メッセージからのcheckpoint復元を説明している。本変更は散在するカードへの入口をまとめる範囲で、事前承認やCursorの全checkpoint能力との同等性は主張しない。[Agent Review](https://cursor.com/docs/agent/agent-review)のAIコードレビューも本Issueの機能ではない。

[Cursor ACP](https://cursor.com/docs/cli/acp)の構造化tool更新と[ACP標準Diff](https://agentclientprotocol.com/protocol/v1/tool-calls)を優先する。既存printには取得済み全文を再利用し、新しいCLI解析やAgent内部の変更追跡を追加しない。

比較対象はJetBrains AI Assistant + Cursor ACPにIDE/MCPを接続した構成まで含む。[JetBrains ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)はIDE内チャットとIntelliJ MCP Server公開設定を備え、[MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)はコード解析・ファイル操作・実行等を提供する。IDE操作自体を独自機能としない。今回のPlugin内の改善は、会話／turnの取得済み差分から、未保存editor内容と実rootを確認する既存IDE復元経路への直接導線である。競合の同一snapshot拒否条件やturn別一覧UIの同等性は未検証。実IDEでの到達性・日本語表示・焦点移動はCaseで確認する。

## 検証

`ConversationChangesTest`は公開print fixture→一覧→実ファイル復元、重複・複数turn・空・欠落・不連続・ACP更新・root分離を確認する。既存のFileRevertOperation/RestorePolicy/WorkspaceOperationGate/配送テストを併走し、headerのメニュー回帰も確認する。単体テストはnative dialogや実AgentのGUI受入を代替しない。[Case正本](../verification/changes/issue-47.json)のGUIはpendingを保持する。

[アーキテクチャの所有境界](../architecture/README.md)と[復元接続契約](restore-target-integration.md)を変更せず、UI→既存予約／IDE復元を共有した。DB・Android ActivityのLifecycle・coroutineは使わない。Swing EDT、controller dispose、process/run tokenの寿命を対象とする。
