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
