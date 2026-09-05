# 動作確認マトリクス

**更新ルール**: エージェントが人間確認が必要な変更を入れたら行を追加。人間が確認したら `Status` / `Verified by` / `Date` を更新。

**UI差分計画（2026-09-05）**: [取り込み計画](../plans/cursor-agent-ui-gap-plan.md) / [#19](https://github.com/shinma06/cursor-agent-plugin/issues/19)。
今回追加したのは調査記録と計画のみ。以下の既存QAは未実施のまま、新機能用の行は各 UX Issue の実装時に実際の Branch・手順を確定して追加する。
Cursor の閲覧調査を、このプラグインの `pass` や実行・送信を伴う検証の実施許可と扱わない。

**推奨ブランチ（2026-09-04 時点）**: `feature/f23-sandbox-prompt-builder` — PR [#18](https://github.com/shinma-postas/cursor-agent-plugin/pull/18)（#16 / #17 を包含。マージ後は #16/#17 を close）

---

## A. 基本チャット（回帰）

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-001 | `feature/f23-sandbox-prompt-builder` | #18 | プロンプト送信・ストリーミング | Cursor Agent ツールウィンドウを開き、短文を送信 | ユーザーバブル → 応答がストリーム表示される | pending | | |
| MV-002 | 同上 | #18 | Stop | 長めのプロンプト実行中に Stop | 処理が止まり入力欄が再有効化 | pending | | |
| MV-003 | 同上 | #18 | 新規チャット | New Chat | タイムラインがクリアされる | pending | | |

## B. 通知・メンション・設定

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-010 | 同上 | #18 | Enter / Shift+Enter | Enter で送信、Shift+Enter で改行 | 意図どおり（改行が送信されない） | pending | | |
| MV-011 | 同上 | #18 | `@` ポップアップ | 入力欄で `@` | 候補表示・選択でトークン挿入・位置/フォーカスが自然 | pending | | |
| MV-012 | 同上 | #18 | `@branch` | `@branch` を含むプロンプトを送信 | エラーなく送信（ブランチ diff コンテキスト注入） | pending | | |
| MV-013 | 同上 | #18 | `@terminal` | **Terminal タブを開いた状態**で `@terminal` 送信 | ターミナル出力が注入される | pending | | |
| MV-014 | 同上 | #18 | `@terminal`（タブなし） | Terminal 未オープンで `@terminal` 送信 | プレースホルダーメッセージでエラーにならない | pending | | |
| MV-015 | 同上 | #18 | デスクトップ通知 | ターン完了・ツール開始（設定 ON） | OS 通知。クリックで Cursor Agent が開く | pending | | |
| MV-016 | 同上 | #18 | 設定ページ | Settings → Tools → Cursor Agent | CLI パス・通知 ON/OFF を保存できる | pending | | |
| MV-017 | 同上 | #18 | ツールウィンドウ close | 実行中に Cursor Agent ペインを閉じる | CLI プロセスが止まる（ゾンビ化しない） | pending | | |

## C. ツール表示・diff・revert

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-020 | 同上 | #18 | シェル出力カード | 「`echo HELLO_TEST` を実行して」と依頼 | タイムラインに stdout 付きシェルカード | pending | | |
| MV-021 | 同上 | #18 | ファイル編集カード | プロジェクト内ファイルの 1 行変更を依頼 | `+N / -M path` の編集カードが表示 | pending | | |
| MV-022 | 同上 | #18 | View Diff | 編集カードの View Diff | IDE Diff Viewer で before/after | pending | | |
| MV-023 | 同上 | #18 | Revert | Revert をクリック | ファイルが編集前に戻る | pending | | |
| MV-024 | 同上 | #18 | ヘッドレス CLI の仕様 | Permission = Ask Every Time のまま編集依頼 | **CLI 側では即書き込み**（プラグインは事後 diff/revert） | pending | | |
| MV-025 | 同上 | #18 | チェックポイント | 編集前後でロールバックアイコン | チェックポイントから復元できる | pending | | |
| MV-029 | `fix/pr18-review-fixes` | #18 | ツールコール行の集約 | 複数ファイルを編集/複数シェルコマンドを実行する依頼を送信 | 各ツールコールにつき「実行中」行が編集/シェルカードに**置き換わる**（重複して両方残らない） | pending | | |
| MV-030 | `fix/pr18-review-fixes` | #18 | 古いRevertの拒否 | 編集カードのRevertを押す**前**に、同じファイルを別の変更（再度エージェントに編集依頼、または手動編集）で書き換える | Revertはエラーダイアログで拒否され、新しい変更は保持される（黙って上書きされない） | pending | | |

## D. F-23 / F-52（CLI フラグ）

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-026 | 同上 | #18 | Sandbox トグル | ⋯ → Sandbox: enabled を選択してファイル編集依頼 | CLI が `--sandbox enabled` で起動（ログまたは挙動で確認） | pending | | |
| MV-027 | 同上 | #18 | Worktree モード | ⋯ → Worktree: isolated を選択して編集依頼 | 変更が `~/.cursor/worktrees/` 側に隔離される（プロジェクト直書きではない） | pending | | |
| MV-028 | 同上 | #18 | MCP 一覧 | ⋯ → MCP Servers | `id` / `Status` が整列表示される。Enable/Disable が動く | pending | | |
| MV-031 | `fix/pr18-review-fixes` | #18 | Terminalプラグイン任意化 | Settings → Plugins で「Terminal」を無効化 → IDE再起動 | Cursor Agentプラグイン自体は正常にロードされる（チャット等は使える。`@terminal`のみ利用不可でよい） | pending | | |

---

## エージェントが既に検証済み（マトリクス対象外）

- `./gradlew test`（パーサー・BranchDiffBuilder 等）
- Teams プランでの CLI スパイク（stream-json 形状、force なしでも即書き込み）
- CLI `--help` に画像添付フラグなし（F-60 は CLI 非対応と判断、2026-09-04）
