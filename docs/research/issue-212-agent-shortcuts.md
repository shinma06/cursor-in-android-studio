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
| `cursor.openBranchMenu` | Cmd+' | Ctrl+' | Ctrl+' | composerにfocusし、選択中会話を読み込み済みかつ空。branch UIへの表示要求でありcontext添付とは別。受信先の実可視性は未確認。 | in-IDE静的確認 / 能力未実装 / P06 |
| `workbench.action.toggleAgentsFromKeyboard` | Cmd+Option+J | Ctrl+Alt+J | Ctrl+Alt+J | 補助windowにfocusがなくisGlass=false。Agent表示中ならfocus位置によらず隠し、非表示なら前の表示領域を戻す。 | in-IDE静的確認 / PluginのToolWindow表示切替へ接続、実focusは未GUI / A1 |
| `aichat.newfollowupaction` | Cmd+Y | Ctrl+Shift+Y | Ctrl+Y | global登録。選択chatを解決し、worktree chatを除外、選択codeを追加して入力へfocusする。WindowsだけShift付き。 | in-IDE静的確認 / pane入口を接続済み、worktree/editor/過去入力の分岐は未達・未GUI / A1 |
| `composer.selectPreviousComposer` | Cmd+Option+Left | Ctrl+Alt+Left | Ctrl+Alt+Left | composer/agentsPaneにfocus、一般editor textにfocusなし。weight410。sidebar状態によって修飾キーを離すまでnavigation modeになる。 | in-IDE静的確認 / sidebar非表示の前後移動・表示中のlocal候補highlightへ接続、拡張候補とGUIは未達 / A2 |
| `composer.selectNextComposer` | Cmd+Option+Right | Ctrl+Alt+Right | Ctrl+Alt+Right | 上記の次conversation。tab表示順・履歴順との一致は未GUI。 | in-IDE静的確認 / sidebar非表示の前後移動・表示中のlocal候補highlightへ接続、拡張候補とGUIは未達 / A2 |
| `composer.selectPreviousSubComposerTab` | Cmd+Option+Left | Ctrl+Alt+Left | Ctrl+Alt+Left | composerにfocus、weight200。切替eventの登録は確認したが受信側とin-IDE所属は未確定。 | 未確定 / 所属未確定 / U-SUB |
| `composer.selectNextSubComposerTab` | Cmd+Option+Right | Ctrl+Alt+Right | Ctrl+Alt+Right | 上記の次SubComposer。通常conversationのweight410との実際の優先も未確定。 | 未確定 / 所属未確定 / U-SUB |
| `aichat.newchataction` | Cmd+L | Ctrl+L | Ctrl+L | isGlass=false。M+Iと同じ入口へ転送する。選択code・現在focus・既定配置で新規/既存chatの動作が変わる。 | in-IDE静的確認 / pane入口を接続済み、worktree/editor/過去入力の分岐は未達・未GUI / A1 |
| `composer.startComposerPrompt` | Cmd+I | Ctrl+I | Ctrl+I | global登録でAgent入口へ転送。ToolWindow表示/focusを既存APIで接続する。 | in-IDE静的確認 / pane入口を接続済み、worktree/editor/過去入力の分岐は未達・未GUI / A1 |
| `composer.newAgentChat` | Cmd+Shift+L / Cmd+Shift+I | Ctrl+Shift+L / Ctrl+Shift+I | Ctrl+Shift+L / Ctrl+Shift+I | global登録。in-IDEでは空のAgentを再利用または作成し、focus済みの空Agentなら隠す場合がある。単純な現在chatへの選択追加とは異なる。 | in-IDE静的確認 / pane入口を接続済み、worktree/editor/過去入力の分岐は未達・未GUI / A1 |
| `composer.createNewComposerTab` | Cmd+T / Cmd+N | Ctrl+T / Ctrl+N | Ctrl+T / Ctrl+N | M+T: composer/view/composer editor/agentsPane。M+Nはさらに一般editor text・files explorer・explorer viewletのfocusを除く。 | in-IDE静的確認 / 一部接続済み / A1 |
| `composer.showComposerHistory` | Cmd+Option+' | Ctrl+Alt+' | Ctrl+Alt+' | 登録にwhenなし。選択中composerを解決し、統合sidebarが表示中なら履歴表示を変更しない。Pluginは既存の選択中会話に対するglobal表示要求へ接続し、sidebar条件と非表示中の会話所有を保つ。 | in-IDE静的確認 / global入口へ接続・GUI pending / A1 |
| `composer.closeComposerTab` | Cmd+W | Ctrl+W | Ctrl+W | composerまたはaichat viewにfocusし、一般editor textにfocusなし。 | in-IDE静的確認 / 一部接続済み / A1 |
| `composer.cancelComposerStep` | Cmd+Shift+Backspace | Ctrl+Shift+Backspace | Ctrl+Shift+Backspace | panel/viewにfocus。保留decision拒否→実行中run取消→表示中の変更却下の順に分岐。Pluginは一意/明示focusの保留要求への拒否をStopより優先する。groupと変更却下は未達。 | in-IDE静的確認 / 要求返信へ部分接続・GUI pending / A3 |
| `composer.acceptComposerStep` | Cmd+Enter / Cmd+Option+Enter | Ctrl+Enter / Ctrl+Alt+Enter | Ctrl+Enter / Ctrl+Alt+Enter | panel/viewにfocus。送信がM+Enter設定ならM+A+Enter、それ以外ならM+Enter。保留decision groupまたはnotificationを受理する。Pluginは一意/明示focusの既存要求カードへの返信に接続し、group/notificationは未接続。 | in-IDE静的確認 / 要求返信へ部分接続・GUI pending / A3 |
| `composer.approvePendingShellToolDecision` | Enter | Enter | Enter | isGlass=falseかつshell decision用contextが有効。providerの提示した許可optionだけに対応する。 | in-IDE静的確認 / 空入力の個別allow_onceへ接続・GUI pending / A3 |
| `composer.approvePendingShellToolDecisionAllowlist` | Shift+Enter | Shift+Enter | Shift+Enter | shell allowlist用contextが有効。ACP allow_alwaysとCursor shell allowlistの意味を同一化しない。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A3 |
| `composer.skipPendingShellToolDecision` | Escape | Escape | Escape | isGlass=falseかつshell decision用contextが有効。拒否と取消の意味を保ち、一般Escapeを奪わない。 | in-IDE静的確認 / 空入力の個別reject_onceへ接続・GUI pending / A3 |
| `composer.cancelComposerStepInputFocused` | Control+C | Ctrl+Shift+Backspace | Ctrl+Shift+Backspace | panel/view条件で取消可能な実行を止める。名称から入力部品focus限定とは推測しない。macOSだけControl+C。Pluginは既存Stopの副キーとして接続し、選択中runとIME/popup保護を再利用。 | in-IDE静的確認 / 一部接続済み / A3 |
| `composer.cancelTerminalToolCall` | Shift+Backspace | Shift+Backspace | Shift+Backspace | panel/viewの選択中composer。保留terminal decisionを拒否するかterminal streamだけを取り消す。全run Stopと別。 | in-IDE静的確認 / 能力未実装 / P05 |
| `composer.triggerCreateWorktreeButton` | Cmd+Shift+Enter | Ctrl+Shift+Enter | Ctrl+Shift+Enter | composerにfocus、空会話かつ非空draft。初回submitを呼ぶ登録であり、named worktree作成を保証しない。 | in-IDE静的確認 / 初回送信を接続・GUI pending、branch/native worktree選択は未実装 / P06 |
| `composer.openModeMenu` | Cmd+. / Cmd+Option+. / Shift+Tab | Ctrl+. / Ctrl+Alt+. / Shift+Tab | Ctrl+. / Ctrl+Alt+. / Shift+Tab | composer/view/composer editor/mode menuにfocus、一般editor textとquick inputを除く。名前に反してmodeを循環する。入力のkeydownでは一致したmode ActionをTabの過去message処理より先に扱う。 | in-IDE静的確認 / Pluginの既存3 mode循環へ接続、提案分岐・拡張mode・GUIは未達 / A1 |
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
| `aiSettings.action.open` | Cmd+Shift+J / Cmd+, | Ctrl+Shift+J / Ctrl+, | Ctrl+Shift+J / Ctrl+, | M+Shift+Jはglobal、M+,はisGlass=false。固定配布版では両方Cursor Settingsを開き、一般IDE設定はM+Shift+,。Pluginはproject内global設定入口へ接続。公式概要との差を保持する。 | in-IDE静的確認 / global設定へ接続・GUI pending / A1 |
| `workbench.action.quickOpenNavigateNextInAgentsPicker` | Control+Tab | Ctrl+Tab | Ctrl+Tab | quick open内のAgents MRU picker、weight250。picker内だけで次へ進む。 | in-IDE静的確認 / local会話の最近使用一覧へ接続、保存会話を含む・未GUI / A2 |
| `workbench.action.quickOpenNavigatePreviousInAgentsPicker` | Control+Shift+Tab | Ctrl+Shift+Tab | Ctrl+Shift+Tab | 上記の前へ移動。通常editor切替には流さない。 | in-IDE静的確認 / local会話の最近使用一覧へ接続、保存会話を含む・未GUI / A2 |

