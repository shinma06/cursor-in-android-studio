# Project Mission, Scope and Competitive Baseline

2026-09-09 / ユーザー指定 / [Issue #134](https://github.com/shinma06/cursor-in-android-studio/issues/134)

本書をプロジェクト全体の最上位判断基準とする。詳細要件・過去の調査や計画と異なる場合、目的・スコープ・比較基準は本書を優先する。統合方式は [ACP First](architecture/cursor-integration.md)、機能別の実装状況は [要件定義](cursor-agent-plugin-requirements.md)、進捗・受入はIssue/PR/QA記録を参照する。これは目標の定義であり、機能実装済み・競合と同等・GUI合格を宣言するものではない。

## 目的と対象

CursorのVS CodeベースIDE内で利用できる **Agent panelの機能と開発体験をAndroid Studio Pluginとして実現する**。IDE部分をAndroid Studioに置き換え、Cursor Agentを同じIDE内で利用する。独自AIエージェントを開発するプロジェクトではない。

再現・拡張する対象は **IDE内のAgent panel** である。エディタ、ファイルツリー、ターミナル、Diff、IDE状態とAgentが連携する開発体験を、Android Studio内に持ち込む。

| 利用形態 | 本プロジェクトでの扱い |
|---|---|
| Cursor IDE + IDE内Agent panel | 機能・操作フロー・フィードバック・IDE統合の基準 |
| 独立したAgents Window | エージェント主体の別ウィンドウ。独立UIそのものは再現対象外 |

Agents Window専用UI、独立したAgent-first workspace、同ウィンドウ内ブラウザ、専用Design Mode、同ウィンドウの複数Agent管理UIは、IDE内Agent panelの実現に必要でない限り対象にしない。「Cursorに存在する」だけでは採用せず、IDE内panelに属する機能かを必ず判断する。両方に存在する能力は、IDE内panelで必要な範囲を個別に評価する。

## 再現すべき開発体験

Cursor IDE内Agent panelの主要な体験について、可能な限り同等以上を目指す。

- Agentとの対話、Agent / Ask / Plan等のモード、コードベース理解、ファイル検索・参照、選択コード・開いているファイルのコンテキスト。
- ファイル・複数ファイル編集、Diff表示、変更レビュー、ターミナルコマンド実行、ビルド・テスト、MCP tool実行。
- tool callの進捗、permission request、ユーザーへの質問、Plan表示、Todo / task progress。
- セッション管理、checkpoint / rollbackに相当する変更管理、Agentの停止・継続、コンテキスト管理、IDEとAgentの状態共有。

ピクセル単位のコピーは目的ではない。再現するのは機能、操作フロー、フィードバック、IDEとの統合体験であり、日本語・アクセシビリティ・IDE標準操作との整合も保つ。

## 公式仕様と競合比較

機能仕様のPrimary Referenceは **Cursor公式の最新IDE内Agent panel** とする。新機能を設計・実装するときは最新の公式仕様を確認し、古い仕様・過去UI・第三者の再現実装を基準にしない。公式変更時は関連要件を再評価する。過去の調査は日付・環境を持つ証拠として残し、現仕様の保証には使わない。

競合ベースラインは、利用可能な範囲で最も強い **JetBrains AI Assistant + Cursor ACP + IntelliJ / Android Studio integration + IntelliJ MCP Server + MCP tools** とする。本Pluginは少なくともこの構成で利用できるCursor関連機能を包含し、その上でより優れたAndroid Studio統合を提供することを目標とする。

新機能の設計・実装では、必ず次を確認し、対象Issueまたは設計資料に記録する。

1. Cursor IDE内Agent panelでは何ができるか。
2. JetBrains AI Assistant + Cursor ACPでは何ができるか。
3. JetBrains側のMCP / IDE integrationと利用可能なtoolsを含めると何ができるか。
4. 本Pluginで同等以上のUXをどう実現するか。
5. Android Studioとの直接統合で、さらに何を提供できるか。

比較には対象版・構成・公式根拠・実測範囲・未確認事項を添える。競合に既にある機能を独自の差別化として扱わない。未確認は `[要検証]` とし、MCP等の存在だけで全機能が利用可能とも断定しない。既存の比較調査は [#115](https://github.com/shinma06/cursor-in-android-studio/issues/115) を利用する。

## Android Studioによる差別化と完成像

IDE内Agent panelのAndroid Studio版を実現した上で、Cursor IDEでは取得・操作しづらく、Android Studio Pluginなら直接扱える情報・機能によって競争優位を築く。候補には以下を含むが、採用は技術的実現可能性と上記の競合比較を検証して決める。

- Android Emulator、Physical Android Device、ADB、Logcat、install / launch / stop。
- Gradle、Build Variant、Run Configuration、Debug Configuration、APK / AAB。
- Android Debugger、Breakpoint / runtime state、instrumentation test、UI test。
- Compose Preview、Layout Inspector、App Inspection。
- Android Resources、AndroidManifest、Navigation。
- IDE inspections、IntelliJ PSI、refactoring API、IDE内部の構造化されたプロジェクト情報。

完成像は、**Cursor IDE内Agent panelの能力をAndroid Studioへ移植し、Android開発についてはCursor IDE本体より深くIDE・ビルド・デバイス・実行環境を理解し操作できるCursor Agent環境** である。
