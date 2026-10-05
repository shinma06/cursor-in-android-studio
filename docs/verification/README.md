# 区切りごとの動作確認

まず [今回の確認一覧](current.md) を開き、上から順に試してください。初期一覧は PR #72/#74/#75/#77/#81/#82 の7ケースと、親#73に残るCUA操作成立の2ケースです。#39の接続未実装は先に解消します。#80はprobe調査記録の整合を確認します。CUA全面操作は親#73の別Caseで、人間が直接ボタンを押せてもCUA受入passにはなりません。

## 正本と結果の入力

- `changes/issue-N.json`: 変更単位の必要Case・操作手順・期待結果・当初のAgent/人間状態・既存証拠の正本。PRの `Verification:` は自分のIssueファイルを指します。既存MV/runは詳細と履歴であり、新しい候補の結果を書き戻す先ではありません。
- `batches/*.json`: 今回確認するファイルだけの一覧。長大なMV全体を人間へ渡しません。
- `promotion.json`: 固定したdevelop候補・対象buildと、その候補上で実施した結果の正本。下記の形式で結果を入力します。`current.md` はこれらから生成する閲覧用で、直接編集しません。

初期一覧の再生成（repository rootから）:

```bash
python3 scripts/workflow/verification.py \
  --batch docs/verification/batches/2026-09-initial.json \
  --output docs/verification/current.md
```

候補確認を開始したら、バッチJSONの `promotion` に `docs/verification/promotion.json` を指定するか、上記へ `--promotion docs/verification/promotion.json` を加えます。結果の入力後に同じコマンドを再実行すると、候補結果・確認者・日時・証拠・Case単位のmain可否が更新されます。生成MarkdownをPRへcommitする場合は**developで候補を固定する前**に行います。promotion branchの直接編集は下記2 JSONに限ります。後からmainが進んだ場合の同期は、下記の限定検証を通す必要があります。候補確認後の閲覧用出力はローカルへ出力してください。

