# Cursor in Android Studio 要件定義書

- **文書バージョン**: v0.1(ドラフト)
- **作成日**: 2026-09-03
- **対象読者**: 実装者(自分)
- **Assumed環境**: Kotlin 2.x / Android Studio (最新安定版) / IntelliJ Platform 2023.1+ 系API / cursor-agent CLI 2026年7月時点仕様
- **現行実装**: 方式B(ネイティブUI方式) — `agent -p --output-format stream-json` をサブプロセス実行し、独自Swing/JBUI製チャットパネルに描画する

**最上位方針（2026-09-09 / #134）**: [Project Mission](project-mission.md)と[ACP First](architecture/cursor-integration.md)を優先する。方式Bは現在の実装記録であり、新規連携はACPを第一級に扱い、IDE API / MCP / 補助CLIを組み合わせる。以下の機能別実装状況や過去検証は、この方針変更だけで実装済み・合格へ変更しない。

**2026-09-08 最新比較（#114）**: [機能・UI/UX・main/develop・非TTY CLIマトリクス](research/cursor-agent-capability-matrix-2026-09-08.md)を現在の調査入口とする。公式仕様、installed help、過去live実測、未検証の設計案を分ける。画像パス参照・headless Skills/subagentsには公開経路があるが、今回の調査は新機能のlive合格ではない。当時の方式B中心の設計方針はACP Firstへ更新する。既存CLI経路とACP各能力の実装・互換性検証は別に追跡する。実装済みとmain反映/GUI合格を混同しない。

