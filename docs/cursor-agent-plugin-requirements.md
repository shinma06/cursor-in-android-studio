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
| F-13 | `@Terminals`(ターミナル出力) | P3(**実装済み 2026-09**) | Reworked Terminal API経由(`TerminalToolWindowTabsManager`/`TerminalView.outputModels.regular`)。開いているTerminalタブの末尾8KBを`@terminal`送信時に埋め込み。タブ未オープン時はプレースホルダー |
| F-14 | `@Docs` / `@Web` | P3 | MCPサーバー追加設定が前提。本フェーズはスコープ外 |
| F-15 | 現在開いているファイル/選択範囲の自動コンテキスト化 | MVP | エディタの`FileEditorManager`/`SelectionModel`から取得し、送信時に自動付与(ネイティブCursorの「Active file and selection」相当) |
| F-16 | `@Branch`(現在のブランチ vs mainの差分) | P2 `[2026-09追加]`(**実装済み 2026-09**) | プラグイン側で`git diff <base>...HEAD`相当を実行し埋め込み。baseは`origin/HEAD`→`main`→`master`の順で解決 |
| F-17 | `@Chats`(別の過去セッションの内容を参照) | P3 `[2026-09追加, 要検証]` | ネイティブCursorには存在する機能。`agent ls`/`resume`が生TTY必須でプラグインから使えないと判明済み(F-50と同じ制約)なため、CLIにトランスクリプト取得の別手段(非対話コマンド等)がない限り実現不可の可能性が高い |
| — | `@codebase` / `@definitions`(Cursor独自のセマンティック検索・シンボルインデックス) | **恒久的にスコープ外** `[2026-09追加]` | Cursor社の独自インデックスバックエンドに依存しており、CLI経由で相当機能を利用する手段がない。「CLIで再現できること」という本プロジェクトの条件を満たさないため実装しない |

> `@codebase`/`@definitions`はネイティブCursor Agentパネルの実在機能だが、CLIに公開されていないため対象外とする(§1.3非目的に準ずる恒久的除外)。将来CLIが同等機能を公開した場合に再検討する。

### 6.3 モード・モデル制御

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-20 | Ask / Agent / Plan モード切替 | MVP(実装済み) | `--mode=ask` / 通常 / `--mode=plan` |
| F-21 | モデル選択UI(ドロップダウン) | MVP(実装済み) | `--model <name>`、選択肢は`agent --list-models`で動的取得 |
| F-22 | 自動承認(force)トグル | MVP(実装済み、**要再設計** `[2026-09]`) | 現状は`--force`のON/OFF二値切替として実装済みだが、実際のCLIの権限モデルは3値(「Run Everything」=`--force`/`--yolo` / 「Auto-Run in Sandbox」=`--sandbox enabled`との組合せ / 「Ask Every Time」=デフォルト)である。加えて`--auto-review`(サーバー側分類器が安全な呼び出しのみ自動実行し残りは確認を求める)という第4のモードも存在する。二値トグルのままでも動作はするが、ネイティブCursorのUXとは乖離があるため、UIを3〜4択のセレクタに再設計することを推奨(P2、後述F-23参照) |
| F-23 | サンドボックス実行モード | P2 `[2026-09追加]` | `--sandbox enabled\|disabled`、`--allow-paths`/`--readonly-paths`/`--blocked-patterns`/`--network`。F-22の再設計と合わせて「Auto-Run in Sandbox」相当を実現する土台。設定はF-22のUIから選択するモードに応じて組み立てる |
| F-24 | Auto-review(スマート自動承認) | P2 `[2026-09追加]` | `--auto-review`フラグ。F-22のトグルを「Ask Every Time / Auto-review / Run Everything」の3択にする際の一角 |

