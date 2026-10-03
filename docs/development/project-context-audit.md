# Project Context監査（#142）

2026-09-09。対象はdocs・指示・必要なコメント/テスト説明のみ。製品コード基準は develop `71c316c9eba125f505759fb093edf445168bee48`、通常mergeしたmainは `2c47f6249299852ddce3d9542ebbc2dc52fb5b92`（#131/#134/#135）。developの製品変更とPonytail fullを保持した。

## 判断の入口

[Project Mission](../project-mission.md) → [ACP First](../architecture/cursor-integration.md) → [現行実装](../architecture/current-implementation.md)。最優先はIDE内Agent panel、競合比較はJetBrains AI Assistant + Cursor ACP + IDE integration / IntelliJ MCP Server / 利用可能なtools。ACP標準 → Cursor拡張 → IDE API → MCP → CLI → 非構造表示解析の順で調べる。IDE直接取得の合理性、CLI補助の必要性は個別判断する。

方針決定と製品実装・GUI受入を分ける。ACPをテキスト往復だけに縮めず、session/stream/tool progress/permission/質問/Plan/Todo/context/usage等を評価するが、提供済み・実装済みとは宣言しない。#115の専用調査成果はこのIssueで作成・改変せず、境界を引き継ぐ。

## 探索範囲と処置

tracked/hiddenファイル名（AGENTS、CLAUDE、README、Instructions、rules、skills、architecture/design、plans、TODO/roadmap）を列挙し、docs・ソース・テストのCLI/print/stdout/process/session/TTY/未接続/black box等の参照と呼出関係を確認した。独立したTODO/roadmap/Project Instructionsファイルは見つからず、残作業は要件§12/次のアクション、旧gap plan、#1/#141と子Issueに存在する。

| カテゴリ・確認対象 | 処置・残した理由 |
|---|---|
| `AGENTS.md` → `CLAUDE.md` symlink | リンクを維持し、ACP正本と現行複数run構造へ更新。日付付きreview/live履歴は当時の記録と明記 |
| `README.md`、要件書全体 | mission/ACP/現行実装/監査へ誘導。架空の旧クラス図、本文永続化済み扱い、旧作業順を修正 |
| `.agents/skills/{start-work,finish-work}/SKILL.md`、`.claude/skills/{start-work,finish-work}/SKILL.md` | 設計正本と実装/証拠の区別、独立Session規約を追加。Issue/claim/worktree/PR/QA/停止引継ぎを保持 |
| `.cursor/rules/{loop-engineering,manual-verification}.mdc` | 同じ正本へ誘導。旧MV正本指示をCase JSONへ訂正。frontmatterを保持 |
| `.claude/settings.json` | コマンド許可設定。CLI-onlyの製品設計指示ではないため保持 |
| `docs/architecture/cursor-integration.md`、`docs/project-mission.md` | mainの決定済み正本を同期。現行構造と監査へのリンクを追加 |
| `docs/architecture/current-implementation.md` | ソース照合した所有・prepare/send・ID/root・EDT・停止/終了・保存範囲を新規記録 |
| `docs/plans/session-tabs-state.md`、`docs/development/restore-target-integration.md` | 未接続/準備Future/Stop順序/単一実行の古い説明を修正 |
| `docs/plans/cursor-agent-ui-gap-plan.md` | 当時の観測と優先度は維持。冒頭から#141/ACPと現在のQA正本へ誘導 |
| `docs/research/*.md`（UI survey、IDE追補、capability matrix等） | 日付付き調査として保持。capability matrixはmainの方針訂正を同期。外部能力の再実測は行わず、#115専用成果を改変しない |
| `docs/development/{github-workflow,pr-automation,codex-execution-policy,github-projects,gui-coordination,ponytail}.md` | mainの運用変更を同期。製品CLIと開発Agentを混同しない。禁止対象/独立Session/GUI lease/保存互換を保持 |
| `docs/loop-engineering/README.md`、human-runbook、prompts、関連手順 | GUI手順と証跡規約を保持。再利用promptの入口へ現在の設計・実行規約を追記 |
| `docs/verification/README.md`、manual-verification README/matrix、runs、Case JSON/生成current.md | READMEの現行接続状況のみ訂正。過去GUI証跡・固定結果・生成物を編集せず、新規issue-142.jsonでGUI不要と検証手順を記録 |
| `scripts/gui-fixture/README.md`、workflow/loop説明・コメント | 開発検証のCLI/GUIであり製品transportの制約ではない。保持 |
| `src/main`、`src/test`、fixture README | 下記型と呼出元/先を確認。古いMCP/履歴説明、printパーサー/heuristicの適用範囲、テスト証拠の限界だけ修正 |
| 外部Project Instructions・アカウント共通設定 | このSessionに提供されたユーザー/環境指示以外の外部Project Instructionsは参照できていない。未参照範囲として残し、アカウント全体の指示・設定は変更しない |

## 旧判断からの訂正

