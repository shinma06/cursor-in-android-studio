# Cursor in Android Studio

**開発ルール:** Gitへの言及がなくても [Issue → worktree → PR](docs/development/github-workflow.md) を必須とします。main直接commit/pushは禁止。GUIは[ホスト単位の予約](docs/development/gui-coordination.md)で直列化し、実装は並列化します。

Android Studio 向け Cursor Agent 統合プラグイン。`cursor-agent` CLI をサブプロセスとして起動し、
`stream-json` 出力をパースして独自のSwing/JBUI製チャットUIに描画する(CLIをブラックボックスとして
ラップする方式)。

> **新しくこのプロジェクトに参加するエージェント/開発者へ**: このREADMEは概要のみです。
> 開発を始める前に必ず次の2つを読んでください。
> 1. **[`CLAUDE.md`](CLAUDE.md)**(`AGENTS.md`はこのファイルへのシンボリックリンク) — アーキテクチャ、ビルド手順、既知の制約・落とし穴
> 2. **[GitHub Issues](https://github.com/shinma06/cursor-in-android-studio/issues)** — 進捗と担当の一次情報源。個別Issueの受入・所有記録・直近コメントを確認し、[開発手順](docs/development/github-workflow.md)に従って着手する。レビューは実装と独立した担当が行い、GUIは予約を持つ指定担当または人間が操作する。
>
> 詳細な機能要件は [要件定義書](docs/cursor-agent-plugin-requirements.md) を参照。
>
> **GUI QA（Computer Useと人間による確認）**: [確認マトリクス](docs/verification/README.md)の固定候補・Case JSONを正本とする。人間が確認する場合は[試験手順](docs/verification/human-qa.md)から進む。

## ループ開発

[人間向けの開始手順](docs/loop-engineering/human-runbook.md) / [開発ループ](docs/loop-engineering/README.md)。Issueの対象に応じ、GUIで再現→修正→独立レビュー→同じ操作で再確認する。

## セットアップ

```bash
git clone https://github.com/shinma06/cursor-in-android-studio.git
cd cursor-in-android-studio
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # Gradle・toolchain・JVM targetは21
./gradlew buildPlugin
```

## 前提

- Android Studio 2026.1系（Platform 261系）Stable。下限compile SDKはQuail 1初版2026.1.1.8。正式0.1.0の同一ZIPをQuail 1/JBR21とQuail 4 Patch 1/JBR25で確認済みです。中間patchの個別実測・262以降の対応は含みません。
- `agent` CLI(`~/.local/bin/agent` 等)がインストール・認証済み(`agent login` または `CURSOR_API_KEY`)
- SDKはGradleが固定取得する。local SDKを使う場合だけ `-PuseLocalPlatform=true -PplatformPath=...` を明示し、固定full buildと不一致なら失敗する。

## 配布と受入

**[正式版0.1.0をダウンロード](https://github.com/shinma06/cursor-in-android-studio/releases/download/v0.1.0/cursor-in-android-studio-0.1.0.zip)** / [Releaseと検証資料](https://github.com/shinma06/cursor-in-android-studio/releases/tag/v0.1.0)。

mainの既存機能を保ち、ビルド構成・SDK・依存と配布経路をモダン化した正式版です。検証済みJVM21 ZIPを再ビルドせず公開し、公開後も同一hashを確認しました。develop未反映のACP等の新機能は含みません。[対応IDE・更新内容・検証結果と既知の制約](docs/releases/0.1.0.md) / [配布手順](docs/development/plugin-zip-delivery.md)。

## Android Studio へのインストール

1. **Settings → Plugins → ⚙ → Install Plugin from Disk...**
2. ダウンロードした `cursor-in-android-studio-0.1.0.zip` を選択（**Source code (zip)** はインストール用ではありません）
3. Restart IDE
4. **View → Tool Windows → Cursor in Android Studio**

旧名称「Cursor Agent」からの更新でも、プラグインIDと設定・履歴の保存先は共通です。

サンドボックスでの動作確認: `./gradlew runIde`

## 開発

- UI比較の証跡: [Cursor Agent UI 閲覧調査（2026-09-05）](docs/research/cursor-agent-ui-survey-2026-09-05.md)
- 次の対応順・優先度・受入条件: [UI差分取り込み計画](docs/plans/cursor-agent-ui-gap-plan.md) / [親 Issue #19](https://github.com/shinma06/cursor-in-android-studio/issues/19)
- ビルド/テストコマンド、アーキテクチャ、既知の制約: [`CLAUDE.md`](CLAUDE.md)
- 機能要件・優先度・検証済み事項: [要件定義書](docs/cursor-agent-plugin-requirements.md)
- 進捗・タスク管理: [GitHub Issues](https://github.com/shinma06/cursor-in-android-studio/issues)(`CLAUDE.md`や要件定義書より新しい場合がある — 実装状況の最終的な確認先はここ)

- Android Studioで実際に確認した範囲と追加タスク: [実機UI追補](docs/research/android-studio-ui-followup-2026-09-05.md)
