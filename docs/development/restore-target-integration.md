# 復元対象の接続契約（#39）

PR #74のdevelop統合後に、復元基盤をcontroller/service/listenerへ接続した。
テスト・独立レビュー・必要Case追跡を揃えてdevelopへ統合する。GUIは固定候補でまとめて確認し、
未確認をpassにしない。main昇格とIssue完了はその受入後に判断する。

## ターンの対象

`TurnWorkspace`は受付時のroot/mode/resume IDを保持し、バックグラウンド準備中に実rootを一度解決する。
`PreparedAgentTurn`を通してCLIの `--workspace` / `-w` / `--resume`、checkpoint、カードが同じ値を使う。
準備中に設定を変えても進行中ターンの対象は変わらない。permission defaultは従来のまま。

ISOLATEDの実保存先は追跡できていないため、分離先パスを推測せず復元を拒否する。
再開する会話は、このserviceが同一rootのDEFAULT実行と確認して保持したsessionだけ復元可能。
再起動後・元root不明・ISOLATEDからの再開はチャットを続行できるが復元は拒否する。
新しいDEFAULT会話からは再びcheckpoint/Revertを利用できる。旧XMLのroot/mode欠落を補完しない。

## 実行と復元の排他

`WorkspaceOperationGate`はproject serviceに1つ。送信受付時からバックグラウンド準備終了まで保持し、
OSProcessHandler生成前に別のprocess予約を取得して、物理終了の通知まで保持する。
`AgentRun.isActive`、停止要求、UIの完了状態だけでは予約を解放しない。
停止中の古いrunが残っている間は新規送信も復元も拒否する。新規会話へ移ってもこの制約を維持する。

checkpointの確認ダイアログ後に復元予約を取得し、Git処理はEDT外で実行、結果表示をEDTへ戻す。
Revertは同じ予約の下でIDE write commandを実行する。復元失敗でも予約を解放する。
監視の初期化失敗時はOS processを直接終了監視して破棄を要求し、実終了まで予約を保持する。
遅いcleanupや重複callbackが次の操作の予約を解放しない。

## 復元と理由表示

- `CheckpointService.createSnapshot(prompt, chatId, target)`はroot/modeを履歴へ保存する。
  作成不可は復元ボタンのtooltipに日本語理由を表示し、操作を無効にする。
- `restoreResult(id)`は履歴targetと現在のroot/modeを照合する。拒否理由を日本語ダイアログに表示する。
- listenerは作成時targetをRevert callbackへ閉じ込め、`revertFileContentResult`へ渡す。
  root外・古いafter内容・未保存editor編集を拒否し、後の編集を保持する。
- Diffはbefore/after本文だけで表示でき、復元不可でも閲覧できる。

## 検証範囲と残る受入

別の実Git worktreeでroot不一致のtracked/untracked両方が不変であることを検証する。
旧XML、mode切替、未知resume、symlink/traversal、正常/stale/未保存Revert、
準備中停止・遅いprocess生成・物理終了待ち・重複cleanup・復元失敗の排他を単体テストで検証する。
実VFS/editor・CLI・確認ダイアログの観察はRESTORE-TARGET-1で固定候補を確認する。

既存checkpointは作成前から未追跡だったファイルの内容を保存せず、作成後にstageされた新規ファイルも
削除対象に含めない。このPRは既存snapshotの保存範囲を拡張しない。
