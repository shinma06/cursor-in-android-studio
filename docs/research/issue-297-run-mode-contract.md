# #297 Run Mode・allowlist・承認設定の適用/表示契約

調査日: 2026-09-12。基準実装: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。

結論: **現行enum/既定値とACPの設定拒否を維持し、実効権限を保証するように読める文言と適用scopeを先に整える。** allowlist editorや独自判定器を追加せず、Cursorの公開契約で確認できない優先順・保存範囲・常駐ACPへの反映を保留する。新採用IssueはPMが分離する。

## 版・証拠・境界

- 公式IDE内panel / Run Modes / CLI / ACP資料は各リンクを2026-09-12に確認。過去の4択を現在へ転用しない。現 [Run Modes](https://cursor.com/docs/agent/security/run-modes) はAuto-review / Allowlist / Run Everythingの3択、sandboxは別設定。公式履歴では3.5 (2026-05-22)にAsk Every Timeを廃止しRun in SandboxをAllowlistへ統合、[3.6 (2026-05-29)](https://cursor.com/changelog/auto-review)でAuto-reviewを導入した。
- CLI `2026.09.10-fd3934a` は #278 の既存helpを再利用。help SHA-256 `be0388f7e15063e8c147ea5759e789a7f2ac87f95727dd596e1d8253e32b378b`、ACP help `9b55e20ac13686f2854b5fa7da3fd03a007be1b3c5cc9ba434f53596c45993af`。helpの存在を実効権限の実測としない。#278の公開可能な既存catalogはconfig IDs `mode/model` のみで、permission/network設定の広告は未確認。
- ACP v1は固定schema commit [`bcb9d7ea13adc0b47e906c82f3d692d495a6fa34`](https://github.com/agentclientprotocol/agent-client-protocol/tree/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1) を照合。stable SHA-256 `caf62ff962ada396878372ced11efb2c6764e59d90919a38583c319948931a42`、unstable `bf7d01218c4fc330b4f08840bda168dca5525951cdaf2dc32e58b9a5b4a8eb75`。permission kinds/selected/cancelledは両方に存在する。
- 公開print fixture `2026.09.02-c22c1a3` の即時編集と事後Diff/Revertを維持する。[fixture範囲](../../src/test/resources/stream-json-fixtures/README.md)。#146の初期wire/Plan/質問/permission/cancel実測、未公開原本や個人の設定を読まない。今回provider prompt、Cursor CLI起動、認証/allowlist/保護変更、sandbox解除、GUI/Browser/clipboard操作は **0**。Gradle内の既存合成テストだけを再利用した。

## 公式設定は別々の層

| 概念 | 公開経路・scope・優先順 | 適用/保存と今回の判断 |
| --- | --- | --- |
| Agent / Ask / Plan | 行動目的のmode。CLI `--mode ask/plan`、agentは省略。ACPは広告されたmode/configから選ぶ。[CLI Parameters](https://cursor.com/docs/cli/reference/parameters) | Run Modeやsandboxとは別。Ask/Planという名前だけで全toolの権限を推測しない。 |
| IDE Run Mode | Auto-reviewはallowlist→可能なshell sandbox→classifier、Allowlistはallowlistと任意sandbox、Run Everythingは自動実行。Browser/File-Deletion/External-File Protectionやteam制約は別に残る。[Run Modes](https://cursor.com/docs/agent/security/run-modes) | 自動実行の選択を全操作の保証としない。classifierはbest-effortであり厳密な境界ではない。Cloud Agentsの設定へ転用しない。 |
| IDE command/MCP allowlist | `permissions.json` の `terminalAllowlist/mcpAllowlist`。userとworkspaceの配列を結合。team管理 > file定義 > IDE UI。定義された種類のUIはread-onlyになる。[permissions.json](https://cursor.com/docs/reference/permissions) | 起動時読込＋変更監視、JSONC可。key欠損と空配列は別。詳細referenceは空配列を空allowlistとする一方、[Deployment Patterns](https://cursor.com/docs/enterprise/deployment-patterns)には空でもUI fallbackという記載があり不整合。editor実装前に実版の挙動を確認する。 |
| classifier指示 | 同 `permissions.json` の `autoRun.allow_instructions/block_instructions`。local user/workspaceの指示を併用し、team定義がある場合は優先。[Run Modes](https://cursor.com/docs/agent/security/run-modes) | 指示はclassifierへの判断材料で、強制deny tokenではない。CLIにも [2026-06-22 changelog](https://cursor.com/docs/cli/changelog)で `--auto-review` と `permissions.json` 指示の利用が公開済み。IDE allowlistとCLI permission tokensの保存契約は同一ではない。ACPでの実効適用は別証拠待ち。 |
| CLI approval設定 | global `cli-config.json` の `approvalMode=allowlist/auto-review/unrestricted`、`sandbox.mode/networkAccess`。project `.cursor/cli.json` はpermissionsのみ。[CLI Configuration](https://cursor.com/docs/cli/reference/configuration) | `CURSOR_CONFIG_DIR` 等の公開overrideがある。Pluginはこれらを管理しない。no flagは保存設定の消去ではない。複数scopeのpermissions配列のmerge/replace、常駐中の再読込、相反flagの優先順は今回の公式根拠では確定しない。 |
| CLI command/file/domain/MCP許可 | `permissions.allow/deny` の `Shell(commandBase[:args])`、`Read/Write(pathOrGlob)`、`WebFetch(domainOrPattern)`、`Mcp(server:tool)`。deny優先、相対pathはworkspace基準。[CLI Permissions](https://cursor.com/docs/cli/reference/permissions) | WebFetch許可はshell全体のnetwork許可ではない。MCP tool許可はserver接続承認や認証ではない。名前・引数・wildcardを勝手に拡張せず、未来の編集機能は構文とscopeが固定できた場合のみ。 |
| sandbox network/filesystem | user/workspace `sandbox.json`、team/hardcoded制約が上位。path/denyはunion、team allowlistはlocal unionに優先、denyとrestrictive booleanが優先。[sandbox reference](https://cursor.com/docs/reference/sandbox) | networkのdomain/IP/CIDR規則とCLI WebFetch規則を共通matcherにしない。特にsandbox `*.example.com` は基底domainも含む記載、WebFetchはsubdomainという記載。default denyとUIのdefaults加算/Allow Allも別層。常駐ACPへの反映・CLIとの完全なscope一致は未実測。 |
| workspace trust | IDEのworkspace trustとCLI `--trust` は別入口。[Agent Security](https://cursor.com/docs/agent/security)、[CLI Parameters](https://cursor.com/docs/cli/reference/parameters) | printは既存の `--trust` を常に付ける。作業フォルダ信頼を、全tool承認・sandbox解除・外部path許可へ読み替えない。ACPには現実装から付けていない。 |
| 個別保護 | Browser / File-Deletion / External-File Protectionは自動modeでも承認を要求し得る。sandboxは `.git/config/hooks`、Cursor設定等を保護する。[Run Modes](https://cursor.com/docs/agent/security/run-modes)、[sandbox reference](https://cursor.com/docs/reference/sandbox) | CLI/ACPで同じ全toggle/状態が取得できるとは未確認。`.cursorignore` はterminal/MCPまでの隔離境界ではない。[Ignore file](https://cursor.com/docs/reference/ignore-file)。Plugin独自の保護switchや全安全表示を作らない。 |
| auth / MCP接続 | CLI login/API key、MCPのlogin/enable/disableとtool許可は別。[Authentication](https://cursor.com/docs/cli/reference/authentication)、[Parameters](https://cursor.com/docs/cli/reference/parameters)。Cursor ACPは既存CLI認証を利用でき、project/user MCPとteam-level制約を記載。[Cursor ACP](https://cursor.com/docs/cli/acp) | 承認済みserver、利用可能tool、認証済み、許可済み操作を別々に表示する必要がある。一般MCP資料のteam提供能力からACP対応を推定しない。今回login/status/mcpコマンドを実行しない。 |

公式にない「全設定の一意な優先順位表」を作らない。CLI no flagからeffective approvalModeを決めたり、IDE `permissions.json` とCLI `permissions.allow/deny` を自動変換したりしない。sandboxの上限/例外・個別保護・team policy・実際のtool経路を失った要約は実効権限ではない。

## 現Pluginの保存・適用・表示

[AgentSettingsState](../../src/main/kotlin/com/cursoragent/settings/AgentSettingsState.kt) はapplication-level persistent stateで、`cursor-agent-settings.xml` に保存する。permission/sandbox/worktreeは全project共有。会話ごとのmode/modelとはscopeが異なる。[ToolWindowChatActions](../../src/main/kotlin/com/cursoragent/ui/header/ToolWindowChatActions.kt) の選択はその場でstateへ書込み、SettingsのApply/Cancelを経由しない。メニューを開く/Escapeだけでは書き換えない。`AgentSettingsConfigurable` のApply/CancelはCLIパス・通知に適用される別UIである。

[AgentUiController.sendPrompt](../../src/main/kotlin/com/cursoragent/ui/AgentUiController.kt) が送信準備前に [TurnSettings / TurnWorkspace](../../src/main/kotlin/com/cursoragent/service/TurnWorkspace.kt) へsnapshotを作る。進行中に共通設定を変えてもそのturnの引数は変えず、他tab/projectを含む次回snapshotへ適用する。実行中でもこれらの選択肢自体は有効であり、「現在値」は進行中turnの実効値とは限らない。provider側のhot reloadはこのsnapshotだけでは保証できない。

| 保存enum / 表示 | print起動引数 | ACP現validation | 判定 |
| --- | --- | --- | --- |
| `ASK_EVERY_TIME` / 標準 | permission flagなし | 許容される唯一のpermission値 | **毎回事前確認ではない**。保存enumは互換性のため維持。CLI保存済み設定・allowlistを解除しない。 |
| `AUTO_REVIEW` / AIによる確認 | `--auto-review` | 拒否 | helpとCLI changelogに存在。未対応/無効時の挙動を勝手にfallbackやforceへ変えない。 |
| `RUN_EVERYTHING` / すべて自動実行 | `--force` | 拒否 | helpは明示denyの例外を残す。UIの「確認を省略して、ツールの操作を実行」は広すぎるため修正候補。 |
| `SandboxMode.DEFAULT` / CLIの既定 | sandbox flagなし | 許容 | off保証ではない。既存test名「default to off」も実際にassertするのはDEFAULT enumだけ。 |
| `ENABLED` / 制限あり | `--sandbox enabled` | 拒否 | flagはconfig overrideとhelpに明記。全toolが必ずsandbox内という意味ではない。 |
| `DISABLED` / 制限なし | `--sandbox disabled` | 拒否 | sandboxを使わない要求。deny/個別保護を全部消す意味ではない。 |
| `WorktreeMode.DEFAULT/ISOLATED` | 通常 / `-w` | DEFAULTだけ許容 | workspace/復元安全性の別設定。未知/分離rootのRevert拒否を維持。 |

printは毎turn processを起動し、workspace引数とsnapshotを組み立てる。標準permissionでもCLIは即時編集でき、Revertは事後操作である。既存 [ImmediateEditNotice](../../src/main/kotlin/com/cursoragent/ui/ImmediateEditNotice.kt) を維持し、入力欄下への常時説明は追加しない。

ACPは [AgentProcessService](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt) から `agent acp` のみを起動し、同じtabの接続を維持する。permission/sandbox/trust flagは付けない。`session/set_config_option` は広告されたmode/modelだけを変更・readbackする。UIの早期検査と [AcpSession.validateSettings](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt) の実行境界の両方で、標準permission / CLI既定sandbox / 通常workspace以外を拒否する。これは**Pluginの確認済み組合せ制限**であり、Cursor ACP全体がこれらの設定を提供できないという意味ではない。起動前の保存設定にどの権限が含まれるかをPluginは確認していない。

print `SessionInit` はsession ID/modelだけを取り込み、既存観測の `permissionMode` はUIへ伝わらない。ACPのconfigイベントもmode/modelだけである。したがって現UIにeffective permission/network scopeの表示はない。global fileを読むだけで上位policyや常駐中の効果まで確定できないので、読み取り診断の新設も独立の契約が必要。

## ACPの要求・拒否・取消・認証待ち

標準 `session/request_permission` は `sessionId/toolCall/options`、選択肢は不透明な `optionId` と `kind=allow_once/allow_always/reject_once/reject_always`。応答は `selected+optionId` または `cancelled`。拒否も選択肢への応答であり成功の許可ではない。`session/cancel` 時は未回答permissionへcancelledを返す。[ACP Tool Calls](https://agentclientprotocol.com/protocol/v1/tool-calls)。毎tool必須の事前要求ではなくAgentが送った要求への返答契約である。

| 状態 | 現コード・表示 | 採否/残証拠 |
| --- | --- | --- |
| permission待ち | `AcpProtocol` → `AgentInputRequest` → `AgentRequestCard`。「操作の確認」、型付きID、plain textで表示。未知選択肢は無効、重複/未提示IDは許可しない | 既存を維持。parserは重複option IDを拒否。全ての操作がここを通るという保証はしない。 |
| 対象不明 | command/path/locations/diffのいずれもないと許可不可。拒否・取消は可能 | 安全側の拒否を維持。URL/server/toolだけの要求は現targetモデルでは表せないため、Fetch/MCP固有target表示は実carrier確認後。titleから対象を推測して許可しない。 |
| 今後も許可/拒否 | providerのkind/nameを示して選択IDを返す。Pluginは自身のenum/allowlistを書き換えない | 「回答を送信しました」は実行成功や永続化のreadbackではない。保存先・全project/当該sessionの範囲・有効期間は未確認。scopeを説明する最小文言候補。 |
| 取消/失敗/終端 | pending cancel、遅いクリックはcancelへ置換、送信失敗はresolved null。AgentRunはerror/Stop/uncertain/outcomeを分離 | 拒否・token/request limit・Agent側cancelを通常成功に丸めない。停止・物理終了未確定は復元を解禁しない。 |
| 認証待ち | 初期接続は既存認証を前提に `initialize → session/new`。`authMethods` をUIへ出さず `authenticate` を呼ばない。RPC errorは数値codeの一般エラー、stderrは破棄 | 明確なauth待ちUIは未接続。`authMethods` 広告は認証が今必要な証明ではない。構造化auth-requiredと安全な案内の契約が得られるまで、曖昧なerror全文からloginを自動起動しない。[ACP初期化](https://agentclientprotocol.com/protocol/v1/initialization)、[Cursor ACP](https://cursor.com/docs/cli/acp) |
| MCP認証/接続待ち | MCP dialogはlistとenable/disableだけ。非zeroを成功にせずgeneric失敗表示、読み直しあり。個別login/timeout/cancelと常駐ACPへの反映は未接続 | enable成功を認証/個別tool許可成功と言わない。公開scopeと適用後readbackが揃ってから改善を採用。 |

`notifyOnApprovalPending` という既存保存名は、printのtool開始通知を制御し、UIも「ツールの実行が始まったら通知する」である。ACP Inputはカード追加のみで専用通知はない。名前から承認待ち通知実装済みと判断せず、必要なら設定互換性を保った別採用にする。

## 比較と最小修正候補

Cursor IDE内panelの設定・承認理由・個別保護が基準。JetBrains AI Assistant **2026.2** の [ACP設定](https://www.jetbrains.com/help/ai-assistant/acp.html) はagent起動args/env、custom MCP、IntelliJ MCPとtool subsetを提供する。[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html) のshell/run configuration確認省略設定は、Cursor側Run Modeと別の層である。最も強い構成では両方の制約を比較する。実runtime/固定IDE比較は #150 へ残し、設定画面や承認カードを本Plugin独自能力と呼ばない。

| 候補 | 最小の変更先・内容 | 必要Case / 採否 |
| --- | --- | --- |
| 表示scopeを正確にする | `ToolWindowChatActions` のhelpに全project共有・次回送信準備へ適用、標準はCLI設定継承を示す。「すべて自動実行」は明示deny等の例外、「制限あり」は対応shellの実行範囲を説明する。`AgentSettingsState` の未使用labelとtestコメントも必要なら直近だけ訂正 | **採用候補**。保存enum/値/引数/既定/validationは変えない。常時警告や4択UIは追加しない。P1/P2。 |
| ACP不適合を選択時に理解できる表示 | 同じmenuの選択肢にACPの現確認範囲を示す。拒否理由は既存serviceに集約し、送信時boundaryを残す。共有設定を勝手にstandardへ戻さない | **採用候補**。別tabのprint設定を壊さない設計を固定してから実装。P1/P2。 |
| 今後も許可のscope | `AgentRequestCard` に「適用範囲は接続先の仕様による」等の簡潔な説明を追加候補。state変更と応答送信を分ける | **採用候補**。実際の保存範囲のUI表示は証拠待ち。P3。 |
| auth待ち・Fetch/MCP target・MCP管理 | `AcpJsonRpc/AcpSession` のtyped error/advertised認証、`AgentTool` の対象、MCP dialogの再読込結果を既存経路で接続 | **保留**。#146を重複せず公開carrier・固定合成fixture・安全な再開経路がPM承認scopeに揃った時。P4/P5。 |
| Run Mode再構成/allowlist editor | IDE設定fileのコピーや自作matcher、独自classifier、global reset、未知flagを採用しない | **保留**。実効scope/優先順/empty policy/適用/保存の不足証拠を解消した別Issueのみ。P5。 |

## 将来の製品Case・既存QAとの分担

| Case | 固定buildで必要な確認（今回未実施） |
| --- | --- |
| P1 保存/適用 | 3 permission×3 sandbox×2 workspaceの選択とprint引数、別tab/project共有、送信準備中/実行中の変更が既存turnへ混入しないこと。メニュー選択とSettings Apply/Cancelを区別。#40/#107の同じ確認を二重実施しない。 |
| P2 ACP設定境界 | 対応組合せだけ許可、他17組合せは早期と実行境界で起動前拒否。明示print選択へ案内し無断fallbackなし。保存設定を自動変更しない。 |
| P3 permission | opaque ID、対象有/無、4kind、未知/重複、不正/二重click、Stop/close/遅着、送信失敗を区別。回答送信/拒否/取消をtool成功や永続設定変更にしない。新live permission実測は #146 owner/公開範囲に従う。 |
| P4 auth/停止 | auth-required根拠のある合成応答と単なるerror/timeoutを分け、無断login/秘密表示/自動再送なし。拒否・Agent cancel・user Stop・非zero・未確定終了の日本語表示と復元拒否を確認。#104/#102へ結果を相互参照。 |
| P5 provider scope | 新規の明示承認がある将来調査だけで、user/project/teamの競合、empty/invalid policy、sandbox/networkとWebFetchの差、個別保護、常駐ACP/新turn/新processへの反映と保存先を確認。今回のsyntheticコード結果では代替しない。 |

[既存QA #20](https://github.com/shinma06/cursor-in-android-studio/issues/20) は追跡、[#40](https://github.com/shinma06/cursor-in-android-studio/issues/40) は即時編集・設定・sandbox横断、[#102](https://github.com/shinma06/cursor-in-android-studio/issues/102) は復元先/排他、[#104](https://github.com/shinma06/cursor-in-android-studio/issues/104) は停止/実異常、[#107](https://github.com/shinma06/cursor-in-android-studio/issues/107) はCLIパス保存を所有する。本文と全コメントを確認した。#204巡回の#104 ERROR passなどは識別済み過去buildの結果であり、本調査・次製品buildのpassにしない。#40/#102未試行、#104停止/#107全系列未完の境界を維持する。

## 再現・検証・完了条件

新しいcheckerを作らず、既存の合成設定/permission/停止テストを再利用する。以下は純粋な設定とmock/callback、XMLメモリ操作、使い捨てpathだけであり、Cursor process/アカウント設定は利用しない。`AgentSettingsBoundaryTest` は18組合せとsnapshot保持、実行境界拒否時のlaunch **0** を確認する。テスト中の「設定変更」は新規メモリobjectの値で、実設定変更ではない。

```bash
./gradlew test \
  --tests com.cursoragent.service.AgentSettingsBoundaryTest \
  --tests com.cursoragent.settings.AgentSettingsStateTest \
  --tests com.cursoragent.acp.AcpProtocolTest \
  --tests com.cursoragent.service.AgentRunTest \
  --tests com.cursoragent.service.TurnWorkspaceTest
python3 scripts/workflow/change_impact.py --base origin/develop --run-tests
git diff --check
```

実行件数・共通分類・CI・固定独立review/STOPはIssue/PRの固定SHA記録を正本とする。研究scopeは対応表・採否・最小候補・残証拠までで、parent #25/M3・製品/GUI・QA/main・#146の完了にはしない。#66/#150とPR261 freeze、既存ownerを維持する。
