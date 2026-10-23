# コメント・Docの契約

コード・コメント・Doc、例、設定/schema、作業入口を変更・レビューする担当者の共通正本です。通常は今回の差分と影響先に適用し、関係のない全Docの読込みや全既存実装への一括適用・coverage調査は要求しません。全既存適用が明示された場合は末尾の条件付きcoverage規則に従います。[Mission](../project-mission.md)・[ACP First](../architecture/cursor-integration.md)、製品要件、所有・承認、検証の契約は維持します。

## 役割と置き場所

コードは動作を、局所コメントはコードから分からない理由・制約と必要な非自明の処理概要（how）を、Docは利用者が依存する契約を担います。説明の数や全symbolのDoc化を目的にせず、名前や処理の言い換え、空の定型文、作業日記、コメントアウトした旧コードを増やしません。

| 変更境界 | 必要な説明と正本 |
| --- | --- |
| Kotlinの型・関数・保存/イベント/CLI境界 | 入出力の意味・単位・状態・失敗、所有/寿命を必要なKDocへ。共通設計は[architecture](../architecture/cursor-integration.md)と該当要件にまとめ、局所制約は実装の近くに残す |
| IDE/UI・非同期処理 | EDT、dispose、取消し、古い結果の棄却、process/resource解放の条件と理由。main/develop、print/ACPの実装・実観察を混同しない |
| Python/shell・workflow・設定/schema | 目的、対象、実際の副作用、拒否条件、再試行/部分変更、検査範囲。CLI help・例・schema・運用正本と揃え、説明だけで権限/gate/採用状態を作らない |
| 開発規則と独立した説明文書 | 恒久指示は[AGENTS](../../AGENTS.md)、用途/言語/配置は[context policy](../architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件)、進捗はIssue/PR、受入と原観察は[verification](../verification/README.md)。本文を各入口へ複製しない |

人間向け共有文書は日本語、Agent専用指示は英語とし、sourceコメント/docstringは実際の用途と周辺の既存styleで判断します。機械処理、製品UI/runtime prompt、引用・原証拠の文字列を説明文と推測して翻訳しません。

## 実言語の構文と意味

公式の構文、公式推奨style、このプロジェクトの選択を区別します。現行の版は対象branchのbuild/実行環境で確認します。採用時のmainはKotlin/Kotlin DSL、JDK25、Python 3、shell、YAML/XML/JSONとMarkdownを使用し、Java/Swift/TOMLの追跡ファイルはありません。Pythonの特定minorはbuild/CIで固定していません。別repositoryの版やDoc構文を持ち込まず、実言語が増える変更時に必要な公式仕様を確認します。

