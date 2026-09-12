# 権限設定の共有範囲とACPの案内 (#300)

[#297固定契約](https://github.com/shinma06/cursor-in-android-studio/blob/635bde65248f9f6772cd869f860e912cd49be058/docs/research/issue-297-run-mode-contract.md)のP1/P2/P3採用範囲。設定値や権限判定を変更せず、既存メニューとpermissionカードの説明だけを改善する。

## 操作する人への説明

- 操作の確認・実行範囲・作業場所は全project共有。メニュー選択時に保存し、他tab/projectを含む次回の送信準備へ使う。表示した保存値を進行中turnの実効値と呼ばない。メニューを開く/Escapeでは変更せず、SettingsのCancelで既に選んだ共有設定は戻らない。別のSettings画面はCLIパス/送信キー/通知をApply/OKで保存する。
- 標準/CLI既定は追加flagなしで保存済みCLI設定を継承する。毎回事前確認、allowlist解除、sandbox無効を保証しない。「すべて自動実行」は明示deny等の例外を残し、sandboxは対応tool/経路の範囲に限定する。[CLI Parameters](https://cursor.com/docs/cli/reference/parameters)、[CLI Configuration](https://cursor.com/docs/cli/reference/configuration)、[sandbox reference](https://cursor.com/docs/reference/sandbox)。
- ACPの保存値との組合せが現在のPluginで未対応なら、候補へ「ACP要設定変更」を付け、既存serviceの拒否理由を表示。候補1つを変えた後の組合せで判定するので、他の保存値が未対応の間は標準候補にも案内が出る。理由に必要な3設定を示す。接続方法のACP候補でも選択前に同じ理由を表示する。
- 表示のために共有値を標準へ戻したり、選択肢を無効化して他tab/projectのprint設定を壊したりしない。利用者の明示選択は保存し、送信時の早期検査と実行境界はそのまま拒否する。無断print切替なし。未対応はPluginの確認範囲でありCursor ACP全体の不可能を示さない。
- 「今後も許可/拒否」の適用範囲・有効期間は接続先が定める。Pluginの共有設定を変更せず、選択されたopaque IDを既存経路で返す。「回答を送信しました」はtool成功やproviderの永続化確認ではない。[ACP Tool Calls](https://agentclientprotocol.com/protocol/v1/tool-calls)。

## 実装境界

ToolWindowChatActionsの候補/説明を更新する。RootPanelから既存AgentProcessService.settingsUnavailableReasonへのcallbackを渡し、service/AcpSession validationを複製・変更しない。update時に選択tab/保存値を読み直し、破棄後はserviceへ触れない。AgentRequestCardのplain text説明を追加し、対象不明の許可拒否・未知kind無効・取消/遅着・二重回答防止を保持する。設定enum/default/flag、Controller、B263の受信/Task表示、StructuredToolCardには固有変更なし。外部設定やraw wireを読まず、allowlist管理やauth操作は追加しない。

## 比較と未確認

[Cursor IDE内panelのRun Modes](https://cursor.com/docs/agent/security/run-modes)の現契約を基準にするが、保存互換enumを新しいRun Modeへ自動移行しない。[Cursor + JetBrains ACP](https://cursor.com/docs/integrations/jetbrains)、[JetBrains ACP設定](https://www.jetbrains.com/help/ai-assistant/acp.html)、[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)を含む構成の設定・承認能力は既存機能であり、本Plugin独自としない。IDE/MCP側の確認省略とCursor側権限も別層。今回の上積みはPluginが実際に保持する共有値/適用時点と、直接IDE接続の確認済み境界を同じ導線で説明すること。実効policy取得や競合とのGUI優位は未確認。

## 検証と引継ぎ

既存18設定組合せの理由・snapshot・起動前拒否を再利用し、各候補の表示とservice理由の一致、開く/更新/取消での共有値保持、tab/transport切替、破棄後callback拒否、permissionのplain text/opaque ID/二重回答を回帰確認する。[新3Case](../verification/changes/issue-300.json)はP1/P2/P3に対応しGPT/人間pending。既存QA40/102/104/107と過去Caseの結果は変更しない。新live permission実測は146のowner/公開範囲に従う。

専用branchはD9979266から停止済み#97 `bb94abdef442c762439ab05dc389197f791ef980` を通常merge。固有reviewbaseは97、全targetはdevelop、258/24/48祖先を保持。297研究は参照だけ。297と既存依存の統合順を守り、統合後はorigin/develop通常mergeと実target差分・テスト・Case・CI・独立レビューを更新。PMが専用QAへ双方向link/readbackしmainを追跡する。それまでenroll/ready/mergeせず、146未公開原本と261freeze/251監査を維持する。
