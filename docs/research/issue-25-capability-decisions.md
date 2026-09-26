# #25 未確認能力の有限調査と採否

2026-09-26。[Issue #25](https://github.com/shinma06/cursor-in-android-studio/issues/25) の2026-09-10固定範囲と、[2026-09-19全項目対応表](https://github.com/shinma06/cursor-in-android-studio/issues/25#issuecomment-5646699438)の12行・補足4行・関連1行を照合した。製品sourceの基準は develop `c178c6faa444886e92864318cde134368c403b79`。これは採否を再判断するときに読む共有研究記録であり、進捗の正本は各Issue/PR/QAである。

**列挙済み項目の判定・採否・実装先または次の必要証拠を以下で確定する。未確認能力の実装、製品GUI、main反映の完了は宣言しない。** 親自身の調査受入を照合した結果であり、子11件がclosedであることだけを完了根拠にしない。独立レビュー・統合・本資料のmain引継ぎは本PRとIssueの記録で別に確認する。

## 判定と証拠の範囲

- **対応確認（公式）**は公開契約があること、**対応確認（実測）**は記録されたCLI/model/入力で観測したこと。どちらもPlugin GUIの合格ではない。
- **非対応**は対象版・経路の明示的制限または現Pluginの実装境界に限定する。**未確認**は存在しないという意味ではない。help不掲載、空一覧、広告だけから成功/非対応を推測しない。
- **採用**は既存の専用実装先を維持、**保留**は条件成立後にPMが個別採用を判断、**初回非採用**は現scopeへ実装しない判断。保留候補を新Issueへ大量複製しない。
- 原証拠は既存研究・fixture・固定PRへ残す。以下の相対リンクは本書と同じcheckoutの資料を指し、各資料冒頭の実測版/SHAを尊重する。source説明の「現状」が研究当時のものである場合、後続実装/QA欄を優先する。

| 基準 | 確認済みの範囲 / 限界 |
| --- | --- |
| 旧UI観測 | [9/5調査](cursor-agent-ui-survey-2026-09-05.md)のUI-03、05–08、12、15、20、22–31、35、42–46を入口として保持。premium案内は表示観測だけで、契約全体やCLIアカウントとの同一性は未確認。旧画面を再公開/現在の既定値へ転用しない。 |
| 固定IDE版 | [9/8能力表](cursor-agent-capability-matrix-2026-09-08.md)のCursor 3.19.13と、[9/10メニュー調査](cursor-agent-menu-ux-2026-09-10.md)の3.19.19 / commit `6496ea8a068aebfcd21990e70ff522e9abf10c80`は別観測。後者も未取得popup/描画があり、全画面観察ではない。今回のインストール版・表示プランは再確認していない。 |
| CLI実測 | 主な子研究は `2026.09.10-fd3934a`。画像/goal/Request IDは `composer-2.5[fast=true]`、Skills/子TaskはAutoまたは広告初期値、usageは同Composerと `gpt-5.3-codex[reasoning=medium,fast=false]`。詳細・mode・回数は各原資料。新しい版/modelへ成功を一般化しない。 |
| 公開仕様の再確認 | 9/26にCursor ACP・slash・output-format・permissions・Enterprise deployment、Cursor/JetBrains連携を閲覧。Web資料の現在記述を固定CLIでの新実測としない。その他は子研究が確認した日付付き根拠を再利用。 |
| 今回の操作 | 公開資料・GitHub管理情報・既存sourceの読み取りのみ。provider prompt、GUI、録音、認証/設定変更、#146原本読取は0。Pro/Teams前提を維持し、旧Free制限を再開障害にしない。 |

## 全17項目の判定・採否・次の証拠

表中のQAは本書作成時点でopen。研究QAのmain追跡と、製品QAの実観察は別である。

| ID / 元項目 | 判定・根拠 | 採否・既存の接続先 | 未確認部分に必要な証拠 / 引継ぎ |
| --- | --- | --- | --- |
| C01 画像path入力 | **対応確認（実測）**: ACP bytesで識別18マス、print pathも限定成功。[#10](issue-10-image-contract.md) / PR275。日本語path改変・未追跡tempとISOLATEDの失敗も保持 | ACP画像1枚を**採用**: #277 / PR323。print自動fallbackは**初回非採用** | 添付/preview/失敗保持/切替の製品I1–I8はQA369。研究mainはQA368。画像入力成功で出力mediaを合格にしない |
| C02 Skills | **対応確認（実測）**: ACP広告/明示実行、print日本語・空白・resume・Worktree・context前置。[#117](issue-117-skills-contract.md) / PR256 | 1メッセージの広告候補選択を**採用**: #258→QA345。研究mainはQA342 | 全探索優先順位、plugin由来、動的削除、Custom Mode持続は**未確認・保留**。scope/競合・解除・idle通知の固定fixtureとGUI/IMEを確認して#26で再判断 |
| C03 Subagents | **対応確認（実測）**: 前景子と同ID結果、print子error。背景・親Stop後の終了・再開成功は**未確認**。[#118](issue-118-subagent-contract.md) / PR262 | 既知子Task情報/成否を既存toolへ結合: #263→QA357。研究mainはQA350。子spawn/resume操作・全文遡及・独自監視は**初回非採用** | #118 Case5の背景条件/所有process静止/返された子IDと保持contextによる再開成功。親successを子成功にしない。拒否を迂回せず提供状態変化後に再評価 |
| C04 streaming | **対応確認（公式・実測）**: print delta/flushとACP deltaの違い。[#116](issue-116-stream-json-contract.md) / PR252 | 正常化は#254 / PR264→QA325、境界表示はQA246 | 固定buildで重複/欠落・途中tool・Stop/遅着を照合。研究で再現した旧不具合を製品正常化のpassへ転記しない |
| C05 双方向transport | **対応確認（公式・実装）**: ACP First、new/prompt/要求応答/取消。[#115](acp-feature-migration-2026-09-09.md) / PR145、#147 / PR151 | 新規会話で明示ACP選択を**採用**。transport自動fallbackなし | #146の公開可能な実wireとQA152の固定build受入が必要。原本・公開承認待ち・ownerを維持し、この表で公開や成功を代替しない |
| C06 Debug | IDEとinteractive `/debug`は**対応確認（公式）**、専用ACP/非TTY起動・計測寿命は**未確認**。[#281](issue-281-debug-contract.md) / PR283 | 専用UI/計測cleanupは**保留**。通常Agentの修正と区別。研究mainはQA382 | D1の専用公開入口/広告→D2–D4の合成bug・要求相関・所有計測だけの削除/Stop・D5実IDE比較。通常修正成功や全file Revertで代替しない |
| C07 構造化Plan/質問/ToDo | 標準Plan、blocking選択質問/Plan承認は**対応確認（公式・実装）**。自由入力・Plan編集→Build・CursorTodoは現Pluginで**非対応**、providerとの安全な拡張契約は**未確認**。[#115](acp-feature-migration-2026-09-09.md)、[Cursor ACP](https://cursor.com/docs/cli/acp) | 既存Plan表示・選択ID返答を維持: #147→QA152。追加UIは**保留** | 下のC07詳細。自由入力field/返答、planUriの所有と読込、Todoのrequest/notification・取消/遅着を公開/許可済みfixtureで確定後に個別採用。#146の初期wireと重複実測しない |
| C08 queue/steer/side question/goal | queueは**対応確認（実装）**。goal広告と単発応答は実測、mid-turn/永続goal状態は**未確認**。[#278](issue-278-midturn-contract.md) / PR280 | ローカルqueue #48→QA340、広告slash #258を維持。steering/side/goal専用UIは**保留**。研究mainはQA381 | R1–R5: 公開入力method、受付/帰属/終端、履歴、pause/resumeと固定GUI比較。Stop後再送・同時prompt・単発応答で代用しない。IDE `/side`とCLI `/btw`の保存範囲は別 |
| C09 会話title/rename/fork | interactive入口は**対応確認（公式）**。ACP空list成功はtitle/更新成功ではない。fork/再開の実提供は**未確認**。[#301](issue-301-hooks-workspace-contract.md)、#66 | #44の本文再表示→QA259を維持。titleは#66の**保留**、fork/Side chatは#26の**保留**。New Agentを保持 | 実title/変更event・対象ID、fork起点/複製境界/新ID、load/replay/resume/root一致のC5。代替名生成・本文連結による擬似forkなし |
| C10 権限/allowlist/sandbox | 公開flag/config/permission応答は**対応確認（公式）**。実効policy/空設定/認証carrierは**未確認**。[#297](issue-297-run-mode-contract.md) / PR298 | 説明整備 #300→QA361を**採用**。enum/既定/ACP拒否を維持。独自allowlist editorは**保留**。研究mainはQA360 | P1–P3の共有/次回適用/選択IDとGUI、P4/P5のauth-required・Fetch/MCP対象、user/project/team競合/empty policy/保存scope。事前確認や全tool sandboxを保証しない |
| C11 Web/Browser/画像生成/MCP rich result | engine能力と標準content型は**対応確認（公式）**。Cursorからの実media/carrier/生成相関は**未確認**。[#290](issue-290-rich-tool-results.md) / PR292 | 自動画像取得抑止 #295→QA355、有限metadata #296→QA358を**採用**。preview/再生/保存/Browser操作/生成extensionは**保留**。研究mainはQA353 | W1–W6の状態/置換/上限・自動取得なし・明示media操作・実carrier・生成物相関。#157の手動BrowserはAgent操作成功ではない。MCP専用engineやHTML実行hostは追加しない |
| C12 plugin/hooks・multi-root/worktree・persist | 公開入口/追加root契約は**対応確認（公式）**、現在の単一rootとISOLATED復元拒否は**対応確認（実装）**。[#301](issue-301-hooks-workspace-contract.md) / PR303 | ISOLATED説明 #305→QA363を**採用**。追加root/setup/persistは**保留**、独自hook runnerは**初回非採用**。研究mainはQA362 | C1–C6の実効scope/reload、追加root全配列、実隔離root、setup失敗/取消/cleanup、provider再開/子process寿命。#26採否と#259/#102の既存QAを維持 |
| C13 補足 @/明示context・Customize | context snapshotとSkillsは**対応確認（実装）**。全Custom設定の出所/優先順は**未確認**。[#117](issue-117-skills-contract.md)、[#301](issue-301-hooks-workspace-contract.md) | #24→QA341、#258→QA345。その他Customizeは#26で**保留** | 送信対象/別tab/draft/IMEの固定buildと、C2のscope・読込・失敗契約。ファイル一覧をproviderの実効catalogとしない |
| C14 補足 Request ID | 正常printの `result.request_id`は**対応確認（公式・実測）**。同sessionで更新する。[#284](issue-284-request-id-contract.md) / PR286 | 正常終了だけ明示copy #289→QA365を**採用**。研究mainはQA364。ACP供給は**保留** | Q1–Q6: 失敗/Stop/遅着/欠損/別tab、ACPの診断値供給と相関。copy command広告を構造化取得とせず、RPC/session/tool IDで代用しない |
| C15 補足 音声 | OS dictationとCursor mic、ACP音声入力は別。OS入力は**未確認**、固定CLI広告はaudio=false（その接続では**非対応**）。[#99](https://github.com/shinma06/cursor-in-android-studio/issues/99)、[#278観測](issue-278-observations.json) | OS標準機能を先に確認する既存#99へ。独自録音/送信機能は**初回非採用** | 指定GUI担当・lease・識別build・録音内容/送信先を確定し、開始/停止/訂正/IME/誤送信/実行中下書きを観測。未実施のまま維持 |
| C16 補足 アカウント/Privacy共有・Agent統計 | Privacy Modeのdesktop/CLI同等適用は**対応確認（公式）**。実アカウント同一性/値取得、Agent Stats算定は**未確認**。下記補足と[#149](issue-149-usage-contract.md)、[#297](issue-297-run-mode-contract.md) | 未提供状態/print counters #287→QA349を維持。アカウント切替/Privacy同期UI・統計計算は#26で**保留** | 公開status/readback、適用account/teamと秘密を含まない値、統計の母集団/期間/重複/出所が必要。#149の実used/size/cost・数値由来・固定build受入は残る。研究main QA348、表示QA106を維持 |
| C17 関連 Android IDE最強構成 | SDK/比較手順は部分成果。実IDEでの優位性・採用機能は**未確認**。[#150](https://github.com/shinma06/cursor-in-android-studio/issues/150) / PR265 | #150の独立担当へ。資料mainはQA380。今回採用機能や合格を決めない | fixture正本受領、対象SDK再固定、同project/CLI/modelの3経路、2module×2Variant×2deviceと10 Case、最初の1機能選定。本文に実観察を要求する#150は未確認表だけで完了にできない |

### 旧UIの細目を対応表から落とさない

- UI-03の専用`Multitask` modeは**未確認・保留**。C03の子Task、複数tab並行run、Cloud/AWの並列操作はそのmodeの成功証拠ではない。C06と同じ固定CLI広告はagent/plan/askのみ。専用modeの公開ID・受付/親子/終了契約が揃った時にPMが再評価し、`--mode multitask`を発明しない。
- UI-22の自動mode遷移承認は**未確認・保留**。C07/C10の通常質問/手動mode変更と分け、遷移要求・応答ID・未回答timeout・取消を固定fixtureで確認するまで、旧画面の15秒を製品へ実装しない。
- UI-23/43の第三者config/CustomizeはC02/C12/C13。読み込めるpathと実効設定は別で、importや自動実行を追加しない。UI-25のMCP認証待ちはC10のP4/P5であり、旧30秒を現在のdefaultへ移さない。
- UI-35のTerminal内編集/previewはC13のcontext読取と異なる。#26のQuick Edit/Terminal編集**初回非採用**を維持し、既存IDE editor/MCPで解決できない具体的フローと公開契約が実証された場合だけ再評価する。
- UI-45/46の復元/Worktree/承認/エラー画面はC05/C10/C12と既存QA152/#40/#102/#104へ対応する。新しいGUI観察なしという限界を保持し、表示取得不能を機能不存在としない。

### C07: 質問・Plan・Todoの未確認を分ける

9/26の[Cursor ACP](https://cursor.com/docs/cli/acp)は選択質問とPlan承認をblockingと説明する。質問responseは `selectedOptionIds`、Planはaccepted/rejected/cancelledと任意 `planUri`。自由入力の返答fieldや「編集後Build」の実行成功はここから確定しない。Todo/task/画像はnotification説明とResponse型が併記されるため、型名だけで返信しない。

基準source [AcpProtocol](../../src/main/kotlin/com/cursoragent/acp/AcpProtocol.kt) は標準`plan`を表示し、permission/選択質問/Plan承認を型付けする。`cursor/update_todos`用表示・自由入力は接続されていない。必要証拠は、(1) 実carrierと要求ID/通知の区別、(2) option以外の入力・skip/cancel/切断時の正規応答、(3) 編集planのURI・内容・読取権限・Buildへの引渡し、(4) Todo ID/状態全置換・終端後遅着。確定するまで既存UIを拡張しない。blocking質問を「回答待ちでも同じAgentが作業継続する」と説明しない。

### C16: Privacyの公式方針と、未取得状態の区別

[Cursor Enterprise deployment](https://cursor.com/docs/enterprise/deployment-patterns#cursor-cli-considerations)はdesktopとstandalone CLIへPrivacy Modeが等しく適用されると説明する。したがって「CLIへのPrivacy適用方針そのものが未確認」という旧評価は、この公式確認へ更新する。一方、当該利用者が同じaccount/teamで認証していること、現在値のPlugin取得/変更API、全IDE設定の継承を証明したものではない。新しい設定同期UIは採用しない。

旧UI-42のAgent Stats `0/0 (0%)` / `No commit scored`と、print token counters、ACP used/size/cost、アカウント残枠は異なる量である。#149の未観測usageを0扱いしたり、token数から採用率・課金・残量を計算したりしない。算定定義・公開field・scope・期間・重複排除を確認できるまで統計表示は保留。将来実測の判断先は#26、数値表示の既存受入は#149であり、#149へ未要求のaccount管理実装を追加しない。

## 元の受入6項目との照合

| #25本文の受入 | 本調査で満たす証拠・判断 | 製品側に残す受入 |
| --- | --- | --- |
| 1. version/表示プラン、@/Skills/Customize/Worktree/承認/エラー/復元の未確認画面 | 上の固定基準、C02/C10/C12/C13。閲覧不能/入力必要な画面は未確認として理由を残すという原条件を適用。旧観測のプラン案内をアカウント同一性へ広げない | QA152/341/345/361/363、既存#40/#102/#104/#107。全画面を新規観測したという受入ではない |
| 2. Debug/Multitask/Skills/分岐/割込みの非対話契約 | C02/C03/C06/C08/C09。公開・実測・必要event・未提供を区別 | D1–D5/R1–R5/C5と既存#26/#66。未知flagや代用品を採用しない |
| 3. Run Mode4択/allowlist/保護/Web/認証 | C10/C11。旧4択を現在へ移植せず#297の層別契約とP1–P5を採用判断に使用 | QA360/361/355/358、認証/実効policyは不足証拠の条件成立後 |
| 4. 既存ログ/fixture/公式優先、安全な追加実測 | 各固定研究を再利用。今回provider/GUI実測なし。原本の匿名化や新公開もなし | 新しい実測は必要証拠に対応した有限fixture/版/許可scopeでのみ再開 |
| 5. 画像/音声、アカウント/Privacy、Agent統計 | C01/C15/C16。アイコンでは判定せず、音声は#99、数値は#149。Privacyの公式方針を限定的に更新 | #99/#149をopenのまま維持。QA348/349/368/369のmain/GUIは別 |
| 6. 対応/非対応/未確認、採否と実装分割 | 全17行で判定・採否・実装または必要証拠を明示。既存採用先を再利用し、未確認機能を自動実装しない | 採用済み製品の各QA。未採用はこの固定研究と#26等の既存判断先から再評価 |

旧「完了の記録」3項目は、調査のみのため証跡/リンク検証と共通Change Impact、既存Case/QAへの引継ぎ、Issue/親#19の完了記録で扱う。製品変更のない本差分にGradle/ZIP/GUI passを要求も主張もしない。文書レビュー・統合・本資料のmain追跡・#19のreadbackが終わるまでは運用上の完了ではない。

## 競合比較と完了境界

[Cursor JetBrains連携](https://cursor.com/docs/integrations/jetbrains)と[JetBrains AI Assistant ACP](https://www.jetbrains.com/help/ai-assistant/acp.html)を9/26に再確認した。Cursor側の有料plan条件と、JetBrains AIサービスsubscriptionなしでもACP利用可という条件は別。custom MCP、IntelliJ MCP、tool選択を含む構成を比較対象に保つ。接続・質問・Skills・MCP tool利用自体を独自機能と呼ばない。

本Pluginの採用範囲は、IDEのtab/draft/context/対象rootと受信eventを一致させ、日本語で確認済み/未提供/失敗を区別すること。競合より良いUXという実測結論は出していない。Android固有の選択対象と実操作の比較は#150が所有し、その残受入を本研究へ取り込んで完了にしない。

#25の有限な調査完了は、未確認をすべて解消した状態を意味しない。本文の固定範囲に従い、理由と再開に必要な証拠・採否を確定した状態である。GUI/mainの残務は各QA、#146の公開承認と初期wire、#66の名前、#99の音声、#149の数値/表示、#150の実IDE比較は各元Issueと既存ownerに残る。親#19、#141、Milestone3を同時に終了しない。
