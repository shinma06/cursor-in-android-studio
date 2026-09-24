# 知識の正本と採用条件

#423。#420 / PR #421で採用した開発コンテキスト規則のmain側正本。共通規則の本文と参照節は同一に保つ。main基準は `67348d75264254e39e964915bfc66a98f227764d`。develop固有の製品実装や履歴整理をmainの現在状態として取り込まない。

## 何をどこへ残すか

恒久指示は[CLAUDE/AGENTS](../../CLAUDE.md)、方針は[Mission](../project-mission.md)と[ACP First](cursor-integration.md)、製品の要件は[要件書](../cursor-agent-plugin-requirements.md)、進捗/一時的な作業順/レビューは対象Issue・PR、Case/固定候補の証拠は[verification JSON](../verification/README.md)が正本。既存の正本を複製しない。

恒久知識は将来の判断に必要で、適用範囲と対象版が明確、根拠または限界を追えるものに限る。原証拠、仮説、合成、未確認は区別する。固定SHAはその版の証拠であり、他branchの現在状態を証明しない。変更時は参照先と消費側を確認する。

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

## mainへの限定反映と旧要件の保持

元の開発規則は[#420](https://github.com/shinma06/cursor-in-android-studio/issues/420)、main反映は[#423](https://github.com/shinma06/cursor-in-android-studio/issues/423)、残受入の正本は[QA #422](https://github.com/shinma06/cursor-in-android-studio/issues/422)。試行結果と統合SHAはIssue/PRで追跡する。恒久的な試験台帳を追加しない。

| 対象 | mainでの配置・保持 |
| --- | --- |
| CLAUDE/AGENTS | Agent専用の英語指示と条件付き参照。symlinkと旧アンカーを保持。Mission/ACP設計、GitHub所有・gate、UI日本語、製品ID・print/復元/解析/EDTの制約を残す |
| 両client start/finish・Cursor 2 rules | #420と同一本文。frontmatter、既存パス、alwaysApplyを保持。製品未確認をpassにせず、GUI時だけ詳細を読む |
| 本書・workflow参照表・PR template・Governance Context | 作成/更新/レビュー/監査は本書の同一規則を参照。共有本文は日本語。mainの#408限定候補導線を保持 |
| loop/review依頼とagent_worker | #420の共通本文/参照を再利用。人間向け説明と報告は日本語、Agent本文は英語。予算・権限・packet/schema・tool無効reviewを保持 |
| main製品状態とbuild | printが既定、ACPはdevelop別記録。旧履歴はmetadata、本文永続化はmainへ取り込まない。JDK21/Quail1固定SDK、local SDK明示opt-in、#408の限定取り込み・同一ZIP公開を維持 |
| 固定履歴/採取fixture/製品試験入力 | [変更前CLAUDE](https://github.com/shinma06/cursor-in-android-studio/blob/67348d75264254e39e964915bfc66a98f227764d/CLAUDE.md)に元の説明と時点別結果を保持。語句・ID・rawデータを翻訳しない。旧MV/runは履歴、現行Case JSONを二重編集しない |
| 製品source/test/resources/runtime prompt・外部plugin/個人設定 | 本反映では編集対象外。根拠として必要な範囲だけ参照し、秘密・#146の公開承認待ち・既存ownerを保全 |

mainとdevelopで製品状態や固定履歴の記述が違うことは正常。共通規則・client入口・開発promptの意味が一致する限り、この限定反映だけを理由に製品差分やmain専用の過去記録をdevelopへ重複同期しない。後のpromotionで必要なDAG/Case照合は既存手順に従う。
