# Cursor in Android Studio

**開発ルール:** Gitへの言及がなくても [Issue → worktree → PR](docs/development/github-workflow.md) を必須とします。main直接commit/pushは禁止。GUIは[ホスト単位の予約](docs/development/gui-coordination.md)で直列化し、実装は並列化します。

Cursor **IDE内Agent panel** の開発体験をAndroid Studioへ統合するIDE Agent Client。
[最上位ミッション](docs/project-mission.md)と[ACP First](docs/architecture/cursor-integration.md)に従い、主要な構造化通信はACPを優先し、IDE API / MCP / 補助CLIを組み合わせる。

現在は互換CLI（`agent -p --output-format stream-json`）を既定とし、新しい会話の「… → 接続方法」でACPを明示選択できる。ACPのtext・ツール状態・要求返答・停止をnative panelへ接続した。実Cursorの固定build GUI受入は未完了で、[実装範囲と制約](docs/architecture/current-implementation.md#acp接続147)を参照する。

> **新しくこのプロジェクトに参加するエージェント/開発者へ**: このREADMEは概要のみです。
> 開発を始める前に必ず次の2つを読んでください。
> 1. **[`CLAUDE.md`](CLAUDE.md)**(`AGENTS.md`はこのファイルへのシンボリックリンク) — アーキテクチャ、ビルド手順、既知の制約・落とし穴
> 2. **[GitHub Issues](https://github.com/shinma06/cursor-in-android-studio/issues/1)** — 進捗の一次情報源。担当と独立レビューは各Issueのclaimで確認する。[Codex実行規約](docs/development/codex-execution-policy.md)とPonytail fullに従い、GUIは指定担当だけが操作する。作業前に必ずIssueの状態と直近コメントを確認し、着手する際は "Starting work" のコメントを残してから始めること(重複作業・競合pushを避けるため)
>
> 詳細な機能要件は [要件定義書](docs/cursor-agent-plugin-requirements.md) を参照。
>
> **GUI QA**: [現在のCase管理と固定候補の手順](docs/verification/README.md)を参照する。[旧手動マトリクス](docs/manual-verification/matrix.md)は履歴・詳細であり、過去buildの結果を最新候補へ転用しない。

責務・正本・変更先は [全体設計の入口](docs/architecture/README.md) から確認してください。製品、知識、テスト、運用、配布の既存資料へ進めます。

## ループ開発

[人間向けの開始手順](docs/loop-engineering/human-runbook.md) / [GPT・Claude・Cursorの役割と開発ループ](docs/loop-engineering/README.md)。GPTへIssueを指定すると、GUIで再現→修正→Claudeレビュー→同じ操作で再確認する。

## セットアップ

```bash
git clone https://github.com/shinma06/cursor-in-android-studio.git
cd cursor-in-android-studio
export JAVA_HOME="$("/usr/libexec/java_home" -v 17)"   # Gradle自体はJDK17+が必要(Kotlinコンパイル自体はJDK21ツールチェーンを自動取得)
./gradlew buildPlugin
```

## 前提

- Android Studio 2026.1 以降(build 261+)
- `agent` CLI(`~/.local/bin/agent` 等)がインストール・認証済み(`agent login` または `CURSOR_API_KEY`)
- `gradle.properties` の `platformPath` をローカルのAndroid Studio SDKパスに合わせて設定(マシン依存、コメントに例あり)

## Android Studio へのインストール

1. **Settings → Plugins → ⚙ → Install Plugin from Disk...**
2. `build/distributions/cursor-in-android-studio-<version>.zip` を選択
3. Restart IDE
4. **View → Tool Windows → Cursor in Android Studio**

旧名称「Cursor Agent」からの更新でも、プラグインIDと設定・履歴の保存先は共通です。

サンドボックスでの動作確認: `./gradlew runIde`

## 開発

- UI比較の証跡: [Cursor Agent UI 閲覧調査（2026-09-05）](docs/research/cursor-agent-ui-survey-2026-09-05.md)
- 現在の設計・移行順: [ACP First再評価 #141](https://github.com/shinma06/cursor-in-android-studio/issues/141) / [契約・機能分類 #115](https://github.com/shinma06/cursor-in-android-studio/issues/115)。[旧UI差分計画](docs/plans/cursor-agent-ui-gap-plan.md)と[UI tracking #19](https://github.com/shinma06/cursor-in-android-studio/issues/19)は既存受入・経緯として併用する。
- ビルド/テストコマンド、アーキテクチャ、既知の制約: [`CLAUDE.md`](CLAUDE.md)
- 機能要件・優先度・検証済み事項: [要件定義書](docs/cursor-agent-plugin-requirements.md)
- 進捗・タスク管理: [GitHub Issues](https://github.com/shinma06/cursor-in-android-studio/issues)(`CLAUDE.md`や要件定義書より新しい場合がある — 実装状況の最終的な確認先はここ)

- Android Studioで実際に確認した範囲と追加タスク: [実機UI追補](docs/research/android-studio-ui-followup-2026-09-05.md)
