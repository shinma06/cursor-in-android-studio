# hooks・複数root・worktreeと保存再開の契約調査

2026-09-12 / #301。基準source: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。公開資料は同日読取、CLI helpは#278で保存済みの `2026.09.10-fd3934a` を再利用した。新しいCursor CLI/provider起動、実hook/plugin実行・install、私的設定/会話/原本の読取、GUI/認証操作は **0**。以下の「対応確認」は公開契約または明記した現sourceの確認であり、実環境での成功ではない。

結論: 標準ACPにも追加rootと保存再開の入口がある。現Pluginは単一rootと新規ACP接続だけを使うため、その機能を接続済みとしない。既存の保存本文再表示・ISOLATED復元拒否・終了未確定時の拒否を維持する。#26の環境候補は不足証拠を固定して保留し、独自hook実行基盤やpersist管理サーバーを作らない。

## 公開契約と現Pluginの対応

親[#25](https://github.com/shinma06/cursor-in-android-studio/issues/25)の「plugin/hooks・multi-root/worktree・persist」行が主対象。「会話title/rename/fork」は保存再開との接点だけを扱い、[#26](https://github.com/shinma06/cursor-in-android-studio/issues/26)の既存候補の外へ広げない。

| 項目 | 公式の入口・版/範囲 | 現Pluginと判断 |
| --- | --- | --- |
| Plugins | [Plugins](https://cursor.com/docs/plugins) はuser/projectへのinstall、user/workspace/team表示、管理配布を説明。Cursor形式はrules/skills/agents/commands/MCP/hooksを束ねる。標準Agent Plugins形式の主対象はskills/MCPであり、形式名だけでhooks互換としない | 公開対応確認、専用設定UIは未実装。既存MCP管理/Skills候補の担当を再利用し、bundle管理は保留 |
| CLI plugin/hooks | [Parameters](https://cursor.com/docs/cli/reference/parameters) と保存helpに反復可能な `--plugin-dir`。[CLI changelog](https://cursor.com/docs/cli/changelog) は2026-08-11にplugin hooksの実行/reload修正を記載 | 起動入口は確認。現在のprint/ACP起動にplugin-dir指定なし。provider内部の既存設定読込はあり得るが、本Pluginが構成/成否を観測した証拠ではない |
| Rules | [Rules](https://cursor.com/docs/rules) はproject `.cursor/rules/*.mdc`、User/Team Rules、`AGENTS.md`、常時/関連性/対象file/手動適用を説明 | model contextの指示であり、OS権限やhook成功とは別。独自に全Rulesをprompt連結する実装は初回非採用 |
| IDE worktree | [Worktrees](https://cursor.com/docs/configuration/worktrees) はUI-native管理をAgents Window専用と明記。IDEには `/worktree`、`/apply-worktree`、`/delete-worktree`、`/best-of-n` のSkills経路がある | IDE内panelに必要なフローを比較する。AW管理UIの複製は初回非採用。Skills名をprint/ACPの専用methodと推測しない |
| CLI worktree | Parameters/help: `-w/--worktree [name]`、`--worktree-base <branch>`（既定current HEAD）、`--skip-worktree-setup`。 [Using Agent](https://cursor.com/docs/cli/using) は明示workspaceと隔離編集先を区別 | 現行は生成名の `-w` のみ。名前/base/setup選択、実隔離rootの構造化取得、再開先の照合は未接続。保留 |
| 複数root | CLI changelog 2026-06-29と保存helpに反復可能な `--add-dir`。Parameters表にないことだけで非対応としない。`/add-dir` は候補を更新し、自動Skills再発見には再起動の説明あり | 現行printは単一 `--workspace`。追加root/複数Git repoの原子的変更は未実装・未確認。contextに別fileを含めることはroot登録の証拠ではない |
| ACP追加root | [ACP v1 session setup](https://agentclientprotocol.com/protocol/v1/session-setup) に `sessionCapabilities.additionalDirectories` と `additionalDirectories` | 標準対応確認、Cursor installed版での広告/実行は今回未確認。現行AcpSessionは送らず、標準全体を「単一rootのみ」と断定しない |
| 本文保存/再開 | [Cursor ACP](https://cursor.com/docs/cli/acp) は `session/new` / `session/load`、CLIは `--resume` を説明。ACP v1はload/resume/closeを別能力として定義 | print再開試行と本文再表示は実装済み。保存ACP本文は閲覧のみ。外部全履歴取得・再接続・forkの成功は未確認 |
| persist | CLI changelog 2026-08-26: `agent persist`、`/detach`、`persist attach/list/stop`、`persist --resume`。保存helpにもpersist入口あり | 切断後も続くCLI実行の管理であり、本文JSON保存やACP常駐と別物。現Plugin未接続。保留 |

CLI変更履歴の上記日付は機能を説明する公開release日で、今回のinstalled版実測日ではない。#146のwire/未公開原本を再読取・再実測していない。Run Modeと設定優先順の全体は[#297 / PR298](https://github.com/shinma06/cursor-in-android-studio/pull/298)へ分担する。

## 設定・hooks・setupの所有と副作用

[Hooks](https://cursor.com/docs/hooks) はJSON/stdioで通信する別processを定義する。これはACP `session/update` のhookイベント仕様ではない。

| 層 | scope・読込/優先順 | 実行/失敗・今回の境界 |
| --- | --- | --- |
| hook設定 | project `.cursor/hooks.json` / user `~/.cursor/hooks.json`、Enterprise/Team配布。全該当hookが動き、応答の衝突はEnterprise→Team→Project→User。設定file変更はwatch/reload。projectはtrusted workspaceで読込 | 作業directoryはproject root / user `.cursor` / 管理元directoryなどsource依存。実ファイルは読まず、設定存在を成功としない |
| command/prompt hook | commandはscript、prompt型はLLM評価。`0`は成功、`2`は対象action拒否、通常の他失敗はfail-open。`failClosed`でcrash/timeout/不正JSON時の拒否を指定できる | hook別例外がある。`sessionStart` は待機/拒否を強制せず、`continue:false`も作成を止めない。`sessionEnd`応答も処理継続の保証ではない。全hookを承認防壁と表示しない |
| lifecycle/環境 | `workspaceOpen`はdesktop/CLIのworkspace開始/変更時に動き、`pluginPaths`を返せる。`sessionStart`のenvは後続hookへ共有。`stop`は追加メッセージを起こし得る | hookのconversation/generation IDは公開schema上の意味だけを使い、Plugin run IDやACP IDへの無検証な同一視をしない。transcript path/利用者情報を含み得るのでrawを保存証拠にしない |
| plugin設定 | Customizeのinstall scope/管理policy、local pluginはReload Window等で再発見、同名marketplace優先。CLIには設定経路/reloadの別記述あり | bundle配布・有効化・provider認証・個々のhook/tool実行を区別。multi-rootで同名が競合した時の実効構成とACP reloadは未確認 |
| Rules | 適用時にcontextへ入る。Team→Project→Userの競合優先、nested `AGENTS.md`は親と合成し詳細側優先 | 命令文の優先であり権限の強制ではない。個々のroot/常駐ACPでの再読込時点、CLI/IDE間の全設定同一性は未確認 |
| worktree setup | `.cursor/worktrees.json`を隔離先→project rootの順で探す。OS別キー優先、generic fallback。配列commandは隔離先で順に実行、scriptは設定fileからの相対path | インストール、fileコピー、DB等への副作用を持ち得る。コピー元 `ROOT_WORKTREE_PATH` は復元先の証拠ではない。失敗時rollback/再試行の冪等性、取消/子process回収、共有資源の隔離は公開記述だけでは保証されない |

hooksの実行権限・sandbox/認証との組合せは接続先依存で未測定。UIの「標準」でも即時編集し得る既存print実測と、事後Diff/Revertを維持する。after-edit formatter等が編集結果を再変更した場合、古いRevertのstale拒否を緩めない。Pluginからhookをもう一度実行したり、providerのstop hook follow-upをローカルqueueへ重複投入したりしない。

## root・ID・終了・保存を別々に扱う

現sourceの根拠は [TurnWorkspace](../../src/main/kotlin/com/cursoragent/service/TurnWorkspace.kt)、[RestoreTarget/Policy](../../src/main/kotlin/com/cursoragent/service/RestoreTarget.kt)、[WorkspaceOperationGate](../../src/main/kotlin/com/cursoragent/service/WorkspaceOperationGate.kt)、[AgentProcessService](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt)、[AcpSession](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt)、[AcpProcessTree](../../src/main/kotlin/com/cursoragent/acp/AcpProcessTree.kt)、[保存契約](../architecture/conversation-persistence.md)。製品codeは変更しない。

| 対象 | 現在の所有/成功境界 | 残る条件 |
| --- | --- | --- |
| command root | project.basePathをturn準備時にreal path化し固定。printのworkspace/作業directoryとACP newのcwdに使う | IntelliJ module/content root全体や追加root集合を意味しない。現在のactive file/mentionの表示pathから実行rootを変更しない |
| 隔離先 | command rootとISOLATED modeは保存するが、実際の隔離rootは取得しない | 名前/既定directory規則から推定しない。ISOLATEDはDiff閲覧だけ、checkpoint/Revert拒否。後でDEFAULTに戻しても古いカードを許可しない |
| provider ID | PRINT/ACP transportと組で扱う。SessionWorkspaceHistoryの同IDに異なるtargetが来ればUNKNOWNを保持 | provider再開成功、root存続、fork関係、完了済みtaskの証明にはならない。#66のNew Agent/代替名なしを維持 |
| Plugin ID | 保存conversation UUID、turn UUID、message UUID、tab UUID、run token参照同一性が別。遅着は元token/世代で拒否 | tabを開く/閉じることはproviderのfork/archive/deleteではない。保存conversationのrootは直近来歴で、turn別の復元権限ではない |
| print終了 | 準備予約を保持し、process構築前に追加予約。開始失敗は解放、構築後の取消は物理終了で解放。復元中は新準備拒否 | 親CLIの終了と任意のdetached hook/子processの終了は同値と保証しない。provider側の終了契約と復元への影響は未観測 |
| ACP終了 | tab単位でresident接続。prompt終端と観測子processの静止を待ち、失敗/切断/取消未確定はproject復元を無効化。closeは該当接続を停止 | resident parentが生きていても静止turnは終わり得る。PID+開始時刻で観測子を識別するが、sample間に逃げた子の完全検出は保証しない。#118の背景/Stop未確認を置き換えない |
| ACP再接続 | root/実行file変更、切断後の同tab再送は拒否。新規接続は `session/new`、load/resume/close methodは未接続 | `session/cancel`やpipe closeを保存session削除と解釈しない。エラーでprintへfallback、自動prompt再送、provider権限回避をしない |
| 本文保存 | project単位、v1 JSON、順序/ID/状態。workerが最新snapshotをまとめatomic書込。成功revisionだけ保存済み。破損/未来形式/失敗は旧dataと他会話を保持 | 保存対象は表示本文等。注入context/raw/tool実行内容/permission callbackなし。本文自体の秘密の完全除去保証なし。未送信draftは対象外。終了直前の未flush分や電源断の完全耐久性は保証しない |
| 保存の再表示 | PRINT/ACPとも可。runningはinterruptedへ。旧XMLは本文なし。保存カードからRevert/承認再実行なし | providerを動かさず表示できることと再開成功は別。PRINTの既知DEFAULT同root/provider IDだけ次送信で試行、送信直前もrootを再照合。ACP/不明/ISOLATEDは閲覧のみ |
| cleanup | Plugin履歴は明示削除、上限超過で自動削除しない。provider会話/隔離treeは別所有 | Cursor Worktrees資料は3.5以降のmachine共通上限/周期cleanupと管理外作成treeも探索対象になることを説明。CLIも同じ保持規則。session ID・名前・本文保存からtreeの永続保持を保証せず、未commit/稼働中の保護や取消後削除は未確認として残す |

WorkspaceOperationGateはproject内の復元排他であり、別IDE/project・共有DB・外部hookまで排他するhost共通lockではない。準備/実行/復元のcode確認は、新たなGUI・実process終了観察の代替ではない。

## ACP標準とCursor再開の不足証拠

[ACP v1 session setup](https://agentclientprotocol.com/protocol/v1/session-setup) ではloadは `loadSession` を確認して履歴をreplay、resumeは `sessionCapabilities.resume` を確認してreplayせず再接続する。closeは別capabilityで実行取消/資源解放を行う。[session/list](https://agentclientprotocol.com/protocol/v1/session-list) も広告が必要で、cwd filter・pagination・任意title/追加root報告を持つ。一覧が空の成功を会話移行やtitle同期成功にしない。

追加rootは広告時だけnew/load/resumeへ絶対path配列で送る。cwdは相対pathの基準のまま。load/resumeでも意図した全リストを再送し、省略/空配列なら保存済みrootが暗黙復活することはない。現在の単一root保存型へそのまま押し込まない。Cursor公式ACPページはloadを説明するが、対象版のadditionalDirectories/resume/close広告・root再開・hook読込成功は今回未測定。

比較に用いた公開schemaは#149で取得済みのcommit `bcb9d7ea13adc0b47e906c82f3d692d495a6fa34`。[stable](https://github.com/agentclientprotocol/agent-client-protocol/blob/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1/schema.json) に追加rootを確認し、forkは同commitの[unstable](https://github.com/agentclientprotocol/agent-client-protocol/blob/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1/schema.unstable.json) 側にある。公開[RFD](https://agentclientprotocol.com/rfds/session-fork)の存在も実提供の保証にしない。[CLI `/fork`](https://cursor.com/docs/cli/reference/slash-commands) と、[IDE Side chat](https://cursor.com/help/ai-features/side-chats)のhidden親context/独立本文は別契約。保存本文をprompt連結した独立tabをfork/Side chatと呼ばない。

## 最も強い既存構成と採否

JetBrains AI Assistant **2026.2** の [ACP設定](https://www.jetbrains.com/help/ai-assistant/acp.html) はcustom command/args/env、custom MCPとIntelliJ MCPの露出/tool選択を提供する。[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html) はprojectPathを指定したmodule/依存関係/検査/build/run/terminal等を提供する。これらを除外して本Plugin固有能力と主張しない。[会話履歴](https://www.jetbrains.com/help/ai-assistant/chat-mode.html)も既存比較に含める。固定Android Studioでの利用可能性/実フローは#150へ残す。

直接IDE連携の候補は、IDEが知るproject/module/content rootとproviderの明示rootを照合し、既存Diff/RestoreTarget/操作排他へ接続すること。別rootを無断で追加せず、曖昧なtargetへ復元しない点を受入で比較する。現在は競合を超えるUXを実測していない。

| 既存候補 | 採否・最小接続先 | 不足証拠 / 再開条件 |
| --- | --- | --- |
| 作業場所の説明 | **採用提案**。ToolWindowChatActionsの既存ISOLATED helpで「checkpointとRevertが利用不可」を簡潔に揃える。現在はcheckpointだけ記述、実境界は両方拒否。必要なら同じ詳細導線へsetupの副作用を説明 | PMが#297の表示候補とまとめて個別採用。enum/flag/既定/validationを変えず、通常入力下の常時警告は増やさない。C1 |
| plugins/hooks/Customize | **保留**。既存MCP/Skills経路とproviderの管理機能を優先。設定editor・自作hook runnerは**初回非採用** | 実効source/scope/競合、reload成功と失敗、安全な表示carrier、取消/子processの所有が公開かつ許可されたfixtureで揃うこと。C2 |
| multi-root / 名前/base/setup | **保留**。最初にACP広告を確認する設計。TurnWorkspace/RestoreTargetと保存来歴を一貫して扱う最小変更だけ検討 | root集合・相対path基準・実隔離root・setup失敗/取消/再開・共有資源/cleanupまで証拠が必要。`--add-dir`追加だけの実装は採らない。C3/C4 |
| provider履歴再開 | **保留**。AcpSessionの既存接続にcapability確認したload/resumeを個別評価、history表示とは分離 | 所有session・root/MCP再接続・欠落/拒否・replay重複/取消・保存型互換が必要。#146の許可範囲と#66の保留維持。C5 |
| persist / Side chat / fork | **保留**。provider公式session操作を評価、独自常駐serverやprompt連結による代替は**初回非採用** | attach/Stop/detach/終了/再起動とID、実root・残子process・provider拒否の証拠。#118を重複実測せずPMが別scopeを指定した時だけ。C5/C6 |
| 既存本文保存/復元 | **対応確認・既存採用先を維持**。#44の履歴実装と#259、#102を再利用 | 新機能を載せず既存固定build受入を先に扱う。追加機能の完了で既存QAをpassにしない |

## 有限Caseと検証

C1–C6は将来製品採用時の不足証拠で、今回のGUI Case/passではない。

| Case | 必要な観察 / 今回の到達点 |
| --- | --- |
| C1 表示と復元 | ISOLATEDの両復元拒否、DEFAULTへ戻した古いカード/後続編集の保持。今回は既存RestorePolicy/TurnWorkspaceの合成結果。GUIは#102へ |
| C2 hooks/settings | project/user/team競合、読込前後、拒否/失敗/timeout、開始取消/終了/追加入力、plugin reload、共有環境の所有。今回は公開記述だけ、実hookは0 |
| C3 root集合 | 新規/再開で広告あり/なし、全配列/省略/空、root変更/消失/symlink、複数repo同名fileと相対path。今回は単一rootの既存安全判定を確認、複数rootは未実装 |
| C4 worktree/setup/cleanup | name衝突/base未存在/setup一部失敗、取消・終了後の子process、再開前にtree消失/変更、dirty/shared fileの保全。今回は引数と公開入口だけ、Cursor worktree操作0 |
| C5 保存/再開/fork | PRINT/ACP/旧metadata、ID・順序・不正/未来形式・書込失敗・終了未保存、load replay/resume非replay、provider拒否時の閲覧維持。今回の保存回帰は#44の範囲、provider復帰は未測定 |
| C6 実行の寿命 | 準備取消/構築失敗、Stop/close/切断後の元run遅着、resident parentと子の静止、persist detach後も動く処理の所有。今回のgate/token合成は実hook/背景taskの終了証拠ではない |

既存7クラス **42件** の純粋合成テスト（メモリcallback/XMLと使い捨てpathのみ）が成功、failure/error/skip 0。ConversationStore 8、ConversationWriter 2、ConversationResumeRoot 2、WorkspaceOperationGate 7、TurnWorkspace 5、RestorePolicy 7、SessionTabs 11。新しいchecker/fixture/frameworkは作らず、ACP初期wireや実process起動testは追加実行しない。

```bash
./gradlew test \
  --tests com.cursoragent.history.ConversationStoreTest \
  --tests com.cursoragent.history.ConversationWriterTest \
  --tests com.cursoragent.history.ConversationResumeRootTest \
  --tests com.cursoragent.service.WorkspaceOperationGateTest \
  --tests com.cursoragent.service.TurnWorkspaceTest \
  --tests com.cursoragent.service.RestorePolicyTest \
  --tests com.cursoragent.session.SessionTabsTest
python3 scripts/workflow/change_impact.py --base origin/develop --run-tests
git diff --check
```

#44はdevelop統合済み、#259のHISTORY-RESTART/INTERRUPT/FAILUREとmain反映は未完。#102は#204固定build巡回で前提未達・未試行。#66は空一覧以外のtitle/名前同期未確認でNew Agentを維持する。本調査でQA状態を変更しない。#146/#118/#150の担当と保留を保持する。

共通検証の分類・実行数・CI・固定独立review/STOPはIssue/PRの固定SHA記録を正本とする。研究受入は対応表・採否・最小接続先・不足証拠の確定まで。親25/26・M3・製品GUI・QA/mainの受入ではなく、#26 deferredを自動採用しない。PR261 freezeを維持し、writer停止後の統合・採用Issue分離はPMへ渡す。
