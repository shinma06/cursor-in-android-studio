# モデル選択の検索時フリーズ調査（2026-09-06 / #53）

調査担当: gpt-53-20260906-model-perf。基準main: `e0569a579780ea343e9f1dae135a6ed810f69c94`。
関連: [#53](https://github.com/shinma06/cursor-in-android-studio/issues/53)、製品修正担当 [#50](https://github.com/shinma06/cursor-in-android-studio/issues/50)。

## 結果

最新mainのモデル一覧で、検索・クリアの反復によりSwingの描画用部品が蓄積する不具合を再現した。
表示部品を毎回新規生成するrendererと、検索のたびに一覧を取り外して再追加する処理が重なっている。
ユーザーが体験した全遅延をこれだけで説明できるとは断定しないが、実IDEのfreezeスタックと一致する処理経路である。
CLIモデル一覧取得は `AgentUiController.loadModels` のバックグラウンド処理であり、検索のDocumentListenerからは呼ばれていない。

## 実IDEの既存証拠

Android Studio AI-261.26222.65.2614.16204760 の2026-09-06ログ:

- 09:49:42: `UI was frozen for 6826ms`
- 09:49:54: `UI was frozen for 10681ms`

09:49:40 / 09:49:48 / 09:49:53の3 thread dumpすべてでEDTが以下の経路に滞在した。

```text
Component.getHWPeerAboveMe / updateZOrder
Component.addNotify / Container.addNotify / JComponent.addNotify
Container.addImpl / Container.add
ModelPopupPanel.refresh(ModelPopupPanel.kt:183)
ModelPopupPanel$5.removeUpdate または insertUpdate
AbstractDocument.fireRemoveUpdate または fireInsertUpdate
```

削除はBackspace、挿入はDefaultKeyTypedActionのスタックを含む。
当時ロードされていたプラグインのsource SHAはこのログだけでは確定できない。
生ログ全体には利用環境情報が含まれるため転載せず、性能診断に必要な箇所だけを記録した。
今回の調査ではGUI操作・インストール・再起動をしていない。

## 最新mainでの再現

`ModelPopupPerformanceTest` をEDT上で実行。GUIウィンドウやCLI通信は不要。
実CLIから取得済みの `model-list-2026-09-06.txt` を入力に、手動モデルを選択した `ModelPopupPanel` を生成する。
popupのpackと同じJListサイズ測定経路を使うため、検索更新後に `modelList.preferredSize` を読む。

| 操作 | 修正前の結果 |
|---|---|
| `claude` の検索→空文字に戻す→サイズ測定を50回 | `CellRendererPane.componentCount` が210→1,117,613、26.600秒 |
| 検索→不一致語→クリアを20回 | scrollPaneの取り外し60回、8.453秒 |

2テストとも期待値（renderer部品数1以下、scrollPane取り外し0）に対して失敗した。
時間はローカル単発測定で、CIの時間しきい値にはしない。描画部品の数と取り外し回数で回帰を検出する。
この負荷テストは派生IDを含む全CLI候補を直接投入して増幅している。通常の系列別表示に同じ数や秒数を適用しない。

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test --tests '*ModelPopupPerformanceTest'
```

原因は `DefaultListCellRenderer` の呼び出しごとに新規のJPanel/JLabelを返すこと。
JListのサイズ測定がその部品をCellRendererPaneへ追加し、再測定で古い部品が残る。
さらに `refresh` は `results.removeAll()` / `results.add(scrollPane)` を毎回実施し、
実表示時には肥大化した配下へ `addNotify` が走る。実IDEのfreeze記録もこの再追加中に停止していた。

## 修正と担当境界

同時にモデルUIを変更していた#50 writerへ、次の製品修正と回帰テストを委譲した。
同じファイルを並行編集していない。

1. rendererの部品ツリーを1回だけ生成し、各行の名前・選択表示・色・tooltipを上書きして再利用する。
2. 一覧と不一致表示を常設し、カードの表示を切り替える。検索のたびに一覧ツリーを再追加しない。
3. 候補更新は `DefaultListModel.addAll` にまとめる。

回帰テストの統合先も#50。同タスクのモデル名/派生表示変更と整合させる。
#53の文書PRは調査証拠のみを保存し、製品修正と実機受入の完了を意味しない。

## 修正後の確認

#50 worktreeで同じ2テストを実行し、成功を確認した（2026-09-06 13:19 JST）。
constructor変更に合わせた引数調整だけで、反復回数・検査条件は同じ。

| 指標 | 修正前main | #50修正後 |
|---|---:|---:|
| 50回検索/クリア後のrenderer部品数 | 1,117,613 | 1（開始時も1） |
| 20回検索/不一致/クリアでのscrollPane取り外し | 60 | 0 |
| renderer反復テストの時間 | 26.600秒 | 0.047秒 |
| 取り外し検査テストの時間 | 8.453秒 | 0.062秒 |

これは単発の単体テスト結果であり、ユーザーの操作応答時間や一般的な速度向上率を保証するベンチマークではない。
#50担当から全81 testsとbuildPlugin成功の報告があり、調査側でも性能テストXMLのfailures=0、部品数/取り外し回数を読み戻した。
調査文書のみの#53 branchも全テスト成功。製品修正は#50 PRに含まれ、独立レビューと実機受入は後続工程。

## MODEL-PERF-1: 残りのGUI受入

GUI状態: pending。#50の固定ビルドで、ホスト共通leaseを取得した担当が実施する。

- HEAD/base、ZIP hash、ロードしたJAR、fixture、実行前のログ時刻を記録する。
- 手動モデルからModel一覧を開き、`claude` の文字入力/Backspace/クリアを50回程度繰り返す。
- 不一致語→クリア、スクロール、モデル選択、閉じて開き直す、Autoからの検索も確認する。
- 長時間停止や操作を続けるほど遅くなる症状がなく、候補・選択・tooltipが正しいことを観察する。
- 観察中の新しいfreezeログの有無を確認する。発生した場合はスレッドスタックと対象ビルドを記録する。

単体テスト成功を実IDE上のGUI passへ読み替えない。#53は必要なGUI受入までopenを維持する。
