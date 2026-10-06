# #562のmain限定候補計画

正本は [issue-562.json](issue-562.json)。親は [#485](https://github.com/shinma06/cursor-in-android-studio/issues/485)。本書は計画の読込条件と受入範囲を説明し、結果を記入する別台帳にはしない。

## 2段階と固定する範囲

段階AはGUI不要のmain tooling。製品sourceとactive compatibility policyを維持し、計画・最小gate拡張・GUI試験入力25ファイルを先に用意する。段階BはA統合後に正式割当を受け、最新mainから別branch/worktreeで新candidateを作る。線形履歴、許可path/mode、候補後の2 JSON限定、独立レビュー・4必須checks、同一buildの全Case受入を維持する。

製品の移植元は旧C `ebac56fe441c08a6c869b8be3b1aa906a887c14e`。PCE修正は [PR #553](https://github.com/shinma06/cursor-in-android-studio/pull/553) のmerge `495ddc5ccbf00f01ce4a62ee2dd4d3ad7a62f145` にあるControllerの取消境界と必要testだけ。最新developのController全体や後続#211/#212等をコピーしない。許可pathは233、うち削除7件。具体的移植元と操作はJSONの `path_sources` に固定する。

`docs/architecture/current-implementation.md` は固定mainには存在しない新規文書。固定Cの同文書と実装、現main CLAUDE/運用規約から限定C＋PCEを説明し、最新developへのfallbackを禁止する。CLAUDE/READMEは現mainを基に限定製品の説明だけ更新し、現在の規約を巻き戻さない。

## Case原本と有効な要求

旧131commit・125 merged PRからの394 Case出現とPCEの1件を `case_origins` に原文/hash・PR/merge/path付きで保存する。新しい一意IDは元Issueを接頭辞にする。旧174 Case＋PCE1件の175件が下限で、Computer Use必須4件、artifactのplugin174件・swing_probe1件、Rabbit環境revision対象10件を維持する。新candidateの観察はすべてpendingで、旧履歴は新しいpassにしない。

意味の異なる3件は、原文を削除せず次の適用版を明示する。

| 新Case | 適用する訂正 | 保存する歴史原文 |
|---|---|---|
| `I189-TAB-HEADER-DIVIDER` | [PR #216](https://github.com/shinma06/cursor-in-android-studio/pull/216) のnative button縦寸法と横108 logical px。Rabbitの実寸法を確認する | PR190の旧38px。PR216のQuail4・36px例もRabbitの固定値にはしない |
| `I440-ACP-TERMINATION-DIAGNOSTIC` | [PR #443](https://github.com/shinma06/cursor-in-android-studio/pull/443) の通常ログに本文/秘密/raw error/path/PIDなし | PR442の「診断にpath/PIDなし」をprivate全経路の禁止へ広げない |
| `I440-ACP-PRIVATE-CORRELATION` | [PR #444](https://github.com/shinma06/cursor-in-android-studio/pull/444) のevent別境界。process-started/prompt-dispatchは時刻/event/ローカルUUID/PID/開始時刻のみ | PR443の旧private全項目限定。first failureの有限追加項目は `I440-ACP-PRIVATE-FIRST-FAILURE` で確認 |

privateの本文/秘密/raw wire禁止、0700/0600、128試行上限、property未指定時の不採取を維持する。GUI/provider/authや#146の追加採取枠はこの計画だけで承認されない。

10件の環境対応は現在mainの [rabbit1.json](../environments/rabbit1.json) のrevision `f644eaaf7d4475d923bf9d5bf55767eb1689d9dbdaac38700987ddbe4b5003fa` と、原本Case hash・PR/merge/pathへ拘束する。新candidateに旧migration ancestryを偽装しない。gateと生成一覧は共通の環境対応を使い、欠落/別revision/別runtimeではCase合格を認めない。

## 隔離preflightとcandidate policy

段階Aで固定Cへ局所PCEだけを適用した隔離preflightを実施。標準依存解決のtest/buildPlugin/verifyPluginStructureは成功し、JVM testsは470件、Verifierは同一ZIPの445クラスを完走してCompatibleと判定した。これは正式candidateや実IDE/GUI合格の証拠ではない。

固定ZIP hashは `879b8d92de7332825af0458761ccb9e2eafc43bfe419ffd0d1d246b55108203c`。APIは非推奨17利用（削除予定5）、experimental34、internal9。非推奨/internalのreport hashは旧Cと同一。experimentalの差は同じ `ManagingFS.flushPendingUpdates` 呼出元の生成lambda名/戻り型だけで、利用APIと件数の増減はない。新hashと判断をJSONの `compatibility_policy` へ固定し、独立した固定HEAD/baseレビューを必要とする。後続developの警告許容を流用しない。

段階Aではこのpolicyは未使用の計画データ。段階Bで対応製品sourceと同時にcandidateの `scripts/workflow/plugin_compatibility.json` へ配置する。既存Verifier/RC/CIの通常読込を維持し、新candidate/ZIPで全class・依存・API hash・SDK/JBR/hashを再検査する。

## fixtureと残条件

GUI試験入力25ファイルは元C由来。`defer/instrumentation.patch` だけはC＋局所PCE向けに準備workerを追従させた。旧mainへ適用せず、試験専用のinstrumented buildと正式ZIPを区別する。既存fixtureの使い方・対象source・観察の限界は各READMEを参照する。

旧#485の3source/branch・原Case/ZIP/観察、QA、owner/registry/PAUSED、GUI保留を維持する。段階Aの統合だけで#562/#485やM6を完了にしない。旧C履歴のSHA特例は追加せず、既存#534/#536の履歴保護・回帰を維持する。追加GUI差が判明した場合だけ、先にmain toolingで計画を改訂してから新candidateを固定する。
