# 共通ボタンの自然なフォーカス表示（#215）

[Issue215](https://github.com/shinma06/cursor-in-android-studio/issues/215)はModel / Auto行の明るい角丸囲み枠を起点に、同目的の独自フォーカス枠を共通箇所で修正する。[発見元QA188](https://github.com/shinma06/cursor-in-android-studio/issues/188#issuecomment-5643361691)の既存Dark/Light passは変更しない。

## 全caller調査と変更範囲

固定develop9979266の全hasFocus/isFocusOwner/drawRoundRect/isFocusPainted/pillColorを検索し、独自focus strokeは下記3箇所と確認した。

| 描画の正本 | 全利用箇所 | 修正 |
| --- | --- | --- |
| SelectorButton | ModeSelectorのAgent/Plan/Ask、ModelSelectorのprint/ACP入口、ModelOptionsPopupPanelのModel/Auto・option・戻る行、Composerの送信/Stop、トークン数パネルの閉じる | focus strokeを削除。通常のボタンはIDE標準ActionButton.pressedBackgroundでhover/focusを示す。固定色Modeは既存userBubbleBackgroundをpillColorより優先して操作対象を識別する。Modeのcaption/icon/foregroundと通常時のpillColorは維持 |
| ModelPopupPanel内AutoToggle | ModelOptionsPopupPanelのThinking/Fast等のboolean option | focus strokeを削除。hover/focus時だけ既存trackとuserBubbleBackgroundを混ぜる。ON/OFFのtrack色の差、白いつまみと位置、寸法は維持 |
| ContextUsageView内TokenCountsButton | Composerのトークン数を開閉するボタン | focus strokeを削除。hover/pressed/focusはIDE標準ActionButton.pressedBackgroundを用い、中央に置くdocument glyphを維持 |

AgentUiColors.RoundedBorderは常設surface境界、SessionTabStripのround rectangleはアイコンの図形で、focus枠ではないため変更しない。標準リストの選択背景/チェック、hover時の子popup展開、入力カーソル、検索欄やIME、その他の装飾は対象外。新しい色体系・共通描画基盤・依存は追加しない。

## 操作と所有権

JButton/JToggleButtonの既存focusable、InputMap/ActionMap、action listenerは維持する。SelectorPopupControllerのfocus/outside-click listenerとclose/disposer、ModelOptionsのLEFT/ENTER/ESCAPE・検索・選択、ComposerのEnter送信とボタンの送信/Stop切替、PromptFocusTraversalとEditorTextFieldのIME経路は変更しない。非対応/実行中のenable制御、accessibleName/descriptionも変更しない。

修正は3ファイル内の描画とtoggleのrollover有効化だけ。新規非同期処理や所有オブジェクトはなく、Android Lifecycle/Context/DB/coroutineはこのSwing描画変更では使用しない。

## CLI検証の範囲

NaturalFocusPaintTestはEDT上の画像描画と一時的なFocusManagerで、独自枠のないfocus/hover一致、通常状態との違い、Modeの識別/寸法、toggleのON/OFFの違い、標準Space actionとdisabled時の無反応を検証する。JBColorのLight/Dark、サイズ28/32、device scale1/1.25/1.5/2を合成確認し、終了時にtheme/UIManager/focus managerを戻す。無地のSelectorとtokenはcomposer背景へ合成し、対象SDKのLight/Dark標準pressed背景（alpha含む）で通常時とのコントラスト比が1.25以上になる回帰チェックも行う。この値は旧Dark背景差約1.024への後戻りを検出する下限であり、アクセシビリティ適合の判定ではない。これは実IDEテーマ/Compact/OS focusのGUI観察ではない。

既存SelectorGeometryTest、ComposerControlsTest、ModelOptionsPopupPanelTest、SelectorPopupControllerTest、PromptFocusTraversalTest、ContextUsageTest等を実行する。tokenの中央配置、popup選択/取消/所有解放、入力と選択の既存契約を保持する。送信/Stopのcall siteはbyte不変を照合し、実機操作はCaseに残す。

## GUI引継ぎ

[Case215](../verification/changes/issue-215.json)の3CaseでModel/Mode/option/戻る・toggle・送信Stop・token開閉を網羅する。各CaseはDark/Light × 通常/Compactの4条件で、マウスとキーボード両方を確認する。全範囲の文字/選択/hover/focus識別、Tab/Shift+Tab/Space/Enter/矢印/Esc、IME確定と送信、既存寸法を確認する。

GUI/install/restartは実装担当未実施。指定担当が固定source/Plugin ZIP SHA256/installed全JAR/起動ロード実体を一致させ、Caseごとの結果と画像を記録する。合成描画の成功や#188の旧build passを新候補のGUI passへ転記しない。develop統合後は標準QAへ双方向handoffし、main受入を別途追跡する。#45/#28/#254の別writer領域を編集しない。
