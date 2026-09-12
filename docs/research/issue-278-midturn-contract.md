# #278 実行中入力とローカルqueueの境界

2026-09-12 / base `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。

調査中。Cursor公式IDE内panel、interactive CLI、非TTY print、ACP、Cloud専用機能を区別し、
実行中追加入力/steering/side question/goalの公開契約を確認する。
#48 / PR279のローカルqueueは前turnの終端後に次turnを送る機能として比較する。
公開呼出経路がある場合だけ少数の合成実測を行う。#146原本/初期wireの再作成・製品編集・GUI・認証変更は対象外。
