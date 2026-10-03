# Cursor上部メニューとAndroid Studioへの対応（2026-09-10）

親 #19 / #1、調査 #154、上部操作の統合 #153。ユーザー提供のCursor IDE内Agentメニュー12項目を対象とする。独立Agents Window専用機能は混同しない。

## 判断

最上段はAndroid Studio標準のToolWindow title actionsと縦メニューを使い、下の重複Agent行を撤去する。メニューは選択中の会話へ作用する操作を先に整え、ファイルエディターの設定には対象が分かる日本語を付ける。Cursor側の汎用エディター操作を、Pluginから無関係なコードファイルを閉じる操作として移植しない。

画像の後半にはVS Codeのeditor group用操作が混ざる。Cursorでは会話もEditorInputとしてgroupに入るが、現PluginはToolWindow内の独自SessionTabsである。この構造差と本文保存の有無を実装条件にする。12項目を調べたことと、12機能を実装・実機受入したことは別である。

## 調査の基準と証拠

| 対象 | 固定基準・確認範囲 |
| --- | --- |
| Cursor IDE | 3.19.19、commit `6496ea8a068aebfcd21990e70ff522e9abf10c80`、vendor build日時2026-09-08。インストール済みproduct metadataと配布workbenchの該当actionを読み取り。内部名を新しいPluginの通信契約にはしない。 |
| Plugin | develop `97d06820596b7089cf6309557634200f4ed9ba4a`。#147のACP新規会話接続を含む。SessionTabs、RootPanel、Timeline、Options、履歴保存の実装とIssue #44/#45を照合。 |
| 実機観察 | run `ui-menu-20260910-r1`、PMのhost leaseで0 Cursor sends。空のNew Agentタブ2件・空入力、標準View → Appearance → Open Browserを確認。実行後AXのウィンドウ名はBrowser Tabになったが内部描画は取得できず、完了観察ではない。 |
| CUA制約 | 画面内クリックは `noWindowsAvailable`、最終読取はScreenCaptureKit `-3812`。UI操作を停止しlease解放。空Browser Tabの後片付けは復旧後の確認待ち。未確認のpopup・保存dialog・取消をpassにしていない。 |
| 公開記録 | [開始・部分観察・停止記録](https://github.com/shinma06/cursor-in-android-studio/issues/154#issuecomment-5609919426)。個人の会話本文・rawログ・ローカル絶対パスを公開しない。 |

## 12項目の対応表

「確認」は公開仕様または固定vendorコードからの確認で、GUI実測とは区別する。「予定」は後続実装の受入契約であり、実装済みを意味しない。

| ID / Cursor表示 | Cursorでの機能・対象 | 操作結果・取消・失敗 | Plugin側の日本語・実装方針 |
| --- | --- | --- | --- |
| M01 Open Browser | 内蔵browserを開く入口。ブラウザーによるweb検証とAgentのbrowser toolsは別の層。[C1] | Browser editor/windowを開く。URL移動・戻る/進む・再読込、Agent側の操作権限やMCP接続は別途必要。今回内部UIは未観察。 | 「ブラウザーを開く…」。IDE既存browser APIを先に照合。外部の既定ブラウザーを開くだけなら明記し、Cursor内蔵browserと同等とは呼ばない。Agentの自動操作接続は#25/#150系統で別に検証。 |
| M02 Export Transcript | 選択会話のタイトル、本文、コード、質問回答をMarkdownへ出力。固定vendor actionは`composer.exportChatAsMd`。Cloud Agent会話を除外。 | 保存先dialogで`.md`選択→保存→完了通知とOpen。空内容は案内、取消で書込なし、書込失敗とdialog失敗を通知。履歴読取失敗時にはロード済み部分へfallbackする。 | 「会話を書き出す…」。#45/#44の保存契約を再利用。読み込めた範囲だけなら出力範囲を明記し、全履歴と誤認させない。元promptと注入contextを区別し、自動アップロードしない。 |
| M03 Copy Request ID | Cursor側の最新generationの診断ID。固定vendor actionは`composer.copyRequestId`、会話ID/JSON-RPC idとは異なる。[C2][C3] | 選択位置から対象会話を解決してコピー。固定版はIDなしの場合も文字列をクリップボードへ書く。 | 「Request IDをコピー」。検証済みCursor診断IDだけを対象にする。ACPからの取得経路は未確認。IDがない場合は理由を表示して無効化し、会話IDやRPC連番で代用しない。既存クリップボードも上書きしない。 |
| M04 Give Feedback | 最新request IDがあればそのrequestの報告dialog、なければ一般feedback入口。 | 報告内容を入力して利用者が送信する。入口を開く操作と送信を区別する。今回送信していない。 | 「フィードバック…」。Pluginの不具合は本repoのIssue作成画面、Cursorの応答自体の報告はCursor公式案内へ分ける。宛先を明示し、会話・ログ・IDを無断添付しない。自動投稿しない。 |
| M05 Agent Settings | Cursor Agentの設定画面へ移動する入口。 | メニューを閉じて設定を開く。設定保存は画面側の通常操作に従う。 | 「設定」。#153で既存接続/権限/実行範囲/作業場所/MCP/要約/設定を縦メニューへ移す。ACPで有効な設定と互換CLI専用の設定を区別する。 |
| M06 Show Opened Editors | 対象groupの開いているeditorを選ぶ一覧。EditorTitleに登録された汎用操作であり、履歴検索ではない。[V1][V2] | 一覧から選択して該当tabへ移る。検索/キーボード選択/取消。現在groupが会話なら会話tabが対象になる。 | 「開いているチャット…」。SessionTabsの現在開いているものだけ一覧化し、安定tab IDで選択。New Agentが重複しても位置等で区別し、#66で保留した自動タイトルを生成しない。過去履歴と別の入口。 |
| M07 Close All | 当該groupのeditorを閉じる。現公開VS Codeコードではsticky tabを除外する。全workspaceのファイル/会話を削除する操作ではない。[V2] | close handlerが未保存内容等を処理し、キャンセルなら対象を維持。tabが閉じてもCursor側の保存会話の削除とは別。 | 「すべてのチャットを閉じる…」。Plugin所有tabだけが対象。#44前は本文/下書きが復元不能なので、その損失と実行停止を具体的に説明して取消可能にする。confirm前に閉じない。最後に空New Agentを1件作る既存契約を再利用。 |
| M08 Close Saved | `savedOnly`で変更のないeditorを閉じ、sticky tabを除く。保存した会話データの削除ではない。[V2] | dirty editorを残す。会話のdraftや生成状態がそのままfile dirtyになるとは限らない。固定Cursorの会話EditorInputに独自isDirty overrideは見つからず、具体的な会話close UXは実機未確認。 | #44の本文保存・dirty/保存完了契約ができるまで「保存済みチャット」と呼んで閉じない。chatIdがある/実行していない/下書きが空という条件は保存済みの根拠にならない。 |
| M09 Enable Preview Editors | 一時preview tabの再利用を切り替える。ファイルを次々選んだ時のtab増加を抑えるVS Code設定である。[V1][V3] | previewを次のeditorで置換、固定化/編集時に通常tabへ移行。既存会話を破棄する設定ではない。 | 会話tabは現状すべて保持され、preview交換モデルなし。ファイル用なら「ファイルのプレビュータブ」と明示してIDE標準のEditor Tabs設定へ接続する。[J2] 会話previewは#44後の履歴再読込・下書き保護が前提。 |
| M10 Lock Group | 新しく開くeditorを別groupへ送って今のgroupを保つ。内容のread-only化やAgentの編集禁止ではない。[V3] | 解除可能。明示的移動は許可し、設定は再起動後も維持。複数groupの存在が条件になる。 | 現PluginのToolWindowはコードeditorと既に別領域で、新規ファイルに置換されない。無意味なLockスイッチを作らない。複数会話group/editor表示を導入する段階で、同じ配置保護が必要か評価する。 |
| M11 Configure Editors | `workbench.editor`で絞った設定画面を開く。[V4] | editorのtab/配置等を設定する。チャットモデルや推論設定ではない。 | 「ファイルエディター設定…」としてIDE標準Editor Tabs等へ接続。設定の適用範囲はIDE全体。Plugin専用会話tab設定と混同しない。 |
| M12 Configure Icon Visibility | 現toolbar actionの表示/非表示を切替え、既定へ戻す。ファイルアイコンテーマ選択ではない。固定vendor toolbar/menu実装を確認。 | actionごとのチェックとreset。隠しても機能を失わせない。[V3] | 「上部アイコンの表示」。新規/履歴の表示切替と既定に戻す。縦︙を隠さず、隠した操作にはメニューから到達可能にする。IDEの既存toolbar customizationが対象title actionsに効くかを実装前に照合する。 |

## Cursor・JetBrains・Pluginの比較

| 観点 | Cursor IDE内Agent | JetBrains AI Assistant + Cursor ACP + IDE/MCP | Pluginの方針 |
| --- | --- | --- | --- |
| 最上段操作 | 会話editorのtitle toolbarと汎用groupメニューを利用 | AI Chatに新規/履歴/Options、editor tab表示の入口がある。[J1] | ToolWindow標準title/gearを使う。独自の二重headerを残す利点はない。 |
| 会話一覧・本文 | Cursor側の会話状態と保存に基づく | AI Chatも過去会話を管理する。[J1] | 現状metadataとメモリ内tabのみ。#44/#45の未実装を明記し、Cursor/AIAより優れるとは言わない。 |
| Agent接続/設定 | vendor固有のpanelとAgentを接続 | ACP Registry/custom agents、custom MCPとIntelliJ MCPの受け渡し、ACPログ取得がある。[J3] | #147の新規会話接続は基盤。標準ACPとIDE機能を使い、未検証の独自コマンドを組み立てない。 |
| ファイルタブ | VS Code editor groupsが会話にも利用される | IDEがpreview、固定、split、tab設定を持つ。[J2] | 既存IDE APIを再利用。会話とファイルの対象を明示する。 |
| Browser | 手動ブラウザーとAgentのbrowser toolsを持つ。[C1] | JCEFは埋込み描画の基盤。MCPは別途Agentに操作能力を渡せるが、同一のブラウザー画面を操作できる保証ではない。[J3][J4][J5] | 内蔵表示とAgent制御を別々に検証。JCEF widgetだけでCursor同等のAgent browserとは呼ばない。 |
| 診断/feedback | backend requestに紐付くIDとfeedback | ACPログ取得とIDE側の支援がある。[J3] | 正確な診断IDが未取得なら取得不能と扱う。共有前に内容/宛先を利用者が確認する。 |

## インストール済みSDKでの独立照合

別Session `gpt-154-sdk-review-cb61` がAndroid Studio `AI-261.26222.65.2614.16204760` のJAR/action XML/signature/bytecodeと公式ソースを読み取り、[独立調査記録](https://github.com/shinma06/cursor-in-android-studio/issues/154#issuecomment-5609993894)へ保存した。GUI操作やコンパイル成功を示す記録ではない。

| 機能 | 確認したAPIと利用境界 |
| --- | --- |
| 内蔵表示 | `HTMLEditorProvider.openEditor(project, title, Request.url(url))`が候補。String overloadはURLでなくHTML本文。`OpenInBrowser`は外部ブラウザー用。継続的な移動UIはJCEFと必要操作だけで構成する。 |
| 保存・コピー | `FileChooserFactory.createSaveFileDialog`は保存先選択に再利用でき、取消はnull。`CopyPasteManager`でコピーできるが、元の会話本文/診断IDの取得契約は提供しない。 |
| ファイル一覧 | `FileEditorManager.openFiles`はproject内ファイルであり会話ではない。今回M06は`SessionTabs.snapshot()`から構成する。 |
| 全close | `CloseAllEditors`はDataContextのEditorWindow/選択file groupに作用する。現SDKにはVS Codeと同じsticky除外の保証がなく、Plugin会話closeには使わない。 |
| 保存済みclose | `CloseAllUnmodifiedEditors`はVCSのFileStatusManagerを参照する。「保存済みだがGit差分あり」を残すためCursorのsavedOnlyと一致しない。M08の同等APIとして採用しない。 |
| ファイルpreview・設定 | `UISettings.openInPreviewTabIfPossible`と`fireUISettingsChanged()`、設定ID`editor.preferences.tabs`/`ConfigureEditorTabs`を確認。IDE全体のファイル用であり、Plugin会話のpreviewではない。 |
| 操作アイコン | `UISettings.showFileIconInTabs`は無関係。title actionsの個別表示をPlugin設定で切り替え、標準︙に代替入口を残す。`createActionToolbar`だけで個別可視性設定が自動生成されるわけではない。 |
| group lock | 同等のpublic actionを当該SDKで確認できず。pinやToolWindow固定を同等機能としない。 |

`BrowserViewAction`はこのSDKのクラス/action一覧で確認できなかった。別製品/版にも存在しないという結論ではない。公式masterとインストール済みSDKにはclose実装の差があり、masterだけを根拠に現SDKの保存保護を保証しない。

## 接続責務

- UIの並び、tab一覧、title actionの可視性、設定画面はPlugin/IDEの責務。ACPに存在しないUI命令を要求しない。
- 会話の復元はcapabilityを確認した`session/load`と再送される`session/update`、またはPlugin側保存契約で行う。[A1][A2] IDだけから本文を作らない。
- Request IDはCursor拡張の検証済み診断フィールドが必要。ACPのrequest-response照合ID、sessionId、toolCallIdは代用品にならない。#146のprivate fixtureを公開せず、この調査だけで新しい通信契約が確認できたことにしない。
- Browser自動操作はIDE UIとは別にAgent側ツール、MCP接続、必要な権限と状態同期を確認する。GUI座標操作の失敗を新規MCP接続の認可に読み替えない。
- 権限/sandbox/worktreeは#153で既存意味を維持する。標準設定でも互換CLIの即時編集は止められず、追加UIを事前承認保証として説明しない。

## 段階実装と残条件

1. **#153 上部統合**: タブ下Agent行を撤去。新規/履歴をnative title actionsへ、既存設定を縦︙へ。選択tab、実行状態、IDE標準移動/表示操作とdisposeを検証。
2. **#156 メニュー第一段階**: 開いている会話一覧、範囲を明示した全tab閉鎖、feedback入口、IDE編集設定への入口、title iconの可視性。#153統合後に同じ共有ファイルへ接続する。native機能の重複実装を避ける。
3. **保存/出力**: #44→#45で本文保存・復元・検索/export・出力範囲・保存済み判定を揃える。先行active transcript出力を分離するなら保存実装を増やさず、対象範囲と#45の残条件を更新する。
4. **#157 Browserの手動入口**: 標準JCEFとURL欄/戻る/進む/再読込を持つ最小UIを独立実装。初回は外部ページへ自動接続せず、JCEF非対応・HTTP/HTTPS検証・認証情報入りURL拒否・読込失敗・disposeを扱う。#156統合後にメニューを接続する。Agent操作はこの段階に含めない。
5. **診断ID/会話group**: #25/#26でvendor拡張またはIDE/MCPの対応能力が確認できたものから実装。単なるメニュー表示を受入にせず、未対応の機能を押せるダミー項目にしない。会話preview/lockの詳細は保存とgroup導入に依存する。

後続実装は#156/#157、本文保存/exportは#44/#45、Request IDの取得は#25、会話preview/groupは#26/#62へ引き継ぐ。GUI環境がblockedでもコード/調査の独立レビューとCase追跡を進め、main候補全体のGUI受入とは分ける。

## 後続GUI Caseの受入契約

これは将来実装のCase設計であり、この調査のpass結果ではない。

| Case | 操作 | 期待結果・保護 |
| --- | --- | --- |
| MENU-OPEN-CHATS | 3tab、同名、狭幅、一覧検索/Enter/Escape、選択 | 正しいIDのtabへ移動し入力/本文/実行状態を維持。履歴を混ぜない。 |
| MENU-CLOSE-ALL | 実行中/下書きあり/本文ありを混ぜ、取消→再操作 | 取消は無変更。実行は説明した範囲のtabだけ停止・閉鎖し空tab1件へ。他project・ファイル・履歴データを削除しない。遅延callbackを遮断。 |
| MENU-EXPORT | 空、旧metadata、長文/コード/質問/tool、保存先取消、失敗 | 範囲を明記したMarkdown。元promptを保ち自動注入contextと区別。取消で出力なし、失敗で既存fileを壊さない。 |
| MENU-REQUEST-ID | IDなし/正しいIDあり/別tabへ切替 | 値を捏造しない。選択tabの診断IDのみ。なしではclipboardを変更しない。 |
| MENU-FEEDBACK | Plugin報告とCursor案内を開き、戻る | 宛先明示、無断の会話/環境添付なし、自動投稿なし。 |
| MENU-EDITOR-SETTINGS | IDE設定を開く/取消、preview切替 | 対象がファイル用と分かり、会話tabの下書き/本文に影響しない。 |
| MENU-ICON-VISIBILITY | 新規/履歴を個別に隠す、再表示、既定へ戻す、再起動 | ︙から各機能に到達可能。保存設定を反映し、狭幅でもメニューが使える。 |
| MENU-BROWSER | 初回/再開、URL移動、失敗、閉鎖、Agentツール呼出 | 手動表示とAgent制御を別に記録。同一画面/セッションの操作であることを確かめ、権限とJCEF非対応環境を扱う。 |

## 一次資料

- [C1: Cursor Browser](https://prod.cursor.com/docs/agent/tools/browser)
- [C2: Cursor Reporting a bug](https://prod.cursor.com/help/troubleshooting/reporting-bugs)
- [C3: Cursor CLI slash commands](https://prod.cursor.com/docs/cli/reference/slash-commands) — `/copy-request-id`と`/copy-conversation-id`は別。対話CLIのコマンド存在だけではACP対応を示さない。
- [V1: VS Code editor menu registrations](https://github.com/microsoft/vscode/blob/main/src/vs/workbench/browser/parts/editor/editor.contribution.ts)
- [V2: VS Code editor commands](https://github.com/microsoft/vscode/blob/main/src/vs/workbench/browser/parts/editor/editorCommands.ts)
- [V3: VS Code custom layout](https://code.visualstudio.com/docs/configure/custom-layout)
- [V4: VS Code editor settings action](https://github.com/microsoft/vscode/blob/main/src/vs/workbench/browser/actions/layoutActions.ts)
- [J1: JetBrains AI Chat](https://www.jetbrains.com/help/ai-assistant/ai-chat.html)
- [J2: JetBrains Editor basics](https://www.jetbrains.com/help/idea/using-code-editor.html)
- [J3: JetBrains ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)
- [J4: JetBrains JCEF](https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html)
- [J5: IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)
- [A1: ACP overview](https://agentclientprotocol.com/protocol/v1/overview)
- [A2: ACP session setup](https://agentclientprotocol.com/protocol/v1/session-setup)

Cursor固有actionの意味は上記固定vendor buildの配布コードを読み取った調査結果である。非公開の利用者データやコードの複製は含めない。公開資料から確認できる契約と、当該版に限る内部実装、GUI未確認を分離する。
