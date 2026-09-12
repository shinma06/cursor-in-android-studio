# 正常終了printのRequest IDコピー

#289。供給元の契約は[固定#284研究](https://github.com/shinma06/cursor-in-android-studio/blob/f5db23038b9a8d4ddf8c08896981547c2a5ce198/docs/research/issue-284-request-id-contract.md)。構造化terminal `result.request_id` だけを採用する。JSON string・非空・前後空白/制御文字なし・UTF-8 1,024 bytes以下をclient側で検査し、UTF-8へ損失なく変換できない孤立surrogateも拒否する。UUID限定・trim・型変換・短縮はしない。不正なoptional fieldだけをnullとし、本文/usage/通常終了を維持する。

## 確定と寿命

[parser](../../src/main/kotlin/com/cursoragent/parser/StreamJsonParser.kt)がnullable fieldへ変換し、[候補](../../src/main/kotlin/com/cursoragent/service/PrintRequestId.kt)が1つのprintプロセス内でResultを仮保持する。`subtype=success`・非error・有効IDと実exit 0が必要。session不一致、ResultのID/本文/model/session/usage/subtype/errorの矛盾はそのturnをコピー不可にする。同じ意味のResult再送は受理するが、欠損/不正IDのResultの後に別Resultで穴埋めしない。

[service](../../src/main/kotlin/com/cursoragent/service/AgentProcessService.kt)のprocessTerminatedは最終JSON断片をfinishし、候補を既存[AgentRun](../../src/main/kotlin/com/cursoragent/service/AgentRun.kt)へ渡す。停止・error・不確定終了の裁定を優先し、成功したprintだけonPrintCompletedを配送する。ACPの通常完了は従来の通知のままで診断値を供給しない。

[Factory](../../src/main/kotlin/com/cursoragent/ui/AgentTurnListenerFactory.kt)は既存のEDT/current generation/token/nonStop検査内で、onRunFinishedより前にControllerのcallbackへ渡す。[SessionTabs](../../src/main/kotlin/com/cursoragent/session/SessionTabs.kt)はPRINT・current token・結び付いたsessionの一致を再確認する。新turn受理で旧値を消し、失敗/欠損/Stopから前turnへfallbackしない。閉じたtab、Controller破棄、project終了で値を解放する。保存本文を開き直しても推測・復元しない。IDは開いたtabのメモリだけで、会話履歴/export/rawログ/通常通知へ追加しない。

## 操作

既存「その他の操作」メニュー内の「Request IDをコピー」を使う。[native menu](../../src/main/kotlin/com/cursoragent/ui/header/RequestIdCopyActions.kt)はメニューを開くごとに選択revisionと確定値のidentityを捕捉し、updateと実行直前に再照合する。A→B→Aの切替、同じ文字列IDでの次応答も古い操作を無効にする。並べ替えだけではtab所有を変えない。

生成中/ACP/未取得/保存本文は理由付きで無効。IDE CopyPasteManagerへ明示操作時だけ正確な文字列を渡してreadbackし、成功/失敗はIDを含まない文言でtimelineに表示する。無効時は空文字や代用品を書き込まない。native menuのkeyboard操作を再利用し、上部toolbarにボタンを増やさない。診断用の追加prompt、ACP slash clipboard操作、print fallback、feedback投稿を追加しない。

## 検証と統合境界

[Case JSON](../verification/changes/issue-289.json)のQ1–Q5は固定build GUI待ち。合成parser/候補/AgentRun/SessionTabs/menuテストと、ローカル合成CLIを実プロセスとして起動する終了接続テストは、実provider・OS clipboard・GUI成功を代用しない。#284の実provider2turn観測は研究に限定し、Q6 ACP診断の構造化取得は公式の供給契約確認後に再評価する。

今回の純依存baseは `905b2e50a144a65b9bd86b164161d8a8ff292f52`（公開STOP#287 `73b31f5e6a50890c22bfd333346eb9b77fd35a60` + #254 `80e19808290f1c20dc882423de0d0d7efcd6ca60` の通常merge）、実targetはdevelop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。既存print正規化/usage/queue/復元を維持する。共有所有解放後にController/FactoryのID接続だけを編集し、未公開#308/#268/#269/#305は取り込んでいない。後のPM統合で#308のbeforeRevert・usage/config callback・changes.beginTurnと今回の完了callbackを両立させ、固定candidateを再レビューする。#300のheader説明と#97の送信キー等も別の公開STOP版として保全する。GUI・merge・enroll・main反映は別工程で追跡する。
