# IDE内Agent panelのショートカット対応表

[#212](https://github.com/shinma06/cursor-in-android-studio/issues/212)。ショートカットの追加・競合修正時に参照する、人間と開発者の共有資料。2026-10-04の固定版調査。**全対象の確定・全対応・GUI合格は未達**。初期の30件抽出にはAction IDの誤対応と漏れがあり、独立した静的監査で訂正した。未確定行を対象外やpassに置き換えない。

## 根拠・数え方・比較

- [Cursor公式一覧](https://cursor.com/docs/reference/keyboard-shortcuts)は概要であり、全件は製品のKeyboard Shortcutsを参照する。以下は概要と配布版の静的調査で、実操作の証拠ではない。
- 固定版: Cursor 3.23.12 stable、commit `2d29876d567da1607532b23bbf2cd5ddbca496f0`、build `2026-10-01T04:41:16.627Z`。`workbench.desktop.main.js` SHA-256 `87cd7ca0b620138599c195d477a728e05910e28dc02082b325d78f13588acced`。配布sourceや端末情報は公開しない。
- `composer.*` / `aichat.*` の登録33 command IDから、Inline専用1件と所属未確定2件を除く30件に、他namespaceの8件とpicker内2件を加え、**in-IDE所属を静的確認した登録は40件**。下の登録表は未確定/対象外も含む43行。OS別・副キー・条件別の件数をこの40件に加算しない。
- 入力部品は別枠で17操作群を記録し、うち15群のin-IDE経路を静的確認した。標準text操作を1群としているため「15 shortcut」や「全キー数」とは呼ばない。共有部品6群、公式のみ/動的登録4候補も未確定範囲として残す。全行のGUI観察・passは0件。
- 362 Action descriptorの抽出は、動的登録・機能条件・共有部品の実際の配置・全popup内キーまでの網羅証明ではない。全対象の確定には固定版のKeyboard Shortcutsとin-IDE実画面の照合が必要。
- [JetBrains AI Assistant](https://www.jetbrains.com/help/ai-assistant/ai-keyboard-shortcuts.html)の既存Keymap機能と、[Cursor ACP連携](https://cursor.com/docs/integrations/jetbrains)、IDE標準機能、[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)を比較対象にする。キー設定や会話のeditor表示を独自機能とは呼ばない。
- Pluginは[IDE Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html)を使い、Cursorの操作を既存タブUUID、選択中run、未保存確認、print/ACPの制約へ直接接続する。ACP/MCPにキーイベントを送る設計ではない。同等以上の判定は有効条件と実GUIの比較を待つ。

## 登録command

`M`はmacOSのCmd、Windows/LinuxのCtrl、`A`はOption/Alt。表はOSを省略せず、macOSのControlとCmdも区別する。状態はPluginのソース上の接続状況で、同等UXの合格ではない。modeのM+A+.とPlugin設定のM+,は既存Actionの副キーへ追加した。A1〜A3/P01〜P11は後述の作業区分でありIssue番号ではない。

| command ID | macOS | Windows | Linux | 有効条件・意味 | 所属 / Plugin / 次作業 |
| --- | --- | --- | --- | --- | --- |
| `cursor.toggleAgentWindowIDEUnification` | Cmd+E | Ctrl+E | Ctrl+E | IDEの空でないworkspace、isGlass=false。Agent用sidebarの左右と表示を切り替え、editor/他領域の配置も調整する。最大化とは異なる。 | in-IDE静的確認 / PluginはToolWindow左右移動と表示へ接続、IDE全体配置の差は未GUI / A1 |
| `cursor.openAgentChangesEditor` | Cmd+Shift+R | Ctrl+Shift+R | Ctrl+Shift+R | composerにfocus。選択中会話のReview Changes editorを開く。PluginはM+Shift+Rを既存の変更一覧へ接続し、editor表示面の一致は未確認。 | in-IDE静的確認 / 一部接続済み / A1 |
| `cursor.openBranchMenu` | Cmd+' | Ctrl+' | Ctrl+' | composerにfocusし、選択中会話を読み込み済みかつ空。開始時のbranch選択でありcontext添付とは別。 | in-IDE静的確認 / 能力未実装 / P06 |
| `workbench.action.toggleAgentsFromKeyboard` | Cmd+Option+J | Ctrl+Alt+J | Ctrl+Alt+J | 補助windowにfocusがなくisGlass=false。Agent表示中ならfocus位置によらず隠し、非表示なら前の表示領域を戻す。 | in-IDE静的確認 / PluginのToolWindow表示切替へ接続、実focusは未GUI / A1 |
| `aichat.newfollowupaction` | Cmd+Y | Ctrl+Shift+Y | Ctrl+Y | global登録。選択chatを解決し、worktree chatを除外、選択codeを追加して入力へfocusする。WindowsだけShift付き。 | in-IDE静的確認 / pane入口を接続済み、worktree/editor/過去入力の分岐は未達・未GUI / A1 |
| `composer.selectPreviousComposer` | Cmd+Option+Left | Ctrl+Alt+Left | Ctrl+Alt+Left | composer/agentsPaneにfocus、一般editor textにfocusなし。weight410。sidebar状態によって修飾キーを離すまでnavigation modeになる。 | in-IDE静的確認 / sidebar非表示相当の前後移動へ接続、sidebar navigation modeは未実装・未GUI / A2 |
| `composer.selectNextComposer` | Cmd+Option+Right | Ctrl+Alt+Right | Ctrl+Alt+Right | 上記の次conversation。tab表示順・履歴順との一致は未GUI。 | in-IDE静的確認 / sidebar非表示相当の前後移動へ接続、sidebar navigation modeは未実装・未GUI / A2 |
| `composer.selectPreviousSubComposerTab` | Cmd+Option+Left | Ctrl+Alt+Left | Ctrl+Alt+Left | composerにfocus、weight200。切替eventの登録は確認したが受信側とin-IDE所属は未確定。 | 未確定 / 所属未確定 / U-SUB |
| `composer.selectNextSubComposerTab` | Cmd+Option+Right | Ctrl+Alt+Right | Ctrl+Alt+Right | 上記の次SubComposer。通常conversationのweight410との実際の優先も未確定。 | 未確定 / 所属未確定 / U-SUB |
| `aichat.newchataction` | Cmd+L | Ctrl+L | Ctrl+L | isGlass=false。M+Iと同じ入口へ転送する。選択code・現在focus・既定配置で新規/既存chatの動作が変わる。 | in-IDE静的確認 / pane入口を接続済み、worktree/editor/過去入力の分岐は未達・未GUI / A1 |
| `composer.startComposerPrompt` | Cmd+I | Ctrl+I | Ctrl+I | global登録でAgent入口へ転送。ToolWindow表示/focusを既存APIで接続する。 | in-IDE静的確認 / pane入口を接続済み、worktree/editor/過去入力の分岐は未達・未GUI / A1 |
| `composer.newAgentChat` | Cmd+Shift+L / Cmd+Shift+I | Ctrl+Shift+L / Ctrl+Shift+I | Ctrl+Shift+L / Ctrl+Shift+I | global登録。in-IDEでは空のAgentを再利用または作成し、focus済みの空Agentなら隠す場合がある。単純な現在chatへの選択追加とは異なる。 | in-IDE静的確認 / pane入口を接続済み、worktree/editor/過去入力の分岐は未達・未GUI / A1 |
| `composer.createNewComposerTab` | Cmd+T / Cmd+N | Ctrl+T / Ctrl+N | Ctrl+T / Ctrl+N | M+T: composer/view/composer editor/agentsPane。M+Nはさらに一般editor text・files explorer・explorer viewletのfocusを除く。 | in-IDE静的確認 / 一部接続済み / A1 |
| `composer.showComposerHistory` | Cmd+Option+' | Ctrl+Alt+' | Ctrl+Alt+' | 登録にwhenなし。Pluginの現在のpanel内限定より広い入口を持つ。 | in-IDE静的確認 / 一部接続済み / A1 |
| `composer.closeComposerTab` | Cmd+W | Ctrl+W | Ctrl+W | composerまたはaichat viewにfocusし、一般editor textにfocusなし。 | in-IDE静的確認 / 一部接続済み / A1 |
| `composer.cancelComposerStep` | Cmd+Shift+Backspace | Ctrl+Shift+Backspace | Ctrl+Shift+Backspace | panel/viewにfocus。保留decision拒否→実行中run取消→表示中の変更却下の順に分岐。PluginのStopだけでは全分岐を満たさない。 | in-IDE静的確認 / 一部接続済み / A3 |
| `composer.acceptComposerStep` | Cmd+Enter / Cmd+Option+Enter | Ctrl+Enter / Ctrl+Alt+Enter | Ctrl+Enter / Ctrl+Alt+Enter | panel/viewにfocus。送信がM+Enter設定ならM+A+Enter、それ以外ならM+Enter。保留decision groupまたはnotificationを受理する。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A3 |
| `composer.approvePendingShellToolDecision` | Enter | Enter | Enter | isGlass=falseかつshell decision用contextが有効。providerの提示した許可optionだけに対応する。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A3 |
| `composer.approvePendingShellToolDecisionAllowlist` | Shift+Enter | Shift+Enter | Shift+Enter | shell allowlist用contextが有効。ACP allow_alwaysとCursor shell allowlistの意味を同一化しない。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A3 |
| `composer.skipPendingShellToolDecision` | Escape | Escape | Escape | isGlass=falseかつshell decision用contextが有効。拒否と取消の意味を保ち、一般Escapeを奪わない。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A3 |
| `composer.cancelComposerStepInputFocused` | Control+C | Ctrl+Shift+Backspace | Ctrl+Shift+Backspace | panel/view条件で取消可能な実行を止める。名称から入力部品focus限定とは推測しない。macOSだけControl+C。Pluginは既存Stopの副キーとして接続し、選択中runとIME/popup保護を再利用。 | in-IDE静的確認 / 一部接続済み / A3 |
| `composer.cancelTerminalToolCall` | Shift+Backspace | Shift+Backspace | Shift+Backspace | panel/viewの選択中composer。保留terminal decisionを拒否するかterminal streamだけを取り消す。全run Stopと別。 | in-IDE静的確認 / 能力未実装 / P05 |
| `composer.triggerCreateWorktreeButton` | Cmd+Shift+Enter | Ctrl+Shift+Enter | Ctrl+Shift+Enter | composerにfocus、空会話かつ非空draft。初回submitを呼ぶ登録であり、named worktree作成を保証しない。 | in-IDE静的確認 / 能力未実装 / P06 |
| `composer.openModeMenu` | Cmd+. / Cmd+Option+. / Shift+Tab | Ctrl+. / Ctrl+Alt+. / Shift+Tab | Ctrl+. / Ctrl+Alt+. / Shift+Tab | composer/view/composer editor/mode menuにfocus、一般editor textとquick inputを除く。Shift+Tabは入力局所処理との優先が未確定。 | in-IDE静的確認 / 一部接続済み / A1 |
| `composer.toggleChatAsEditor` | Cmd+D | Ctrl+D | Ctrl+D | composerにfocus、一般editor textにfocusなし。同一chatのeditor表示を切り替える。 | in-IDE静的確認 / 能力未実装 / P07 |
| `composer.previousChatTab` | Cmd+[ | Ctrl+[ | Ctrl+[ | composerにfocus、一般editor textにfocusなし。複数タブは表示順、1タブは全会話の更新日時順で循環する静的経路を確認。実GUIは未確認。 | in-IDE静的確認 / 複数/単一タブ分岐へ接続、未GUI / A2 |
| `composer.nextChatTab` | Cmd+] | Ctrl+] | Ctrl+] | 上記の次tab。 | in-IDE静的確認 / 複数/単一タブ分岐へ接続、未GUI / A2 |
| `composer.openModelToggle` | Cmd+/ / Cmd+Option+/ | Ctrl+/ | Ctrl+/ / Ctrl+Alt+/ | composerにfocus、一般editor textにfocusなし。Windowsはbinding全体の置換なのでAlt副キーを継承しない。 | in-IDE静的確認 / 一部接続済み / A1 |
| `composer.cycleModelParameter` | Cmd+Shift+/ | Ctrl+Alt+/ / Ctrl+Shift+/ | Ctrl+Shift+/ | 同じfocus条件、weight201。広告されたcycleable parameterで複数値を持つものを循環する。WindowsではAll Agentsのweight200とも重なる。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / P10 |
| `composer.openAddContextMenu` | Cmd+Option+P | Ctrl+Alt+P | Ctrl+Alt+P | composerにfocus、一般editor textにfocusなし。context候補を開く。 | in-IDE静的確認 / 一部接続済み / A1 |
| `composer.toggleVoiceDictation` | Cmd+Shift+Space | Ctrl+Shift+Space | Ctrl+Shift+Space | isGlass=false、terminal focusなし。選択chatの音声入力を開始/停止する。 | in-IDE静的確認 / 能力未実装 / P09 |
| `composer.cancelVoiceDictation` | Escape | Escape | Escape | composer editorまたは表示中aichat viewで録音中。処理中も取消handlerはあるがwhenは録音中だけなので到達は未GUI。 | in-IDE静的確認 / 能力未実装 / P09 |
| `composer.find.focus` | Cmd+F | Ctrl+F | Ctrl+F | composerにfocus、会話内findにはfocusなし。 | in-IDE静的確認 / 能力未実装 / P01 |
| `composer.find.hide` | Escape | Escape | Escape | 会話内findにfocus。検索欄が可視という条件だけではない。 | in-IDE静的確認 / 能力未実装 / P01 |
| `composer.find.next` | F3 / Enter | F3 / Enter | F3 / Enter | 会話内findにfocus。次の本文matchへ移動する。 | in-IDE静的確認 / 能力未実装 / P01 |
| `composer.find.previous` | Shift+F3 / Shift+Enter | Shift+F3 / Shift+Enter | Shift+F3 / Shift+Enter | 会話内findにfocus。前の本文matchへ移動する。 | in-IDE静的確認 / 能力未実装 / P01 |
| `workbench.action.openAgentsView` | Control+Shift+S | Ctrl+Shift+/ | Ctrl+Shift+/ | Background Composer Window以外。統合sidebarを表示するかAll Agents pickerを開く。macOSはControlを使う。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| `workbench.action.quickOpenPreviousRecentlyUsedAgent` | Control+Tab | Ctrl+Tab | Ctrl+Tab | agentsPane/composerにfocus、chatEditorGroup.enabledではなくBackground Composer Windowでもない。最近使用順picker。 | in-IDE静的確認 / local会話の最近使用一覧へ接続、保存会話を含む・未GUI / A2 |
| `workbench.action.quickOpenLeastRecentlyUsedAgent` | Control+Shift+Tab | Ctrl+Shift+Tab | Ctrl+Shift+Tab | 上記と同じ条件で初期選択を逆順にする。tab表示順とは別。 | in-IDE静的確認 / local会話の最近使用一覧へ接続、保存会話を含む・未GUI / A2 |
| `composer.sendToAgent` | Cmd+L | Ctrl+L | Ctrl+L | editor prompt barが存在しfocus中。Inline Edit→Agent境界であり、通常panel内操作の対象外。入口として採るかは別判断。 | 対象外 / 通常panel対象外 / X-INLINE |
| `aiSettings.action.open` | Cmd+Shift+J / Cmd+, | Ctrl+Shift+J / Ctrl+, | Ctrl+Shift+J / Ctrl+, | M+Shift+Jはglobal、M+,はisGlass=false。固定配布版では両方Cursor Settingsを開き、一般IDE設定はM+Shift+,。公式概要との差を保持する。 | in-IDE静的確認 / 一部接続済み / A1 |
| `workbench.action.quickOpenNavigateNextInAgentsPicker` | Control+Tab | Ctrl+Tab | Ctrl+Tab | quick open内のAgents MRU picker、weight250。picker内だけで次へ進む。 | in-IDE静的確認 / local会話の最近使用一覧へ接続、保存会話を含む・未GUI / A2 |
| `workbench.action.quickOpenNavigatePreviousInAgentsPicker` | Control+Shift+Tab | Ctrl+Shift+Tab | Ctrl+Shift+Tab | 上記の前へ移動。通常editor切替には流さない。 | in-IDE静的確認 / local会話の最近使用一覧へ接続、保存会話を含む・未GUI / A2 |

panel内の初期接続は`CursorAgent.NewChat`、`CloseChat`、`PreviousChat`、`NextChat`、`Stop`、`ModeMenu`、`ModelMenu`、`AddContext`、`History`、`Changes`、`Settings`の11 Action。project全体から使う`CursorAgent.TogglePanel`と`CursorAgent.SwapPanelSide`に`OpenChat`、`FollowUp`、`NewAgent`を加えた5 Actionを別に登録する。ToolWindowがまだ生成されていなくても標準content生成/表示へ接続する。`RecentChat` / `LeastRecentChat`の2 Actionもpanel内と専用一覧内へ接続し、修飾左右の`PreviousAgent` / `NextAgent`も別Actionとして接続し、panel内は計15 Actionとなる。設定・会話owner・未保存確認等は既存経路を再利用する。表の一部接続だけで全分岐を満たしたとは扱わない。

## 入力部品内の操作

| 操作群 | キー | 条件・意味 | 所属 / Plugin / 次作業 |
| --- | --- | --- | --- |
| INPUT-RESET | M+R | 入力にfocus、Shiftなし。M+R→onReset→新規会話（新tab指定false）の静的経路を確認。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
| INPUT-LEGACY-NEW | M+N | 入力にfocus、Shiftなし。composer.createNewの現在の既定登録は発見できず、legacy/custom条件付き経路として未確定。 | 未確定 / 既存機能へ接続/差分確認が必要 / A1 |
| INPUT-MESSAGE-NEXT | Tab | ghost候補位置・mention menuがなくhuman messageを編集中。次のhuman messageまたは末尾入力へ移動。通常の末尾入力ではnative focusへ渡す。 | in-IDE静的確認 / 能力未実装 / P02 |
| INPUT-MESSAGE-PREVIOUS | Shift+Tab | 同じ候補保護に加えてtool review待ちでない。末尾入力からも前のhuman messageへ移動。公式mode回転・登録mode menuとの優先は未GUI。 | in-IDE静的確認 / 能力未実装 / P02 |
| INPUT-UP | ArrowUp | caretが入力境界、Shift/候補menuなし。保留reviewの前option、最新queue編集、過去human message等へ状態別に移る。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / P02 |
| INPUT-DOWN | ArrowDown | caretが入力境界、Shift/候補menuなし。保留reviewの次option、steering条件下の空末尾入力からqueue focus等へ分岐。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| INPUT-ESCAPE | Escape | model nudge、個別Escape処理、preview/review状態の解除後に入力focus解除等へ分岐。popup/IMEを優先する。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
| INPUT-MOD-ENTER | M+Enter; modifier+Alt+Enter branch requires routing check | repeat抑止、tool review待ちは別処理。質問/decision回答、送信/queue、空入力時のPlan review/変更承認/Apply worktreeへ状態別に分岐。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A3 |
| INPUT-SHIFT-MOD-ENTER | M+Shift+Enter | 空draftで変更reviewがあれば全承認/Apply worktree、実行中選択toolformerなら取消、それ以外は別submit。初回draftのglobal登録と分ける。 | in-IDE静的確認 / 能力未実装 / P08 |
| QUEUE-NAVIGATION | ArrowUp / ArrowDown | steering機能が有効でqueue list本体にfocus。上下の項目移動、末尾を越えたら入力へ戻る。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| QUEUE-ESCAPE | Escape | 同じqueue listのfocus条件で入力へ戻る。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| QUEUE-SUBMIT | Enter / M+Enter / M+Alt+Enter | Shiftなし。送信キー設定・主修飾キー・Altで送信とsteer/interruptの動作を分ける。Pluginの次turn予約で同一turn入力を代用しない。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| QUEUE-EDIT | ArrowRight / Space | queue listにfocusし項目を選択、機能条件を満たすと編集へ入る。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| QUEUE-REMOVE | macOS Cmd+Backspace; Windows/Linux Ctrl+Delete | 同じqueue条件、他の修飾キーなし。macOSはCmd+Backspace、Windows/LinuxはCtrl+Delete。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| INPUT-TRIGGERS | @ / / | 通常の入力文字とcaret/候補状態で処理する。global Actionに@や/を奪わせない。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
| INPUT-NATIVE-TEXT | Select all / undo / redo / cut / copy / paste / newline / caret keys | 入力editorまたはfocus中native controlの選択・undo/redo・cut/copy/paste・改行・caret。既存部品を再利用し、キー組合せ総数とは数えない。 | in-IDE静的確認 / 既存native部品 / A1 |
| INPUT-DEFAULT-SUBMIT | Enter / Shift+Enter; submit-on-modifier setting | 公式in-IDE概要と汎用入力dispatchの候補。固定版の全入力実装での分岐は未確定。Pluginの実行中Enter→queueは未接続。 | 未確定 / 既存機能へ接続/差分確認が必要 / A2 |

## 未確定範囲と公式との差

| 対象 | 未確定範囲・次の確認 |
| --- | --- |
| 共有PromptInputKeymap | Enter/Shift+Enter/M+Enter/M+A+Enter/Escape/Tab/上下、選択/undo/redo/削除等を確認したが、固定版in-IDEの実配置と機能条件が未確定。確認済み入力群へ混ぜない。 |
| 共有入力候補 | Tabで受理、Escapeで解除する共有部品と、in-IDEの既存ghost候補が同じ実装/意味か未確定。 |
| Debug入力 | M+Enterのcallbackはあるが、instrumentationと入力の全契約は未確認。#281の研究完了をDebug機能実装済みとしない。 |
| Canvas preview | M+Enterのcomposer preview contextは発見したが、canvas/browser/編集previewのどの表示面か未確定。 |
| shell用context | 許可/allowlist/拒否Actionは確認したが、contextの設定元と優先、実GUIでの成立は未確認。 |
| 子popup/各カード | mention、slash、model、mode、branch、history、find、permission、question、Plan、changesの上下左右/Enter/Space/Escape/Tab等を全件照合する。共有handlerの存在だけで対象panelに含めない。 |
| clipboard（公式） | M+Vのcontext付きpasteとM+Shift+Vのplain paste。固定版のclipboard形式・提供元を確認する。普通のpasteや画像添付だけで同等としない。 |
| 選択codeの検索（公式） | 選択code側のM+Enterによるcodebase検索付きchat。入力中の送信や一般の選択追加と区別し、固定版の対象handlerと公開検索契約を確定する。 |
| 入力focus解除（公式） | Escapeの入力callbackは静的確認したが、popup/voice/find/permission/IMEとの優先は未GUI。 |
| 動的・未割当command | cycleMode/cycleModel/入力転送commandの参照は既定shortcutの証拠ではない。Keyboard Shortcuts全一覧でcustom/未割当と既定キーを分ける。 |

特に次は初期一覧からの訂正で、GUIで差を確認する。

- M+Lは`aichat.newchataction`（Open Chat）。`composer.cancelChat`への初期抽出の対応は誤り。M+Iと同じ入口を使い、選択やfocusで新規/既存が分岐する。M+Shift+L/Iも単純な既存chatへの選択追加とは異なる。
- Windowsのmodel menuはCtrl+/だけ。Ctrl+Alt+/とCtrl+Shift+/はparameter変更であり、Mac/Linuxのmodel menu副キーをWindowsへ継承してはいけない。Pluginは標準Windows KeymapをCtrl+/だけにし、Linuxの副キーは`Default for XWin`へ定義した。parameter変更の接続はP10で未達。
- M+Rは入力部品から新規会話への静的経路を確認した。入力局所の条件を保って接続する。
- Shift+Tabは公式概要のmode回転、Action登録のmode menu、入力内の前human message移動という3経路がある。[Plan Mode公式説明](https://cursor.com/docs/agent/plan-mode)との版/focus差も残し、GUI前に一つへ決めつけない。現行の逆focus移動も同等passではない。
- M+Shift+Enterは初回draft submit、空入力の変更承認/Apply worktree、取消等で意味が分かれる。すべてを「新しいworktreeで開始」としない。
- 固定版のM+,はCursor Settings、一般IDE設定はM+Shift+,。公式概要のGeneral settingsと異なるため、既存IDEの割当を一括上書きしない。
- in-IDEのEnter予約/M+Enter即時入力と、独立Agents Window/Webの新しいsteer操作を混ぜない。JetBrainsのSend Nowが実行を中断する場合、非中断の同一turn入力と同等には扱わない。

## 実装と不足能力の追跡

A1は既存操作のOS/focus/入口/設定、A2は既存navigation/queueと最近使用順の移動、A3は既存permission/question/Plan等の保留要求に対するkeyboard操作を#212内で進める。既存機能へキーを付けるだけのIssueを量産しない。

P01は不足能力とin-IDE経路を確認し、[#518](https://github.com/shinma06/cursor-in-android-studio/issues/518)へ実分解した（native sub-issue、未実装）。その他は分割候補で、能力と公開契約の不足を確認したものを実際のsub-issue/必要な依存として登録する。閉じた研究Issueを実装済み扱いせず、#212の全対応条件を減らさない。

| 区分 | 不足能力・有限範囲 | 既存再利用・先に必要な確認 |
| --- | --- | --- |
| P01 | [#518](https://github.com/shinma06/cursor-in-android-studio/issues/518): 開いている会話の本文findと次/前/解除 | #45の保存履歴検索と区別。大文字小文字/単語単位/正規表現、一致箇所の強調と位置/総数、長い本文/不正な式の応答性、stream・タブ所有、検索focusとIME/送信競合を検証。実装・GUIは未達。 |
| P02 | 過去human messageの選択/編集と前後移動 | #42/#205のaccessibilityとIME、draftを保ち、過去message再送範囲とShift+Tabの優先を確定する。 |
| P03 | コピー元を識別できるcodeのcontext付き貼付け | #24/#343を再利用。公開clipboard形式で得られた出典だけを使い、path/range/versionを推測しない。 |
| P04 | 同一実行turnへの即時入力 | #278は公開method待ちの研究完了。次turn queueやStop後再送を代替にしない。公開契約と有限live証拠が必要。 |
| P05 | 実行中terminal toolだけの取消 | #147/#297/#300のrun取消/permissionと区別。providerのtool ID付き取消契約が必要。 |
| P06 | 初回draftのbranch/native Git worktree選択 | #301/#39のroot所有を保持。ISOLATED作業コピーをGit worktreeと呼ばず、公開起動・setup/cancel/cleanupを確定。 |
| P07 | 同一Agent会話のToolWindow/editor表示切替 | #156/#213を再利用。chat ID/run/draft/contextを二重化せず、close/reopen/focus/別projectを検証。 |
| P08 | 保留変更の一括承認/却下とworktree適用 | #47/#308の事後Diff/Revertと分離。公開された未適用提案のsnapshot/所有契約が前提。 |
| P09 | Agent Voiceの録音開始/停止/取消 | #99はOS dictation研究でありVoice実装ではない。録音権限、選択chat所有、失敗と保存寿命を含め採用経路を確定。 |
| P10 | 広告されたmodel parameterの循環選択 | #43のexact variant IDを維持。広告された値/順序のみを使い、ACP busyとWindowsのキー優先を検証。 |
| P11 | 選択codeからcodebase検索付きchatを開く | #24の一般選択添付と区別。固定版の操作と公開検索意味を確定し、非公開flagや独自indexを発明しない。 |

#48のqueue、#97の送信設定、#258/#404のSkills/command候補、#156のopened chats、#213の最後tab閉鎖等を再利用する。これらの実装Issueがclosedでも、対応QAやmain反映は未達の場合がある。SubComposerの所属未確定からside chat能力を推測して起票しない。

## Keymap・競合・完了条件

panel内15 Actionの既定キーは`plugin.xml`でWindows/Linux、旧Mac OS X、Mac OS X 10.5+に定義する。model menuはWindowsの`$default`にCtrl+/、Linuxの`Default for XWin`にCtrl+/とCtrl+Alt+/を定義し、GNOME/KDEはXWinから継承する。固定SDK `AI-262.9437.185.2621.16467767`の同梱Keymap XMLと`DefaultKeymap.getDefaultKeymapName`で、この親子関係とOS別の既定選択を確認した。ユーザーが別OS系Keymapを選んだ場合は選択したKeymapに従い、OS判定でユーザー割当を強制変更しない。MacのCtrl継承を置換し、panel-local登録も同じActionのshortcut setを使う。ユーザーのKeymap変更/削除を尊重する。OS差の静的照合だけでは全対応・GUI合格とは呼ばない。

M+N/T/W、M+[/]、M+/、M+Shift+J等はIDE既存操作と競合する。15個のpanel内local Actionは一般editorの操作を奪わない。M+A+J（表示切替）、M+E（左右移動）、M+L/I（開く）、M+Y（WindowsはCtrl+Shift+Y、入力へ戻る）、M+Shift+L/I（New Agent）はproject内のglobal入口で、M+EのRecent FilesやM+L/I/Y等のIDE操作とは意図した割当競合が生じる。IDE Keymapで変更/解除でき、別projectのToolWindowを操作しない。IME、候補、子popupでは既存入力処理を優先し、selected tab/run、ACP busy、破棄済みpanel、late event、未保存確認を保持する。OS/US-JIS配列、custom Keymap変更、実際の優先はGUIで照合する。

表示切替はToolWindowのvisible状態を基準にし、会話・draft・runを生成/終了せずnative show/hideへ接続する。左右移動はnative anchorのLEFT/RIGHTを反転し、TOP/BOTTOMからはLEFTへ移して表示する。ToolWindowのtype・split・他のIDEパネルは変更しない。固定SDKの標準ToolWindow移動もanchorを変更する経路であり、Floating/Windowed時の実配置と、Cursorのunified sidebar/auxiliary/editor全体とのUX差はGUI照合に残す。単一Agent ToolWindowへの対応だけで、Cursorの全配置が同じと主張しない。

チャット入口は固定版のpane分岐を接続した。M+L/Iは選択中チャットを開き、パネルにfocusがあれば隠す。非表示から戻る際、既存の明示選択があればeventの選択コードを追加しない。入力へ戻る操作は選択中チャットを保持して選択コードを追加し、入力欄へfocusする。New Agentは表示順で最初の空Agentを再利用し、なければ新しいAgentを作る。focus済みの空Agentは最後の表示/focus要求から500ms以上経っていれば隠す。表示/focus要求ごとに単調時計でこの間隔を更新する。既存view/draft/contextは再作成せず、新規タブだけをAgent modeにし、共有設定を変更しない。

空判定は送信時保存のdraftではなく現在の入力欄を読み、本文・run・予約・選択command・未送信画像がある会話、本文来歴不明のlegacy会話を再利用対象から外す。画像/commandを空扱いしない条件はPluginの入力保護であり、Cursorとの完全一致は未確認。明示contextだけの空Agentは添付を保持して再利用する。選択コードはcontent生成/表示前のevent editorから取得し、遅いfocus callbackは同じview・世代・生存状態を再確認する。native Git-worktree除外（P06）、editor表示（P07）、過去入力から末尾への移動（P02）は能力未実装のため残り、`ISOLATED`をnative Git-worktreeと推測しない。根拠は上記固定版のentry handler、`isComposerEmpty`、`showAndFocus`、`wasRecentlyShown`と500ms定数の静的確認であり、実GUI合格ではない。

最近使用一覧は全OSでControl+Tab / Control+Shift+Tab（MacでもCmdではない）。固定版のruntime訪問リストは最大10件で、未登録の候補を更新日時の新しい順で10件まで補う。通常開始は2番目、逆方向開始は末尾を選び、一覧内では同じキーで次/前へ循環する。タブの表示順とは異なり、閉じた保存会話も候補に含む。Pluginはproject内のPRINT/ACP本文・旧print metadata・未保存の開いたタブを統合し、訪問順は表示の確定時だけ更新する。タブ並替え、stream更新、候補上の移動は順序を変えない。本文IDと旧print provider IDを別の型にし、同じprovider文字列のACPをprint legacyとしてまとめない。

修飾キーの解放はIDEで設定された単一strokeのControl/Meta/Altを使い、Shiftは他の修飾キーを離した場合だけ確定に使う。複数strokeはEnterで確定する。一覧の入口と候補移動は同じAction/Keymapを共有し、別popupや通常editorに配送しない。読込み前の解放は一覧を残し、遅れて候補が来ても勝手に開かない。Esc、外部click、別window、panel非表示/破棄は取消。候補はmetadataだけを保持し、閉じた会話の本文は確定時に再読込して削除/最新保存を照合する。選択中tabや世代が変わった後、IME変換や別popupが始まった後の読込み結果は捨てる。本文表示とprovider再開/Revertの可否は既存履歴経路を共有する。

根拠は上記固定版のMRU service/provider、Quick Accessの初期選択、Quick Pickの修飾keyup handlerと固定SDKのnative popup API。GUI操作は未実施。共通providerにはBackground候補、未読/保留要求の印もあり、in-IDEでの実データ条件と公開取得契約は未確定・未接続として残す。All Agentsは別の`chat:` provider（更新日時/検索順、最大200件）とsidebar表示制御を使うため、最近使用一覧や既存opened chatsで代替済みとは扱わない。sidebar表示中のM+A+左右のnavigation modeも別経路で、次の実装対象として残る。

前後移動は固定版の`selectNextComposer` / `selectPrevComposer`と`Kku`の静的経路を確認した。選択タブが複数なら表示順で循環し、1つなら全会話を更新日時（なければ作成日時）の降順で循環する。MRUの10件制限は使わない。M+[/]と、sidebar非表示時のM+A+左右は同じ経路に接続する。M+A+左右は専用Actionなので、将来のsidebar navigation modeとKeymapを混同しない。

Pluginは1タブ時に保存履歴を背景読込みし、現行tab・世代・IME/子popup・削除を再確認する。保存済みで作業が残らないタブは置き換えて連続移動を保つ。run token・保存失敗/保留・queue・下書き・明示context・command・画像・手動名があるタブは保持し、対象を追加タブで開く。固定版のrunning switchも新タブを選ぶ実装で、単に取消/確認分岐の文字列があることから確認dialogを推測しない。Pluginはdraft等も保護対象に含めるため、その後は複数タブ移動になる差をGUIで照合する。空の未保存会話を履歴へ新規保存せず、名前/modeを推測しない。sidebar表示中の候補highlight・Control/Meta解放での確定・blur取消は別経路として残る。

1. 固定版Keyboard Shortcutsと入力/各popupを全件照合し、43登録行・17入力群・未確定共有/公式候補の所属、OS、focus、mode条件を確定する。
2. A1〜A3を既存経路へ接続し、確定した不足能力を具体Issueとnative関係へ分割する。無反応のダミーActionは追加しない。
3. [Case正本](../verification/changes/issue-212.json)で全対象の実装・条件・競合・固定build GUI証拠・main未反映を追跡する。現行panel内15 Actionとglobal5 Actionや静的件数だけで#212を閉じない。[Milestone 8](https://github.com/shinma06/cursor-in-android-studio/milestone/8)はCursor 3.23.12のIDE内Agent全ショートカットを固定範囲とし、対象全件の確定・不足能力の実装・固定build GUI・main反映までを到達条件とする。未確定行や公開契約待ちを省略しない。
