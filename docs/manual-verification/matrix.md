# 動作確認マトリクス

**更新ルール**: エージェントが人間確認が必要な変更を入れたら行を追加。人間が確認したら `Status` / `Verified by` / `Date` を更新。

**推奨ブランチ（2026-09-04 時点）**: `feature/m4-m5-tool-diff-display` — PR [#16](https://github.com/shinma-postas/cursor-agent-plugin/pull/16) と [#17](https://github.com/shinma-postas/cursor-agent-plugin/pull/17) の両方を含む。

---

## A. 基本チャット（回帰）

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-001 | `feature/m4-m5-tool-diff-display` | #17 | プロンプト送信・ストリーミング | Cursor Agent ツールウィンドウを開き、短文を送信 | ユーザーバブル → 応答がストリーム表示される | pending | | |
| MV-002 | 同上 | #17 | Stop | 長めのプロンプト実行中に Stop | 処理が止まり入力欄が再有効化 | pending | | |
| MV-003 | 同上 | #17 | 新規チャット | New Chat | タイムラインがクリアされる | pending | | |

## B. PR #16 — 通知・メンション・設定

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-010 | `feature/m4-m5-tool-diff-display` | #16 | Enter / Shift+Enter | Enter で送信、Shift+Enter で改行 | 意図どおり（改行が送信されない） | pending | | |
| MV-011 | 同上 | #16 | `@` ポップアップ | 入力欄で `@` | 候補表示・選択でトークン挿入・位置/フォーカスが自然 | pending | | |
| MV-012 | 同上 | #16 | `@branch` | `@branch` を含むプロンプトを送信 | エラーなく送信（ブランチ diff コンテキスト注入） | pending | | |
| MV-013 | 同上 | #16 | `@terminal` | **Terminal タブを開いた状態**で `@terminal` 送信 | ターミナル出力が注入される | pending | | |
| MV-014 | 同上 | #16 | `@terminal`（タブなし） | Terminal 未オープンで `@terminal` 送信 | プレースホルダーメッセージでエラーにならない | pending | | |
| MV-015 | 同上 | #16 | デスクトップ通知 | ターン完了・ツール開始（設定 ON） | OS 通知。クリックで Cursor Agent が開く | pending | | |
| MV-016 | 同上 | #16 | 設定ページ | Settings → Tools → Cursor Agent | CLI パス・通知 ON/OFF を保存できる | pending | | |
| MV-017 | 同上 | #16 | ツールウィンドウ close | 実行中に Cursor Agent ペインを閉じる | CLI プロセスが止まる（ゾンビ化しない） | pending | | |

## C. PR #17 — ツール表示・diff・revert

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-020 | `feature/m4-m5-tool-diff-display` | #17 | シェル出力カード | 「`echo HELLO_TEST` を実行して」と依頼 | タイムラインに stdout 付きシェルカード | pending | | |
| MV-021 | 同上 | #17 | ファイル編集カード | プロジェクト内ファイルの 1 行変更を依頼 | `+N / -M path` の編集カードが表示 | pending | | |
| MV-022 | 同上 | #17 | View Diff | 編集カードの View Diff | IDE Diff Viewer で before/after | pending | | |
| MV-023 | 同上 | #17 | Revert | Revert をクリック | ファイルが編集前に戻る | pending | | |
| MV-024 | 同上 | #17 | ヘッドレス CLI の仕様 | Permission = Ask Every Time のまま編集依頼 | **CLI 側では即書き込み**（プラグインは事後 diff/revert）。Apply 前ブロックではない | pending | | |
| MV-025 | 同上 | #17 | チェックポイント | 編集前後でロールバックアイコン | チェックポイントから復元できる | pending | | |

---

## エージェントが既に検証済み（マトリクス対象外）

以下は `./gradlew test` または live CLI スパイクで確認済みのため、人間の runIde 確認は上表の UI 観点のみ必要。

- stream-json パーサー（`ToolCallPayloadParserTest`, `StreamJsonParserTest`）
- `@branch` の git ロジック（`BranchDiffBuilderTest`）
- Teams プランでの CLI プロンプト・file edit・shell tool_call イベント形状
