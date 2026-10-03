# ACP内容の有限表示（#296）

## 境界と仕様根拠

ACP標準のcontentを既存のsession/update・tool表示へつなぐ。型の契約は
[ACP Content](https://agentclientprotocol.com/protocol/v1/content)、部分更新は
[Tool Calls](https://agentclientprotocol.com/protocol/v1/tool-calls)を2026-09-13に確認。
調査基準は[#290 / 固定9bb03286](https://github.com/shinma06/cursor-in-android-studio/blob/9bb03286ec6beaebd52acca152909ca7a90221b3/docs/research/issue-290-rich-tool-results.md)。
公式仕様にある画像・音声・リソースを認識する。受信型の認識と実内容の検証は区別する。

Cursor IDE panel、JetBrains AI Assistant + Cursor ACP、IntelliJ MCP Serverを含む比較は上記#290の固定調査を継承する。
メディアを表示できる競合能力を本Plugin独自とは呼ばない。本変更はW1/W2/W3の安全な情報表示まで。
上積みは既存IDE内会話・Task・ネイティブ差分操作と同じ履歴へ統合する点で、実画像previewの同等性は未達。
W4 preview/Play/Save、W5未知MCP structured carrier、W6 browser/image拡張、print媒体は対象外。

## 受信と置換

`AcpContent`は有限のText/Diff/Summaryへ射影し、wire JSON・data/blob本体を保持しない。
image/audioは申告MIME、imageの任意URIを示す。resource_linkは名前/URI/任意title/description/MIME/非負Long整数size、
resourceはtext/blobのどちらかを示す。text resourceはplain本文を表示する。未知型は型名だけ。
MIMEとsizeは申告値、バイナリの実形式/サイズは不明。base64妥当性を検査・decodeせず、値があるだけで有効な画像とは認定しない。
欠落/型違い/競合resource text+blob/不正sizeは形式不正。0 byteは保持し、負数・小数・指数表記・文字列sizeは不正とする。

同一session/tool IDのcontentとlocationsは、フィールド省略なら保持、配列提供なら全置換、[]なら消去する。
null/非配列の明示提供は以前の値を消し、不正表示に置き換える。配列内の不正要素は個別に隔離し、正常な兄弟を残す。
locationsの不正要素は対象リストから除外し、別の通知を表示する。切り詰めたpathで操作しない。
ツールの実statusとは別に「情報のみ・内容未検証」「内容の形式が不正」「表示未対応」「表示上限により省略」を示す。
Task相関・完了/失敗の表示とwire未終了判定、session/Stop/dispose/terminalの配送保護は維持する。
terminalは引き続き未対応参照であり、端末出力へ変換しない。rawOutputを未知のMCP carrierと推定しない。

## 有限性と操作

| 対象 | 上限と超過時 |
|---|---|
| content/locations配列 | 各64要素。残りは省略を明示 |
| メタデータ文字列 | 各2048文字。表示用は省略を明示、操作対象pathは除外 |
| tool text / embedded text | 各32768文字。残りは省略を明示 |
| diff | path 2048、before/after各32768文字。超過時は差分全体を省略しボタンを作らない |
| ターンの射影文字 | 累積1 Mi文字（型/本文/metadata/diff/path）。置換で予算は戻さず、超過内容を省略表示 |
| assistant非テキスト | 512行 + 一度の省略通知。以降も通常text deltaは保持 |
| 既存wire/受信/tool | 1 MiB/frame、4 Mi文字/turn、512 toolを維持 |

表示ラベル・省略通知の固定長オーバーヘッドは射影文字予算に含めないが、要素/行/ツール数とwire上限により有界。
通常assistant原文は既存4 Mi文字/turnの範囲で維持し、非テキストの予算枯渇でも削らない。
全てのmetadata/埋め込みtextはJTextArea。URI/pathをリンク・許可対象へ昇格させない。
画像取得、ファイル読取、decode、再生、Save、復元時の再実行は行わない。
Diffだけは既存の実データを既存ボタンへ渡し、Revertを追加しない。content内の文字/差分ボタンの順も保持する。

## 応答の順序と保存互換性

assistantの非テキストchunkは独立した`AgentEvent.Content`を追加行へ出す。
前の本文を確定し、後のtext deltaは同じmessage IDでも別本文へ開始する。反復deltaをまとめ消ししない。
thoughtは引き続き一時表示で、保存本文へ移さない。

保存version 1 / role集合を維持し、ChatMessageへ任意`presentation: "acp_content"`だけを追加する。
通常assistantのtextと原文Copyは変更しない。内容情報はrole=assistantの表示用plain textとして順序通り保存し、
復元時は識別子で専用plain行に戻す。表示用の種類/URI/MIME/埋め込みtext（各上限内）を保存するがバイナリ本体は保存しない。
既存version 1には識別子がなく、従来通り読み込む。未知の識別子/roleとの組合せは保存読取で拒否し、既存ファイルを保全する。
旧版readerは任意フィールドを無視してassistant文字列として読むため、専用plain行の見た目は新版のみ保証する。
#45の既存role/textベースの検索・Markdown出力には表示文字列として残るが、媒体原本のexportではない。

Tool/Taskの詳細は従来の安全な保存境界を維持。任意の名前/URI/本文/未知型名は保存せず、
通常toolでは既知の内容種類・表示状態を要約に加える。再表示は静止した要約だけである。
この差は「詳細は保存しません」の保存要約で明示する。Taskは従来の子状態要約のみ。
会話全体8 MiBの保存上限とatomic保存失敗時の旧ファイル保全は変更しない。

## 検証

`AcpContentTest`は型・置換・不正要素・上限・元statusを、`AcpContentRecordingTest`は
literal Swing行・差分操作・順序・原文・旧保存互換を確認する。合成ACP serverはforeign/terminal/Stopを通す。
#295のMarkdown画像取得抑止、Task/キュー/復元保護も既存テストで確認する。
実IDEのW1/W2/W3は[Case JSON](../verification/changes/issue-296.json)のpendingで追跡し、合成テストをGUI passとしない。
