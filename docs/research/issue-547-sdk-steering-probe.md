# SDK live steeringのoffline準備（#547）

P04のSDK採用を判断する担当が読む有限の検証資料。**実provider検証はblocked、P04は未実装。** 製品のPRINT/ACP、queue/Send Now、保存JSON v1、Gradle依存を変更しない。元の[#212接続調査](issue-212-agent-shortcuts.md#残る操作の接続契約2026-10-05確認)と[#278の固定調査](issue-278-midturn-contract.md)を保持する。

## 固定した入力と観測の区分

2026-10-06、base develop `26c525f6a23280a113eb6124de50dc8cc7866f61`。公式npm registryから`@cursor/sdk@1.0.31`を独立した私的検証領域へ取得した。install scripts/auditを無効にし、専用cacheと空のnpm設定を使った。Node `v26.10.0`、npm `11.19.1`、darwin/arm64。製品・共有設定へ依存を追加していない。

| 固定情報 | 確認値 |
| --- | --- |
| 配布元 | [公式registry metadata](https://registry.npmjs.org/@cursor%2fsdk/1.0.31)、[SDK archive](https://registry.npmjs.org/@cursor/sdk/-/sdk-1.0.31.tgz) |
| archive整合性 | `sha512-0SdJQqp5oXn81oJqIVkLpgHih+CL6CAudK83pCsdJyA23AvInSWlMaGpLi+JlrK3efHoswiEhhASs9WpeEz3QQ==`。取得bytesのSHA-512とmetadata/lockfileを直接照合 |
| archive SHA-256 / size | `6316337f3bf154406d704f00c939031ebaa388031ebfcabde843f74452c02a03` / 4,861,419 bytes |
| Node要件 | package engine `>=22.13`。offline合成checkはSDKをimportしないため、Nodeの標準機能だけを使う |
| platform package | `@cursor/sdk-darwin-arm64@1.0.31`取得。helper起動/sandbox作動は未観察。他OSを検証済みとしない |
| 推移依存 | private package-lockに15 packageを固定（他platform optional entryを含む）。lock SHA-256 `fadd3b3c16c57b91e9435e8134a6dd666550c1fb624bc7868c0e42ec2536ab6f`。新環境の再解決結果を同じsnapshotとしない |

取得先・lockfile・型のhash・生ログ・実設定の原証拠はIssue専用の私的記録に残す。公開物へ個人path/session ID/credentialを含めない。依存取得・型読取・合成実行は、SDK agent起動や課金prompt実行の証拠ではない。

## 公開契約と固定型の照合

[公式SDK](https://cursor.com/docs/sdk/typescript#steering-a-run-in-flight)はlocal live handleのoptional `run.steer`と2つの最終ackを公開する。配送済みだけが入力所有の移転。不受理と結果不明を分ける。SDKのpublic `run.d.ts`では中間確認を最終結果として公開せず待ち続ける説明がある。実際の配送・model消費は未観察。

public `options.d.ts`では`tools: []`がMCP/subagentを含むtoolを除外し、resume時に再指定が必要。localのtransport/stall retryは既定有効なのでprobeは明示無効にする。明示store、設定sourceなし、inline MCP/custom toolなし、sandbox有効を候補設定とした。[公式SDK説明](https://cursor.com/docs/sdk/typescript)、[配布version](https://www.npmjs.com/package/@cursor/sdk/v/1.0.31)。型/設定値の照合は強制動作の実測ではない。

ACP標準→Cursor拡張の不足は#212/#278の調査を再利用する。TypeScript候補をJVMの[SDK Bridge](https://cursor.com/docs/sdk/bridge)や既存ACPへ暗黙移植しない。Cursor native IDEの[同一turn入力](https://cursor.com/docs/agent/overview#queued-messages)と、JetBrainsの[中断するSend Now](https://www.jetbrains.com/help/ai-assistant/ai-keyboard-shortcuts.html)を分ける。JetBrains AI Assistant + Cursor ACP + IDE MCP/tools構成のGUI比較は未実施。直接Android Studio統合の追加価値もまだ実測していない。

## 最小probeと実行手順

[sdk_steering_probe.py](../../scripts/workflow/sdk_steering_probe.py)はPython標準ライブラリの入口とNode標準機能のmoduleを1ファイルに保持する。既存workflow test経路で実行し、製品buildや新しいCI gateへ接続しない。SDK内部agent loopや汎用adapterは作らない。許可後の実行用driverも同じmoduleに保持し、offlineではFake SDKだけを渡す。

```bash
# 合成Runだけを使う。SDK import、認証探索、network、実providerは呼ばない
python3 scripts/workflow/sdk_steering_probe.py
python3 -m unittest scripts.workflow.test_sdk_steering_probe -v

# 既に私的領域へ取得したpackageのmetadata・public型・hashだけを読む
python3 scripts/workflow/sdk_steering_probe.py --inspect-sdk "$SDK_PACKAGE_DIR"

# 将来の許可済み検証用に同じmoduleを新しい私的ファイルへ出力するだけ
python3 scripts/workflow/sdk_steering_probe.py --export-node "$PRIVATE_PROBE_MODULE"
```

変数は担当が非公開で指定する。export先の既存ファイルを上書きしない。export自体は実行しない。moduleの通常importもSDKをimportしない。`--check-config`は明示ファイルの条件・private権限・固定SDK entry hash・ledgerを読むだけで、credential内容を読まない。`--execute-approved`だけが明示credentialを読み、固定SDKをdynamic importし、選択した1 Caseを実行する。driverコードの準備は今回の範囲、実SDK接続・driver実行は未許可である。

配送側は所有run handle参照・agent/run ID・owner・local送信ID・draft revisionを照合し、1件ずつ受け付ける。ack不明は本文を保全して追加受付/再送を止める。遅いackは旧ownerに記録し、新draftやclose後のdraftを消さない。不受理でも自動follow-upを送らない。保存snapshotのpendingは再読込でunknown、再開状態は受付停止のままにする。これはprobeの保守的な境界であり、製品への採用決定ではない。

| Case | offlineで確認した境界 | 実provider Agent状態 / 人間状態 |
| --- | --- | --- |
| S1 | Fake Agentへのtext-only設定/初回send、合成配送ackで同じsnapshotだけ確定、同じIDの再送拒否 | blocked / pending。同一runへの実配送・model消費は未観察 |
| S2 | 不受理、method不在、終端済みの区別と本文保持 | blocked / pending。detached実handleは未観察、Cloudは除外 |
| S3 | timeout/例外/未知ackはunknown、再送拒否、遅いackで新draftを消さない | blocked / pending。provider切断・実際の最終履歴は未観察 |
| S4 | pending中のStop/closeと遅いack、取消失敗、追加受付停止 | blocked / pending。SDK取消・stream/tool/物理終了は未観察 |
| S5 | JSON roundtripでpending/unknown/配送済みを保持、再開からの自動入力禁止 | blocked / pending。SDK store/checkpoint/resumeは未観察。製品JSONとは別 |
| S6 | 別owner/run/agent/handle、同じID、同時入力を拒否。同文別ownerを誤dedupeしない | blocked / pending。実SDKのID帰属/重複は未観察 |

合成成功はこの処理のassertのみ。SDKの本体を起動していないため、`sdk_agents=0 / provider_calls=0`。実provider Caseをpassに変更しない。[Case JSON](../verification/changes/issue-547.json)ではGUI不要の既存schemaに従い`cases: []`、CLI checksからこの表を参照する。GUI Caseを偽造せず、既存#381/#531の状態を変更しない。

## 許可後の具体的な入口（現在は実行しない）

担当が空のprivate rootと、root外のprivate config/既存credentialファイルを用意する。次の雛形は承認なしで拒否する値であり、認証を作成する手順ではない。model/params、SDK entryのSHA-256、path、承認記録を担当が固定し、root/config/credentialは本人所有・symlinkなし・他者権限なしにする。SDK archive/lockの固定証拠も照合する。

```json
{
  "schema": 1,
  "case": "S1",
  "approval": "",
  "approved": false,
  "acceptNoHardCostCap": false,
  "model": {"id": "", "params": []},
  "root": "<absolute fresh private root>",
  "sdkPackage": "<absolute pinned SDK package directory>",
  "sdkEntrySha256": "<SHA-256 of reviewed dist/esm/index.js>",
  "apiKeyFile": "<absolute existing private credential file>",
  "maxRuns": 6,
  "runMs": 60000,
  "steerMs": 10000,
  "budgetUsd": 1.0,
  "warningUsd": 0.5
}
```

```bash
# 準備確認のみ。credential内容・SDK import・agent/providerは扱わない
node "$PRIVATE_PROBE_MODULE" --check-config "$PRIVATE_APPROVED_CONFIG"

# 以下は独立レビューと具体的な課金実行許可が得られた後だけ使う入口
# Node起動前のNODE_OPTIONS/NODE_PATHも空にする。今回このコマンドは実行しない
NODE_OPTIONS='' NODE_PATH='' node "$PRIVATE_PROBE_MODULE" --execute-approved "$PRIVATE_APPROVED_CONFIG"
```

承認flagは人間/PMの許可証拠の参照であり、flagをtrueにするだけで承認は得られない。driverはSDK/version/entry hashとmodel/limitsのbindingを保持する。既存lockは自動削除しない。crash/unknown/保存失敗の痕跡が残れば担当へ返し、自動再実行しない。

| 選択Case | driverが記録する操作（未実行） |
| --- | --- |
| S1 | toolsなし新規agent/runに1件steer。run/agent/送信ID、draft revision、時系列、stream、wait結果、conversation、usageをprivateへ記録。終端handleへの追加結果はS2の証拠として保存する。配送ackだけでmodel消費を確定しない。 |
| S2 | S1の同一store/runをdetached getRunで読む。method不在/結果/例外を記録し、S1の終端handle結果を参照。fallback sendなし。S1がなければ開始しない。 |
| S3 | 新規runのsteer待機を1msで合成切断し、直後snapshotと後続draft・遅着状態・最終履歴を別記録。SDK ackが先に返ればtimeout未観察として判断し、無駄な再試行をしない。 |
| S4 | 新規runでsteer pendingを保存し、受付close/cancelと競合させる。最終row/取消/stream/wait/disposeを別記録する。toolは有効にしない。 |
| S5 | S1のagentを同じstoreでresume、tools/settings制限を再指定。detached runとmessages、受付停止したlocal snapshotを読むだけでsend/steerをしない。 |
| S6 | 新規owner/runとS1の保存済みowner/runで、foreign handle/agent/runへの入力をdispatch前に拒否し、own handleへ1件だけsteer。2つの実runの記録を照合する。 |

1起動で1 Caseだけ。Caseとrun数は処理前に予約し、各実行の`observed`は観測取得の意味であってCase passではない。実行後は必ず停止し、次のCaseに自動進行しない。`ledger.json`の`costReviewed`はfalseのまま保存する。独立担当が請求の確定証拠を確認して`chargedUsd`（USD）と`costEvidence`（private証拠参照）、`costReviewed: true`を記録した時だけ次の起動を検討する。usage UUIDとclient run IDを混同せず、欠落/遅延/取消後料金を0と推測しない。累積US$0.50以上でも保守的に停止する。unknown Case、重複、未確定費用、上限到達は拒否する。ledgerを手修正してunknown/retry境界を解除しない。

合成checkは同じdriverにFake SDKだけを渡し、6入口（新規send4件、resume1件）、許可未指定・上限・費用未確認の拒否、private保存と元記録保持を検証した。実SDK import/credential内容の読取/agent/providerは0。新規SDK storeの実動作、取消や終了の強制性、model消費は全て未観察のまま。

## 実provider検証の許可・上限案

**現在は未許可でblocked。** 専用の既存API key（user/service account）、固定model/parameters、Node/platform helper、空のfixture rootと専用SDK store、network/保存先をPMと独立reviewerが照合する。新login・API key作成・共有設定変更を勝手に行わない。SDK runtimeを起動する前に、snapshot全体へ実project・既存MCP・plugin/subagent・個人contextが入らないことを確認する。

- 新規runは総計最大6、各Case1回まで。retry/follow-up/`local.force`なし。S2/S5は既存runの読取を再利用し、無駄な新規runを作らない。総時間は計画6分、1runの待機期限60秒、steerのclient待機期限10秒を提案する。
- 60秒で新規受付を閉じcancelを要求する。退出不明なら次runを開始せず停止する。**待機期限は受付/待機の上限であり、providerの継続・課金・物理終了の強制上限ではない。** 合成checkのtimerはsteer待機だけを扱い、driverは1起動1 Case、共通ledgerで最大6新規run/累積待機360秒と重複Caseを制限する。client timeoutはPromiseの待機だけを止め、取消/asyncDisposeにも最大steer待機を適用する。SDK importとcleanupを含む厳密な実時間/費用hard capは保証しない。
- 費用停止値は**総額US$1.00、観測警戒値US$0.50**を案とする。毎run後に確定usageを確認し、未確定/取得不能/超過なら次runを止める。[SDK課金](https://cursor.com/docs/sdk/typescript#usage-and-billing)。**SDKの公開send/steer optionsに単発のUSD強制上限は今回確認できない。** usageの確定遅延、実行中の費用、取消後の課金によりUS$1.00を超えない保証はできない。費用上限を強制できるアカウント条件の確認、またはこの限界を含めた具体的許可が得られるまで実行しない。
- 最初は`tools: []`のみ。tool境界での検証が不可欠でも、このIssueの許可ではcustom tool/MCPを足さない。別途許可と固定差分レビューを得た時だけ無害な合成tool1つを検討する。SDK sandbox非対応をsandbox無効化で回避しない。

SDKのlocalはmodelもofflineという意味ではない。モデル推論はhostedで課金対象。[SDK runtime説明](https://cursor.com/docs/sdk/typescript#overview)。SDK制限や費用制御が成立しなければ、理由・不足契約を返して有限調査を終了する。無期限の再試行や独自agent loopを追加しない。

## 保存・ログと残る判断

moduleのsnapshotは明示入力と状態だけを扱う私的な検証データ。driverは専用directory0700/file0600、exclusive lock、予約済みCase/run数、fsync+atomic renameの記録を使う。合成でJSON roundtrip、file mode、保存失敗時の元記録保持を確認した。実SDK store/checkpointの耐久性やcrash復旧は未観察であり、SDK保存契約の検証済みとはしない。SDK store/checkpointには推論に必要な履歴が残り得るので、製品v1の保存除外と同一に扱わない。

公開結果は合成Case ID・版/hash・最終状態・未確認理由のみにする。API key/env、実agent/run ID、原本文・tool引数/結果・思考・error全文、store/rawログ・個人path/session IDは私的記録に限定する。SDKの再開、cancel成功、追加配送成功だけで#465の外部仕事終了問題や権限UIの同等性を解消済みとしない。

現在の採否は**blocked（offline準備は完了、SDK製品採用は未決定）**。次はreview-bが固定成果と未実行driverを独立照合し、PMが認証・費用・具体的実行条件を判断する。live根拠が揃った場合だけtext-only/live-owned runの製品接続範囲を新たに定める。permission/質問/Plan/Todo/MCP/画像/context/モデル/保存・終了排他の差分を、その時点で照合する。

公開仕様確認、offline準備、固定版live probe、製品実装/develop、GUI、mainは別状態。#547のscope終了やPR統合で#212 P02/P04、全ショートカット、Milestone、GUI/mainを完了にしない。実providerの6 Caseとmain反映はPMが既存Issue/QAへ保持・readbackする。
