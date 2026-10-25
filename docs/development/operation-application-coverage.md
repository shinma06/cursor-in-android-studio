# 全既存運用適用のcheckpoint（#580）

採用済み運用を全既存リソースへ適用する作業の、現在の分類と明示pendingを固定したcheckpointです。全goalの完了、製品Case PASS、NEW実稼働、監査baseline更新を意味しません。範囲と進行は[Issue #580](https://github.com/shinma06/cursor-in-android-studio/issues/580)、有限監査は[#598](https://github.com/shinma06/cursor-in-android-studio/issues/598)で継続し、両IssueをOPENに保持します。

## 一つの対象一覧

全資源行の唯一の一覧は[coverage JSON](../verification/coverage/issue-580.json)です。この文書は読み方とcheckpointの要約で、同じ行を二重管理しません。既存の12列は `columns` と各resource行の同名fieldで保持します。baseline、共有/外部資源、開始後の新規tracked、限定保留義務は別配列にし、資源のコピーや保管先を別の論理資源として水増ししません。

| 対象群 | このcheckpointの行数・意味 |
| --- | --- |
| baseline tracked | 1,065行。main326＋develop739、756path。開始時のbranch/path/revision/blobを保全 |
| 共有/GitHub/外部/ローカル | 8089行。受領した全ページsnapshotとactual deltaをstable resource_idでupsert。各revisionと読取範囲の時点を保持 |
| 並行採用で追加されたtracked | 19行（main10＋develop9）。固定レビューsourceと採用blob一致・同branch文脈を確認 |
| 本人の限定保留 | PR582↔Issue580のnative Development関連1義務。既存資源件数に重複加算しない |

開始時mainは `471b6383ba4fcd290c12c25edef63a5e6d10be66`、developは `69f230280ee223569ccff2ac9ef92f2ddeb56c3b`。共通309pathのうち同blob198/異blob111、main-only17/develop-only430を区別します。今回取り込んだ採用inputはmain `a9055e9f21a0bd01a5ceaffe511a94f0b8064edb` / develop `32919f96da05c9b991036f109976ecd6d40e3f87`。PR585/589/592/593/594/595/597/602の実採用と追加/変更pathをJSONに記録し、製品差を平坦化しません。Gitの変更path数と、baselineの意味判定の件数は別です。

## baselineの実読と適用判定

| disposition | 行数 | 意味 |
| --- | ---: | --- |
| conformant | 792 | 用途・契約・消費側と採用運用の適合。製品受入PASSではない |
| changed | 53 | 必要修正の採用を固定PR/sourceへ結ぶ |
| protected | 189 | 原観察・履歴・生成/vendor等の保持理由を確認。未読を既準拠へ変えない |
| excluded | 16 | 当該適用の対象外である理由を記録 |
| pending | 15 | mainコメント等7行とNEW配布4path×2branchの8行。下記のowner/再開条件を保持 |

本文読取は925 read＋140 reused、未読0。再利用は同blob・適用文脈一致と読了元に結び、binaryの性質確認と命令全監査を区別します。`read_status`はbaselineではこの読取状態、外部資源ではmetadata/本文/bytes/全ページ等の実際の範囲を記録します。snapshotの存在、リンクの到達、metadataの保護だけを全文読了や適用完了にしません。

原Case7行とscope562の1行は原blob/旧FAIL/結果を保護したまま、main595/develop602の現consumer・現在案内とPM/coverageの運用custody受領により適合としました。canonical develop案内は採用OLD artifact SHA256 `f043d91f36c12bc7b5623fe18d4827157ac6618ee9cdec2a1ab25b5acecbba74` に結び、scope562旧候補の案内と混同しません。製品Case実行/PASS生成や9QA人間ACKを適合条件に追加していません。

## 適用済みの管理metadataと残るnative操作

過去を含む元379＋603の380 Issueについて、363 title、204の限定type、581のexact冒頭、Project596 Done、13件のmetadata/照会/残件管理窓口受領を適用・個別readbackし、独立post-readbackも担当範囲PASSです。[実適用receipt](https://github.com/shinma06/cursor-in-android-studio/issues/603#issuecomment-6395597892)に結び、元state/終了理由/時刻・Case/原FAIL/本文・assignee/Milestone・既存native関係と理由付き空欄を保持します。管理窓口の受領は元claim/source/人間QCの移管・解放や、現worker認定ではありません。

Development199組の実UIは未実施でARB予約を優先します。main OPEN残条件11組、既存8組、実owner不明の旧PR14/16/17/18の理由付き非適用4を分けます。PR582↔580だけは[本人の明示保留](https://github.com/shinma06/cursor-in-android-studio/pull/582#issuecomment-6393598798)で、本人の再開指示と認証済み経路・指定担当/leaseが揃うまで試行しません。他の585/594/602等の未関連をこの保留で免除しません。本文Refsをnative関連成功にしません。

Issue581/584/587/590のcloseと599/600/603等のblocked更新は別actorの準備中で、今回の入力では未適用です。後送のactual state-operation deltaで更新し、title反映や子PR採用をIssue完了へ置き換えません。

## 明示pendingと次の責任

| 残件 | ownerとcheckpoint時点 | 再開・解消条件 |
| --- | --- | --- |
| main7行 | [#588](https://github.com/shinma06/cursor-in-android-studio/issues/588) owner/運用変更PM。main未採用、必要nativeGUI環境blocked・ARB優先 | 固定candidate/同ZIP/必要受入・独立評価と正規promotionの実結果。scope plan PR592だけで完了にしない |
| NEW8行 | [#599](https://github.com/shinma06/cursor-in-android-studio/issues/599) writer/運用変更PM。未採用・未実稼働 | automatic early/promoted、manual stable/candidateの全guard付きreadinessを固定基盤/OLD保全・実run/権限/queue/担当と照合し、正規検証・採用・actualearly切替をreadback。正式version人間指定がなくactualcandidate/正式公開は未実施 |
| native/後続main反映 | PM共有担当・各残責任owner。602↔600等は別未達 | ARB優先後の明示枠と正規経路、状態不変/両端readback。develop固有QA caller/案内の後続main反映custodyを維持 |
| OLD配布 | 既存[#579](https://github.com/shinma06/cursor-in-android-studio/issues/579)/#440のowner。run40176018520はbuild成功/publish FAIL | 同tagの既存複数Releaseを検出して拒否。採用329の共有assetは未公開。原asset/URL/FAILを保持し、所有/許可を確定した別scopeで扱う |
| 有限監査/終端照合 | [#598](https://github.com/shinma06/cursor-in-android-studio/issues/598)監査owner・独立評価者、coverage-planner | 後続実採用delta、終端refs/追加・削除・rename、primary FF、必要な各owner原本の永続private custody/index/hash・権限readback。現在checkpointで#563 baselineを進めない |

590/600のowned sourceは復元検証済みbundle/必要原本を保持してcleanup完了、remoteは既に不在でした。native・main反映・OLD/NEWとIssue完了は別判定です。共有resource担当の必要原本とpost-readback評価は永続privateへ同bytes保管・hash/索引で受領済みで、raw/host/private pathは公開しません。

このcheckpointを通常レビュー/必要検証で採用しても#580/#598全体を完了にしません。後続は同じresource_idで更新し、未読/他owner待ち/新たな差分を既準拠に置き換えず、未達・受領先・解除条件を記録します。
