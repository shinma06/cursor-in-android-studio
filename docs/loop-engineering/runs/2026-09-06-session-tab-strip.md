# セッションタブ部品 / SESSION-TABS-UI

親 #62、部品 #64、製品接続 #65。owner gpt-session-tabs-20260906。

## 現在の状態

未接続のSwing部品を追加。自動テストは表示データ・イベント・scrollモデルを検証する。
IDEへの配置、実機GUI、Cursor CLI送信は未実施。#55のhuman handoff中はGUIへ触らない。
GUI受入はpending。部品単体のoffscreen描画やunit testをGUI passとしない。

## 接続契約

`SessionTabStrip.setTabs(List<SessionTabPresentation>, selectedId)`をEDTで呼ぶ。
`onSelect(id)`/`onClose(id)`/`onMove(id, finalIndex)`をownerへ通知するcontrolled component。
callback後ownerが状態を更新しsetTabsする。source除去後の最終indexは#63のmoveと一致する。
部品は会話本文・設定・processを所有しない。選択後のcomposer focusも#65が担当。
閉じる×は選択/hover時に表示、幅は常に確保して名前が揺れない。
9文字はUnicode grapheme単位。完全名と閉じるの意味はtooltipへ残す。
左右/Home/Endはタブ選択、Deleteは選択タブを閉じる、Alt+Shift+左右は並べ替え。
ドラッグ中Escapeで取消、端でauto-scroll。removeNotifyでtimerを停止する。

## 固定buildによる受入

#65接続後の識別済みbuild、または指定GPTがlease取得後に管理する専用GUI fixtureへ本部品を載せる。
HEAD/base/ZIP SHA256/ロードJAR/fixture/観察者/run IDを記録し、次を確認する。

1. 3タブと20タブ。水平配置、左アイコン、中央名、New Agent、9文字と10文字・結合濁点・絵文字。
2. activeはchat背景と一致、下境界なし。inactiveは区切りあり。hover/activeだけ右×が見える。
3. 幅320px/広幅/light/dark/IDE拡大率。領域hoverでだけ下部の横scrollbarが見え、つまんで移動可能。
   barへ移った際に消えない。外へ移ると消える。trackpad/wheelでも横へ移動できる。
4. 選択タブへ自動scroll。通常clickで選択、×だけclose。×から外へdragして離しても誤close/選択なし。
5. 左→右、右→左、隣、同位置へD&D。端に保持して見切れたタブまで移動、順序が指定どおりになる。
6. D&D中Escape/パネル非表示で取消、timer残留なし。keyboard選択/close/並べ替えと全名tooltip。
7. #65で会話・draft・mode/model・input focusが正しいタブへ結び付くことを別途確認。

budget: 15min、CLI送信0回（部品QA）。結果はcase別にpass/fail/blocked/pendingを記録。