### 6.4 実行結果の可視化・適用

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-30 | 差分プレビュー(IDE純正Diff Viewerで表示) | MVP(M0のforce ON/OFF書き込みタイミング検証待ちでブロック中) | AgentのJSON出力からファイル変更内容を抽出し、`DiffManager`で表示 |
| F-31 | Apply / Reject ボタン | MVP(同上、ブロック中) | Apply時に実ファイルへ書き込み、Reject時は破棄。**force=OFF時のデフォルト運用** |
| F-32 | Shell実行結果の表示 | MVP(M0のtool_call結果イベント検証待ちでブロック中) | stream-jsonのtool_callイベント(shell系)をコンソール風に整形表示 |
| F-33 | エラー発生時の分かりやすい表示 | MVP(実装済み) | プロセスのstderr / 非ゼロ終了コードをUIにトースト+ログパネルで表示 |

> `[2026-09追加]` ネイティブCursor CLIには`/changes`(Ctrl+R)という、そのセッションでの全編集を集約した統合レビューUIが存在する模様(CLI changelogで言及)。非対話モードでの相当コマンドの有無は未確認。F-30/F-31の設計を確定させるM0検証と合わせて調査し、単純なdiffカードの羅列ではなく統合ビューにすべきか再検討する。

### 6.5 安全策(チェックポイント/ロールバック) ★重要テーマ関連

ネイティブCursorの「チェックポイント」はCLIに存在しないため、**Git連携による代替実装**を必須要件とする。

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
| F-50 | 過去チャット一覧(`@Past Chats`相当) | P2(実装済み) | `agent ls`/`agent resume`は生TTY必須で非対話サブプロセスから使用不可と判明済みのため、プラグイン側で`(chatId, 冒頭プロンプト, 更新時刻)`を独自に永続化・一覧表示。選択で次ターンから`--resume`。**既知の制約**: 過去ターンの再描画は非対応(CLIにトランスクリプト取得手段がないため) |
| F-51 | チャットのプロジェクト単位分離 | MVP(実装済み) | `--workspace <path>`をプロジェクトルートに固定 |
| F-52 | Worktree分離実行 | P3 `[2026-09追加, 要検証]` | `-w/--worktree [name]`、`--worktree-base <branch>`、`--skip-worktree-setup`。Agentを独立したgit worktree(`~/.cursor/worktrees/<repo>/<name>`)上で実行する機能。プロジェクト本体のワーキングツリーを変更しない実行モードとして有用だが、「変更がどこに反映されるか」のUXが複雑になるため、導入する場合はF-30/F-31(diff適用)の設計と合わせて別途検討する |

### 6.7 マルチモーダル・拡張入力

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-60 | 画像添付 | P3(**`[要検証]`のまま、矛盾情報あり `[2026-09]`**) | CLI changelogは`--image`フラグ・クリップボード貼り付け対応を謳うが、`cursor.com/docs/cli/reference/parameters`の正式リファレンスには`--image`の記載がない。ドキュメント間で矛盾しているため「対応している」と早合点せず、実機検証(M0のクォータ回復後)で確認するまでフラグ名を含め未確定として扱う |
| F-61 | 音声入力 | P3 | CLIに相当機能なし。macOSのOSレベル辞書入力(Fn Fn)が`EditorTextField`に対して標準のテキスト入力として機能するなら追加実装不要という仮説あり、要検証 |
| F-62 | ブラウザ視覚検証(`@Browser`相当) | P3 | ネイティブCursorにも直接のCLIフラグはなし。Playwright系MCPサーバー導入 + 汎用MCPツール結果表示(F-32拡張、画像サムネイル対応)で代替可能と判断し、専用実装はしない方針 |

### 6.8 MCP連携

| ID | 機能 | 優先度 | 実現方式 |
|---|---|---|---|
| F-70 | MCPサーバー一覧表示 | P2(実装済み、出力パース精度は要フォローアップ) | `agent mcp list`の結果をUIに反映。現状は生出力をそのまま表示(構造化パース未実装、実配置環境での検証が必要) |
| F-71 | MCPサーバーの有効/無効切替 | P3(実装済み) | `.cursor/mcp.json`の直接編集ではなく、`agent mcp enable <id>` / `agent mcp disable <id>`サブコマンドを利用(要件定義時点の想定より簡単に実現できた) |

