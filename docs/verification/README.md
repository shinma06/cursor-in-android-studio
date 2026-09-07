# 区切りごとの動作確認

まず [今回の確認一覧](current.md) を開き、上から順に試してください。初期一覧は PR #72/#74/#75/#77/#81/#82 の7ケースと、親#73に残るCUA操作成立の2ケースです。#39の接続未実装は先に解消します。#80はprobe調査記録の整合を確認します。CUA全面操作は親#73の別Caseで、人間が直接ボタンを押せてもCUA受入passにはなりません。

## 正本と結果の入力

- `changes/issue-N.json`: 変更単位の必要Case・操作手順・期待結果・当初のGPT/人間状態・既存証拠の正本。PRの `Verification:` は自分のIssueファイルを指します。既存MV/runは詳細と履歴であり、新しい候補の結果を書き戻す先ではありません。
- `batches/*.json`: 今回確認するファイルだけの一覧。長大なMV全体を人間へ渡しません。
- `promotion.json`: 固定したdevelop候補・対象buildと、その候補上で実施した結果の正本。下記の形式で結果を入力します。`current.md` はこれらから生成する閲覧用で、直接編集しません。

初期一覧の再生成（repository rootから）:

```bash
python3 scripts/workflow/verification.py \
  --batch docs/verification/batches/2026-09-initial.json \
  --output docs/verification/current.md
```

候補確認を開始したら、バッチJSONの `promotion` に `docs/verification/promotion.json` を指定するか、上記へ `--promotion docs/verification/promotion.json` を加えます。結果の入力後に同じコマンドを再実行すると、候補結果・確認者・日時・証拠・Case単位のmain可否が更新されます。生成MarkdownをPRへcommitする場合は**developで候補を固定する前**に行います。promotion branchでは下記2 JSON以外の差分を許しません。候補確認後の閲覧用出力はローカルへ出力してください。

## 状態の意味

`pending` は未実施、`blocked` は環境等で操作不能、`fail` は製品の期待結果と不一致、`pass` はそのbuildで確認済みです。GPTがblocked/failでも、必要テスト・独立コードレビューを通り、全Caseと次の操作が記録されていればdevelopへ統合できます。製品failは専用修正Issueを作成し、修正branch/PRと再確認を紐付けます。テスト失敗や未解決コード指摘は、この例外に含みません。

mainは固定候補内の**全変更・全必要Case**のpassが必要です。GPT/humanどちらの適切な観察も有効ですが、過去SHA/buildのpassをコピーしません。Case別passはmain全体の昇格許可ではなく、最後に自動gateがコミット範囲を照合します。developに入っただけでCase/QA Issueをcloseしません。

## 固定候補からmainへ

1. PMが区切りを選び、未実装依存を解消してdevelopへ統合します。最新mainをdevelopへ取り込む必要がある場合は、**専用Issue branchをdevelopから作りmainを通常mergeし、develop向けPRをsquash merge**します。同期も自分のCase JSON・CI・独立レビューが必要です。main/developへの直接pushは禁止です。
2. PMはdevelopの候補40桁SHAを固定し、そのSHAからbuildします。developへの後続統合は継続できます。ZIPのSHA-256と実際にロードしたJARを照合します。probe等の別成果物は同じcandidateからbuildし、`artifacts.swing_probe`にそのhashを記録します。Caseの`artifact`（省略時plugin）ごとに一致検査します。candidateが現在のdevelopの祖先であることをgateが検査します。candidateより後の変更は今回のpromotionに含めず次バッチへ回します。無関係なSHAや候補の差し替えは受入に使えません。
3. 専用promotion Issueと `codex/N-promotion` branch/worktreeを候補SHAから作成し、現在のmainを通常mergeします。`candidate`からの製品差分がゼロである必要があります。main先行toolingや前回QA記録が差分に残れば、1に戻してdevelopへ同期し直します。
4. `docs/verification/changes/issue-N.json` をGUI不要の「確認結果の記録」として作成し、`docs/verification/promotion.json` を下記の形式で用意します。PR本文は `Integration: promotion`、`GUI: not-required`（結果記録自体の区分）、`Verification: docs/verification/changes/issue-N.json`。必須GUIは元develop PRのCaseから自動収集され、ここで不要と宣言しても免除されません。
5. `changes` に `git rev-list <main SHA>..<candidate SHA>` の**全コミット**を1回ずつ登録します。各コミットは実際にmerge済みの同一repository develop PRのsquash結果でなければなりません。省略、未対応コミット、merge/rebase取り込み、別PRへの付け替えをgateが拒否します。選択的なmain統合は実装していません。通常は固定develop候補全体を確認します。
6. `results` は全必要Caseを `Issue番号:Case ID` で列挙します。各Caseの操作後、実施者が実際の結果を入力します。失敗なら専用修正Issue/PRをdevelopへ入れ、新候補で全必要Caseを再確認します。
7. テスト・独立レビュー・PR policy・Acceptance gateを通し、PM/coordinatorが**merge commit**で統合します。squashでは候補の祖先関係が失われるため禁止です。mainのstrict base、直前のcandidate再照合、head指定mergeを維持します。
8. promotion Issueは全受入完了時にclose可能です。元の機能/QA Issueは残条件を個別に照合して更新し、まとめてcloseしません。次の区切りでは1のmain同期を先に行います。main/developをcleanupしません。

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