panel内の初期接続は`CursorAgent.NewChat`、`CloseChat`、`PreviousChat`、`NextChat`、`Stop`、`ModeMenu`、`ModelMenu`、`AddContext`、`History`、`Changes`、`Settings`の11 Action。project全体から使う`CursorAgent.TogglePanel`と`CursorAgent.SwapPanelSide`に`OpenChat`、`FollowUp`、`NewAgent`、`AllChats`を加えた6 Actionを別に登録する。ToolWindowがまだ生成されていなくても標準content生成/表示へ接続する。`RecentChat` / `LeastRecentChat`の2 Actionもpanel内と専用一覧内へ接続し、修飾左右の`PreviousAgent` / `NextAgent`も別Actionとして接続し、入力局所の`ResetChat` / `UnfocusInput`も加え、AcceptPendingとSubmitInitialChat、ApproveTool/SkipToolも加えてpanel専用は計19 Action、Settings/Historyは同じIDを保って7/8つ目のglobal Actionへ移す。Settings/Historyは引き続きpanelにもlocal登録する。設定・会話owner・未保存確認等は既存経路を再利用する。表の一部接続だけで全分岐を満たしたとは扱わない。

## 入力部品内の操作

| 操作群 | キー | 条件・意味 | 所属 / Plugin / 次作業 |
| --- | --- | --- | --- |
| INPUT-RESET | M+R | 入力にfocus、Shiftなし。空会話の再利用、履歴後draftの引継ぎ、元ownerを保持した表示置換へ接続。 | in-IDE静的確認 / 実装済み・GUI pending。native worktree/editor分岐はP06/P07 / A1 |
| INPUT-LEGACY-NEW | M+N | 入力にfocus、Shiftなし。composer.createNewの現在の既定登録は発見できず、legacy/custom条件付き経路として未確定。 | 未確定 / 既存機能へ接続/差分確認が必要 / A1 |
| INPUT-MESSAGE-NEXT | Tab | ghost候補位置・mention menuがなくhuman messageを編集中。次のhuman messageまたは末尾入力へ移動。通常の末尾入力ではnative focusへ渡す。 | in-IDE静的確認 / 能力未実装 / P02 |
| INPUT-MESSAGE-PREVIOUS | Shift+Tab | 同じ候補保護に加えてtool review待ちでない。末尾入力からも前のhuman messageへ移動。mode Actionに一致する場合は先行keydownがこの局所処理へ進ませない。Keymap変更時のfallbackと実配送は未GUI。 | in-IDE静的確認 / 能力未実装 / P02 |
| INPUT-UP | ArrowUp | caretが入力境界、Shift/候補menuなし。保留reviewの前option、最新queue編集、過去human message等へ状態別に移る。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / P02 |
| INPUT-DOWN | ArrowDown | caretが入力境界、Shift/候補menuなし。保留reviewの次option、steering条件下の空末尾入力からqueue focus等へ分岐。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| INPUT-ESCAPE | Escape | model nudge、個別Escape処理、編集/review状態の解除後に入力focus解除等へ分岐。popup/IMEを優先する。 | in-IDE静的確認 / 予約編集取消と通常focus復帰を接続・GUI pending。過去入力/review等はP02/A3 / A1 |
| INPUT-MOD-ENTER | M+Enter; modifier+Alt+Enter branch requires routing check | repeat抑止、tool review待ちは別処理。質問/decision回答、送信/queue、空入力時のPlan review/変更承認/Apply worktreeへ状態別に分岐。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A3 |
| INPUT-SHIFT-MOD-ENTER | M+Shift+Enter | 空draftで変更reviewがあれば全承認/Apply worktree、実行中選択toolformerなら取消、それ以外は別submit。初回draftのglobal登録と分ける。 | in-IDE静的確認 / 能力未実装 / P08 |
| QUEUE-NAVIGATION | ArrowUp / ArrowDown | steering機能が有効でqueue list本体にfocus。上下の項目移動、末尾を越えたら入力へ戻る。 | in-IDE静的確認 / inline/管理一覧に接続 / A2 |
| QUEUE-ESCAPE | Escape | 同じqueue listのfocus条件で入力へ戻る。 | in-IDE静的確認 / inline/管理一覧に接続 / A2 |
| QUEUE-SUBMIT | Enter / M+Enter / M+Alt+Enter | Shiftなし。送信キー設定・主修飾キー・Altで送信とsteer/interruptの動作を分ける。Pluginの次turn予約で同一turn入力を代用しない。 | in-IDE静的確認 / Send Nowを接続・steering/GUIは未達 / A2 |
| QUEUE-EDIT | ArrowRight / Space | queue listにfocusし項目を選択、機能条件を満たすと編集へ入る。 | in-IDE静的確認 / inline入力欄と管理dialogの編集へ接続 / A2 |
| QUEUE-REMOVE | macOS Cmd+Backspace; Windows/Linux Ctrl+Delete | 同じqueue条件、他の修飾キーなし。macOSはCmd+Backspace、Windows/LinuxはCtrl+Delete。 | in-IDE静的確認 / 既存の予約削除へ接続 / A2 |
| INPUT-TRIGGERS | @ / / | 通常の入力文字とcaret/候補状態で処理する。global Actionに@や/を奪わせない。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
| INPUT-NATIVE-TEXT | Select all / undo / redo / cut / copy / paste / newline / caret keys | 入力editorまたはfocus中native controlの選択・undo/redo・cut/copy/paste・改行・caret。既存部品を再利用し、キー組合せ総数とは数えない。 | in-IDE静的確認 / 既存native部品 / A1 |
| INPUT-DEFAULT-SUBMIT | Enter、またはM+Enter設定。Shift+Enter等の改行はnative入力へ委譲。 | 固定版の通常queue policyとgenerating時の登録経路を確認。Pluginは設定済み送信キーを実行中の次turn予約へ接続する。保留decision、steer/interruptと各特殊submit分岐は別途照合する。 | in-IDE通常queue経路を静的確認 / 次turn予約へ接続・全分岐/GUIは未達 / A2 |

## 未確定範囲と公式との差

| 対象 | 未確定範囲・次の確認 |
| --- | --- |
| 共有PromptInputKeymap | Enter/Shift+Enter/M+Enter/M+A+Enter/Escape/Tab/上下、選択/undo/redo/削除等を確認したが、固定版in-IDEの実配置と機能条件が未確定。確認済み入力群へ混ぜない。 |
| 共有入力候補 | Tabで受理、Escapeで解除する共有部品と、in-IDEの既存ghost候補が同じ実装/意味か未確定。 |
| Debug入力 | M+Enterのcallbackはあるが、instrumentationと入力の全契約は未確認。#281の研究完了をDebug機能実装済みとしない。 |
| Canvas preview | M+Enterのcomposer preview contextは発見したが、canvas/browser/編集previewのどの表示面か未確定。 |
| shell用context | eYb/tYbで空draft・画像/動画なし・過去入力編集中ではない・blocking判断・対象review・allowlist候補/設定gateを静的確認。名称と異なりMCP等のreviewも含む。公開ACPとの対応、優先と実GUIは未確認。 |
| 子popup/各カード | mention、slash、model、mode、branch、history、find、permission、question、Plan、changesの上下左右/Enter/Space/Escape/Tab等を全件照合する。共有handlerの存在だけで対象panelに含めない。 |
| clipboard（公式） | M+Vのcontext付きpasteとM+Shift+Vのplain paste。固定版のclipboard形式・提供元を確認する。普通のpasteや画像添付だけで同等としない。 |
| 選択codeの検索（公式） | 選択code側のM+Enterによるcodebase検索付きchat。入力中の送信や一般の選択追加と区別し、固定版の対象handlerと公開検索契約を確定する。 |
| 入力focus解除（公式） | Escapeの通常blur/Terminal復帰と予約編集取消を接続した。popup/voice/find/permission/IMEとの全分岐・優先は未GUI。 |
| 動的・未割当command | cycleMode/cycleModel/入力転送commandの参照は既定shortcutの証拠ではない。Keyboard Shortcuts全一覧でcustom/未割当と既定キーを分ける。 |

特に次は初期一覧からの訂正で、GUIで差を確認する。

