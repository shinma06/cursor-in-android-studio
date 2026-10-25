# stream-json fixtures

CLI `2026.09.02-c22c1a3` の `agent -p --output-format stream-json --stream-partial-output --trust` で実採取した出力です。parserのunit test（M0/M4/M5）に使用します。

各ファイルは1行1イベントのJSON Linesです。記載されたpathの置換を除き、CLI出力を保持しています。対象は当時のprint出力で、ACPや現在のGUI受入を証明するものではありません。

## Files

- `01_plain_question.jsonl` — assistant応答前に`resource_exhausted`となったFree-tierの部分採取。当時の条件の記録です。
- `02_edit_completed.jsonl` — diff metadataを含むcompleted `editToolCall` 1件（Teams plan）。
- `03_shell_completed.jsonl` — stdoutを含むcompleted `shellToolCall` 1件（Teams plan）。
- `04_plain_question_success.jsonl` — init、thinking、assistant、resultを含む成功経路（Teams plan）。
