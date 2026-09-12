# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
`AGENTS.md` is a symlink to this file, so any agent reading either name gets identical content.

## 最上位ミッションとACP First（2026-09-09 / #134）

[Project Mission](docs/project-mission.md)をプロジェクト全体の最上位判断基準、[ACP First](docs/architecture/cursor-integration.md)を今後の統合設計基準とする。対象はCursor **IDE内Agent panel** の機能・操作フロー・フィードバック・IDE統合であり、独立Agents Window専用UIやピクセル単位の複製を目的にしない。

新機能ごとに最新のCursor公式IDE内panel、JetBrains AI Assistant + Cursor ACP、さらにIntelliJ / Android Studio integration + IntelliJ MCP Server + 利用可能なMCP toolsを含む最も強い構成と比較し、本Pluginの同等以上UXと直接IDE統合による上積みを記録する。競合の既存能力を独自機能と呼ばない。完成像はAndroid開発のIDE・ビルド・デバイス・実行環境まで深く理解・操作できるCursor Agent環境である。

新規連携はACP標準 → Cursor ACP extensions → IDE APIs → MCP → CLI → 非構造出力解析の順で検討する。正確なIDE APIの直接利用を妨げず、CLI専用処理・合理的な補助/fallbackを残す。ACPをChat APIに限定せず、session・tool進捗・承認・質問・Plan/Todo・停止・context/usage等の構造化状態をUIへ対応付ける。各能力の提供可否は検証し、現方式Bの実装状態とACP Firstの設計方針を混同しない。Cursor内部のAgent機構を不必要に再実装せず、Cursor固有/ACP標準/IDE/MCP/CLI/UIの責務を分ける。

## Ponytail（full / #128）

