# 実行ライフサイクルの6条件と証明範囲

#231の基準はdevelop `029060104ff8b077fea9868e50dd678445b60827`。この表は検証の選択と限界への入口で、pass結果の台帳ではない。変更分の期待・初期状態は [issue-231.json](changes/issue-231.json)、候補の結果は [検証正本](README.md)へ記録する。

[Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html)の考え方を、既存の単体・契約・結合・GUIへ適用する。比率や件数のノルマは設けず、同じ失敗を最も小さい実経路で検出する。層名だけでは保証を増やさない。

## 条件ごとの入口

以下のテスト名は当基準と本変更の実在するメソッド。JUnit列はrepository rootで `./gradlew test --tests '*クラス名'`、全体は `python3 scripts/workflow/change_impact.py --base origin/develop --run-tests`（commit済みHEAD）で実行する。複数クラスは `--tests` を追加する。GUIは指定lease担当が同じ固定候補/ZIP/JAR・CLI版を記録して実施する。

| 条件 / 製品の責務 | JUnitクラス・代表テスト名 / 直接通る経路 | 既存QA Case / 証明しない範囲 |
| --- | --- | --- |
| ① 終了通知は一度だけ / AgentRunの終端・listener detach | [AgentRunTest](../../src/test/kotlin/com/cursoragent/service/AgentRunTest.kt): `intentional stop suppresses shutdown errors and completes once`、`real stderr error is delivered once without a second completion notification`、`stop racing process construction cannot leave a process alive`。実AgentRunを呼ぶ | [SESSION-TABS-RUNS](changes/issue-65.json)、[ACP-START / ACP-CANCEL](changes/issue-147.json)。fake processの競合試験は全OS schedulingや実IDE通知を保証しない |
| ② 所有tabだけに配送し旧tokenを拒否 / controllerのgeneration・SessionTabs token・listenerのEDT再判定 | [SessionTabsExecutionTest](../../src/test/kotlin/com/cursoragent/service/SessionTabsExecutionTest.kt): `switch and close route output only to the original live tab while physical exits hold restore` は実Run/Tabsだが配送listenerは代替。追加 [AgentTurnDispatchTest](../../src/test/kotlin/com/cursoragent/ui/AgentTurnDispatchTest.kt): `queued output follows its owner across selection and rejects closed or replaced tokens`、`queued callbacks recheck disposal generation and stop while terminal callbacks may finish stop` は実factoryが使うguardとSwing EDTを通る | SESSION-TABS-RUNS、ACP-STREAM、今回 [LIFECYCLE-EDT](changes/issue-231.json)。選択tabと所有tabは異なってよい。helper試験は実controller生成・Project dispose・widget結線全体を実行しない |
| ③ 取消要求と物理終了は別 / WorkspaceOperationGateの準備/実行予約、AcpSessionの観測child | [WorkspaceOperationGateTest](../../src/test/kotlin/com/cursoragent/service/WorkspaceOperationGateTest.kt): `stop while constructing a process blocks restoration through actual exit`、`process exiting synchronously does not unlock remaining preparation`。[AcpSessionTest](../../src/test/kotlin/com/cursoragent/acp/AcpSessionTest.kt): `cancel response cannot release restore until observed child exits and another tab stays usable` は実sessionとPython fake server/childを起動 | SESSION-TABS-RUNS / SESSION-TABS-RESTORE、ACP-CANCEL。synthetic childの終了は実Cursorの全子孫・detach済み未観測processを保証しない |
| ④ 終了/来歴が不確定なら復元拒否 / AcpSessionとRestorePolicy | AcpSessionTest: `EOF after prompt latches uncertainty rejects same session retry and releases pending once`、`persistent child after cancellation latches uncertainty instead of claiming stopped`。[RestorePolicyTest](../../src/test/kotlin/com/cursoragent/service/RestorePolicyTest.kt): `unknown missing relative and changed roots fail closed`、`legacy history loads without assuming a mode or root` | ACP-CANCEL / ACP-RESTORE、SESSION-TABS-RESTORE。実wireの静止契約は#146公開待ち。metadataしかない旧履歴の本文/復元が可能になった証拠ではない |
| ⑤ 後続保存・未保存編集を保全 / write lock内のFileRevertOperationとGitSnapshotStore | [FileRevertOperationTest](../../src/test/kotlin/com/cursoragent/service/FileRevertOperationTest.kt): `normal Revert restores content but an old card preserves later saved edits`、`unsaved editor changes prevent writing even when disk still matches the card`、`file resolved outside root or retargeted after lookup cannot write`。実restore関数＋一時disk/代替FileAccess。[GitSnapshotStoreTest](../../src/test/kotlin/com/cursoragent/service/GitSnapshotStoreTest.kt)は実Git fixture | ACP-RESTORE / SESSION-TABS-RESTORE。fake unsavedフラグは実Document/VFS/write commandの接続を証明しない。全未追跡ファイルやIDE外並行writerの完全snapshot保全を主張しない |
| ⑥ 要求への返信は一度だけ / AgentInputRequestのCAS、AcpSessionのpending解放、request card | AcpSessionTest: `unconfirmed config fails before prompt and permission replies once with exact numeric zero`、`unsupported request returns error and stop resolves outstanding permission`。[AgentRequestCardTest](../../src/test/kotlin/com/cursoragent/ui/timeline/AgentRequestCardTest.kt): `effective cancelled answer is shown even when allow was clicked before stop` は実requestとSwing card | ACP-REQUESTS / ACP-UI。合成serverは公開wire互換/実Agentの動作を証明しない。card試験だけではIDEのkeyboard/focus/accessibility全体を保証しない |

