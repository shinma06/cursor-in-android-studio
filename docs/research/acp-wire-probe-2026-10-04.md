# ACP初期接続・設定・取消の限定実測 — #146

2026-10-04、CLI `2026.10.01-14929f9`、macOS 27.0.1 / arm64。基準developは `795ec3d01b9bab811ee82e1cfb6f098aaa692a33`。親は[#141](https://github.com/shinma06/cursor-in-android-studio/issues/141)、契約分類は[#115](https://github.com/shinma06/cursor-in-android-studio/issues/115)。製品runtimeは変更していない。

**既存認証での新規session、正式configの設定と応答照合、連続2turn、ファイルの部分更新、permissionの1回許可と取消を観測した。取消のprompt応答直後にも別PIDのshellを観測したため、prompt終端だけでは実処理の静止を証明できない。** 質問・Plan・usageは今回の記録では未観測。GUI・製品受入・main反映の成功を示す資料ではない。

## 証拠と実行条件

本人承認の上限8回に対し、新規session 8個・固定prompt 7回を実行した。2回はmetadataのみ、連続2turnは1 sessionに限定した。追加試行、新login/logout/authenticate、既存会話のlist/load、実開発の委譲、GUI操作は行っていない。

各試験は新しい空Git workspaceを使用。許可対象は`input.txt`、有限command試験だけ`tick.py`と`ticks.txt`を追加した。終了後のファイル一覧もこの範囲と一致した。scriptは40回、0.2秒間隔で1行ずつ書く。各promptは採取時の[probe](../../scripts/probes/acp_wire_probe.py)に固定し、MCP/Web/他ファイル/子Agentを依頼しない。permission callbackで許可できるのは対象sessionの既知ファイル、または変更されていない有限scriptを起動する完全一致commandだけである。

Clientはfs read/writeとterminalをfalseで広告し、newには`mcpServers: []`を渡した。これはprovider自身の操作を禁止する仕組みではない。ユーザーMCP設定ファイルは存在せず、MCP toolイベントも未観測だが、外部サービスの静止保証とはしない。既存のユーザーhooksは変更せず維持した。`--trust`、`--force`、`--auto-review`、`--approve-mcps`は使用していない。API-key/認証token/endpointの環境変数上書きを渡さず、既存loginを利用した。

共有設定の手動変更はしていない。ただし、run 01の実行中にCLI設定ファイルのhash変化を検出した。この回は項目別の差を採っていないため変更箇所は不明。run 07は`privacyCache`だけの変化を確認し、他の6回では監視した設定・hooks・MCPファイルのhashは不変だった。「起動だけなら共有ファイルへの書込みがない」とは主張しない。

[manifest](fixtures/acp-146/manifest.json)がCLI版、probe/exporterのhash、各JSONLのhash、開始時刻、元/公開イベント数を結び付ける。JSONLは`seconds`、`direction`、`frame`を持つ。`seconds`はprobe起動後の観測時刻、`probe`行はfilesystem/processの観察でありACP frameではない。

2026-09-09の旧資料4ファイルは保全したが、当時の実測fixture 9件は元の一時保存先と保全アーカイブに存在しなかった。本資料は**新しい実測**であり、旧記録の復元、旧hashとの一致、旧CLIでの再現とは扱わない。

## 公開時の加工と限界

[exporter](../../scripts/probes/export_acp_wire.py)で次を加工し、残る文字列を確認した。

- client initialize/newを全省略し、initialize応答はprotocolVersionだけを残す。認証案内・client/agent/MCP capability metadata・cwdを含めない。初期能力の観測記述は非公開元記録に基づき、公開excerptだけから全setupを再現できるとはしない。
- command一覧通知、旧形式model catalogを省略。stable model optionは実際のcurrent valueだけを残し、全model一覧ではないことを明記する。
- session/tool ID・PIDを一貫して置換し、workspaceは`/fixture`にする。thought本文は伏せる。今回のlive tool IDには改行を観測していない。改行付きopaque IDの保持はsynthetic testで検証する。
- frame境界、本文delta、RPC数値ID（特に0）、permission outcome、prompt応答、時刻、別々のprocess snapshotを保持する。

公開物は匿名化したliveの**部分記録**。認証情報、アカウント情報、個人のcommand一覧、絶対path、stderrは含めない。元データは非公開で保持し、公開JSONLから欠けたframeを生成・補完しない。exporterはこの限定capture用であり、任意の既存会話を自動で安全に公開できるツールではない。

## Caseと結果

#115のT01/02/03/04/05/07/14に対応する有限のCLI調査。未観測・未試験を成功へ置き換えない。

| Case / 対応 | 手順・結果 | 証拠 / 残条件 |
| --- | --- | --- |
| INIT / T01・14 | 追加引数なし、既存認証でinitialize→new成功。protocolVersion=1。認証操作なし | [01](fixtures/acp-146/01-metadata.jsonl)。失効・別アカウント・認証UIは未試験 |
| ROOT-OPTIONS / T14 | `--mode ask --sandbox enabled acp`でもnewのmodeはagent | [02](fixtures/acp-146/02-root-options.jsonl)。flag受理を同義設定やsandboxの実効性としない |
| CONFIG / T05・14 | 広告されたmodeのaskとmodelのcurrent valueをsetし、応答のcurrentValueを照合 | [03](fixtures/acp-146/03-text.jsonl)。modelは`composer-2.5[fast=true]`。別modelへの変更成功やAuto選択の証拠ではない |
| TEXT / T02 | 同sessionの2turnで各々`amber amber cedar`を受信。deltaは`amber`＋` amber cedar` | [03](fixtures/acp-146/03-text.jsonl)。重複語を削らない。他model/長文/並列会話は未試験 |
| EDIT / T03・04・14 | read/editの部分更新とdiff、実ファイルの変更を確認。標準permission要求は到着しなかった | [04](fixtures/acp-146/04-edit.jsonl)。全編集の事前承認を保証しない。Revertの来歴やGUIは別受入 |
| EXTENSIONS / T04 | 選択式質問を1回、その後に実行しないPlanを依頼。モデルは利用不可と説明し終了。該当拡張frame・file操作なし | [05](fixtures/acp-146/05-extensions.jsonl)。質問/Plan/Todo/task/imageの一般的な非対応とは断定しない |
| CANCEL-TEXT / T07 | prompt開始3秒後にidなしcancel通知。元promptへcancelled応答。接続processはまだ存在 | [06](fixtures/acp-146/06-cancel-text.jsonl)。本文streaming前の取消。全競合や取消後の次turnは未試験 |
| PERMISSION-ALLOW / T04 | rawInputを含まないpermission id=0を、先行tool stateと結合。既知commandだけallow-once | [07](fixtures/acp-146/07-cancel-child.jsonl)。allow-alwaysとreject-onceのlive返信は未試験 |
| CANCEL-CHILD / T07 | ticks=2でcancel。応答直後は別PIDのzsh、1秒後はagentのみ、ticksは2のまま。EOF後の観測familyは空 | [07](fixtures/acp-146/07-cancel-child.jsonl)。離脱した子孫・外部仕事の終了保証ではない |
| CANCEL-PERMISSION / T04・07 | permissionを1秒保留しcancelとcancelled返信。ticks=0、prompt cancelled、応答直後はagent/zsh、EOF後family空 | [08](fixtures/acp-146/08-cancel-permission.jsonl)。待機中のsnapshotはなく、応答後shellの生成時点・用途は不明 |
| SYNTHETIC / T01・04・07 | frame分割/結合、双方向ID、deadline、EOF、session/command/script境界、id=0、公開時の削除・不明field拒否 | [tests](../../scripts/workflow/test_acp_wire_probe.py)。実Cursorの異常EOF/無応答の成功証明ではない |

8回ともCLIはstdin EOF後にexit0となり、最後の観測familyは空だった。prompt終端と物理終了の時点は別記録である。実測でreject-once、allow-always、異なるmodel、Plan動作、画像、MCP接続、履歴互換を確認したとは扱わない。

## 製品へ渡す契約

### 設定と本文

initializeはloadSession、session list、image入力、MCP http/sseを広告し、audio/embeddedContextはfalseだった。これは広告の観測で、画像理解・旧履歴互換・MCP実接続の成功ではない。newはmodes、legacy models、stable configOptionsを併せて返した。正式に広告されたid/valueを送り、返答のcurrentValueを照合する。CLI flagや旧保存値からmodel alias・context容量を推測しない。

Askの正式set/readback後にはtext 2turnが成立したが、rootのask/sandbox引数だけではnewのmodeがagentだった。明示sandbox・AUTO_REVIEW・RUN_EVERYTHING・ISOLATED worktreeの同義利用は未検証で、設定を黙って通常ACPへ落とす根拠にしない。Planは広告のみ。使用したmodelは既存設定から広告されたcurrent valueの再設定である。

本文は正しい反復を含むdeltaだった。printの全体置換推定を混ぜない。read/editは、最初にkind/status/空rawInput、次にpath/locationsだけ、続いてstatusだけ、最後にrawOutputまたはdiffを返した。省略fieldで先行stateを消さない。新規会話の`session_info_update.title`も6件観測したが、[#499](https://github.com/shinma06/cursor-in-android-studio/issues/499)のタブGUI受入や[#66](https://github.com/shinma06/cursor-in-android-studio/issues/66)の旧履歴・保存・改名を完了にはしない。

### permission・取消・復元

permissionのRPC IDは0。truthy判定でnotificationとして捨てず、idの存在でrequestを識別する。toolCall.rawInputはなく、先行tool updateとの結合が必要だった。今回のoptionIdは`allow-once`、`allow-always`、`reject-once`。製品ではkindと広告されたoptionIdを対応させ、不完全な情報から推測で許可しない。probeの有限command自動返信を製品の一般自動承認へ流用しない。

cancel-childの主な時点（PIDは公開fixture内の置換値）:

| probe開始後 | 観測 |
| --- | --- |
| 8.851s | ticks=2、agent(1000)→zsh(1002)→Python(1003) |
| 8.852s | idなし`session/cancel`を送信 |
| 8.855s | 元promptが`stopReason: cancelled`を返す |
| 8.883s | ticks=2、agent(1000)→**別のzsh(1004)**。Pythonはsnapshotにない |
| 9.935s | ticks=2のまま、agentだけを観測 |
| 10.956s | stdin EOF・exit0の後、観測familyは空 |

1秒を安全な待機定数にしない。snapshot間に生成・終了・親から離脱した処理や外部IDE/MCP仕事までは証明していない。製品の不確定時の復元拒否、自動再送禁止、所有run/root/世代の境界を維持する。安全な意図的終了とsession/load継続は[#465](https://github.com/shinma06/cursor-in-android-studio/issues/465)に残り、本研究の2turnやEOF成功で開始条件を緩和しない。

## 残条件と検証

- 質問/Plan/Todo/task/image通知、usage更新は今回未観測。モデルの説明を能力の不存在へ一般化しない。[#149](https://github.com/shinma06/cursor-in-android-studio/issues/149)のusage対応に実frameが得られたとは扱わない。
- 異常EOF、無応答、別tabとの同時復元、離脱した子孫の回収、別model、明示sandbox、force/auto-review、分離worktreeは実測していない。synthetic testや今回の観測で代替しない。
- [Case JSON](../verification/changes/issue-146.json)はGUI不要の調査として`cases: []`、CLI Caseの正本は本表とmanifest。製品の固定build・GUIは[QA #152](https://github.com/shinma06/cursor-in-android-studio/issues/152)で追い、今回のdevelop統合とmain反映を分ける。

通常検証はCursor/networkを起動しない。既存のworkflow test discoveryにprobe/exporterの回帰試験を含める。Change Impactのunknown分類と必要な全検証を維持し、分類器やCIを変更して検査を省略しない。

```bash
python3 -m unittest scripts.workflow.test_acp_wire_probe -v
python3 scripts/workflow/change_impact.py --run-tests
git diff --check
```

再採取は別途範囲と上限を定め、既存認証で失敗すれば認証を変更せず停止する。公開前の元データはrepository外の非公開領域へ保存する。

```bash
python3 scripts/probes/acp_wire_probe.py --agent /path/to/agent \
  --output-parent /path/to/private-evidence --scenario metadata
python3 scripts/probes/export_acp_wire.py /path/to/private-evidence/acp146-capture \
  /path/to/private-evidence/review-candidate.jsonl
```

通常採取は製品やCIから自動実行しない。exportは元データを変更せず、未確認のfieldや残存識別情報を検出した場合は停止する。公開候補の目視確認とmanifest/hash照合を行い、PRの固定HEAD/base独立レビューを経て統合する。

仕様との区別に使う一次情報: [Cursor ACP](https://cursor.com/docs/cli/acp)、[設定](https://cursor.com/docs/cli/reference/configuration)、[権限](https://cursor.com/docs/cli/reference/permissions)。仕様の存在と、この版・条件での実測・製品実装・GUI受入は別の判断である。
