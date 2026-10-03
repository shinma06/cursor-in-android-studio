# 送信キー設定と確定操作 (#97)

既定のEnter送信・Shift+Enter改行を維持し、設定画面からmacOS Cmd+Enter / 他OS Ctrl+Enter送信を選べる。後者ではEnterは改行になる。保存先は既存のCursorAgentSettings XML内のsendKeyMode。旧XMLで項目がなければENTERのまま。実行設定・mode/modelの保存規約は変えない。

## 入力の責務

- 送信だけをIntelliJ AnActionの専用shortcutとして登録し直す。改行・貼付・caret・Tab移動はEditorTextFieldの既存処理を使い、キー変更で本文を代入し直さない。
- 設定Applyは[IDE標準Message Bus](https://plugins.jetbrains.com/docs/intellij/messaging-infrastructure.html)で表示中Composerへ通知。removeNotifyで購読を解除し、再表示時に最新値を読み直す。各tabの下書き・caret・mode/modelを変更せず、入力focusも要求しない。
- #258のIME確定イベント後まで保護するgeneration付き処理をPromptImeGuardへ共通化し、入力欄・slash・mention検索で使う。遅いresetで新しい変換を解除しない。候補側のEnter/Escape/上下とダブルクリックは変換中に作用しない。
- submitはIME・slash/mention popup・実行中・入力無効時に受け付けない。候補のEnterは選択だけ、Escapeは取消だけ。実行中は送信キーで予約やStopを呼ばず、予約ボタンとStopボタンを明示操作する。Controllerのrun token/同期開始・終了処理は既存のまま。
- 設定変更・複数行貼付だけで送信しない。選択済みコマンドや明示contextは各Composerに残り、#24/#48/#258の送信・予約snapshotを継続する。

## 現行公式能力との比較（2026-09-12）

| 基準 | 今回の対応と限界 |
| --- | --- |
| [Cursorのprompt・候補操作](https://cursor.com/docs/agent/prompting) | @と/による候補入力、Enterによる単発Skills選択を尊重。公式ページは複数surfaceを含み、Custom ModeはAgents Window/CLIの記述。IDE内panelの全キーを実測したとはしない。 |
| [JetBrains AI Assistantのキー](https://www.jetbrains.com/help/ai-assistant/ai-keyboard-shortcuts.html) | Enter送信/Shift+Enter改行とKeymap変更は既存能力。Ctrl+EnterのSend Nowも公式にあるが、本Pluginでは#97受入に従い実行中の送信キーは割込み・予約にしない。 |
| [Cursor + JetBrains ACP](https://cursor.com/docs/integrations/jetbrains) | Agentのproject読取・編集・terminal実行は既存能力。今回はACPへキー設定を送らず、IDEクライアントの入力操作で完結する。 |
| [JetBrains ACP](https://www.jetbrains.com/help/ai-assistant/acp.html) + [IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html) | IDE tools/MCPを含む構成を比較基準とする。キー変更に新たなMCP/CLI経路は不要。直接IDE context・会話別draft・明示queueとの一貫性が本Pluginで検証する範囲。 |

競合にない独自能力やUX優位を主張しない。日本語IME・実IDEのfocus/キー配送・各OS・テーマの成功は固定buildでのGUI確認待ち。

## 依存と検証

D9979266から専用branchを作り、停止済み#258実commit `86934b13a81243b2772daf6a07f20dbf2ad6f95a` を通常merge。#97固有レビューbaseはこの固定版。#24/#48を含む全候補の統合先はdevelop。依存統合後にorigin/developの通常merge・実target全差分・必要テスト・Case・CI・独立レビューを更新する。それまでenroll/ready/mergeしない。#251監査・PR #261保留を維持する。

自動回帰は保存XMLの旧形式/両方式、OS別shortcut、複数入力欄のdocument/caretとnative編集キー保持、IME確定直後/遅着、mention/slashの選択・取消・移動を確認。既存SessionTabs/PromptQueue/入力focusのテストも全体テストで維持する。Swingの合成入力テストは実IDEのキー配送成功を代替しない。

[Case正本](../verification/changes/issue-97.json)はMV-010/011/016に対応する新4Case、GPT/人間ともpending。旧MVや依存Caseの結果は変更しない。統合担当PMが専用QAとmain未反映を双方向link/readbackして引き継ぐ。
