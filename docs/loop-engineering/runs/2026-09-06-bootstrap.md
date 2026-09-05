# 2026-09-06 基盤の初回試運転

対象: Issue #29。基点: `9603cbf260f825ba08e48899db6919e0036d998e`と今回の未コミット基盤変更。GPTが実装・Computer Use操作、Claude Proが独立資料レビュー。機能修正・既存GUI QA合格はこの試運転の対象外。

## 自動確認

- 既存`./gradlew test buildPlugin`: 成功（既存成果物はUP-TO-DATE）。新しいGUI操作の合格証拠にはしていない。
- loop証跡ゲート: 11テスト成功。CLI代用、未確認状態、証跡欠損、Cursorだけ、重複、別runパス、不足ケース、古い観察、異なるビルドSHA、不正tool参照を拒否。
- 実fixture生成: 同一の`greeting.txt`、独立Git、remoteなしを確認。同じrun IDの再作成と`../`を含むrun IDを拒否し、既存manifestが保持された。
- dirtyなソースでの`build`と、環境/ビルド/GUI証拠が不足する`check`が非0で終了することを確認。
- skill-creatorの`quick_validate.py`はシステムPythonにPyYAMLがなく実行不可。スキルのfrontmatter・参照先・GPT/Claude双方の内容一致を別途確認。外部依存は追加していない。

cleanコミット後に新しい`loop.py build`自体も実行し、成功した。対象SHA: `b12ccbef57097872bac4e60e5d7b0bc6ae5f9dea`、ZIP SHA-256: `325c8f8017494c02ed443673fecf513fe51c63f71f4a68a40d3659980a39f290`。起動中ビルドの照合は依然未了のため、`check`がINCOMPLETEを返すことも確認した。

## Computer Useで観察した事実

2026-09-06（JST）、このタスクのCUAツール出力が一次証拠。共有要約には他プロジェクト・個人情報を含めていない。

| 面 | 操作・観察 | 判定 |
|---|---|---|
| アプリ一覧 | ChatGPT（内部ID com.openai.codex）、Claude、Android Studio、Cursorを検出 | 接続入口確認 |
| Claudeアプリ | 既存会話のAXとスクリーンショットを取得。新規会話への操作で`The user changed … Re-query`が繰り返された。既存会話へは送信していない | GUI経由のレビューは未実施 |
| Claude Code | sandbox内のauth statusはloggedIn=false。通常実行環境でclaude.ai / Pro / loggedIn=trueを確認。CLI 2.1.261 | 認証制限を切り分け |
| Cursor IDE | 起動後のAXと画面を取得。File → フォルダーを開くに到達。生成fixtureへのパス移動を試したが、操作後も元のcursor-agent-pluginウィンドウが取得され、切替を確認できなかった | MV-036 blocked。入力/送信は未実施 |
| Android Studio | 初回取得はScreenCaptureKit -3811（stream開始失敗）。再試行でCursor AgentのReady・空欄・Send・モード/モデル等のAXと画面を取得 | 接続一部回復。ロード中のビルドSHAは未照合 |
| プラグイン | ⋯をクリック後にAX・スクリーンショットを取得。メニュー展開の結果を確認できなかった | 操作成否未確認。製品failにしない |

**新しいMV-001/010/020/021/022/023等の製品QAはpassにしていない。** 送信数は0、検証用ファイルの変更依頼も0。現在開いている開発リポジトリへ課題を送信しない前提が機能した。画面取得が成功したことと操作が完走したことを分けた。

## Claude Proの独立レビューと採否

送信は自動承認レビューで一度拒否された。ユーザーが今回作成した5ファイルのClaude Pro送信を明示承認した後、`claude -p --tools '' --output-format text`で実施。資料読解のみで、GUI確認/コード実行ではない。認証情報や既存製品コードは送信していない。

| 指摘 | GPTの対応 |
|---|---|
| 失敗ケースを結果から除外するとcheckを通せる | 採用。prepareで面とMV IDを先に固定したplan.jsonを作り、結果集合の過不足を拒否。意図的なplan改ざん防止ではない |
| tool参照と非空ファイルだけでは真実性を証明できない | 採用。出力をFORMAT ONLYへ変更、tool参照の形を厳格化。内容の真実性は観察・レビューの責任と明示。マーカー文字列だけで真実性を保証する案は採らない |
| fixtureと本体リポジトリの取り違え | 採用。LOOP_FIXTURE.txtにrun/面/絶対パスを出し、送信前にGUIで照合。OS sandboxではないことも明示 |
| 古い観察結果の流用 | 採用。pluginケースにビルドSHA一致・ビルド以後のタイムゾーン付き観察日時を要求 |
| Gradle増分ビルドの誤判定の可能性 | 未再現の仮説として保留。UP-TO-DATEだけで不正とは判定しない。cleanソースでGradle実行し、ログ/ZIPハッシュ/ロード実体の照合を要求。疑いが出た時点で再ビルドを追加する |

## 次に再開する操作

1. GUI操作が他の手動操作と競合しない状態で、新runをprepareする。
2. Cursor・Android Studioで生成fixtureを開き、LOOP_FIXTURE.txtのrunと面を画面で照合する。自動切替が安定しなければこの初期配置だけ人間が担当し、GPTへ戻す。
3. cleanな対象SHAから`loop.py build`を実行し、インストール・再起動・ロードの同一性を確認する。
4. MV-036のブロッカーを解除後、#20の受入条件に沿う1サイクルを開始する。既存#5や#19の未実施QAは維持する。

基盤の成果物・スクリプトは利用可能。GUIの送信→編集→復元を含む両面完走は未確認で、環境解除後に行う。
