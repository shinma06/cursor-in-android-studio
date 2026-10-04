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
| `cursor.toggleAgentWindowIDEUnification` | Cmd+E | Ctrl+E | Ctrl+E | IDEの空でないworkspace、isGlass=false。Agent用sidebarの位置/表示を切り替える。最大化とは異なる。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
| `cursor.openAgentChangesEditor` | Cmd+Shift+R | Ctrl+Shift+R | Ctrl+Shift+R | composerにfocus。選択中会話のReview Changes editorを開く。PluginはM+Shift+Rを既存の変更一覧へ接続し、editor表示面の一致は未確認。 | in-IDE静的確認 / 一部接続済み / A1 |
| `cursor.openBranchMenu` | Cmd+' | Ctrl+' | Ctrl+' | composerにfocusし、選択中会話を読み込み済みかつ空。開始時のbranch選択でありcontext添付とは別。 | in-IDE静的確認 / 能力未実装 / P06 |
| `workbench.action.toggleAgentsFromKeyboard` | Cmd+Option+J | Ctrl+Alt+J | Ctrl+Alt+J | 補助windowにfocusがなくisGlass=false。Agent表示とkeyboard focusを切り替える。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
| `aichat.newfollowupaction` | Cmd+Y | Ctrl+Shift+Y | Ctrl+Y | global登録。選択chatを解決し、worktree chatを除外、選択codeを追加して入力へfocusする。WindowsだけShift付き。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
| `composer.selectPreviousComposer` | Cmd+Option+Left | Ctrl+Alt+Left | Ctrl+Alt+Left | composer/agentsPaneにfocus、一般editor textにfocusなし。weight410。sidebar状態によって修飾キーを離すまでnavigation modeになる。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| `composer.selectNextComposer` | Cmd+Option+Right | Ctrl+Alt+Right | Ctrl+Alt+Right | 上記の次conversation。tab表示順・履歴順との一致は未GUI。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| `composer.selectPreviousSubComposerTab` | Cmd+Option+Left | Ctrl+Alt+Left | Ctrl+Alt+Left | composerにfocus、weight200。切替eventの登録は確認したが受信側とin-IDE所属は未確定。 | 未確定 / 所属未確定 / U-SUB |
| `composer.selectNextSubComposerTab` | Cmd+Option+Right | Ctrl+Alt+Right | Ctrl+Alt+Right | 上記の次SubComposer。通常conversationのweight410との実際の優先も未確定。 | 未確定 / 所属未確定 / U-SUB |
| `aichat.newchataction` | Cmd+L | Ctrl+L | Ctrl+L | isGlass=false。M+Iと同じ入口へ転送する。選択code・現在focus・既定配置で新規/既存chatの動作が変わる。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
| `composer.startComposerPrompt` | Cmd+I | Ctrl+I | Ctrl+I | global登録でAgent入口へ転送。ToolWindow表示/focusを既存APIで接続する。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
| `composer.newAgentChat` | Cmd+Shift+L / Cmd+Shift+I | Ctrl+Shift+L / Ctrl+Shift+I | Ctrl+Shift+L / Ctrl+Shift+I | global登録。in-IDEでは空のAgentを再利用または作成し、focus済みの空Agentなら隠す場合がある。単純な現在chatへの選択追加とは異なる。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A1 |
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
| `composer.previousChatTab` | Cmd+[ | Ctrl+[ | Ctrl+[ | composerにfocus、一般editor textにfocusなし。端で循環するかは未GUI。 | in-IDE静的確認 / 一部接続済み / A2 |
| `composer.nextChatTab` | Cmd+] | Ctrl+] | Ctrl+] | 上記の次tab。 | in-IDE静的確認 / 一部接続済み / A2 |
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
| `workbench.action.quickOpenPreviousRecentlyUsedAgent` | Control+Tab | Ctrl+Tab | Ctrl+Tab | agentsPane/composerにfocus、chatEditorGroup.enabledではなくBackground Composer Windowでもない。最近使用順picker。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| `workbench.action.quickOpenLeastRecentlyUsedAgent` | Control+Shift+Tab | Ctrl+Shift+Tab | Ctrl+Shift+Tab | 上記と同じ条件で初期選択を逆順にする。tab表示順とは別。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| `composer.sendToAgent` | Cmd+L | Ctrl+L | Ctrl+L | editor prompt barが存在しfocus中。Inline Edit→Agent境界であり、通常panel内操作の対象外。入口として採るかは別判断。 | 対象外 / 通常panel対象外 / X-INLINE |
| `aiSettings.action.open` | Cmd+Shift+J / Cmd+, | Ctrl+Shift+J / Ctrl+, | Ctrl+Shift+J / Ctrl+, | M+Shift+Jはglobal、M+,はisGlass=false。固定配布版では両方Cursor Settingsを開き、一般IDE設定はM+Shift+,。公式概要との差を保持する。 | in-IDE静的確認 / 一部接続済み / A1 |
| `workbench.action.quickOpenNavigateNextInAgentsPicker` | Control+Tab | Ctrl+Tab | Ctrl+Tab | quick open内のAgents MRU picker、weight250。picker内だけで次へ進む。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |
| `workbench.action.quickOpenNavigatePreviousInAgentsPicker` | Control+Shift+Tab | Ctrl+Shift+Tab | Ctrl+Shift+Tab | 上記の前へ移動。通常editor切替には流さない。 | in-IDE静的確認 / 既存機能へ接続/差分確認が必要 / A2 |

