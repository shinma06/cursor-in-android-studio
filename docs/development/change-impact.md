# Change Impactと検証の選択

2026-09-11 / #198。変更がruntime・build・tests・packaging・生成物・依存解決・配布・開発環境の意味を変えるかで、必要な検証を選ぶ。「コード以外」はskipの根拠にしない。分類と実行選択の正本は [change_impact.py](../../scripts/workflow/change_impact.py) だけで、workflow/hookごとにpath条件をコピーしない。

## 分類と実行

| Impact | 現在の対象例 | 選ぶ検証/生成 |
| --- | --- | --- |
| IMPACT_RUNTIME | `src/main/**`（同梱Markdown、アイコン、翻訳、assetsを含む）、AndroidManifest | Gradle test、Plugin ZIP |
| IMPACT_BUILD | Gradle/wrapper/依存、buildSrc、plugin.xml、packaging。入力/成果物を決める `.gitignore` / `.gitattributes` / classifier / CI・ZIP workflow / ZIP scriptも含む | Gradle test、Plugin ZIP。Tooling併存ならその検証も実行 |
| IMPACT_TEST | `src/test/**`（fixture/resourcesを含む） | Gradle test |
| IMPACT_TOOLING | 既知のworkflow/loop Python・shell、hook、Actions YAML、ruleset、Agent設定、非Markdownのverification入力 | workflow/loop tests。CIでは固定版actionlintでActionsの構文も検証 |
| IMPACT_KNOWLEDGE_ONLY | 非実行のAGENTS/CLAUDE/README/CONTRIBUTING、`docs/*.md`、既知Skill入口・Cursor説明rules | 差分の空白検査、内容/リンク/指示整合のレビュー |
| IMPACT_METADATA_ONLY | Issue/PR template、CODEOWNERS | 差分検査、管理情報と独立レビュー |
| IMPACT_UNKNOWN | 未知path/type、実行可能なMarkdown、未確認symlink、不完全/取得不能な履歴 | 全検証・ZIP生成へ倒す |

複数Impactを集合で保持し、その和の検証を行う。知識＋Kotlinをknowledge-onlyにしない。`plugin.xml`はRuntimeとBuild、classifierはBuildとTooling。test-onlyはZIPを作らないがテストは省略しない。未知の新directoryを広い`docs/**` / `scripts/**`規則だけでskipへ入れない。AGENTS→CLAUDEは現在の非実行symlinkとして明示扱いする。

pathは現在のbuild/呼出しを照合したallowlistであり、意味を推論する万能判定ではない。新しいsource/resource、生成入力、同梱資料、toolingを追加したら消費側と分類を同時に見直す。例えばdocsのMarkdownを配布物へ同梱する変更ではbuild変更を検証し、以後そのMarkdownをKnowledge扱いしないよう分類を更新する。未知のtooling自身に固有の確認が要る場合はCaseのCLI checksへ追加する。

## 呼出しと差分

```bash
# origin/develop（なければorigin/main）とのmerge-baseからIssue全体を分類・検証
python3 scripts/workflow/change_impact.py --run-tests
# main向け等で比較先が明確な場合
python3 scripts/workflow/change_impact.py --base origin/main --run-tests
# 判定にかかわらず全テストを実行する
python3 scripts/workflow/change_impact.py --base origin/develop --force-full --run-tests
```

commit済みのHEADを対象にする。push直前のdirty/別branch/非fast-forward拒否は従来のgit guardが先に実施する。ローカルの分類は直前commitだけでなくIssue差分全体を使い、コード変更後に文書commitを追加してもコード検証を消さない。

Gitの完全なraw diffをNUL区切りで読み、renameは旧pathの削除＋新pathの追加として双方を分類する。実行権限・symlinkへの変化も確認し、`diff.ignoreSubmodules`によるgitlink省略を明示解除する。GitHubのファイル一覧APIの件数上限へ依存しない。PR CIはbase SHAとcheckoutしたmerge結果、push CIはbefore〜HEAD全範囲を検証する。履歴不足・初回pushのzero before・非祖先push・空差分では全実行。誤検知による余分な実行を、必要な検証の欠落より優先する。

## Git起点の処理の棚卸し