| 旧説明・判断 | 起こりうる誤判断 | 修正後と保持する制約 |
|---|---|---|
| CLIのopaque process制御だけが製品 | ACP質問要求も文字列から推定する | 内部Agentはblack box、公式通信はACP優先。未確認APIを捏造しない |
| stdoutがAgent状態、processがsession | Resultで復元排他を解除、別タブへ遅い応答を表示 | tab/chat/run/processを分離。EDTのtoken検証、UI停止と物理終了を区別 |
| single activeHandler、新promptが既存runをkill | 別タブ送信で他タブを止める実装へ戻す | serviceは複数run、controllerはtab別。共有復元gateを維持 |
| SessionTabsは未接続、preparation Futureを所有 | 存在しない接続作業を再実装 | 接続済み。準備はrun.isActive、モデル取得Futureは別管理。GUI受入は別 |
| 停止中は新規送信も常に拒否 | 並行タブ機能を不要に直列化 | 複数準備可、復元は全準備/物理process終了まで拒否 |
| TTY picker失敗だから会話APIは使えない | ACP list/loadも不可能と決める | 過去のprint経路観測へ限定。本文保存未実装とID互換性未検証を維持 |
| MCP出力は未確認で常にraw表示 | 既存parserと整形UIを重複実装 | id:status既知形式は解析、未知出力はraw fallback |
| ライブ混在観測でdeduper全体が正しい | ACP chunkへheuristicを無条件流用 | print限定heuristic。全文置換・回帰テストを保持、#115で契約を再評価 |
| 過去のWorktree復元不一致が現在も無保護 | 解消済みの危険な復元を前提に調査 | 現在はISOLATED/未知rootを拒否。完全な分離先復元は未実装 |
| データ設計に本文保存を記載 | 再起動後のtranscriptがあると扱う | XMLはmetadata、開いた本文はメモリのみ。捏造復元をしない |
| UI/CLI gateと旧gap順序が現在も絶対 | ACP/IDE API経路を調べず機能不可にする | #141/#115とACP Firstへ誘導。日付付きUI/CLI証拠は保持 |
| 旧manual matrixが新結果の正本 | 過去passを新buildへ流用 | Case JSON/固定候補へ記録、過去runと生成物は維持 |

## 命名・抽象化の棚卸し

すべて現在の実装を説明する名前として維持する。一括rename、将来用interface追加、保存形式変更は行わない。

| ソースの型・契約 / 主なテスト | 維持理由 / #115で再評価する境界 |
|---|---|
| `AgentProcessService` / `AgentProcessListener` / `ModelOption` | 現行process/補助CLIとイベント配送。ACP connection/session/promptとの責務を確認し、必要な接続単位だけ分離 |
| `AgentRun` / `AgentRunTest` | 準備・停止・一度だけ終了の現行要求単位。ACP cancel/応答/切断の寿命との対応を検証 |
| `PreparedAgentTurn` / `TurnSettings` / `TurnWorkspace` / `TurnWorkspaceTest` | 不変のターン設定と復元root。ACP session設定/working directory対応とCLI fallbackを区別 |
| `SessionTabs` / `SessionTabsTest` / `SessionTabsExecutionTest` | tab IDとtoken、複数runの隔離。ACP ID互換性と再接続・復元を別途検証 |
| `AgentUiController` / `AgentTurnListenerFactory` / root/view/strip | 現行UI調整。ACP通知を直接Swingへ結合せず、既存EDT・token保護を残して実際の契約から接続 |
| `StreamJsonParser` / `StreamEvent` / `ToolCallPayloadParser` / `ParsedToolCall` / parser tests | print専用構造化JSON。ACP標準/拡張型を同じJSONとみなさない。未知/不正入力の防御を維持 |
| `AssistantChunkDeduper` / 同Test | 現行heuristicと全文置換の互換。ACP streamingの増分規則は別検証 |
| `ModelListParser` / `McpListParser` / 同Tests / `ModelFamilies` tests | 実CLI IDと表示解析の現行互換。ACPモデル/設定、MCP設定とIDE能力のtool公開を分ける |
| `PromptContextBuilder` / `MentionResolver` / mention tests | IDE APIとGitから現在のprompt文字列へ注入。ACP content/resource/IDE toolsとの対応を調査 |
| `TokenUsage` / `ContextUsageState` / usage tests | print入力と古い更新抑止。ACP usage/context stateを同じ値と推測しない |
| `WorkspaceOperationGate` / `SessionWorkspaceHistory` / `RestoreTarget` / `RestorePolicy` / `CheckpointService` / `GitSnapshotStore` / `FileRevertOperation` / 各restore・checkpoint tests | 共有復元排他、root由来、stale/未保存変更保護。ACPの編集/取消/終了でも安全境界を維持 |
| `ChatHistoryState` / `CheckpointHistoryState` / `AgentSettingsState` / settings tests | 既存XML・enumとIDの保存互換。ACPとprintのsession互換・移行可否を確認するまで補完/改名しない |
| `com.cursoragent.plugin`、PluginBrand、内部tool-window/notification ID、CLI flags | 公開登録・保存・実行契約の互換性。方針だけで変更する利益がなく、今回のscope外 |
| その他UI/layout/selector/Markdown tests、fixture/probe | transport名があっても実際の対象はUI安全性・描画・回帰。現行責務に適した名前として保持。GUI合格の代用にしない |

## 検証と引継ぎ

検証手順は [issue-142.json](../verification/changes/issue-142.json)。差分のKotlin実行tokenがdevelop基準と同一であること、相対リンク・AGENTS symlink・競合marker・Case schemaを確認し、workflow/loopの既存テストと `./gradlew test buildPlugin` を実行する。結果と独立レビューの対象SHAはPR/Issueへ記録する。Androidアプリ機能変更はなく、Lifecycle/Context/DB等への新しい実装影響はない。

文書・コメントの整合とmain同期のためGUIは不要。インストール/runIde/GUI操作は行わず、既存の未確認Caseはそのまま。writer停止後の独立レビュー・v2引継ぎ・develop統合・QA作成・main反映追跡はPMの既存運用へ渡す。
