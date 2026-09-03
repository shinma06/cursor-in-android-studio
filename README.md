# Cursor Agent Plugin

Android Studio 向け Cursor Agent 統合プラグイン。

- **リポジトリ**: https://github.com/shinma-postas/cursor-agent-plugin
- **ローカルパス**: `~/Dev/cursor-agent-plugin`

## 含まれる機能

- Tool Window「Cursor Agent」（右ペイン）
- テキストプロンプト送信（F-01）
- stream-json ストリーミング表示（F-02, 基本）
- 会話履歴表示（F-03, メモリ内）
- 新規チャット（F-05）
- Ask / Agent / Plan モード切替（F-20）
- Force トグル（F-22, デフォルト OFF、⋯ メニュー内）
- アクティブファイル/選択範囲の自動コンテキスト付与（F-15）
- `--workspace` をプロジェクトルートに固定（F-51）

## 前提

- Android Studio 2026.1 以降（build 261+）
- `agent` CLI（`~/.local/bin/agent` 等）
- `agent login` または `CURSOR_API_KEY` で認証済み

## セットアップ

```bash
cd ~/Dev/cursor-agent-plugin
export JAVA_HOME="$("/usr/libexec/java_home" -v 17)"
./gradlew buildPlugin
```

## Android Studio へのインストール

1. **Settings → Plugins → ⚙ → Install Plugin from Disk...**
2. `build/distributions/cursor-agent-plugin-0.1.0-SNAPSHOT.zip` を選択
3. Restart IDE
4. **View → Tool Windows → Cursor Agent**

## 開発

`gradle.properties` の `platformPath` で Android Studio SDK を指定します。

| インストール方法 | platformPath |
|------------------|--------------|
| brew cask（/Applications） | `/Applications/Android Studio.app/Contents` |
| Homebrew Caskroom 直指定 | `/opt/homebrew/Caskroom/android-studio/2026.1.3.8,quail3-patch1/Android Studio.app/Contents` |

サンドボックス確認: `./gradlew runIde`

## ドキュメント

- [要件定義書](docs/cursor-agent-plugin-requirements.md)

## ロードマップ

- GUI-2: Markdown 表示
- F-21: モデル選択（`agent --list-models`）
- F-10: `@ファイル` 補完
- F-30/F-31: 差分 Apply/Reject
- F-40〜42: チェックポイント / ロールバック
