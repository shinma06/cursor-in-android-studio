# #118 SubagentのACP・print契約と進捗表示

2026-09-12 / コード調査base `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。
統合・独立レビューの固定HEAD/baseはPRへ記録する。

**採用候補は、既存tool行に確認済みの子task情報・成否・取得できた結果を結合する有限の表示機能。**
ACP1回/print2回で、前景子の完了とprint子再開時のエラーを採取した。
ACPの`cursor/task`は今回ID付きrequestで届き、公式のnotification説明と異なる。
再開成功、背景実行、親Stop時の子終了、完全な子transcriptは未確認。
製品UI/GUIや全Subagent対応が完了したという結論ではない。

## 公開契約と観測の区別

一次情報は2026-09-12に確認した。

| 公開根拠 | 契約と本調査での扱い |
| --- | --- |
| [Cursor Subagents](https://cursor.com/docs/subagents) | editor/CLIで独立contextの子を呼び出せる。foreground/background、readonly定義、agent IDによる再開が公開される。親の複数tabやCloudの別VMとは分ける |
| [CLI changelog](https://cursor.com/docs/cli/changelog) | headless/single-turnが委譲子の完了を待つ、完了子のcontext保存・背景完了本文の記載。通常完了の説明から強制終了後の子静止を保証しない |
| [ACP tool calls](https://agentclientprotocol.com/protocol/v1/tool-calls) | session内toolCallId、pending/in_progress/completed/failedと部分更新。親子階層・resume API・token配賦をこの標準だけから生成しない |
| [Cursor ACP](https://cursor.com/docs/cli/acp) | cursor/taskは完了通知/返信不要という説明。一方Response型、agentId再開、durationMsの説明も併記される。今回のID付きrequestとcustom型差を含め、実wireと版を優先して境界を記録する |

公開custom型は`{custom:string}`だが、今回task requestは`{custom:{custom:{name:string}}}`、
標準tool rawInputとprint argsでは`{custom:{name:string}}`だった。非互換形状を一般仕様へ昇格しない。
通知説明を根拠にID付きrequestを無視したり、Response型を根拠にClientが子を生成したりしない。

## 実測結果

合成定義・prompt・公開投影・原本照合は[実測記録](issue-118-fixture.md)。
CLI `2026.09.10-fd3934a`、ask、既存認証、合成rootのみ。
ACPはmodel config初期値、printはinit Auto、task modelはdefault。実際の内部model名は推定しない。

| 対象 | 確認できたこと | 確認できていないこと |
| --- | --- | --- |
| ACP前景 | 1つのtoolCallIdでpending→in_progress→completed。その直後のcursor/taskも同ID、任意agentId/durationMsあり | 子の内部read進捗・本文stream・通知なし経路・全順序 |
| ACP request | JSON-RPC2.0の非null整数id付き。採取clientが未対応requestへ-32601を返した後も親end_turn/marker末尾を得た | 正規の成功応答・応答しない場合の待機・全版でのrequest保証 |
| ACP結果 | rawOutputはdurationMs整数/isBackground=false。親本文には子定義のmarkerが反映された | tool/task自身に子の結果本文はなく、親説明から子全文を復元できない |
| print前景 | taskToolCall started/completed、同一call_idと親session_id。successにconversationSteps/agentId/isBackground=false/durationMs文字列 | 任意の段階進捗・ツール・完全transcript、background完了経路 |
| print再開試行 | success.agentIdを指定し、次のargs.agentId/resumeへ一致。親sessionも同一 | 再開成功とcontext保持。子はprovider Request blockedで失敗 |
| 親子の成否 | 子result.errorがあっても、親は文章で報告しresult.success/is_error=false/exit0 | 親exit0だけで子成功・全子成功と判定すること |

print初回の**args.agentIdとsuccess.agentIdは別値**。前者を保存済みresume IDへ流用しない。
top-level call_idとtool_call.toolCallIdも別値。transport内の対応キーを維持し、
ACP toolCallId・print call_id・親sessionId・返された子agentIdを1つのIDへ統合しない。
再開拒否を回避する言換え・別model・追加実行は行わず、今回の3実行で採取を止めた。

## 現行製品の接続境界

- [AcpSession.notification](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt)はsession/update以外を捨てる。
  ID付きcursor/taskはrequest側へ来ても[AcpProtocol.input](../../src/main/kotlin/com/cursoragent/acp/AcpProtocol.kt)に型がなく拒否される。
  現行の安全な未知request処理を、観測だけから成功応答へ変更しない。
- AcpProtocolはtoolの部分更新をID別に統合するが、rawInputのcommand/pathと標準表示を扱うだけ。
  task rawOutput/子agentId/duration/isBackgroundは[AgentTool](../../src/main/kotlin/com/cursoragent/service/AgentEvents.kt)へ渡らない。
- AcpSessionはturn後のtool更新をuncertain/disconnectとする。新しい背景子の遅着を扱う場合は、
  current.terminal・process静止確認・connection/session/turn寿命を先に照合する。
  この調査を理由に静止確認や復元禁止の保護を緩めない。
- [ToolCallPayloadParser](../../src/main/kotlin/com/cursoragent/parser/ToolCallPayloadParser.kt)はread/edit/shell以外を汎用summaryへ落とす。
  今回のtask success/error・子ID・conversationStepsは専用表示へ届かない。
  [AgentTurnListenerFactory](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt)はcompleted subtypeを「完了」と記録するため、
  今回の子errorも外側completedだけでは成功/失敗を区別できない。
- [ChatTimelinePanel](../../src/main/kotlin/com/cursoragent/ui/timeline/ChatTimelinePanel.kt)のactiveToolCallRows/structuredToolsと置換操作を流用する。
  同一子に標準tool行・task行・完了行を3重追加しない。詳細の折畳み/通知抑制は#98の共通設計に合わせる。
- #44の保存/restore静止表示へraw payload、思考、認証、巨大な子全文を無制限に保存しない。
  #254のprint本文正規化と子本文の所属を混ぜない。全callerと復元経路を実装担当が照合する。

## 表示の採用範囲

| 場面 | 必要な表示・操作 |
| --- | --- |
| 開始/進捗 | 名前/description・観測状態・親turnとの対応を表示。子内部進捗がない間は「子の詳細な進捗は未取得」。pendingだけで承認待ちと断定しない |
| 完了/結果 | 同一tool行を更新し、提供された結果だけを展開/折り畳む。親本文と子結果を別の所属に保つ。展開後は同じ親timelineの位置へ戻れる |
| 結果なし | 「子の結果本文は取得できません」。ACP親本文から子全文を作らない。不存在/失敗とも決めつけない |
| 失敗 | print result.errorまたは標準failedを失敗表示。親successでも上書きしない。理由は日本語補助と実エラー詳細、通知は親単位へ集約 |
| Stop/切断 | 親Stopの受付と子の停止確認を分ける。子終了の根拠がなければ「終了を確認できません」。completedへの補完や自動再送をしない |
| モデル | 受信値default等をそのまま扱い、親から実モデルを推定しない。値がなければ未取得 |
| 時間 | provider durationMsとUIで測った経過時間を別項目とし、数値/文字列・欠損・負値/上限を境界で検証。完了時のdurationから開始時刻を捏造しない |
| token | task通知に子usageはなかった。親totalへ子推定tokenを加算しない。配賦不明は未取得、0 tokenと表示しない |
| ID/再開/リンク | 返されたagentIdをopaque値として保持可能だが、子専用resumeボタン・外部deep link・全文取得は未採用。公式形式/実行成功が未確認のURLを生成しない |
| 前景/背景 | 明示isBackgroundがあるときだけ前景/背景を表示。欠損やenum名から決めない。背景を別tabへ自動変換しない |

ACP標準toolを主経路にし、cursor/taskの確認済みmetadataは同一connection/session/toolへ限定して結合する案。
request/notificationを分け、ID付きrequestの応答は未対応時の明示errorを保持するか、
正規応答を別の有限契約で確認してから実装する。Clientの子spawnや架空outcomeを追加しない。
未知custom型/遅着/重複は防御的に扱い、既存の汎用tool表示を使えるままにする。

## 競合とIDE統合

[Cursor Subagents](https://cursor.com/docs/subagents)のIDE/editor内委譲と親への結果返却を対象にする。
Cloud/Agents Window専用UIは追加しない。[CursorのJetBrains連携](https://cursor.com/docs/integrations/jetbrains)、
[AI AssistantのACP設定](https://www.jetbrains.com/help/ai-assistant/acp.html)と
[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)には既存のAgent/IDE/MCP連携能力がある。
子実行・ファイル読取・terminal/MCPを独自機能と呼ばない。

今回両製品のGUIは未操作であり、子カード/取消/展開のUX同等以上は未確認。
本Pluginで加える対象は、既存tab・親turn・draft・IDEの停止/復元状態に矛盾しない表示と、
同一taskの二重通知/誤成功表示を防ぐこと。競合ができないとの主張ではない。
IDEへのファイルリンクは検証済みlocationが返った場合に既存APIを使い、モデルの文字列を任意パスとして開かない。

## 引継ぎ・有限Case

採用する専用機能IssueはPMが確定する。最初は確認済みmetadata/結果/子errorの表示と標準tool結合、
#98共通折畳み・通知・状態を使う。一般Subagent管理、子spawn、再開ボタン、全文取得、背景監視基盤は作らない。
#98全体を#118完了待ちにしない。製品共有writerは#44/#254の変更と順番を調整する。

本調査のissue-118.jsonはGUI不要/cases空。以下は製品固定buildの**GPT pending / human pending**案:

1. 固定HEAD/ZIP hash/loaded JAR/CLI版/transportを記録し、指定GUI lease担当が合成子を実行。
   1つの子行が開始→進行→結果へ置換され、展開/折畳み/親へ戻る/通知集約が一貫すること。
2. 制御serverで標準toolとtaskの順序逆転・重複・欠損ID・未知custom型・不正duration・別sessionを再生。
   二重表示/誤配送/架空tokenやmodelがなく、requestとnotificationの応答が区別されること。
3. 子error/結果なし/親successを個別に再生し、失敗が成功へ変わらないこと。
   今回のprovider拒否はlive失敗fixtureとして保持し、将来の拒否を意図的に発生させ続けない。
4. Stop前後/close/別tab/復元後に遅着を再生。終了未確認と静止確認、再送禁止を保ち、保存から子を再実行しない。
5. 背景と親Stopのliveは、合成子・明示背景条件・終了根拠/残プロセス観測・時間上限を固定した別Case。
   子再開成功は環境/提供状態の変化後に今回の制御IDとcontext照合で確認し、要求送信だけをpassにしない。

## 受入対応と残条件

| #118受入 | 確認と引継ぎ |
| --- | --- |
| 親子ID/開始進捗終了/前景背景/再開/エラー | 前景ACP/print成功、ID相違、再開要求一致と子errorを実測。背景/再開成功/親Stopは未観測と有限Case5へ |
| printとACPの区別 | 公開投影とnotification/request差、custom/duration型差を記録 |
| 子行/展開/状態/親Stop | 表示表とCase1〜4。製品実装/GUI passではない |
| token/時間/全文 | 未取得と0の区別・非加算・提供結果のみ・未知link非生成 |
| #98境界と実装分離 | 上記専用Issueへ有限採用、共有部品と未観測Caseを引継ぎ |

追加の未観測: 全モデル/版、全subagent type・入れ子、多数子、background実行、Client成功応答、
切断/失敗全種、子のusage内訳・完全transcript・再開成功。未観測は能力不存在とは判定しない。
親#25の固定scopeに従い、未確認理由と次の証拠を明示して製品受入から分離する。
