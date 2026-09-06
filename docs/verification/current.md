# 今回の動作確認一覧

> 自動生成。結果は正本JSONへ入力して再生成してください。過去buildの結果は参考です。

固定候補SHA: 未固定
対象ZIP SHA-256: 未登録

| Case / Issue / PR | 対象 | 候補結果 | main可否（Case単位） | 修正先 |
|---|---|---|---|---|
| TOKEN-PANEL-HIERARCHY-1 / #71 / #72 | トークン数パネルの文字階層・数値・グループを読みやすくする | pending | 不可・固定候補のpass未登録 | — / — |
| STOP-UX-1-STOP / #46 / #74 | 意図的停止をエラーから分離し、次の応答へ影響させない | pending | 不可・固定候補のpass未登録 | — / — |
| STOP-UX-1-ERROR / #46 / #74 | 停止要求のない実際の終了コード137を異常として表示する | pending | 不可・固定候補のpass未登録 | — / — |
| PROMPT-TAB-1 / #42 / #75 | 入力欄のTab/Shift+Tabをフォーカス移動にする | pending | 不可・固定候補のpass未登録 | — / — |
| CLI-PATH-1 / #76 / #77 | 自動検出CLIパスを表示し、手動指定から自動検出へ戻せるようにする | pending | 不可・固定候補のpass未登録 | — / — |
| RESTORE-TARGET-1 / #39 / #81 | checkpoint/Revertが異なるroot・Worktree modeへ復元しないようにする | pending | 不可・固定候補のpass未登録 | — / — |
| SWING-CUA-BASIC-1 / #80 / #82 | 最小Swing fixtureのComputer Use接続・基本入力・再起動後再識別を確認する | pending | 不可・固定候補のpass未登録 | — / — |
| SWING-CUA-WINDOW-1 / #80 / #82 | 最小SwingのComputer Use popup/modal取得不具合を切り分ける | pending | 不可・固定候補のpass未登録 | — / — |

## #71 / TOKEN-PANEL-HIERARCHY-1: トークン数パネルの文字階層・数値・グループを読みやすくする

