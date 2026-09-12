# hooks・複数root・worktreeと保存再開の契約調査

2026-09-12 / #301。基準sourceは develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。調査中のDraft。

対象は親#25の既存「plugin/hooks・multi-root/worktree・persist」行と#26の名前付きworktree/base/setup・再接続候補。公開契約・既存source・純粋な合成検証を照合する。Cursor provider/CLIの新起動、実hook/plugin、私的設定/会話、GUI操作は実施しない。

現sourceの `TurnWorkspace` は単一rootから `--workspace`、任意の `-w` / `--resume` を組み立てる。`RestorePolicy` は実root不明のISOLATEDを拒否する。ACPはtab単位のresident connectionで `session/new` を使い、接続切断後の自動再送を行わない。本文の保存・再表示とprovider再開は別の判断である。

公開入口は [hooks](https://cursor.com/docs/hooks)、[plugins](https://cursor.com/docs/plugins)、[CLI parameters](https://cursor.com/docs/cli/reference/parameters)、[Cursor ACP](https://cursor.com/docs/cli/acp)。公開入口の存在だけで現Plugin接続や実行成功を認定しない。scope・寿命・失敗・採否・不足証拠の表をこの文書へ固定する。

#26 deferred、#146/#66/#118/#150の既存保留、PR261 freezeを維持する。固定独立レビュー後にwriterを停止してPMへ引き継ぐ。親25/26・製品GUI・QA/mainの完了とは区別する。
