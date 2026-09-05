# 実行記録と検証コマンド

Python 3.9以降・Git・既存のGradle環境で動く。APIアクセス、送信、GUI操作、Issue更新はスクリプトに含めない。

## 新しいrunとfixture

リポジトリルートで実行する。run IDを使い回すとエラーになり、既存データは変更しない。

```bash
python3 scripts/loop/loop.py prepare 20260906-issue20-r1 --issue 20 --case plugin:MV-025 --case cursor:MV-025
```

`.loop-runs/20260906-issue20-r1/`に次ができる。

| ファイル | 内容 |
|---|---|
| `plan.json` | prepareで先に宣言した面とMV ID。run結果と集合照合する。変更するなら新runを作る |
| `run.json` | Issue、基点SHA、担当、予算、環境、ケース判定、次の操作 |
| `report.md` | 操作と観察、レビュー結果、引継ぎの文章 |
| `cursor/` / `plugin/` | 同じ初期ファイルを持つ、remote無しの独立Gitリポジトリ |
| `evidence/` | スクリーンショット、AX抜粋、必要最小限のログ |

履歴を失わないためreset/clean/deleteコマンドは提供しない。再検証は新run IDを生成する。`.loop-runs`はGit除外。認証情報や他プロジェクトの画面をリポジトリに混ぜない。共有するのは内容を確認した要約を`docs/loop-engineering/runs/`に、必要な証拠だけIssueへ添付する。

## 対象ビルドを固定する

製品GUI QAでは先にソースをcommitし、作業ツリーをcleanにする。新規変更が無関係でcommitできない場合は別のclean checkoutでビルドする。未コミット変更がある試運転は可能だが製品QA完了には使わない。

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
python3 scripts/loop/loop.py build .loop-runs/20260906-issue20-r1
```

`build`はcleanなHEADで`test buildPlugin`を実行し、ログ・SHA・ZIP SHA-256を記録する。ビルド中の変更も拒否する。失敗したrunのログは上書きしない。ZIPが複数残っていたら適切な別checkoutなどで解消して新runで実行する。

そのZIPをGUIから対象IDEへインストールして再起動し、インストール操作とロード済み版を記録する。`runIde`を使う場合も起動ログ・sandboxのplugin実体・対象ソースを照合する。**ZIPを作っただけでは起動中のプラグインとの同一性は証明できない。** 同じ`0.1.0-SNAPSHOT`表示だけで同一ビルドとしない。照合できなければ`blocked`。診断表示機能自体の改善は#28で扱う。

## run.jsonの記入例

`prepare --case plugin:MV-001 --case cursor:MV-001`のように対象を事前宣言する。`plan.json`とpendingの`cases`が生成される。実行後に観察を追記する。失敗したケースを配列から削除してcheckを通さない。再実行の履歴はreportに残す。

```json
{
  "id": "MV-023",
  "surface": "plugin",
  "status": "pass",
  "method": "computer-use",
  "expected": "RevertでHello loopへ戻る",
  "actual": "Revert後にエディタとディスクの両方でHello loopを確認",
  "build_source_head": "<build.source_headと同じ40文字SHA>",
  "observer": "GPT / Computer Use",
  "observed_at": "2026-09-06T10:30:00+09:00",
  "evidence": "evidence/MV-023-after.txt"
}
```

- `surface`: `cursor` / `plugin`。別々の行にする。Cursorだけの成功でプラグインをpassにしない。
- `status`: `pending` / `pass` / `fail` / `blocked`。操作不能はblocked、観察できた期待不一致はfail。
- `method`: `computer-use` / `human-gui`。CLI試験はreportへ記録しGUIの代用にしない。
- `evidence`: run内の空でないファイルへの相対パス、または`tool:<session-id>/<turn-or-call-id>`。スクリーンショットをツールから保存できない場合は、操作と観察のAX抜粋をテキスト保存してツール参照を併記する。作り物の画面は使わない。
- `build.installed_identity_evidence`: 対象ZIPをインストール・再起動しロードを照合した記録への同様の参照。
- `build_source_head`: pluginケースではビルドSHAと一致させる。`observed_at`はタイムゾーン付きで`build.built_at`以降とする。自己申告の改ざん検出ではなく、古い記録の取り違え防止。
- `environment`: IDE/Cursor版、CLI絶対パス・版、モデル、permission、sandbox、worktreeを埋める。複数モデルなら面ごとの差を記載する。
- `state`: `prepared` → `running` → `review` → `verified` / `blocked`。GPTが更新する運用情報で、自動遷移ではない。

```bash
python3 scripts/loop/loop.py check .loop-runs/20260906-issue20-r1
```

終了コード0は**証跡の形式が揃った**という意味。内容の真実性、UIとビルドの一致、対象Issueの全受入条件はGPTが確認する。空の環境、証跡なし、CLIのみ、Cursorのみ、未確認ケースがあれば非0。これ自体はQAマトリクスのpassやIssue closeを実行しない。

## 引継ぎの最小項目

Issueコメントにrun ID、対象SHA、試したMV ID、pass/fail/blocked、証拠の共有先、Claude指摘と採否、残予算、次の具体操作を書く。ローカルパスだけでは別マシンの担当が読めないため要約をcommitする。

改善を比較する際は、同条件の操作回数、期待と違った箇所、待ち時間の実測、再現回数を記録する。計測していない速さや使いやすさを数値で補わない。
