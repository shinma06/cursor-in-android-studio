# ACP画像添付

#277は、入力欄のPNG/JPEGファイルD&Dとclipboard imageから1枚の画像を取り込み、
ACP標準image blockを送る。画像のみの入力にも対応する。製品の固定buildでのGUI確認は
[Case I1–I8](../verification/changes/issue-277.json) に分け、合成テストをGUI成功へ転記しない。

## 能力と入力経路

[ACP初期化](https://agentclientprotocol.com/protocol/v1/initialization) の
`agentCapabilities.promptCapabilities.image` がbooleanのtrueである接続だけを使用する。
欠落・false・不正型・未接続・printでは本文と画像を保持して理由を示す。
モデル/モードは実際の送信前に設定を確認し、画像対応も送信直前に再確認する。
画像を落とした本文送信、path化、printへの切替、自動再試行は行わない。
[ACP content](https://agentclientprotocol.com/protocol/v1/content) のPNG image blockにはdataとmimeTypeを設定し、元pathやtemp URIを含めない。

GrowingPromptFieldが生成したEditorだけにUserDataとnative DropHandlerを設定する。
EditorPasteのwrapperはその印とimage/file flavorを確認し、通常テキストと他のEditorを既存handlerへ渡す。
IME・mention/slash・送信キー・Tab移動の既存経路を保持する。画像の読取・decode・保存は背景で行い、
EDTへ戻った時点でdraft generation、tab、接続世代、モデル、実行設定を照合する。
新たな入力を既存画像へ暗黙に置き換えず、1枚制限の理由を表示する。

## 検査と送信サイズ

これはclient policyであり、provider上限ではない。入力は10 MiB以下、各辺4096以下、
合計800万画素以下。decode前にbytes・形式・寸法を確認し、decode後の寸法も照合する。
PNGへ正規化して512 KiBを超えた画像は差替えを案内する。送信用の画像を自動で縮小しない。
プレビューと送信は同じsnapshotを用い、表示用サムネイルだけを小さく描く。

AcpJsonRpcは採番済みID・text/context・base64・JSON escape・改行を含む最終UTF-8 frameを実serializerで測る。
1 MiBを超えた要求はpending/writer登録前にAcpLocalRejectionで拒否し、接続を維持する。
到達不明のwrite失敗や受信形式/上限エラーは従来どおり接続を閉じる。
受信lineの1 MiB、turn payloadや履歴再生の既存上限は引き上げない。

## 所有と失敗時の操作

検査済みPNGはproject外のUUIDディレクトリへ保存する。元画像は読取りだけで、名前やpathをtemp名へ移さない。
owner markerにはUUID・PID・process startを記録し、POSIX環境では所有者だけにアクセスを許可する。
送信時はサイズとhashを再照合する。元画像の後編集・削除・symlink先の変更は保存済みsnapshotを変えない。

下書き・各予約項目・実行中ターンは個別の参照を持つ。予約の編集/移動は同じ画像を保持し、
削除/画像除去/会話closeは対応する参照だけを解放する。画像だけの予約から画像を除去すると、その空項目も削除する。
予約dispatchは画像所有権をターンへ渡す。未開始の失敗は予約をその場で一時停止する。
開始後の失敗/Stopは元の本文・設定・context・画像を一時停止した予約一覧へ戻すため、次の下書きを上書きしない。
送信開始済みなら届いた可能性も表示する。再送は利用者が内容を確認して予約送信を再開した場合だけである。
初回の送信サイズ超過など、prompt未送信を確認できた画像再試行は会話IDがまだなくても明示再開できる。
通常の新規予約は継続IDを要求する既存ガードを保つ。切断したACP会話は既存方針どおり再利用しない。
その場合は予約の画像プレビューから画像をコピーし、新しい会話で利用者が明示的に再添付できる。

正常完了では最後の所有tempを削除する。会話UIには表示用サムネイルだけをメモリで保持し、
本文保存には「画像添付・再添付が必要」の印だけを残す。base64/temp絶対pathを履歴やrawログへ保存しない。
project disposeはrun停止とACP closeの後にimage workerへstore closeを依頼する。
異常終了の回収は、次回project service起動時に背景で停止PID/start ownerと既知の通常ファイルだけを確認して行う。
生存owner・不明marker・未知ファイル・symlinkは保持し、元pathを含めない理由をログへ残す。経過時間だけの削除はない。
ローカル削除をprovider履歴削除とは扱わない。end_turnは画像理解の成功を保証しない。

## 既存製品との比較（2026-09-13確認）

[Cursor IDEの画像入力](https://cursor.com/docs/agent/prompting) と
[JetBrains AI AssistantのChat mode](https://www.jetbrains.com/help/ai-assistant/chat-mode.html) に画像添付があるため、添付自体は独自機能ではない。
[JetBrainsのACP接続](https://www.jetbrains.com/help/ai-assistant/acp.html) はCursor ACPに加えて
custom MCP/IntelliJ MCPの受渡しを用意している。この組合せを比較対象とし、MCP経由のIDE操作が存在しないとは扱わない。
本実装は既存のIDE入力欄・タブ・予約・実行token・contextへ画像所有寿命を直接接続する。
1枚と上記client上限に限定しており、複数添付や製品全体での同等以上UXを確認済みとはしない。
この変更では実installed競合UIや接続先の利用可能MCP toolsを操作/列挙していないため、その実環境比較とI1–I8は未検証。

[固定調査 #10](https://github.com/shinma06/cursor-in-android-studio/blob/3b949706e188f61c24514b8c9ca3bb9b9967e982/docs/research/issue-10-image-contract.md)
の単一CLI/modelでの識別成功・不正PNGのUNAVAILABLE/end_turnと、本製品のGUI受入を区別する。

## 検証の入口

ImageInputTestは破損/形式/寸法/bytesと元画像不変、ImageAttachmentStoreTestは参照/改変検知/停止owner回収、
ImageDraftTestは遅着/設定変更/二重取込/本文委譲、QueuedImageTestは画像のみのturn・予約所有移譲/再試行/削除を確認する。
AcpSessionTestはliteral trueだけの送信・画像のみblock・設定確定と再利用、AcpJsonRpcTestは実UTF-8境界/ID桁増加/未送信拒否/接続再利用を確認する。
GUI、OS clipboard、実Cursorの画像理解・保存再開は別途I1–I8の担当が固定source/ZIP/全JARを照合して実施する。
