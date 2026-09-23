# 知識の正本と採用条件

#228。唯一の全体入口は[全体設計](README.md)。本書は知識の配置と採用条件を扱い、製品設計・進捗・Case結果の台帳にはしない。照合基準は develop `4d1514d8fa6c020d41ad9c0205b9ea24268bef57`。別branch/SHAを調べた指摘は最新baseとの差を先に確認する。

## 何をどこへ残すか

| 情報 | 正本 / 更新者 | 採用と検証 |
| --- | --- | --- |
| 恒久指示 | [CLAUDE.md](../../CLAUDE.md)（AGENTSは同一symlink） / 規約変更writer | 有効な制約と読む入口だけ。設計詳細・修正日誌を重ねない。必要な承認/安全/互換の意味と適用対象を保持 |
| 方針 | [Mission](../project-mission.md)、[ACP First](cursor-integration.md) / 方針変更writer | 目的と採用済み方針。実装/提供能力/GUI成功の証明とは分ける |
| 現行設計 | [current-implementation.md](current-implementation.md) / 該当実装writer | 対象branch/SHA・実責務・制約・source/test・未確認範囲を示す。固定版を読むときはその版の設計として扱う |
| 構造/安全性を左右する判断 | 下の判断表または既存設計文書 / 判断を変えるwriter | 決定・状態・理由・結果・適用条件・置換先を残す。既に正本がある決定はリンクを使う |
| 証拠 | source/test、[採取fixture](../../src/test/resources/stream-json-fixtures/README.md)、research、[Case JSON](../verification/README.md) / 採取者・検証担当 | 観測、合成、仮説、未確認を分離。版/SHA/build/手順/結果とアクセス条件を記載 |
| 一時的な作業順・試行錯誤・blocker・handoff | 対象Issue/PRのコメント / 当該owner・PM | 過去の全文を別の恒久文書へ丸ごと移さない。有効claim・未達受入・秘密/公開承認待ちは残す |

恒久知識へ昇格する条件は **将来の判断に必要、現在の適用範囲が明確、第三者が根拠または限界を追える** の3つ。『このセッションで直した』だけなら作業記録へ。取得不能な資料はアクセス条件を示し、未確認の結論を検証済みにしない。固定SHAリンクは証拠、branchリンクは現行案内という用途を区別する。

[ADRの原典](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)の決定・状態・理由・結果の要点を、次の重要判断にだけ適用する。1変更1ADRという追加義務や新しい常駐監査は設けない。

## 開発コンテキストの用途・言語・形式と読込条件

#420。新規作成、更新、レビュー、監査に共通して適用する正本。分類基準は「人間が閲覧できるか」ではなく、主用途と誰が理解して判断するか。言語だけで用途を判断せず、配置・入口の説明・読込条件も確認する。製品の仕様や受入基準を変更する規則ではない。

| 主用途 | 言語・形式 | 配置・適用条件・保持するもの |
| --- | --- | --- |
| 人間向け・共有知識 | 日本語Markdown | 方針、設計判断、利用説明、QA、人間向けIssue/PR/報告。Agentも同じ正本を読む。全文英訳を併設しない |
| 開発Agent専用指示 | 明瞭な英語。文章はMarkdown、Skill/ruleは規定形式 | AGENTS/CLAUDE、Skill、rule、開発/レビュー/引継ぎprompt。人間の保守レビューだけでは共有文書に分類しない。常時制約と必要時の詳細を分け、入口に読む条件を示す |
| 機械データ | 既存JSON等とschema | key/ID/enum/完全一致文字列を維持。説明値はfieldごとの用途で日本語/英語を選ぶ。日本語QAを英語promptへ引き渡しても翻訳しない |
| 原証拠・外部管理情報 | 原言語・原形式 | rawログ、引用、採取fixture、wire、過去会話・固定履歴、外部仕様。訳/要約は原証拠と分離し、版と限界を示す |
| 製品実装・UI・runtime prompt・保存・製品tool契約 | 当該製品契約に従う | コンテキスト整理の編集対象外。source/testは知識の根拠として参照可。明示された製品変更は別の実装scopeで扱う |

