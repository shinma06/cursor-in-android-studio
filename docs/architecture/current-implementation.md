# 現行実装の責務と境界

2026-09-12 / #228照合。ソース基準は develop `4d1514d8fa6c020d41ad9c0205b9ea24268bef57`。元の#142説明へ#147のACP接続が追加された後のコードを対象とする。設計方針は別文書、GUI/mainの結果は各QAが正本。更新時の根拠と履歴の扱いは[知識の正本](knowledge.md)。

[Project Mission](../project-mission.md) / [ACP First](cursor-integration.md) が設計方針。本書は現在のprint経路と#147のACP接続を説明する。ACPの固定build GUI合格を意味しない。機能別のACP契約・移行設計は [#115](https://github.com/shinma06/cursor-in-android-studio/issues/115)、全体順序は [#141](https://github.com/shinma06/cursor-in-android-studio/issues/141)で扱う。

## 所有関係

| 所有者 | 現在の責務・寿命 |
|---|---|
| `AgentToolWindowRootPanel` | `SessionTabs` とtab ID別のview/controllerを所有。CardLayoutで切替、同じviewの本文・入力・caret・scrollを保持。表示置換では旧ownerを保持し、明示closeで該当controllerをdispose |
| `SessionTabs` | 同期化されたメモリ内状態。会話ownerと表示タブを分け、plugin tab UUID、nullable chat ID、draft/mode/model/title、run tokenを保持。プロセスやSwing、ディスク保存を持たない |
| `AgentUiController` | **タブごと**のactive run/token/generation。送信準備、context/checkpoint、listenerとUI、停止・復元を調整。選択中タブへイベントを転送しない |
| `AgentProcessService` | **projectごと**の複数`AgentRun`集合、sessionの復元元記録、復元排他。printプロセス構築とstream解析・配送、tab ID別ACP接続。新しい別タブ送信で既存runをkillしない |
| `AgentRun` | 準備を含む**1回の要求**。停止要求・listener・終了の一度だけの配送と遅いprocess attachを管理。会話全体でもOSプロセスそのものでもない |
| `AgentTurnListenerFactory` | print callbackとtyped ACP eventからタブUIへのbridge。EDT上でtoken/generation/dispose/停止を再照合。tool card、session metadata、usage、終端表示を調整 |

対応ソース: [root](../../src/main/kotlin/com/cursoragent/ui/AgentToolWindowRootPanel.kt)、[状態](../../src/main/kotlin/com/cursoragent/session/SessionTabs.kt)、[controller](../../src/main/kotlin/com/cursoragent/ui/AgentUiController.kt)、[service](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt)、[run](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt)、[listener](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt)。

最後の会話タブを閉じると、既存の`SessionTabs`が空の`New Agent`を用意し、対象controllerの破棄後にfactoryが標準`ToolWindow.hide`でAgentパネルを非表示にする（#213）。再表示時はその新規会話を使い、閉じた会話の本文・下書き・provider IDを復帰させない。閉じるボタン、タブ上のDelete、全チャット閉鎖は同じ処理を通る。確認dialog後の現行タブ集合で最後かを判定し、確認中に追加されたタブがあればパネルを維持する。未保存/実行中の確認・取消、対象runだけの停止と遅着拒否は既存経路を保つ。ヘッダーの「パネルを隠す」は会話を閉じず、そのまま保持する別操作。実画面の非表示・再表示と入力/並行実行の受入は[Case #213](../verification/changes/issue-213.json)で確認する。

## パネルのActionとKeymap

入力欄のM+R（`ResetChat`）は現在/表示中の空会話を再利用し、なければ新しい会話へ表示を置き換える。会話本文があれば未送信draftの本文・command・context/自動設定・画像を複製し、入力ありのPlan/Askを引き継ぐ。初回送信前のdraftは元ownerだけで保持する。新しい会話の接続/モデルは既存の新規既定を使い、ACP provider IDをコピーしない。`SessionTabsSnapshot.tabs`は非表示を含む保持owner、`visibleTabs`はタブ列/前後移動/並替え/開いているチャット/全閉鎖の対象。置換では元のtoken・draft・画像・queue・controllerを破棄せず、All Agents/最近使用一覧から同じownerを再表示する。予約は会話切替の既存方針で一時停止し、元の実行だけは継続する。明示closeは対象ownerを終了し、root破棄は非表示を含む全ownerを終了する。最後のvisible閉鎖は#213を維持する。IME/popup/入力不可/import/別focusを拒否し、未送信draftの再起動保存は提供しない。固定版との差とGUI条件は[ショートカット対応表](../research/issue-212-agent-shortcuts.md#入力欄から新しい会話への切替)へ分離する。

