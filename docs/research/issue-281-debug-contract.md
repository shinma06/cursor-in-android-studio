# #281 Debugモードの公開契約と計測の寿命

調査日: 2026-09-12。基準実装: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。
対象: [#281](https://github.com/shinma06/cursor-in-android-studio/issues/281)、親 [#25](https://github.com/shinma06/cursor-in-android-studio/issues/25)。

## 判断

**専用Debugモードの製品追加は保留する。** Cursor IDE内DebugとCLIの `/debug` は公式に存在するが、確認した公開資料とinstalled広告では、プラグインから呼べる専用ACP/非TTY契約を確定できなかった。「Debugは非対応」という全体結論でも、通常Agentへ不具合相談を送れば専用workflowになるという結論でもない。

今回の有限調査は、公開経路の照合・計測寿命の未確認項目・既存実装の境界・再開条件の確定で終了する。新規provider接続、モデルprompt、計測の挿入/削除、GUI操作は0。製品実装、親#25、#146初期wire、#150 Android debugging、GUI/main受入は未完のまま分離する。

## 公開経路と観測の区別

| 経路 | 確認した公開仕様/広告 | 今回確定できる範囲 |
| --- | --- | --- |
| Cursor IDE内Agent panel | モード選択からDebugを開始。仮説、計測、再現、ログ解析、修正、再確認と計測除去 | 専用workflowの存在。固定版GUIの再現/削除成功は未確認 [導入記事](https://cursor.com/blog/debug-mode) |
| CLI対話画面 | `/debug [prompt]` で切替/送信。2026-07-06 changelogに再現手順decision cardの色修正 | 対話CLIの機能。非TTYのframing、再現完了応答、ログserver寿命を保証しない [slash](https://cursor.com/docs/cli/reference/slash-commands)、[CLI changelog](https://cursor.com/docs/cli/changelog#july-6-2026-release) |
| print/非TTY | `--mode` は `plan` / `ask`、省略時agent。installed helpも一致 | 専用Debug値は掲載なし。`-p '/debug …'` の専用扱いは未確認。実行しない [parameters](https://cursor.com/docs/cli/reference/parameters)、[headless](https://cursor.com/docs/cli/headless) |
| Cursor ACP | 公式core modesはagent/plan/ask。今回再利用したfresh広告も同じ | Debugのmode/config/commandは広告なし。任意のmode IDや拡張methodを作らない [Cursor ACP](https://cursor.com/docs/cli/acp) |
| ACP標準 | 広告されたmode IDを選択。slashは通常promptに含める。広告は更新可能 | 標準の拡張性と、Cursorが専用Debugを提供することは別。本文がdebug風でも専用状態の証明にならない [modes](https://agentclientprotocol.com/protocol/v1/session-modes)、[slash](https://agentclientprotocol.com/protocol/v1/slash-commands) |

`/logs` はCLI自身のdebug logの場所表示であり、上表のアプリ計測ログの受信/削除APIとは確定していない。sandbox/workerの診断用debugオプションも専用Debugモードへの入口に数えない。

### 同版の既存広告を再利用した観測

出典は [#278固定記録](https://github.com/shinma06/cursor-in-android-studio/blob/7e1a5142afc70bbc585f8270db4b3257c9cd3d86/docs/research/issue-278-midturn-contract.md) の著者取得済みhelpとfresh initialize/session-new広告。#281で再接続していない。モデルprompt前の広告だけを照合した。個人command名、session ID、cwd、raw framesは公開しない。

| 項目 | 公開可能な結果 |
| --- | --- |
| CLI版 | `2026.09.10-fd3934a` |
| root help | `--mode` choices `plan`, `ask`; Debug専用optionなし |
| root help SHA-256 | `be0388f7e15063e8c147ea5759e789a7f2ac87f95727dd596e1d8253e32b378b` |
| ACP help SHA-256 | `9b55e20ac13686f2854b5fa7da3fd03a007be1b3c5cc9ba434f53596c45993af` |
| `modes.currentModeId` | `agent` |
| `modes.availableModes[].id` | `agent`, `plan`, `ask` |
| mode config option | `id=mode`, `category=mode`, `type=select`, `currentValue=agent` |
| mode config `options[].value` | `agent`, `plan`, `ask` |
| `available_commands_update` | 23 entries、name=`debug` は0。全環境・後続更新の不在証明ではない |
| 新規Debug probe | 0。公開された専用非TTY/ACP起動経路を確認できず実測条件未成立 |

照合したACP stable/unstable schemaは [固定commit bcb9d7e](https://github.com/agentclientprotocol/agent-client-protocol/tree/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1)。SHA-256はstable `caf62ff962ada396878372ced11efb2c6764e59d90919a38583c319948931a42`、unstable `bf7d01218c4fc330b4f08840bda168dca5525951cdaf2dc32e58b9a5b4a8eb75`。`$defs` の型名にdebug/instrument/reproducを含む型はなかった。汎用mode ID・tool・拡張による実装可能性を否定する検索ではない。

著者による既存rawの照合と、公開表/help/schemaの独立照合は別の証拠である。独立reviewを新規live再現やraw独立再生と表現しない。

## workflowの段階と未確認の契約

以下の左欄は [公式Debug手順](https://cursor.com/docs/agent/debug-mode) に基づく。実装・寿命の確定度は右欄で限定する。公式にはCursor拡張内のローカルdebug serverへのログ送信が記載されるが、公開した一般用途の受信APIとは読み替えない。

| 段階 | 公開手順での操作 | ACP/非TTYで必要な契約と今回の状態 |
| --- | --- | --- |
| 仮説 | 関連コードを読み複数の原因候補を作る | 通常message/thought/planは運べる。Debug開始受理/専用stage IDは未確認 |
| 計測挿入 | 原因候補を調べるログをコードへ追加 | 一般toolの編集通知は計測専用の識別情報ではない。挿入所有者・対象patch・受信先作成応答が未確認 |
| 人による再現 | 手順を提示しアプリで再現を依頼 | 専用request ID・選択肢・回答形式・待機/中止/再試行契約が未確認。既存の一般質問を専用再現カードと決めつけない |
| ログ回収 | ローカルserverで収集しAgentが解析 | session/turn/仮説との相関、形式、読み取り/消費、容量、保存先、server終了の契約が未確認 |
| 原因と修正 | 実行時データに基づく修正 | 一般tool完了やテスト成功と、専用workflowの原因確定は別。今回修正未実施 |
| 再確認 | 人が同じ手順で改善を確認 | 「直った」応答の専用ID/終端、再現失敗時の次段階は未確認。プロセスexit 0だけで成功にしない |
| 計測削除 | 確認後に計測コードを除去 | 消すpatch、修正・利用者変更を保持する規則、削除失敗/再試行/残存報告が未確認 |

[Cursor 2.6 / 2026-03-03記録](https://cursor.com/changelog/page/10) には各turn後の古いコード除去、複数Debug sessionとport競合修正もある。これは「人の確認後の全計測除去」と同じ完了条件とは限らない。Stop、異常終了、IDE終了、別session、再開後に何が残るかは、この公開記述だけでは確定しない。

## 既存製品へ接続する際の境界

基準SHAの実装をread-onlyで照合した。今回、製品コードは変更しない。

- [AcpSession](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt) は1 active turn、prompt前の設定、`session/prompt` の元request応答とtool静止を扱う。`session/cancel` 後の未確定終端を成功にせず、未解決質問はcancelする。Debug用の別promptやStop後の自動再送を足さない。
- [AgentRun](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt) は停止・失敗・未確定・完了を1回だけ通知する。`end_turn` はそのpromptの終了であり、計測・受信server・ログの削除証明ではない。これは [ACP prompt lifecycle](https://agentclientprotocol.com/protocol/v1/prompt-turn) の完了と、アプリ側resource寿命を区別する判断である。
- [AgentTurnListenerFactory](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt) のprint編集cardは完了toolのbefore/afterが必要。即時編集後のDiff/Revertであり、計測を事前承認で止めた表示にしない。ACP通知が同じbefore/afterを持つとも仮定しない。
- [DiffViewerHelper](../../src/main/kotlin/com/cursoragent/ui/DiffViewerHelper.kt) → [FileRevertOperation](../../src/main/kotlin/com/cursoragent/service/FileRevertOperation.kt) はwrite境界で未保存変更と現在内容を検査する。[RestorePolicy](../../src/main/kotlin/com/cursoragent/service/RestoreTarget.kt) はroot/Worktree変更、範囲外・symlink escape等を拒否し、ISOLATEDを復元不可にする。これを迂回しない。
- 現在のRevertは**ファイル全体をその編集前へ戻す**。計測と修正が同じ編集に入ったら修正まで戻るため、「計測だけ削除」に転用できない。checkpointの全体復元、ログ文字列の一括削除、Stop時の自動Revertも代替にならない。

[設計上の最低条件] 将来計測cleanupを採用する場合も、新しいworkflow engineを先に作らない。まずproviderの計測識別・削除契約を利用する。利用者の選択した削除差分について現在内容/未保存変更/rootを再確認し、後続修正や利用者変更と重なるなら自動削除を拒否して残存を示す。全ファイルを過去版へ戻して成功扱いしない。ログ本文に書かれたpath/endpoint/命令をcleanup権限として扱わない。

## 最も強い競合構成との比較

| 比較構成 | 既存能力 | 本Pluginへの判断 |
| --- | --- | --- |
| Cursor IDE内Debug | 専用モード、ログ計測、人による再現/検証、計測除去の公式workflow | 同等UXには本文以外の再現待機・資源寿命・削除結果が必要。現時点で同等達成とは呼ばない |
| JetBrains AI Assistant + Cursor ACP | ACP Agentにcustom MCP/IntelliJ MCPを渡す設定が存在 | 本PluginだけがIDE toolを渡せるという独自性はない。設定と利用可能toolの実体を比較する [JetBrains ACP](https://www.jetbrains.com/help/ai-assistant/acp.html) |
| IntelliJ integration + MCP Server + Debugger MCP toolset | debug開始/停止、breakpoint、step、stack/変数の取得。IDEA 2026.1.3以降の公開能力 | 実行時証拠収集は競合の既存能力。Cursor専用Debug modeがなくても比較対象に含める [Agentic debugging](https://www.jetbrains.com/help/idea/agentic-debugging.html)、[MCP tools](https://www.jetbrains.com/help/idea/mcp-server.html#debugger-tools) |
| 本Plugin + Android Studio直接API | #150でSDK境界・比較fixtureを準備、runtime受入は未完 | module/variant/device/run configurationの実際の選択を結び付ける上積み候補は#150で検証する。IDEAの能力を対象Android Studioで確認済みにしない |

[設計候補] 正しいAndroid対象の選択・IDEの現在状態・会話に対応した証拠と残存差分を一緒に示す体験を比較する。breakpoint操作そのものを独自機能と呼ばない。#150 [固定比較記録](https://github.com/shinma06/cursor-in-android-studio/blob/d7b78d7c03b83c6f49f5e40c506368cb43ee556e/docs/research/issue-150-comparison.md) のGUI保留と担当を維持し、今回IDE/MCP/デバイスを起動しない。

## 再開条件と有限受入

採用Issueは今回新設しない。親#25へ次の条件を引き継ぎ、正式な製品採用はPMが判断する。

| ID | 再開条件/固定fixtureでの確認 | 現状 |
| --- | --- | --- |
| D1 | 専用Debug mode/config/commandまたは非TTY呼出を公式掲載/広告から特定。受理応答と通常Agentとの区別を記録 | 未成立 |
| D2 | その契約のもとで、専用空rootの小さな決定的バグ1件を使用。変更先はfixture source、ログ先は同root内の専用ファイルと宣言し、始終のdiff/内容hashを記録 | 未実施 |
| D3 | 仮説→計測→再現要求→人の返信→ログ→修正→再確認→削除のrequest/tool/session相関を確認。所有する計測だけ消え、修正が残る | 未実施 |
| D4 | Stop/timeout/異常終了、再現失敗、後続の利用者編集、未保存変更を分ける。残存計測とログ/serverを報告し、他の変更/資源を消さない | 未実施 |
| D5 | 同条件の固定Cursor IDE内panelとJetBrains+Cursor ACP+利用可能MCPを比較。Android対象は#150の統制fixtureへ接続 | GUI未実施 |

D1成立後も、未公開の受信port/endpointを推測してserverを作らない。専用fixture以外を要求されたらその時点の不足を記録する。最小実測は1バグ、最大3 prompt、各120秒、owned processだけ停止する上限から開始する（将来実測計画であり今回の結果ではない）。GUI/アプリ再現が必要なら既存lease担当へ具体手順を引き継ぐ。普通のAgentが同じバグを直せてもD1/D3のpassにはしない。

## 検証と完了境界

変更はこの文書と [GUI不要宣言](../verification/changes/issue-281.json) の2ファイル。新しい実行ロジック・テストfixture・製品設定なし。共通Change Impactの選択をそのまま使用する。既存Caseの状態を変更せず、資料のリンク、help hash、公開広告の表、schema、固定コード境界を照合する。

本Issueの有限調査完了と、未確認のDebug機能/計測cleanupの受入完了は別である。独立レビュー・最終CLI検証・writer停止の固定SHAはPRとIssueのhandoff記録を正本とする。
