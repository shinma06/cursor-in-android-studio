# 動作確認マトリクス

**更新ルール**: GUI確認が必要な変更で行を追加。GPTのComputer Useを優先し、人間が補完する。実観察した担当が `Status` / `Verified by` / `Date` と証跡リンク・対象SHAを記録。取得/認証エラーは `blocked`。単体テストやmergeはpassではない。詳細: [ループ手順](../loop-engineering/README.md)。

**UI差分計画（2026-09-05）**: [取り込み計画](../plans/cursor-agent-ui-gap-plan.md) / [#19](https://github.com/shinma06/cursor-agent-plugin/issues/19)。
[Android Studio実機追補](../research/android-studio-ui-followup-2026-09-05.md)で設定・表示を限定確認した（E節）。以下の既存QAは未実施のまま、新機能用の行は各 UX Issue の実装時に実際の Branch・手順を確定して追加する。
Cursor の閲覧調査を、このプラグインの `pass` や実行・送信を伴う検証の実施許可と扱わない。

**現在の検証対象**: `main`の対象SHAを各runで固定する。A〜Dは#18までの実装に対する未実施QA。旧作業ブランチや「同上」を現在のcheckout指示に使わない。

---

## A. 基本チャット（回帰）

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-001 | `main`（run SHA参照） | #18 | プロンプト送信・ストリーミング | Cursor Agent ツールウィンドウを開き、短文を送信 | ユーザーバブル → 応答がストリーム表示される | pending | | |
| MV-002 | `main`（run SHA参照） | #18 | Stop | 長めのプロンプト実行中に Stop | 処理が止まり入力欄が再有効化 | pending | | |
| MV-003 | `main`（run SHA参照） | #18 | 新規チャット | New Chat | タイムラインがクリアされる | pending | | |

## B. 通知・メンション・設定

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-010 | `main`（run SHA参照） | #18 | Enter / Shift+Enter | Enter で送信、Shift+Enter で改行 | 意図どおり（改行が送信されない） | pending | | |
| MV-011 | `main`（run SHA参照） | #18 | `@` ポップアップ | 入力欄で `@` | 候補表示・選択でトークン挿入・位置/フォーカスが自然 | pending | | |
| MV-012 | `main`（run SHA参照） | #18 | `@branch` | `@branch` を含むプロンプトを送信 | エラーなく送信（ブランチ diff コンテキスト注入） | pending | | |
| MV-013 | `main`（run SHA参照） | #18 | `@terminal` | **Terminal タブを開いた状態**で `@terminal` 送信 | ターミナル出力が注入される | pending | | |
| MV-014 | `main`（run SHA参照） | #18 | `@terminal`（タブなし） | Terminal 未オープンで `@terminal` 送信 | プレースホルダーメッセージでエラーにならない | pending | | |
| MV-015 | `main`（run SHA参照） | #18 | デスクトップ通知 | ターン完了・ツール開始（設定 ON） | OS 通知。クリックで Cursor Agent が開く | pending | | |
| MV-016 | `main`（run SHA参照） | #18 | 設定ページ | Settings → Tools → Cursor Agent | CLI パス・通知 ON/OFF を保存できる | pending | | |
| MV-017 | `main`（run SHA参照） | #18 | ツールウィンドウ close | 実行中に Cursor Agent ペインを閉じる | CLI プロセスが止まる（ゾンビ化しない） | pending | | |

## C. ツール表示・diff・revert

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-020 | `main`（run SHA参照） | #18 | シェル出力カード | 「`echo HELLO_TEST` を実行して」と依頼 | タイムラインに stdout 付きシェルカード | pending | | |
| MV-021 | `main`（run SHA参照） | #18 | ファイル編集カード | プロジェクト内ファイルの 1 行変更を依頼 | `+N / -M path` の編集カードが表示 | pending | | |
| MV-022 | `main`（run SHA参照） | #18 | View Diff | 編集カードの View Diff | IDE Diff Viewer で before/after | pending | | |
| MV-023 | `main`（run SHA参照） | #18 | Revert | Revert をクリック | ファイルが編集前に戻る | pending | | |
| MV-024 | `main`（run SHA参照） | #18 | ヘッドレス CLI の仕様 | Permission = Ask Every Time のまま編集依頼 | **CLI 側では即書き込み**（プラグインは事後 diff/revert） | pending | | |
| MV-025 | `main`（run SHA参照） | #18 | チェックポイント | 編集前後でロールバックアイコン | チェックポイントから復元できる | pending | | |
| MV-029 | `main`（run SHA参照） | #18 | ツールコール行の集約 | 複数ファイルを編集/複数シェルコマンドを実行する依頼を送信 | 各ツールコールにつき「実行中」行が編集/シェルカードに**置き換わる**（重複して両方残らない） | pending | | |
| MV-030 | `main`（run SHA参照） | #18 | 古いRevertの拒否 | 編集カードのRevertを押す**前**に、同じファイルを別の変更（再度エージェントに編集依頼、または手動編集）で書き換える | Revertはエラーダイアログで拒否され、新しい変更は保持される（黙って上書きされない） | pending | | |

## D. F-23 / F-52（CLI フラグ）

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-026 | `main`（run SHA参照） | #18 | Sandbox トグル | ⋯ → Sandbox: enabled を選択してファイル編集依頼 | CLI が `--sandbox enabled` で起動（ログまたは挙動で確認） | pending | | |
| MV-027 | `main`（run SHA参照） | #18 | Worktree モード | ⋯ → Worktree: isolated を選択して編集依頼 | 変更が `~/.cursor/worktrees/` 側に隔離される（プロジェクト直書きではない） | pending | | |
| MV-028 | `main`（run SHA参照） | #18 | MCP 一覧 | ⋯ → MCP Servers | `id` / `Status` が整列表示される。Enable/Disable が動く | pending | | |
| MV-031 | `main`（run SHA参照） | #18 | Terminalプラグイン任意化 | Settings → Plugins で「Terminal」を無効化 → IDE再起動 | Cursor Agentプラグイン自体は正常にロードされる（チャット等は使える。`@terminal`のみ利用不可でよい） | pending | | |

---

## E. 2026-09-05 実機閲覧の追補

対象はインストール済み `0.1.0-SNAPSHOT`。コミット/ブランチとの対応は未特定。CUAによる実画面/AX確認であり、A〜Dの送信・設定保存QAとは別。幅は画像からの概算で、実装時に対象版と実測幅を更新する。

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-032 | 未特定（installed SNAPSHOT） | - / #27 | モデル名のインライン可読性 | 約350〜400px幅で閉じたモデル選択部品を見る | 完全名をインラインで読める | fail | Codex / CUA: Autoの名前が省略。代替の完全名導線は未確認 | 2026-09-05 |
| MV-033 | 未特定（installed SNAPSHOT） | - / #21 | 設定ページの閲覧到達 | Cmd+, → Tools → Cursor Agent、変更せずEscape | CLIパス・通知2項目が表示され、Apply無効のまま閉じる | pass | Codex / CUA（保存動作は対象外） | 2026-09-05 |
| MV-034 | 未特定（installed SNAPSHOT） | - / #28 | インストール済みビルドの識別 | Plugins → Installed / 診断画面の版を照合 | インストール済みバイナリのSHA等を特定できる | pending | Codex / CUA: 0.1.0-SNAPSHOTのみ確認 | 2026-09-05 |
| MV-035 | 未特定（installed SNAPSHOT） | - / #25 #27 | メニュー展開・取消 | モデル/モード/⋯を開き、変更せずEscape | 候補と現在値が読め、取消で値が変わらない | pending | Codex / CUA: 操作ツールのウィンドウ取得失敗で未確認 | 2026-09-05 |

MV-032はこの幅での表示結果。ツールチップ等で補完する仕様を採る場合は、#27の受入条件に従って完全名の確認手段も追加検証する。操作ツールの取得失敗は製品のfailにしない。

---

## エージェントが既に検証済み（マトリクス対象外）

- `./gradlew test`（パーサー・BranchDiffBuilder 等）
- Teams プランでの CLI スパイク（stream-json 形状、force なしでも即書き込み）
- CLI `--help` に画像添付フラグなし（F-60 は CLI 非対応と判断、2026-09-04）

## F. ループ基盤の試運転（#29）

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-036 | `main`（run SHA参照） | - / #29 | GUIループの接続・fixture切替 | prepareのfixtureを両IDEで開き、LOOP_FIXTURE.txtのrunと面を確認 | 別のソースツリーへの誤送信を防ぎ、同じ初期状態でGUI課題へ進める | blocked | GPT / CUA: 両アプリの画面取得、CursorのOpen操作を確認。fixture切替結果を確認できず送信未実施。[試運転記録](../loop-engineering/runs/2026-09-06-bootstrap.md) | 2026-09-06 |

MV-036は接続環境の結果で、製品のfailではない。#29の基盤実装完了と、実際のGUIサイクル完走は区別する。