| 入口 | 今回の接続/維持 |
| --- | --- |
| `.githooks/pre-commit` | branch/所有範囲の事故防止。コード変更を前提としないため常時維持 |
| `.githooks/pre-push` | guard後、共通判定からPython/Gradleを選択。削除だけ・更新なしの既存skipは維持。ZIP/cacheは生成しない |
| `ci.yml` push/PR | 軽量判定→選択したテスト。Gradle不要時はJDK/Gradle設定・Android Studioの取得/cacheまで省略 |
| `branch-zip.yml` push/schedule/manual | 共通判定でZIPが必要なbranchだけmatrixへ。生成・転送・公開は同じplanから実施 |
| `agent_loop.py` base同期/修正後push | trusted側classifierで固定base/新HEADを判定。選択テスト後もdirty、remote HEAD、Issue、再レビューの検査を維持 |
| PR policy / Acceptance / Agent review | 検証対象がPR/Issue/Case/承認自体なのでImpactによるskipをしない |
| `loop.py build` / `gui-fixture/build.py` / 手動Gradle | 実buildを明示要求する手順。Git event起点ではなく、固定QA証拠/成果物作成が目的なので省略しない |
| Agent Skill/Context | 無条件の「毎回Gradle」を共通コマンドへ統合。GUI/build明示要求と追加の受入検証は維持 |

現在、独立したAndroid instrumentation test、アプリlint/static analysis、coverage、APK、deployment、codegen自動検証workflowはない。架空のjobを追加せず、導入時にこのclassifierへ接続する。`.claude/settings.json`は権限allowlistで、自動buildを起動するhookではない。

## Required checksと可視化

CI workflow全体をpath filterで止めない。既存のrequired **test** jobを常に起動し、判定成功＋必要なstep成功、または安全なskip判断をsuccessとして返す。判定/テスト失敗は失敗のまま、check名とサーバーの4必須checkは変更しない。Action summaryにImpact、ファイル、比較SHA、実行/skipと理由を出し、PRのChecksから確認できる。skipをテスト実行済みやGUI passと表示しない。

CIのRun workflowはfull検証。CLIは`--force-full`、ZIPの手動実行は`force_build`を明示できる。force-skip、ラベルでのskip、token権限の拡大は追加しない。actionlintはCI内の開発用検証だけに固定版・checksum付きで取得し、Plugin依存へ追加しない。

## ZIPの意味と導入順

既存の正常なZIPがあるbranchは、その**実際にビルドしたSHA**から現在HEADまで比較する。複数回のpushをまとめて照合し、前回の生成失敗を次の知識変更で隠さない。新規branchは確認できるintegrationのmerge-baseから比較し、知識だけならReleaseを作らない。履歴が不明、Releaseが途中/破損なら生成・復旧側へ倒す。

skip時はReleaseやassetを変更せず、古いZIPへ新HEADの名前を付けない。画面/ZIP名のSHAは実buildのSHAで、branch HEADと異なる場合がある。正確に現HEADのZIPが必要なら`force_build`を使う。[配布手順](plugin-zip-delivery.md)を参照。

新workflowを含むbranchのCI/hook/push配布から適用される。既定branchのscheduleとtrusted-main coordinatorはmain上のコードを使うため、develop統合だけでは全経路の切替完了ではない。main反映と旧branchでのsafe fallbackはQAへ明記する。旧branchにclassifierがないhookでは従来の全テストを行い、存在しない検証をskip成功としない。既存enrollment/owner/PAUSED heartbeatを自動変更しない。

新しい入口/import/依存や分類対象を追加する際は、[4実行経路の副作用・配置境界](tooling-boundaries.md)も既存PRレビューで確認する。

## Governance Auditと検証

[Git Governance Audit](git-governance-audit.md)では実tree/消費側に対するallowlist、古いpath、新runtime resource、条件の重複、不要な重い実行、required checkとskipの一致を確認する。今回の導入はHigh Impactとして#198で7領域への影響を照合する。skip率や待ち時間の削減率は実測するまで断定しない。

`test_change_impact.py`の必須8例とGit差分境界、`test_pre_push.py`の実push、`test_agent_loop.py`、`test_branch_zip.py`で、混在・unknown・rename/delete/mode・初回/手動・旧ZIP保全/復旧を検証する。GitHub Actions構文は[actionlint](https://github.com/rhysd/actionlint)で検証する。

GitHubの仕様: [required jobを安全にskipする方法](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-jobs-with-conditions)、[workflowのpath filterとPendingの注意](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onpull_requestpull_request_targetpathspaths-ignore)、[比較APIの件数制限](https://docs.github.com/en/rest/commits/commits#compare-two-commits)。
