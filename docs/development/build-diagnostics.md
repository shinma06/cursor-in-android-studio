# 設定画面のビルド・CLI診断（#28）

設定 → Tools → Cursor in Android Studioに、ロード中のプラグインversion、同梱ビルドID、IDE build、適用済みCLI設定と解決候補を表示する。「表示を更新」はこれらを読み直すだけで、CLIのversion/status/aboutや認証処理を実行しない。CLI版・認証状態は未取得と明示する。

## 値の出所と未特定表示

- versionはロード済みPlugin descriptor、IDE buildはIDEのApplicationInfo APIから取得する。[Plugin versionの公式定義](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#version)
- ビルドIDはGradleのgenerateBuildIdentityが生成し、processResources経由でPlugin JARへ入れるcursor-agent-build.propertiesを読む。Git HEADと作業treeの状態をタスク入力にするため、commit/dirty状態が変われば再生成する。時刻やローカルpathをresourceに含めない。Gradle標準のWritePropertiesを使う。[Gradleのresource処理](https://docs.gradle.org/current/dsl/org.gradle.language.jvm.tasks.ProcessResources.html)
- cleanかつ40桁SHAを持つ場合にだけSHAを表示する。未commit変更（未追跡ファイルを含む）がある開発ビルドは「未特定」、Gitなし・Git取得失敗・resource欠落/不正も未特定。SNAPSHOTというversion名だけでは固定sourceを断定せず、同梱SHAとclean状態を照合する。
- Git情報を取得するのはビルド時だけ。インストール後の表示でprojectのcheckoutやbranchを調べない。ビルド途中のsource変更まで防ぐ仕組みではないため、固定QA build中はwriterを停止する。

## CLIの候補表示

既存detectAgentExecutable()を再利用し、固定候補を表示する。自動探索の設定値は空欄のまま保持し、診断のために実行パスを保存し直さない。CLI欄の未適用編集は実行設定ではないため、診断は適用済み設定を表示すると画面に明記する。適用/reset、表示更新で診断を読み直す。

現AgentProcessServiceは、手動指定に実行権限があればそれを選び、なければ固定候補、最後に実行時PATHのagentへ戻る。診断では無効な手動指定を明示し、選ばれる自動候補も表示する。絶対パスの実行可能ファイルでも「ファイル確認のみ。CLI起動は未確認」であり、正しいCLI版・認証成功を保証しない。相対パスは解決先未確認、候補なしはPATH検索が未確認とする。候補がdirectory等なら実行可能ファイル確認失敗として示す。探索規則やservice実行を変更しない。

## コピーと所有権

「表示中の診断情報をコピー」は、画面の同じsnapshotだけを標準clipboardへコピーする。コピー時の再取得や自動送信はしない。ローカルパスを含むことをボタンの前に明示する。収集項目を上記だけに限定し、認証情報、アカウント、環境変数全体、会話本文は読まない。パス等の改行・制御文字・方向制御は空白にし、追加の診断行や隠し表示として解釈させない。

新しいprocess/coroutine/listener/service/cache/監視はない。設定UIの参照はdisposeUIResourcesで解放する。Android Activity/構成変更/Context/DBはこのSwing設定画面では使用しない。

## 検証とQA

PluginDiagnosticsTestでclean/dirty/不明の表示、手動/自動/無効/相対/PATHの候補、CLIを実行しないこと、文字列の安全な表示、明示コピーと表示snapshot一致を確認する。既存CLI設定テストを維持する。

buildPluginではZIP/JAR内のresourceと固定HEAD/clean状態を照合する。追加の生成タスク確認ではclean→dirty→cleanとGitのないsourceを実行し、状態変更後に旧resourceを残さないことを確認する。これらはインストール後の表示成功とは別の証拠である。

[Case28](../verification/changes/issue-28.json)に識別・CLI/コピーの手順を記録する。指定GUI担当が固定source/ZIP SHA256/installed全JAR/起動ロード実体と画面のversion/buildID/IDE buildを照合する。別checkoutを開いた場合も表示がインストール済み成果物の値を保つことを確認する。dirty/Gitなしbuildを試せない場合は該当手順を未実施として残す。GUI/install/restartは実装担当未実施であり、develop統合だけでmain受入や実機成功にしない。
