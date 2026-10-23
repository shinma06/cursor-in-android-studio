# Android Studioの知見を使う条件

本製品の目的・有効な要件・実装と固定証拠を判断基準にする。同じAndroid Studioプラグインの先行知見は、具体的な不足を補うときに再利用する。外部repositoryの全件読込・毎回巡回・不参照理由の記入は求めない。

| 判断すること | 最初のローカル入口 | 外部知見を使う条件 |
| --- | --- | --- |
| SDK/JBR/JVM・build・配布 | [build入力](../../scripts/workflow/check_build_inputs.py)、[互換性policy](../../scripts/workflow/plugin_compatibility.json)、[ZIP配布](plugin-zip-delivery.md) | 実対象版のAPI・bytecode・依存・標準buildで足りない事実がある |
| EDT・background・dispose・取消し・process | [実装指示](../../AGENTS.md#main-print-implementation-constraints)、対象source/callers/tests | API境界、寿命、generation、所有子の実停止を判断する必要がある。外部のPASSを本製品へ転記しない |
| profile・fixture・実ロード・GUI | [GUI調整](gui-coordination.md)、[loop証拠](../loop-engineering/evidence.md) | 実IDE/profile/PID/source/ZIP/JARと所有fixtureの結合に不足がある。worktreeは共有IDEを隔離しない |
| 保存・復元・失敗保全・安全性 | [要件](../cursor-agent-plugin-requirements.md)、[製品制約](../../AGENTS.md)、対象保存コード/tests | state/storage互換、atomic更新、対象root・失敗/取消し保全の不足を確認する。録画製品固有の形式やpinを本製品要件にしない |
| 受入・harness・証拠再利用 | [verification](../verification/README.md)、[責任](../verification/responsibility.md)、対象Case | 保証→既存証拠→不足事実を先に示し、必要な検査/実接続だけ選ぶ |

ARBの[固定Android Studio知識](https://github.com/shinma06/android-replay-buffer/blob/56dad1d9c63cbfd8c504099498bb993791520109/docs/android-studio.md)や[固定UI設計](https://github.com/shinma06/android-replay-buffer/blob/56dad1d9c63cbfd8c504099498bb993791520109/docs/design/ide-ui.md)は参考先の例。関連source/test/Issueへ必要な範囲だけ進み、main採用済み・developのみ・設計・未確認を区別する。採用時だけ既存Issue/PRに「固定source/版 → 適合理由・前提差 → 本製品の検査/残条件」を残す。公式APIを確認し、他製品のID・数値・Case・信頼hash・受入・認証・個人設定・実行状態をコピーしない。

mainはprint、developにはACP等の別実装・履歴がある。共通規約の同期で製品差分を平坦化せず、当該branchの実source/buildを確認する。知識経路の追加はSDK更新・GUI・他repositoryへの還元/連絡を始める許可ではない。
