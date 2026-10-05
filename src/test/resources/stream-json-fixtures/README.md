# stream-json fixtures

CLI `2026.09.02-c22c1a3` の `agent -p --output-format stream-json --stream-partial-output --trust` で採取したprint出力を、parserの回帰テストに使うため匿名化したJSON Linesです（M0/M4/M5）。

#540で作業場所を表す`cwd`と、session/request/conversation/tool/model-callの実識別値を固定の合成値へ置換しました。同じ識別子の繰返しと参照関係を保っています。元のイベント順序・件数・重複、項目と値の型、本文・diff・usage・model・時刻は維持しています。既存のedit用pathは試験用の表記です。原本と置換対応は非公開で保全しており、この派生fixtureを無加工の生ログとは扱いません。

対象はprint transportだけです。ACP JSON-RPCやGUIの受入を証明しません。tool fixtureはcompleted payloadの採取であり、startedの項目は実採取による確認が必要です。履歴や過去配布物に残る原情報は監査#512の別の確認対象です。

## Files

- `01_plain_question.jsonl` — assistant応答前のFree-tier部分採取。当時の`resource_exhausted`に至る条件の記録であり、現在のPro/Teamsのblockerではありません。
- `02_edit_completed.jsonl` — diff metadataを含むcompleted `editToolCall` 1件（Teams plan）。
- `03_shell_completed.jsonl` — stdoutを含むcompleted `shellToolCall` 1件（Teams plan）。
- `04_plain_question_success.jsonl` — init、thinking、assistant、resultを含む成功経路（Teams plan）。