## 実経路と確認した欠落

`AgentUiController` は `!disposed && turnGeneration == generation && sessions.accepts(token)` をfactoryへ渡す。`AgentTurnListenerFactory.update` は全callbackを `updateCurrentTurnOnEdt` へ通し、**enqueue時ではなくEDT上の実行時**にproject disposed/current/stoppedを再確認する。Stopped/Uncertainだけは `allowStopped=true`、それでもdisposed/旧tokenは許さない。①の重複終端防止はRunの責務で、EDT helperが終端回数を所有するわけではない。

従来のSessionTabsExecutionTestは独自listener内で `tabs.accepts` を直接呼び、EDT queueを通らない。guardをenqueue前へ誤って移しても検出できない欠落があった。本変更では元の判定/dispatchを同じsourceファイルのinternal関数へ移し、その実関数をテストする。新interface/イベント基盤/IDEテストframeworkは不要。queuedテストはlatchでEDTを保持し、enqueue後にclose/token置換/stop/dispose/generationを変更してから解放する。sleep依存ではない。

有効tokenの別tab選択でも配送する対照例と、EDT上では即時実行する例を含む。条件を一時的に無効化した場合の失敗をPRに記録し、故障はcommitしない。実controller/widgetの配線確認は独立sourceレビュー＋LIFECYCLE-EDTに残す。

## 変更前・目標・適用構造

| 場所 | 変更前 → 目標/適用 | 維持/移動の理由 |
| --- | --- | --- |
| `src/main/.../ui/AgentTurnListenerFactory.kt` | create内guard＋private runOnEdt → 同ファイルのinternal updateCurrentTurnOnEdt | 実guardを試す最小境界。呼出し・判定順・EDT即時/queueの意味を維持 |
| `src/test/.../service/` | 状態/復元/Runと代替listener結合 → 維持 | sourceの責務に対応。層別folderへ一括移動しても保証が増えない |
| `src/test/.../acp/` | protocol/JSON-RPC/fake resident process → 維持 | 合成Python serverはテスト内の入力。実wireへ名前を変えない |
| `src/test/.../ui/` | 既存card/selector等 → AgentTurnDispatchTestを追加 | 実sourceの配送境界を対応配置。標準JUnit source setで自動検出 |
| `src/test/resources/stream-json-fixtures/` | 採取済みprint fixture → 維持 | 出所と観測版は既存記録。started eventの推測をcompleted採取と同等にしない |
| `docs/verification/` | Case JSON/生成表示 → 本条件表を入口から参照 | 結果を複製せず関連Caseへ案内 |

旧→新ファイル移動はなし。package/import、plugin.xml、resource path、Gradleのtest/resource検出設定を変えない。runtime/test/knowledge/Case JSONの混在はChange Impactで分類する。#229は設定責務、#230はprint/ACP意味境界、#44は本文保存が担当で、このIssueはそれらを先行実装しない。

## 証拠の読み方と次回の発火

合成入力のJUnit、採取済みprint fixture、公開待ちACP wire、実GUIを区別する。JUnitのpassは実行HEAD/コマンドと共にPRへ、GUIの実観察は固定候補/buildを伴うCase結果へ記録する。既存QA #152ほかのpending/blockedをこの棚卸しでpassへ変えない。実wireや実GUIに未確認があっても、条件表の初回適用と最小回帰テストの完了とは分ける。

以後、停止/終端・token/generation/EDT配送・復元・要求返信を変更するwriterは該当行と実経路/未実測範囲を見直す。独立reviewerは固定HEAD/baseで実source経由、故障を検出するassert、Case/コマンド/出所の一致を確認する。既存PRの境界レビューに接続し、新常駐監査や全GUIの毎回実行は追加しない。次の実変更での不具合削減効果は未測定。
