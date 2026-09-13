# AGENTS・Skillsの指示監査（#271）

基準: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。対象はCLAUDE.md（AGENTS.mdのsymlink先）、.agents/.claudeのstart-work/finish-work、GitHub workflow。全ユーザー共通Skills・他plugin・サーバー保護・自動化コードは対象外。

## 記事から採用する考え方

[OpenAI: Rethinking skills and prompts for GPT-6 Astra](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra)（2026-09-11公開、2026-09-12確認）は、Skillの発動条件を絞り、詳細を必要時に読み、AGENTSの無条件読取や過剰な手順を見直し、成果の完了条件を明確にすることを勧めている。モデルの特性だけを理由に既存の権限・品質基準を解除しない。異なるモデルも使うrepositoryなので、固有のコマンドと安全条件は明示したままにする。

## 現状と変更の対応

| 現状・履歴 | 改訂・正本 | 保持する条件 |
| --- | --- | --- |
| 古いroot checkoutは472行のCLAUDEだが、現developでは過去履歴を固定リンクへ整理済み | 現developを基準にし、履歴ファイルの再移動や複製をしない | 旧版の版付き観測・既知の回避策・参照先 |
| AGENTSの6段手順、start/finish、workflowに開始/終了条件が重複 | AGENTSは常時制約と入口。Skillsは開始/終了の成果。具体判断はworkflowの「参照する範囲と進行判断」へ | Issue/claim/branch/worktree/PR、独立review、GUI lease、4必須check、QA/main/cleanup |
| typo/docs作業にも全技術資料を無条件に要求 | workflowの作業別参照表へ。確認済み同一版は再利用 | Issue全コメント、依存、該当要件/消費側の照合。変更/矛盾は再取得 |
| #261では短い承認の対象確認が不十分なまま登録後、tickが自動承認レビューで拒否 | ユーザー発言と具体質問/操作の対応を確認。記録・要約を承認そのものにしない | 明確な既存承認の継承。拒否された設定変更/mergeは別途承認待ちを維持 |
| #254/#45では公開操作の具体承認後に進行、#215では公開PR作成が拒否され独立確認だけ継続 | 元依頼の範囲内は再質問せず、拒否時は根拠確認か必要な承認だけを求める | 認証/公開/削除/サーバー保護の境界。別担当への代理実行で拒否を迂回しない |
| writer停止・enroll・scheduled coordinatorの説明だけでは、次の実行が不明になり得る | PMは実session/job handleと結果/次担当を確認。停止は当該branchへ限定 | owner/source/registry、PAUSED heartbeat、Astraの子Agent禁止 |
| #44の保存ID確定後にも#47/#48に古いblocker記録が残った | 部分条件とIssue全体の終了を分け、現状態から次の独立作業を選ぶ | 未解決受入・最終固定候補GUIを完了扱いにしない |
| CLAUDEの本文保存は#44前の説明、現行実装文書は更新済み | 会話保存契約のproject JSONとQA259へ訂正 | 旧XML metadata互換、本文閲覧/provider再開/Revertは別判定 |
| #27の人間部分PASSを再受領、既存コメントに同じ結果あり | 既存証拠を再利用し、未観察の別Case/新buildへ転用しない | Case JSONの正本と全候補promotion gate |

承認レビューの判断はrepository指示だけでは変更できない。本改訂で#261/#215の拒否が解除された、または質問回数が減ったとは判定しない。公開履歴は[PR261](https://github.com/shinma06/cursor-in-android-studio/pull/261)、[PR264](https://github.com/shinma06/cursor-in-android-studio/pull/264)、[PR267](https://github.com/shinma06/cursor-in-android-studio/pull/267)、[QA27の部分結果](https://github.com/shinma06/cursor-in-android-studio/issues/27#issuecomment-5643949636)を参照。承認のprivate原文/host/pathは転記しない。

## 固定差分で照合する代表ケース

以下は意味と参照経路の回帰確認。新しいモデルを実行したA/B性能評価やGUI試験ではない。独立reviewerは各入力で旧要件を失っていないか、改訂後の期待動作を照合する。

| 入力・状況 | 改訂後に必要な動作 |
| --- | --- |
| 文書の誤字修正 | Issue所有とPRは必要。無関係な製品全資料・Gradleの手動追加は不要。共通classifier/hook/CIに従う |
| Kotlinと文書の混在変更 | 製品のcall flow/関連要件を確認し、Runtime側の必要テストを実施。文書扱いでskipしない |
| 通常のread-onlyレビュー | 新Issue/worktreeやwriter操作を要求せず、固定差分と受入を判定する |
| 明確な同一操作のユーザー承認がある | その範囲を継承して進め、理由のない再質問をしない |
| 別Issueの「承認します」/要約/自動継続だけがある | 未承認の削除/保護変更へ適用しない。具体質問との対応を確認する |
| 自動承認レビューが公開操作を拒否 | 当該操作を停止。証拠追加がなければ代理実行/言換え再試行せず、不足する承認を具体化。他の独立作業は進める |
| writer停止・enroll後、coordinator未実行/PAUSED | 自動完了と報告せずhandle/結果/次担当を確認。勝手な再開/子worker起動なし |
| session待機のtimeout | 終了と推定して再起動せず同じhandleを確認。成功した検証の再走を開始しない |
| GUI未実施のdevelop実装 | 必要テスト/固定独立review/Caseを満たせば統合可。QA引継ぎ・readback後に元実装をclose、QA/mainは未完のまま |
| 一部Case passのmain候補 | 全commit/全必要Case/同一buildの条件を満たすまでpromotion不可 |
| merge済みだがcleanup未確認 | 自分の停止済みclean資源とremote/local/trackingを照合。保留資源は担当/再開条件を残す |
| #44本文再表示とACP再開 | 保存本文の存在だけでprovider resumeやRevert可能と判断しない |

形式確認はfrontmatter・両Skillの一致・AGENTS symlink・変更した相対リンクを確認する。必要な自動検証はCase271とChange Impactに従い、結果はPRの固定SHAに記録する。効率・停止率の改善は未測定であり、以後の実タスク履歴で確認する。
