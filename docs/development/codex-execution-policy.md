# Codex Execution Policy — GPT-6 Astra Subagent Restriction

2026-09-09 / [#135](https://github.com/shinma06/cursor-in-android-studio/issues/135)。ユーザーが指定した開発エージェントの実行規約の正本。ユーザーから明示的に変更されない限り維持する。

## 適用条件

現在のメイン開発モデルが **GPT-6 Astraの場合にのみ**、本規約によるSubagent禁止を適用する。GPT-6 Astra以外のモデルには、この規約による禁止を適用しない。
モデル名または実行環境から現在のモデルを判定できる場合は、その情報に基づいて判断する。判定できない場合は不明と扱い、別モデルだと推測して禁止の対象外にはしない。

本規約は本プロジェクトを開発するCodexの実行方法を定める。製品としてのCursor CLIのSubagent対応・UI実装範囲を変更するものではない。

## GPT-6 Astra使用時のSubagent禁止

GPT-6 Astraがメインエージェントの場合、現在のセッション内部から子エージェントを生成し、タスクを委譲してはならない。推奨ではなく、明示的なプロジェクト制約である。

以下は名称にかかわらず禁止する。

- Subagent、child agent、worker agent、scoped subagentの生成・委譲。
- delegate機能、メインAgentの内部ツールとしてのspawn、Subagent APIによる並列処理。
- parent-child構造、nested agent、recursive agent。
- 「調査担当」「レビュー担当」「テスト担当」等の子Agent生成。

判断基準は、**現在のAgentの子として生成され、親Agentから仕事を委譲され、結果を親へ返すAgentかどうか**。別の名前やAPI、プロセスを使っても、この実行構造ならSubagentとして禁止する。

## 独立Session / Threadは許可する

それぞれが独立したtop-level session / threadとして存在するSession間の連携は許可する。本プロジェクトでは「独立セッション間連携」または「multi-session coordination」と呼び、Subagentとは区別する。

```text
禁止: Main Agent → 子として生成したSubagent
許可: 独立Session A ↔ 独立Session B ↔ 独立Session C
```

必要であれば別の独立Sessionを開始して連携できる。独立したコードレビュー、技術調査、別アプローチの検討、テスト・検証、作業状態のhandoff、設計情報・実装結果・レビュー結果の共有、並行実行、Threadのfork、worktreeによる作業環境の分離を許可する。利用可能な実行環境のSession / Thread間連携機能を使ってよい。

追加のAgentを使う前に、名称ではなく実際の実行構造が独立top-level Session / Threadなのか、現在のAgentの子なのかを確認する。Threadのforkやworktree分離という名称だけでは独立性の根拠にならない。

独立SessionをSubagent禁止の形式的な迂回路として大量生成してはならない。独立レビュー、コンテキストの分離、異なるアプローチの並行検証、長大な調査の分離、worktreeの分離、作業フェーズの分離など、Sessionを分ける合理的理由を持つこと。

## 通常のTool利用と大規模タスク

GPT-6 Astra自身による通常の非Agent Tool利用は許可する。ファイル操作、コード検索、terminal、build、test、lint、Git、Web調査、MCP、ACP、Android Studio API、IDE tools等を利用できる。Toolという名前で子Agentを起動する場合は、上記の実行構造による禁止判定に従う。

独立Sessionを利用しない大規模タスクは、現在のGPT-6 Astra Session自身が次の順で進める。規模の大きさをSubagent使用の理由にしない。

1. タスクを分解する。
2. Planを作る。
3. TODOを管理する。
4. 一つずつ実装する。
5. 各段階で検証する。
6. 最後に統合検証する。

## 実行順序と既存規約との関係

原則として、現在のGPT-6 Astra Session自身で実行し、通常のToolを利用し、必要なら独立top-level Session / Threadと連携する。Subagentは使用しない。

[GitHub workflow](github-workflow.md)のIssue・claim・専用worktree・PR・独立レビュー・target別gateと、[PR自動進行](pr-automation.md)のwriter停止・引継ぎは維持する。一般的な「並列agent」「worker」等の記述を、Astra使用時の子Agent生成許可と解釈しない。独立レビューは、独立したtop-level Sessionで実施する。
実行環境や上位指示による別の権限制約も守る。本規約の許可は、それらを解除するものではない。
