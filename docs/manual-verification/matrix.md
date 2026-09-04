# 動作確認マトリクス

**更新ルール**: エージェントが人間確認が必要な変更を入れたら行を追加。人間が確認したら `Status` / `Verified by` / `Date` を更新。

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

## D. F-23 / F-52（CLI フラグ）

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-026 | 同上 | #18 | Sandbox トグル | ⋯ → Sandbox: enabled を選択してファイル編集依頼 | CLI が `--sandbox enabled` で起動（ログまたは挙動で確認） | pending | | |
| MV-027 | 同上 | #18 | Worktree モード | ⋯ → Worktree: isolated を選択して編集依頼 | 変更が `~/.cursor/worktrees/` 側に隔離される（プロジェクト直書きではない） | pending | | |

---

## エージェントが既に検証済み（マトリクス対象外）

- `./gradlew test`（パーサー・BranchDiffBuilder 等）
- Teams プランでの CLI スパイク（stream-json 形状、force なしでも即書き込み）
- CLI `--help` に画像添付フラグなし（F-60 は CLI 非対応と判断、2026-09-04）
