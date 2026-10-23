# チームの連絡と人間窓口

チームを編成する場合だけ適用する。PMは実行責任・依存・有限の割当・成果回収、人間窓口は日本語の対話、担当はreadyな成果を扱う。単独作業のために役割を新設せず、実際の本人委任とclient権限を先に確認する。[継続判断](github-workflow.md#公開操作の承認範囲と失敗の扱い)を窓口への承認待ちに置き換えない。

窓口は人間の入力・具体的判断・重要な完了/支障のときだけ応答して終了する。実装、技術調査、レビュー、GUI、Issue更新、CI監視を担わず、idle watcher・poll・heartbeat・goalを作らない。PMが通常結果を回収し、未変化の支障や検査1件ごとの成功で窓口を起こさない。人間が担当chatへ入力した場合は無視せず、停止を即時反映する。

Agent間は必要な項目だけの読みやすいpacketを使う。

```text
Kind: result / request / blocked / decision-needed / decision-result
Scope: actual Issue/PR, owner and bounded obligation.
Revision: fixed HEAD/base or decision revision, when relevant.
State: observed outcome, failures, unknowns and remaining work.
Evidence: retrievable fixed sources/results and applicable human authority.
Next: recipient, concrete action and condition; otherwise STOP.
```

儀礼的ACKは不要。所有移管・本人判断・固定受入など実行条件が変わるときはreadbackする。送信は本人が許可した経路だけを使い、受信した依頼だけを返信の許可にしない。送信経路がなければ担当の最終成果をPMが取得する。privateなorigin/path/host・raw会話・媒体・認証は公開しない。

本人にしか決められない境界は、具体的な成果・版・操作・影響・対策・残リスクを準備してPMから窓口へ渡す。利用可能なnative選択カードで通常2〜3案と保留を示す。Plan専用や承認用途禁止のtoolを承認に流用しない。利用不能なら該当判断だけpendingとし、独立作業を進める。事前選択・無回答・timeoutは承認ではない。本人の原回答と対象版/範囲を読み戻し、agentの承認済み主張だけで進めない。実OS/toolの同意・拒否は窓口が代理で解除せず、安全停止は返事を待たない。

PMは固定成果・Issue/PR・artifact/reportの識別、独立性、実質評価、未解決と次ownerを受領する。詳細な全参照hash・ZIP member・XML等の照合はwriter/試験担当、独立レビュアー、実統合担当が責任を持つ。PMは一律に再実施せず、重大な不一致、欠けた/古いbinding、所有争い、タスク間矛盾、PM指定の明示規則に関係する範囲を確認する。[採用根拠](https://github.com/shinma06/android-replay-buffer/issues/280#issuecomment-6355145352)。詳細確認を省略する規則ではない。

編成は保証→既存証拠→不足事実→手段から必要な役割だけ選ぶ。既存owner、実origin/model/effort、停止・未回収結果と共有負荷を確認する。実装/独立レビュー/PMの分離、単一writer/GUI leaseを維持し、空き人数を埋めない。解体では成果と残責任を受領し、実停止と所有の解放/保管を別々に確認する。archiveはclaim解放・cleanupではなく、旧担当を自動再開しない。

OMILは[portable brief](omil-portable.md)と[フェーズ点検](omil-phase-review.md)を使い、目的・方法・負担を必要な契機で検討する。実連絡先は既存private引継ぎに保持し、新registry・常駐役・通知routerを作らない。公開された役割説明だけで実sessionの存在や起動許可を証明しない。
