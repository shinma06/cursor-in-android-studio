# 採用済み変更によるCaseの適用条件

[#590](https://github.com/shinma06/cursor-in-android-studio/issues/590)の4群だけを対象とする。元Case JSON・scope・過去の観察は変更せず、trusted mainの[適用表](issue-590.json)から、固定候補に適用する手順・期待結果を生成する。これは製品の試験結果ではない。

| 元の保証 | 適用条件と採用根拠 | 残試験の保管先 |
| --- | --- | --- |
| #42 / #205 / #97 のTab・Shift+Tab、本文・選択・focus保護 | PR #513を含む候補では既定Shift+Tabがmode循環。Mode Actionの割当解除時は逆focus移動。IME・popup・通常Tab・再生成・通常editorの保証は継続 | QA #103 / #418 / #359 と #531 |
| #48 / #97 の実行中入力・明示予約・draft/queue/run所有 | PR #513を含む候補では設定済み送信キーも明示予約。Stop/改行/IME/popup/入力不可・拒否時のdraft・重複/自動再送禁止を継続 | QA #340 / #359 と #531 |
| #153 の選択tab・履歴・要約 | PR #255を含む候補では保存済み本文を表示し、旧metadata-onlyは本文なしと説明。閲覧・provider再開・Revertを区別 | QA #160 と #259 |
| #180 の1行配置・右固定幅・tab/4操作 | PR #184を含む候補ではSDK余白込み108 logical px。狭幅・倍率・既存操作は継続 | QA #182 と #185 |

採用根拠は、元merged Caseのsource PR/merge/path/hashと後続merged Caseのhashを対応付ける。QA #182の[実引継ぎ](https://github.com/shinma06/cursor-in-android-studio/issues/182#issuecomment-5626167417)も108pxを優先している。採用前の候補は元契約を使う。設定条件を満たす操作・満たさない操作を両方試し、片方のOKをCase全体のpassにしない。

## 固定候補と結果

生成一覧には現在条件・適用revision・元の手順/期待/観察を分けて表示する。固定候補・ZIP/ロード実体を照合した新観察の `applicability_revision` は、そのCaseについて生成された値と一致させる。候補SHA・artifact hash・実施者・日時・証拠・Computer Use指定など、通常の受入条件も必要。古いPASS、`reused`、Rabbit環境revisionだけでは変更後の契約を充足しない。環境改訂と本改訂が両方適用されるCaseは、両方のbindingを満たす。

原FAIL、元ID/hash、Computer Use、artifact、fix/recheck、他Caseを保持する。未実施・blocked・部分観察はそのまま残す。製品NGの修正は専用Issue/PRと訂正候補の再確認へ渡す。

## #562の限定候補

固定scopeの全体hashと `original_candidate`、埋込原Caseのhash/元merge、`original_provenance`を照合する。PR #255 / #184は元Cに含まれるため、I153の履歴説明とI180の幅だけに適用する。元Cに含まれないPR #513の送信キー/Shift+Tabを追加しない。175 Case、Computer Use必須4件、Rabbit binding10件、2 artifact、原観察と製品範囲を維持する。scopeと受入JSONは書き換えない。
