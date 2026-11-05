# 全既存運用適用の終端snapshot（#580）

採用済み運用を全既存資源へ適用した範囲、保護対象、未達と受領済みの残責任を示す時点付き記録です。全資源行の唯一の一覧は[coverage JSON](../verification/coverage/issue-580.json)。この文書は読み方と要約で、別台帳ではありません。製品全体の成功、全QA終了、所有・操作権限の取得を意味しません。

## 対象と履歴

JSONの`terminal.cutoff`時点で[#580](https://github.com/shinma06/cursor-in-android-studio/issues/580)と[#598](https://github.com/shinma06/cursor-in-android-studio/issues/598)はOPENです。これは時点の観測で、後日もOPENを維持する指示ではありません。`checkpoint`は[PR582](https://github.com/shinma06/cursor-in-android-studio/pull/582)時点の履歴、`terminal`が今回受領した終端差分です。private v004のcutoff・原本・唯一current pointerは変更せず、formal受入と後続sidecarを証拠として再利用しました。

| 対象群 | 行数・読取範囲 |
| --- | --- |
| baseline tracked | 1,065行。main326＋develop739、756path。開始時のbranch/path/revision/blobと925read＋140reusedを保持 |
| 共有/GitHub/外部/ローカル | 8,435行。既存の全ページ取得範囲に有限の実差分をstable resource_idでupsert。metadata/body/bytes等、各行の実読範囲を区別 |
| 開始後のtracked追加 | 30行。従来19＋後続11。採用PRと採用blobへ結び、コピーや保管先で水増ししない |
| 本人の限定保留義務 | 1行。582↔580の未関連事実と本人指示を保持。PR/Issue自体とは異なる義務identity |

合計9,531行、既存12列と一意resource_idを保持します。開始時mainは`471b6383ba4fcd290c12c25edef63a5e6d10be66`、developは`69f230280ee223569ccff2ac9ef92f2ddeb56c3b`。原分類792 conformant／53 changed／189 protected／16 excluded／15 pendingはcheckpointに保持しています。今回、旧pending15を実採用・受入に結んだ現在分類は792／68／189／16／0です。未読をconformantに変更せず、生成/vendor/license/directive・固定履歴・原FAILを保護します。metadataの取得やリンク到達を全文読了・製品PASSとは扱いません。

## 終端差分と受入

今回の採用inputはmain `f5d68c55970679e329c619d646d7925c4002bf93`、develop `2811a991a5783d317a3b96663b9afd301b9f9602`。PR582 checkpointのinput以降の追加・変更・削除・rename判定77件を`terminal.tracked_delta`で記録し、最終blobと最後に変更した採用PRへ結びます。既存行の元revision/blobを現物へ書き換えず、削除/renameの履歴も消しません。既存ownerの固定差分・検査・独立評価・通常採用の証拠を再利用し、全件再読や同入力の製品suite/GUIを増やしません。

- main旧pending7行はPR612の限定コメント/fixture反映と5Step受入・独立評価・main採用を[実receipt](https://github.com/shinma06/cursor-in-android-studio/issues/580#issuecomment-6645405551)へ結びます。固定candidateは`1231254b58f4f1494ba3a650939ac6a290e5c93c`、ZIP SHA-256は`a023e706c8f2ea9b67bdf6a478ac5d60314ff7c6fe127c862f310fab38f2d76f`。正確な単一表示保証は別QA325で未達です。
- NEW旧pending8行はPR614/618の両branch readinessとPR619/620の現行案内、OLD停止/drain・NEW実early1を[#599終端receipt](https://github.com/shinma06/cursor-in-android-studio/issues/599#issuecomment-6648839214)へ結びます。製品source `3cf0da9a204c45fa02d1e70f24cb9fd7fd4f4cb9`、CI41951260521 attempt1、ZIP SHA-256 `7d56dc3c4d1cc7aa06fc772c5a7aad6d8043cada8b9170ba9a72453227b4b93e`、2,071,134 bytes、Release434690553/asset711729856。後続案内変更のCI/delivery成功は入力変更なし・Release変更なしで、この初回earlyと別です。
- NEW delivery394292718/candidate394292721はactive、OLD353992272はdisabled/drained。正式version指定・candidate実生成・正式公開は未実施、将来の自動productearly/queueは未観測です。旧FAIL・旧URL/asset、未知2資源と権限境界を保持します。

## native・残責任・保管

v004で実施済みの元202組と追加6組、既存8組、理由付き非適用4組を区別し、後発610↔605、612↔588、616/617↔615は各担当の実receiptへ結びます。v004時点のhold件数を書き換えて現在件数と混同しません。614/618/619/620↔599も[実適用receipt](https://github.com/shinma06/cursor-in-android-studio/issues/599#issuecomment-6649244857)で4/4 UI/API両端・保護差分0を受領しました。元CLOSED/completed・closedAt `2026-11-05T03:25:45Z`・ProjectDone・M6・parent580を保持、12:55:37 JST返却ACK・12:55:56 JST別FREE・operator停止を確認。旧[待機receipt](https://github.com/shinma06/cursor-in-android-studio/issues/580#issuecomment-6648935676)は経過履歴として残し、[親受領](https://github.com/shinma06/cursor-in-android-studio/issues/580#issuecomment-6649250254)へ結びます。本文Refsはnative成功ではありません。

| 残責任 | 実受領owner・未達・再開条件 |
| --- | --- |
| 582↔580 | [本人hold](https://github.com/shinma06/cursor-in-android-studio/pull/582#issuecomment-6393598798)を維持し試行なし。管理ownerは運用変更PM。本人の明示再開、認証済み経路、指定担当/leaseが揃うまで未関連。理由付き保留の運用処理とnative成功を区別 |
| QA325 | [PMが今回観察の管理/保管を実受領](https://github.com/shinma06/cursor-in-android-studio/issues/325#issuecomment-6645221861)。単一表示保証未達・原因不明。元Case/owner/state/原FAILを保持し、固定QA build/ZIP/CLI条件で元担当/PMが次の有限確認を定める |
| #579未知2資源 | H579-U1/U2は保護継続。[PMは照会・残件管理だけ実受領](https://github.com/shinma06/cursor-in-android-studio/issues/579#issuecomment-6647561415)。所有・元/現許可・次担当の実受領は未確定。原#573/F1と配布/保管/repo窓口への資源別確認が再開条件。管理受領を操作権限や製品成功へ置換しない |
| main反映・親受入 | PR582からのmain反映責任はPM保持。今回develop採用後、同3ファイルの必要最小main反映を新固定review/通常採用し、#598最終8領域＋自己編集別担当確認、その後#580受入へ進む |
| primary | 今回は更新しない。終端source採用後の別段階で、固定target/clean/衝突/復元保全/ancestryを確認した通常FFとreadbackが必要 |

#588はclaim返却、採用r2 source/refs/worktree削除済み、旧601はsuperseded終了。実復元済bundle・必要原本15・ignored証拠と旧43f/ZIPの原blockedを保管しています。#599/615の6所有sourceはremote/local/tracking/worktree不在・held0、raw/ZIP/14bundleと全6source単独復元bundle・必要ignored証拠を保持。[588返却](https://github.com/shinma06/cursor-in-android-studio/issues/588#issuecomment-6645849608)・[599 cleanup receipt](https://github.com/shinma06/cursor-in-android-studio/issues/599#issuecomment-6648903362)を再利用し、元実行を繰り返しません。旧580 sourceのcleanup/実復元済bundle、共有原本の永続private custody/index/hashも保持します。今回sourceは採用/許可されたcleanupまでcoverage ownerが保持します。

公開表にhost/private path/rawを含めず、各原本の索引・hash・実originは既存private保管先で受領します。削除を完了条件に追加せず、保持理由・owner・再開条件を明示します。監査baselineは不更新。このPR自体や後発close/claim返却/native等の管理receiptは既存Issueに追補し、終了のたびsource再PRを強制しません。cutoff内の欠落・誤分類、受入を変える新事実は必要箇所を訂正します。
