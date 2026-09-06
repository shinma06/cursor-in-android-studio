# 復元対象の接続契約（#39）

このPRは復元基盤のみ。共有呼出し側はPR #74が所有しており、接続未了のため
**単独ではリリース不可**。通常モードを含む旧呼出しは対象不明として安全に拒否する。
以下の接続、独立レビュー、CI、固定ビルドのGUI受入が完了するまでDraftを維持する。

## APIと不変条件

- `RestoreTarget.capture(projectRoot, worktreeMode)` はターン開始時に一度取得する。
  rootは存在する絶対ディレクトリの実パス。ISOLATEDのroot値は元のCLI workspaceであり、
  分離先を意味しない。ISOLATEDでは実rootが追跡されていないため必ず復元を拒否する。
- CLIの `--workspace` / `-w`、checkpoint作成、編集カードのRevert callbackが**同じターンの
  root/mode**を使う。CLI構築時に変更可能な設定を読み直してはいけない。
- `CheckpointService.createSnapshot(prompt, chatId, target)` は対象情報を履歴へ保存する。
  `unavailableReason(target)` は作成不可の日本語理由。`restoreResult(id)` は保存した対象と
  復元時のプロジェクト・Worktree設定を照合し、`restored` / `rejectionReason` を返す。
- `DiffViewerHelper.revertFileContentResult(project, path, before, after, target)` は同じ照合と
  root内パス検証を行う。ファイルの実内容がafterと異なる場合や未保存の編集がある場合は拒否。
  Diff表示はbefore/after本文だけを使うので、復元不可でも利用できる。
- 旧履歴のroot/mode欠落、不明なmode値を現在の設定で補完しない。旧履歴は保持して拒否する。
  既存の権限default/CLIフラグは変更しない。

## 共有呼出しへの最小接続（所有解放後）

1. `AgentUiController.sendPrompt` のターン受付時にroot/modeを固定し、checkpoint準備と
   `AgentTurnListenerFactory.create` へ同じtargetを渡す。
2. `AgentProcessService` に同一ターンのroot/modeを渡し、CLI構築で使用する。
   `AgentRun` に保持するか専用引数を渡すかは #74 の最終形に合わせる。
   準備中の設定切替や遅いプロセス開始にも同一性が必要。再開セッションが別rootで作業する
   可能性を否定できない場合は、実行結果のworkspaceを確認するまで復元を有効化しない。
3. listenerはカード作成時のtargetをRevert callbackへ閉じ込め、結果の日本語理由を表示する。
   現在の設定からカードの作成時targetを再構成しない。
4. rollbackは `restoreResult` の理由を表示する。Git処理はEDT外、結果表示はEDT。
   実行中CLIと復元を競合させない。停止完了の確認前に復元操作を開始しない。
5. ISOLATED・root不明・snapshot不可の理由を復元導線に表示する。通常の入力欄へ常時説明を
   増やさず、既存のcheckpoint/Revert操作やtooltipで伝える。

## 検証境界

単体テストは別の実Git worktreeを作り、同じGitオブジェクトが見えてもroot不一致なら
tracked/untrackedの両方を変更しないことを検証する。通常モードの復元、旧履歴、設定切替、
不明root、相対/絶対パス、symlink逃避、空白/日本語/改行を含む未追跡名も検証する。

既存のcheckpointは作成前から未追跡だったファイルの内容を保存せず、作成後にstageされた
新規ファイルも削除対象に含めない。このPRはその既知のsnapshot範囲を拡張しない。
実際のVFS/未保存editor/ダイアログと実CLIの同一root接続はGUI受入が必要。
