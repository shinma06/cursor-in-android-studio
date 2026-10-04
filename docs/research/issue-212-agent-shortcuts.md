# IDE内Agent panelのショートカット対応表

[#212](https://github.com/shinma06/cursor-in-android-studio/issues/212)。ショートカットの追加・競合修正時に参照する、人間と開発者の共有資料。2026-10-04の初期一覧であり、**全対象の確定・全対応・GUI合格は未達**。未確定のOS条件、入力部品内の独自キー、Keyboard Shortcuts実画面との照合を残す。独立Agents Window、一般エディターの補完、Inline Edit、Terminal prompt専用操作は対象外。Agent panelへの入口は対象に含む。

## 根拠と比較

- [Cursor公式一覧](https://cursor.com/docs/reference/keyboard-shortcuts)は概要であり、全件は製品のKeyboard Shortcutsを参照する契約。表のDはこの概要、Bは下記配布版の静的なAction登録確認。両方とも実操作の証拠ではない。
- B: Cursor 3.23.12 stable、commit `2d29876d567da1607532b23bbf2cd5ddbca496f0`、build `2026-10-01T04:41:16.627Z`。`workbench.desktop.main.js` SHA-256 `87cd7ca0b620138599c195d477a728e05910e28dc02082b325d78f13588acced`。`composer.*` / `aichat.*`登録のうち30件にkeybindingを確認した有限調査。別namespace・入力部品のevent handler・動的割当まで網羅したとは扱わない。原本コード・端末識別子は公開しない。
- [JetBrains AI Assistant](https://www.jetbrains.com/help/ai-assistant/ai-keyboard-shortcuts.html)にも新規会話、検索、送信、改行等のKeymap設定がある。[Cursor ACP連携](https://cursor.com/docs/integrations/jetbrains)とIDE標準機能・[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)を加えた構成が比較対象。キー設定自体は独自価値ではない。
- 本Pluginの追加価値は、Cursorの操作習慣を既存のタブUUID、選択中run、未保存確認、print/ACP設定制約へ直接接続すること。同等以上の判定には全キーと有効条件の実測が必要。ACP/MCPへキーイベントを送る実装ではなく、[IDE Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html)が操作の発見・Keymap・panel contextを担当する。

## 対応表

`M`はmacOSのCmd、Windows/LinuxのCtrl。`A`はOption/Alt。Dの共通OS表記に対し、BのOS別例外や条件は別記する。Action IDの`CursorAgent.`は表中で省略。実装欄の「接続」は本変更のコード接続で、GUIはすべて未確認。

| 操作 | 基準キー | 条件・根拠 | Pluginの状態 / 次の対応 |
| --- | --- | --- | --- |
| 新規タブ | M+T | B/D、panel focus。BにはM+Nの別条件もある | `NewChat`へ接続。M+T/M+Nは同じ新規タブ操作 |
| 新規会話 | M+N / M+R | D。BのcreateNewComposerTabはM+N、M+Rの同等条件は未確定 | M+N接続。M+Rは未割当、#212で条件照合 |
| 閉じる | M+W | B/D、panel focus、Bは一般editor text focus外 | `CloseChat`。既存の未保存/実行中確認を共用、最後のtabは#213の非表示経路 |
| 前/次タブ | M+[ / M+] | B/D、panel focus、Bは一般editor text focus外 | `PreviousChat` / `NextChat`。表示順で循環、1件なら無効 |
| 前/次conversation | M+A+← / → | B、composer/agentsPane、editor text focus外 | 未接続。上記tabとsubComposerの対象差を#212で照合 |
| 前/次subComposer | M+A+← / → | B、composer focus、別weight | 未接続。独立Agents Windowとの所属を確認 |
| 履歴 | M+A+' | B | `History`。既存の保存履歴、予約を一時停止して開く |
| 停止 | M+Shift+Backspace | B/D、panel focus | `Stop`。選択中controllerのrunのみ。非実行中は無効 |
| 入力focus中の停止 | macOS Control+C | B、上記停止のOS別登録 | 未割当。コピー/IMEとの条件を#212で照合 |
| mode menu | M+. / M+A+. | B/D、panel、一般editor text focus/quick input外 | `ModeMenu`へM+.を接続。ACP busy制約を保持、副キーは未割当 |
| mode切替 | Shift+Tab | Dは回転、Bはmode menuの副キー | 未接続。#42/#205の逆focus移動を保持。双方の操作意味と代替focus経路を確定してから対応 |
| model選択 | M+/ / M+A+/ | B/D、panel、一般editor text focus外 | `ModelMenu`。既存popup/再取得に接続。Dの「モデルを巡回」とBのmenuとの差は実測待ち |
| model parameter変更 | macOS/Linux M+Shift+/、Windows M+A+/（M+Shift+/も登録） | B、model menuと競合するweight 201 | 未接続。実際のparameterと#43のprovider model ID契約を照合 |
| context menu | M+A+P | B、panel、一般editor text focus外 | `AddContext`。既存mention候補を開く |
| Plugin設定 | M+Shift+J | DのCursor settingsに対応 | `Settings`。Plugin設定を開く |
| panel表示/focus | M+I / M+L | B/D。選択code/既存chat等で意味が異なる | 未接続。IDEのToolWindow標準表示は利用可。#212で選択context/表示条件を分離 |
| chat followup focus | M+Y系 | B、OS分岐の適用条件は未確定 | 未接続。実画面とOS判定の静的確認が必要 |
| Agent layout切替 | M+E | D | 未接続。JetBrains ToolWindow表示modeとの対応を確認 |
| chatをeditor表示 | M+D | B、panel、一般editor text focus外 | 未実装。一般ファイルeditor操作へ誤接続しない |
| 新しいworktreeで開始 | M+Shift+Enter | B、panel | 未接続。既存ISOLATEDは作業コピーであり同一操作ではない（#301） |
| 選択codeを現在のchatへ | M+Shift+L | B/D、選択あり | `AddSelectionToChat`は既存、既定キー未割当 |
| 選択codeを新しいchatへ | M+L | D、選択あり | 未接続。現在tabへ追加する既存Actionと区別 |
| editor promptからAgentへ | M+L | B、editor prompt bar focus | 未実装。通常panelの切替と区別 |
| 入力送信/予約 | Enter | Dのqueue、実行状態で分岐 | #97の送信設定/#48のqueueは既存。実行中Enter予約は未接続 |
| 即時介入 | M+Enter（入力中） | D | #278の公開契約とローカルqueueとの差を保持。停止→再送で代用しない |
| 変更を一括承認 | M+Enter / 条件付きM+A+Enter | B/D、提案変更あり | 未実装。#47の事後Diff/Revertを事前承認として扱わない |
| 変更を一括却下 | M+Shift+Backspace（提案中） | D、停止と共用 | 未実装。現在のStopはファイルをRevertしない |
| shell許可 / allowlist許可 / 拒否 | Enter / Shift+Enter / Escape | B、pending shell decision専用context | キー未接続。#297/#300の権限意味、ACP exactly-once replyを保持 |
| terminal tool取消 | Shift+Backspace | B、panel | 未実装。run全体停止との対象差を保つ |
| clipboardをcontext / 本文へ | M+V / M+Shift+V | D、code/log/reference clipboard | 現行はnative pasteと画像添付。参照code識別は未対応、由来を捏造しない |
| メッセージ間移動 | Tab | D | 未対応。現行#42/#205のcontrol focus移動との条件分離が必要 |
| 入力focus解除 | Escape | D | 専用Action未接続。popup/IME取消を優先して設計する |
| mention / command | @ / / | D、入力文字 | #24/#258/#404の既存入力候補。通常文字を奪うglobal keybindingにはしない |
| panel内検索 / 閉じる | M+F / Escape | B、composer/find context | #45の履歴検索は既存。現在のtimeline内findは別の未実装操作 |
| 検索の次/前 | F3・Enter / Shift+F3・Shift+Enter | B、find context | 上記panel内検索に従属、未実装 |
| codebaseをchat検索 | M+Enter（選択code側） | D | 未接続。入力中の送信/承認とcontextを分離する |
| 音声 | M+Shift+Space | D | #99でOS標準入力を調査中。Cursor独自Voiceとの同一性は未確認 |
| IDE設定 / Action検索 | M+, / M+Shift+P | Dの一般設定/command palette | JetBrains標準操作を利用。Cursor互換の副キー登録は未対応 |

## 現在の接続範囲と競合

10 Actionの既定キーは`plugin.xml`でWindows/Linux、旧Mac OS X、Mac OS X 10.5+に定義する。MacではCtrl継承を置換する。ユーザーはIDEのKeymapで変更/削除でき、root panelへのlocal登録も同じActionのshortcut setを参照する。標準keymapの一括書換えや他pluginの割当削除はしない。

M+N/T/W、M+[/]、M+/、M+Shift+J等はIDEの新規・閉じる・移動・コメント等と競合し得る。panel内はlocal Action、ファイルeditorや別ToolWindowではpanel dataがないため無効にする。実際の優先順位、custom Keymapの変更即時反映と別projectはCaseで実測する。入力中のIME、mention/command候補、子popupではpanel操作を無効にする。Tab/Shift+Tab/Enter/Escape/貼付けの既存処理は保持する。これは既存入力契約の保全であり、Cursor全ショートカット対応の完了ではない。

## 完了まで残る作業

1. 固定Cursor版のKeyboard Shortcutsと入力部品を照合し、全件・OS・focus・mode条件を確定する。特に表の「未確定」と既定キー未設定commandを確認する。
2. 未実装能力を既存Issueの採否/限界と照合し、必要な具体実装をsub-issue/依存へ分ける。#278等の終了済み調査を、その能力の実装完了の根拠にしない。
3. 全対象の実装と競合解消を判定し、[Case正本](../verification/changes/issue-212.json)に実GUI証拠と未反映mainを追跡する。本初期接続だけで#212を閉じない。