開発コメント/docstringは用途で判断する。実行時、生成、テスト抽出で使う文字列は説明文と推測して変えない。CLI引数、保存済みID、固有名詞、正確なコード例、製品テスト期待値は翻訳しない。開発/製品が共用し影響を分離できないものは維持し、理由を当該Issue/PRへ残す。外部管理plugin・個人共有設定は影響と提案だけを記録し、無関係なプロジェクトへ適用範囲を広げない。#146、未解放claim、公開承認待ち、PAUSED automationを保全する。

作成・更新時は上の配置表を使い、正本・用途・読む条件・現行/履歴・対象版・編集可否を選ぶ。関連する既存文書/コードの消費側を確認し、重複なら参照へ統合する。移動は旧要件→新配置→維持条件をPRに残し、新経路の到達を確認するまで旧入口を切らない。規定ファイル名、frontmatter、symlink、client別Skill経路、リンク/アンカー、schemaを保持する。全ファイルへのfrontmatter追加や新しい知識台帳は不要。

レビュー・監査時も同じ表で判定する。必須/推奨/禁止、適用条件、例外、権限/承認/所有、互換性、受入と完了条件を比較し、正当な原証拠例外を言語違反にしない。形式、意味、実client検出、実model挙動、資源量は別判定。発見/読込/意味/出力言語/互換性が後退する候補は展開せず、保留担当と再開条件を記録する。

英語化は用途統一であり削減実績ではない。必要な比較ではA=現行、B=同じ言語で整理、C=BのAgent専用部だけ英語化とし、文章単体のB/C差と変更全体の実行差を区別する。対象modelとの対応を確認したtokenizer/公式計数/既存usageだけを使い、文字数をtokensへ換算しない。初回/反復と入力/出力/cacheを区別し、未取得は未測定。費用・subscription枠・電力をtokensだけから推定しない。試験のためだけに認証・課金経路や恒久計測基盤を増やさない。実行比較は同じbase/課題/tools/権限/model設定の新規独立sessionで候補を分離し、模擬Issue/claimと使い捨て状態を使う。各1回は予備評価に留め、失敗/中断/再試行も記録する。

