# GUI操作と実対象ごとの調整

> developはテスト・独立レビュー・Case追跡で統合可能（GUI pending/blocked/failを保持）。mainは固定候補全体の必要Caseを既存gateで確認する。[確認マトリクス](../verification/README.md)が入口。過去MV/runを新候補のpassへ転記しない。

## 現行方針と適用範囲

本人の[GUI予約制廃止指示](https://github.com/shinma06/cursor-in-android-studio/issues/623#issuecomment-6656102943)を適用する。GUI予約・host全体の占有権・lease取得/renew/free・token/期限確認を開始/継続/証拠登録/cleanupの条件にしない。文書採用や旧予約の期限待ちも不要。host全体lock、global GO、必須ACK、予約server/daemon/schemaへ置換しない。

旧共有leaseファイルは、存在・古さ・破損・別ownerの表示を含め現在の許可/停止の根拠にしない。他repo/ownerが保持する旧記録を削除・変更したり、acquire/freeを偽装したりしない。固定Case・原FAIL・過去runは原文を保管し、そこにある旧GUI予約条件だけが現運用では失効する。Computer Use、実担当/出所、ロードbuild/fixture、観察、実権限や安全停止など他の保証は維持する。Gitの`--force-with-lease`や配布/Releaseの権限・直列化は別の契約であり変更しない。

## 観察と入力の経路

[共通の役割条件](github-workflow.md#正本と役割)で指定された操作者は、観察・入力とも **agent-desktopを第一選択**とする。利用版の`skills get desktop`、`skills get platform`と対象commandの`--help`で対応機能を確認する。clientが許す経路・実OS権限・対象surfaceを個別に確認し、導入やhelp取得だけで操作可能とはしない。[公式の操作手順](https://github.com/vercel-labs/agent-desktop/blob/d3dc190afbf34cd2377252398ddd5c30efbd093a/skills/agent-desktop/SKILL.md)

1. 実PID・profile・ロードbuild・fixtureで対象窓を特定し、新しいsnapshotのrefを使う。描画や配置のCaseでは実画像を観察し、部分AX treeを画面全体の証拠にしない。
2. 限定した入力後、対象窓と結果が現れる別窓/dialogを新しく観察し、期待する値と照合する。exit 0、配送済み、結果不明だけで成功にしない。結果不明の保存/送信を別経路から再送しない。
3. 未導入、対象選択不可、必要描画を取得できない、入力未対応など具体的な限界と実施範囲を記録し、その操作に合うPeekabooまたは利用中agentの純正Computer Useを選ぶ。fallbackの固定順位はない。未試行/接続不良を実拒否とせず、認証・実tool/OS拒否・本人停止を迂回しない。
4. Case固有の純正経路やComputer Useを維持する。使える経路がなければ対応可能な指定担当または許された人間へ必要操作だけを引き継ぎ、未達を保持する。Computer Use必須Caseを手操作でpassにしない。

## 対象・結果先・副作用の調整

操作前に、対象と**結果先**のPID/window、profile/設定、fixture、ロードbuild、許可されたwrite、実行中の処理と証拠への影響を確認する。focus・物理入力・clipboard・display、IDE起動/再起動・plugin、ADB/device/daemon、trace/録画や共通sourceへの実際の影響で判断する。headless、別worktree/窓、対象指定、終了コードだけで独立性を認定しない。

独立した対象限定の背景操作は、影響と結果先を確認して進める。既知の競合または具体的な影響が不明な操作だけを止め、関係する担当へ対象・副作用・安全な区切りを示して直接調整する。無関係なshellや他作業の存在をhost全体の停止条件にせず、全担当のACKやPMのglobal GOを待つ形にしない。送信は[許可された連絡経路](team-communication.md)を使う。

人間が実際に使う対象への競合操作は止め、本人の停止・引継ぎを優先する。CASとARBがこの共有hostの同じ対象/副作用で実際に競合するときは、既存の作業を保全して安全な区切りを合わせ、次の操作は準備済みARBを優先する。実行中の割込みや未準備の先取りはしない。独立操作や別hostへ一律の優先/待機を適用しない。CAS内は障害/復元安全性→依存解除→受付順を目安に、実負荷と依存から有限の割当を見直す。

## 依頼・証拠登録

既存IssueへGUI依頼を記録してPRへリンクする。`gui-queued`は担当・不足事実・次操作を示す進捗であり予約/占有ではない。実装・レビュー等の独立作業は継続する。

```text
GUI request: queued
Issue / PR / implementation owner / GUI operator session:
Target apps and result destinations / actual conflicting side effects:
Source HEAD / base SHA / artifact ID / ZIP SHA-256:
Case IDs / initial state / steps / expected result:
Fixture ID / allowed writes / CLI mode:
Run ID / budget (default 45min, 3 fixes, 8 sends to the product under test):
Dependencies / priority reason / next action:
```

実担当/実施経路を既存の割当・runの`operator`・観察の`observer`と実tool/session/turn証拠で照合する。coordinatorの既存GUI evidence登録は、当該Issue/PRで実際に指定された担当を`--operator`へ渡し、既存JSONの`observer`と一致させる。これはhost予約やorigin認証の代替ではない。登録者が実割当と原証拠の出所・内容を確認し、文字列一致だけを真実性や実行成功の証明にしない。新しいassignment/予約schemaやregistryは作らない。

```bash
python3 scripts/workflow/agent_loop.py gui --pr N --evidence /private/gui-evidence.json --operator designated-session
```

HEAD/base・artifact・実ロードidentity・run/観察者・Case別観察・`processes_stopped`を保持し、promotionは既存の候補resultsで受入を確認する。lease/tokenを要求・読取りしない。host/OS user・絶対パス・raw原本はprivateへ保管し、公開側には内容を確認した要約/共有可能な証拠を残す。`max_cursor_sends`等の既存キーは維持する。

## 停止・引継ぎ・cleanup

終了時は未送信入力・実行中agent・dialog・fixture差分・IDE PID/ロードbuildと結果先を確認する。自分が起動した処理だけ安全に停止するか、対象/現状態/未保存・未確定write/owner/次操作を明示して引き継ぐ。他タスクのIDE/agentの一括kill、無断のsource切替やbuild上書きはしない。結果の投稿が失敗した場合も安全に停止して原証拠を保持し、接続復旧後に投稿する。

安全STOP・復元/保全・custody・所有返却と製品の合否を分ける。実writer/process停止と実返却条件を満たした資源は、製品PASS・offline封印・OMIL応答を待たず返す。停止宣言だけ、結果不明writeや不明ownerでは返却済みにせず、実際に影響する資源だけを保持する。[既存の有限判断](omil-phase-review.md#stop-and-method-decisions)で方法前提と残責任を再考する。

cleanupは原source/workerの停止、HEAD/dirty/所有、worktreeの使用、対象refと必要保管を従来どおり照合する。GUI leaseの空きや期限は読まず、対象の実利用・未知write・他ownerの資源は保全する。旧lease理由で中断した処理も自動resume/owner交換/PAUSED解除をせず、元ownerまたは明示引継ぎ先が残る実際の停止理由を照合して通常経路で再開する。

<a id="検証目的と継続"></a>

## build・受入との関係

Caseはsource HEAD、artifact hash、ロード実体、fixture、run、観察者と実観察へ結び付ける。画面取得不可・実権限/認証不足等で必要事実を満たせなければ該当範囲をblockedとし、CLI/レビュー/mergeだけでGUI passにしない。別PRのmain採用を起動中IDEの更新と解釈せず、変更・未知の影響に応じ必要なCaseを見直す。

[製品責任](../verification/responsibility.md)から保証→適用できる既存証拠→不足事実→最小の手段を選ぶ。新しいZIPや文書だけを理由に全native操作を繰り返さない。install/通常再起動は実IDE/profile/所有、退避・復元、固定ZIPの全JARと実ロードを照合する。公式local ZIP配置で足りる場合にPlugins画面の探索を一律の試験にせず、有効Caseの実施経路と自製品の実接続を維持する。ARB固有条件を本製品へコピーしない。[loop手順](../loop-engineering/README.md)・[証拠形式](../loop-engineering/evidence.md)を併用する。