### 6.9 将来調査事項(2026-09の網羅的リサーチで新たに判明、優先度未確定)

ネイティブCursor Agentパネル/CLIの調査で見つかった、現時点では実現方式が固まっていない項目。実装に着手する前に個別の技術検証(spike)が必要。

| 項目 | 概要 | CLIでの再現性 |
|---|---|---|
| Subagents / `/multitask` | Agentが複数のサブタスクを並列的な非同期サブエージェントとして実行する機能(Cursor 3.2+)。CLI changelogに「subagent transcript」「サブエージェント用チェックポイント永続化」の言及あり | `[要検証]` 何らかのCLI側サポートがある可能性はあるが、コマンド体系が未確認 |
| Custom Modes | `/`コマンドで任意のスキルをカスタムモードとしてピン留めする機能 | `[要検証]` `--mode`まわりの拡張である可能性 |
| デスクトップ通知 | ターン完了時・承認待ち発生時のOSネイティブ通知 | **実装済み 2026-09** — IntelliJ `Notification` API。ターン完了/ツール呼び出し開始時に通知(設定でOFF可) |
| `permissions.json` | チーム管理者向けのターミナル/MCP許可リスト宣言ファイル。IDEとCLIで設定共有 | F-22/F-23再設計時の参考として調査対象。個人利用が主眼の本プラグインでは優先度低 |
| CLI Hooks(session start/end, stop, pre-compaction等) | チーム管理向け自動化フック | Agentパネルの「UX」ではなく設定機能のため本プラグインのスコープ外と判断 |
| クラウドエージェント / バックグラウンドPR自動生成 | 常時稼働のクラウドエージェント、Slack連携等 | §1.3の非目的に該当。恒久的にスコープ外(クラウド実行はローカルCLIサブプロセスのラップという本プラグインの前提と根本的に相容れない) |

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

