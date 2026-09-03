# Android Studio向け Cursor Agent 統合プラグイン 要件定義書

- **文書バージョン**: v0.1(ドラフト)
- **作成日**: 2026-09-03
- **対象読者**: 実装者(自分)
- **Assumed環境**: Kotlin 2.x / Android Studio (最新安定版) / IntelliJ Platform 2023.1+ 系API / cursor-agent CLI 2026年7月時点仕様
- **開発方式**: 方式B(ネイティブUI方式) — `agent -p --output-format stream-json` をサブプロセス実行し、独自Swing/JBUI製チャットパネルに描画する

---

## 1. 背景・目的

### 1.1 背景
Androidアプリ開発チームは、 Android Studio と、AI支援コーディングツールとして Cursor を併用している。
しかし現状はAI支援を使用するタイミングは Cursor 、その他の作業ではよりネイティブな Android Studio を使うという運用となっており、開発ツールを行き来するという手間が発生している。

### 1.2 目的
Android Studio(IntelliJ Platform)上に、**Cursor IDEのAgentタブと可能な限り同一の使い心地**を再現するツールウィンドウ型プラグインを開発する。
バックエンドは `cursor-agent` CLIのプロセス制御とJSONストリームのパースで実現し、UIはIntelliJ Platform Swing/JBUIコンポーネントでネイティブに構築する。

### 1.3 非目的(スコープ外)
- Cursor CLI自体の機能拡張・改造は行わない(ブラックボックスとして利用する)
- Android Studio以外のIDE(VS Code等)への対応は行わない
- クラウドエージェント(バックグラウンドでのPR自動生成)機能は本フェーズでは対象外とする(§13 将来拡張で扱う)

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
  2. Agentが提示した差分をIDE内で確認し、Apply/Rejectする
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
- CLIには「チェックポイント」「@Browser視覚検証」「音声入力」等、IDE専用機能が存在しない。これらは本プラグイン側で代替実装するか、スコープ外とする(§7 機能要件で個別判定)
- MCP(Model Context Protocol)はCLIとエディタで共通設定を使うため、`@Docs`/`@Web`相当の機能はMCPサーバー追加が前提となる
- 画像添付のCLI経由での可否は `[要検証]`(公式リファレンス未確認)。検証方法: `cursor.com/docs/cli/reference` 配下のパラメータ一覧を再度精査、または実機で `agent -p` に画像パスを渡すテストを行う

---

## 5. 全体アーキテクチャ

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

優先度は **MVP(必須) / P2(次フェーズ) / P3(将来検討)** の3段階。各項目にCLIでの実現可否根拠を付す。

### 6.1 チャット基本機能

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-01 | テキストプロンプト送信 | MVP | `agent -p --output-format stream-json "<prompt>"` |
| F-02 | ストリーミング応答表示(トークン単位) | MVP | `--stream-partial-output`イベントを逐次パースしUI更新 |
| F-03 | 会話履歴の保持・スクロール表示 | MVP | プラグイン内メモリ + セッションID紐付けで永続化 |
| F-04 | セッション再開(前回の続きから) | MVP | `--resume [chatId]` |
| F-05 | 新規チャット開始 | MVP | セッションID未指定で新規起動 |
| F-06 | コンテキスト圧縮 | P2 | `/summarize`をプロンプト経由で送信 |

### 6.2 コンテキスト注入(`@メンション`)

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-10 | `@ファイル名`補完UI | MVP | Android Studioのプロジェクトツリー/開いているファイル一覧からJList/JPopupMenuで候補表示 → 選択時にプロンプト文字列へ`@path`を埋め込み |
| F-11 | `@フォルダ`指定 | P2 | 同上、ディレクトリ選択対応 |
| F-12 | `@Git diff`(未コミット差分) | P2 | プラグイン側で`git diff`を実行し結果をプロンプトに埋め込み(CLI自動対応なし) |
| F-13 | `@Terminals`(ターミナル出力) | P3 | IntelliJ Terminal Pluginのバッファ取得APIと連携 `[要検証]` |
| F-14 | `@Docs` / `@Web` | P3 | MCPサーバー追加設定が前提。本フェーズはスコープ外 |
| F-15 | 現在開いているファイル/選択範囲の自動コンテキスト化 | MVP | エディタの`FileEditorManager`/`SelectionModel`から取得し、送信時に自動付与(ネイティブCursorの「Active file and selection」相当) |

### 6.3 モード・モデル制御

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-20 | Ask / Agent / Plan モード切替 | MVP | `--mode=ask` / 通常 / `--mode=plan` |
| F-21 | モデル選択UI(ドロップダウン) | MVP | `--model <name>`、選択肢は`agent --list-models`で動的取得 |
| F-22 | 自動承認(force)トグル | MVP | `--force`フラグのON/OFF切替。**デフォルトはOFF**とし、誤操作でのファイル破壊を防止 |

### 6.4 実行結果の可視化・適用

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-30 | 差分プレビュー(IDE純正Diff Viewerで表示) | MVP | AgentのJSON出力からファイル変更内容を抽出し、`DiffManager`で表示 |
| F-31 | Apply / Reject ボタン | MVP | Apply時に実ファイルへ書き込み、Reject時は破棄。**force=OFF時のデフォルト運用** |
| F-32 | Shell実行結果の表示 | MVP | stream-jsonのtool_callイベント(shell系)をコンソール風に整形表示 |
| F-33 | エラー発生時の分かりやすい表示 | MVP | プロセスのstderr / 非ゼロ終了コードをUIにトースト+ログパネルで表示 |

### 6.5 安全策(チェックポイント/ロールバック) ★重要テーマ関連

