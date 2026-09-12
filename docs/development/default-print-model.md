# 新規print会話の既定モデル（#269）

Settings → Cursor in Android Studio の「新規会話の既定モデル（互換CLI）」で選ぶ。設定画面の一時選択をApplyした時だけ、既存selectedModelへ保存する。Cancel/resetは保存値を変更しない。
新しい保存enumやモデル名変換は追加しない。旧XMLの空値、Autoの正確な `auto`、未知の保存IDを保持する。一覧にないIDは一覧失敗を理由に消去・先頭候補へ置換しない。Routerの設定値を名称から推測しない。

#43のModelSelector（検索/既存popup）、ModelCatalogState、ModelCatalogLoaderを再利用する。取得中/失敗/空と再試行、request generation/Future取消/遅着拒否を複製しない。
設定viewは独自の一時AgentSettingsStateだけを選択部品へ渡し、disposeUIResourcesでrequestとpopupを閉じ、以後のUI配送・Applyを拒否する。
アプリ共通設定なので一覧取得は適用済みCLI pathとユーザーhome directoryで行い、特定projectを推測で選ばない。既存同期metadata runner/実行ファイル解決を共通化し、15秒timeout・exit/timeout/cancel拒否・文字コード/環境を保持する。
project内の一覧取得やMCP管理は従来のproject directoryを引き続き使う。CLI path変更時はApply後に設定を開き直し、同じ適用済みpathで取得し直す。CLI/providerの認証やlive成功は本実装の合成検証から推定しない。

Composer生成時にmodeとモデルIDを切り離した選択状態へコピーする。新規printだけに保存済み既定モデルを渡し、復元会話/legacy/ACPは空のCLI選択で開始する。保存会話にない過去モデルを推測しない。
既に開いたviewは再生成せず、Applyを各tabや実行中TurnSettingsへ配信しない。ACPのuseAcpによるクリアとserver configurationからの選択は#43のまま。既定モデル編集によってACPのprovider設定を書き換えない。

## 比較・検証と統合

2026-09-13に [Cursor models](https://cursor.com/docs/models-and-pricing)、[JetBrains AI Chat](https://www.jetbrains.com/help/ai-assistant/ai-chat.html)、[JetBrains ACP](https://www.jetbrains.com/help/ai-assistant/acp.html) を確認した。
Cursor IDE内panelのモデル/Auto選択、JetBrains AI Assistant + Cursor ACP + configured MCP + IntelliJ MCP Serverの既存連携を比較対象とする。モデル選択自体を独自機能と呼ばない。
本変更は既定値編集と会話ごとの選択を分け、既存/復元/実行中会話とACPの所属を維持する接続である。実IDEでの同等以上UXは未受入。公開カタログの名称からCLI/ACP IDを生成しない。

DefaultModelSettingsPanelTestは一時選択・Apply/reset・失敗/空/再試行・遅着破棄・旧XML/再起動・新規/既存/復元/ACP境界を合成検証する。AgentMetadataCommandTestはローカル合成実行ファイルだけでargv/workspace/失敗契約を確認する。
[Case269](../verification/changes/issue-269.json)の3件はGPT/人間pending。実CLI一覧・GUI/インストール・実推論は未実施。Android Lifecycle/DBは本経路にない。SwingのEDT、view/request破棄、取得失敗時のデータ保持を確認する。

基礎は公開済み停止#43固定16165ecのみ。#28診断、#97送信キー、#300説明等の公開STOP成果と将来のSettings統合点はPMへ引き継ぐ。公開承認待ち#268/#305のコード・Case・本文は含めず、公開待ちの迂回にしない。B #296 Factory/Timeline/StructuredToolCardは非編集。