`[2026-09改訂]` 進捗はGitHub Issues([トラッキングIssue #1](https://github.com/shinma-postas/cursor-agent-plugin/issues/1)、子Issue #2〜#10)が一次情報源。以下は俯瞰用。

| フェーズ | 内容 | 含む主要機能ID | 状態 |
|---|---|---|---|
| **Phase 1(MVP)** | 基本チャット + `@ファイル`コンテキスト + モード/モデル切替 + 差分Apply/Reject + チェックポイント基本機能 | F-01〜05, F-10, F-15, F-20〜22, F-30〜33, F-40〜42, F-44, F-51 | F-30〜32以外は実装済み。F-30〜32はM0のCLI実機検証待ちでブロック中 |
| **Phase 2** | フォルダメンション、Git diffメンション、コンテキスト圧縮、過去チャット一覧、MCP一覧表示、Branchメンション | F-06, F-11, F-12, F-16, F-50, F-70 | 実装済み |
| **Phase 2.5(2026-09追加)** | 権限モデル再設計(sandbox/auto-review)、デスクトップ通知 | F-23, F-24, デスクトップ通知(§6.9) | F-24実装済み。デスクトップ通知実装済み(2026-09)。F-23未着手 |
| **Phase 3(将来検討)** | Terminals連携、Docs/Web(MCP前提)、画像添付、音声入力、ブラウザ視覚検証、Worktree、Chats参照、Subagents、Custom Modes | F-13, F-14, F-17, F-52, F-60〜62, F-71 | F-13/F-71実装済み。他は要検証/未着手 |

---

## 13. 未確定事項(要検証リスト)

- [ ] `[要検証・矛盾情報あり 2026-09]` CLIでの画像添付方法の有無・実装方法 — CLI changelogは`--image`フラグ対応を謳うが、公式パラメータリファレンス(`cursor.com/docs/cli/reference/parameters`)には記載なし。ドキュメント間で矛盾しており実機検証必須。未検証(Freeプランのクォータ枯渇で保留、下記参照)
- [x] `[検証済 2026-09]` 認証情報(`CURSOR_API_KEY`/ブラウザログイン状態)のサブプロセスへの引き継ぎ可否 — `GeneralCommandLine.withEnvironment(System.getenv())`で`agent status`相当のログイン状態が引き継がれることを確認済み
- [ ] `[要検証]` stream-jsonの正確なイベントスキーマ(公式リファレンスの詳細ページを別途取得) — `system/init`, `user`, `connection`, `retry`イベントは実機確認済み(`CLAUDE.md`参照)。ファイル編集イベント・tool_call結果イベントは未確認(下記参照)
- [ ] `[仮説]` チェックポイントの保持期間・上限件数の妥当な設計値 — デフォルト15日で実装済みだが、ユーザーが変更できる設定UIは未実装
- [x] `[検証済・方針決定 2026-09]` IntelliJ Platform側で`@`補完UIをどのコンポーネントで実現するのが最も自然か → `EditorTextField`+自作補完コントリビューターで実装済み。ただし`runIde`での実機QA(Enter送信/Shift+Enter改行、ポップアップのフォーカス挙動)は未実施
- [x] `[検証済 2026-09]` 新規ワークスペースでは`--trust`/`--yolo`/`-f`なしだと「Workspace Trust Required」で即失敗することが判明。プラグインは常に`--trust`を付与するよう修正済み(IDEでプロジェクトを開いている時点がユーザーの信頼判断そのものであるため)
- [x] `[検証済 2026-09]` `agent ls`/`agent resume`(過去セッション選択)は生TTY必須のInkベースTUIで、`OSProcessHandler`等の非TTYサブプロセスからは`Raw mode is not supported`で失敗する。過去チャット一覧(F-50)はCLIのセッション一覧機能に頼らず、プラグイン側で`chatId`を自前で永続化する設計とする(実装済み)
- [x] `[検証済 2026-09]` `agent mcp`には`list`/`list-tools <id>`/`enable <id>`/`disable <id>`/`login <id>`サブコマンドが存在する。F-71(MCP有効/無効切替)は`.cursor/mcp.json`相当を直接編集せず、これらのサブコマンドを呼び出す実装で十分(実装済み)
- [x] `[検証済 2026-09]` `--list-models`と`agent mcp list`/`enable`/`disable`はローカルのメタデータ操作でチャットのクォータを消費しないことが判明。F-21/F-70/F-71はこの発見によりM0のクォータブロッカーを回避して実装済み
- [ ] `[未検証・保留]` force ON/OFFでのファイル書き込みタイミングとtool_call結果イベントの内容 — 検証プロンプト実行時に`resource_exhausted`(Freeプランのクォータ枯渇)で行き詰まり中。クォータ回復後またはプラン変更後に再検証する。**F-30/F-31/F-32はこれに依存する唯一の残ブロッカー**
- [ ] `[要検証 2026-09追加]` `dedupeAssistantChunk`(現`AgentUiController`、切り出し予定)のストリーミング重複排除ロジックは、実際の`assistant`イベント(累積 vs 差分)を一度も観測せずに書かれた未検証の推測ロジック。上記のブロッカー解消時に最優先で実データと突き合わせて検証すること
- [ ] `[要検証 2026-09追加]` Subagents/`/multitask`、Custom Modesの非対話CLIでの対応状況(§6.9参照)
- [ ] `[要検証 2026-09追加]` `agent mcp list`の出力を実際にMCPサーバーが1つ以上設定された状態で確認し、`McpServersDialog`の表示を構造化パースに置き換えるべきか判断する

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

`[2026-09改訂]` 唯一の残ブロッカーは **M0のforce ON/OFF書き込みタイミング検証**(Freeプランのクォータ枯渇で保留中)。これが解消され次第、F-30〜32(diff Apply/Reject、tool_call結果表示)と、実データに基づく`dedupeAssistantChunk`ロジックの検証を最優先で行う。

クォータに依存しない残作業としては、F-23/F-24(sandbox/auto-reviewによる権限モデル再設計)とデスクトップ通知(§6.9)が実装コスト対効果が高く、次の着手候補。