**2026-09-05 UI比較の追補（当時の計画）**: [閲覧調査46項目](research/cursor-agent-ui-survey-2026-09-05.md)と
[差分取り込み計画](plans/cursor-agent-ui-gap-plan.md)を追加。
UI-xx は観測ID、UX-xx は取り込み単位であり、既存 F-xx を置換しない。
優先順・依存関係・受入条件は計画、実際の進捗は [親 Issue #19](https://github.com/shinma06/cursor-in-android-studio/issues/19) と子 Issue を参照する。
Cursor のメニュー存在と headless CLI の対応は別の証拠であり、今回の閲覧で CLI 動作や既存の手動QAを検証済みに変更しない。

**2026-09-06 開発運用基盤**: [GPT主導GUIループ](loop-engineering/README.md) / [人間向け手順](loop-engineering/human-runbook.md)を整備。GPTがComputer UseでCursor IDEと本プラグインを操作し、Claude Proが独立レビューする。これは開発・検証の運用であり、F-xx機能追加や既存QA合格ではない。

**2026-09-06 静的外観改修（#19）**: Cursor実画面に合わせ、会話の自然な高さ/全幅ユーザー枠/枠なし応答、一体型Composer、モード/モデルの小さな選択ボタンを実装。権限やCLI挙動は維持。GUI受入と経過は[MV-038の記録](loop-engineering/runs/2026-09-06-ui-parity.md)を参照。

**2026-09-07 統合/検証方針（#83）**: 通常実装はdevelopへ統合。必要テスト・独立レビュー・[Caseマトリクス](verification/README.md)が必須で、GUIの未実施/環境blocked/製品failは正確に残す。mainは固定候補全体の必要動作確認pass後のみ。人間は区切りでまとめて確認し、失敗は専用修正Issue/branch/PRで扱う。develop統合で既存F/MV受入を完了にしない。

---

## 1. 背景・目的

### 1.1 背景
Androidアプリ開発チームは、 Android Studio と、AI支援コーディングツールとして Cursor を併用している。
しかし現状はAI支援を使用するタイミングは Cursor 、その他の作業ではよりネイティブな Android Studio を使うという運用となっており、開発ツールを行き来するという手間が発生している。

### 1.2 目的
Android Studio上で **Cursor IDE内Agent panelの機能・操作フロー・フィードバック・IDE統合を同等以上に実現する**。独立Agents Windowの再現やピクセル単位のコピーではなく、Android Studio固有の能力でAndroid開発体験をさらに深める。[最上位ミッションと必須の競合比較](project-mission.md)を適用する。

Cursor Agentとの高度な双方向連携はACP Firstとし、IDE API / MCP / CLIを適材適所で併用する。現行Swing/JBUIと方式Bからの具体的な接続・移行は、[統合設計方針](architecture/cursor-integration.md)に従い機能ごとに検証する。

### UIの言語方針（2026-09-06 ユーザー指定）
日本語での利用を前提とし、設定・説明・注意・エラーなど意味の理解が重要な情報は日本語にする。
常時見える短い `Agent` / `Plan` / `Ask` / `Auto`、モデル名、`MCP`、アイコン、ユーザー指定の
placeholderはCursorの外観を尊重して維持する。一律の全日本語化は行わない。
詳細はCLAUDE.mdの「UIの言語と見た目の方針」を参照。詳細設定や編集の説明は「…」内へまとめ、
入力欄の下へ常時表示しない。CLI引数・保存値・生ログは翻訳しない。

### 1.3 非目的(スコープ外)
- Cursor Agent本体・内部harness・モデル制御・検索・推論等を不必要に独自再実装しない。公式interfaceを利用するIDE clientを構築する。
- Android Studio以外のIDE(VS Code等)への対応は行わない
- 独立Agents Window、専用UI/ブラウザ/Design Mode/複数Agent管理、独立Agent-first workspaceは、IDE内panelの実現に必要でない限り再現しない。クラウド専用の独立作業環境も対象に自動追加しない。

---

## 2. 用語定義

| 用語 | 定義 |
|---|---|
| Agent | `cursor-agent` CLIが提供する自律的コーディングエージェント機能 |
| ツールウィンドウ | IntelliJ Platformの画面端に配置されるパネル(Terminal, Logcatと同種) |
| チェックポイント | Agentによる編集前のコード状態スナップショット。ネイティブCursorではロールバック可能 |
| `@メンション` | チャット入力中に`@`でファイル/フォルダ/コンテキストを注入する記法 |
| stream-json | `cursor-agent -p --output-format stream-json`が出力するイベント単位のJSON Lines形式 |
| PTY | 疑似端末。本プラグインでは方式Bのため主として使用しないが、shell実行結果の表示に関連 |

---

## 3. 対象ユーザーと利用シーン

- **対象ユーザー**: 本人(Androidアプリ開発チーム所属、Kotlin/Android Studioでの日常開発)
- **主な利用シーン**:
  1. 実装中のファイルについて `@ファイル名` や、選択範囲 ` Add to Chat ` でコンテキストを渡し、修正案を依頼する
  2. Agentの変更をIDE内でレビューする。現方式Bは即時編集後のDiff/Revertであり、事前Apply/Rejectではない。
  3. 変更が意図しない結果だった場合にチェックポイントへロールバックする
  4. タスクごとにユーザがモデル(Sonnet/Opus等)を切り替える
  5. 過去のセッションを再開して続きの作業を行う

---

## 4. 前提条件・制約

### 4.1 前提条件
- `cursor-agent` CLIがローカル環境にインストール・認証済みであること(`agent login` または `CURSOR_API_KEY`)
- Android Studioのバージョンに対応するIntelliJ Platform Plugin SDKでビルドすること
- `cursor-agent`の`--output-format stream-json`のJSONスキーマは変更される可能性がある `[仮説]` ため、パーサーは防御的に実装する

### 4.2 制約
- 復元・Browser・音声入力は、現方式BでIDEと同じ入力/出力・UI契約を利用できるかを個別検証する。CLI interactiveの`/rewind`やBrowser subagent等の公開能力と、pluginの復元/画像表示/操作UIを分け、CLI全体に機能が存在しないとは断定しない（§6 機能要件・最新比較参照）。
- 現プラグインの`@Docs`/`@Web`はMCP利用のヒント文字列を注入する実装。CLI自身のWeb能力やBrowser subagentとは分け、MCP追加だけが唯一の経路とは扱わない（[最新比較](research/cursor-agent-capability-matrix-2026-09-08.md)）。
- 画像は[公式headless資料](https://cursor.com/docs/cli/headless)にprompt内のファイルパスを読む経路がある。installed helpの`--image`不在だけで非対応と断定しない。#10で識別画像・空白/日本語path・resume/Worktreeのlive検証を行ってからUI受入を決める。

---

## 5. 全体アーキテクチャ

目標の基本経路は `User → Android Studio → Agent Panel Plugin → Plugin Orchestration Layer → Cursor ACP Agent`。IDE APIs / Android tooling / MCP / CLIを必要に応じて併用し、調整責務と各integration・UIを分離する。具体的な優先順位、構造化イベントの対応対象、CLI併用条件は[ACP First](architecture/cursor-integration.md)を参照する。

以下の図は **現行方式Bの実装** であり、目標をCLI printに限定しない。

```
┌─────────────────────────────────────────────┐
│ Android Studio (IntelliJ Platform)            │
│                                                │
│  ┌──────────────────────────────────────┐    │
│  │ CursorAgentToolWindow (Swing/JBUI)    │    │
│  │  - ChatPanel (入力欄, @メンション補完) │    │
│  │  - MessageListPanel (会話履歴描画)     │    │
│  │  - DiffPreviewPanel (IDE Diff Viewer)  │    │
│  │  - CheckpointPanel (ロールバックUI)    │    │
│  │  - ModelSelector / SessionSelector     │    │
│  └───────────────┬──────────────────────┘    │
│                  │                            │
│  ┌───────────────▼──────────────────────┐    │
│  │ AgentProcessService                   │    │
│  │  - GeneralCommandLine起動/管理        │    │
│  │  - stream-json パーサー               │    │
│  │  - 環境変数/認証情報の引き継ぎ         │    │
│  │  - チェックポイント管理(Git連携)      │    │
│  └───────────────┬──────────────────────┘    │
└──────────────────┼────────────────────────────┘
                    │ stdin/stdout (subprocess)
                    ▼
          ┌─────────────────────┐
          │  cursor-agent CLI    │
          │  -p --output-format  │
          │     stream-json       │
          └─────────────────────┘
```

---

## 6. 機能要件

優先度は **MVP(必須) / P2(次フェーズ) / P3(将来検討)** の3段階。以下は既存機能の実現方式と状態の記録。新規・拡張設計ではACP標準/拡張を先に確認し、IDE API / MCP / CLIを選ぶ根拠を記録する。

### 6.1 チャット基本機能

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-01 | テキストプロンプト送信 | MVP | `agent -p --output-format stream-json "<prompt>"` |
| F-02 | ストリーミング応答表示(トークン単位) | MVP | `--stream-partial-output`イベントを逐次パースしUI更新 |
| F-03 | 会話履歴の保持・スクロール表示 | MVP(部分実装) | mainは会話viewと履歴metadata。develop #65はタブ別の本文/draft/caret/scrollをメモリ内保持。再起動後の本文永続化は #44、検索/exportは #45で未実装 |
| F-04 | セッション再開(前回の続きから) | MVP | `--resume [chatId]` |
| F-05 | 新規チャット開始 | MVP | セッションID未指定で新規起動 |
| F-06 | コンテキスト圧縮 | P2 | `/summarize`をプロンプト経由で送信 |

### 6.2 コンテキスト注入(`@メンション`)

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-10 | `@ファイル名`補完UI | MVP | Android Studioのプロジェクトツリー/開いているファイル一覧からJList/JPopupMenuで候補表示 → 選択時にプロンプト文字列へ`@path`を埋め込み |
| F-11 | `@フォルダ`指定 | P2 | 同上、ディレクトリ選択対応 |
| F-12 | `@Git diff`(未コミット差分) | P2 | プラグイン側で`git diff`を実行し結果をプロンプトに埋め込み(CLI自動対応なし) |
| F-13 | `@Terminals`(ターミナル出力) | P3(**実装済み 2026-09**) | Reworked Terminal API経由(`TerminalToolWindowTabsManager`/`TerminalView.outputModels.regular`)。開いているTerminalタブの末尾8KBを`@terminal`送信時に埋め込み。タブ未オープン時はプレースホルダー |
| F-14 | `@Docs` / `@Web` | P3(部分実装) | 現プラグインはMCP利用hintの注入のみ。CLIのWeb検索/取得能力・設定と独自Docs管理UIは別途 #25で実証し、MCP必須や独自検索実装済みとは表示しない |
| F-15 | 現在開いているファイル/選択範囲の自動コンテキスト化 | MVP | エディタの`FileEditorManager`/`SelectionModel`から取得し、送信時に自動付与(ネイティブCursorの「Active file and selection」相当) |
| F-16 | `@Branch`(現在のブランチ vs mainの差分) | P2 `[2026-09追加]`(**実装済み 2026-09**) | プラグイン側で`git diff <base>...HEAD`相当を実行し埋め込み。baseは`origin/HEAD`→`main`→`master`の順で解決 |
| F-17 | `@Chats`(別会話の内容参照) | P3(未実装) | `ls`/`resume` pickerは過去の非TTY実測で失敗。方式Bの機械可読transcript取得契約は未確認。ACPのproject限定listは空応答を取得したが本文/実title取得ではない（#115/#66）。公開APIが将来も存在しないとは断定しない。#44のplugin本文保存を使う案は別受入 |
| — | `@codebase` / `@definitions`(Cursor独自のセマンティック検索・シンボルインデックス) | **独自実装は非目的** `[2026-09追加]` | Cursor社の独自インデックスバックエンドに依存しており、本プロジェクトは独自インデックス実装を非目的とする。CLI側の検索能力と同名mention UIの同等性は未検証であり、機能不存在とは断定しない |

> `@codebase`/`@definitions`はネイティブCursor Agentパネルの実在機能だが、独自index再現は§1.3の非目的とする。同等のコードベース理解は目標に含む。ACP/CLIの検索能力とIDE APIによる構造化情報の補完を #25/#115で確認し、独自index再現と混同しない。

### 6.3 モード・モデル制御

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-20 | Ask / Agent / Plan モード切替 | MVP(実装済み) | `--mode=ask` / 通常 / `--mode=plan` |
| F-21 | モデル選択UI(ドロップダウン) | MVP(実装済み) | `--model <name>`、選択肢は`agent --list-models`で動的取得 |
| F-22 | 操作確認モード | MVP(実装済み) | `PermissionMode`の3択（追加flagなし / `--auto-review` / `--force`）とsandboxを別設定。日本語の現在値・説明を提供。Cursor IDEのRun Mode分類と同義扱いしない。既存headless即時編集実測を維持し、事前承認を保証しない |
| F-23 | サンドボックス実行モード | P2 `[2026-09追加]`(**基本実装済み 2026-09**) | `--sandbox enabled\|disabled`をComposerの⋯メニューから選択(`SandboxMode`)。`--allow-paths`等の細粒度フラグは未実装 |
| F-24 | Auto-review | P2(基本実装済み) | 操作確認3択の一つとして`--auto-review`を付与。分類結果/質問への双方向応答UIは現方式Bに未実装。公開ACP経路の比較は #25配下の別調査 |

### 6.4 実行結果の可視化・適用

`[2026-09-06 / #20部分実装・ユーザー指定で配置改訂]` 入力欄の下の常時説明は削除し、「…」内のチャット設定とSettingsに、標準設定でも即時編集が起こり得ることとRevertが事後undoであることを日本語で表示する。権限設定値・CLI引数は変更していない。識別済みビルドでのGUI受入は未完了。Worktree復元整合性など#20の残条件は引き続き未完了。

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-30 | 差分プレビュー(IDE純正Diff Viewerで表示) | MVP(**実装済み 2026-09**) | `ToolCallPayloadParser`が完了した`editToolCall`イベントから`FileEditDetails`(before/after/diff)を抽出、`ui/timeline/FileEditCard.kt`の View Diff から`DiffManager`/`DiffContentFactory`で表示 |
| F-31 | Apply / Reject ボタン | MVP(**実装済み 2026-09、事後Revertモデルに変更**) | Teams プランでの実CLI検証により、ヘッドレスモードではforce有無に関わらずCLIが即座にファイルへ書き込むことが確定(分岐B)。現方式Bでは書込み前の介入契約を確認できないため、`FileEditCard`のRevertボタン(`DiffViewerHelper.revertFileContent`)による事後取り消しとして実装。現在のファイル内容がそのeditの`afterFullFileContent`と一致する場合のみ復元を実行し、それ以降に変更されていれば拒否する |
| F-32 | Shell実行結果の表示 | MVP(**実装済み 2026-09**) | stream-jsonの`tool_call`(`started`/`completed`)イベントを`ToolCallPayloadParser`で解析、`ui/timeline/ToolCallBubble.kt`でコンソール風に整形表示(stdout/stderr/interleavedOutput) |
| F-33 | エラーと停止の表示 | MVP(基本実装済み) | timelineのエラーとIDE通知を提供。develop #46は意図停止を「停止しました」とし異常137を区別（QA #104）。経過時間・詳細折畳み・一般通知抑制は #98 |

> `[2026-09追加]` ネイティブCursor CLIには`/changes`(Ctrl+R)という、そのセッションでの全編集を集約した統合レビューUIが存在する模様(CLI changelogで言及)。非対話モードでの相当コマンドの有無は未確認。F-30/F-31の設計を確定させるM0検証と合わせて調査し、単純なdiffカードの羅列ではなく統合ビューにすべきか再検討する。

### 6.5 安全策(チェックポイント/ロールバック) ★重要テーマ関連

現方式Bは**plugin側Gitスナップショット**を利用する。CLIには公開interactive `/rewind` があるが、非TTYで同じ復元を要求する契約は未確認。CLI全体に復元機能が存在しないという断定はしない。

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-40 | プロンプト送信前の自動スナップショット | MVP(実装済み) | `git stash create`(非破壊、オブジェクト生成のみ)でワーキングツリー全体を退避 |
| F-41 | チェックポイント一覧・タイムライン表示 | MVP(部分実装) | 各ユーザーメッセージにロールバックアイコンとして表示済み。専用タイムラインパネルとしての一覧表示は未実装 |
| F-42 | 任意チェックポイントへのワンクリックロールバック | MVP(実装済み) | 該当スナップショットへ`git checkout <sha> -- .`+スナップショット後に作成された未追跡ファイルの削除で復元 |
| F-43 | チェックポイントの有効期限管理 | P2(実装済み) | デフォルト15日で自動失効・クリーンアップ実装済み(`CheckpointService.pruneExpired`) |
| F-44 | Gitとの併用時の競合回避 | MVP(実装済み・テスト済み) | `git stash create`はstash一覧/コミット履歴を一切変更しないため、正規のGit運用と衝突しない設計。`GitSnapshotStoreTest`で検証済み |

> `[2026-09追加]` 参考: ネイティブCursor自身のチェックポイントUI(Restoreボタン、diffナビゲーションバー等)にも2026年時点でCursorコミュニティフォーラムに複数の不具合報告(表示消失等のリグレッション)がある。ネイティブUXの再現度を追い求めすぎず、「壊れずに機能する」ことを優先する。

### 6.6 セッション・履歴管理

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-50 | 過去チャット一覧 | P2(部分実装) | pluginがchatId/冒頭prompt/更新時刻を保存し、選択後の次turnを`--resume`する。developでは同IDの既存タブを再選択。本文は未保存でその旨を表示し、再起動後本文復元は #44。CLIの履歴存在と非TTY取得API、plugin本文復元を区別する |
| F-51 | チャットのプロジェクト単位分離 | MVP(実装済み) | `--workspace <path>`と起動cwdをプロジェクトルートへ揃える。mainは`AgentProcessService`、developは`TurnWorkspace.arguments()`がworkspace引数を生成する |
| F-52 | Worktree分離実行 | P3(基本実装済み) | 上部チャット設定から`-w`を選択。`--worktree-base`/`--skip-worktree-setup`のUIは未実装。develop #39は実rootを追跡できないISOLATEDの復元を安全拒否（QA #102）。分離rootで復元可能になったとは扱わない |

### 6.7 マルチモーダル・拡張入力

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-60 | 画像添付 | P3(公開経路あり・live未検証) | [headless資料](https://cursor.com/docs/cli/headless)はprompt内の画像path読取を説明。CLI `2026.09.02-c22c1a3`のhelpに`--image`はないが非対応の証明にはならない。#10で非TTY実証後、添付/paste/D&D/preview/remove/失敗UIを設計する |
| F-61 | 音声入力 | P3(未検証) | 専用録音/送信UIは未実装。#99でOS標準音声入力がEditorTextFieldへ文字入力できるか検証する。音声ファイル解析とdictationを別機能にする |
| F-62 | ブラウザ視覚検証 | P3(未実装・調査) | [Browser](https://cursor.com/docs/agent/tools/browser)と[Subagents](https://cursor.com/docs/subagents)の公開経路を調査する。MCP利用も候補だが汎用tool summaryだけでは画像描画/視覚検証は成立しない。方式Bでのevent・表示・操作契約は #25で確認 |

### 6.8 MCP連携

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-70 | MCPサーバー一覧表示 | P2(実装済み) | `agent mcp list`の結果をUIに反映。`id: status`行を`McpListParser`でパースし整列表示(2026-09-04検証) |
| F-71 | MCPサーバーの有効/無効切替 | P3(実装済み) | `.cursor/mcp.json`の直接編集ではなく、`agent mcp enable <id>` / `agent mcp disable <id>`サブコマンドを利用(要件定義時点の想定より簡単に実現できた) |

### 6.9 将来調査事項(2026-09の網羅的リサーチで新たに判明、優先度未確定)

ネイティブCursor Agentパネル/CLIの調査で見つかった、現時点では実現方式が固まっていない項目。実装に着手する前に個別の技術検証(spike)が必要。

| 項目 | 概要 | CLIでの再現性 |
|---|---|---|
| Subagents / Multitask | [Subagents](https://cursor.com/docs/subagents)とCLI changelogにheadless対応の公開記載。IDEの複数モデル実行と子agentを区別する | 公開対応。方式Bの親子event/終了/停止/usageのlive fixtureとネイティブ表示は未実装 |
| Skills / Custom Modes | [Skills](https://cursor.com/docs/skills)に1turn呼出しとsession持続modeの説明 | headless `/skill-name`は公開対応。発見/補完UIは未実装。持続modeのprint呼出し間の契約は別途検証し、`--mode`値を推測しない |
| デスクトップ通知 | ターン完了時・承認待ち発生時のOSネイティブ通知 | **実装済み 2026-09** — IntelliJ `Notification` API。ターン完了/ツール呼び出し開始時に通知(設定でOFF可) |
| `permissions.json` | チーム管理者向けのターミナル/MCP許可リスト宣言ファイル。IDEとCLIで設定共有 | F-22/F-23再設計時の参考として調査対象。個人利用が主眼の本プラグインでは優先度低 |
| CLI Hooks(session start/end, stop, pre-compaction等) | チーム管理向け自動化フック | 独立した管理基盤の再現は対象外。IDE内panelの体験に必要な範囲があるかを最上位ミッションに従って判断する |
| クラウドエージェント / バックグラウンドPR自動生成 | 常時稼働のクラウドエージェント、Slack連携等 | 独立したCloud/Agents Window作業環境の再現は対象外。IDE内panelに必要かを§1.3で判断し、現CLI実装だけを根拠に将来の可否を断定しない |

---

## 7. 非機能要件

| カテゴリ | 要件 |
|---|---|
| パフォーマンス | ストリーミング応答の表示遅延は体感で許容できる範囲(1トークンあたりの描画遅延がUIスレッドをブロックしないこと。EDT上での重い処理を避け、`invokeLater`でバッチ更新する) |
| 安定性 | サブプロセスのクラッシュ・ハングがAndroid Studio本体をフリーズさせないこと(別スレッド/`ProcessHandler`での非同期管理必須) |
| リソース管理 | ツールウィンドウを閉じた際、または`Disposable#dispose()`時にサブプロセスを確実にkillすること(ゾンビプロセス防止) |
| セキュリティ | `--force`のデフォルトOFF。APIキー等の認証情報をプラグインのログに出力しないこと |
| 可搬性 | Windows/macOS/Linuxの各OSでCLIパスの解決方法(PATH検索、環境変数)に差異があるため、OS別のプロセス起動処理を用意する |
| 保守性 | stream-jsonのパーサーはバージョン非互換に備え、未知のJSONフィールドを無視するフォールト・トレラントな実装とする |

---

## 8. UI/UX要件 ★最重要テーマ

機能・操作フロー・フィードバック・IDE統合の同等以上を評価する。ピクセル単位の一致は要求しない。以下の既存UX要件に加え、新機能では最新公式IDE内panelとMCP/IDE統合を含む競合構成の比較を記録する。

### 8.1 レイアウト方針
- チャット入力欄は画面下部固定、会話は上方向にスクロールする縦積みレイアウト(ネイティブCursor Agentタブと同じ配置)
- ユーザーメッセージとAgent応答を吹き出し/カラーリングで視覚的に区別する
- モデル選択・モードトグル(Ask/Agent/Plan)は入力欄のすぐ上または横に常時表示し、ワンクリックで切替可能にする(ネイティブの「入力欄下のモード切替」相当)

### 8.2 `@メンション`のUX再現
- 入力欄で`@`を入力した瞬間に候補ポップアップを表示(IntelliJの`TextFieldWithAutoCompletion`または`EditorTextField`+カスタム補完コントリビューターを使用)
- ファジーマッチによる絞り込み(ネイティブの「Start typing after @ and Cursor shows matching suggestions」の再現)
- 選択後、チップ(タグ)状にメンションを視覚化する(可能であれば。困難な場合はテキスト埋め込みで妥協し、P2で改善)

### 8.3 差分表示のUX
- Agentがファイル変更を提案した際、チャット内にインライン差分サマリー(+N/-M行)を表示し、クリックでIDE純正Diff Viewerを開く
- 変更の確認と取り消しをチャットから行えるようにする。現方式Bは事後Revert。ACPのpermission/Plan承認は個別契約を確認し、すべての書込みの事前承認と同一視しない。

### 8.4 チェックポイントのUX
- チャットのタイムライン上に、各ユーザープロンプトの左側(またはメッセージヘッダー)に「ロールバック」アイコンを配置し、ネイティブCursorの「チャットのタイムラインから復元」体験を模倣する

### 8.5 レスポンシブなストリーミング表示
- Agentの思考過程・ツール呼び出し(tool_callイベント)をリアルタイムで「〇〇を実行中...」のようなステータス表示にする(ネイティブAgentモードの実行過程可視化の再現)

### 8.6 UX再現度の評価基準(受け入れ基準)
- [ ] `@`入力→候補表示→選択→送信までの操作回数がネイティブCursorと同等(3クリック以内)
- [ ] チャットから差分確認と事後Revertへ進め、後続変更との競合時は安全に拒否できる。事前承認が提供されるACP操作は、その要求・返答を別の受入Caseで確認する。
- [ ] モデル切替がツールウィンドウを離れずに1クリックでできる
- [ ] ロールバックが誤操作なく(確認ダイアログ付きで)1クリックで実行できる

---

## 9. 外部インターフェース仕様

### 9.1 CLI呼び出しコマンド例

```bash
# 通常のプロンプト送信(ストリーミング)
agent -p --output-format stream-json --stream-partial-output "<prompt>" --workspace <projectRoot>

# セッション再開
agent -p --output-format stream-json --resume <chatId> "<prompt>"

# モデル一覧取得
agent --list-models

# MCPサーバー一覧
agent mcp list
```

### 9.2 プロセス起動時の環境変数
- `CURSOR_API_KEY`:未設定の場合は`agent login`によるブラウザ認証状態(OS資格情報ストア等)を引き継げるか要検証 `[要検証]`
- `PATH`:Android Studioがサブプロセスに継承する環境変数がシェル起動時と異なる場合があるため、明示的にフルパスで`cursor-agent`実行ファイルを指定する設計にする

### 9.3 stream-jsonイベントの扱い(パーサー設計方針)
- JSON Lines形式で1行1イベントを受信する想定
- 未知の`type`フィールドを持つイベントは無視してクラッシュしない
- 最終的な結果オブジェクト(`result`, `chatId`, `model`を含む)を受信したらセッション状態を更新する

---

## 10. データ設計(概略)

| データ | 保存先 | 内容 |
|---|---|---|
| チャット履歴 | プラグイン内部ストレージ(IntelliJ `PersistentStateComponent`) | メッセージ本文、ロール、タイムスタンプ、対応chatId |
| チェックポイント | プラグイン内部ストレージ or `.git`とは別領域 | 対象ファイルパス、変更前スナップショット、紐づくプロンプトID |
| 設定(モデル、force設定等) | `PersistentStateComponent`(Application/Project Settings) | ユーザー選択の永続化 |

---

## 11. リスク・既知の課題(再掲・追加)

1. **stream-jsonスキーマの非互換リスク**:CLIアップデートで出力形式が変わる可能性がある `[仮説]`。パーサーのバージョン検出・フォールバック設計が必要
2. **チェックポイント機構の複雑性**:Gitのステージング状態と自前スナップショットが衝突するケースの設計が甘いと、ユーザーの実コミット履歴を壊すリスクがある
3. **認証情報の引き継ぎ失敗**:サブプロセスの環境変数継承がOSによって異なり、認証エラーが頻発する可能性
4. **UIスレッドブロッキング**:ストリーミング処理をEDTで直接処理すると、Android Studio全体がカクつくリスク
5. **画像添付・音声入力のCLI対応状況不明**:機能要件確定前に公式ドキュメントでの検証が必須

---

## 12. フェーズ計画

`[2026-09改訂]` 進捗はGitHub Issues([トラッキングIssue #1](https://github.com/shinma06/cursor-in-android-studio/issues/1)、子Issue #2〜#10)が一次情報源。以下は俯瞰用。

| フェーズ | 内容 | 含む主要機能ID | 状態 |
|---|---|---|---|
| **Phase 1(MVP)** | 基本チャット・context・mode/model・事後Diff/Revert・checkpoint | F-01〜05, F-10, F-15, F-20〜22, F-30〜33, F-40〜42, F-44, F-51 | 基本実装済み。本文永続化/専用checkpoint一覧は未実装、復元安全性の後続はdevelopとQA #102。過去M0待ちは解消 |
| **Phase 2** | フォルダメンション、Git diffメンション、コンテキスト圧縮、過去チャット一覧、MCP一覧表示、Branchメンション | F-06, F-11, F-12, F-16, F-50, F-70 | 基本実装あり。F-50本文復元は #44、MCP診断拡充は #25 |
| **Phase 2.5(2026-09追加)** | 権限モデル再設計(sandbox/auto-review)、デスクトップ通知 | F-23, F-24, デスクトップ通知(§6.9) | F-24実装済み。デスクトップ通知実装済み。F-23は`--sandbox`トグル基本実装済み(2026-09) |
| **Phase 3(将来検討)** | Docs/Web・画像・音声・Browser・Subagents・Skills/Custom Modes | F-14, F-60〜62 | 公開経路とlive未検証を最新マトリクスで分離。F-13/F-52(基本)/F-71は実装済み。画像/会話取得の永久非対応という旧判定は撤回 |

---

## 13. 未確定事項(要検証リスト)

- [ ] `[公開経路あり・要実測 2026-09-08]` CLI画像path読取 — helpの画像flag不在による旧非対応判定を訂正。#10で識別画像を非TTY送信し、正答/失敗とUI要件を確認する(F-60)
- [x] `[検証済 2026-09]` 認証情報(`CURSOR_API_KEY`/ブラウザログイン状態)のサブプロセスへの引き継ぎ可否 — `GeneralCommandLine.withEnvironment(System.getenv())`で`agent status`相当のログイン状態が引き継がれることを確認済み
- [x] `[検証済 2026-09-04]` stream-jsonのイベントスキーマ(実機) — `system/init`, `user`, `connection`, `retry`, `assistant`(累積/差分混在), `tool_call`(started/completed、`readToolCall`/`editToolCall`/`shellToolCall`)。`editToolCall` completed に `beforeFullFileContent`/`afterFullFileContent`/`diffString` を確認。fixture: `src/test/resources/stream-json-fixtures/`
- [ ] `[仮説]` チェックポイントの保持期間・上限件数の妥当な設計値 — デフォルト15日で実装済みだが、ユーザーが変更できる設定UIは未実装
- [x] `[検証済・方針決定 2026-09]` IntelliJ Platform側で`@`補完UIをどのコンポーネントで実現するのが最も自然か → `EditorTextField`+自作補完コントリビューターで実装済み。ただし`runIde`での実機QA(Enter送信/Shift+Enter改行、ポップアップのフォーカス挙動)は未実施
- [x] `[検証済 2026-09]` 新規ワークスペースでは`--trust`/`--yolo`/`-f`なしだと「Workspace Trust Required」で即失敗することが判明。プラグインは常に`--trust`を付与するよう修正済み(IDEでプロジェクトを開いている時点がユーザーの信頼判断そのものであるため)
- [x] `[検証済 2026-09]` `agent ls`/`agent resume`(過去セッション選択)は生TTY必須のInkベースTUIで、`OSProcessHandler`等の非TTYサブプロセスからは`Raw mode is not supported`で失敗する。過去チャット一覧(F-50)はCLIのセッション一覧機能に頼らず、プラグイン側で`chatId`を自前で永続化する設計とする(実装済み)
- [x] `[検証済 2026-09]` `agent mcp`には`list`/`list-tools <id>`/`enable <id>`/`disable <id>`/`login <id>`サブコマンドが存在する。F-71(MCP有効/無効切替)は`.cursor/mcp.json`相当を直接編集せず、これらのサブコマンドを呼び出す実装で十分(実装済み)
- [x] `[検証済 2026-09]` `--list-models`と`agent mcp list`/`enable`/`disable`はローカルのメタデータ操作でチャットのクォータを消費しないことが判明。F-21/F-70/F-71はこの発見によりM0のクォータブロッカーを回避して実装済み
- [x] `[検証済 2026-09-04]` force ON/OFFでのファイル書き込みタイミングとtool_call結果 — Teamsプラン実機: `permissionMode: default`でもヘッドレス subprocess では**即書き込み**。F-30/F-31は事後 diff/revert モデルで実装済み(PR #18)
- [x] `[検証済 2026-09-04]` `AssistantChunkDeduper`のストリーミング重複排除 — Teams実機で `--stream-partial-output` が増分断片と累積再送を混在させることを確認。heuristic を更新し `AssistantChunkDeduperTest` で固定
- [ ] `[公開対応と未実測を分離 2026-09-08]` headless Skills/Subagentsは公開対応。子event/UI・sticky Custom Mode・Multitaskとの境界をlive fixtureで確認する(§6.9/最新マトリクス参照)
- [x] `[検証済 2026-09-04]` `agent mcp list`の出力形式(`id: status`行)を実配置環境で確認。`McpListParser`+整列表示に置き換え済み

---

## 14. 参考ドキュメント

- Cursor CLI Parameters: https://cursor.com/docs/cli/reference/parameters
- Cursor CLI Output Format: https://cursor.com/docs/cli/reference/output-format
- Cursor CLI Using Agent: https://cursor.com/docs/cli/using
- Cursor CLI MCP: https://cursor.com/docs/cli/mcp.md
- Cursor Agent Prompting(@メンション仕様): https://cursor.com/docs/agent/prompting.md
- IntelliJ Platform Tool Windows: https://plugins.jetbrains.com/docs/intellij/tool-windows.html
- IntelliJ Platform Extensions: https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html
- `[2026-09追加]` Cursor CLI Changelog: https://cursor.com/docs/cli/changelog
- `[2026-09追加]` Cursor Context/Mentions(@Branch, @Chats, @Browser, @codebase等の一覧): https://cursor.com/docs/context/mentions
- `[2026-09追加]` Cursor Changelog 2.4: https://cursor.com/changelog/2-4
- `[2026-09追加]` Cursorコミュニティフォーラム: チェックポイントUIの既知の不具合報告(参考程度、一次情報ではない) — forum.cursor.com上のRestore Checkpointボタン消失・diffナビゲーションバー消失に関するスレッド

---

## 次のアクション

`[2026-09-05訂正]` M0の即時書込み検証、F-30〜32、sandbox基本選択、通知は PR #18 までに完了している。
旧来のブロッカー記述を次の作業選択に使わない。既存の残作業はGitHubの現行Issue一覧で管理する。#10は画像path、#99はOS音声入力、製品変更後のQAは #102〜#108へ分離済み。

UI差分の新規取り込みは [計画書](plans/cursor-agent-ui-gap-plan.md) の UX-01（権限・sandbox表示とWorktree復元整合性）から進める。
以降は入力操作、本文履歴、状態/レビュー/キュー、コンテキスト導線の順とする。
Debug/Multitask/権限個別制御などは CLI 検証ゲートを通す。計画作成は機能実装・実行検証の完了を意味しない。

実機UIの限定確認（設定・パネル表示）と追加観点は [Android Studio実機追補](research/android-studio-ui-followup-2026-09-05.md) を参照。UX-08（P1、モデル名・多数候補）とUX-09（P2、ビルド/CLI診断）を計画に追加した。入力・送信・設定保存のQA完了を意味しない。

### 2026-09-06 同一モデルのオプション選択（#27）

同じモデルのThinking/Fast/Context/Effort違いをモデル系列にまとめ、利用可能なオプションだけを表示する。選択値は実際の `agent --list-models` のIDへ解決して既存の `--model` に渡す。モデルや容量の固定カタログは持たず、不明な派生は独立項目として残す。既存IDの設定はそのまま復元する。

取得した一覧でContext容量が1種類しか分からない場合はContextを隠す。CLIヘルプには `model[context=1m,effort=high,fast=false]` 形式の説明があるが、対応値一覧は得られないため、未検証の容量を生成しない。Cursorの画像はUI参考であり、このCLIアカウントでの対応値を保証しない。
