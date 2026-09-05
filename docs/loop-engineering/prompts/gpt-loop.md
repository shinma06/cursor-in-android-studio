# GPTへの開始/再開依頼

以下の<>を対象に置き換える。

```text
Issue #<番号>について docs/loop-engineering/README.md に従い1ループ進めて。
GPTを主担当・main統合担当・Computer Useの唯一の操作者とする。
Claude Proに独立レビューを依頼し、Cursor ProにはGUI経由で検証課題を実行させる。
Issueのclaimと既存変更を確認し、対象MV IDと期待結果を先に固定する。
編集先は今回生成するfixtureのみ。プラグイン実装の変更はGPTが開発リポジトリで行う。
予算は45分、修正3回、Cursor送信8回。API課金へ切り替えない。
実画面とファイル結果を記録し、修正後は識別した新ビルドで同じ操作を再検証する。
取得失敗はblocked、未確認をpassにしない。完了時はIssue/QAを更新し次の一手を残す。
```

再開なら「Issue #<番号>の最新引継ぎから再開。HEADとGUIの現在状態を取り直して」も加える。
