# Context Usage（#59）

Case: CONTEXT-USAGE-1 / status: pending / implementation owner: gpt-context-20260906。
GUI担当は中央進行役がhost lease取得後に割当。今回writerはGUI・IDE配置・CLI送信をしていない。
統合依存: #57を先にmerge。ComposerPanelのcontext追加とoverflow撤去が両立することを確認する。

## 準備

最新PR HEAD/base、ZIP絶対パス/SHA-256、ロードしたJAR・IDE PID・fixture識別をIssueへ記録する。
共有mainプロジェクトではなくdisposable fixtureを使う。予算: 30分、3 fixes、Pro/Teamsで最大2送信。
既存の別PR用GUI leaseを横取りしない。数値確認はまず合成CLI fixtureのresult.usageを使ってよいが、
その場合は合成と記録し、現在の実CLI能力を新たに検証したとは言わない。

## 操作と期待

1. 送信/停止ボタンの直前左に18pxリング（24px hit area）があり、tooltipがShow Context Usage。
2. 押すと入力欄上に角丸Context Usage。初期は使用率・残量/種別別内訳/4種の数値が取得不可。
3. 入力欄・エディターへfocus、mode popup/model popupを開閉、Escapeでselectorを閉じる。
   Context Usage自体は残り、selectorが前面に重なる。既存selectorの取消動作が維持される。
4. 同じリングで閉じ、再度開く。×で閉じ、リングにkeyboard focusが戻る。Enter/Spaceからも開閉。
5. result.usageがある応答完了で入力/出力/cacheRead/cacheWriteが表示される。
   開閉で値が消えない。生成中の架空割合や4値の合計は表示されない。
6. 次送信、新規chat、履歴再開、停止で古い値が消える。usageなし/不正値では取得不可。
7. 幅320pxと通常幅、light/dark、長い会話、入力12行、ウィンドウ高さを縮めた状態で
   数値・×・送信/停止が利用でき、本文とパネルのレイアウトが崩れないことを観察する。

実画面未観察の項目を単体テストやbuild成功でpassにしない。
合否・画像・対象buildと最終fixture/プロセス状態をIssueに記録してleaseを解放する。
