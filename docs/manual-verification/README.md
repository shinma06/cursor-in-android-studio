# 手動動作確認（Manual Verification）

エージェントが `./gradlew test` や CLI スパイクだけでは検証できない項目の **動作確認マトリクス** を置くディレクトリです。

## 誰が何をするか

| 役割 | 作業 |
|------|------|
| **エージェント** | 人間確認が必要な変更を入れたら [`matrix.md`](matrix.md) に行を追加・更新する。チャットに長文 QA リストを書く代わりにここを正本にする |
| **開発者（人間）** | 指定ブランチを checkout → `./gradlew runIde` 等で確認 → `Status` / `Verified by` / `Date` を更新 |

## 確認の基本手順

```bash
git fetch origin
git checkout <branch>   # matrix.md の Branch 列を参照
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew runIde
```

前提:

- `agent` CLI がログイン済み（`~/.local/bin/agent status`）
- `gradle.properties` の `platformPath` がローカル Android Studio を指している

## ファイル

| ファイル | 用途 |
|----------|------|
| [`matrix.md`](matrix.md) | **現在**の確認一覧（常にここだけ見ればよい） |
| [`archive/`](archive/) | マージ済み・クローズ済みの古い行（必要時のみ） |

## Status の意味

- `pending` — 未確認
- `pass` — 人間が期待どおり確認済み
- `fail` — 不具合あり（Issue / PR に詳細を残す）
- `merged` — PR マージ済み。archive へ移す前の中間状態

## プロジェクトルール

Cursor エージェント向けルール: [`.cursor/rules/manual-verification.mdc`](../../.cursor/rules/manual-verification.mdc)
