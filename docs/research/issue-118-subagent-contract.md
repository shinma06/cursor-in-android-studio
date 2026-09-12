# #118 SubagentのACP・print契約調査

2026-09-12 / 調査base `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。

調査中。製品Cursor Subagentの通知/標準toolとの対応とprint親子ID/終端を最小の合成実測で確認する。
開発Agentの委譲、Cloud、GUI、認証変更、#117停止sourceと#146原本は対象外。

## 既知の入口

- [Cursor ACP](https://cursor.com/docs/cli/acp): cursor/taskは通知と説明されるがResponse型も併記。実wireなしでspawn/resume APIとしない。
- [ACP Tool calls](https://agentclientprotocol.com/protocol/v1/tool-calls): session内toolCallIdと部分更新/状態。
- [Subagents](https://cursor.com/docs/subagents) / [CLI changelog](https://cursor.com/docs/cli/changelog): foreground/backgroundとheadless完了待ち、再開の公開能力。
- #115 K15/T15、#98共通表示、#44永続ID、#254本文正規化を引き継ぐ。#98の全体進行は本調査待ちにしない。

## 最小実測計画

使い捨てGit repository内のreadonly custom agent1定義と合成テキストだけを使う。
ACP1回とprint1回で親子対応/終端を採取し、追加のcancel/resume/失敗条件は実測の不足を見て限定する。
資格情報や既存会話を入力しない。rawはprivate、公開投影とwriterの原本照合を区別する。

## 受入の記録予定

親子ID/標準tool対応、開始/進捗/終端/前景背景/再開、表示と停止/不明状態、usageと時間の非重複、
専用実装先・未観測契約と必要証拠、固定GUI Case案を対応付ける。