現在の初期接続は`CursorAgent.NewChat`、`CloseChat`、`PreviousChat`、`NextChat`、`Stop`、`ModeMenu`、`ModelMenu`、`AddContext`、`History`、`Changes`、`Settings`の11 Action。設定・会話owner・未保存確認等は既存経路を再利用する。表の一部接続だけで全分岐を満たしたとは扱わない。

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
- Windowsのmodel menuはCtrl+/だけ。Ctrl+Alt+/とCtrl+Shift+/はparameter変更であり、Mac/Linuxのmodel menu副キーをWindowsへ継承してはいけない。初期Pluginにはこの差の修正が残る。
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

現行11 Actionの既定キーは`plugin.xml`でWindows/Linux、旧Mac OS X、Mac OS X 10.5+に定義する。MacのCtrl継承を置換し、panel-local登録も同じActionのshortcut setを使う。ユーザーのKeymap変更/削除を尊重する。OS差が確認された既定値は訂正が必要で、初期定義を全対応とは呼ばない。

M+N/T/W、M+[/]、M+/、M+Shift+J等はIDE既存操作と競合する。現行のpanel内local Actionと、これから追加するglobal入口を区別し、一般editorや他projectの操作を奪わない。IME、候補、子popupでは既存入力処理を優先し、selected tab/run、ACP busy、破棄済みpanel、late event、未保存確認を保持する。OS/US-JIS配列、custom Keymap変更、実際の優先はGUIで照合する。

1. 固定版Keyboard Shortcutsと入力/各popupを全件照合し、43登録行・17入力群・未確定共有/公式候補の所属、OS、focus、mode条件を確定する。
2. A1〜A3を既存経路へ接続し、確定した不足能力を具体Issueとnative関係へ分割する。無反応のダミーActionは追加しない。
3. [Case正本](../verification/changes/issue-212.json)で全対象の実装・条件・競合・固定build GUI証拠・main未反映を追跡する。現行11 Actionや静的件数だけで#212を閉じない。[Milestone 8](https://github.com/shinma06/cursor-in-android-studio/milestone/8)はCursor 3.23.12のIDE内Agent全ショートカットを固定範囲とし、対象全件の確定・不足能力の実装・固定build GUI・main反映までを到達条件とする。未確定行や公開契約待ちを省略しない。