入力欄のEscape（`UnfocusInput`）は予約編集中なら既存の取消を優先し、通常時は古いfocus要求を無効化してnative editorへ戻る。IDEが最寄りcomponentのlocal Actionを先に収集するため、予約取消とfocus復帰を同じ入力欄へ登録し、controller破棄で解除する。global会話入口のactivation前にTerminal由来を確認した場合だけ、その時点の生成済みTerminalへ1回復帰する。閉じた/無効/別project/未生成Terminalでは新規processを作らずeditorへ戻す。元のcomponentを保持せず、IME/popup/入力不可/別focus/root破棄を拒否し、本文・画像import・queue・run・保留要求を変えない。`ChatFocusReturnTest`でoriginの更新/消費とtargetの失効を、`ComposerSubmissionTest`で再生成後の実Editorを使ってfocus/IME/予約編集の条件を確認する。native focus、Keymap競合と未接続のreview等は[Escapeの対応範囲](../research/issue-212-agent-shortcuts.md#入力欄のescapeと作業場所への復帰)とGUI Caseへ残す。

#212の初期接続は新規/閉じる/前後タブ/停止/mode/model/context/履歴/変更一覧/設定の11操作。`AgentPanelAction`はprojectを保持せず、rootの`UiDataProvider`から現在の操作対象を取得し、EDT上で実行直前にも可否を照合する。rootのlocal shortcut登録はIDEで設定された同じActionのshortcut setを使い、disposeで解除する。停止と閉鎖は既存controller/確認経路へ接続する。変更一覧も既存の予約一時停止とsnapshot/owner確認を共有し、M+Shift+Rで開く。modeのM+A+.、設定のM+,も同じActionの副キーとして扱う。IME、入力候補、子popupとACP設定のbusy制約を保つ。

`AcceptPending`は送信設定に応じたM+Enter / M+A+Enterを、focus中または単一のlive要求カードへ接続する。許可/拒否は一意のonce option、Planは承認/却下、質問は明示選択済み回答/スキップ。Stop Actionは保留要求への拒否を優先し、対象不明の複数要求や拒否option欠落を全run停止へ読み替えない。停止ボタンは既存run Stopを維持する。返信済みカードから他要求へ振替しない。入力欄へsendと同じ階層で登録し、実focus/IME/popup・現在viewを照合する。turn listenerから渡したproject/token/generation/Stop/terminalの生存条件をキーとマウス双方で再確認し、既存のexactly-once replyを共有する。`RequestShortcutKeys`は押下中のkey codeだけを解放まで保持し、連続返信や拒否からStopへの長押しfall throughを防ぐ。root破棄でもdispatcherを解除する。`RequestShortcutsTest`は実listener/SessionTabs/カードの境界を検証する。公開受信データにないCursorの判断group・shell allowlist・通知や特殊submitを完成扱いにせず、[返信と取消の優先](../research/issue-212-agent-shortcuts.md#保留要求への返信と取消の優先)と固定build GUIで追う。

`ModeMenu`はID/classを保って既存3 modeの循環へ接続し、M+. / M+A+. / Shift+TabでAgent → Plan → Ask → Agentを選ぶ。ボタンのクリックは選択menuを維持する。入力のnative editorにも同じActionを登録し、現在のKeymapと可否によりShift+Tabの逆focus移動と排他にする。入力再生成でも接続し、通常Tab・選択範囲・貼付tab文字と共有既定/別会話を保持する。ACP busyはwidget状態とは別にも拒否する。Cursorのmode提案・拡張mode、Keymap変更後のP02 fallbackと実GUIは未達。[Mode循環とShift+Tabの優先](../research/issue-212-agent-shortcuts.md#mode循環とshifttabの優先)を参照する。

`SubmitInitialChat`はM+Shift+Enterで読み込み済み・本文なし・run準備/実行なしの選択中会話を既存送信へ渡す。本文来歴不明、空白のみ、予約編集、入力不可、IME/候補/子popup、画像importは拒否する。native入力欄にも同じActionを登録し、通常送信とEnter押下状態を共有して修飾変更中の長押しでも一度だけ送る。接続・mode/model・command・context・画像、workspace/rootの選択は既存送信に従う。branch/native worktreeの作成契約と履歴後/変更review/実行中の特殊送信は未実装として残す。[初回メッセージの送信](../research/issue-212-agent-shortcuts.md#初回メッセージの送信)と`ComposerSubmissionTest`、固定build GUI Caseを根拠とする。

`Settings`は既存IDとM+Shift+J / M+,を保ってproject内global Actionへ接続する。既存rootのIME/候補/子popupとproject/window生存を各eventで確認し、contentを生成/表示せずnativeのPlugin設定を開く。panelへのlocal登録/破棄時解除も維持する。会話・draft・run・queueは変更しない。projectなし/ToolWindow利用不可では無効。OS別Keymapとcontent未生成・event projectの切替は`AgentWindowActionTest`で検証し、IDE既存割当との競合・実IME/キー配送はGUI pending。履歴も同じIDとM+A+'を保ってglobal入口へ移し、既存の選択中会話だけへ表示要求を記録する。ToolWindow/contentを生成・表示せず、非表示中は同じtab UUIDの要求を保持する。実表示中のsidebarがある場合は開かず、sidebar表示時にその会話の要求を解除する。キーの反復は開閉切替や重複読込みにせず、ヘッダークリックだけを切替とする。読込み後にも選択・表示・IME/popup・世代を照合し、閉鎖/破棄済みの要求を別会話へ転送しない。予約一時停止は既存の履歴dialogを実際に表示する直前に行う。`PastChatsCoordinatorTest`と実rootを使う`AgentWindowActionTest`で検証し、native dialogとCursor flyoutの操作差はGUIで照合する。[設定と履歴の入口条件](../research/issue-212-agent-shortcuts.md#設定と履歴の入口条件)を参照する。

`ApproveTool`（Enter）/`SkipTool`（Escape）は空入力の個別tool確認に接続する。選択中会話の入力欄またはtimelineのfocusから、実permission要求の一意なallow_once/reject_onceだけを既存cardへ返信する。非空draft・command/画像/import・予約編集・IME/候補/子popup・失効は拒否し、Enterでnative buttonの操作を上書きしない。送信/入力focus解除は現在のKeymapで競合する時だけ確認Actionへ譲り、キー解除・変更後は元の操作へ戻る。長押しは解放まで消費し、解決後の次要求や送信へ流さない。group・通知・shell allowlistは未接続で、全run Stopや永続許可へ読み替えない。`ToolReviewShortcutsTest`と`RequestShortcutsTest`、[Case #212](../verification/changes/issue-212.json)を参照する。実OS配送・GUI比較は未確認。

project内のglobal入口として`AgentWindowAction`のM+A+J（表示切替）とM+E（左右移動）を登録する。各eventでprojectとToolWindowを取り直し、破棄・利用不可・IME/候補/子popupを再確認する。標準show/hideとanchor変更を使い、会話・draft・run・ToolWindowのtype/splitを変更しない。左右移動はLEFT/RIGHTを反転し、上下配置からはLEFTへ移す。別projectを保持せず、未生成contentはupdate時に生成しない。M+EはIDE Recent Filesと競合するため、他のglobal入口と同じくKeymapで再割当/解除できる。Cursorの複数領域配置やFloating/Windowedでの表示差は全対象GUIに残す。

`OpenChat`（M+L/I）、`FollowUp`（Mac/LinuxはM+Y、WindowsはCtrl+Shift+Y）、`NewAgent`（M+Shift+L/I）もproject内global Action。開く操作はpanel focus中なら隠し、それ以外では現在のviewを保持して入力へ戻す。非表示の既存明示選択は追加選択で上書きしない。FollowUpは常に現在の入力へ戻る。NewAgentは本文/run/予約/入力/command/画像のないAgentを再利用し、なければAgent modeの新規タブを作る。focus済みの空Agentでは最後の表示/focus要求から500msを過ぎると隠す。本文不明のlegacy会話は空と推測しない。選択コードは表示前にevent editorから取り、添付先を確定してからnative activateへ進む。遅いfocusはtab viewと世代、project/panel/windowの生存、IME/popupを再確認する。既存draft/contextと共有mode設定を保持する。根拠・安全側の空判定差・未実装のworktree/editor/過去入力分岐は対応表に残す。

`RecentChat` / `LeastRecentChat`は全OSのControl+Tab / Control+Shift+Tabで、最大10件の最近使用一覧をnative popupへ開く。表示確定時の訪問順をruntime内に持ち、閉じた保存会話と更新日時順の候補も含む。移動/stream更新で訪問順を変えず、旧print IDと本文ID、PRINTとACPを区別する。初期選択は2番目/末尾、一覧内の同じキーで循環、修飾キー解放またはEnterで開き、Esc等で取り消す。読込み前に解放しても遅い自動選択は行わない。native popupのkey handlerとdisposable付きlocal shortcutを使い、app全体のkeyboard hookを追加しない。候補はmetadataだけで、閉じた本文は確定時に再読込し、世代/選択/削除とIME/別popupを照合して既存履歴の表示・再開制約を保持する。保存形式やprovider操作は追加しない。All Agentsは下記の専用入口へ分離し、共通providerのCloud/未読表示は未達として対応表に残す。

前後タブ（M+[/]）とsidebar非表示時の前後Agent（M+A+左右）は、複数タブなら表示順、1タブなら保存会話を更新日時順で循環する。入力局所のResetChat/UnfocusInputを含めpanel専用は計19 Action、Settings/Historyを含むglobal入口は8 Action。1タブでも無効にせず、履歴の背景読込み後にowner/世代/IME/popup/削除を確認する。保存済みで作業のないタブを置き換え、実行token・保存失敗/保留・queue・draft/context/command/画像・手動名は追加タブにより保持する。Closeと同じ未保存判定を共有し、実行tokenも確認する。履歴を10件へ切り詰めず、MRUとは別の順序を使う。保護対象がある場合のタブ数変化、固定buildのfocus/停止境界は未GUIとして対応表/Caseで追跡する。

`AllChats`はMacのControl+Shift+S、Windows/LinuxのCtrl+Shift+/を使う6つ目のglobal入口。project内のチャット一覧と名前/冒頭文の検索popupを表示し、pane focus中で一覧が見える場合は一覧だけを隠す。表示状態をprojectごとに保持する。非表示では同じviewと検索語/追加表示件数/scroll位置を保持し、worker/timerを止めて読込み/検索の旧世代を無効にする。再表示で再読込みし、viewはcontentの寿命に合わせて破棄する。popupは通常/アーカイブの各候補を検索した上で各最大200件、空検索は更新日時順、検索時はSDK native matcherの一致順位と強調を使う。sidebarは部分一致検索を使い、区分内の更新日時順を保つ。開いたview・保存本文・旧履歴をmetadataへ統合し、検索を背景処理、確定時に本文を再読込みする。修飾左右は一覧の候補highlightへ分岐して端で止まり、Control/Meta解放またはEnterで確定する。navigation中だけ登録するdispatcherはroot外focus/blur/非表示/破棄で解除し、IME/別popupを奪わない。local一覧はpin/今日/昨日/7日/30日/それ以前の区分と6件単位のMoreを持つ。pinと折り畳みはprojectに保存し、75件の有効pin上限、local calendarと経過日数の境界を保つ。navigationは表示中の会話/Moreだけを使い、見出しと折り畳んだ候補を除く。Moreは表示を増やすだけで会話を開かない。保存不明時の新規pinは無効とし、本文や空会話を生成しない。Cloud・Find with Agent・transient/editor表示と、popupのnative matcherのscore差は対応表の残作業。GUI合格や全対応とはしない。

All Agentsのアーカイブ/復元はprojectの型付きID別metadataとして保持する。通常一覧・最近使用10件・単一tabの保存履歴循環からアーカイブ済みを除き、All Agentsの折り畳み区分から検索・表示・復元できる。復元は更新日時と区分だけを変え、会話を開いたり送信を再開したりしない。アーカイブ時は対象controllerの既存Stopを呼び、物理終了前のowner/tokenや未送信draft・画像・queueを破棄しない。sidebarで選択中の会話をアーカイブすると元ownerを隠して隣のvisible tab（なければ新しい空tab）へ移る。popupでは現在viewを維持する。sidebarのアーカイブはpin解除、popupではpin metadataを残すが有効pin数から除外する。検索待ち・IME・非表示/破棄・削除・古い状態からの選択/変更を拒否する。JSON v1/旧XMLやprovider session/Revertの制約、本文100件上限は変更しない。未保存draftの再起動保存は提供せず、保存済み本文の再起動後の区分保持は固定build GUI待ち。`ChatArchiveTest`は実list renderer・root/controllerとAgentRunで停止要求/遅延終了/owner保持を検証する。比較上の差は[対応表](../research/issue-212-agent-shortcuts.md#all-agentsのアーカイブと復元)を参照。

予約管理一覧には#212の専用6 Actionで上下移動・Right/Space編集・OS別削除・入力へ戻る操作と選択1件の送信を接続する。`PromptQueueList`だけがfocus時のAction contextを供給し、実行時にも会話owner/focus/子popupと最新の予約IDを照合する。末尾での下移動/Escapeは一時停止したまま閉じ、現在の会話なら入力へfocusを戻す。Dialogの既定Enterで全件再開しないよう、再開ボタンは明示操作にする。編集・画像解放・送信ticketの取消は既存queueを共有する。入力欄上のinline一覧にも同じ部品を使い、空入力の絶対先頭から上方向で末尾、絶対末尾から下方向で2番目（1件ならその項目）へ移る。EditorUp/Downのhandlerはprompt marker・focus・単一caret・選択なしを確認し、本文/境界外/IME/候補popup/未送信command/画像等では元処理へ委譲する。通常editorはmarker照合後そのまま元処理を使う。inlineのfocus移動ではpause/resumeを変えず、管理表示ではpauseする。inline編集は対象IDを保留し、それより前の予約は配送でき、後続は編集中の先頭を飛び越さない。最後の予約がなくなれば一覧を隠し、必要なら同じ会話の入力へ戻す。controller破棄でinlineの登録を解除する。inline一覧のRight/Spaceは入力欄での予約編集へ入る。元の下書きの本文・全caret/選択・mode/model・command・context/自動設定・画像を退避し、保存/取消で復元する。通常入力の設定済み送信キーは実行中に次turn予約へ追加する。編集中は実行中なら同じID・位置の更新、idleなら更新した1件の送信へ進む。Escapeは同じKeymap Actionで取り消す。編集保留の解除後に通常queueを再判定するが、管理/Stop/Revert/失敗による明示pauseは解除しない。idle送信の準備失敗は更新後の予約を保持してpauseし、自動再試行しない。Enter解放までの連続実行を抑え、復元された元draftの誤送信を防ぐ。画像を別leaseで所有する。既存の選択snapshotは保持し、新規/変更選択を検証する。古い予約への保存を拒否し、編集内容をコピー・取消可能なまま残す。ACP設定通知で編集中の選択を差し替えない。draft切替は本文と添付の不整合を防ぐUndo境界とし、世代で遅い候補を拒否する（`PromptQueueEditorTest`、詳細は対応表）。選択1件のSend Nowは下記へ接続する。過去human messageやreview候補への分岐と同一runのsteeringは未接続であり、次turn予約や全件再開で代替しない（[Case #212](../verification/changes/issue-212.json)、`PromptQueueListTest`）。

`QueueSubmit`は一覧の送信キーを選択1件のSend Nowへ接続する。待機中は既存queued送信、実行中は元runの停止/正常終了が確定してから送る。`PromptQueueSubmission`はrun/世代/行objectを保持し、終端後の予約解放を待ったEDTで最新owner・表示・idle・非編集を再確認する。終端callbackは`RunPhase`で停止と失敗/不確定を区別し、不確定/失敗や停止要求の例外で次を送らない。Stop/会話切替/Revert/管理一覧表示は待機送信を取り消す。選択snapshotと画像lease、元draftを保持し、残る予約は操作前の自動配送状態を引き継ぐ。操作前にpauseしていた場合、新しいpause/予約変更や元runの未送信入力の復元があった場合は一時停止を維持する。native Keymapの送信設定・修飾キーと長押しguardを共有し、破棄時に解除する。[予約1件のSend Now](../research/issue-212-agent-shortcuts.md#予約1件のsend-now)と実listener/AgentRunのテスト、GUI Caseを根拠とし、steeringは未実装のまま残す。

model menuの既定値はWindowsではCtrl+/、LinuxではCtrl+/とCtrl+Alt+/、MacではCmd+/とCmd+Option+/。Linuxの副キーは同梱`Default for XWin`とそれを継承するGNOME/KDEへ定義し、OS判定によるユーザー割当の変更は行わない。OS別キー、IDEとの競合、Cursor側の版/有効条件と未対応は[ショートカット対応表](../research/issue-212-agent-shortcuts.md)を参照。現行のTab/Shift+Tab focus移動と送信キー設定を含め、Cursorの全操作との一致は未達。[Case #212](../verification/changes/issue-212.json)のGUI受入と全対象照合を省略して完了扱いにしない。
## 会話タブの折り返し

会話タブはIDEの `Settings → Editor → General → Editor Tabs → Show tabs in` に連動する（#211）。`Multiple rows` は `UISettings.scrollTabLayoutInEditor == false`、`One row` は `true` で、Rabbit 1の既定値は `true`。設定はIDE全体に適用され、各panelは生成時と `UISettingsListener.TOPIC` のEDT通知で反映する。接続はprojectに登録し、panel破棄時にも明示的に切断する。製品独自の設定・永続化は増やさない。読み取りAPIと設定画面の値対応はRabbit 1 `AI-262.9437.185.2621.16467767` のSDKで確認した。[設定の公式説明](https://www.jetbrains.com/help/idea/using-code-editor.html) / [UISettings](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/ide/ui/UISettings.kt) / [既定値](https://github.com/JetBrains/intellij-community/blob/master/platform/editor-ui-api/src/com/intellij/ide/ui/UISettingsState.kt)。

`SessionTabStrip`の既存描画・IDによる操作を保ち、右固定Actionを除いた幅で改行する。幅変更・追加・閉鎖で高さを更新し、タブ領域は親の利用可能高さの半分までに抑えて会話・入力欄の領域を残す。収まらない行は既存の`JScrollPane`で縦スクロールする。バーなしの全利用可能幅で必要高さを先に求め、親の高さ上限を超える場合だけバー幅を除いて再配置する。直前のバー表示状態に依存せず、幅変更・タブ削除・高さ拡大で不要になったバーを解除する。選択・並べ替え・幅変更時には選択タブを表示範囲へ戻す。各行で選択・閉じる・tooltip・DnDを同じtab IDへ送り、折り返し中のdragは上下端で縦スクロールする。右固定Actionは先頭行の右側に置く。折り返し切替と複数行表示のresizeで進行中のdragを取消し、1行へ戻すと既存の自然幅・横スクロールを再使用する。IDEの `Squeeze tabs`、editorの配置/非表示/上限・pinは移植対象外で、1行指定はどちらの幅方針でも従来の横スクロールになる。会話・下書き・run・PRINT/ACPの接続には触れない。

コンポーネントテストは複数行の配置・幅変更・切替・2行目の操作・行間DnD・背景とAction領域に加え、固定320×500の親で20タブを折り返し、高さ変更後の全行への到達・会話/入力欄の領域・縦ホイール・閉鎖/キー操作を確認する。IDEの設定画面との実連動、Dark/Light・多数タブ・複数projectでの固定build受入は[Case #211](../verification/changes/issue-211.json)へ残す。競合構成でもAI Chatをeditor tabとして開けるため、複数会話やIDEタブ設定自体を独自機能とは扱わない。本件は会話をAgentパネル内に保持したままIDEの表示選択を共有する。[JetBrains公式](https://www.jetbrains.com/help/ai-assistant/customize-ai-chat.html)。

## 入力文書と候補popupの境界

この節は2026-09-24 / [PR #405](https://github.com/shinma06/cursor-in-android-studio/pull/405)で更新。先行Document生成とcommand対応の実装基準は`aa83b8f155cde6affe88a90c20467180246213e9`、Undo境界は同PRのレビュー修正。冒頭の全体照合基準とは別の追加実装であり、PRの固定commitとCaseを根拠とする。

Platform 261の`EditorTextField(Project, FileType)`はDocumentを遅延生成する。Document生成前の`addDocumentListener`はfield内の一覧に保持されるが、後の`getDocument()`による生成では実Documentへの接続が行われない。`GrowingPromptField`は従来のPSI-backed Documentを初期化時に取得してから、`CommandInputPanel`と`MentionPopupController`がlistenerを登録する。入力通知を各consumerで複製して回避しない。Quail1/4の実SDKでlazy生成時の接続0件・先行生成時1件を確認した登録境界の再現と、実GUIの候補表示成功は別の証拠である（[#404](https://github.com/shinma06/cursor-in-android-studio/issues/404)）。

`GrowingPromptField`の背景は子Editorだけでなく親`EditorTextField`へ設定し、`updateUI()`でも現在のcomposer色を再適用する（#209）。Rabbit 1 SDKの`EditorImpl.setBackgroundColor`はscheme既定色と同じ指定を解除するため、初期化時の`JBColor`指定だけではテーマ更新後の色を保証できない。親から設定することでSDK側のEditor再設定も同じ背景を参照する。`PromptThemeTest`は実SDKの同じEditorでlight/dark更新を繰り返し、背景・下書き・caret・選択範囲を検証する。画面上のhover/focus、描画残り、IME、送信後の再生成は[Case #209](../verification/changes/issue-209.json)の固定build GUIで別に確認する。

候補選択で本文の`@`や`/`を削除するときは、write actionだけでなくIDEのcommand境界も必要。両選択処理は共有の`consumePromptTrigger`で`WriteCommandAction`を使い、既存のdocument stamp・project dispose・候補の有効性確認を保つ。[公式Document規約](https://plugins.jetbrains.com/docs/intellij/documents.html#what-are-the-rules-of-working-with-documents)と、Quail1の固定試験buildで発生した[候補選択例外](https://github.com/shinma06/cursor-in-android-studio/issues/404#issuecomment-5762029921)が根拠。通常Documentへ変更した試行で観測された例外であり、従来のPSI-backed Documentでの再現を確認したとはしない。

添付とコマンド選択は本文とは別の状態で、既存の×ボタンで解除する。triggerを消費するときは`UndoManager.nonundoableActionPerformed(DocumentReference, false)`でその入力Documentに非Undo境界を置く。選択前の本文編集履歴には戻れず、境界でのUndoはIDE標準の「取り消せない変更」案内で拒否される。選択後の本文編集はUndo/Redoでき、他Documentの履歴には作用しない。triggerだけ復活して選択が残る不整合を防ぎ、添付そのもののUndo機能は追加しない。

popupが消えた後の画面だけで「一度も開かなかった」と判定しない。入力直後・候補選択・取消・別アプリへのfocus移動を分け、IME変換中/確定EnterとShift+Enter、入力再生成後も同じ固定buildで確認する。修正後の両IDE GUIと、#205のTab修正を含む統合候補でのmain受入は[Case #404](../verification/changes/issue-404.json)と統合後のQAで未完了として追跡する。旧Phase5 #392は終了済みで再開せず、正式v0.1.0や封印RCを変更しない。

## printの送信から終了まで

1. controllerが入力を保存し、`beginTurn`でtokenとprompt/mode/model/chat IDを固定。同じタブでの重複送信を拒否する。Composerのmode/modelは生成時にアプリ設定からコピーしたタブ別選択値であり、切替のたびに全体設定へ書き戻さない。
2. `captureWorkspace` と `prepareTurn` がroot/worktree/resume、executable・permission・sandbox等を `TurnWorkspace` / `TurnSettings` / `PreparedAgentTurn` に固定。準備予約とrunを作る。後から変更された設定で進行中ターンを組み替えない。
3. EDTでactive editor/selection、VFSのfile/folder、optional Terminal APIを取得。pooled threadで実root解決、checkpointとGit contextを準備し、prompt文字列を組み立てる。準備Futureをrunが保持する設計ではなく、各段階の`run.isActive`確認で停止後の送信を防ぐ。モデル一覧取得のFutureはcontrollerが別管理する。
4. `sendPrompt` がprocess予約を取得して `GeneralCommandLine` / `OSProcessHandler` を作る。現行引数は `-p --output-format stream-json --stream-partial-output --trust` と固定済みworkspace/settings/prompt。構築中の停止後にattachされたprocessも破棄する。
5. stdout断片内の行を `StreamJsonParser` に渡し、stderrはエラー用に蓄積。init/resultで得た最初のsession IDをrun内のresume IDと対応付け、listenerがtokenを確認してtabへ結び付ける。plugin tab ID・chat ID・run tokenを同一視しない。`Result` はusage/session/fallback/errorの入力であり、OS終了の代替ではない。
6. process terminationからrunを一度だけ完了し、process予約を閉じる。listenerはEDTで内容・終端表示を反映して`finishTurn`する。stdoutだけで状態が完結するのではなく、UI操作、token、run、OS終了、復元予約も状態を決める。

[TurnWorkspace](../../src/main/kotlin/com/cursoragent/service/TurnWorkspace.kt) / [PromptContextBuilder](../../src/main/kotlin/com/cursoragent/ui/PromptContextBuilder.kt) / [MentionResolver](../../src/main/kotlin/com/cursoragent/ui/composer/mention/MentionResolver.kt) を参照。context注入は現行prompt文字列経路。`@Docs`/`@Web`はヒントであり、独自検索やMCP server実装ではない。

Rabbitではeditorの保存後もVFSからdiskへの書き込みが残り得るため、PRINT/ACP共通の送信準備とcheckpoint復元の外部I/O前に、pooled thread（write action外）で`ManagingFS.flushPendingUpdates()`を待つ。失敗時は既存の送信準備/復元エラー経路で中止し、待機中のStopも再確認する。未保存Documentの自動保存や後続の編集を固定する機能ではない。[公式の非同期保存契約](https://blog.jetbrains.com/platform/2026/06/async-vfs-content-writes-what-plugin-authors-need-to-know/)と[AgentUiController](../../src/main/kotlin/com/cursoragent/ui/AgentUiController.kt)が根拠。VFS内だけで読み書きするfile Revertには待機を追加しない。実IDE回帰は[Case #466](../verification/changes/issue-466.json)で追跡する。

## 停止・タブclose・project終了

Stopはそのタブのrunへ停止要求を出す。通常Stopでは先にtokenを捨てず、`onStopped`だけ停止済みrunからの終端配送を許して表示後に完了する。以降の通常イベントは抑止する。tab closeは状態を無効化して該当controllerのlistenerをdetachし、runを停止。content終了は全tokenを無効化し各controllerをdispose、project service終了は全runを停止する。

`killActiveProcess()` は現在の名前と異なり全runの停止・detachを行うservice cleanup用メソッド。通常のStopからこれを呼んで他タブまで停止してはいけない。UI上の停止完了と物理process終了は別であり、復元予約は実終了まで保持する。

## 復元の安全性

`WorkspaceOperationGate` はproject共通。複数の準備・processは同時に許可するが、準備開始から各processの実終了まではRevert/checkpoint復元を拒否する。復元中は新規準備を拒否する。停止中の旧processは**復元を妨げるが、他の準備を一律拒否しない**。

`TurnWorkspace` が実rootを固定し、`SessionWorkspaceHistory` がservice寿命内のsession由来を記録する。DEFAULTの既知rootだけ復元可。ISOLATED、再起動後など由来不明のresume、旧XMLのroot/mode欠落は推測せず拒否する。Diffの閲覧は継続可能。

`RestorePolicy` / `FileRevertOperation` はroot一致・symlink/traversal・現在内容・未保存editor変更を確認する。Revertはそのeditのafterと一致する場合だけ戻す。checkpointはGit snapshotとroot/mode/untracked名一覧を記録するが、元から未追跡のファイル本文や作成後にstageされた新規ファイルの完全復元は提供しない。

[接続契約](../development/restore-target-integration.md) / [RestoreTarget](../../src/main/kotlin/com/cursoragent/service/RestoreTarget.kt) / [GitSnapshotStore](../../src/main/kotlin/com/cursoragent/service/GitSnapshotStore.kt) / [CheckpointHistoryState](../../src/main/kotlin/com/cursoragent/settings/CheckpointHistoryState.kt)。ACP移行でも対象の来歴、後続編集保護、取消と実終了の区別を残す。printの即時書込み実測をACPの全操作へ一般化しない。

## イベント・補助CLI・保存

- `StreamEvent` / `ToolCallPayloadParser` は現行printの構造化JSON用。未知/不正入力は防御的に扱う。completed fixtureとstarted payloadの推定を分ける。`PrintAssistantText` は実測版2026.09.10-fd3934aとpartial指定に基づきdelta/flushを分離し全文置換する（#254）。旧/未知版と契約外metadataは従来`AssistantChunkDeduper`のheuristicへ戻す。版取得・EOF・保存境界と保証範囲は[event-contracts](event-contracts.md)を参照。ACPへ流用しない。
- mode/modelとモデルオプションは実CLI IDへ対応。`ModelListParser` / `McpListParser` は補助CLIの表示文字列解析。MCP dialogは既知の`id: status`を整形し、解析不能ならraw表示する。これらはACPモデル設定やMCP tool公開の実装ではない。
- Settingsの既定モデル一覧は未取得から始まり、「モデル一覧を取得」の明示操作で既存`ModelCatalogLoader`を呼ぶ。設定の表示・reset・診断更新・コピーではCLIを起動しない。保存済みIDは未取得/失敗/空一覧でも保持し、Applyでのみ保存する。破棄後の遅着拒否と再試行は既存loaderに任せる。Composerの自動取得とACP設定は別経路で維持する（#493、`DefaultModelSettingsPanelTest`）。CLIパス欄は表示桁数を固定してラベル下に置き、説明はplain textで折り返す。フォームはSDKの`ScrollablePanel`でSettingsのviewport幅に追従する。`Configurable.NoMargin`と同等の余白指定で、IDEの非Scrollableな余白wrapperによる幅追従の喪失を防ぎ、縦方向は標準Settingsでスクロールする。診断textareaの列数指定でscrollbar幅を推奨幅へ繰り返し加算せず、狭幅でも診断の複数行と設定値の可読性を保つ（実`ConfigurableCardPanel`経路を使う`AgentSettingsLayoutTest`）。固定buildでの狭幅操作は[Case #493](../verification/changes/issue-493.json)と元Case28/76/269で確認する。
- usageはprintの入力値を `TokenUsage` / context usage状態へ反映する現行表示。ACP usage/contextとCursor Todoは未実装。ACPの標準Plan表示・Cursor質問/Plan要求・permission UIは以下の範囲で実装。
- `cursor-agent-chat-history.xml` はproject単位のchat ID/preview/更新時刻のみ。開いたタブの本文はメモリ内、PRINT/ACP本文は[保存契約](conversation-persistence.md)のproject単位JSONへ保存（#44、実IDE再起動QAは別）。`cursor-agent-checkpoints.xml` はsnapshot metadata、`cursor-agent-settings.xml` はアプリ設定。保存ID/enum、`com.cursoragent.plugin`、内部tool-window/notification IDは変更しない。

型・テスト名の維持理由と再評価箇所は [監査記録](../development/project-context-audit.md)。syntheticテスト成功は実装の回帰確認であり、実Cursorのwire採取や新しいbuildのGUI合格には数えない。



## 実行状態・経過時間・ツール詳細・通知（#98）

各会話の上部に準備中→実行中/考え中/ツール実行中→完了/停止/失敗と、送信準備を含むターン全体の経過時間を表示する。`RunStatusPanel`はEDT上の会話所有で単調時計を使い、終端で時間を固定し、Controller破棄でtimerを止める。printのprocess構築後とACPのprompt dispatchを`onStarted`で反映し、Thought受信だけを考え中とする。最後の活動イベントを表示するもので、思考時間・並列tool数・推測した承認待ち・再接続状態を作らない。実際の質問/permissionカードは独立して表示する。旧形式・未知subtype・typed解析失敗のgeneric tool情報は、提供された名前をliteralな会話内カードへ残し「状態未取得」と表示する。開始の根拠にはせず、開始通知も出さない。

Stop直後は終了が未確認なら「停止を確認中」を保ち、物理終了/ACP終端の既存判定で停止または失敗へ進む。`AgentRun`とFactoryのEDT token/generation/dispose再照合を維持し、古いcallbackは新turnの時間や通知を更新しない。経過時間はproviderの計測値ではなく、履歴復元では再計測しない。

printのコマンド出力と長い要約、ACPのtool内容は既定で折り畳み、概要と提供された状態を残す。ボタンはkeyboardで操作でき、同じcall IDの更新でも開閉状態を保持する。printの同じcall IDの完了は同じ行を置換し、完了後のstartedで行や状態を戻さない。ID再利用は次の実turnで分離する。長い本文・空出力・エラー・差分の順序を保持し、raw HTMLを解釈しない。既存の子Task表示とその詳細開閉を維持し、編集カードのDiff/Revertや要求回答controlsは隠さない。

ツール開始通知は背景の会話の各turnに一度だけ送る。初回活動が前景ならそのturnは通知しない。project内の開始通知は最新1件へ置換し、旧turnの終了で別turnの通知を消さない。完了・失敗・停止の通知は各turnで独立し、設定でOFFにできる。通知の「会話を開く」は元のtabを選択する既存導線を使い、閉じたtabを再作成しない。終了とtab/project破棄で所有する開始通知を解放する。通知本文へツールのcommandやAgentのraw errorを載せない。

同一turnのエラーは会話内の詳細と日本語の失敗通知で知らせ、Factoryの重複modal割込みを廃止する。通知OFFでも会話内詳細は残す。他の設定/認証/IDEエラーdialogは変更しない。停止は途中本文が残り、適用済み編集を自動復元しないことを表示する。usage、実exit0後のRequest ID、queue停止/予約配送、非テキスト/子Task終端、Revert前のpauseは既存契約を保持する。

2026-09-13の比較根拠: [Cursor IDE内Agent panel](https://cursor.com/docs/agent/overview)はコード・検索・terminal・編集を統合する。[JetBrains AI Assistant + ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)は外部Agentとcustom/IntelliJ MCP server公開を提供し、[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)には実行構成・ファイル・編集等のtoolsがある。IDE内Agent、tool進捗やIDE操作そのものを独自能力とは呼ばない。本変更は既存能力との同等UXを目指す日本語表示と、Pluginのtab/token・queue・Request ID・復元保護への直接接続を担当する。[要検証] 最新競合の細かな折畳み/通知/経過時間の外観差と本候補の同等以上UXは実機未観測であり、公式資料だけで達成済みと判定しない。受入手順は[issue-98.json](../verification/changes/issue-98.json)、GUI/mainはpending。


## ACP接続（#147）

`AcpSession`をproject serviceがtab ID別に所有する。候補取得の接続準備または初回送信時に `agent acp` をroot作業ディレクトリで起動し、initialize → session/new → config確認 → promptと進む。既存認証を使い、自動loginはしない。次ターンは同じ接続/provider sessionを使用し、タブのcloseだけなら他接続を終了しない。root/executable変更・不確定切断後の同接続再送は拒否する。旧print履歴と保存XML/enumは変更しない。ACP session IDは旧履歴へ保存しない。

- `AcpJsonRpc`はbackground readerと上限付きwriter queueを分離。改行単位のUTF-8、JSON-RPC request/response/notification、opaque string/numeric ID、null/errorを処理する。1 frameは1 MiB、outbound待ち/inbound requestは各32、1 turnの更新・要求payloadは4 Mi文字、tool/観測childは各512件まで。超過・不正frame・EOFはpendingを解放して接続を終了する。stderrは読み捨て、prompt/key/raw errorを診断へ出さない。
- textは正当な重複を含むdeltaのままEDTへ渡し、message ID変更・thought/tool/requestを境に本文を分ける。readerで全文コピーを蓄積せず、printのdeduper/result補完を使わない。toolは改行を含むopaque IDでupsertし、省略fieldを保持、明示content/locationsを置換する。`completed`は報告状態であり、shell成功や復元可の証明ではない。
- permissionのoption ID/kindはserver提示値だけ使用。command/path/location/diff、または検証済みMCP対象がない場合は許可を無効化する。#447のMCP対象はCursorの構造化rawInput（server/tool/args）から `get_project_modules` と唯一の引数 `projectPath` を有限の型へ抽出する。許可要求がJSON本文を含む場合は同じ引数との完全一致が必要で、未知tool・追加引数・欠落/不正/超過は許可しない。許可要求内の更新・検証失敗は同じtool IDの保持MCP対象にも反映し、拒否後のIDだけの再要求で古い対象を復活させない。この保持更新で実行状態の生成・遷移は行わない。表示はserver/操作/projectのplain文字列で、raw JSON・新しい永続化項目・自動許可を追加しない。`AcpMcpPermissionTest` のfixtureはCLI `2026.09.02-c22c1a3` の要求組立処理を合成値で実行した結果と同版tool更新形式であり、run23実wireの採取ではない。実IDE受入は[issue-447.json](../verification/changes/issue-447.json)で追う。Cursor質問はIDによる選択・複数選択・skip/cancel、Planは表示・accept/reject/cancelを扱う。返答直前にrun/Stop/closeを再確認し、一要求に一度だけ返信する。送信直前の取消変換を含む実際の返答を保持し、許可/拒否/取消等をカードに残す。未対応requestにはerror、notificationには返信しない。質問の自由文・Plan編集をwire契約として発明しない。
- Client fs read/writeとterminalはfalse、未実装elicitationは広告しない。これはAgent自身のファイル操作を禁止する宣言ではない。標準permission設定でも即時編集が起こり得る。ACP diffは閲覧のみで、正確な復元由来がない変更カードにRevertを提供しない。既存checkpointのroot/後続編集/Git制約は維持する。
- mode/modelはsessionのselect configOptions（確認済みID `mode` / `model`）を使い、set_config_option応答の全listを置換し、最終的な組み合わせを照合してからpromptを送る。初回modelはserver既定、以降は返された一覧のみ。printのmodel catalog/flagsをACPへ流用しない。permissionは標準、sandboxはCLI既定、worktreeはこのprojectのみを許可し、他の設定を無視せず送信前に説明する。

新規ACP会話のタイトルは、[標準session metadata](https://agentclientprotocol.com/protocol/v1/session-list)の`session_info_update.title`から、現在開いている対応タブだけへ反映する（#499）。根拠はCLI `2026.10.01-14929f9`の新規ACP会話に限定した[通知/一覧の同一ID・title実測](https://github.com/shinma06/cursor-in-android-studio/issues/66#issuecomment-5974770091)。`AcpSession`は省略を変更なし、明示nullを未命名として扱い、非string・空白のみ・4096 UTF-16 code units超を無視する。上限はローカルUIの防御でありprovider仕様ではない。準備中の最新値は保持し、初回prompt dispatchのsession bindingを先に配送してから公開する。正常prompt終端後も接続metadataとして受け取り、EDTで接続世代・生存・tab/ACP/provider ID・dispose・手動改名を再照合する。更新はstripの再描画だけで、選択・Editor・draftを再生成しない。既存の日本語9文字相当の幅内でgraphemeを省略し、全文tooltipはHTMLを無効化する。

`AcpSessionTest`と`SessionTabsTest`は初回binding、終端後/null/異常値、閉鎖した接続と別tab・手動改名保護を検証する。`AcpCommandConnectionTest`は世代更新、`SessionTabStripTest`は幅・Unicode・全文literal tooltipの根拠。合成GUI用の[既存fixtureのtitles scenario](../development/acp-gui-fixture.md)と[Case #499](../verification/changes/issue-499.json)を使い、固定buildの実画面・実provider受入は別に追う。title保存や履歴再表示、providerへの改名要求、print/ACP ID互換、外部改名の通知契機は実装しておらず、未取得は`New Agent`を維持する。親#66の残条件は継続する。#146の[2026-10-04限定実測](../research/acp-wire-probe-2026-10-04.md)は本人承認済みの新規会話の部分wireであり、このタブGUI受入・保存・改名の成功証明ではない。

Stopはsession/cancelと未回答requestの取消を送る。**cancel送信・prompt応答だけで復元を解放しない**。応答完了、拒否、長さ/要求回数上限、Agent側取消はtyped終端値で区別し、日本語で表示する。準備予約をprompt終端まで保持し、実行中に観測した子processの終了も確認する。PIDと起動時刻を保持してreparenting/PID再利用を区別し、識別不能・応答なし・childが残る・切断・終端後のtool更新は不確定とする。不確定は対象processの回収を試み、同接続再送に加え、project寿命中の別tab/printを含む新規送信・ACP metadata接続準備と復元を拒否する。準備済みturnもACP prompt dispatchまたはprint process起動予約で再確認する。判定前に開始を受理済みの処理を遡って停止せず、既存実行の完了・Stopは各runが所有する。静止したidle接続のみなら復元できる。 #440の[終了診断](../research/issue-440-acp-termination.md)はlocal trace/phase/typed終端/接続状態/子数だけを記録し、provider本文・生error・pathを出さない。明示opt-inの非公開0600ファイルでは、tab/conversationとACP PID・開始時刻、送信traceの対応に加え、最初に観測した失敗箇所/固定分類・応答検証段階・保持子孫のPID/開始時刻/生存状態/現在の親関係を別途採取できる（POSIX 0700保存先・128記録試行上限）。通常ログへ識別情報を追加しない。診断結果で終了条件を変えず、run07の原因と固定build GUI受入は未確定。

観測は25 ms間隔の条件確認であり、短時間に生成・離脱した未観測子processまで保証するものではない。常駐childや任意のdetachを安全に許可したという契約はなく、該当用途は対象外。10秒の期限は「待てば安全」の判定ではなく、不確定へ移す期限。識別できないprocessを推測でkillしない。

検証は [Case JSON](../verification/changes/issue-147.json) のT01〜04/07/10/14/16。`AcpJsonRpcTest`、`AcpProtocolTest`、`AcpSessionTest`、`AgentRequestCardTest`と既存print/tab/restoreテストを実行する。fake serverはテスト専用Python標準ライブラリで、実Cursor・認証・networkを使わない。#146の[限定実測artifact](../research/acp-wire-probe-2026-10-04.md)と固定候補のGUI受入は区別する。実測の2turn・EOF後の観測family終了から、製品の安全な複数turn・外部仕事静止を保証しない。外部providerの旧履歴load/title保存・改名同期、高度config、Android/MCP公開は後続Issueの範囲を維持する。画像入力は[ACP画像添付](../development/image-attachment.md)の1枚/snapshot/予約/失敗保持を接続し、実GUI I1–I8は未完了。画像等の受信内容表示とは分ける。

## 制約を検証する入口

| 判断 | 実装 / 回帰確認 | 証明の限界 |
| --- | --- | --- |
| ACP接続実装とGUI受入 | [AcpSession](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt) / [AcpSessionTest](../../src/test/kotlin/com/cursoragent/acp/AcpSessionTest.kt) / [変更Case](../verification/changes/issue-147.json) / [QA #152](https://github.com/shinma06/cursor-in-android-studio/issues/152) | fake serverは合成契約。[#146の匿名化した限定実測wire](../research/acp-wire-probe-2026-10-04.md)とも固定build GUIとも区別する |
| 取消と物理終了・復元排他 | [AgentRun](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt) / [WorkspaceOperationGateTest](../../src/test/kotlin/com/cursoragent/service/WorkspaceOperationGateTest.kt) / AcpSessionTest | 観測child外の任意detachまでは保証しない。不確定ならprojectの新規送信と復元拒否 |
| root/後続編集/未保存保護 | [RestoreTarget](../../src/main/kotlin/com/cursoragent/service/RestoreTarget.kt) / [FileRevertOperationTest](../../src/test/kotlin/com/cursoragent/service/FileRevertOperationTest.kt) / [RestorePolicyTest](../../src/test/kotlin/com/cursoragent/service/RestorePolicyTest.kt) | 来歴不明、ISOLATED、root外、after不一致、未保存内容は拒否。snapshotの未追跡本文制約は上記参照 |
| 旧履歴の互換 | [ChatHistoryState](../../src/main/kotlin/com/cursoragent/settings/ChatHistoryState.kt) / [PastChatsCoordinator](../../src/main/kotlin/com/cursoragent/ui/PastChatsCoordinator.kt) / [#44](https://github.com/shinma06/cursor-in-android-studio/issues/44) | metadata保持は本文永続化やACP provider resumeの保証ではない。旧metadataと新本文JSONの移行・破損・再開はConversationStoreTestで確認し、実IDE再起動は別QA |
| parser / 表示 | [print fixtureの出所](../../src/test/resources/stream-json-fixtures/README.md) / [AssistantChunkDeduperTest](../../src/test/kotlin/com/cursoragent/parser/AssistantChunkDeduperTest.kt) / [MarkdownRenderer](../../src/main/kotlin/com/cursoragent/ui/timeline/AssistantMessageBubble.kt) | completed採取とstarted推定、heuristicとACP deltaを区別。HTMLはescapeしraw実行しない |
| optional Terminal | [plugin.xml](../../src/main/resources/META-INF/plugin.xml) / [TerminalOutputReader](../../src/main/kotlin/com/cursoragent/ui/composer/mention/TerminalOutputReader.kt) | Terminalなし/無効時に全Pluginをロード不能にせず、Exception/LinkageErrorを扱う。API読取りはEDT |

実行コマンドは `python3 scripts/workflow/change_impact.py --run-tests`、Kotlin対象は `./gradlew test`。追加の横断対応表は#231の担当範囲であり、ここにCaseのpass状態を複製しない。

<a id="jvm-language"></a>

## JVMソースの言語選択

Kotlinを標準とし、Javaの例外理由・成立条件・再評価条件は対象sourceコメントまたは既存tool READMEを正本にする。Javaの追加・実質変更、その理由に関わるSDK/build前提の変更とレビュー時だけ、影響する根拠を確認してPRから参照する。Kotlinだけの作業に全Javaの棚卸しを要求せず、修辞変更だけでは再検証しない。

アイコン描画テスト/SDKの変更時は[テスト冒頭の理由](../../src/test/java/com/cursoragent/ui/ToolWindowIconTest.java)、独立Swing probeの変更時は[用途と維持条件](../../scripts/gui-fixture/README.md#現在の用途と維持条件)を読む。調査履歴・依存の棚卸しは[#430](https://github.com/shinma06/cursor-in-android-studio/issues/430)、過去の研究原証拠は[固定版の案内](../research/issue-290-rich-tool-results.md#再現と受入境界)を必要時だけ参照する。

## ビルドと実行環境

[build.gradle.kts](../../build.gradle.kts)は `androidStudio("2026.2.1.8")` で最低対応のRabbit 1 Stableを固定取得する。Gradle実行・Java/Kotlin toolchain・bytecode targetは25。[CI](../../.github/workflows/ci.yml)はRabbit 1 / 同梱JBR 25.0.3だけを対象に同一ZIPを検証する。Gradle 9.8.0 / KGP 2.4.20を使用し、KGP公式の完全サポート上限9.7.0との差は実測結果と区別する。Kotlin language/apiは2.4、stdlibはIDE同梱2.4.0を使ってZIPへ同梱しない。依存の選定根拠は[#466](https://github.com/shinma06/cursor-in-android-studio/issues/466)に記録する。

[branch ZIP](../../.github/workflows/branch-zip.yml)のschedule/manualは過去のsourceも扱うため、そのsourceの`jvmToolchain`を読み取ってJDKを選ぶ。旧21指定は旧branchを再生成するためだけに残す。tracked gradle.propertiesに有効なplatformPath代入がある旧sourceだけ、従来Quail 3 Patch 1の取得・local指定を維持する。現在のRabbit成果物にJava 21 / Quail互換処理はない。

RabbitではJCEF APIがIDE本体から分離されたため、対応する公式Web Browser (JCEF) 262.9437.22をGradleのplugin依存としてOS/CPU別に参照する。製品ZIPには同梱しない。Verifierはchecksum固定したprovider ZIPを専用offline cacheから解決し、無条件のAPI除外や旧Quailの例外流用は行わない。provider不在では`ManualBrowser`の生成境界でも`LinkageError`を捕捉してブラウザー非依存の回復画面を表示し、チャットの登録を維持する。Terminalの任意登録・linkage防御は引き続き必要。

Verifier 1.410は旧形式`depends`の`com.intellij.modules.jcef`をmodule aliasとして扱うため、公式の依存宣言でも任意依存の未解決表示が残る。固定providerの実解決をdependency graphで別途必須にし、全製品classの検査・API欠落の拒否を維持する。`-ignore-os-arch`はVerifierが実体のないOS/CPU制約moduleを要求する問題に限定し、実行host・SDKのlaunch情報・providerのOS/CPUはwrapperで照合する。[Rabbit用policy](../../scripts/workflow/plugin_compatibility.json)には再評価した任意機能の不在、API report hash、配布物の48件の古いlayout pathを含む警告全文のhashを保存し、未知の警告を拒否する。根拠は[Verifierの公式option](https://github.com/JetBrains/intellij-plugin-verifier#common-options)と[JCEF移行の公式案内](https://platform.jetbrains.com/t/2026-2-is-coming-time-to-check-your-plugin-compatibility/4618)。これらは実IDEのclassloader・native browser動作の合格を代替しない。

#212の最近使用一覧/予約一覧はSwing標準の`DefaultListCellRenderer`を使う。旧PromptQueueDialogからのdeprecated参照は削除し、残る従来16 usageだけを固定report hashで照合する。未レビューの新API使用やAPI欠落を免除せず、実ZIPと固定Rabbit/JBRで全classを検証する。

`verifyBuildSdk` は解決したproduct-infoのproductCode/full buildを `AI-262.9437.185.2621.16467767` と照合し、compile/resources/sandbox/ZIP生成前に不一致・確認不能を失敗にする。通常IDEや利用者共通の `platformPath` propertyは暗黙に使わない。local SDKが必要なときだけ両propertyを指定する（パスは各自の非公開設定に保持）。

```bash
./gradlew clean test buildPlugin -PuseLocalPlatform=true -PplatformPath="<Rabbit 1 SDKのルート（macOSはContents）>"
./gradlew buildPlugin -PpluginVersion=0.2.0-rc.1
python3 scripts/workflow/check_build_inputs.py --local-sdk "<Rabbit 1 SDK>" --wrong-sdk "<別版の有効なSDK>"
```

versionの既定は `gradle.properties` の `pluginVersion`。上の版は入力例であり正式版の決定ではない。正式候補はversionをcommit・develop統合してsourceを固定後に生成する。`-PpluginVersion` の明示入力でも内部plugin.xml、元ZIP名と内包identityのplugin.versionを揃える。source.commit/state、sdk.build、jvm.targetもZIP内へ記録し、ローカルパス・hostは含めない。Rabbitへの移行とその受入は #466で追跡し、過去の#391/#392/#393の結果を新成果物の合格根拠にしない。

標準 `buildPlugin` と配布物の識別は [ZIP配布](../development/plugin-zip-delivery.md) が正本。旧2.10.5の `androidStudio()` URL解決失敗は [固定版のCommands](https://github.com/shinma06/cursor-in-android-studio/blob/4d1514d8fa6c020d41ad9c0205b9ea24268bef57/CLAUDE.md#commands) に保全し、新版での結果と混同しない。現在の対象IDE・公式配布元・checksumは [互換検証policy](../../scripts/workflow/plugin_compatibility.json)、旧Quailの選定は履歴の [Phase 1記録](../research/modernization-baseline-2026-09-21.md) を参照。

送信前の設定利用可否と実行境界のvalidation、同一snapshotの捕捉/受け渡しは [設定検証の境界](settings-boundary.md)を参照。設定/準備変更のwriterと独立reviewerが早期拒否位置と通信側防御を照合する。

print/ACPの本文・思考・tool・要求返答・終端・usageと、全文置換/メッセージ境界の分離理由は [UIイベント契約](event-contracts.md)を参照。イベント変更のwriterと独立reviewerがwire/表示/未知処理と実経路テストを照合する。

#44の会話/turn/message ID、保存型/責務・来歴、保持削除、元入力と注入context、保存状態は[会話保存契約](conversation-persistence.md)を参照。過去のprovider sessionと本文閲覧・Revertを分ける。

## ACP非テキスト内容（#296）

画像・音声・リソースの有限情報表示、更新/上限/保存互換と未実装previewの境界は[ACP内容の有限表示](acp-content.md)を参照。
