# #290 Web・Browser・MCP rich resultの受信/表示契約

調査日: 2026-09-12。基準実装: develop `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。結論は、Webの実行能力をMCP限定にせず、まず既存ACPカードへ型・状態・安全なメタデータを接続すること。Browser操作、画像生成、MCP Appsの再実装は採用しない。製品変更はPMが別Issueへ分離する。

## 根拠・確認範囲

- Cursor公式IDE内panel/CLI/ACP資料は下記各行のリンクを2026-09-12に確認。CLI `2026.09.10-fd3934a` の公開可能な観測は #278/#284 を参照し、今回の追加provider promptは **0回**。CLI版と更新されるWeb資料を同一版の実測とみなさない。
- ACP v1 schemaは commit [`bcb9d7ea13adc0b47e906c82f3d692d495a6fa34`](https://github.com/agentclientprotocol/agent-client-protocol/tree/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1) のstable/unstableを照合。stable SHA-256 `caf62ff962ada396878372ced11efb2c6764e59d90919a38583c319948931a42`、unstable `bf7d01218c4fc330b4f08840bda168dca5525951cdaf2dc32e58b9a5b4a8eb75`。本書のcontent/tool型は両方に存在する。
- MCPは明示版 **2025-11-25** の [Tools仕様](https://modelcontextprotocol.io/specification/2025-11-25/server/tools) を使う。全MCPの最新版という主張ではない。
- 公開済みfixtureは [README](../../src/test/resources/stream-json-fixtures/README.md) のprint `2026.09.02-c22c1a3` の観測範囲。Web/Browser/media/MCP出力の実測fixtureはこの集合にない。#146の未公開原本を再作成・転載しない。
- [オフラインチェック](issue-290-check.java) は基準製品のparser/rendererを直接呼ぶ合成データ **4 test / 0 failure / 0 error**。providerがその合成形状を送った証拠ではない。画像の `AA==` は型解析用で、有効な画像・音声のデコード検証ではない。GUI、URI取得、IDE/Browser起動、MCP/認証設定、clipboard操作なし。

## エンジン能力と受信経路

| 能力 | 公式根拠と判定 | 現Plugin・採否 |
| --- | --- | --- |
| Web Search / Fetch | IDE Agentには検索・取得toolがあり、CLIにもweb accessがある。MCPは拡張経路。[Agent概要](https://cursor.com/docs/agent/overview)、[CLI利用](https://cursor.com/docs/cli/using)。CLI changelog 2026-07-13にはWeb Search承認設定をheadless/ACPでも尊重する記載がある。[更新履歴](https://cursor.com/docs/cli/changelog) | F-14の現要求書は既に「MCPのみではない」と修正済み。`MentionResolver` の `@web/@docs` 文はconfigured MCP toolを勧めるだけで、実行APIではない。文言の整理候補はあるが、検索専用tool名・結果fieldを推測実装しない。 |
| Agent Browser | CursorはBrowser操作とscreenshot参照を提供し、内部ではMCP server/拡張を利用する。外部MCPの手動設定が常に必要という意味ではない。network traffic表示はIDE Agent panel向け。Browser storageは保持され得る。[Browser公式](https://cursor.com/docs/agent/tools/browser) | #157/#208の手動browser表示はAgent操作能力ではない。CLI/ACP経由のBrowser操作・screenshot搬送は今回未実測。専用操作UIは保留。既存tool更新を表示することとBrowserを実際に操作できることを分ける。 |
| 画像生成 | IDE Agentの能力として公開されている。Cursor ACPは `cursor/generate_image`、`toolCallId`、`description`、optional `filePath/referenceImagePaths` を記載。[ACP](https://cursor.com/docs/cli/acp) | 同ページはnotification/response不要と記しつつResponse例もあるため、要求/応答を確定できない。`filePath` は提案pathであり生成済み証明ではない。現 `AcpSession` は独立notificationを無視、未知requestをrejectする。新しい生成エンジンや応答を作らず、公式整合化または承認済みcapture待ち。 |
| MCP tool結果 | MCPの結果型とCursorがACP/printへ変換する形は別契約。`content` と `structuredContent`、`isError` はMCP側の実在field。[MCP Tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools) | PluginからMCP serverへ重ねて実行しない。ACP標準contentを先に扱い、MCP固有carrierは実測待ち。一般assistant本文をtool結果と逆推定しない。 |
| MCP Apps | `_meta.ui.resourceUri` / `ui://` resource、HTML/JS、sandboxとhost bridgeが必要。[公式概要](https://apps.extensions.modelcontextprotocol.io/api/documents/overview.html) | 単なる画像・resource表示ではない。現Pluginにhost実装なし。embedded HTMLを自動実行する近道は採用せず、独立需要と契約が出るまで保留。 |

