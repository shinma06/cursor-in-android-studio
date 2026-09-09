# Cursor Agent UI 閲覧調査（2026-09-05）

> 履歴資料。現在の機能・UI/UX・main/develop差分とCLI実現性は[2026-09-08比較マトリクス](cursor-agent-capability-matrix-2026-09-08.md)を参照。以下の当時の観測・優先度を最新状態やGUI合格として転用しない。

関連: [差分取り込み計画](../plans/cursor-agent-ui-gap-plan.md) / [親 Issue #19](https://github.com/shinma06/cursor-agent-plugin/issues/19)

追補: その後、[Android Studioプラグインの実機UI調査](android-studio-ui-followup-2026-09-05.md)を実施。以下の元表はコード比較として保持し、実機で確認できた範囲は追補に分けて記録した。

## 調査条件と証跡の読み方

- macOS の Cursor、ワークスペース `cursor-agent-plugin` を UI ベースで閲覧。Cursor のバージョン番号は `[要検証]`。
- 画面、メニュー、設定ページを開き、スクロールして表示文言とアクセシビリティ情報を確認した。プロンプト入力・送信、Agent/CLI 実行、ファイル編集、設定値変更は行っていない。
- 初めは New Agent の空画面。途中で画面に現れた会話の進行・完了表示も閲覧したが、調査者から送信した会話ではない。
- モデル選択に有料プランへの案内が表示された。アカウントの契約全体や Teams CLI 検証アカウントとの同一性は未確認。表示値を製品の初期値・全利用者共通仕様と解釈しない。
- 証跡はこの調査で観測した UI 文言の転記。スクリーンショットは調査時に閲覧したが、画像ファイル・生の AX ログはリポジトリへ保存していない。再検証時には機密情報を除いた画像とバージョンを追加する。
- 実装比較はローカル `feature/f23-sandbox-prompt-builder`、`6e1ee971da7bae9111dc30a1f5c17435ae8deb1f` のソース読取り。文書化時の `origin/main` (`1a402a9`) との `src/` 差分はなし。プラグインを起動しての動作検証ではない。
- `[要検証]` は未確認、`[仮説]` は推論・設計案。ボタンや設定の存在確認と、その動作・CLI 互換性の確認を分ける。
- 優先度は本プロジェクト向けの提案（工数・効果は `[仮説]`）。P0=誤認防止・既知の安全性問題、P1=日常操作、P2=拡張、保留=調査ゲート、低=IDE 標準に委譲。対応順・Issue は計画書を正本とする。

## A. Agent タブ・入力・会話

| ID | Cursor 側の機能 | 確認した UI 証跡 | 本プラグインの対応状況（コード確認） | 差分 | 実装候補の優先度 |
|---|---|---|---|---|---|
| UI-01 | Agent パネル表示 | `Toggle Agents ⌥⌘J`、右側の Agent タブ | 右側 Tool Window | Cursor と同じショートカットは未実装 | P2 |
| UI-02 | 新規会話・置換 | `New Agent ⌘N`、`[⌥] Replace Agent` | New Chat あり | 置換導線・複数会話タブなし | P2 |
| UI-03 | モード選択 | `Agent / Plan / Debug / Multitask / Ask`、Agent にチェック | Ask/Agent/Plan の3択 | Debug/Multitask なし。動作・CLI 対応は `[要検証]` | 保留 |
| UI-04 | モデル選択 | `Cursor Grok 4.6 Medium` → `Upgrade to unlock premium models` | CLI モデル一覧取得・選択保存 | プラン制限の専用表示なし。Cursor 全モデル一覧は `[要検証]` | P1 |
| UI-05 | コンテキスト追加 | 入力欄 `@ for context` | ファイル/フォルダ/Git diff/Branch/Terminal/Docs/Web 候補 | 入力禁止のため Cursor 候補・チップの詳細は `[要検証]` | 保留 |
| UI-06 | Skills 呼出し | 入力欄 `/ for skills` | 専用一覧・補完なし | 発見導線なし。CLI 処理は `[要検証]` | P2・調査先行 |
| UI-07 | 添付導線 | 入力欄右下にクリップ形アイコン | 添付 UI なし | 種別・選択画面は `[要検証]`。画像対応と断定しない | 保留・既存 #10 |
| UI-08 | 音声入力 | マイク形アイコン、Voice Submit Keywords、登録語 `submit` | 専用音声 UI なし | 録音・送信動作は `[要検証]` | P2・既存 #10 |
| UI-09 | 進行状態 | `Planning next moves`、実行中入力欄 `Add a follow-up` | Preparing/Running/Thinking/ツール名、Stop | 状態文言・追加入力が異なる。Cursor の停止操作は `[要検証]` | P1 |
| UI-10 | 思考・処理時間 | `Thought 1s`、時刻情報 `Worked for 2s` | Thinking の先頭80文字を状態表示 | 独立表示・時間なし。展開内容は `[要検証]` | P1 |
| UI-11 | 応答コピー | 応答下 `Copy message` | 専用ボタンなし | ワンクリックコピーなし | P1 |
| UI-12 | 会話分岐 | 応答下 `Fork chat` | 未対応 | 分岐範囲・CLI 可否は `[要検証]` | P2・調査先行 |
| UI-13 | 履歴検索・アーカイブ | Show Chat History → `Search Agents...` / `No matching agents` / `Archived` | 冒頭文・更新日時一覧、セッション再開 | 明示検索欄・アーカイブ・再開時の過去本文表示なし。Cursor の過去本文再読込は未確認 | P1 |
| UI-14 | 会話出力・診断 | ⋯ → `Export Transcript / Copy Request ID / Give Feedback` | 専用 UI なし | 保存・問い合わせ情報への導線なし。出力形式は `[要検証]` | P1：出力、P2：診断 |
| UI-15 | ブラウザ・設定導線 | ⋯ → `Open Browser / Agent Settings` | 専用ブラウザなし、IDE Settings に設定ページ | Agent タブから設定への直接リンクなし | P1：設定、ブラウザは保留 |

## B. Agent Settings

導線: Agent タブの ⋯ → Agent Settings → 見出し Agents。

| ID | Cursor 側の機能 | 確認した UI 証跡（調査時の値） | 本プラグインの対応状況（コード確認） | 差分 | 実装候補の優先度 |
|---|---|---|---|---|---|
| UI-16 | 文字サイズ・コード折返し | Text Size=`Default`、Code Block Word Wrap=`off` | Markdown 描画、専用設定なし | 個別調整なし | P2 |
| UI-17 | 送信キー変更 | Submit with ⌘ + Enter=`off`。on 時 Enter 改行との説明 | Enter 送信の固定登録 | 送信キー選択なし | P1 |
| UI-18 | 既定モデル | Default Model=`Cursor Default` | 選択モデルをアプリ設定に保存 | 既定と現在の会話を分けない | P2 |
| UI-19 | 実行中メッセージのキュー | New Messages=`Queue` | 実行中入力欄を無効化 | 指示の入力・予約なし | P1 |
| UI-20 | キューからの割込み | Manually Sent Messages from Queue=`Interrupt` | Stop、キューなし | 割込み送信なし。実処理は `[要検証]` | P2・調査先行 |
| UI-21 | 入力補完 | Agent Autocomplete=`on` | `@` 候補あり | 一般的なプロンプト補完なし | P2 |
| UI-22 | 自動モード遷移 | Auto-Approve Mode Transitions=`off`。off は確認、未回答15秒でスキップとの説明 | 手動選択のみ | 遷移要求・承認 UI なし。説明どおりの動作は `[要検証]` | 保留 |
| UI-23 | 他ツール設定取込み | Include Third-Party Plugins, Skills, and Other Configs=`on` | 専用設定なし | CLI 読込範囲は `[要検証]` | P2・調査先行 |
| UI-24 | Web ツール制御 | Web Search Tool=`on`、Auto-Accept Web Search=`off`、Web Fetch Tool=`on` | Docs/Web のヒント注入 | 検索・取得・自動承認の個別制御なし | 保留 |
| UI-25 | MCP 認証待ち | Wait for MCP Authentication=`on`。off は30秒でスキップとの説明 | MCP 一覧・有効無効 UI | 認証待ち設定・専用状態なし | P2・調査先行 |
| UI-26 | 権限・sandbox | Run Mode 4択: `Allowlist` / `Allowlist (with Sandbox)` / `Auto-Review (with Sandbox)` / `Run Everything (Unsandboxed)`。2番目を選択中 | 権限3択＋sandbox3択を別々に選択 | UI の区分が異なる。同名・類似名で意味が一致するとは限らない | P0：意味・現在値の明示 |
| UI-27 | sandbox ネットワーク | Auto-Run Network Access=`sandbox.json + Defaults`。workspace の sandbox.json 編集との説明 | 専用 UI なし | 適用元・ドメイン範囲を表示しない | P1候補・調査先行 |
| UI-28 | コマンド許可リスト | Command Allowlist、入力欄、`+ git status` 等、Add Suggestions | 専用 UI なし | 個別許可 UI なし。`+` は追加提案であり許可済みとは未確認 | 保留 |
| UI-29 | MCP 許可リスト | MCP Allowlist、`server:tool` / `server:*` / `*:tool` / `*:*` の説明 | サーバー単位 Enable/Disable | ツール単位の自動実行許可とは別機能 | 保留 |
| UI-30 | URL取得の許可リスト | Fetch Domain Allowlist、ドメイン入力欄、ワイルドカードの説明 | 専用 UI なし | 取得の自動許可範囲を設定できない | 保留 |
| UI-31 | 個別保護 | MCP Tools Protection=`off`、File-Deletion Protection=`off`、External-File Protection=`on` | 同名設定なし | MCP・削除・workspace 外編集の個別保護なし | P0：制約表示、制御実装は保留 |
| UI-32 | 差分レビュー | Inline Diffs=`on`、Jump to Next Diff on Accept=`on`、説明に `⌘Y` | View Diff/Revert カード | インライン表示・次差分移動なし。Cursor 実レビュー画面は `[要検証]` | P1 |
| UI-33 | 完了時整形 | Auto Format on Agent Finish=`off` | 専用設定なし | 完了後自動整形なし | P2 |
| UI-34 | 選択コード操作 | Toolbar on Selection=`on`、Add to Chat & Quick Edit の説明 | アクティブファイル・選択範囲を自動注入 | 明示追加の選択ツールバーなし。実ツールバーは `[要検証]` | P1：Add to Chat |
| UI-35 | Terminal/Quick Edit | Legacy Terminal Tool / Auto-Parse Links / Terminal Hint / Preview Box for Terminal ⌘K がすべて `off` | `@terminal` 出力参照のみ | Terminal 内編集・プレビューとは異なる | P2・スコープ判断 |
| UI-36 | 差分配色 | Themed Diff Backgrounds=`on` | IDE 標準 Diff Viewer | 専用設定なし、IDE 側依存 | 低 |

## C. General・拡張導線・未確認領域

導線: タイトルバー Open Cursor Settings → General。Customize は案内のみ確認。

| ID | Cursor 側の機能 | 確認した UI 証跡 | 本プラグインの対応状況（コード確認） | 差分 | 実装候補の優先度 |
|---|---|---|---|---|---|
| UI-37 | 会話の詳細度 | Conversation Density=`Detailed` | 固定ツールカード | 詳細度切替・折畳み設定なし | P1 |
| UI-38 | レビュー操作の配置 | Review Control Location=`Breadcrumb`、説明に floating island | チャットカード内操作 | 配置選択なし | P2 |
| UI-39 | タブ・レイアウト | Window Layout=`Editor`、Open Chat as Editor Tabs=`on`、Auto-Hide Editor When Empty=`off` | 単一 Tool Window | 複数タブ・自動最大化なし | P2 |
| UI-40 | タイトル・ステータスバー | Title Bar / Status Bar=`on` | IDE の枠組み | 専用切替なし | 低 |
| UI-41 | 通知・音 | System Notifications=`on`、Menu Bar Icon=`on`、Completion Sound=`off` | 完了・ツール開始通知設定 | Cursor 説明は「完了/注意が必要」。通知契機が異なる。音設定なし | P1：契機、P2：音 |
| UI-42 | 利用状況 | Agent Stats=`0/0 (0%)`、No commit scored | 表示なし | 採用統計なし。算出方法は `[要検証]` | P2・調査先行 |
| UI-43 | カスタマイズ入口 | `Plugins, MCPs, Skills, and Rules have moved to Customize`、Open Customize | MCP ダイアログのみ | 統合入口なし。移動先の内容は `[要検証]` | P2 |
| UI-44 | アカウント・プライバシー | Cursor Account=`Open`、Upgrade to Pro、Privacy Mode=`Configure` | CLI パス設定のみ | 状態・設定案内なし。CLI との共有は `[要検証]` | P2・調査先行 |
| UI-45 | チェックポイント復元 | Restore 相当 UI を確認できず `[要検証]` | Git スナップショット・復元 UI | Cursor の復元範囲・操作単位は比較不能 | 保留 |
| UI-46 | Worktree・ツール承認・エラー | 該当会話/画面を確認できず `[要検証]` | Worktree 選択、ツールカード、エラー表示 | Cursor 側を機能なしと判断できない | 保留 |

## 実装比較の根拠

ソースリンクは現在のコードへの導線。履歴比較は上記コミットを固定して参照する。

- [ComposerPanel](../../src/main/kotlin/com/cursoragent/ui/composer/ComposerPanel.kt)、[ModeSelector](../../src/main/kotlin/com/cursoragent/ui/composer/ModeSelector.kt)、[ModelSelector](../../src/main/kotlin/com/cursoragent/ui/composer/ModelSelector.kt): 入力、モード、モデル、overflow、Stop。
- [AgentSettingsState](../../src/main/kotlin/com/cursoragent/settings/AgentSettingsState.kt)、[AgentSettingsConfigurable](../../src/main/kotlin/com/cursoragent/settings/AgentSettingsConfigurable.kt)、[AgentProcessService](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt): 保存値、設定 UI、CLI フラグ。
- [AgentUiController](../../src/main/kotlin/com/cursoragent/ui/AgentUiController.kt)、[AgentTurnListenerFactory](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt): 入力の無効化、状態、時間表示の不足、通知。
- [AssistantMessageBubble](../../src/main/kotlin/com/cursoragent/ui/timeline/AssistantMessageBubble.kt)、[FileEditCard](../../src/main/kotlin/com/cursoragent/ui/timeline/FileEditCard.kt)、[ToolCallBubble](../../src/main/kotlin/com/cursoragent/ui/timeline/ToolCallBubble.kt): 応答・差分・ツールカード。
- [ChatHistoryState](../../src/main/kotlin/com/cursoragent/settings/ChatHistoryState.kt)、[PastChatsCoordinator](../../src/main/kotlin/com/cursoragent/ui/PastChatsCoordinator.kt): メタデータのみ保存、再開時本文クリア。
- [MentionCandidateSource](../../src/main/kotlin/com/cursoragent/ui/composer/mention/MentionCandidateSource.kt)、[MentionPopupController](../../src/main/kotlin/com/cursoragent/ui/composer/mention/MentionPopupController.kt)、[PromptContextBuilder](../../src/main/kotlin/com/cursoragent/ui/PromptContextBuilder.kt): 候補とコンテキスト注入。
- [McpServersDialog](../../src/main/kotlin/com/cursoragent/ui/mcp/McpServersDialog.kt): サーバー一覧と有効無効。

## 観測からは確定しない事項

Cursor UI と CLI の権限の同一性、全モデル、添付形式、`@`/`/`候補、Debug/Multitask/分岐/割込みの実行仕様、承認・エラー・復元の実画面は `[要検証]`。追加調査でも実行・送信が必要な段階と閲覧段階を分ける。

`Ask Every Time` の説明と headless CLI の即時書込みの差、Worktree 分離と checkpoint 保存先の不一致は [CLAUDE.md](../../CLAUDE.md) の**既存検証・既知の課題**に由来する。今回 Cursor UI で再検証した事実ではない。計画の P0 に取り込むが、UI 観測と混同しない。
