# Cursor Agent Plugin

Android Studio 向け Cursor Agent 統合プラグイン。`cursor-agent` CLI をサブプロセスとして起動し、
`stream-json` 出力をパースして独自のSwing/JBUI製チャットUIに描画する(CLIをブラックボックスとして
ラップする方式)。

> **新しくこのプロジェクトに参加するエージェント/開発者へ**: このREADMEは概要のみです。
> 開発を始める前に必ず次の2つを読んでください。
> 1. **[`CLAUDE.md`](CLAUDE.md)**(`AGENTS.md`はこのファイルへのシンボリックリンク) — アーキテクチャ、ビルド手順、既知の制約・落とし穴
> 2. **[GitHub Issues](https://github.com/shinma06/cursor-agent-plugin/issues/1)** — 進捗の一次情報源。GPTが進行・実装・GUI検証と統合、Claude Proが独立レビュー、Cursor ProがGUI検証課題を担当する。作業前に必ずIssueの状態と直近コメントを確認し、着手する際は "Starting work" のコメントを残してから始めること(重複作業・競合pushを避けるため)
>
> 詳細な機能要件は [要件定義書](docs/cursor-agent-plugin-requirements.md) を参照。
>
> **GUI QA（Computer Use優先、人間による補完）**: 実画面で確認する項目は [docs/manual-verification/matrix.md](docs/manual-verification/matrix.md) に一覧化する。確認前に Branch 列を参照すること。

## ループ開発

[人間向けの開始手順](docs/loop-engineering/human-runbook.md) / [GPT・Claude・Cursorの役割と開発ループ](docs/loop-engineering/README.md)。GPTへIssueを指定すると、GUIで再現→修正→Claudeレビュー→同じ操作で再確認する。

## セットアップ

```bash
git clone https://github.com/shinma06/cursor-agent-plugin.git
cd cursor-agent-plugin
export JAVA_HOME="$("/usr/libexec/java_home" -v 17)"   # Gradle自体はJDK17+が必要(Kotlinコンパイル自体はJDK21ツールチェーンを自動取得)
./gradlew buildPlugin
```

## 前提

- Android Studio 2026.1 以降(build 261+)
- `agent` CLI(`~/.local/bin/agent` 等)がインストール・認証済み(`agent login` または `CURSOR_API_KEY`)
- `gradle.properties` の `platformPath` をローカルのAndroid Studio SDKパスに合わせて設定(マシン依存、コメントに例あり)

## Android Studio へのインストール

1. **Settings → Plugins → ⚙ → Install Plugin from Disk...**
2. `build/distributions/cursor-agent-plugin-<version>.zip` を選択
3. Restart IDE
4. **View → Tool Windows → Cursor Agent**

サンドボックスでの動作確認: `./gradlew runIde`

## 開発

- UI比較の証跡: [Cursor Agent UI 閲覧調査（2026-09-05）](docs/research/cursor-agent-ui-survey-2026-09-05.md)
- 次の対応順・優先度・受入条件: [UI差分取り込み計画](docs/plans/cursor-agent-ui-gap-plan.md) / [親 Issue #19](https://github.com/shinma06/cursor-agent-plugin/issues/19)
- ビルド/テストコマンド、アーキテクチャ、既知の制約: [`CLAUDE.md`](CLAUDE.md)
- 機能要件・優先度・検証済み事項: [要件定義書](docs/cursor-agent-plugin-requirements.md)
- 進捗・タスク管理: [GitHub Issues](https://github.com/shinma06/cursor-agent-plugin/issues)(`CLAUDE.md`や要件定義書より新しい場合がある — 実装状況の最終的な確認先はここ)

- Android Studioで実際に確認した範囲と追加タスク: [実機UI追補](docs/research/android-studio-ui-followup-2026-09-05.md)