PR: [#72](https://github.com/shinma06/cursor-in-android-studio/pull/72)

前提・対象build: 確認区切りのdevelop候補SHA・ZIP/JAR hash・ロード実体を固定し、当該PRの包含を確認する。使い捨てプロジェクトで既存値または合成CLI fixtureを使用し、合成値なら証拠へ明記する。CLI通信不要。

1. 明細を開き、タイトル・補足・項目名・数値を見比べる。
2. 通常幅と約280〜320px、明暗テーマを切り替えて右端まで確認する。
3. 全値28,791/141/5,748/0、一部値、全欠損を表示する。
4. 同じ明細ボタン・×・Spaceで開閉し、再表示と入力へのfocus移動を試す。
5. 元のテーマと幅へ戻す。

期待結果: タイトルは少し大きい太字、補足は小さく、数値はラベルより識別しやすい。ラベル・右揃え数値・×が重ならず、桁区切りと0を保つ。入力/出力とキャッシュが両方ある場合のみ区切りを表示。欠損行・不要な空行は出さず、全欠損は短い案内。開閉・再表示で数値を保持する。

初期登録時のGPT: pending — headless合成画像のみ観察済み。実IDEのGUIは未実施。
初期登録時の人間: pending — 新しい固定候補で未実施。過去の観察結果を転記しない。

修正Issue/PR: 未登録 / 未登録
再確認: 固定候補全体の必要Caseを再確認する。製品の不具合を観察したら専用修正Issue/branch/PRへ紐付け、修正を含む新候補で再確認する。
次の操作: 区切りの固定候補・検証担当・資材を決め、手順を実施して日時・確認者・証拠を記録する。
根拠: https://github.com/shinma06/cursor-in-android-studio/issues/71#issuecomment-5557554048, https://github.com/shinma06/cursor-in-android-studio/blob/ece9dc4027077ccc6cbe6448ad530e814eefb9b8/docs/loop-engineering/runs/2026-09-06-token-panel-hierarchy.md

<details><summary>過去の観察（新候補へ転記しない）</summary>

```json
[
  {
    "status": "pending",
    "kind": "non_gui_observation",
    "head": "364c17f53f0d6cf7a6b13b284f5e287c63e75cb2",
    "reason": "280/350px・模擬明暗・全値/一部/空の12画像を生成し4条件を目視したが実IDEのGUI passではない。",
    "evidence": "https://github.com/shinma06/cursor-in-android-studio/issues/71#issuecomment-5557554048"
  }
]
```

</details>


## #46 / STOP-UX-1-STOP: 意図的停止をエラーから分離し、次の応答へ影響させない

PR: [#74](https://github.com/shinma06/cursor-in-android-studio/pull/74)

前提・対象build: 確認区切りのdevelop候補SHA・ZIP/JAR hash・ロード実体を固定し、当該PRの包含を確認する。使い捨てプロジェクト、Pro/Teams、Askを使用。通常応答は2回まで。

1. 「1から100までの数字を1行ずつ出力してください。ファイル操作とコマンド実行は不要です。」を送信する。
2. 応答途中で停止ボタンを押す。
3. 停止後の本文・状態表示・入力欄・通知を確認する。
4. 短い通常応答を送信して完了させる。

期待結果: 「停止しました」と表示して部分本文を保持する。エラー行・通知・ダイアログが出ず、入力と送信が復帰する。次応答が正常完了し、前の停止による本文消去や停止表示への逆戻りがない。

初期登録時のGPT: pending — 当該2項目は以前に人間へ引継ぎ、旧固定buildのpassがある。新候補は未実施。
初期登録時の人間: pending — 新しい固定候補で未実施。過去の観察結果を転記しない。

修正Issue/PR: 未登録 / 未登録
再確認: 固定候補全体の必要Caseを再確認する。製品の不具合を観察したら専用修正Issue/branch/PRへ紐付け、修正を含む新候補で再確認する。
次の操作: 区切りの固定候補・検証担当・資材を決め、手順を実施して日時・確認者・証拠を記録する。
根拠: https://github.com/shinma06/cursor-in-android-studio/issues/46#issuecomment-5558463227, https://github.com/shinma06/cursor-in-android-studio/issues/46#issuecomment-5558477249, https://github.com/shinma06/cursor-in-android-studio/blob/42c08c7d57ee5ff70acec67b06f902aade272ad0/docs/loop-engineering/runs/2026-09-06-intentional-stop.md

<details><summary>過去の観察（新候補へ転記しない）</summary>

```json
[
  {
    "status": "pass",
    "actor": "human",
    "head": "42c08c7d57ee5ff70acec67b06f902aade272ad0",
    "artifact_sha256": "775f78cd0e107c7ec23ba15cb3a397fe1df7f567fbf5cef54dbc4ccf22aa2093",
    "observer": "ユーザー（Issueコメントに個人名記載なし）",
    "recorded_at": "2026-09-06T09:58:55Z",
    "observed_at": null,
    "evidence": "https://github.com/shinma06/cursor-in-android-studio/issues/46#issuecomment-5558477249",
    "reason": "案内した意図的停止と次応答の2項目について「OK」を受領。異常終了137は含まない。実際の操作日時はコメントから確定不可。"
  }
]
```

</details>


## #46 / STOP-UX-1-ERROR: 停止要求のない実際の終了コード137を異常として表示する

PR: [#74](https://github.com/shinma06/cursor-in-android-studio/pull/74)

前提・対象build: 確認区切りのdevelop候補SHA・ZIP/JAR hash・ロード実体を固定し、当該PRの包含を確認する。元のCLI設定を控える。使い捨てfixtureに内容が「#!/bin/sh」と「exit 137」の2行で実行権限を持つファイルを用意する。外部通信・編集なし。

1. SettingsのCLIパスを検証用実行ファイルへ一時変更する。
2. 使い捨てプロジェクトから送信し、停止ボタンは押さない。
3. 終了時の表示を確認する。
4. CLI設定を元へ戻し、処理・ダイアログが残っていないことを確認する。

期待結果: 「停止しました」へ誤分類せず、実際の異常として表示する。検証後に元のCLI設定へ復元できる。

初期登録時のGPT: blocked — Computer UseがSettingsダイアログを取得できず、設定変更も検証送信も未実施。製品failではない。
初期登録時の人間: pending — 新しい固定候補で未実施。過去の観察結果を転記しない。

修正Issue/PR: 未登録 / 未登録
再確認: 固定候補全体の必要Caseを再確認する。製品の不具合を観察したら専用修正Issue/branch/PRへ紐付け、修正を含む新候補で再確認する。
次の操作: 区切りの固定候補・検証担当・資材を決め、手順を実施して日時・確認者・証拠を記録する。
根拠: https://github.com/shinma06/cursor-in-android-studio/issues/46#issuecomment-5558495528, https://github.com/shinma06/cursor-in-android-studio/issues/46#issuecomment-5559176840, https://github.com/shinma06/cursor-in-android-studio/blob/42c08c7d57ee5ff70acec67b06f902aade272ad0/docs/loop-engineering/runs/2026-09-06-intentional-stop.md

<details><summary>過去の観察（新候補へ転記しない）</summary>

```json
[
  {
    "status": "blocked",
    "actor": "gpt",
    "head": "42c08c7d57ee5ff70acec67b06f902aade272ad0",
    "reason": "設定ダイアログを取得できず、本当の137は未確認。",
    "evidence": "https://github.com/shinma06/cursor-in-android-studio/issues/46#issuecomment-5558495528"
  }
]
```

</details>


## #42 / PROMPT-TAB-1: 入力欄のTab/Shift+Tabをフォーカス移動にする

PR: [#75](https://github.com/shinma06/cursor-in-android-studio/pull/75)

前提・対象build: 確認区切りのdevelop候補SHA・ZIP/JAR hash・ロード実体を固定し、当該PRの包含を確認する。使い捨てプロジェクト、Ask、通常送信は1回まで。日本語IMEを使用する。

1. 日本語を含む複数行の未送信入力でTabとShift+Tabを押す。
2. 空欄と文字選択中でも同じ操作を試す。
3. Shift+Enter改行、日本語IME変換確定、@候補の選択と取消を試す。
4. 短い通常応答を完了させ、入力欄の再有効化後にTabとShift+Tabを再確認する。
5. 通常のコードエディタでTab字下げを確認する。

期待結果: 本文や選択を変えず入力外の次・前の操作へfocusが移る。改行・IME確定・mentionで誤送信しない。候補popupにfocusがある間は入力欄のTab処理を強制しない。再生成後も同じ挙動を保ち、通常エディタの字下げは維持する。

初期登録時のGPT: pending — Swing単体キー処理の確認のみ。実IDEのdispatch・IME・mentionは未観察。
初期登録時の人間: pending — 新しい固定候補で未実施。過去の観察結果を転記しない。

修正Issue/PR: 未登録 / 未登録
再確認: 固定候補全体の必要Caseを再確認する。製品の不具合を観察したら専用修正Issue/branch/PRへ紐付け、修正を含む新候補で再確認する。
次の操作: 区切りの固定候補・検証担当・資材を決め、手順を実施して日時・確認者・証拠を記録する。
根拠: https://github.com/shinma06/cursor-in-android-studio/issues/42#issuecomment-5558419104, https://github.com/shinma06/cursor-in-android-studio/blob/6799bb8189b3383a2844aeaf1ef69028f6b2eb35/docs/loop-engineering/runs/2026-09-06-prompt-tab.md


## #76 / CLI-PATH-1: 自動検出CLIパスを表示し、手動指定から自動検出へ戻せるようにする

PR: [#77](https://github.com/shinma06/cursor-in-android-studio/pull/77)

前提・対象build: 確認区切りのdevelop候補SHA・ZIP/JAR hash・ロード実体を固定し、当該PRの包含を確認する。元のCLI上書き設定と通知設定を控える。通信しない実在fixture実行ファイルを用意する。CLI送信は行わない。

1. CLIパスを空欄でApplyし、自動パスと説明を確認する。再表示してApplyが無効であることを確認する。
2. 通知設定だけ変更してApplyし、自動状態を保つことを確認して通知設定を戻す。
3. fixtureのパスを手動指定してApplyし、再表示で保持されることを確認する。
4. 「自動検出に戻す」を押してCancelし、再表示で手動パスが残ることを確認する。
5. 再び「自動検出に戻す」を押してApplyし、再表示で自動に戻ることを確認する。保存XMLの上書き値は空文字または省略を確認する。
6. 手動指定を保存してから空欄Applyでも自動へ戻ることを確認する。
7. 元のCLI設定と通知設定へ復元する。

期待結果: 表示だけで自動検出を手動固定しない。手動/自動切替はApply/OKで保存し、Cancelでは破棄する。表示・検出だけでCLIが起動しない。固定候補なしは単体テストで検証し、実機CLIを削除・移動しない。

初期登録時のGPT: blocked — 最新r3は接続と固定JARロード照合に成功したが、Settingsの画像/AXは背面Welcome。別経路の入力確定も失敗。設定入力・Apply・CLI送信ゼロ。製品failではない。
初期登録時の人間: pending — 新しい固定候補で未実施。過去の観察結果を転記しない。

修正Issue/PR: 未登録 / 未登録
再確認: 固定候補全体の必要Caseを再確認する。製品の不具合を観察したら専用修正Issue/branch/PRへ紐付け、修正を含む新候補で再確認する。
次の操作: 区切りの固定候補・検証担当・資材を決め、手順を実施して日時・確認者・証拠を記録する。
根拠: https://github.com/shinma06/cursor-in-android-studio/issues/76#issuecomment-5558612480, https://github.com/shinma06/cursor-in-android-studio/issues/76#issuecomment-5562501139, https://github.com/shinma06/cursor-in-android-studio/blob/34a763ce6e1dcd263446db8e4da11ffc5c87be64/docs/loop-engineering/runs/2026-09-06-cli-path.md

<details><summary>過去の観察（新候補へ転記しない）</summary>

```json
[
  {
    "status": "blocked",
    "actor": "gpt",
    "head": "34a763ce6e1dcd263446db8e4da11ffc5c87be64",
    "artifact_sha256": "47c7c01a3b29cb7a720a36dc0c3e8a0c10520cc0e6f0aa513b299365ffc1debd",
    "loaded_jar_sha256": "98c91b7ab891afbb55dce5749d315a7d8f05f16f494f24a37413c7d1a5c633e6",
    "run": "20260907-cli-path-r3",
    "reason": "設定入力を開始できず。検証後は元PR74 buildに復元し設定XMLの開始前とのバイト一致を確認。",
    "evidence": "https://github.com/shinma06/cursor-in-android-studio/issues/76#issuecomment-5562501139"
  }
]
```

</details>


## #39 / RESTORE-TARGET-1: checkpoint/Revertが異なるroot・Worktree modeへ復元しないようにする

PR: [#81](https://github.com/shinma06/cursor-in-android-studio/pull/81)

前提・対象build: 確認区切りのdevelop候補SHA・ZIP/JAR hash・ロード実体を固定し、当該PRの包含を確認する。共有callsite接続・日本語理由UI・実行中CLIとの競合抑止を実装して再テスト/独立レビュー後に実行する。現PR単独は通常modeも接続未了でリリース不可。使い捨てGit fixtureへcommit済み・事前untracked・別rootの同名ファイルを用意。Pro/Teams、permission defaultを維持。

1. DEFAULTで1ファイルを編集し、Diff表示とRevertを試す。
2. 同じ対象を再編集し、保存済み・未保存それぞれで古いRevertを試す。
3. DEFAULT checkpoint後にtracked編集と新規untracked追加を行い、checkpointを復元する。
4. ISOLATEDで編集してcheckpoint/Revertを試し、元rootと分離先の内容を比較する。
5. ISOLATEDとDEFAULTを両方向へ切り替え、古いカード/checkpointを操作する。準備中切替でもCLIと保存targetを照合する。
6. root/modeのない旧履歴・root移動/不明・新規DEFAULT履歴を比較する。
7. 応答中の復元を試し、実行中CLIと競合しないことを確認する。

期待結果: 通常DEFAULTのDiff・Revert・checkpoint復元が動作する。古いRevertは日本語理由で拒否し後の保存済み/未保存内容を保持。checkpointはtrackedを戻し新規untrackedだけ削除して事前untrackedを保持。ISOLATED・対象不明・異なるroot/modeは日本語理由で拒否し両rootを不変に保つ。ISOLATEDのDiffは閲覧可能。DEFAULTへ戻しても古いisolatedカードは拒否。旧履歴は安全拒否し新規DEFAULT履歴は復元可能。

初期登録時のGPT: blocked — GUI環境以前に共有callsite未接続。旧APIは対象不明として通常modeも安全拒否する。環境blockedとは区別する。
初期登録時の人間: pending — 新しい固定候補で未実施。過去の観察結果を転記しない。

修正Issue/PR: 未登録 / 未登録
再確認: 固定候補全体の必要Caseを再確認する。製品の不具合を観察したら専用修正Issue/branch/PRへ紐付け、修正を含む新候補で再確認する。
次の操作: PMがPR74の所有境界と接続担当を解決し、未実装接続を完了してから固定統合候補で確認する。GUI未確認だけの例外として単独リリースしない。
根拠: https://github.com/shinma06/cursor-in-android-studio/issues/39#issuecomment-5562606190, https://github.com/shinma06/cursor-in-android-studio/issues/39#issuecomment-5562663611, https://github.com/shinma06/cursor-in-android-studio/blob/215794eab3927a015e24ec7fa59c82806226c713/docs/loop-engineering/runs/2026-09-07-restore-target-safety.md


## #80 / SWING-CUA-BASIC-1: 最小Swing fixtureのComputer Use接続・基本入力・再起動後再識別を確認する

PR: [#82](https://github.com/shinma06/cursor-in-android-studio/pull/82)

前提・対象build: 確認区切りのdevelop候補SHA・ZIP/JAR hash・ロード実体を固定し、当該PRの包含を確認する。固定sourceからplain Javaとjpackage .appを作り同一JAR hashを照合。fixture専用build/run/bootとGUI担当/予約を確認する。人間が直接操作してもComputer Use経路のpassにはならない。人間確認者はGPT/CUA操作と結果の証拠を照合する。

1. plain Javaの一覧表示とgetAppを比較する。
2. .appへComputer Useで接続し、buttonのAXクリックで回数増加を確認する。
3. Computer UseのtypeText+Returnと値の直接設定+Returnを試す。
4. 一覧末尾へのキーscrollを試す。
5. fixtureだけ停止・再起動し、新bootで再識別してComputer Useの座標クリックとscroll APIを試す。

期待結果: Computer Useの結果表示と補助イベントが一致する。再起動後に同じbuild/JARと新bootを区別できる。失敗経路と成功経路を分ける。日本語直接設定をIME変換passにしない。

初期登録時のGPT: pending — 旧fixture sourceで.app基本操作と再識別が成功。plain識別と初回座標/scrollはblocked、再起動後は成功。新固定候補で未実施。
初期登録時の人間: pending — 未実施。人間が直接GUI操作してもComputer Use経路の合格にはならない。固定候補でGPT/CUA操作と結果証拠を照合する。

修正Issue/PR: 未登録 / 未登録
再確認: 固定候補全体の必要Caseを再確認する。製品の不具合を観察したら専用修正Issue/branch/PRへ紐付け、修正を含む新候補で再確認する。
次の操作: 区切りの固定候補・検証担当・資材を決め、手順を実施して日時・確認者・証拠を記録する。
根拠: https://github.com/shinma06/cursor-in-android-studio/issues/80#issuecomment-5562668110, https://github.com/shinma06/cursor-in-android-studio/issues/80#issuecomment-5562710046, https://github.com/shinma06/cursor-in-android-studio/blob/70fe9fe0fd318b258bee05717723d50a813ef8a3/docs/loop-engineering/runs/2026-09-07-swing-cua.md, https://github.com/shinma06/cursor-in-android-studio/pull/82#issuecomment-5562698895

<details><summary>過去の観察（新候補へ転記しない）</summary>

```json
[
  {
    "status": "pass",
    "actor": "gpt",
    "observer": "gpt-80-swing-20260907",
    "head": "214d6ad8d1ddae914034a4d804da6a64434a24f6",
    "jar_sha256": "2e00a23dc9019a83c096a67e53b9234dd5fd141c3dd62781845b7cbbb7792376",
    "run": "swing-cua-r1",
    "recorded_at": "2026-09-06T22:36:38Z",
    "reason": ".app接続、AXクリック、typeText+Return、値の直接設定+Return、キーscroll、再識別、再起動後座標/scrollが成功。",
    "evidence": "https://github.com/shinma06/cursor-in-android-studio/issues/80#issuecomment-5562668110"
  },
  {
    "status": "blocked",
    "actor": "gpt",
    "observer": "gpt-80-swing-20260907",
    "head": "214d6ad8d1ddae914034a4d804da6a64434a24f6",
    "jar_sha256": "2e00a23dc9019a83c096a67e53b9234dd5fd141c3dd62781845b7cbbb7792376",
    "run": "swing-cua-r1",
    "recorded_at": "2026-09-06T22:36:38Z",
    "reason": "plain Javaは一覧に出てもInvalid app。初回座標/scrollはnoWindowsAvailable。全面操作passではない。",
    "evidence": "https://github.com/shinma06/cursor-in-android-studio/issues/80#issuecomment-5562668110"
  }
]
```

</details>


## #80 / SWING-CUA-WINDOW-1: 最小SwingのComputer Use popup/modal取得不具合を切り分ける

PR: [#82](https://github.com/shinma06/cursor-in-android-studio/pull/82)

前提・対象build: 確認区切りのdevelop候補SHA・ZIP/JAR hash・ロード実体を固定し、当該PRの包含を確認する。固定source/JAR/run/bootを識別したfixtureを使用。人間の直接クリックでpopup/modalが使えてもCUA取得経路のpassではない。人間はGPT/CUA操作の画像/AX/イベントを照合する。製品IDE受入とは別ケース。

1. Computer UseでPopupボタンをクリックし、AX・画像・OPENイベントを比較する。
2. Escape後にPopupを開き、DownとReturnで選択結果を確認する。
3. Computer UseでDialogボタンをクリックし、子Dialogのタイトル・入力欄・確定ボタンと画像を確認する。
4. 同じアプリを1回再取得して比較する。
5. 同じ背面窓のままなら打ち切り、自分のfixtureだけ終了する。

期待結果: popup展開表示と選択結果を別々に判定する。modalを開いた後は背面親窓ではなく子DialogをCUAで取得する。取得できなければ環境blockedを残し、製品failやpassへ転換しない。

初期登録時のGPT: blocked — 旧実測ではpopupキー選択結果のみ成功、展開画像は未達。ModalはDIALOG_OPEN後も背面親窓を返し再取得でも回復しない。
初期登録時の人間: pending — 未実施。人間が直接GUI操作してもComputer Use経路の合格にはならない。固定候補でGPT/CUA操作と結果証拠を照合する。

修正Issue/PR: 未登録 / 未登録
再確認: 固定候補全体の必要Caseを再確認する。製品の不具合を観察したら専用修正Issue/branch/PRへ紐付け、修正を含む新候補で再確認する。
次の操作: 区切りの固定候補・検証担当・資材を決め、手順を実施して日時・確認者・証拠を記録する。
根拠: https://github.com/shinma06/cursor-in-android-studio/issues/80#issuecomment-5562668110, https://github.com/shinma06/cursor-in-android-studio/issues/80#issuecomment-5562710046, https://github.com/shinma06/cursor-in-android-studio/blob/70fe9fe0fd318b258bee05717723d50a813ef8a3/docs/loop-engineering/runs/2026-09-07-swing-cua.md, https://github.com/shinma06/cursor-in-android-studio/pull/82#issuecomment-5562698895

<details><summary>過去の観察（新候補へ転記しない）</summary>

```json
[
  {
    "status": "pass",
    "actor": "gpt",
    "observer": "gpt-80-swing-20260907",
    "head": "214d6ad8d1ddae914034a4d804da6a64434a24f6",
    "jar_sha256": "2e00a23dc9019a83c096a67e53b9234dd5fd141c3dd62781845b7cbbb7792376",
    "run": "swing-cua-r1",
    "recorded_at": "2026-09-06T22:36:38Z",
    "reason": "PopupのDown/Returnによる選択結果を画面とイベントで確認。展開画像passではない。",
    "evidence": "https://github.com/shinma06/cursor-in-android-studio/issues/80#issuecomment-5562668110"
  },
  {
    "status": "blocked",
    "actor": "gpt",
    "observer": "gpt-80-swing-20260907",
    "head": "214d6ad8d1ddae914034a4d804da6a64434a24f6",
    "jar_sha256": "2e00a23dc9019a83c096a67e53b9234dd5fd141c3dd62781845b7cbbb7792376",
    "run": "swing-cua-r1",
    "recorded_at": "2026-09-06T22:36:38Z",
    "reason": "Popup画像は親窓のみまたはScreenshot unavailable。Modalは背面親窓を誤取得し再取得も回復せず。",
    "evidence": "https://github.com/shinma06/cursor-in-android-studio/issues/80#issuecomment-5562668110"
  }
]
```

</details>