保存形式の`gpt`キーと`actor: gpt`は互換用のAgent観察枠です。GPT専任を意味しません。旧Case手順の「指定GPT」等の担当表記も現行の能力・許可条件で割り当てます。過去に実観察した担当名・モデル・結果は書き換えません。実際の担当session・モデル・実施経路を証拠に記録し、`human`と既存の観察履歴は保持します。担当条件は[共通規約](../development/github-workflow.md#正本と役割)に従います。

## 状態の意味

`pending` は未実施、`blocked` は環境等で操作不能、`fail` は製品の期待結果と不一致、`pass` はそのbuildで確認済みです。Agentがblocked/failでも、必要テスト・独立コードレビューを通り、全Caseと次の操作が記録されていればdevelopへ統合できます。製品failは専用修正Issueを作成し、修正branch/PRと再確認を紐付けます。テスト失敗や未解決コード指摘は、この例外に含みません。

mainは固定候補内の**全変更・全必要Case**のpassが必要です。Agent/人間どちらの適切な観察も有効ですが、過去SHA/buildのpassをコピーしません。Case別passはmain全体の昇格許可ではなく、最後に自動gateがコミット範囲を照合します。develop統合後、元実装IssueはQAへの双方向link/readbackを確認してcloseします。Case/QA Issueは実際の確認完了まで残します。GUI不要のmain未反映分もQAに追跡し、固定候補JSONは書き換えません。closed元Issueの固定merge Caseをpromotionが参照し続けます。

## 固定候補からmainへ

1. PMが区切りを選び、未実装依存を解消してdevelopへ統合します。最新mainをdevelopへ取り込む必要がある場合は、**専用Issue branchをdevelopから作りmainを通常mergeし、develop向けPRをsquash merge**します。同期も自分のCase JSON・CI・独立レビューが必要です。main/developへの直接pushは禁止です。
2. PMはdevelopの候補40桁SHAを固定し、そのSHAからbuildします。developへの後続統合は継続できます。ZIPのSHA-256と実際にロードしたJARを照合します。probe等の別成果物は同じcandidateからbuildし、`artifacts.swing_probe`にそのhashを記録します。Caseの`artifact`（省略時plugin）ごとに一致検査します。candidateが現在のdevelopの祖先であることをgateが検査します。candidateより後の変更は今回のpromotionに含めず次バッチへ回します。無関係なSHAや候補の差し替えは受入に使えません。
3. 専用promotion Issueと `codex/N-promotion` branch/worktreeを候補SHAから作成し、現在のmainを通常mergeします。`candidate`からの製品差分がゼロである必要があります。初回同期のmain先行toolingや前回QA記録が差分に残れば、1に戻してdevelopへ同期し直します。候補を保った既存promotionへ後続mainを同期する場合は下記の限定検証を使います。
4. `docs/verification/changes/issue-N.json` をGUI不要の「確認結果の記録」として作成し、`docs/verification/promotion.json` を下記の形式で用意します。PR本文は `Integration: promotion`、`GUI: not-required`（結果記録自体の区分）、`Verification: docs/verification/changes/issue-N.json`。必須GUIは元develop PRのCaseから自動収集され、ここで不要と宣言しても免除されません。
5. `changes` に `git rev-list <main SHA>..<candidate SHA>` の**全コミット**を1回ずつ登録します。通常は各コミットを実際にmerge済みの同一repository develop PRのsquash結果へ対応付けます。既存の承認済みmain同期mergeは下記の厳密な履歴照合に限って内包commitも同じPRへ対応付けます。省略、未対応コミット、任意のmerge/rebase取り込み、別PRへの付け替えをgateが拒否します。選択的なmain統合は実装していません。通常は固定develop候補全体を確認します。
6. `results` は全必要Caseを `Issue番号:Case ID` で列挙します。各Caseの操作後、実施者が実際の結果を入力します。失敗なら専用修正Issue/PRをdevelopへ入れ、新候補で全必要Caseを再確認します。
7. テスト・独立レビュー・PR policy・Acceptance gateを通し、PM/coordinatorが**merge commit**で統合します。squashでは候補の祖先関係が失われるため禁止です。mainのstrict base、直前のcandidate再照合、head指定mergeを維持します。
8. promotion Issueは全受入完了時にclose可能です。元の機能/QA Issueは残条件を個別に照合して更新し、まとめてcloseしません。次の区切りでは1のmain同期を先に行います。main/developをcleanupしません。

### 候補固定後のmain同期（#533）

候補を再buildせずに同期できるのは、製品・build入力を変えないmain更新だけです。promotion側で直接編集できるのは引き続き `promotion.json` と当該IssueのCase JSONだけです。

- first-parent履歴が固定candidateへ戻り、全追加commitを説明できることを検査します。旧main同期の第二親は現在mainの祖先で、各差分が上記2 JSONだけなら過去の同期として維持します。
- 文書/toolingを取り込むmergeは、第二親が現在mainの祖先、merge baseが一意で、Gitの競合なしmerge treeと2 JSON以外が完全一致する場合に限ります。文書の手直しや競合解消が必要なら同期専用develop PRと新候補に戻します。
- main側の取り込み履歴も各親との差分を検査します。既存Change Impact分類でruntime/build/test/unknown、symlink/submoduleを拒否し、製品変更後のRevertを最終差分だけで見逃しません。既存Case・環境/範囲policy・結果JSONの改訂は認めず、新規のGUI不要tooling受入JSONだけを追加できます。
- 同期後に `promotion.base` を現在mainへ合わせ、全commit/PR/Case集合を再照合します。固定candidate、artifact、実観察、環境revision、全Case pass、独立レビューと4必須checksは維持します。構造検証の成功はGUI合格ではありません。

### 既存main同期mergeの履歴照合（#250）

通常のdevelop統合は引き続きsquashです。このvalidatorは過去に承認・mergeされた同期履歴を受け入れるもので、新しいmerge方法や保護設定の例外を許可しません。PR番号/SHAのallowlistは持ちません。

- `changes` は引き続き `main..candidate` の全commitを一度ずつ列挙します。同期の外側mergeと内部first-parent commitにも実際の同期PR番号を指定します。mainに既にある祖先は追加しません。
- 同期PRは同repoで実際にdevelopへmerge済み、GUI不要の受入JSONを持ち、外側mergeの2親がAPIのbase/head SHAと一致する必要があります。境界は実Gitの親から取得し、APIと不一致なら受け入れず再調査します。
- 内部first-parent鎖はdevelop側の親まで戻り、内部mergeの第二親は固定main baseの祖先に限定します。少なくとも1回のmain mergeが必要です。root、3親以上、無関係なside branch、説明不能なcommitを拒否します。
- 内部の各first-parent差分と外側mergeの両親との差分をtooling許可pathに限定します。renameは削除/追加に分けて検査し、製品変更後のRevertや製品ファイルのdocsへの移動も拒否します。
- 各PRに割り当てたcommit集合と検証済みDAG範囲が完全一致しなければ拒否します。製品PRを同期PRへ付け替えられません。全PRのCase JSONはそのPRの固定merge SHAから読み、後のCase削除では必要集合を縮めません。

#226の履歴では外側mergeと内部3commitを#226へ、他の製品/tooling commitをそれぞれのmerged PRへ対応付けます。実候補のGUI結果を登録しない診断はCase/証拠不足で拒否されることが正しく、履歴照合成功だけでpromotion合格にはしません。#250統合後、#249でmainをdevelopへ同期してから候補/全Caseを再収集します。

### Rabbit移行後の旧Caseの適用環境（#479）

#466でQuail/Java21対応を終了したため、[環境改訂JSON](environments/rabbit1.json)は#389の4件、#205・#404各1件、#208の4件だけをRabbit 1へ適用し直します。機械入力の正本であり、全候補のpromotion gateと候補付き一覧生成時に読みます。元の固定merge Caseと観察履歴は変更しません。Case ID・必要集合・artifact・Computer Use要件・操作手順・機能上の期待結果も保持します。旧Q1/Q4というIDは出典の識別に残し、RabbitでのpassをQuailのpass・互換性と表示しません。

改訂はpromotionの固定**base（trusted main）**からだけ読みます。promotion HEAD側の追加・変更では適用できません。候補が#468の移行commitを含み、候補のSDK/JVM policyが改訂のbuild/JBR/JVM/bytecodeと一致する場合に限り適用します。移行前の候補は旧条件を維持し、未知のSDKは拒否します。元PR/merge/Case内容のSHA-256が不一致なら停止します。main限定候補のtrusted scopeはこの改訂の対象外です。

対象10件の新観察には、通常のsource/ZIP/ロードJAR・実施経路・証拠に加え、以下を記録します。`environment_revision`は改訂JSONを`json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(',', ':'))`で正規化したUTF-8のSHA-256です。`environment`は**実際にロードした**環境の照合結果を記録し、改訂JSONの同名objectと一致させます。文字列だけの`loaded_identity`や旧結果の転記で代用しません。

```json
{
  "environment_revision": "trusted mainの環境改訂JSONの64桁hash",
  "environment": {
    "build": "AI-262.9437.185.2621.16467767",
    "java_version": "25.0.3",
    "jvm_target": "25",
    "class_major": 69
  }
}
```

候補付き一覧は固定merge由来の操作・履歴に、新しい前提と環境revisionを表示します。revision未登録・不一致の結果はCase合格になりません。#389の旧Q1/Q4の同一操作をRabbit上で共通実施した証拠は、各Caseの全手順を満たす場合に同じ証拠へリンクできますが、全Caseキーの結果登録は必要です。print/ACP・Markdown・旧fixture/新会話の再起動後の本文/順序/ID/設定、Terminal ON/OFF等を省略しません。旧文面中の過去QA番号・「両IDE」は履歴の文脈であり、今回の担当と対象環境は固定候補のQA引継ぎと本改訂で明示します。

### promotion.jsonの形式

以下は説明用の値です。SHA/hashや観察は実際の値へ置換し、未実施をpassにしないでください。

```json
{
  "schema": 1,
  "base": "現在のmainの40桁SHA",
  "candidate": "固定developの40桁SHA",
  "artifact_sha256": "確認したZIPの64桁hash",
  "changes": [{"commit": "developのsquash merge SHA", "pr": 123}],
  "results": {
    "42:PROMPT-TAB-1": {
      "status": "pending",
      "reason": "まだ確認していません",
      "head": "固定developの40桁SHA",
      "artifact_sha256": "確認したZIPの64桁hash",
      "actor": "human",
      "observer": "公開可能な確認者ID",
      "at": "2026-09-07T10:00:00+09:00",
      "evidence": "共有可能な証拠URL",
      "loaded_identity": "実際にロードしたbuild/JARを照合した証拠",
      "execution": "manual"
    }
  }
}
```

`required_execution: computer_use` のCaseは結果も `execution: computer_use` が必要です。人間がCUA実施証拠を確認する場合も、直接の手操作とは区別します。公開JSON・Issue・PRにローカル絶対パス、ホスト名、token、private rawログを書かないでください。

## 初期移行

初期7ファイルは現在のIssueコメントと固定runから登録しています。過去の部分passは`history`のみで、候補結果は未登録です。PMは各PRのwriter/既存enrollmentを確認した後、導入済みdevelopを同期し、必要な受入JSONとPR metadataを付けてretargetします。#81の未接続をGUI環境blockedとして扱わないでください。

候補後のcommitも個別検査します。後続developを取り込んで製品差分だけRevertする操作は、最終treeが一致しても未確認履歴の混入として拒否されます。

## main起点の限定候補（#408）

モダン化だけをmainへ取り込むユーザー承認済み経路は[限定取り込み手順](../development/main-scoped-release.md)に従う。trusted mainに先行保存した計画の全必要Caseを同一候補/ZIPで確認する。develop全体のpromotion、GUI不要tooling、4必須checksと独立レビューは維持する。
