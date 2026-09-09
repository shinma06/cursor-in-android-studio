# Cursor Integration Architecture — ACP First

2026-09-09 / ユーザー指定 / [Issue #134](https://github.com/shinma06/cursor-in-android-studio/issues/134)

[最上位ミッション](../project-mission.md)を実現する統合方針。今後は **Cursor AgentをACP経由でAndroid Studio IDE内へネイティブ統合し、CLIを必要に応じて補助利用するPlugin** として設計する。従来の「方式Bを中心に維持し、ACPは採用未決の別候補」という設計方針を更新する。

ACP導入自体を目的にせず、IDE内Agent panelの構造化されたAgent体験を再構築するために用いる。Agents Window再現にはスコープを広げない。

## 統合手段の選択

新しいCursor連携機能では、最初にACPで実現可能かを調査する。原則の優先順は以下とする。

1. ACP標準機能
2. Cursor ACP extensions
3. Android Studio / IntelliJ Platform API
4. MCP
5. Cursor CLI
6. CLI標準出力等の非構造データ解析

この順位は絶対ではない。IDE APIから直接取得した方が正確で高機能な情報は直接扱い、ACPを経由させるための不要な抽象化は作らない。ACPで安全かつ構造的に得られる状態を、新たにCLI表示文字列の推測で実装しない。

CLI専用能力、CLIが適した処理、必要なfallbackにはCLIを継続利用する。ACPとCLIの排他的な設計にはせず、ACP + CLI + IDE APIs + MCPを必要に応じて組み合わせる。なお、Cursor公式のACP入口自体も `agent acp` をstdio/JSON-RPCで起動する。ACP FirstはCLI実行ファイルの廃止ではなく、連携契約の選択である。[Cursor ACP公式](https://cursor.com/docs/cli/acp)

既存方式Bのstream-jsonも構造化されたJSONであり、非構造テキストと一括りにしない。ただし新機能の設計ではACP契約を先に調べ、CLI経路を選ぶ場合は合理的理由と制約を残す。

## 構造化されたAgent体験

ACPをpromptとテキスト応答だけのChat APIとして扱わない。利用可能な状態・イベント・操作を次のUI領域へ対応付ける。

| 領域 | 評価・対応付けの対象 |
|---|---|
| セッション | lifecycle、creation、restoration、モード、継続・停止/cancellation |
| 応答と実行 | streaming responses、tool execution、tool progress、task state |
| ユーザーとの往復 | permission requests、user questions、Plan表示・承認 |
| 作業状態 | Todo、context state、usage state |
| 拡張 | Cursor固有ACP events、将来追加されるACP capabilities |

これは評価対象の一覧であり、すべてがACP標準または現Cursor版で提供済みという意味ではない。標準契約、Cursor拡張、capability広告、installed版での実測、Plugin UI実装・受入を分けて記録する。仕様やcapabilitiesの追加・変更時は利用可能性を再評価する。

ACP標準のsession/updateやpermission要求と、Cursor拡張の質問・Plan・Todo等は別契約として扱う。要求への応答と通知表示を区別し、未対応・拒否・取消・切断時の動作を確認する。Cursor資料内で通知説明とResponse型が併記される箇所は、推測で返信仕様を決めず検証する。[ACP標準概要](https://agentclientprotocol.com/protocol/v1/overview) / [Cursor拡張仕様](https://cursor.com/docs/cli/acp)

## IDEとの直接統合と責務

基本経路は `User → Android Studio → Agent Panel Plugin → Plugin Orchestration Layer → Cursor ACP Agent` とする。Orchestration LayerはUI操作・Agentの状態・IDE操作を調整する責務を表す概念であり、新規の汎用基盤やクラス階層を必須にするものではない。

この調整部分は、必要に応じてAndroid Studio APIs、IntelliJ Platform APIs、Android tooling、Gradle、ADB、Emulator / Device、Debugger、MCP、Cursor CLIとも連携する。IDEから直接取得・操作できる能力はPlugin APIを積極的に使い、Agentへ提供すべき能力はMCP tool等として公開する。ACPは通信境界であり、IDEの能力の上限ではない。

Cursor固有処理、ACP標準処理、Android Studio固有処理、MCP integration、CLI integration、UI処理の責務を可能な限り分ける。既存構造を利用し、必要な接続から段階的に実装する。Cursor/ACPの仕様変更に追従しやすくし、Android Studio統合をCursor内部実装に強く依存させない。

Cursor Agent本体は可能な限りブラックボックスとして扱う。内部harness、モデル制御、コード検索、推論、prompt engineeringを不必要に独自再実装しない。Pluginの役割は公式interfaceを利用するIDE Agent clientの構築である。

## 機能ごとの設計確認と現状

実装時は [必須の競合比較](../project-mission.md#公式仕様と競合比較)に加え、ACP標準で取得可能か、Cursor拡張があるか、IDE APIから直接取得すべきか、MCP toolとして提供すべきか、CLIを選ぶ合理的理由があるか、脆弱な表示文字列への依存を増やさないか、Agents Window専用機能を誤って含めていないかを確認する。

現行の所有関係・送受信・停止・復元の詳細は [現行実装](current-implementation.md)、指示・命名の点検は [監査記録](../development/project-context-audit.md)を参照する。

現在の製品実装は `agent -p --output-format stream-json` を使う方式Bであり、本方針文書によってACP実装済みに変わるわけではない。既存経路の即時編集と事後Diff/Revert、session ID・復元root・停止の安全性、未確認QAを保持し、ACP経路のpermissionを未検証のまま全書込みの事前承認保証へ読み替えない。

[#115](https://github.com/shinma06/cursor-in-android-studio/issues/115)で、ACP Firstを前提に実装契約・互換性・段階的接続範囲を確定する。ACPを第一級に扱う方針は決定済みであり、各capabilityの利用可否と既存機能の移行手順は別途検証する。CLIでしか成立しない範囲は補助経路として残す。#66の命名保留とNew Agent表示は、有効な取得経路の検証が終わるまで継続する。

比較はJetBrains AI Assistant + Cursor ACPにIDE integration / IntelliJ MCP Server / 利用可能なMCP toolsを加えた構成で行う。公式の連携・MCP能力の記述だけで、特定Android Studio版での全機能利用成功を推定しない。[CursorのJetBrains連携](https://cursor.com/docs/integrations/jetbrains) / [IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)
