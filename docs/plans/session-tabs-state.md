# セッションタブの状態契約

2026-09-06 / 親 #62、基盤 #63、UI部品 #64、製品接続 #65、名前同期 #66。

## 現段階

`SessionTabs` は未接続の純粋Kotlin基盤。既存の単一セッションUIを変更しない。
ユーザーのタブ機能全体の完了やGUI passを意味しない。#57 → #61の既存PR統合後、
#65が最新mainへ状態・タブ部品を接続する。#44の再起動をまたぐ本文保存は別Issue。

## 所有と値

- pluginのtab IDは新規タブ時のUUID。CLI chat IDは初回init/result後にrun token経由で結び付ける。
  未送信タブにも安定IDがある。同じCLI IDの履歴を開くと既存タブを選択する。
- mode/model ID/draft/caret/titleはタブに所属。全体設定を切替のたびに書き換えない。
  新規の初期mode/modelはstore作成時に指定。新規既定値設定の追加は別契約。
- `snapshot()`はdetached Listとimmutable値。各操作は同期化し、外からのリスト操作で状態は変わらない。
  GUIはEDTでsnapshotを読む。view/CLI/context/権限設定をこの基盤へ持ち込まない。
- `move(id, index)`はsourceを除去した後の最終index。未知ID/範囲外/同位置はno-op。
- active closeは右、右がなければ左へ。inactive closeは選択維持。最後を閉じるとfresh New Agent。

## 実行と終了の接続義務 (#65)

1. 入力を`updateComposer`で保存し、`beginTurn`が返すoriginal prompt/mode/model/chat IDのsnapshotを使う。
   同時にpermission/sandbox/worktree/executable/workspaceも別途snapshot化し、background処理中に全体設定を読み直さない。
2. preparation Futureとprocessをtab ID **およびtoken**で所有する。init、delta、tool、result、error、
   completionを配送する際は対象tokenを保持し、EDT実行時に再度`accepts`を確認する。selectedIdへ配送しない。
3. close/stopは先に状態側を無効化し、返却tokenが所有するpreparation/processだけ停止する。
   process構築中にcloseされた場合、登録前にtokenを再確認し新processを即破棄する。タブ切替だけでは停止しない。
4. completionは内容/usage/finalizationを反映した後`finishTurn`する。errorとterminationが重複しても一度だけ終了。
   project/content終了時は`stopAll`で先に全tokenを無効化し、その後すべての所有process/listenerを解放する。
5. timeline/scroll/composer/context usageはtab IDで別々に保持し、切替時入力へfocus。
   本基盤はprocess停止、UI本文保持、ディスク保存を実行しない。#65の接続試験が必要。

## 名前

初期値はNew Agent。`applyAutomaticTitle`は実際に確認したprovider由来の名前だけを受け付ける。
未確認のCLIフィールドや冒頭promptからの合成名は渡さない。ユーザーの`rename`が優先し、
古い自動結果で上書きしない。CLI/IDE側での改名同期は#66で取得経路・新旧判定を検証する。
9文字＋省略記号は表示部品の責任とし、状態には完全名を保存する。
