# #39 復元先の安全性 — GUI引継ぎ

状態: queued / 共有呼出し接続待ち。実行・インストール・runIde・再起動は未実施。
この基盤PRだけでは通常modeの呼出し接続も未了のため、リリース/GUI passにしない。
親 #20 / #1、GUI #40、関連 MV-023 / MV-027 / MV-030。

operator未割当。固定HEAD/base/ZIP hash/ロード実体とleaseを確認した指定担当だけが実行する。
ローカル資材位置と環境識別値は公開文書へ書かず、担当間で安全に引き継ぐ。

## RESTORE-TARGET-1（接続後に実行）

使い捨てGit fixtureにcommit済みファイル、事前の未追跡ファイル、同名の別rootファイルを用意する。
許可書込みはfixtureのみ。CLIはPro/Teams、permission defaultを維持。既存GUI予約に割り込まない。
予算: 45分、最大8送信、3修正。

1. DEFAULTで1ファイル編集。カードDiffを開けること、Revertで変更前に戻せることを確認。
2. 同カード対象を再編集（保存済みと未保存editorの両方）。古いRevertが日本語理由で拒否され、
   後の内容を保持することを確認（MV-030）。
3. DEFAULT checkpoint後にtrackedを編集、新規untrackedを追加。復元でtrackedが戻り、新規分だけ
   削除され、事前untrackedは残ることを確認。確認ダイアログ/完了/失敗理由を観察。
4. ISOLATED実行では分離先パスを推測せず、checkpoint/Revertを日本語理由付きで拒否。
   元rootと分離先の両方が復元操作で変化しないことを確認（MV-027）。Diffは閲覧可能。
5. ISOLATED→DEFAULT、DEFAULT→ISOLATEDで古いカード/checkpointを操作し、別rootへの書戻しがないこと。
   準備中の切替でもCLIと保存targetが一致すること。DEFAULTへ戻してもisolatedカードは拒否のまま。
6. root/modeのない旧履歴をロードして復元拒否、新規DEFAULT履歴は復元可能。root移動・不明も拒否。
7. 応答中の復元は実行中CLIとの競合を防ぐこと。確認後、fixture内容・稼働処理・設定を記録する。

結果: 全件pending。ソーステスト/ビルド成功はGUIの観察記録ではない。
