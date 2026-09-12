# 応答のトークン数と情報未提供の状態

#287は#149研究のうち、実測数値の追加を待たずに直せる表示状態とローカル遅着抑止を担当する。数値の正本と未観測範囲は[固定研究](../research/issue-149-usage-contract.md)。研究source/Caseは変更しない。

依存祖先は#215 `14081a219d5193dcb081e401e58058e133131140` と#149研究 `ace52be8872a657e4239b8edee01f33cb202af7c`。develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a` から通常mergeした実装前の固有review baseは `bfc59ad93f31c6be63504349c27c4d79c6dfeae5`。未統合の依存変更をコピーせず実commitを保持する。実統合後は最新developを通常同期して全候補を再検証する。

Controller接続前に#258の停止commit `86934b13a81243b2772daf6a07f20dbf2ad6f95a`（#24/#48祖先を含む）を通常mergeし、合流commitは `fda9403b9a44a110f81dae0946431ab410c2a266`。当初baseと#258だけの純依存合流treeは `596c2fa1e49711cf8cd89ed600a8cba07958f6d9`（`git merge-tree --write-tree`で再現）。このtreeからの差分を#287固有変更としてレビューし、developからの全候補とは区別する。#258の公開CI・実統合状態は別途確認する。

| 状態 | 数値がない場合 | 数値がある場合 |
| --- | --- | --- |
| 未開始 | まだ応答を開始していません | なし |
| 準備・実行中 | トークン数の報告待ちです | この応答で受信した4counterを個別に表示 |
| 正常終了 | この応答では情報未提供です | 完了した応答として保持 |
| 停止要求後 | トークン数は未取得です | 停止処理中の応答として保持。新しい数値更新を拒否 |
| 停止・失敗 | トークン数は未取得です | 停止/失敗した応答として保持 |

開始から終端までを「応答を準備・実行中」とする。OS processの起動時刻や思考の内訳を推定する状態ではない。ACPの候補一覧を取得するだけの接続は応答開始に含めない。状態は既存の開閉パネル内に置き、入力欄へ恒常的な警告は追加しない。

StateはEDT所有。beginは旧値を消して新ticketを発行、finishは成功/停止/失敗を一度だけ確定してticketを失効させる。停止要求後はSTOPPINGとして数値を受け付けず、実際の終端通知だけを受け付ける。停止要求だけで物理終了を認定しない。終端後のusageや古い終端は保持した値・次の応答を変更できない。resetは値/送信時modelを消して未開始へ戻し、旧ticketを無効にする。ControllerはprepareTurn直前にticketを発行し、null/例外による開始拒否も失敗へ確定する。既存のEDT準備・背景準備・executor投入例外はAgentRun→Factoryへ流す。StopはAgentRunが受け付けた場合だけ停止中へ進め、既にOS終了を観測した場合は実終端を優先する。接続/transportの置換とdisposeではresetする。#258の未送信入力回復と#48の成功判定・予約送信は変更しない。Factoryは既存run/token・dispose・Stopの検査をUI配送直前に行い、その内側でticketを照合する。

print4counterは`TokenUsage`の非負整数Long・field別validationを維持する。明示0を表示し、部分欠損はその行だけ非表示にする。全field未提供と0は別であり、正常本文も捨てない。null更新はその受信snapshotを表す。入力/cacheの重複関係が不明なため合計せず、context占有率・料金・account残枠へ換算しない。

送信時に選択したモデルをこの応答の情報としてパネルへ表示する。printで生成中に次のモデルを選んでも、実行中/直前の応答の数値を新モデルの値とは表示しない。Auto/既定は実モデルへ推測変換しない。別タブへ表示を切り替えるだけでは各タブの値を失効させない。新しい応答・会話/接続置換・disposeの所有変化とは区別する。

#149 fixtureの採用済みprint（実測3件と合成print境界）を製品`TokenUsage.parse`へ通し、ローカルcallbackはStateと既存`updateCurrentTurnOnEdt`/SessionTabsで検証する。受信時にactive tokenを付けることは、由来不明のACP usage wireが現在の応答かを証明しない。ACP数値eventを追加せず、実used/size/cost・unstable scope・順序未確認は親#149へ残す。

[Cursor IDE Agent](https://cursor.com/docs/agent/overview)と[JetBrains AI Assistantのcontext管理](https://www.jetbrains.com/help/ai-assistant/chat-mode.html)は既存の会話・context機能を持つ。今回の改善は本Pluginが確認できる情報の提供状態と応答所有を示すもので、usage計測やMCPのIDE操作を独自機能と呼ばない。Cursor ACP + IntelliJ MCP構成でも、IDE側情報からproviderの未提供usageを生成できるとは扱わない。providerの数値契約を追加せず既存のIDEパネル/イベントを利用する。

GUI受入は[Case287](../verification/changes/issue-287.json)のU01/U03/U05相当。自動テストと実clipboard/フォーカス等の他Issue受入を混同せず、固定buildでの表示・開閉・狭幅・テーマ・Stop/遅着を指定GUI担当が確認する。#261統合保留中はenroll/mergeせず、develop統合後のQA双方向link/readbackとmain全候補確認をPMへ引き継ぐ。#287の完了で親#149全体を閉じない。
