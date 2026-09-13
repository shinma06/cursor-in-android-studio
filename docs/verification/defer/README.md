# #313: 対象callbackだけを保留する検証ビルド

通常製品へ検証サービスを追加しない。`src/test` の制御部品と固定
`instrumentation.patch` を、commit済み候補の別コピーだけへ接続する。
通常の `buildPlugin` はこれらを含まない。別コピーの表示名は
`Cursor in Android Studio [Verification 313]`、versionは
`0.1.0-verification-313`。同じplugin IDなので、将来のGUI担当は通常版と同時導入せず、
専用IDE環境とhost leaseの手順に従う。ここではIDE操作・インストールは実施していない。

## 接続する境界

| Case | 接続点・識別 | 解放後に確認する既存処理 |
|---|---|---|
| CTX-24-LIFETIME（公開#24） | `queue`: ticket取得後、owner再評価前。ownerはproject/input fieldの合成UUID、tokenはgeneration:revision:item UUID | `queue.dispatch`の既存owner/generation/revision/先頭item検査。close・選択変更・queue編集後は送信0件 |
| CTX-24-LIFETIMEの候補遅着 | `popup`: Futureの結果取得後、EDT上の既存guard前。入力fieldの同じowner、popup UUID/query generation | close・次query後の既存project/popup dispose・showing・generation検査がfalseとなり旧候補を表示しない |
| #229 SETTINGS-BOUNDARY step 2 | `preparation`: 元pooled callback内、最初のisActive検査前。ownerとturn UUID | 設定変更試行はcapturedとsend-entryの設定/workspace fingerprint一致。Stop試行はguard=false、send-entryなし、reservation-closed |
| #231 LIFECYCLE-EDT | `edt-chunk` / `edt-stop` / `edt-complete` / `edt-update`: 元guard直前。owner、turn UUID、normal/terminal区別 | disposed/current/stopped/allowStoppedを再評価。古いchunk・旧終端は拒否、同じownerの許容Stop終端だけ配送 |

CTX-24-SNAPSHOTは送信操作が戻った最初の機会の次draft変更と既存queue手順を使う。
同期Add to Chatの非同期化・追加hookはない。#229の設定変更とStopは別試行にする。
#231の結果は **test-deferred callbackのguard試験** であり、元OS EventQueueに滞留した
順序そのものの観測ではない。検証版の結果をD997・b9f8275・以前のZIPへ転記しない。
元Caseの実GUIと最終候補受入は引き続き未実施。

## 固定候補の生成（非GUI）

1. 専用Issue worktreeをcommitしてcleanにする。通常の必要テストと`buildPlugin`を実行する。
2. `python3 docs/verification/defer/defer_fixture.py prepare <未作成の出力ディレクトリ>`。
   `git archive HEAD`から別sourceを作り、patchを`git apply --check`してから適用する。
   通常src/main・build設定を書き換えず、別source内でだけ制御部品をtest→mainへ移す。
3. `python3 docs/verification/defer/defer_fixture.py build <出力ディレクトリ>`。
   必要なら`--platform-path <SDK>`を付ける。既存SDKの参照のみでIDEは起動しない。
4. `build-identity.json`のHEAD、元source tree、patch/helper SHA256、生成src/mainの各hash、
   source digest、ZIPと全JAR SHA256を保存する。ZIP CRCも検査する。
   通常ZIPには`com/cursoragent/verification/`がなく、検証ZIPにはあることを照合する。
   最終候補へ変更を加えたら新しい未作成出力へ再生成し、古い証拠と混同しない。

## 将来のGUI試行（今回未実施）

操作は指定GUI担当だけが行う。本文・認証情報・実projectパスをcontrolへ渡さない。
controlは任意コード・コマンド・URLを実行せず、固定のarm/release/closeだけを受け付ける。

1. `python3 docs/verification/defer/defer_fixture.py init`でrun専用0700ディレクトリを作る。
   検証IDEだけに表示された`-Dcursor.verification.directory=<directory>`を設定する。
   propertyなしでは制御thread・待機・control I/Oを開始しない。通常ZIPはpropertyを読まない。
2. 検証IDEで対象tabを開き、`... status <directory>`の`owner`記録を、対象tabを開いた操作と
   対応付ける。project/fieldの合成UUIDだけが記録される。tab切替だけでownerは変えない。
3. `... arm <directory> <owner> <point> [--token <正確なtoken>]`。
   既定`next`はこのowner/pointの次の一件のみ。応答の通常chunkを保留しても、
   `edt-stop`は別pointなので同じStop終端や他ownerを巻き込まない。
   コマンドは最大5秒で受付を確認する。未確認時は同じ操作を連打せずstateを確認する。
4. 該当操作を開始し、stateの`reached`とpending UUIDを記録する。
   操作時刻・owner/tokenを保存してから、設定変更／Stop／close／次queryを行う。
   背景準備のreachedはcheckpoint/context/送信開始より前。EDTは停止しない。
5. `... release <directory> <pending UUID>`を一回実行する。
   既存guardの結果と実表示・fixtureの送信件数を照合する。単なるreleasedは合格ではない。
   設定試行はcaptured/send-entryのfingerprint一致と下流の固定入力を照合する。
   Stop試行はguard=false、send-entry無し、reservation-closedと復元可能への回復を照合する。
   logのsend-entryはserviceへの入口であり、OS process起動自体の観測ではない。
6. 各保留は最大60秒。timeout/close/interruptionは`aborted`であり成功解放と扱わない。
   準備workerでは例外で元のcatch/finallyへ戻り、そのturnを失敗終了して元予約を解放する。
   EDTの中止は保留closureを破棄し、当該試行を未達にする。中止後に結果を再利用しない。
7. 対象turnをStopし、`... close <directory>`、IDE終了後に残thread・保持callback・
   対象runを確認する。closeは新規保留を無効にして一件の参照を破棄/workerを起こす。
   背景workerのfinally完了までをcloseコマンドの応答だけで断定しない。
   必要な証拠を保存後、所有runディレクトリだけを削除する。再接続は新runと検証IDEで行う。

## 非GUIの証拠と限界

`DeferControlTest`は実`PromptQueue`、`updateCurrentTurnOnEdt`、`AgentRun`、
`WorkspaceOperationGate`を用い、一件限定・重複release拒否・旧ticket/chunk/終端拒否・
Stop終端の独立配送・準備Stopの送信0件・timeout/close/interruptionの予約解放を確認する。
`test_defer_fixture.py`は通常source不変、限定3ファイルへのpatchと別helper配置、
専用directoryと未確認command再送拒否を確認する。
これらの成功は実IDEのCase PASSではない。
