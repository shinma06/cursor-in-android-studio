# 人間向け：ループ開発の始め方

> 2026-09-07 / #83: developはテスト・独立レビュー・Case追跡で統合可能（GUI pending/blocked/failを保持）。mainは固定候補全体の必要Case pass後のみ。区切り単位の入口は [確認マトリクス](../verification/README.md)。過去MV/runは履歴であり新候補のpassへ転記しない。


普段は修正内容だけ伝えれば、エージェントがIssueの検索/作成、専用worktree、PRを用意します。Git操作を毎回指示する必要はありません。
[GitHub開発規約](../development/github-workflow.md)と[GUI予約手順](../development/gui-coordination.md)が全タスクの共通ルールです。
複数タスクは別worktreeで並列に進め、GUIだけ予約順に操作します。GPTがGUI操作・修正・Claudeへのレビュー依頼・再確認・記録を進めます。

## 初回の準備

1. ChatGPTデスクトップアプリでこのリポジトリを開く。Computer Useプラグインを有効にし、macOSの画面収録・アクセシビリティ権限を設定する。対象はAndroid Studio、Cursor、必要ならClaude。アプリから権限を求められたら表示内容を確認する。[公式設定手順](https://learn.chatgpt.com/docs/computer-use)
2. CursorアプリにProアカウントでログインする。ターミナルで`agent status`も確認し、未ログインなら`agent login`を実施する。GUI版のログインだけでCLIも使えるとは限らない。
3. ClaudeアプリにProでログインする。Claude Codeも使うなら`claude`を起動しClaude.aiの契約アカウントでログインする。APIキーは不要。`claude auth status`で確認する。[公式認証](https://code.claude.com/docs/en/authentication)
4. `gradle.properties`の`platformPath`が手元のAndroid Studioを指すことを確認し、以下を一度実行する。

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew test buildPlugin
python3 -m unittest discover -s scripts/loop -p 'test_*.py'
```

GUI検証では作業中の本物のプロジェクトに編集を依頼しません。GPTが作る`.loop-runs/<run-id>/cursor`と`plugin`を使います。これらは同じ内容から開始する独立した検証用Gitリポジトリです。OSレベルのsandboxではないため、編集先を限定した課題で使います。

## 毎回GPTへ送る依頼

```text
このプロジェクトの docs/loop-engineering/README.md に従って、Issue #20を1ループ進めて。
GPTが主担当とComputer Use操作者、Claude Proが独立レビュー担当。
Cursor ProをGUIから操作して、小さな課題を実行させ、Android Studio内の
Cursor in Android Studioプラグインと比較して。編集は今回生成するfixtureに限定。
上限45分・修正3回・Cursor送信8回。対象Issueの受入条件と関連MV IDを選び、
再現→修正→レビュー→再検証まで進め、記録と次の一手を残して。
```

対象を決めていない日は「Issue #1から優先順位とclaimを確認して、着手できる1件を選んで」に置き換えます。#20は既知の権限表示とWorktree復元問題のため最優先です。今回の基盤作成だけで#20の修正は完了していません。

## GPTの作業中

- GUI操作中は対象ウィンドウの手動操作を控える。同じ入力欄を人間とGPTが同時に触らない。
- ログイン、OSの許可、CAPTCHAなどを引き継いだら完了後に「再開して」と伝える。パスワードやトークンをチャットに貼らない。
- 判断が必要なら「モデル名は常に読めるようにしたい」など期待する結果を伝える。実装手段はGPTに任せてよい。
- Claude上限時は、レビュー待ちのままGPTができる作業を続けられる。課金プランを変更する必要はない。

## 完了時に見るもの

GPTの報告にあるIssue、検証したSHA、[QAマトリクス](../manual-verification/matrix.md)、実行記録を見る。
`pass`はそのビルド・その操作範囲の確認済み、`fail`は製品の不具合、`blocked`は環境などで確認できない状態です。
`./gradlew test`成功や「Claudeが問題なしと言った」だけではGUI合格になりません。

主観的な使いやすさだけ最後に自分で確認したい場合は、同じfixtureで同じ手順を試し、確認者を`human`として記録します。人間が全ケースを再実行する義務はありません。

## 中断・翌日の再開

「ここで止めて、Issueに引継ぎを書いて」と伝えると、GPTが実行中処理と変更を整理し、最後の状態と次の操作を残します。
翌日は「Issue #番号の引継ぎから再開して」で開始できます。未完了の他エージェントがいる場合はGPTが担当の重複を確認します。

自分で起動したい場合は、[evidence.md](evidence.md)でfixture生成とビルドを行い、GUI予約の担当と調整した後、`./gradlew runIde`でsandbox IDEを起動します。通常使用するAndroid StudioにZIPを入れる場合は、Settings → Plugins → Install Plugin from Disk → 指定ZIP → 再起動。旧版SNAPSHOTとの取り違えを避け、インストール対象を記録します。
