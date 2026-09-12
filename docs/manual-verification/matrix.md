# 動作確認マトリクス

**更新ルール**: GUI確認が必要な変更で行を追加。GPTのComputer Useを優先し、人間が補完する。実観察した担当が `Status` / `Verified by` / `Date` と証跡リンク・対象SHAを記録。取得/認証エラーは `blocked`。単体テストやmergeはpassではない。詳細: [ループ手順](../loop-engineering/README.md)。

**UI差分計画（2026-09-05）**: [取り込み計画](../plans/cursor-agent-ui-gap-plan.md) / [#19](https://github.com/shinma06/cursor-in-android-studio/issues/19)。
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
| MV-021 | `main`（run SHA参照） | #18 | ファイル編集カード | プロジェクト内ファイルの 1 行変更を依頼 | `+N / -M path` の編集カードが表示 | blocked | GPT / CUA: r3でfixture・ビルド照合済み。メニュー後の画面取得が空になり送信前で停止。[記録](../loop-engineering/runs/2026-09-06-issue20-edit-notice.md) | 2026-09-06 |
| MV-022 | `main`（run SHA参照） | #18 | View Diff | 編集カードの View Diff | IDE Diff Viewer で before/after | pending | | |
| MV-023 | `main`（run SHA参照） | #18 | Revert | Revert をクリック | ファイルが編集前に戻る | blocked | GPT / CUA: r3でfixture・ビルド照合済み。メニュー後の画面取得が空になり送信前で停止。[記録](../loop-engineering/runs/2026-09-06-issue20-edit-notice.md) | 2026-09-06 |
| MV-024 | `main`（run SHA参照） | #18 | ヘッドレス CLI の仕様 | Permission = Ask Every Time のまま編集依頼 | **CLI 側では即書き込み**（プラグインは事後 diff/revert） | blocked | GPT / CUA: r3でfixture・ビルド照合済み。メニュー後の画面取得が空になり送信前で停止。[記録](../loop-engineering/runs/2026-09-06-issue20-edit-notice.md) | 2026-09-06 |
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

## G. #20 即時編集・事後Revertの説明

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-037 | `main`（run SHA参照） | - / #20 | 入力付近・設定の即時編集説明 | 識別済みビルドのfixtureでComposer（約350px幅と広幅）とSettings → Tools → Cursor Agentを表示。説明を読み、設定変更なしで閉じる | Ask Every Timeでも即時編集が起こり得ること、Revertが事後undoであることが両画面で省略なく読める。入力・Sendが使用可能で、説明表示だけではApplyが有効化されない | blocked | GPT / CUA: r3で説明表示を一部確認。Settings/広幅未了、CUA取得blocked。[記録](../loop-engineering/runs/2026-09-06-issue20-edit-notice.md) | 2026-09-06 |

MV-037は説明表示だけの受入。MV-024/021/023の実送信・編集・復元、および#20のWorktree復元整合性を代替しない。

## H. #19 静的外観の一致

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-038 | `main`（run SHA参照） | - / #19 | Cursorに近い会話とComposer | 識別済みビルドで短文/長文の会話を表示し、狭幅/広幅、モデル/モードの開閉・取消を確認 | 上詰めで本文の高さに追従、全幅の角丸ユーザー枠と枠なし応答、一体型Composerの入力/モード/モデル/送信が読める。選択取消で値が変わらない | blocked | GPT / CUA: ebe397cを反映、AXで初期化と入力/選択/送信を確認。最終スクリーンショット・本文・幅変更・popupは取得blocked。[記録](../loop-engineering/runs/2026-09-06-ui-parity.md) | 2026-09-06 |

## I. #27 入力エリアの操作と外観

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-039 | `main`（run SHA参照） | - / #27 #19 | 検索・Auto・モード・可変高入力・クリック範囲 | モデルを開いて検索/選択/Escape、Autoオン→オフ、Agent/Plan/Ask選択、改行なし長文と複数行を入力/削除。狭幅/広幅で余白をクリック | 名前/IDで絞込み、現在値チェック、Autoオンで一覧を畳みオフで直前の個別モデルへ戻る。Planは橙、Askは緑。入力は最大12表示行まで伸び、その後縦スクロール、削除で縮む。モデルの余白でメニューが開かない。指定placeholderが表示される | blocked | GPT / CUA: r1で検索/Auto/Plan/Ask/入力伸縮を確認。cdf86a7の反映・12行上限とthumbを確認、UI更新後のthumb消失を最終修正。最終修正は054e2baに含めて反映済み、長文再検証・余白クリック・ホイール・幅変更は未了。[記録](../loop-engineering/runs/2026-09-06-composer.md) | 2026-09-06 |

## J. #20 / #21 日本語の詳細設定

MV-037の「入力付近に説明を常時表示」は2026-09-06のユーザー指定で廃止。過去のblocked記録は保持し、
現在の配置・言語の受入をMV-040に移す。即時編集・Revertの実行QAは別途継続する。

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-040 | `main`（run SHA参照） | - / #20 #21 | 日本語の詳細設定・通常画面の簡潔化 | 入力欄→「…」→各選択肢を開きEscapeで取消、プラグイン設定/MCPを閲覧 | 入力下の文面なし。3設定の現在値・選択肢・注意・操作が日本語。未確定で設定変更なし。入力/Agent/モデル/placeholderの外観を維持。即時編集説明は詳細設定とSettings内に表示 | pass | GPT / CUA: 054e2baを反映。通常表示、3選択肢の日本語・取消、Settings（Apply無効）、MCP導線を確認。[記録](../loop-engineering/runs/2026-09-06-japanese-options.md) | 2026-09-06 |

## K. #27 UIスケール・選択ボタンの寸法

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-041 | `main`（run SHA参照） | - / #27 | 小さな文字とUI、中央の矢印、内容に沿うクリック範囲 | 同幅で入力・選択ボタンを比較し、モデル/モードpopupを開閉。ボタン右余白をクリックし、入力の伸縮を確認 | フォントと余白がコンパクト。矢印が文字の中央に揃い、内容のすぐ外でボタンが終わる。popup/取消・入力が使用可能 | blocked | GPT / CUA: 最終2fe07ae反映。縮小・中央矢印・内容幅の枠・入力伸縮を確認。余白クリック/メニュー開閉は操作ツールblocked。[記録](../loop-engineering/runs/2026-09-06-compact-ui.md) | 2026-09-06 |

## L. #27 設定値とポップアップの配置

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-042 | `main`（run SHA参照） | - / #27 #20 | 設定の全文・上方向popup・Agent背景 | チャット設定の値と項目名を確認。モデル/モードを開き、同じボタン/外側フォーカス/Escapeで閉じる。検索・Autoの高さ変更を確認 | 値が余白を使って全文表示。指定の日本語項目名。ボタンを隠さず上に展開、閉じる操作と高さ変更で位置を維持。Agentはグレー背景 | blocked | GPT / CUA: b264791反映・Agent背景を確認。設定/popupの操作取得はScreenCaptureKit -3811・noWindowsAvailableで未了。[記録](../loop-engineering/runs/2026-09-06-popup-ui.md) | 2026-09-06 |

## M. #30 正式名称

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-043 | main | - / #30 | 正式名称と更新互換性 | 既存版を更新しPlugins/Settings/ツールウィンドウ/空状態を確認 | Cursor in Android Studioと表示。既存設定とウィンドウ配置を維持 | blocked | GPT / CUA: c5abde4反映。見出し・サイドバー・空状態の新名称と選択モデル/配置維持を確認。Settings/Pluginsは操作後も対象画面を取得できず未確認。[記録](../loop-engineering/runs/2026-09-06-rename.md) | 2026-09-06 |

## N. #27 モデル系列とオプション

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| MV-044 | main | - / #27 | 同一モデルのThinking/Fast/Context/Effort | 旧ID復元→オプション表示/切替→Model検索→Auto往復→取消 | 系列は1行、対応項目だけ表示、実在するIDへ解決。他オプションを勝手に変更しない | pass | GPT / CUA: 0310729でThinking/Fast/Effort・系列検索・Auto・取消、最終1cca572で旧ID復元・モデル往復保持・元設定復元を確認。Contextは複数容量未提供で非表示、切替は単体試験。[記録](../loop-engineering/runs/2026-09-06-model-options.md) | 2026-09-06 |

## O. #24 Add to Chatと明示context

#48固定依存を含む候補を指定して実施する。詳細は[Case24](../verification/changes/issue-24.json)。既存#5の結果は再判定しない。

| ID | Branch | PR | 観点 | 手順 | 期待結果 | Status | Verified by | Date |
|----|--------|-----|------|------|----------|--------|-------------|------|
| CTX-24-SELECTION | `codex/24-explicit-context` | #24 / 依存#48 | Add to Chat/自動・明示/削除変更 | Case24の同IDをDark/Light・通常/Compactで実施 | 追加した内容と表示が対応し、隠れた古い選択を送信せず、削除/変更できる。 | pending | | |
| CTX-24-MENTIONS | `codex/24-explicit-context` | #24 / 依存#48 | 候補検索・0件/多数・IMEと種類の保証 | Case24の同IDをDark/Light・通常/Compactで実施 | 候補収集中も操作でき、種類ごとの実データ/placeholder/hintを区別する。 | pending | | |
| CTX-24-SNAPSHOT | `codex/24-explicit-context` | #24 / 依存#48 | 通常送信/予約項目のsnapshot | Case24の同IDをDark/Light・通常/Compactで実施 | 通常/予約各requestが自分のsnapshotを持ち、後から別draftのchipへ差し替わらない。 | pending | | |
| CTX-24-LIFETIME | `codex/24-explicit-context` | #24 / 依存#48 | tab/破棄・表示と回帰 | Case24の同IDをDark/Light・通常/Compactで実施 | 所有tabとViewの寿命を保ち、非表示timer/候補Futureが残らず、各表示条件で操作できる。 | pending | | |


## P. #258 入力前Skills候補（未確認）

正本: [issue-258.json](../verification/changes/issue-258.json)。全4CaseはGPT/human pending、main未反映。#24/#48依存を含む固定buildで確認し、旧Caseのpassを流用しない。

| Case | 確認対象 | GPT / 人間 |
|---|---|---|
| SKILLS-258-CONNECT | 入力前接続・settings/transport | pending / pending |
| SKILLS-258-PICK | 候補検索・選択/取消/解除・IME | pending / pending |
| SKILLS-258-SEND | 単発呼出しと予約snapshot | pending / pending |
| SKILLS-258-LIFETIME | 全置換・不正/遅着・複数tab/破棄 | pending / pending |