条件付き参照と明確な完了条件は[指定記事](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra)を参考にした。Astra固有のモデル特性を他modelへ一般化しない。計測は[公式token counting](https://developers.openai.com/api/docs/guides/token-counting)と[prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching)を確認する。

### 作成・更新・監査の入口

| 工程 | 同じ正本へ到達する経路 |
| --- | --- |
| 常時/新規/再開 | CLAUDE/AGENTS → 本節。start-work → 本節、workflowの条件別参照表 → 本節 |
| 変更レビュー/終了 | finish-work → 本節、PR templateのContext review → 本節 |
| Cursor | loop-engineering.mdc → 本節。manual-verification.mdc → 本節（共有QAの言語） |
| 開発promptの生成/消費 | agent_worker.pyのreview/fix prompt → 本節。packet/schemaと日本語報告を保持 |
| 今後のコンテキスト監査 | Governance AuditのContext行 → 本節。個人監査Skillは改変せず、project側のAGENTS/監査入口から適用 |

### #420の対象版・配置と処置

基準はdevelop `e938308dd75ca800d8a7a2344554b0f43cb43abc`。移行作業/試行結果/必須保留は[#420](https://github.com/shinma06/cursor-in-android-studio/issues/420)とPRが正本。本表は対象群の境界だけを持つ。履歴は群として分類し、全過去会話を精読しない。

| 対象群 / 正本 | 主用途・読込条件 | 処置・例外理由 |
| --- | --- | --- |
| CLAUDE/AGENTS、両start/finish Skill、Cursor 2 rules | 現行Agent専用。常時/開始/終了/GUIの条件別 | 英語化、条件と参照を整理。symlink・両client経路・alwaysApplyを保持 |
| knowledge、workflow参照表、Governance Audit、PR template | 現行共有規約。文脈の作成/更新/レビュー/監査時 | 日本語の本節を唯一の正本として接続。独立review/権限/完了gateを保持 |
| agent_worker.py → review/fix、agent_loopのpacket/REPORT_SCHEMA | 現行Agent専用prompt、機械データ、日本語報告 | promptは既に英語。本節への参照のみ追加。role境界、生成/消費schema、実行権限は変更しない |
| Mission/要件/architecture/運用/README/調査と開発サンプル | 人間共有。workflow参照表で必要部分を読む | 日本語本文・正確なコード・固有名詞を維持。英語見出し/固定題名はリンク互換のため維持。過去記録は当時の版で解釈 |
| codex-execution-policy / ponytail導入記録 | 人間と共有する実行方針/判断証拠。Agent追加や導入調査時 | 日本語を維持。Astra条件と独立sessionの境界は翻訳対象のAgent専用指示とは別の共有判断 |
| Case JSON/生成Markdown、qa_document.pyの人間向け生成物 | 共有QA/機械処理。対象受入とQA引継ぎ時 | 既存Case/受入/結果/schemaと日本語を維持。#420自身の検証JSONのみ追加。生成Markdownを直接編集しない |
| GitHub Issue/PR/claim/handoff、固定履歴、旧監査#271 | 人間共有/証拠。対象Issue全コメント/必要な関連履歴 | 新規作業記録だけ更新、過去の言語/原証拠/未解放claimを改訂しない。非公開handoffはprivate保管 |
| scriptsの非実行コメント/docstring、CLI help/返却 | 開発者共有または機械契約。該当tool変更時 | 現存英語の短い技術説明はコード同居の保守情報として維持。CLI引数/エラー/JSON/schemaや抽出文字列の一括翻訳はしない |
| 製品source/test/resources、製品prompt/tool | 製品契約。根拠が必要な範囲だけ読む | 対象外。コメントも本変更ではread-only。開発用との共用fixture/入力は原形を保持 |
| 個人AGENTS/記憶/会話要約、外部plugin/Skill/tool説明、検索取得資料 | 外部管理。実際に適用/取得された範囲 | 編集対象外。今回見えた指示と適用範囲をIssue記録で分類し、非公開内容を転載しない。存在/実読込不明は未確認。外部検索内容は権限の追加と扱わない |

## 現在有効な判断と置換関係

| 判断 / 状態 | 理由・代案・結果 / 適用条件 | 根拠・置換関係 |
| --- | --- | --- |
| ACP First / accepted | 構造化された状態・要求返答を公式契約で扱う。CLIのみ継続を設計の固定前提にせず、IDE APIと補助CLIの合理的利用は維持。各能力の実装/実測は個別に判断 | [方針の正本](cursor-integration.md)。旧「方式B中心、ACP採否未定」はsuperseded。旧経路の互換保守を廃止する判断ではない |
| 復元来歴と実終了が不確定なら拒否 / accepted | 後続編集・別root・実行中の書込みを上書きしない。ISOLATEDのpath推測、cancel応答だけでの復元解放は不採用。復元できない場合もDiff閲覧は可能 | [現行の復元/停止](current-implementation.md#復元の安全性)、RestorePolicy/WorkspaceOperationGate/AcpSessionTest。旧「worktree未対応で無保護」はsuperseded、完全な分離先復元は未提供 |
| 本文再表示・provider再開・Revertを別判定 / accepted（保存実装は後続） | 旧metadataだけから本文やACP ID互換を発明しない。全ID共通化/過去tool再実行を避ける。新しい保存契約は#44が所有 | [現行保存](current-implementation.md#イベント補助cli保存) / [#44](https://github.com/shinma06/cursor-in-android-studio/issues/44)。旧TTY picker失敗を全transportの永久的な本文取得不可に広げる説明はsuperseded |

旧状態の証拠と訂正経緯は[固定CLAUDE](https://github.com/shinma06/cursor-in-android-studio/blob/4d1514d8fa6c020d41ad9c0205b9ea24268bef57/CLAUDE.md)および[当時のContext監査](../development/project-context-audit.md)に残る。現行判断へ引用するときは上表の適用条件とソースを先に読む。

## 初回の分類・配置・適用

```text
変更前                                    目標 = 適用後
CLAUDE.md                                  CLAUDE.md
  有効規約 + 現行詳細 + 長い修正/実測履歴       有効規約 + 制約 + 入口 + 固定履歴リンク
architecture/{2設計文書,README.md}          architecture/{同じ3文書,knowledge.md}
  方針/実装と旧source基準                    方針 / 照合済み現行設計 / 全体入口 / 本書
Issue #44 複数の「最新/現在」                 最新案内1つ + 受入/担当範囲 + 折り畳み旧評価
Issue #141 入れ子旧段階                      最新案内1つ + 有効な親受入 + 1つの旧履歴欄
```

| 旧段落群 → 配置先 | 処置と理由 |
| --- | --- |
| CLAUDEのMission、Ponytail、能力比較、GitHub-first、develop/main、日本語UI、製品ID | 維持。複数Agentが必ず知る有効な制約。元の段落を短さだけで削除しない |
| Current blocker | Pro/Teamsの利用条件と即時編集の意味を制約欄へ統合。M0/Freeの経緯は固定履歴リンクへ。通常の現在blockerではない |
| Commands | 必須の共通検証・build・GUI lease・SDK指定を維持。SDKの実接続と過去回避理由は現行実装のビルド節へ。『テストなしだった』訂正履歴は固定版へ |
| Architecture | 詳細の複製を既存current-implementationへ置換。失ってはいけないtoken/EDT/復元/互換/秘密境界は短い制約として保持 |
| Verified CLI behavior | printの即時編集/TTY/モデルID/started推定の制約を現行設計とfixture/要件§13へ集約。版付き採取の全文は固定版へ。観測が全Cursor版の契約になるとは扱わない |
| foundation review、PR #18 review | 「直した順」は固定版へ。起動失敗処理、EDT分離、raw HTML防御、optional Terminal、stale edit保護はsource/testと現行制約に残す。旧Stop全run呼出し/無保護Worktreeの説明を現行から除去 |
| Implementation history vs requirements | 既存機能/要件は要件書、モデルの実ID保持・未確認の画像/名前・本文保存は制約欄。旧buildをinstall済みとする時点情報、MV結果、調整順は固定版/既存runへ |
| architecture 2文書 | 位置は維持。ACP方針の正本と現行source基準/制約テスト参照を明確化。旧SHAで既に修正されたACP未実装という指摘は撤回し、新たな不具合としない |
| Issue #44/#141 | 本文の現行入口を整理し、受入/担当と旧評価を分ける。既存コメント、未達QA、#146公開待ち、#141のPM ownerは保全。製品writerのscopeを取得しない |

repo内の移動はなく、既存ファイルに適切な詳細を集約した。import/package/XML/resources/Gradle/hook/fixtureの参照変更は不要。追加した非実行MarkdownとCase JSONは既存Change Impactで分類する。CLIの意味は知識表に複製しない。

### アクセス条件と未確認

公開source/test/固定CLAUDEはGitHubで追跡可能。Project #2は非公開で閲覧権限が必要。#146の実wire artifactは公開承認待ちで、この整理でもアクセス・公開・検証を代行しない。元ownerを維持し、fake ACPの成功とは区別する。GUIは各QAの識別build/観察記録が必要で、過去のfixtureやPM報告だけでは新しいpassにしない。

## 既存レビューでの受入

該当する設計・保存型・境界・配置を変更するwriterが正本更新を行い、独立reviewerがPR templateの同じレビュー行から、対象SHA・根拠・仮説/未知・superseded先を照合する。repo内リンクとJSONの機械検査は実在/構文だけを保証し、記述の真偽はsourceとのレビューで確認する。全PR全履歴を再監査しない。

初回は入口だけを渡して①ACP実装とGUI受入②復元不可条件③旧履歴互換の3問を独立reviewerが回答する。さらに使い捨ての記述例「このセッションで直した」「旧SHAで見つけたが最新baseで修正済みの指摘」「現在の根拠付き制約」を採用条件で分類し、その判断・根拠をPRへ残す。実文書へ誤情報を入れて試さない。次の関連変更レビューでも同じ条件で再混入を確認する。実運用での改善率・作業時間削減は未測定。