通常の実装・監査・レビューは [Ponytail](https://github.com/DietrichGebert/ponytail) の `full` 相当で進める。
呼出元・呼出先と既存要件を理解してから、必要性 → 既存コードの再利用 → 標準ライブラリ →
ネイティブ機能 → 導入済み依存 → 直接的な実装 → 最小の新規実装、の順で判断する。
短さだけを理由に抽象化を削除せず、実際の参照・登録・保存互換性・テストで根拠を確認する。
trust boundary のvalidation、認証/認可、型安全性、データ整合性/損失防止、エラー処理、
アクセシビリティ、並行処理、必要なログと明示要件を優先し、既存テストを弱めない。
安全な変更候補がなければ維持する。大規模rewrite・一括整形・新規依存・ultraへの自動切替はしない。
この方針はplugin/hook未読込時も本ファイルから適用し、独立Sessionへの引継ぎにも含める。追加Agentの可否は[Codex実行規約](docs/development/codex-execution-policy.md)に従う。
既存のGitHub/GUI/承認規約は維持する。導入方法・有効状態・監査結果は
[導入と監査記録](docs/development/ponytail.md)を参照。

## 最新の能力比較（2026-09-08 / #114）

[機能・UI/UX・main/develop・非TTY CLIマトリクス](docs/research/cursor-agent-capability-matrix-2026-09-08.md)を追加調査/実装選択の入口にする。画像は公式headless資料にprompt内path読取経路があり、`--image`がhelpにないことだけで非対応と判断しない。Skills/Subagentsのheadless対応も公開済みだが、本プラグインのUI/event接続と新機能live受入は別途必要。過去のCLI即時編集実測と事後Revert、#66の名前取得待ちは維持する。方式Bは現行実装の記録であり、新規連携の設計方針は上記ACP Firstを優先する。以下の過去記録の「非対応」は観測時点・経路に限定する。

## GitHub-first collaboration (2026-09-06, #31)

[GitHub Work Management Rules](docs/development/work-management.md)に従い、Issueは具体作業、Projectは全体管理、Milestoneは到達目標、native Relationshipは実際の依存/分解に使う。作成/triage時にProject登録・Milestone選定・関係判定・Status/Priority表示を確認する。Standaloneは正常であり、#1等へ分類目的で接続しない。

[Git Governance Audit](docs/development/git-governance-audit.md)を開始・統合終了時の判断に適用する。主要Milestone/High Impact/構造問題と10 meaningful mergesを確認し、既存ルールの削除・統合・単純化を先に検討する。監査状態は各回の監査Issueを正本とし、毎PRの全体監査や二重台帳を追加しない。

開発Agentの追加・委譲前に[Codex実行規約](docs/development/codex-execution-policy.md)を確認する。GPT-6 Astraがメインの場合は子Agentの生成・委譲を禁止し、通常のToolと合理的な独立top-level Session間連携で進める。他モデルには本規約による禁止を適用しない。正本の最新版・統合状態は[#135](https://github.com/shinma06/cursor-in-android-studio/issues/135)を参照する。製品のCursor Subagent対応範囲とは区別する。

**Apply this to every change request, even when the user says nothing about Git/GitHub.**
The previous no-PR/direct-main and GUI-before-any-merge policies are superseded. Read
[the GitHub workflow](docs/development/github-workflow.md) before implementation.
Every code, docs, or configuration task needs an existing/new Issue, an ownership claim,
its own Issue-numbered branch and worktree, and a PR. Never commit or push directly to main.
Read-only advice/review does not need a new Issue. Preserve unrelated local work.

1. Read this file, requirements, the Project roadmap and relevant Milestone, target Issue/comments, and open PRs. Inspect status,
   worktrees, run `git fetch --prune origin`, and compare HEAD with the intended origin/develop or origin/main base. Search before creating an Issue.
2. Claim scope with a unique owner session, files, base SHA, dependencies, reviewer, GUI need,
   and next action. Unreleased claims do not expire with time. Follow the conflict/takeover rules
   in the workflow; no concurrent writers to the same worktree.
3. Normally create `<codex|claude|cursor>/<issue>-<slug>` in a separate worktree from origin/develop,
   clear its initial upstream (tooling bootstrap uses origin/main), and run `bash scripts/workflow/bootstrap.sh`.
   Parallel agents implement assigned independent Issues there and push their own branches.
4. Open a Draft PR early; update the Issue at each handoff/state change.
   Follow [PR automation](docs/development/pr-automation.md): stop the original writer and enroll
   the clean Issue worktree with the trusted-main `agent_loop.py enroll` command. The scheduled
   coordinator then owns fixes, independent re-review, Issue completion and verified Issue-branch cleanup (never main/develop).
   `Agent review` is required along with CI. Do not resume editing an enrolled branch concurrently. Record independent
   review by session and SHA. GPT coordinates integration through GitHub PR merge only.
5. GUI operations, installs, restarts and runIde require a host-wide lease under
   [GUI coordination](docs/development/gui-coordination.md). Only its designated GPT session
   (or human handoff) operates the desktop. Other tasks continue implementation/tests/review.
   Worktree isolation does not isolate IDE state. Use disposable fixtures and identified builds.
6. Run tests and independent review, reconcile the target branch and verify current checks.
   Develop permits pending/blocked/failed GUI with complete Case/fix tracking. Main requires every
   required Case of the entire fixed candidate to pass on its identified build. See docs/verification/README.md. Never force push, bypass hooks or invent a GUI pass.
   Close Issues only when acceptance is complete; otherwise record blockers, next action and ownership.
   Merge and cleanup are separate completion checks: verify remote/local/tracking refs and owned worktrees,
   or record the retained resource, owner and retry trigger under [branch hygiene](docs/development/pr-automation.md#ブランチ残存の判定と完了確認).

The [loop protocol](docs/loop-engineering/README.md) defines GUI cases and evidence;
[the runbook](docs/loop-engineering/human-runbook.md) is the human entrypoint.
`.agents/skills/` and `.claude/skills/` route start/finish to the same rules;
Cursor follows this file and `.cursor/rules/loop-engineering.mdc`.
GitHub Issues/PRs are the shared source of truth; local notes are supporting evidence.
Existing user authorization applies. Routine work within scope needs no repeated confirmation.

**Server protection active (2026-09-06, #33):** the user made the repository public.
Ruleset `main-pr-required` (ID 22368189) now requires PRs, successful `test` and `PR policy`
checks against an up-to-date base, and resolved review conversations; main deletion and force
push are blocked, with no bypass actors. Same-account agents still record independent session
reviews; required GitHub approval count is 0. See the workflow for settings and verification.

全open `type:qa` は[人間向け試験ドキュメント](docs/verification/human-qa.md)への本文リンクを必須とする。作成/引継ぎ時は前提・番号付き手順・期待結果・記録方法を揃え、Project #2の「QA — 人間向け試験」への登録/表示をreadbackする。

## develop統合とmain昇格（2026-09-07 / #83、ユーザー方針）

通常実装はdevelop向けIssue PRへ。必要テストと独立コードレビューが通り、
[確認マトリクス](docs/verification/README.md)に必要Case・手順・期待結果・GPT/人間の状態・次の操作があれば、
GUIのpending/環境blocked/製品failでもdevelopへ統合できる。製品failは専用修正Issue/PRへ追跡する。
未解決コード指摘やテスト失敗は許可しない。develop統合後はQAを先に作成・双方向link/readback確認し、実装受入完了の元Issueをcloseする。GUI不要でもmain未反映はQAマトリクスへ引継ぐ。失敗時はcloseせず冪等再試行。QA Case/QA Issueの完了やmain反映とは区別し、親tracking/researchを子PRだけでcloseしない。Issue命名・3軸labelはgithub-workflow.mdの#96規約に従う。
mainは固定develop候補全体をGPT/人間が適切に確認したpromotion PRをmerge commitで統合する。
過去buildのpass、1 Caseだけのpass、未確認commitの混入はAcceptance gateが拒否する。
GUI不要docs/toolingだけは理由とCLI検証を記録したmain PRも可能。main/developはcleanup禁止。
初期9 Case（製品/probe 7件と親#73のCUA 2件）の入口は [今回の確認一覧](docs/verification/current.md)。JSONを正本に再生成し、二重編集しない。
既存enrollmentのowner/sourceやPAUSED heartbeatは自動変更しない。公開引継ぎにはopaque IDを使い、
host/sourceはlocal registryだけへ保存する。#83 bootstrapとGitHub設定順序は運用文書を参照。

## UIの言語と見た目の方針

このプラグインは**日本語での利用を前提**とする。CursorからUIを移植するときは、
見た目だけでなく、その文言を理解して判断する重要性に応じて言語を選ぶ。

- 設定項目、選択肢の説明、注意点、エラー、確認文、ヘルプなど、意味の理解が操作判断に
  関わる文章は、自然で簡潔な日本語にする。Cursorにある長い英語説明をそのまま持ち込まない。
- 常時見える短いラベルは、Cursorの外観と慣れた呼び方を尊重する。`Agent` / `Plan` / `Ask` /
  `Auto`、モデル名、`MCP`、アイコン、`@` / `/`、ユーザーが指定したplaceholderは、
  無理に日本語へ置き換えない。英語を残す場合も、詳しい意味は日本語のtooltipや設定内で補う。
- 通常の入力画面は簡潔に保つ。詳しい設定や編集の注意点は「…」のチャット設定・設定画面に
  まとめ、説明文を入力欄の下に常時追加しない。警告文の追加でCursorとの外観差を広げない。
- 「標準」を「毎回必ず事前確認」などと訳して、CLIが保証しない保護を示唆しない。
  即時編集と事後Revertの意味を日本語で正確に説明する。
- CLIフラグ・保存済みenum/ID・モデル固有名・パス・生ログ・ユーザー/モデルの会話本文は
  翻訳の対象にしない。新規/変更箇所とその直近の設定導線から整え、無関係な画面の一括置換は避ける。

## What this is

The product name is **Cursor in Android Studio** and the repository/artifact slug is
`cursor-in-android-studio`. Keep `com.cursoragent.plugin`, existing state/storage names,
and the internal `Cursor Agent` tool-window/notification IDs stable for upgrades.
Use `PluginBrand.NAME` for runtime product labels. Historical evidence keeps its original names.

An Android Studio (IntelliJ Platform) client for Cursor Agent inside the IDE. The target integration
is ACP First, with native IDE APIs, MCP and supplementary CLI paths as needed; see the mission and
architecture documents above. Cursor Agent internals remain a black box, accessed through official interfaces.

The default transport uses `agent -p --output-format stream-json` with a Swing/JBUI chat UI (方式B).
Develop also has explicit ACP selection for new conversations (#147); fixed-build GUI acceptance
remains in #152. See the current implementation document for transport scope and limitations.
`docs/cursor-agent-plugin-requirements.md` records detailed requirements and implementation status
under the mission and ACP First policy. Read these before adding features.

## 開発・検証の入口

[全体設計](docs/architecture/README.md)から担当境界を確認し、[現行実装](docs/architecture/current-implementation.md)を読んで変更する。設計判断と履歴を整理するときは[知識の正本](docs/architecture/knowledge.md)を使う。旧checkout由来の指摘は最新baseと照合し、修正済みなら撤回する。

[Change Impact](docs/development/change-impact.md)をCI・hook・coordinator・ZIP生成で共通利用する。push前は `python3 scripts/workflow/change_impact.py --run-tests`。混在/unknownの検証、明示buildとGUI、独立review/Acceptance gateは維持し、`--no-verify`・保護無効化を使わない。

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
python3 scripts/workflow/change_impact.py --run-tests
./gradlew test          # 製品・Kotlinテスト変更のJUnit
./gradlew buildPlugin   # 標準Plugin ZIPの明示生成
./gradlew runIde        # GUI leaseが必要
```

Gradle自体はJDK17+、KotlinはJDK21 toolchain。`gradle.properties`の`platformPath`にローカルAndroid StudioのContentsを指定する。CIは取得したSDKのpathを同じ`local()`へ渡す。SDK取得/ZIPの詳細と過去の回避理由は[現行実装のビルド](docs/architecture/current-implementation.md#ビルドと実行環境)。bootstrap/Gradleが `.githooks` を設定し、pre-pushはbranch/dirty/fast-forward保護後に共通分類のテストを実行する。

開発・検証はPro/Teamsを使う。Free tierの旧resource_exhaustedを通常作業のblockerとして再採用せず、Freeが明示的に再導入された場合だけ再評価する。インストールはSettings → Plugins → ⚙ → Install Plugin from Disk、ZIP選択後restart。[配布ZIPとbuildの識別](docs/development/plugin-zip-delivery.md)とGUI leaseに従う。

## 変更時に保持する制約

- tab UUID / chat ID / run token / provider session / OS processは別の寿命。所有タブへ配送し、EDT上でtoken/generation/disposeを再照合する。Stopはそのrunだけ、`killActiveProcess()`は全run cleanup用。
- editor/VFS/Terminal APIの取得はEDT、Git/checkpoint/process起動は背景へ分ける。Stop後の遅い起動も破棄する。Terminalはoptional登録とLinkageErrorの防御を保持する。
- printのResultとACP prompt終端を物理終了と同一視しない。準備/実行と復元を排他にし、ACPの不確定終了はproject寿命中の復元を拒否する。ISOLATED/由来不明root、root外、後続編集、未保存内容を推測で復元しない。
- 現printは標準permissionでも即時編集が起こる。Diff/Revertは事後操作。ACPのpermissionを全書込みの事前承認保証にせず、来歴のないACP差分へRevertを追加しない。
- printのdeduperは全文置換を返すheuristic。ACPの正当な反復deltaへ流用しない。不正/未知wireを防御的に扱い、採取済みcompletedと推定startedを同じ証拠強度にしない。
- ACPは新規会話の初回送信前だけ選択可。固定settingsを使い、非対応設定を無視しない。要求への一度だけの返答、取消/拒否/切断を保持し、失敗promptを自動再送しない。
- 保存XML/enumと既定`PermissionMode.ASK_EVERY_TIME`、Plugin ID、内部tool-window/notification IDを維持。print履歴はmetadataのみ、開いたviewはメモリ内。本文永続化は#44、ACPの旧print ID互換は未保証。本文表示・provider再開・Revert可否は別判定。
- モデルは実CLI/provider IDを保持し、未知alias/Context容量を推測で発明しない。`New Agent`と#66の命名取得待ちを維持する。画像/Skills/subagentsは公式能力、UI実装、live受入を分ける。
- Markdownのraw HTMLをそのまま描画しない。秘密・raw error・非公開wireを公開ログや恒久指示へ移さない。#146の公開承認待ちとownerを維持する。
- Case JSONは受入/証拠の正本。旧MV/run・過去buildのpass・合成テスト成功を新しい固定buildのGUI passにしない。未確認main/GUIは既存QAへ引き継ぐ。

制約の理由・実source/test・未知範囲は[現行実装](docs/architecture/current-implementation.md)へ。恒久知識へ入れるのは将来の判断に必要で、適用範囲と第三者が追える根拠/限界があるものだけ。セッションの作業順・修正報告はIssue/PRに記録する。

## 履歴の参照

旧spike・foundation review・PR #18・UI調整の全文は[変更前の固定版](https://github.com/shinma06/cursor-in-android-studio/blob/4d1514d8fa6c020d41ad9c0205b9ea24268bef57/CLAUDE.md)に保持する。現在の説明を上書きする指示ではない。段落群ごとの維持/移動/置換理由とsuperseded判断は[知識の正本](docs/architecture/knowledge.md)から追える。新しい履歴の複製ファイルは作らない。

### Verified CLI behavior

`AgentNotificationService`の既存コメントからの入口。print即時編集の版付き観測は[固定版のCLI記録](https://github.com/shinma06/cursor-in-android-studio/blob/4d1514d8fa6c020d41ad9c0205b9ea24268bef57/CLAUDE.md#verified-cli-behavior-from-a-live-spike-2026-09)、現在の適用範囲は[現行実装](docs/architecture/current-implementation.md#イベント補助cli保存)へ。ACPへの一般化や新buildの実測済み判定には使わない。
