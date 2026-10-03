# Ponytail の導入と全体監査（2026-09-09 / #128）

目的は機能を保ちながら必要十分な実装へ整理すること。通常は `full` を使い、
短さより既存の安全性・振る舞い・レビュー可能性を優先する。

## 導入

[公式README](https://github.com/DietrichGebert/ponytail#install)のCodex plugin方式を使用。
確認したupstreamは4.9.0、commit `356918eba965ee1eac64bd3a7f0dd02108350de5`。
Codex CLI 0.153.4、非対話shellのNode.js v26.8.1で確認した。Nodeは開発agentのhook用で、
製品のGradle依存や配布ZIPには追加していない。

```sh
codex plugin marketplace add DietrichGebert/ponytail --ref 356918eba965ee1eac64bd3a7f0dd02108350de5
codex plugin add ponytail@ponytail
codex plugin list --json
```

ユーザー設定の `marketplaces.ponytail` と `plugins."ponytail@ponytail"` が追加され、
`installed: true` / `enabled: true` / version 4.9.0を確認した。既存plugin設定は維持。
marketplace自体は上記SHAに固定したが、upstreamのplugin entryは `ref: main` を持つ。
plugin更新時まで内容が固定されるとは断定せず、今回はインストールされた全hookと6技能の
内容が取得済みSHAと一致することを照合した。更新時も再確認する。

plugin manifestは `skills/` と `hooks/claude-codex-hooks.json` を参照する。
後者のイベントは SessionStart / SubagentStart / UserPromptSubmit の3つ。
READMEの「two hooks」という説明より、実際のmanifestとhookファイルを正とする。
hookはNode標準機能でmode状態を保存し、指示を出力する。MCPやネットワーク処理の追加はない。

**この実行の状態:** plugin導入済み。自動hookの信頼設定は自動承認レビューに拒否され、未設定。
第三者hookの永続自動実行は導入とは別の明示承認が必要、という理由だった。
信頼hashを手書きしたり、別経路でhookを実行したりして回避していない。
公式手順はCodexの `/hooks` で内容を確認して信頼し、新しいタスクを開始するもの。
Desktopのplugin読込は公式READMEが再起動を案内しているが、既存タスクを中断する再起動は行っていない。

現在の親agentと独立監査agentは、公式 `ponytail` / `ponytail-audit` / `ponytail-review` の
SKILL.mdを明示的に読み込んで `full` の判断順序を適用した。自動hook発火の証拠とは区別する。
今後の本repositoryでは `AGENTS.md` → `CLAUDE.md` の既存共通入口にも方針を置き、
hook未読込時も適用する。既存Cursor ruleやClaude skillを重複コピーしない。
新しいCodexタスクでplugin技能が表示されたら `@ponytail full`、`@ponytail-audit`、
`@ponytail-review` を使用できる。監査/review技能自体は所見のみを返すもので、自動修正コマンドではない。

## 調査範囲とベースライン

基準: `origin/develop` = `9e3cb85d192831e5a447086abb3345993e647049`。開始時clean、HEADとremote一致。
Issue #1・既存Issue/PR/claimとworktreeを確認し、#128の独立worktreeで実施した。
#127のZIP配布/運用scripts/READMEは別ownerのため読み取り監査のみ。

| 対象 | 確認した構成・データフロー |
|---|---|
| 製品/build | Kotlin 2.3.0、Gradle 8.13、JDK 21 toolchain、IntelliJ Platform plugin 2.10.5、local Android Studio SDK。Web/React/DBなし |
| エントリ/UI | plugin.xml → ToolWindowFactory → RootPanel → タブ別Controller/Composer/Timeline。Swing/JBUI、EDT更新 |
| 実行/応答 | prompt/context → prepareTurn/AgentRun → OSProcessHandler → StreamJsonParser → listener → deduperの全文置換 → Markdown表示 |
| 状態/復元 | SessionTabs・run token・履歴metadataのXML保存。checkpointはGit snapshotと開始時root/mode。復元はプロセス排他とwrite境界で再検証 |
| 共通機能 | ModelFamilies・CLI一覧parser・mention resolver・色/寸法・focus/popup制御。設定と実行のCLI検出候補が重複 |
| 依存 | Gson 2.11.0（stream JSON）、CommonMark 0.30.0（Markdown）、JUnit 5.11.0。Terminalはplugin.xmlのoptional依存 |
| 検証/CI | JUnit・Python unittest、CI/test・PR policy・Agent review・Acceptance gate、pre-push test/build。専用lint/typecheck taskなし。Kotlin型検査はcompileKotlin/compileTestKotlin |
| 指示 | CLAUDE/AGENTS symlink、.agents/.claude start/finish skills、Cursor rules、Claude settings、GitHub workflow。Copilot専用指示なし |

変更前: `./gradlew test buildPlugin --console=plain` 成功（JUnit 165件、失敗/skip 0）。
`python3 -m unittest discover -s scripts/workflow -p 'test_*.py'` 148件、
同 `scripts/loop` 11件成功。ProcessAdapter・file chooser APIの非推奨、既存テストの不要な `!!`、
Gradle 9非互換の警告は変更前から存在。lint導入や無関係な警告修正は行わない。

## Ponytail audit と採否

削除候補は宣言だけでなく、製品・tests・fixture・plugin登録・履歴・呼出元を照合した。
public修飾なしのKotlin宣言にもpublic既定値はあるが、以下は外部公開APIや保存型として使われていない。

| 分類 | 対象・変更 | 根拠 |
|---|---|---|
| delete | CursorAgentPlugin、ChatMessage型階層を削除 | 初期雛形。plugin.xml登録/実装側参照なし。実際のentrypointと会話表示は別実装 |
| delete | clearTimeline/showEmptyState、sectionPadding、CheckpointService.isAvailableを削除 | 呼出なし。新規チャットは別タブのTimelineを生成し、空状態の初期表示は維持 |
| delete | CheckpointService.restore、DiffViewerHelper.revertFileContentを削除 | 未使用Boolean wrapper。現行呼出は理由付きResultと記録済みtargetを使う。復元検証と説明は維持 |
| simplify | AssistantMessageBubbleのappendContent/StringBuilder/refreshLabelを削除 | 全call siteはdeduperが返す全文をsetContentへ渡す。HTMLは受け取った全文から直接生成 |
| reuse | 設定側のdetectAgentExecutableを実行serviceでも使用 | 重複候補list/loopを削除。候補順、canExecute、手動指定優先、不正指定時のfallback、最後のPATH検索を維持 |

製品コードは10ファイルで6行追加・80行削除、差引74行削減（空行/コメント込み）。
2ファイル、5型（未登録class 1、未使用sealed interface 1と子data class 3）、
7未使用メソッドと描画用private helper 1、重複テキストbuffer 1を削除。依存削減は0。
補助型の新設はない。変更を守る全文置換テスト1件は既存テストファイルに追加した。
導入説明/監査/必要Case JSONはこの製品LOCと分けて数える。

## 維持した設計・残る候補

- Gson/CommonMark: 両方実利用。独自JSON/Markdown処理への置換は複雑性と危険を増やす。
- Markdownのcustom HTML renderer: CommonMark 0.30.0公式sourceの `escapeHtml(true)` はHTML blockを
  `<p>`で囲む。現在の表示と厳密に等価でないため変更しない。採用するならblock表示の要件/GUI確認が必要。
- FileAccess、RestoreTarget/Policy、複数段のvalidationとcopyRecord: VFS/write lock、実path/symlink、
  変更後ファイル/未保存編集、可変listのaliasによるデータ損失を防ぐ境界。短縮目的の統合はしない。
- AgentRun/WorkspaceOperationGate/SessionRunToken/ContextUsageState: OS process終了・世代・タブの分離を保証。
  画面完了と物理終了は異なるため状態を重複と扱わない。
- MessageTextPaneの高さcache、popup renderer/focus/disposer、タブ別view保持: 性能・caret/scroll・実GUIの制約がある。
  変更するなら再現計測と操作証拠を先に用意する。
- parserの未知形状/旧payload対応、ModelFamiliesの曖昧alias: CLI境界の互換処理。#116の実測契約確定前に削除しない。
- ZIP/QAのreadbackやgate: 別ownerの作業範囲であり、trust/競合境界の検証を担う。横断整理は別Issueで判断する。

## 最終検証

関連JUnit（描画/parser/service/settings/session）は成功。全文置換テストは初期値、累積文字列、
訂正、HTML風テキスト、空文字への更新をEDT上の実componentで確認する。
最終 `./gradlew test buildPlugin --console=plain` は成功（JUnit 166件、失敗/skip 0）。
Kotlin型検査成功、専用lint taskなし。`git diff --check` とCase JSON validatorも成功。
独立Ponytail diff reviewと正当性review、CI/引継ぎの固定SHA結果はPRへ記録する。
必要GUIは [issue-128.json](../verification/changes/issue-128.json) の2 Caseで追跡し、未実施をpassにしない。
