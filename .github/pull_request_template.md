Issue: #<number>
Integration: develop
Verification: docs/verification/changes/issue-<number>.json
GUI: required
GUI reason: <Case IDと必要挙動。不要なら具体的理由とCLI検証>

## 変更

<問題と変更後の動作。developはRefs #N。promotionはIntegration: promotionでmainへ、GUI不要toolingはIntegration: toolingでmainへ>

## 検証

- Tests: <コマンド/結果とCI URL>
- Independent review: <担当session・固定HEAD/base・指摘と解決・結果URL。変更した境界/正本をdocs/architecture/README.mdから照合。設計変更は根拠/仮説/置換先をknowledge.mdの採用条件で確認>
- Matrix: <必要Case JSONと今回の確認一覧。GUI pending/blocked/fail/passを正確に保持>
- Issue schema: <typeに対応した題名・type/priority/status各1ラベル>
- QA handoff: <develop統合後に元実装Issueの全Case/main反映追跡をQAへ双方向link/readbackしてからclose。未実装受入は別Issueへ保全>
- Dependencies: <先行Issue/PR、未実装部分。GUI環境の障害と分ける>
- Main promotion: <全候補commit/Caseの固定build結果。develop統合だけではmain可としない>

## 引継ぎ

<owner、HEAD、残条件、次の操作。writer停止後にv2 opaque enrollment。ローカル絶対パス/host/tokenを公開しない>