#10/#277は入力画像、ここは出力画像である。`promptCapabilities.image/audio/embeddedContext` は入力能力の宣言であり、出力の可否を決めるフラグではない。[ACP Content](https://agentclientprotocol.com/protocol/v1/content)。#118/#263のTask契約はそのまま参照し、子Taskや汎用rich結果からBrowser能力を推定しない。

## 型・状態・更新の契約

ACPは `session/update.params.sessionId` と `update.toolCallId` で照合する。tool IDは不透明なsession内IDで、URIや数値として解釈しない。`tool_call` / `tool_call_update` のstatusは `pending/in_progress/completed/failed`。省略fieldは以前の値を維持し、`content` / `locations` の配列は**全置換**、空配列は消去。固定schemaの `ToolCallUpdate.content` に replacement が明記される。[Tool Calls](https://agentclientprotocol.com/protocol/v1/tool-calls)、[固定schema](https://github.com/agentclientprotocol/agent-client-protocol/tree/bcb9d7ea13adc0b47e906c82f3d692d495a6fa34/schema/v1)。

| 結果型 | ACP / MCPの実在field | 基準実装と採否 |
| --- | --- | --- |
| text | ACP `content[].type=content` 内の `content={type:text,text}`。MCP `content[]` の `type/text` | ACP toolは `AgentToolContent.Text` → `JTextArea` の文字列表示。既存経路を維持。assistant chunkのtextは別の本文経路。 |
| image | `type:image,data` (base64), `mimeType`。ACPはoptional `uri` もある | toolは `Unsupported("image")`、assistant非textはnullで無表示。型・MIME・安全なサイズ/名前と未対応表示を採用候補にする。静止画previewは上限・decode・明示操作を満たす別Case後。入力画像受入を流用してpassにしない。 |
| audio | `type:audio,data,mimeType` | 同様に未対応/本文無表示。型と状態の通知は候補、再生は保留。明示Playと停止/破棄、形式・長さ・decode上限の根拠が必要。autoplay不可。 |
| resource link | `type:resource_link,name,uri`、optional title/description/mimeType/size | 元URI/metadataは現モデルに残らない。安全な文字列表示＋明示Open候補。URIを見ただけで取得しない。MCP `resources/read` は別操作であり、必ず `resources/list` に出るとも限らない。 |
| embedded text/blob | `type:resource,resource` 内に `uri` と `text` または `blob`、optional MIME | 現状 `Unsupported("resource")`。textは長さを制限したplain表示候補。blobのpreview/保存はmediaと同じ検証が必要。HTML/JSは文字列として扱い、実行しない。 |
| 構造化データ | MCP `structuredContent` はobject、optional `outputSchema`、`isError` はtool error。ACP `rawOutput` は任意JSONでMCP objectとの同一性を保証しない | 現 `AcpProtocol` は `rawOutput` を保持しない。実carrierが確認できるまでMCP専用JSONカードは保留。将来もtool生成元を示したread-only表示とし、同内容のtextとの重複に注意する。 |
| ACP diff / terminal | `type:diff,path,oldText,newText` / `type:terminal,terminalId` はwrapped contentとは別の `ToolCallContent` | diffは既存差分表示、terminalは未対応。terminal IDは出力本文ではなくclient管理端末への参照。diff表示は安全なRevertの証拠ではなく、ACPカードにRevertを追加しない。 |
| 未知型・欠損 | unknown type、optional fieldなし、不正な配列要素を区別 | 未知型は型名のみ保持しstatusを残すが元payloadは失う。`content` 欠損/null/非配列は旧値維持、`[null]` は例外になる。後者は合成で再現した現状であり望ましい契約ではない。表示未対応とtool失敗を混同しない。 |

MCP `isError=true` のtool失敗とJSON-RPC protocol error、ACP `status=failed` は別の層である。Cursorの変換を観測せず対応を断定しない。`completed` はproviderの報告完了で、ファイル・Browser・画像生成の効果をPluginが検証した意味ではない。未知statusは現UIで「状態確認中」となり、quiescence判定も未完了として保持される。

printは [出力形式](https://cursor.com/docs/cli/reference/output-format) のJSON Linesと、公開fixtureのnested `readToolCall/editToolCall/shellToolCall` を区別する。基準 `ToolCallPayloadParser` は `call_id` / `callId` を読み、既知read/edit/shellだけを型付けする。その他の `*ToolCall` はkind要約のみでmedia/structured data/errorを残さない。公式の `tool_call.function` 例も型付けparserではnullとなり、上位のgeneric ToolCall fallbackへ進む。print独自のWeb/Browser/media field名は未確認であり、合成チェックの `syntheticRichToolCall` は観測名ではない。

## 最小の接続とmedia境界

既存の `AcpProtocol` → `AgentTool/AgentToolContent` → `AgentTurnListenerFactory` → `ChatTimelinePanel.upsertStructuredTool` → `StructuredToolCard` を拡張候補とする。同じtool IDの行を置換する仕組みを再利用し、stream片を別カードへappendしない。`locations` は現実装ではpathのみでlineを保持しない。session違い/終了後のtool更新は `AcpSession` の既存境界を維持する。

メディア対応の最低条件は次のとおり。以下は**client採用条件**でありproviderの上限値ではない。

- base64は厳密decodeし、宣言MIMEと実形式、圧縮bytes・decode後の画素/寸法・音声長、item数とturn総量を別に制限する。値は製品Issueのfixtureと用途で固定し、未確定のままpreviewを有効にしない。現ACP wireは1 MiB frame上限（送信はUTF-8と改行込み）、turn上限は `JsonObject.toString().length` の合計4×1024×1024で**bytesではない**。tool数512。mediaのために一括緩和しない。
- URI/pathは内容であって取得権限ではない。http/httpsも既定では取得せず、未知scheme・`javascript:`・`data:`・MCP `ui://` はブラウザーへ自動渡ししない。local pathは正規化、実在、許可workspace境界、symlink、形式/サイズを確認する。`filePath` の提示だけで生成済みや閲覧許可としない。
- 明示Open/Preview/Play/Saveは対象・取得元を表示してから行う。保存はuser選択先、重複/上書き確認、パストラバーサル防止が必要。結果中の指示文やannotationsを実行・追加権限の根拠にしない。本文表示のためのURL自動取得をしない。
- 無効・超過・未知型でもtool ID/状態と「表示未対応」「内容が不正」「上限超過」を分けて残す。byte全保持は不要で、bounded metadataから始める。再読込、tab切替、Stop/dispose、配列置換で古いmediaや遅延処理を表示せず、復元時にURLへ再接続しない。

**既存Markdown画像の別修正候補。** `MarkdownRenderer` (commonmark 0.30.0) は生HTMLをescapeするが、合成 `![synthetic](https://example.invalid/pixel.png)` は `<img>` になる。`MessageTextPane` は標準 `HTMLEditorKit` を使用する。[Java 21 ImageView](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/swing/text/html/ImageView.html) のURL画像を非同期読込する仕様から、[仮説] 表示時に意図しない取得が起こり得る。今回証明したのは文字列変換までで、実ネットワーク取得は未実測。最小候補は共通Markdown Imageノードをalt text/明示リンクにすること。生HTMLescapeだけを「自動接続なし」の根拠にせず、既存本文経路も対象にした別製品IssueをPMへ提案する。

到達経路は `AgentTurnListenerFactory` の本文callback → `ChatTimelinePanel.setAssistantText` → `AssistantMessageBubble.setContent` → `MarkdownRenderer.toHtmlFragment` → `MessageTextPane.text`。[会話復元](../../src/main/kotlin/com/cursoragent/ui/timeline/ChatTimelinePanel.kt) のassistant行も同じ経路を通るため、live受信と復元の両方が修正対象となる。[UserMessageBubble](../../src/main/kotlin/com/cursoragent/ui/timeline/UserMessageBubble.kt) は同じpaneを使うがMarkdown変換をせず本文をescapeし、ACP tool textはplain `JTextArea` なので今回のimg生成経路とは異なる。共通rendererの呼出元はこのassistant経路のみと確認した。

既存要求 [F-60](../cursor-agent-plugin-requirements.md) の添付/paste/previewは入力画像、F-62はBrowser視覚検証の将来契約であり、任意のassistant Markdown URLを無確認で取得する要件ではない。ただし画像抑止は現在表示できていた外部Markdown画像にも影響するので、alt textと取得元を残し、採用する明示previewを別Caseで受け入れる。画像表示機能全体の撤去を提案するものではない。

## 最も強い既存構成との比較

| 構成 | 比較と本Pluginの判断 |
| --- | --- |
| Cursor IDE内Agent panel | Web/Browser/画像生成とその表示が既存能力。Browserの操作状態・screenshotの統合を比較対象とするが、独立Agents Window専用UIやピクセル複製を目標にしない。rich表示そのものは独自機能ではない。 |
| JetBrains AI Assistant + Cursor ACP + configured MCP + IntelliJ MCP Server | ACP agentへcustom MCP/IDE MCP toolを渡せる構成とIDE操作能力を比較する。[JetBrains ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)、[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)。公開設定能力は確認したが、各SDK/runtime・media rendererの実受入は #150 の固定IDE比較へ残す。 |
| 本Pluginの候補 | 既存ACP eventを日本語の状態・未対応表示へ正確に接続する。将来のAndroid build/device/artifactへのIDE直接連携は、競合MCPで可能な範囲も比較して初めて上積みを主張できる。今回Android実環境操作・同等以上UXは未受入。 |

## 採用順・将来Caseと保留解除

PMが製品Issueへ分離する順は、(1) 共通Markdown画像の自動取得抑止、(2) ACPの型/状態/安全なmetadataと非text本文fallback、(3) 必要な静止画・resourceの明示preview。音声、MCP structured carrier、Browser専用UI、画像生成extension、MCP Appsは根拠不足または追加host機構が必要なため保留。Web engineの再実装は不要。

| Case | 将来の製品/GUI受入・解除条件（今回すべて未実施） |
| --- | --- |
| W1 状態/置換 | 同IDの部分更新、別ID/session、欠損と `[]`、mixed text/media、成功/失敗/未知statusを確認。未対応でも完了結果を消さず、カード重複や他tab混入なし。CLI parser checkと固定build GUIの両方。 |
| W2 不正/上限 | 不正base64/MIME、巨大寸法/bytes、壊れた要素、未知type、terminal参照切れでも誤成功・無制限allocationなし。正当な兄弟contentの扱いと拒否理由を製品仕様に固定する。 |
| W3 自動取得なし | Markdown画像、生HTML、resource URI、embedded HTMLの表示/復元/Copyで通信・実行なし。許可した隔離fixtureと観測手段を使い、明示Open/Preview後だけ対象へ接続。今回の文字列チェックだけをGUI passとしない。 |
| W4 明示media操作 | 有効PNG/JPEGなど採用形式、許可path/外側symlink、URI、Save上書き、Stop/dispose/tab切替・遅延完了を固定fixture/buildで確認。音声は別途Play/Stopと制限確定後。 |
| W5 実carrier | 承認された公開/合成データだけでCursor ACP/printのWeb/Fetch/MCP実tool結果を取得し、版・相関ID・status・fieldを公開可能な形で固定。MCP `isError/structuredContent` 変換を確認するまで専用mapperを採用しない。 |
| W6 Browser/生成 | Browser操作と手動表示を別々に確認。生成extensionはnotification/requestの公式不整合を解消し、実際のfile/bytes/MIMEとtool結果相関を確定。GUI lease・公開fixture・明示scopeが揃うまで追加probeしない。 |

## 再現と受入境界

JDK環境をプロジェクトの通常検証と同じにし、repository rootで実行する。一時Gradle initだけで研究用Javaをtest sourceへ追加する。製品build設定の変更・新依存・provider呼出しはない。

```bash
python3 - <<'PY'
import pathlib, subprocess, tempfile
with tempfile.TemporaryDirectory(prefix="issue290-") as directory:
    init = pathlib.Path(directory) / "check.gradle"
    init.write_text("allprojects { plugins.withId('java') { sourceSets.test.java.srcDir('docs/research') } }\n")
    subprocess.run(["./gradlew", "-I", str(init), "test", "--tests",
                    "com.cursoragent.acp.Issue290ContractCheck"], check=True)
PY
```

4件は現状を固定するcharacterizationで、望ましくない無表示/不正要素例外/Markdown img生成も記録する。修正後にその挙動を守るための製品テストではない。既定test sourceに追加しないため、通常のGradle testとは別に上記を実行する。共通Change Impactは研究JavaをUNKNOWNとして必要な検証を選ぶので、分類を緩めてskipしない。

研究の受入は公開契約・基準実装・合成再現・型別採否と未確認条件の固定まで。独立review/共通検証結果とwriter停止はIssue/PRの固定SHA記録を正本にする。#290の研究完了を親 #25/M3、製品GUI、QA/main、#146/#150の完了にはしない。