ネイティブCursorの「チェックポイント」はCLIに存在しないため、**Git連携による代替実装**を必須要件とする。

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-40 | プロンプト送信前の自動スナップショット | MVP | プロンプト送信直前に対象ファイルの内容をプラグイン内部ストレージ(または`git stash create`相当)に退避 |
| F-41 | チェックポイント一覧・タイムライン表示 | MVP | チャット履歴と紐付けて、各ユーザープロンプトごとにチェックポイントを1件生成し、パネルに時系列表示 |
| F-42 | 任意チェックポイントへのワンクリックロールバック | MVP | 該当スナップショットの内容でファイルを上書き復元 |
| F-43 | チェックポイントの有効期限管理 | P2 | 参考実装(CodeArts Agent等)では15日で自動失効する例あり。本プラグインでも一定期間後にクリーンアップするか要検討 `[仮説]` |
| F-44 | Gitとの併用時の競合回避 | MVP | チェックポイント機構はGit本体のコミット履歴を汚さない設計とする(別ブランチ/内部差分ストレージを使用し、正規のバージョン管理を代替しない) |

### 6.6 セッション・履歴管理

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-50 | 過去チャット一覧(`@Past Chats`相当) | P2 | `--resume`で使えるchatIdを一覧化し選択可能にする |
| F-51 | チャットのプロジェクト単位分離 | MVP | `--workspace <path>`をプロジェクトルートに固定 |

### 6.7 マルチモーダル・拡張入力

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-60 | 画像添付 | P3 | CLI経由での可否が`[要検証]`のため保留。検証後に優先度確定 |
| F-61 | 音声入力 | P3 | CLIに相当機能なし。OS音声認識APIとの自前統合が必要なため将来検討 |
| F-62 | ブラウザ視覚検証(`@Browser`相当) | P3 | Playwright系MCPサーバー導入が前提。スコープ外 |

### 6.8 MCP連携

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-70 | MCPサーバー一覧表示 | P2 | `agent mcp list`の結果をUIに反映 |
| F-71 | MCPサーバーの有効/無効切替 | P3 | CLIとエディタで設定共有のため、`.cursor/mcp.json`相当の設定ファイル編集UIを検討 |

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

「**極限までCursor IDEのAgentタブと同じ使い心地**」を実現するため、以下を具体的な受け入れ基準とする。

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
- Apply/Rejectをチャット内のカードUIから直接操作できるようにする(ネイティブCursorの「diffを見ながらその場で承認」体験の再現)

### 8.4 チェックポイントのUX
- チャットのタイムライン上に、各ユーザープロンプトの左側(またはメッセージヘッダー)に「ロールバック」アイコンを配置し、ネイティブCursorの「チャットのタイムラインから復元」体験を模倣する

### 8.5 レスポンシブなストリーミング表示
- Agentの思考過程・ツール呼び出し(tool_callイベント)をリアルタイムで「〇〇を実行中...」のようなステータス表示にする(ネイティブAgentモードの実行過程可視化の再現)

### 8.6 UX再現度の評価基準(受け入れ基準)
- [ ] `@`入力→候補表示→選択→送信までの操作回数がネイティブCursorと同等(3クリック以内)
- [ ] 差分確認からApplyまでを画面遷移なしで完結できる
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

| フェーズ | 内容 | 含む主要機能ID |
|---|---|---|
| **Phase 1(MVP)** | 基本チャット + `@ファイル`コンテキスト + モード/モデル切替 + 差分Apply/Reject + チェックポイント基本機能 | F-01〜05, F-10, F-15, F-20〜22, F-30〜33, F-40〜42, F-44, F-51 |
| **Phase 2** | フォルダメンション、Git diffメンション、コンテキスト圧縮、過去チャット一覧、MCP一覧表示 | F-06, F-11, F-12, F-50, F-70 |
| **Phase 3(将来検討)** | Terminals連携、Docs/Web(MCP前提)、画像添付、音声入力、ブラウザ視覚検証 | F-13, F-14, F-60〜62, F-71 |

---

## 13. 未確定事項(要検証リスト)

- [ ] `[要検証]` CLIでの画像添付方法の有無・実装方法
- [ ] `[要検証]` 認証情報(`CURSOR_API_KEY`/ブラウザログイン状態)のサブプロセスへの引き継ぎ可否
- [ ] `[要検証]` stream-jsonの正確なイベントスキーマ(公式リファレンスの詳細ページを別途取得)
- [ ] `[仮説]` チェックポイントの保持期間・上限件数の妥当な設計値
- [ ] IntelliJ Platform側で`@`補完UIをどのコンポーネントで実現するのが最も自然か(`TextFieldWithAutoCompletion` vs カスタムEditor実装)の技術検証

---

## 14. 参考ドキュメント

- Cursor CLI Parameters: https://cursor.com/docs/cli/reference/parameters
- Cursor CLI Output Format: https://cursor.com/docs/cli/reference/output-format
- Cursor CLI Using Agent: https://cursor.com/docs/cli/using
- Cursor CLI MCP: https://cursor.com/docs/cli/mcp.md
- Cursor Agent Prompting(@メンション仕様): https://cursor.com/docs/agent/prompting.md
- IntelliJ Platform Tool Windows: https://plugins.jetbrains.com/docs/intellij/tool-windows.html
- IntelliJ Platform Extensions: https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html

---

## 次のアクション
Phase 1(MVP)のうち、まず技術的に不確実性が高い**F-40〜44(チェックポイント機構)**と**F-10/F-15(コンテキスト自動注入)**の技術検証(spike)から着手するのが、手戻りリスクを最小化する順序として妥当。
