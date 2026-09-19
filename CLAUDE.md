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

## 変更作業の共通契約

コード・文書・設定の変更は、Issue・所有claim・専用のIssue番号付きbranch/worktree・PRで進める。読み取りだけの助言/レビューは新Issue不要。main/developへ直接commit/pushせず、無関係な編集と未解放claimを保全する。通常の変更はorigin/developから開始し、GUI不要toolingのmain向け変更は[GitHub workflow](docs/development/github-workflow.md)の条件を使う。

開始/再開は[start-work](.agents/skills/start-work/SKILL.md)、検証/引継ぎ/終了は[finish-work](.agents/skills/finish-work/SKILL.md)を入口にする。`.claude/skills/`の同名Skillも同じ契約、Cursorは本ファイルと`.cursor/rules/loop-engineering.mdc`に従う。作業別の参照条件と承認/再開判断は[GitHub workflow](docs/development/github-workflow.md#参照する範囲と進行判断)に集約する。確認済みの同一資料を段階ごとに読み直さず、変更や矛盾がある箇所を確認する。

- [Work Management](docs/development/work-management.md): Issueは具体作業、Projectは全体管理、Milestoneは到達目標、native Relationshipは実際の依存/分解。作成・triage・終了時に登録/Status/Priority/関係をreadbackし、分類だけの架空の親を付けない。
- [Codex実行規約](docs/development/codex-execution-policy.md): 追加Agent/委譲前に確認。GPT-6 Astraの子Agentは禁止し、合理的な独立top-level Session連携だけを使う。他モデルの扱いと最新版は#135を参照し、製品のCursor Subagent対応と混同しない。
- [PR automation](docs/development/pr-automation.md): writer停止・clean確認後にtrusted-mainのv2 enrollへ引き継ぐ。独立sessionによる固定HEAD/baseレビューと、最新のtest / PR policy / Agent review / Acceptance gateの成功が必要。enrollmentは完了ではなく、PM/coordinatorが実行・結果・次担当を追跡する。既存owner/source/registry/PAUSED heartbeatを勝手に変更しない。
- [GUI coordination](docs/development/gui-coordination.md): desktop操作・install・restart・runIdeはhost共通leaseを持つ指定GPTまたは人間だけが行う。worktree分離はIDE状態を分離しない。使い捨てfixtureとロードしたbuildを識別する。
- [Git Governance Audit](docs/development/git-governance-audit.md): 開始・統合終了時に既存trigger（主要Milestone/High Impact/構造問題/10 meaningful merges）を確認する。既存監査Issueを正本にし、毎PRの全体監査や二重台帳を増やさない。

## 統合と完了

必要テスト・独立レビュー・Case追跡が揃えば、GUI pending/環境blocked/製品failでもdevelopへsquash統合できる。製品failは専用修正Issueへ追跡する。未解決コード指摘やテスト失敗は許可しない。元実装Issueのcloseは全受入とQAへの双方向引継ぎ/readback後。親tracking/researchやQAを子PRだけでcloseしない。

main promotionは固定develop候補の**全commit・全必要Case**を識別した同一buildで確認し、merge commitで統合する。具体的なGUI不要理由とCLI検証があるdocs/toolingはmain向けPRも可能。[確認マトリクス](docs/verification/README.md)のJSONを正本とし、生成Markdownは二重編集しない。過去build・部分pass・合成テストを新候補全体のpassにしない。

全open QAには[人間向け試験ドキュメント](docs/verification/human-qa.md)への本文リンク、前提/手順/期待結果/記録方法、Project #2のQA表示readbackが必要。GUI不要でもmain未反映はQAに残す。初期Caseの入口は[今回の確認一覧](docs/verification/current.md)、GUIの詳細は[loop protocol](docs/loop-engineering/README.md)と[human runbook](docs/loop-engineering/human-runbook.md)。

mergeとcleanupは別に確認する。[branch hygiene](docs/development/pr-automation.md#ブランチ残存の判定と完了確認)に従い、所有する停止済みclean Issue branch/worktreeとremote/local/tracking refを照合する。main/master/developは削除しない。残る資源は理由・担当・再開条件を記録する。force push・hook/保護回避は禁止。GitHubを共有の正本とし、ローカルパス/host/秘密は公開せずprivate registryへ保存する。

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

製品コードの責務・イベント・保存を変更するときは[全体設計](docs/architecture/README.md)と[現行実装](docs/architecture/current-implementation.md)の該当箇所から担当境界を確認する。設計判断と履歴を整理するときは[知識の正本](docs/architecture/knowledge.md)を使う。旧checkout由来の指摘は最新baseと照合し、修正済みなら撤回する。

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
- 保存XML/enumと既定`PermissionMode.ASK_EVERY_TIME`、Plugin ID、内部tool-window/notification IDを維持。旧print履歴XMLはmetadataのみ。PRINT/ACP本文は#44の[会話保存契約](docs/architecture/conversation-persistence.md)に従うproject単位JSONへ保存する。実IDE再起動はQA #259で追跡し、ACPの旧print ID互換は未保証。本文表示・provider再開・Revert可否は別判定。
- モデルは実CLI/provider IDを保持し、未知alias/Context容量を推測で発明しない。`New Agent`と#66の命名取得待ちを維持する。画像/Skills/subagentsは公式能力、UI実装、live受入を分ける。
- Markdownのraw HTMLをそのまま描画しない。秘密・raw error・非公開wireを公開ログや恒久指示へ移さない。#146の公開承認待ちとownerを維持する。
- Case JSONは受入/証拠の正本。旧MV/run・過去buildのpass・合成テスト成功を新しい固定buildのGUI passにしない。未確認main/GUIは既存QAへ引き継ぐ。

制約の理由・実source/test・未知範囲は[現行実装](docs/architecture/current-implementation.md)へ。恒久知識へ入れるのは将来の判断に必要で、適用範囲と第三者が追える根拠/限界があるものだけ。セッションの作業順・修正報告はIssue/PRに記録する。

## 履歴の参照

旧spike・foundation review・PR #18・UI調整の全文は[変更前の固定版](https://github.com/shinma06/cursor-in-android-studio/blob/4d1514d8fa6c020d41ad9c0205b9ea24268bef57/CLAUDE.md)に保持する。現在の説明を上書きする指示ではない。段落群ごとの維持/移動/置換理由とsuperseded判断は[知識の正本](docs/architecture/knowledge.md)から追える。新しい履歴の複製ファイルは作らない。

### Verified CLI behavior

`AgentNotificationService`の既存コメントからの入口。print即時編集の版付き観測は[固定版のCLI記録](https://github.com/shinma06/cursor-in-android-studio/blob/4d1514d8fa6c020d41ad9c0205b9ea24268bef57/CLAUDE.md#verified-cli-behavior-from-a-live-spike-2026-09)、現在の適用範囲は[現行実装](docs/architecture/current-implementation.md#イベント補助cli保存)へ。ACPへの一般化や新buildの実測済み判定には使わない。
