# 最小Swing Computer Use probe（#80 / #73 段階1）

JDKだけの検証画面。製品部品・IntelliJ・CLI・保存設定へ接続しない。
既存SessionTabStripFixtureのbuild/run識別を参考に、標準Swingだけで
アプリ接続と入力経路を先に確かめる。製品UI全体の複製ではない。

## ビルド（GUI操作なし）

cleanなIssue worktreeをcommitしてからmacOSのJDK 21で実行する。
`jpackage --help` の `--type app-image` / `--mac-package-identifier` を使用する。
署名証明書や追加依存は不要。生成先は未作成の使い捨てrunディレクトリにする。

```bash
python3 scripts/gui-fixture/build.py --jdk "$(/usr/libexec/java_home -v 21)" \
  --run swing-cua-r1 --output /tmp/swing-cua-r1
```

JARと.app内JARのSHA-256を照合し、HEAD/base/runを埋め込む。
`manifest.local.json` は環境パスを含むため公開しない。
画面のrun/HEAD/JAR hash/起動ごとのboot IDとstdoutのBOOT/READYを照合する。
アプリは各操作の観測補助イベントをstdoutへ出す。イベントログだけでGUI passにしない。

## 限定GUI試行

GitHubの最新handoffとホスト共通leaseを確認し、指定GUI担当が予約/開始投稿した後だけ起動する。
初回は20分、Cursor CLI送信0回。IDEの起動/設定変更は不要。
以下は起動経路例であり、PID・stdoutファイル・lease情報は非公開のrun記録へ保存する。

```bash
# 経路A: plain Java。終了確認後にBへ進む。同時起動しない。
"$(/usr/libexec/java_home -v 21)/bin/java" -jar /tmp/swing-cua-r1/input/swing-cua.jar
# 経路B: jpackage .app。同じJAR、独立したnative launcher/runtime/bundle識別。
/tmp/swing-cua-r1/app/SwingCuaProbe.app/Contents/MacOS/SwingCuaProbe
```

1. Computer Useでアプリ/画面/AXを取得し、固定build/run/bootを確認。
2. クリック回数、文字入力→Return/確定、Popup選択A/Bを画面とログで照合。
3. 一覧をscrollし、開始時に見えなかった行を選択。
4. 余裕があればDialogの前面取得/入力確定、IME変換中と確定、ドラッグ元文字列からドロップ先へのD&D。
   Unicode文字の直接入力/貼付はIME変換の検証と区別する。
5. 自分が起動したPIDだけ終了し、同じ.appを再起動。
   同じbundle/run/hashと新boot/PIDで再取得し、再度クリックできるか確認。
6. 起動物を終了・残存確認し、ケース別結果と次案をIssueへ記録してleaseを解放。

接続失敗時は名前/観測した識別子/パス指定を限定比較する。繰り返し失敗なら打ち切る。
.app成功でも原因をbundle IDだけと断定しない（native launcher/runtimeも変わる）。
Dialogを開いたイベントと実際に前面Dialogを取得した証拠は別に扱う。
このprobeでSettings、実CLI停止、製品タブD&D、製品IME等をpassにしない。