- M+Lは`aichat.newchataction`（Open Chat）。`composer.cancelChat`への初期抽出の対応は誤り。M+Iと同じ入口を使い、選択やfocusで新規/既存が分岐する。M+Shift+L/Iも単純な既存chatへの選択追加とは異なる。
- Windowsのmodel menuはCtrl+/だけ。Ctrl+Alt+/とCtrl+Shift+/はparameter変更であり、Mac/Linuxのmodel menu副キーをWindowsへ継承してはいけない。Pluginは標準Windows KeymapをCtrl+/だけにし、Linuxの副キーは`Default for XWin`へ定義した。parameter変更の接続はP10で未達。
- M+Rは入力欄だけで新しい会話へ移る。本文消去や追加tab作成だけに置換せず、後述の再利用/表示置換/下書き条件へ接続する。
- Shift+Tabは[Plan Mode公式説明](https://cursor.com/docs/agent/plan-mode)のmode回転と整合する固定版の実handlerを確認した。`openModeMenu`という名前だけでmenu表示とした初期解釈を修正する。先行keydownがmode Actionへの一致を検出した場合は、Tabによる過去human message移動へ進まない。Keymap変更/条件不一致時のP02 fallbackは別に残し、静的優先を実OSの配送passへ読み替えない。
- M+Shift+Enterは初回draft submit、空入力の変更承認/Apply worktree、取消等で意味が分かれる。すべてを「新しいworktreeで開始」としない。
- 固定版のM+,はCursor Settings、一般IDE設定はM+Shift+,。公式概要のGeneral settingsと異なるため、既存IDEの割当を一括上書きしない。
- in-IDEのEnter予約/M+Enter即時入力と、独立Agents Window/Webの新しいsteer操作を混ぜない。JetBrainsのSend Nowが実行を中断する場合、非中断の同一turn入力と同等には扱わない。

## 実装と不足能力の追跡

A1は既存操作のOS/focus/入口/設定、A2は既存navigation/queueと最近使用順の移動、A3は既存permission/question/Plan等の保留要求に対するkeyboard操作を#212内で進める。既存機能へキーを付けるだけのIssueを量産しない。

P01は不足能力とin-IDE経路を確認し、[#518](https://github.com/shinma06/cursor-in-android-studio/issues/518)へ実分解した（native sub-issue、未実装）。その他は分割候補で、能力と公開契約の不足を確認したものを実際のsub-issue/必要な依存として登録する。閉じた研究Issueを実装済み扱いせず、#212の全対応条件を減らさない。

| 区分 | 不足能力・有限範囲 | 既存再利用・先に必要な確認 |
| --- | --- | --- |
| P01 | [#518](https://github.com/shinma06/cursor-in-android-studio/issues/518): 開いている会話の本文findと次/前/解除 | #45の保存履歴検索と区別。大文字小文字/単語単位/正規表現、一致箇所の強調と位置/総数、長い本文/不正な式の応答性、stream・タブ所有、検索focusとIME/送信競合を検証。実装・GUIは未達。 |
| P02 | 過去human messageの選択/編集と前後移動 | #42/#205のaccessibilityとIME、draftを保持する。下記の履歴変更契約が未確定であり、末尾への通常再送に置換しない。 |
| P03 | コピー元を識別できるcodeのcontext付き貼付け | #24/#343を再利用。公開clipboard形式で得られた出典だけを使い、path/range/versionを推測しない。 |
| P04 | 同一実行turnへの即時入力 | #278のACP/print調査とSDKの公開`run.steer()`を区別する。下記の別方式候補を評価し、次turn queueやStop後再送を代替にしない。 |
| P05 | 実行中terminal toolだけの取消 | #147/#297/#300のrun取消/permissionと区別。providerのtool ID付き取消契約が必要。 |
| P06 | 初回draftのbranch/native Git worktree選択 | #301/#39のroot所有を保持。ISOLATED作業コピーをGit worktreeと呼ばず、公開起動・setup/cancel/cleanupを確定。 |
| P07 | 同一Agent会話のToolWindow/editor表示切替 | #156/#213を再利用。chat ID/run/draft/contextを二重化せず、close/reopen/focus/別projectを検証。 |
| P08 | 保留変更の一括承認/却下とworktree適用 | #47/#308の事後Diff/Revertと分離。公開された未適用提案のsnapshot/所有契約が前提。 |
| P09 | Agent Voiceの録音開始/停止/取消 | #99はOS dictation研究でありVoice実装ではない。録音権限、選択chat所有、失敗と保存寿命を含め採用経路を確定。 |
| P10 | 広告されたmodel parameterの循環選択 | #43のexact variant IDを維持。広告された値/順序のみを使い、ACP busyとWindowsのキー優先を検証。 |
| P11 | 選択codeからcodebase検索付きchatを開く | #24の一般選択添付と区別。固定版の操作と公開検索意味を確定し、非公開flagや独自indexを発明しない。 |

#48のqueue、#97の送信設定、#258/#404のSkills/command候補、#156のopened chats、#213の最後tab閉鎖等を再利用する。これらの実装Issueがclosedでも、対応QAやmain反映は未達の場合がある。SubComposerの所属未確定からside chat能力を推測して起票しない。

### 残る操作の接続契約（2026-10-05確認）

この節はP02/P04/P05/P09の接続方式を選ぶ際に読む。対象は現在の公開仕様とCLI `2026.10.01-e373342`の静的確認であり、
新しいprovider prompt・SDK実行・GUI合格の証拠ではない。既存[#278の固定調査](issue-278-midturn-contract.md)を
全版・全経路の非対応判断へ拡大しない。未接続の行は親#212の受入に残す。

| 能力 | 確認した契約と限界 | 次に確定する接続条件 |
| --- | --- | --- |
| P02 履歴編集 | [ACP v1 schema](https://agentclientprotocol.com/protocol/v1/schema)のPromptRequestはsessionId/prompt/_metaで、編集対象messageや履歴分岐点を指定しない。[Cursor CLIの`/fork`・`/rewind`](https://cursor.com/docs/cli/reference/slash-commands)はinteractive経路の公開機能。固定CLIのACP command処理から、この組込み操作への接続は確認できなかった。 | 元会話を保持する分岐か後続履歴の置換か、対象ID、provider側の履歴変更と失敗時復旧を確定する。画面の本文だけを消したり、旧本文を通常promptとして末尾へ送ったりして編集済みと表示しない。 |
| P04 同一turn入力 | [SDK `run.steer`](https://cursor.com/docs/sdk/typescript#steering-a-run-in-flight)は実行中turnへの追記を公開し、`complete_delivered`と`revert_to_followup`を区別する。localのlive handleが対象で、Cloud/detached localのfallbackは追記成功ではない。固定CLIのACPは次のprompt受付で先行promptの取消を呼び、同時promptを非中断steeringには使えない。 | SDKを別方式として採用する場合、run/追加message/ackの所有、終端との競合、結果不明時の再送禁止、Stop/close、保存と再開を検証する。既存PRINT/ACP会話をSDKへ暗黙移行しない。 |
| P05 terminal tool限定取消 | [ACP prompt取消](https://agentclientprotocol.com/protocol/v1/prompt-turn)とSDKの`run.cancel()`はrunを止める契約。今回確認したCursor ACPのclient→agent拡張handlerに、実行中tool IDを指定する取消はなかった。clientが所有するterminalの操作とprovider内部toolの取消を同一視しない。 | 対象toolだけの取消受付、他tool/親runの継続、完了競合と結果帰属が保証される公開契約。現在のStop/OS process停止をこのキーへ別名接続しない。 |
| P09 Voice | 固定CLIのACP初期capabilityはaudio=false。これは音声をそのままpromptに渡す経路の広告であり、client側の文字起こし可能性を否定しない。[#99](https://github.com/shinma06/cursor-in-android-studio/issues/99)のOS dictation研究だけではAgent Voiceの開始/停止/取消は実装されない。 | 録音・文字起こしの採用経路、権限/費用、chatと録音の所有、取消後の遅着拒否、音声の保存/破棄を確定する。OS dictationの起動だけをVoice受入としない。 |

SDK候補には公開された[JVM等向けBridge](https://cursor.com/docs/sdk/bridge)もあるが、
確認した[固定proto](https://github.com/cursor/sdk-bridge/blob/d932194bcaf3d8ca4cae7373dfec32633ed0c9d0/proto/sdk/v1/sdk_agent_service.proto)
のサービスはSend/CancelRun等を持ち、steer RPCはない。TypeScriptのmethodがBridgeにも存在すると推定しない。
SDKはAPI keyと別のruntime/lifecycleを必要とし、[公開のlocal実行説明](https://cursor.com/docs/sdk/typescript#quick-start)では
既定tool実行に確認を挟まない。新方式では既存の権限・質問・Plan・Todo・MCP・保存を含む全契約を照合する必要がある。
この確認では依存追加、認証作成、SDK agent起動は行わず、採用決定や実装完了とはしていない。

固定CLIの静的観測はACP実装bundleのSHA-256
`9f99eb2b344aace7e5c67f7c07dc6d6b4bab3b85ba068e0cb9b1074ffb5eebf1`に限定する。
raw bundle・wire・個人command一覧は公開しない。公開method/広告または固定版が変わった場合に、該当行だけ再照合する。
JetBrains AI Assistant + Cursor ACP + IDE MCP/toolsでも、IDE側の検索・context・terminal能力と
provider会話の編集/実行中入力契約は別に検証する。ここで未測定の競合機能を非対応と断定せず、同等以上のGUI受入は残す。

## Keymap・競合・完了条件

panel内19 Actionの既定キーは`plugin.xml`でWindows/Linux、旧Mac OS X、Mac OS X 10.5+に定義する。model menuはWindowsの`$default`にCtrl+/、Linuxの`Default for XWin`にCtrl+/とCtrl+Alt+/を定義し、GNOME/KDEはXWinから継承する。固定SDK `AI-262.9437.185.2621.16467767`の同梱Keymap XMLと`DefaultKeymap.getDefaultKeymapName`で、この親子関係とOS別の既定選択を確認した。ユーザーが別OS系Keymapを選んだ場合は選択したKeymapに従い、OS判定でユーザー割当を強制変更しない。MacのCtrl継承を置換し、panel-local登録も同じActionのshortcut setを使う。ユーザーのKeymap変更/削除を尊重する。OS差の静的照合だけでは全対応・GUI合格とは呼ばない。

M+N/T/W、M+[/]、M+/、M+Shift+J等はIDE既存操作と競合する。18個のpanel内local Actionは一般editorの操作を奪わない。M+A+J（表示切替）、M+E（左右移動）、M+L/I（開く）、M+Y（WindowsはCtrl+Shift+Y、入力へ戻る）、M+Shift+L/I（New Agent）、MacのControl+Shift+S / Windows・LinuxのCtrl+Shift+/（All Agents）はproject内のglobal入口で、M+EのRecent FilesやM+L/I/Y等のIDE操作とは意図した割当競合が生じる。IDE Keymapで変更/解除でき、別projectのToolWindowを操作しない。IME、候補、子popupでは既存入力処理を優先し、selected tab/run、ACP busy、破棄済みpanel、late event、未保存確認を保持する。OS/US-JIS配列、custom Keymap変更、実際の優先はGUIで照合する。

表示切替はToolWindowのvisible状態を基準にし、会話・draft・runを生成/終了せずnative show/hideへ接続する。左右移動はnative anchorのLEFT/RIGHTを反転し、TOP/BOTTOMからはLEFTへ移して表示する。ToolWindowのtype・split・他のIDEパネルは変更しない。固定SDKの標準ToolWindow移動もanchorを変更する経路であり、Floating/Windowed時の実配置と、Cursorのunified sidebar/auxiliary/editor全体とのUX差はGUI照合に残す。単一Agent ToolWindowへの対応だけで、Cursorの全配置が同じと主張しない。

チャット入口は固定版のpane分岐を接続した。M+L/Iは選択中チャットを開き、パネルにfocusがあれば隠す。非表示から戻る際、既存の明示選択があればeventの選択コードを追加しない。入力へ戻る操作は選択中チャットを保持して選択コードを追加し、入力欄へfocusする。New Agentは表示順で最初の空Agentを再利用し、なければ新しいAgentを作る。focus済みの空Agentは最後の表示/focus要求から500ms以上経っていれば隠す。表示/focus要求ごとに単調時計でこの間隔を更新する。既存view/draft/contextは再作成せず、新規タブだけをAgent modeにし、共有設定を変更しない。

空判定は送信時保存のdraftではなく現在の入力欄を読み、本文・run・予約・選択command・未送信画像がある会話、本文来歴不明のlegacy会話を再利用対象から外す。画像/commandを空扱いしない条件はPluginの入力保護であり、Cursorとの完全一致は未確認。明示contextだけの空Agentは添付を保持して再利用する。選択コードはcontent生成/表示前のevent editorから取得し、遅いfocus callbackは同じview・世代・生存状態を再確認する。native Git-worktree除外（P06）、editor表示（P07）、過去入力から末尾への移動（P02）は能力未実装のため残り、`ISOLATED`をnative Git-worktreeと推測しない。根拠は上記固定版のentry handler、`isComposerEmpty`、`showAndFocus`、`wasRecentlyShown`と500ms定数の静的確認であり、実GUI合格ではない。

最近使用一覧は全OSでControl+Tab / Control+Shift+Tab（MacでもCmdではない）。固定版のruntime訪問リストは最大10件で、未登録の候補を更新日時の新しい順で10件まで補う。通常開始は2番目、逆方向開始は末尾を選び、一覧内では同じキーで次/前へ循環する。タブの表示順とは異なり、閉じた保存会話も候補に含む。Pluginはproject内のPRINT/ACP本文・旧print metadata・未保存の開いたタブを統合し、訪問順は表示の確定時だけ更新する。タブ並替え、stream更新、候補上の移動は順序を変えない。本文IDと旧print provider IDを別の型にし、同じprovider文字列のACPをprint legacyとしてまとめない。

修飾キーの解放はIDEで設定された単一strokeのControl/Meta/Altを使い、Shiftは他の修飾キーを離した場合だけ確定に使う。複数strokeはEnterで確定する。一覧の入口と候補移動は同じAction/Keymapを共有し、別popupや通常editorに配送しない。読込み前の解放は一覧を残し、遅れて候補が来ても勝手に開かない。Esc、外部click、別window、panel非表示/破棄は取消。候補はmetadataだけを保持し、閉じた会話の本文は確定時に再読込して削除/最新保存を照合する。選択中tabや世代が変わった後、IME変換や別popupが始まった後の読込み結果は捨てる。本文表示とprovider再開/Revertの可否は既存履歴経路を共有する。

根拠は上記固定版のMRU service/provider、Quick Accessの初期選択、Quick Pickの修飾keyup handlerと固定SDKのnative popup API。GUI操作は未実施。共通providerにはBackground候補、未読/保留要求の印もあり、in-IDEでの実データ条件と公開取得契約は未確定・未接続として残す。All Agentsは別の`chat:` provider（更新日時/検索順、最大200件）とsidebar表示制御を使うため、下記の独立した検索入口へ接続する。最近使用一覧やopened chatsの別名にはしない。

前後移動は固定版の`selectNextComposer` / `selectPrevComposer`と`Kku`の静的経路を確認した。選択タブが複数なら表示順で循環し、1つなら全会話を更新日時（なければ作成日時）の降順で循環する。MRUの10件制限は使わない。M+[/]と、sidebar非表示時のM+A+左右は同じ経路に接続する。M+A+左右は専用Actionなので、将来のsidebar navigation modeとKeymapを混同しない。

Pluginは1タブ時に保存履歴を背景読込みし、現行tab・世代・IME/子popup・削除を再確認する。保存済みで作業が残らないタブは置き換えて連続移動を保つ。run token・保存失敗/保留・queue・下書き・明示context・command・画像・手動名があるタブは保持し、対象を追加タブで開く。固定版のrunning switchも新タブを選ぶ実装で、単に取消/確認分岐の文字列があることから確認dialogを推測しない。Pluginはdraft等も保護対象に含めるため、その後は複数タブ移動になる差をGUIで照合する。空の未保存会話を履歴へ新規保存せず、名前/modeを推測しない。sidebar表示中は下記の候補highlight・解放確定へ分岐し、非表示時の履歴循環と区別する。

All Agentsは固定版の`workbench.action.openAgentsView` / `chat:` providerを確認した。Agent paneにfocusがありsidebarが見えている場合だけsidebarを隠す。それ以外はsidebarを表示して検索pickerを開く。Pluginは独立したチャット一覧と検索popupへ接続し、ToolWindowや会話本体は閉じない。一覧の表示状態をprojectごとに保持する。新規projectでは非表示から始めるPluginの初期状態と、ToolWindow内の分割配置は固定版の独立sidebarとGUI比較する。MacはControl+Shift+S、Windows/LinuxはCtrl+Shift+/。Windowsのmodel parameter優先（P10）は未接続として残す。

候補は開いた会話・保存本文・旧print metadataを型付きIDで統合し、開いたviewの手動名/冒頭文を優先する。pickerは空検索なら更新日時降順、入力時は名前/冒頭文の一致順位で最大200件。全候補を検索してから件数を制限し、古い会話を検索対象から落とさない。IDE native matcherによる曖昧検索と一致強調を使い、Cursor内部のscore値・同順位の順序との完全一致は主張しない。sidebarは大文字小文字を区別しない部分一致で検索し、区分内の更新日時順を保つ。popupの曖昧検索と混同しない。本文全文検索は従来の履歴検索であり、この入口へ混ぜない。読込み/検索を背景処理し、IME中や結果待ちは確定を拒否する。保持するのはmetadataで、閉じた候補の本文/削除を確定時に再読込みする。

sidebar表示中のM+A+左右は選択中chatから前後の表示候補（会話・More）をhighlightし、端で止まる。見出しと折り畳んだ区分の会話/Moreはnavigation候補から除く。Control/Metaの両方が離れた時に確定し、Altだけの解放では開かない。変更済みKeymapでControl/Metaを使わない場合は設定した修飾キーへ従い、複数strokeはEnterで確定する。Esc、root外へのfocus移動、window blur、非表示/破棄は取消。子入力のkeyupを受けるdispatcherはnavigation中だけ登録し、対象root内だけで処理して全終了経路で解除する。最近使用popupの解放方式とは分離する。候補選択は既存の会話を保持して対象を開くため、暗黙の送信・Stop・既存tab置換はしない。

固定版sidebarのpin・日付section・折り畳み・Moreを下記のlocal候補へ接続する。Archived/復元、Cloud候補、検索語からのFind with Agent、transient/editor-groupへの表示は未接続/未確定として全対象照合に残す。公開されたCloud取得/操作契約や既存能力を確認せず、local background実行と同じものにしない。ソース根拠は固定版のproviderの候補除外/重複統合/検索上限、sidebarのnavigation候補と確定handler、SDK native matcher/popup/splitter。実GUIは未実施。

local候補は固定したチャット、今日、昨日、過去7日間、過去30日間、それ以前の区分へ分ける。固定候補は日付区分と重複させず、区分内は更新日時降順。今日/昨日はlocal calendar、7/30日の境界は経過時間を使い、DSTでも「昨日」を24時間差と同一視しない。1分ごとにlocal日付の変更を確認して区分を再評価する。日付が同じ間は再描画でscroll位置を戻さない。空区分を表示せず、各区分は初期6件・Moreごとに6件追加する。Moreの確定では会話を開かず、追加された候補を表示する。表示切替では同じviewを保持し、検索語・追加表示件数・scroll位置を戻す。表示件数はIDE再起動等でcontentを作り直した時に6件へ戻る。折り畳み状態とpinはproject単位で保存し、区分見出しのクリック/Enterと「一覧に固定 / 固定を解除」で変更する。

固定版の`xQt`は通常の`setUnifiedSidebarHidden`でgridの可視状態だけを変更し、rendererを破棄しない。layout mode切替やpartのdisposeでは破棄する。Pluginもsidebarの有無をviewの生存と分け、非表示時は読込み/検索の世代を無効にしてworkerと日付timerを止める。同じviewで再表示する時は新しい履歴を読み、旧表示のcallbackを反映しない。非表示中のsidebar IME状態は他の入口/履歴循環を妨げない。内容の保持と実scroll復元は固定build GUIで照合する。

pinは固定版の有効候補75件上限に合わせる。削除済み等の現在存在しないIDは上限に数えず、残るpin metadataから本文や空会話を生成しない。本文UUIDと旧print provider IDを区別した保存値を使い、名前から同一会話を推測しない。履歴読込み失敗/不明候補がある間は新規pinを無効にし、既存pinの解除は可能にする。未保存draftをpinすることは本文保存やIDE再起動復元の保証ではない。pin/折り畳み/MoreでStop・送信・履歴削除は発生しない。根拠は固定版の`SGn` / `o4p` / `TQt=6` / `expandMore` / `I4p`とpin storageの75件判定。editor sticky同期とArchivedの扱いはP07/残作業に残し、固定buildのkeyboard/scroll/accessibilityと再起動での状態保持はGUI Caseで確認する。

1. 固定版Keyboard Shortcutsと入力/各popupを全件照合し、43登録行・17入力群・未確定共有/公式候補の所属、OS、focus、mode条件を確定する。
2. A1〜A3を既存経路へ接続し、確定した不足能力を具体Issueとnative関係へ分割する。無反応のダミーActionは追加しない。
3. [Case正本](../verification/changes/issue-212.json)で全対象の実装・条件・競合・固定build GUI証拠・main未反映を追跡する。現行panel専用17 Actionとglobal8 Actionや静的件数だけで#212を閉じない。[Milestone 8](https://github.com/shinma06/cursor-in-android-studio/milestone/8)はCursor 3.23.12のIDE内Agent全ショートカットを固定範囲とし、対象全件の確定・不足能力の実装・固定build GUI・main反映までを到達条件とする。未確定行や公開契約待ちを省略しない。

## 入力欄のEscapeと作業場所への復帰

固定版のin-IDE入力`onEscape`はmodel確認、個別callback、予約/目標編集、用途未確定の別入力分岐を先に処理し、その後で`composerViewsService.blur`へ進む。空の末尾入力では保留reviewのreject入力切替も存在する。`showAndFocus`はTerminalまたはeditorから入ったことを記憶し、`blur`はTerminal由来ならその時点のactive Terminalへ一度戻し、それ以外はeditorへfocusを渡す。この静的経路と[公式のEscape説明](https://cursor.com/docs/reference/keyboard-shortcuts)を根拠とする。model確認、過去入力や保留reviewの全分岐はP02/A3の残作業であり、通常blurの接続で全達成とはしない。

Pluginは入力局所の`CursorAgent.UnfocusInput`へ接続する。予約編集時はこのActionを無効にし、既存の`QueueReturnToInput`による編集取消を優先する。固定Rabbit SDKの`IdeKeyEventDispatcher.updateCurrentContext`は最寄りcomponentで一致するlocal Actionを集めてから可否を評価するため、予約取消とfocus復帰を同じ入力欄へ登録する。親rootだけの登録では無効な予約Actionに遮られ、IDE標準Escapeが先行し得る。登録はcontroller破棄時に解除する。通常時はEDTでselected view、入力focus/有効状態、IMEと候補/子popup、project/rootの生存を再確認し、古いfocus要求の世代を無効にしてから戻す。本文・caret・画像・queue・run/保留要求は変更しない。画像importも同じdraftで継続できる。sidebar、timeline、各requestカードや一般editorのEscapeは対象にしない。

OpenChat/FollowUp/NewAgent/All Agentsのglobal入口ではcontent生成・activation前にoriginを読み、Terminalの選択中content内focus、またはIDEのeditor active状態が確認できた場合だけ更新する。チャット内の操作はoriginを保ち、明示的な選択コード追加ではeditor由来へ更新する。復帰時はproject所属/生存/利用可と生成済みcontentを再確認し、開いているTerminalをnative ToolWindow APIでactivateする。閉じた/無効/未生成のTerminalは新規processを作らずeditorへ戻す。componentやterminal sessionを保存せず、1回復帰したoriginは消費する。既存のnative editor/Terminal内のcaret・scroll・表示はIDEへ委ね、IDEのauto-hide設定を変更しない。

[JetBrainsのToolWindow](https://www.jetbrains.com/help/idea/tool-windows.html)もEscapeによるeditor復帰を既に提供する。[AI Assistant](https://www.jetbrains.com/help/ai-assistant/ai-chat.html)＋[Cursor ACP](https://cursor.com/docs/integrations/jetbrains)と[MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)を備えた構成に対する独自機能とは扱わない。Pluginでの直接統合は、同じKeymapで会話の編集状態・IMEとnative focus復帰を整合させるために使う。providerへのprompt/ACP/MCP要求を発行しない。IDEの既存Escape割当を削除しないため、Plugin側のキーを変更/解除した場合はIDE側Actionとの競合を含めて確認する。Terminalのeditor表示、マウス経由の入口、各popup・IMEと実focusの一致は固定build GUIで照合する。

## 予約一覧のキー操作と残る送信能力

固定版のqueue list handler（`frb`）は一覧自体にfocusがある場合に上下移動、末尾から入力へ戻る操作、Escape、Right/Space編集、OS別削除を処理する。Pluginは既存の予約管理一覧へ`QueuePrevious` / `QueueNext` / `QueueEdit` / `QueueRemove` / `QueueReturnToInput` / `QueueSubmit`の6 Actionを接続する。panel専用19操作・global8入口とは別に、予約一覧のfocus contextで有効にし（入力欄での編集取消は後述）、IDE Keymapの変更/解除を使用する。修飾キーなしの上下/右/Space/Escapeは標準Keymapを継承し、削除だけMacのCmd+Backspaceへ置換する。

管理dialogを開くと予約を一時停止する既存契約を保持する。編集・削除は表示時snapshotではなく、選択IDから最新の予約を取得して既存操作へ渡す。前後移動は循環せず、先頭の上移動は先頭に留まり、末尾の下移動/Escapeは一時停止したまま一覧を閉じて現在の会話入力へ戻る。別会話/project、破棄後、編集dialogや子popupへのfocusでは一覧Actionを実行しない。削除時の画像解放、編集時のmode/model/command/context固定、世代/revisionによる古い送信ticketの拒否は既存queueへ委ねる。

入力欄上のinline queueと空入力境界からの入口は下記へ接続した。入力欄を使うqueue編集も後述の経路へ接続した。選択1件のSend Nowは下記へ接続し、同一run内へのsteeringは未接続として残す。既存の「予約送信を再開」は全予約の再開操作であり、QUEUE-SUBMITの代用にしない。そのボタンの既定Enterを外し、一覧でEnterを押しただけでは全件再開しない。再開ボタンの明示操作は残す。固定buildの実配送、IME中の編集、default button、focus復帰、Keymap変更と解除後のnative一覧/標準dialog Escの挙動はGUI Caseで照合する。

## モデル設定循環の接続条件

固定版`cycleHotkeyParameter`は各選択modelのparameter定義から`isCycleableByHotkey === true`かつ複数値を持つ最初の項目を選ぶ。CLIのalias末尾から作る既存`ModelFamilies`のaxis順を、この広告された順序やhotkey許可と同一視しない。現在のPluginのprint catalogにはこの属性がなく、ACP UIも確認済みの`mode` / `model`だけを扱うため、P10は未接続である。

[ACP config options](https://agentclientprotocol.com/protocol/v1/session-config-options)は`thought_level` / `model_config` categoryと広告順をUI/shortcut判断に利用でき、設定応答では全listの置換を求める。[Cursor SDKのmodel catalog](https://cursor.com/docs/sdk/typescript)にもparameter/variantの公開型がある。これらは次の接続候補であり、現在のCursor ACPが対象設定を広告することやCLI aliasへの対応、SDK catalogに同じhotkey許可が含まれることの証拠ではない。[JetBrains AI AssistantのACP対応](https://www.jetbrains.com/help/ai-assistant/acp.html)も比較対象のまま維持し、モデル設定循環の同等性は有限の実測と公開契約の照合後に判定する。

## 空入力からinline予約一覧への移動

入力欄の上に同じ`PromptQueueList`を表示し、管理dialogと専用6 Action・最新ID照合・編集/画像所有の経路を共有する。固定版の`J4t` / `sX_` / `aX_` / `onFurtherArrowUp`・`onFurtherArrowDown` / `frb`を根拠に、空入力から上方向では予約末尾へ、下方向では2件以上なら2番目・1件ならその1件へ入る。旧入力rendererの先頭/末尾判定と新入力rendererの視覚境界の差、空白/改行だけの入力では、Pluginがcaretの絶対先頭/末尾で移る条件を固定buildで照合する。

`EditorUp` / `EditorDown`のnative handlerをprompt editorのmarkerで限定して包む。通常editorはmarker確認だけで元handlerへ渡し、IDE Keymapの変更/解除を継承する。選択範囲/複数caret/本文/境界外、無効な入力、IME（確定eventのEDT turnを含む）、command/mention・子popup、未送信command/画像ではqueueへ移らない。画像に加えてcommandを保護する条件はPluginの既存draft保護であり、Cursorの全分岐との同一性を未確認のまま主張しない。非空draftから過去human messageへ戻るP02、review候補等の別分岐は残る。

inline一覧へのfocus・上下移動・入力への復帰だけでは予約のpause/resumeを変えない。管理dialogは既存どおりpauseする。inline編集は対象項目だけを配送保留にし、取消/更新で通常のqueue判定へ戻る。Stop/Revert/失敗等による明示pauseは解除しない。自動送信で項目が変わっても、操作対象は最新IDから取り直す。最後の予約削除でinline一覧が消える場合は同じ生存中会話の入力へ戻す。表示中は最大3行を基本にscrollし、予約がなくなれば一覧を隠す。chat controllerの破棄でAction登録を解除し、focus callbackを無効化する。native Document/Caretと実promptのIME listenerを使う`PromptQueueNavigationTest`はguard/委譲を検証するが、実Keymap配送・幅・focusのGUI合格ではない。


## 入力欄での予約編集と下書きの保持

固定版の`PGh`は選択queueの本文・context・model/modeを入力欄へ読み込み、`GHh` / `sq_`が編集対象とsnapshotを保持する。`oq_`の取消にはrestore snapshotがある場合だけ復元する分岐があり、通常draftを無条件に保持する証拠とはしない。Pluginはinline一覧のRight/Spaceから既存入力欄を使う編集へ入り、元の下書きの本文・全caret/選択範囲・mode/model・command・明示context/自動設定・画像を別snapshotとして保持する。これは既存下書きの消失防止であり、Cursor全状態の同一動作を主張しない。管理dialogの簡易本文編集も残す。

inline編集は対象IDを配送保留にし、その項目が先頭になった時点で自動配送を待つ。それ以前の予約は処理でき、後続は編集中の先頭を飛び越さない。編集中は別予約の変更や管理dialogへの移動を抑止する。実行中の送信キー/「予約を更新」はIDと位置を保って予約を更新し、idleの送信キー/「送信」は更新した1件を現在の会話へ送る。idle送信の準備が失敗すれば更新後の予約を残してpauseし、自動で再試行しない。Stopは同じrunを停止する操作のまま保持する。Escapeは同じ`QueueReturnToInput` Actionを入力欄にも接続して取消し、IME/候補popup/別focus/別会話では実行しない。更新・送信・取消では元draftを復元し、編集の配送保留を解除する。通常queueは再判定するが、管理dialog/Stop/Revert/失敗による明示pauseは解除しない。一覧からの選択1件のSend Nowは下記の独立した操作で、同一turnへのsteeringは残作業である。

画像はqueue・編集中入力・退避した下書きが別leaseを所有する。画像previewの読込み/解放は既存background workerを使い、取消や破棄で遅い結果を退避下書きへ上書きしない。登録済みの明示選択は固定snapshotとして保持し、新規/変更した選択だけ現行Documentと照合する。commandを変更した場合は現在のcatalog広告を確認する。開始後に対象予約が変更/削除された場合は保存を拒否し、編集本文はコピー・取消可能なまま残す。実行中turnのACP設定通知で編集中のmode/modelを差し替えない。

native入力のdraft切替をUndo境界にし、本文だけを別draftから復活させない。それ以前のUndo履歴は引き継がず、復元後の文字入力は通常どおりUndo/Redoできる。全caret/選択範囲を復元し、本文が同じ場合もdraft世代を変えて古い`@` / `/`候補表示を拒否する。`PromptQueueEditorTest`は実入力欄・IME・送信Action、複数caret・snapshot/画像lease・古い予約・Undo境界を検証する。Keymapの実配送、IME確定直後、狭い幅、実run終了/Stopとの競合と同等UXは`KEYMAP-QUEUE-EDIT`の固定build GUIで照合する。

2026-10-04に再確認した[Cursor in-IDE Agent概要](https://cursor.com/docs/agent/overview#queued-messages)は次turn queueと即時follow-upを区別する。[JetBrains AI Assistant + ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)と[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)は外部Agent・IDE toolの連携能力を持つ。Pluginの予約編集はこれらを独自能力と呼ばず、native editor/Keymap、tab所有・未保存draft・画像lease・送信ticketを直接結ぶ。公式概要だけでは他実装のqueue編集条件や同等以上の操作性を確定しない。


## 実行中の送信キーと予約編集の配送条件

固定版の`Hc`から`submitChatMaybeAbortCurrent`、QUEUINGの`onStartSubmitChatReturnShouldStop`を追跡した。`Xuo` / `vGd`の通常queue policyでgenerating中ならcontext/model/modeを固定して登録し、編集対象は`requeueEditedItem`で同じID・位置へ更新する。idleの編集submitはその予約を取り除いて通常送信へ進む。`aay` / `tryDispatchNextQueueItem`は編集中の先頭を待ち、`oq_`による取消後は再判定する。Pluginは停止中全体を自動再開する実装にせず、対象IDの配送保留と既存の明示pauseを分ける。失敗/Stop/復元と不確定な接続の制約は保持する。

通常入力の送信キーは、idleなら送信、実行中なら既存の`enqueuePrompt`へ渡す。「予約に追加」ボタンも同じIME/候補popup/画像import/入力可否の入口を使う。本文・commandだけ・画像だけの入力を扱い、登録が拒否されたら下書きを保持する。設定のM+Enterは通常送信キーの変更であり、P04の即時steeringを実装した意味にはしない。キー押下後はEnterの解放まで再実行を拒否し、編集から復元された非空draftを長押しで続けて送らない。解放・focus移動・editor再生成は入力欄に属するlistenerで処理し、IDE全体のキー監視は追加しない。

固定版には保留decision/goal/plugin flow、設定によるstop-and-send/steer、空入力や選択queueのsubmit等もある。これらを通常queue分岐の成功で確認済みにしない。`ComposerSubmissionTest`は実送信Action・実入力欄/IMEと画像状態で通常送信/予約/拒否時保持/Stop/長押しを確認し、`PromptQueueTest`は対象IDの保留、先行予約、明示pause保持、idleの1件配送・古いticket拒否と準備失敗を検証する。実Keymap配送、修飾キー先行解放やIMEのkey release、run終端との競合は固定buildの`KEYMAP-QUEUE-SUBMIT` / `KEYMAP-QUEUE-EDIT`へ残す。

## 入力欄から新しい会話への切替

固定版の入力command `bBs` → `onReset` → `NE` → `agentLayoutService.createNewComposer`を確認した。ShiftなしのM+Rで、新tab指定はfalse。呼出先はまず現在の空会話、次に表示中で最初の空会話を再利用する。なければ現在の表示を新しい会話へ置き換える。`Hxn`の空判定と`createComposerImpl`の`swapComposerView`/表示ID更新まで追い、単なる本文消去や無条件のタブ追加とは区別した。空会話の再利用時はmodeや明示contextを維持する。

Pluginの`ResetChat`は入力focus、入力可、IME/候補/子popupなし、画像import中でない時だけ有効。実行直前にroot/選択中composerを再取得する。現在の空会話を優先し、次に表示中の空会話を選ぶ。run/予約/command/画像/本文来歴不明を空扱いしない追加条件は既存のデータ保護による。会話本文がある場合に限り、現在の未送信本文・command・context/自動設定・画像を新しい入力へ複製する。新しい入力がある場合はPlan/Askを引き継ぎ、それ以外はAgentとする。まだ一度も送っていないdraftは新しい入力へ複製せず、元ownerで保持する。新しい会話は既存のPRINT既定接続/モデル設定から始まり、元のACP provider IDやモデルIDを流用しない。複製したcommandや画像は新しい接続でも対応確認が必要で、未確認なら送信を拒否して保持する。

`SessionTabs`は同じproject内の会話ownerと表示タブを区別する。置換は旧ownerのvisibleをfalseにするだけで、tab UUID、conversation/provider ID、run token、完全なview/controller、下書き、queue編集と画像leaseを保持する。元の実行の応答は元ownerへ届き、Stopや新しい会話への再送は起こさない。予約は従来の会話切替時と同じく一時停止する。All Agentsや最近使用一覧から同じownerへ戻り、provider再接続や保存履歴の再生で代替しない。本文のないdraftは一覧の説明に短い本文/command/画像表示を出すが、正式な会話名を生成しない（#66維持）。表示タブの前後移動/並替え/開いているチャット/全タブ閉鎖はvisible集合だけを使い、最後のvisibleを閉じた時は#213の空New Agentとパネル非表示を維持する。root破棄では非表示ownerも停止・破棄する。未送信draftのディスク保存や再起動復元は本変更に含まず、既存の保存範囲を変更しない。

[JetBrains AI Chat](https://www.jetbrains.com/help/ai-assistant/ai-chat.html)にもNew Chat、会話履歴、editor表示があり、[Cursor ACP連携](https://cursor.com/docs/integrations/jetbrains)と[IDE MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)を含む構成を比較対象とする。会話切替は同等UXへの接続であり独自機能とは呼ばない。直接IDE統合では、IDE Keymapと入力focusに限定して会話owner/実行tokenを維持する。配布版の静的経路とSessionTabs/Action/入力guardのテストが根拠で、liveのキー配送・画面・各接続の動作は[Case正本](../verification/changes/issue-212.json)のKEYMAP-INPUT-RESETで未確認として追う。native worktree・spec/project・subagent/editorの分岐は既存P06/P07/全対象照合に残す。

## 設定と履歴の入口条件

`Settings`はM+Shift+J / M+,をproject内global Actionにし、既存の`CursorAgent.Settings` IDとユーザーKeymapを維持する。各eventのprojectと利用可能なToolWindowを再確認し、既存rootがあればIME/候補/子popupを拒否する。updateも実行もcontentを生成せず、パネルの表示/配置・会話・draft・queue・runを変えずに、native設定dialogの本Plugin設定を開く。panel内のlocal登録も維持し、破棄時に解除する。IDEの設定/行結合等との競合はKeymapで再割当/解除する。projectなし/ToolWindow利用不可は対象外。welcome画面の設定入口や、一般editor/Terminal側IMEのキー配送は本接続の検証済み範囲に含めない。

根拠は固定版`Ryp`と`gfy` / `ffy`のglobal登録、[公式Settings shortcut](https://cursor.com/docs/reference/keyboard-shortcuts)、IDE Action System。`AgentWindowActionTest`はpanel contextなし、eventごとのproject、content未生成/非表示維持、失効後の拒否、旧IDとOS別2キーを確認する。実配送とユーザー再割当は`KEYMAP-SETTINGS`でGUI pending。これはJetBrainsの設定/Keymapと同種の入口であり、直接統合では既存のPlugin設定と会話ownerをそのまま使う。

履歴の`GK`登録はglobalだが、`run`は選択中composerのhandleを必要とし、`showComposerHistory`は統合sidebar表示中に何もしない。それ以外で会話の`shouldShowHistory`を立て、editor側の履歴表示flagを下げる。未生成会話の作成やパネル表示を直接要求するhandlerではない。固定版のrendererは表示要求からheaderに紐づくflyoutを表示し、sidebar表示時に現在の会話のflagを下げる。キーは`show`だけで、headerのクリックは`show/hide`の切替である。Pluginは`History`をproject内global Actionへ移し、content未生成/選択会話なしでは無効とする。既存rootだけから同じtab UUIDへ要求を記録し、ToolWindowを表示/生成しない。非表示中や別会話選択中の要求は元ownerへ保持し、再表示/再選択時に表示条件を確認する。表示中のsidebarでは要求を拒否し、sidebar表示時にその会話の要求を解除する。反復キーは読込みを重ねず、表示中の要求も閉じない。headerだけは切替で取り消せる。

`PastChatsCoordinator`は表示要求・読込み・dialogを同じownerと世代に紐づける。非表示/会話切替で古い読込みと表示を無効化し、close/project破棄では要求を破棄する。表示直前のIME/子popup等のguardが失効した場合は遅れて開かず、明示的な再要求を待つ。予約一時停止は実際のdialog表示直前だけに保ち、隠れた要求や取消済み読込みだけではpauseしない。保存形式・provider resume・既存の本文検索/削除/書出しは変えない。native dialogと固定版のheader flyout、header icon非表示時の到達、editor表示の履歴はGUI/P07で照合し、入口の接続だけで同等UXのpassとはしない。根拠は同じ固定配布版の`GK`、`showComposerHistory`、rendererとheader handlerの静的確認、および実coordinator/Action/rootのテストで、GUI到達の証拠ではない。


## 保留要求への返信と取消の優先

`AcceptPending`はEnter送信設定ならM+Enter、修飾キー送信設定ならM+A+Enterで、現在の会話の既存要求カードへ返信する。defaultの2組以外にKeymapで割り当てたキーは送信設定による切替の対象にせず、defaultの組合せには設定条件を維持する。設定値はActionのupdate/実行ごとに取得する。入力欄でもnative sendと同じcomponentへlocal登録し、親より近いhandlerの優先で無効なacceptが送信を隠さないようにする。通常送信/予約編集、shell用Enter/Shift+Enter、通知の承認などの別分岐をこの接続の完了に含めない。

対象はfocus中の要求カード、または現在の会話に一つだけあるlive要求。返信済み/失効カードにfocusがある場合は別の要求へ転送しない。複数要求がありfocusでも選べなければ無効にする。許可は一意の`allow_once`、拒否は一意の`reject_once`だけを同じボタンへ接続する。対象情報がない許可、同kindの複数option、`*_always`しかない要求を推測で選ばない。Planは承認/却下、質問は全項目を明示選択した回答/スキップを既存のtyped replyへ送る。画像・draft・queueや共有permission設定は変えない。

`Stop` ActionのM+Shift+BackspaceとMac Control+Cは、対象を特定できる保留要求があれば拒否/スキップを優先する。保留要求があるのに対象/拒否optionが曖昧な場合は全run停止へfall throughしない。保留がなければ従来のStopへ進む。停止ボタン自体は従来どおりrun停止を行う。返信対象とinput focusは実行時に確認し、IME/候補/子popup、別領域/別projectのfocus、非表示viewからは実行しない。

要求cardには受信したturnのproject・token/generation・Stop・terminal条件を渡す。キーとマウスの両経路が返信直前にこの寿命と`isPending`を照合し、`AgentInputRequest`とACP側の既存の一度だけ返信/Stop競合処理を維持する。押下中のキーcodeだけをroot単位に保持し、解放まで次の要求への返信や拒否後のrun Stopを抑止する。keyupを受け取るdispatcherは押下後から解放までだけ登録し、root破棄でも解除する。OSがアプリ外のkeyupを配送しない場合を含め、focus遷移・長押しの実動作はGUI Caseで確認する。

固定版の`f6t`は現在のpending decision groupの全`accept`、`Kft`/`j5e`の`$dp`はgroupの全`reject`を通常run取消より優先する。groupは単なる全pending集合ではなく、terminal優先・類似tool・質問の状態を使う。shell用`U8o`はreview modelとallowlist候補・設定gateを参照し、一部経路では確認dialogと共有自動実行設定変更を伴う。現在の[ACP permission](https://agentclientprotocol.com/protocol/v1/tool-calls#requesting-permission)受信経路は個別option IDを扱い、この内部group・allowlist候補を提供しない。そのため、今回の個別返信をgroup全件、shell allowlistや通知の対応と呼ばず、A3の残作業として保持する。`allow_always`とshell allowlistを同一化しない。

空入力のtool確認は`ApproveTool`（Enter）と`SkipTool`（Escape）を追加した。固定版の`eYb/J4t/U8o`は、空draft・画像/動画なし・過去入力編集中ではないblocking reviewからrun/skipを選ぶ。対象にはterminalだけでなくMCP、編集、削除、Web検索/取得もある。PluginはACPの実際のpermission要求と、一意な`allow_once` / `reject_once`を使う。kindやtool名から許可を生成せず、対象情報不足では許可できない。ACPにdecision groupの識別情報がないため、空入力から複数要求の最後を推定せず、focusしたカードまたは一意の要求だけを対象とする。

対象は選択中会話の入力欄かtimeline内のfocus。非空draft、command/画像、import中、予約編集、入力不可、IME/候補/子popup、失効token・破棄後は実行しない。Enterではfocus中のnative buttonを置き換えず、Space/ボタンの既存操作を保つ。送信とfocus解除は同じ入力componentで候補を集め、tool確認の現在のKeymapと一致し、かつ返信可能な時だけ譲る。キー解除/再割当で元操作へ戻り、複数strokeの先頭もKeymapに従う。承認/拒否後の長押しは既存の一時dispatcherで解放まで消費し、後続要求・送信・改行・focus解除へ流さない。返信そのものは既存card/buttonと一度だけのrequest処理を使い、全runを止めない。

`ToolReviewShortcutsTest`は実Composer/native Editor・timeline・requestで空入力/添付/予約編集/IME/入力再生成、曖昧・古いカード・別focus、Keymap変更/解除と通常送信/解除との優先を確認する。`RequestShortcutsTest`の実listener/tokenによる失効試験とキー解放試験も維持する。native画面の優先・OS配送・accessibilityは`KEYMAP-TOOL-REVIEW`で未GUIとして追う。CursorのShift+Enterはallowlist候補/設定変更も扱う別操作で、ACPの`allow_always`と同一視せず未接続に残す。group/notificationもこの2 Actionで実装済みとはしない。

`RequestShortcutsTest`は実カード/ボタン・timeline・turn listenerと実SessionTabs tokenで、一意/曖昧/focus失効、返信の一度性、質問選択、Stop/close/token更新/project破棄/terminal後の拒否、設定/OSとキー解放を検証する。`AgentPanelActionTest`と`ComposerSubmissionTest`はAction/XMLと入力componentへの登録/破棄時解除を確認する。これは合成入力のCLI検証であり、固定版Cursorや[JetBrains ACPの承認UI](https://www.jetbrains.com/help/ai-assistant/acp.html)と実GUIを比較した合格ではない。直接IDE統合の追加価値は、同じ会話ownerとIDE Keymapから既存の提示済み要求へ返信する接続にある。

### Mode循環とShift+Tabの優先

固定版の`Kdp`（`composer.openModeMenu`）はM+. / M+A+. / Shift+Tabを同じhandlerへ登録し、menuを開かず次のmodeを選ぶ。`getAllModes`の候補と現在値を用い、project/background・複数model・Plan/Debug提案済みflag等で分岐する。入力の`GMf`は一般keydown（`n9e`）をTab局所command（`BRe`）より先に配送する。一般keydownの`softDispatch`が`m6t`へ一致するとそこで返るため、既定Shift+Tabと過去human messageへの局所移動を同列の競合とはしない。ただし実際のfocus/context・別Keymap・IMEの到達は固定build GUIで確認する。

Pluginは既存`CursorAgent.ModeMenu` IDとclass・M+. / M+A+.を保ち、Shift+Tabも同じActionへ追加する。既存選択肢のAgent → Plan → Ask → Agentを現在値から循環し、ラベル/アクセシビリティ名を同時に更新する。modeボタンのクリックは従来の選択menuを開く。現在の入力・選択範囲・model・別会話・共有既定設定・実行中turnの設定を変更しない。printでは次turn用の選択を更新できるが、ACP実行中はwidgetが一時的に有効でも変更を拒否する。これは現行3 modeへの接続であり、CursorのDebug等や自動提案を同等実装した意味ではない。非公開の提案判定・未対応modeを推測した置換は行わず、A1の残分岐に保持する。

入力のnative editorを生成/再生成するたび、同じmode ActionをTab字下げ防止用Actionと同じcomponentへ登録する。IDE KeymapがShift+Tabをmodeへ割り当て、現在のpanelで実行可能な場合はmode Actionを優先する。割当の変更/解除やmode実行不可では従来の逆focus移動を保ち、通常Tab・選択範囲・貼り付け済みtab文字を変更しない。複数strokeの先頭に割り当てた場合もIDE側の解決へ渡す。SDKのlocal登録はcomponentに保持され、IDE全体のkey listenerやeditorを保持する独自registryは追加しない。mode操作が無効な要求card内の移動、IME/候補popup、別project、Keymap再設定の実配送はGUI Caseで照合する。P02の過去message編集/移動はこの接続で完了とはしない。

`SelectorInitializationTest`は循環順・最新選択値・無効状態・別会話・アクセシビリティ表示を確認する。`PromptFocusTraversalTest`は実Actionと現在のshortcut setでmode/逆focusの排他、Keymap変更・複数stroke・失効focusと本文/選択保持を確認する。`ComposerSubmissionTest`は実Editor再生成後の登録、printとACP busyの区別、入力不可と共有設定/draft保持を検証する。比較相手の[JetBrains ACP連携](https://www.jetbrains.com/help/ai-assistant/acp.html)に対するmode/agent選択能力の独自性は主張しない。直接IDE統合では同じKeymapと会話所有、入力保護へ接続し、画面上の同等性は未確認として残す。

## 初回メッセージの送信

`SubmitInitialChat`はM+Shift+Enterを、選択中の読み込み済み・本文なし会話の初回送信へ接続する。固定Cursor版の`composer.triggerCreateWorktreeButton`は空でないdraftと空会話を照合して通常submitを呼ぶため、Action名だけからGit worktreeの新規作成とは解釈しない。Pluginでも既存送信経路と現在の接続・mode/model・command・context・画像を使う。run準備中/実行中、予約編集中、本文がある/本文来歴不明の会話、空白のみ、画像import、IME/候補/子popupでは送らない。入力欄にも同じnative Actionを登録してKeymap変更を尊重し、通常送信ActionとEnter押下状態を共有して修飾キーを変えながらの長押しによる重複送信を防ぐ。

[Cursorのworktree説明](https://cursor.com/docs/configuration/worktrees)はAgents Windowのnative UIとIDEのSkillsを区別している。初回送信の接続はP06のbranch/name/base/setup/cancel/cleanup契約を確定しない。既存のworkspace設定・root所有・復元条件を保持し、履歴後/変更review/実行中のM+Shift+Enter分岐（P08）も残す。`ComposerSubmissionTest`は実native Editorと同じ送信Actionを使う初回送信・拒否条件・長押し共有・登録解除を確認する。実キー配送、全contextの送信と固定Cursorの条件差は`KEYMAP-INITIAL-SUBMIT`のGUI pendingで追う。

## 予約1件のSend Now

固定版のqueue listは送信設定がEnterならEnter / M+Enter、M+EnterならM+Alt+Enter / M+Enterを受け付け、steering可否・既定・反転指定からSend NowまたはSteerを選ぶ。Send Nowは選択IDを解決し、通常submitの中断経路へ渡す。Pluginは`QueueSubmit`をinline/管理一覧の同じnative Actionへ登録し、既存の`PromptQueue.dispatchSelected`と`startPrompt`へ接続する。待機中は選択した1件を送り、実行中はそのrunへStopを要求して停止/正常終了の確定後に送る。現在のprint/ACPにはsteeringを接続していないため、このActionはSend Nowとして扱う。全件再開ボタン、通常入力の予約追加、予約編集中の更新とは分ける。

`PromptQueueSubmission`は元run・世代・選択時の予約objectを保持する。通常のrun後片付けと予約解放の完了後のEDTで、同じ会話/表示/世代・idle・最新snapshot・非編集を再確認する。`AgentTurnListenerFactory`は終端を`RunPhase`で渡し、停止と不確定/失敗をBooleanで潰さない。終了不確定/失敗、Stop要求の例外、別会話/破棄/編集/変更済み予約では予約を保持して自動送信しない。明示Stop、会話切替、Revert、管理一覧を開く操作は待機中のSend Nowを取り消す。送信済み行の画像leaseだけをturnへ移し、他の行は操作前の自動配送状態を引き継ぐ。操作前のpause、新しいpause/予約変更や元runの未送信入力の復元があればpauseを維持し、停止中の入力を自動で再送しない。通常draftは既存queued送信経路で保持し、長押しは既存のキー解放guardで1回に限定する。

`PromptQueueSubmissionTest`は実`AgentRun`・turn listener・`SessionTabs`を接続し、確定終端とfinally後の開始、画像/command/context/mode/modelの同一snapshot、終了未確認/失敗、後続の取消/世代/行更新/編集と一度性を確認する。`PromptQueueListTest`はSDK登録Action、設定別/OS別キー、最新の行、focus/owner失効と長押し/解放/登録解除を確認する。実processの終了、PRINT/ACP再送、IME/Keymap配送は`KEYMAP-QUEUE-SEND-NOW`の固定build GUIで追う。

[Cursor SDK](https://cursor.com/docs/sdk/typescript#steering-a-run-in-flight)は現在、localの`run.steer`と受付結果を文書化している。既存PRINT/ACPの入力契約とは別で、未実装の同一run入力に対する次の評価候補である。停止後送信をsteering成功に置換せず、Cloud/detached localのfallback、run所有・ack・保存を別に確認する。

## Branch menuの受信先

固定main bundleで追跡できた`cursor.openBranchMenu`の受信componentは、Cloud移行UI内のbranch selectorに配置されていた。command側の選択済み/読み込み済み/空会話という条件に加え、受信先はBACKGROUND_COMPOSER capability・移行UIの可視性と進行/エラー状態・選択ID・実在する要素を確認する。登録条件と受信先が同時に成立する実場面は未GUIであり、通常の空Agentでbranch popupが開くと断定しない。P06の行を対象外/合格へ落とさず、固定版で到達条件を照合する。既存ISOLATED selectorや初回送信Actionを、このbranch選択の代用にしない。