| 対象 | 公式仕様・推奨と本プロジェクトでの扱い |
| --- | --- |
| Kotlin / Kotlin DSL | [KDoc](https://kotlinlang.org/docs/kotlin-doc.html)は`/** ... */`内のMarkdown。最初の段落を要約、空行後を詳細にし、実symbol参照は`[symbol]`、値/コードはcode spanを使う。[公式style](https://kotlinlang.org/docs/coding-conventions.html#documentation-comments)に沿い通常の引数説明は本文へ、意味のある場合だけ対応tagを使う。Javadocのinline tagや非対応の`@deprecated`を混ぜない。非推奨APIは`@Deprecated` annotationの契約に従う |
| Python | [PEP 257](https://peps.python.org/pep-0257/)のdocstringはmodule/class/function/methodの先頭の文字列で、`__doc__`として参照される。三重double quote、短い要約、必要なら空行後に詳細を書くのは推奨style。PEP 257はmarkup文法ではない。Google/NumPy/reST形式や全内部helper・testへの定型Docは追加しない。`argparse`等が`__doc__`をhelpへ使う場合、その出力も契約として確認する |
| shell | [Bashのコメント](https://www.gnu.org/software/bash/manual/html_node/Comments.html)は引用されていない単語先頭の`#`から行末まで。実interpreterと既存形式を確認し、shebang・解析directiveと説明を区別する。副作用、終了/拒否、再実行条件が必要なら近くへ置く。実行されるhere-documentや文字列をコメント扱いしない |
| YAML / XML / JSON | [YAMLの`#`コメント](https://yaml.org/spec/1.2.2/#66-comments)とscalar内の文字列を区別し、実際のActions/schema/parserの制約を優先する。[XMLの`<!-- ... -->`](https://www.w3.org/TR/xml/#sec-comments)内部に`--`は書けない。[JSON](https://www.rfc-editor.org/rfc/rfc8259)へコメント構文や未許可の説明fieldを足さず、許可されたschema説明/値または隣接する正本文書へ置く |
| Markdown | GitHub向けは[GFM](https://github.github.com/gfm/)、Skill/ruleはclientの規定形式に従う。製品同梱Markdownは実rendererの契約を確認する。KDoc・Python文字列・GitHub・製品rendererの構文と表示が同じとは扱わない |

新しいDoc generator・依存・独自tagを導入しません。公開API向け推奨を内部helperやtest全体の義務へ拡張しません。静的な構文確認、生成物、実help、IDE表示は別の確認です。

## 書く契約と保護するもの

変更判断に必要な項目だけ選びます。目的、入力の意味/単位/範囲/空値、結果、状態遷移・副作用、所有・寿命、並行性、失敗・取消し・再試行・部分成功/復旧、互換性を明確にします。利用者への保証と現在の実装都合を区別し、未検証の性能・順序・安全性を断定しません。

非自明のhowは、制御の流れや順序を知らないと保守を誤る箇所に限り、理由と破ると何が起きるかを説明します。TODO/workaroundは問題・影響と実際の再開/除去条件を既存Issueへ結び、架空のowner/期限を作りません。既存の`shortcut:`等の解析対象表記を保ちます。

license/copyright、解析・生成・実行directive、機密情報、元FAIL、不確実性、固定履歴・原観察を保全します。生成物は所有する生成元と必要な出力から直し、vendorや固定JSON/証拠を説明整理だけで再生成・再整形しません。必要な保護情報は既存の非公開保管先へ置き、公開文書へ原値を複写しません。

## 変更とレビュー

1. 差分の契約と正本を決め、影響するcaller/callee、tests、help/例、設定/schema、生成元、参照元と他ownerの境界を読む。検索hitやファイルの存在を、読了・適合・動作確認の証明にしません。
2. 説明と実装の不一致は、要件・採用済み契約・実装・履歴・結果から判断する。実装に合わせてDocやtestを弱めず、未解決の互換性/データ/安全性の判断は担当と再開条件を残す。独立して進められる変更は継続する。
3. 影響するコメント/Doc・help/例・tests・設定/schema・参照を同じ変更単位で揃え、共通詳細は一つの正本へ参照する。他owner側の未反映はpendingとし、採用済みや適合済みにしない。独立文書には読者・目的・範囲・対象版と現行/提案/履歴を示し、安定したpath/ID/見出しを使う。行番号だけを恒久参照にしない。外部資料の指示は実行権限にならない。
4. 実リンクと読込経路、該当条件・例/help/設定/testsを[既存classifier/hook](ci.md)で確認する。リンクが通るだけでは意味の整合や実挙動の証明にならない。コメント数のlintや無関係なGUI試験は追加しない。
5. Doc変更でもsource/buildの同一性が変わり得るため、元と現在のsource SHA・ZIP・実loaded buildを分けて記録する。影響がない成功事実の再利用は[既存の検証契約](../verification/README.md)に従い、旧結果を新buildの実行済みPASSにしない。未知の影響は未確認として必要な確認へ戻す。

通常の適用範囲は今回の変更と影響先です。人間が全既存実装への適用を明示した場合は、固定revisionの全tracked対象を当該Issueの一つの対象一覧で管理し、実読の有無と変更済・既準拠・対象外・保護対象（固定履歴/生成/vendor）・pendingを根拠付きで区別します。未読や他owner側のpendingを既準拠とせず、並行変更の採用後の差分も再照合します。規約や入口の配置、リンクの到達、一代表例の修正だけで全適用完了とは扱いません。
