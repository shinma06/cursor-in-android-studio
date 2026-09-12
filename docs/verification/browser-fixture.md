# Browser QAの管理HTTP/TLS入力

#311。QA #164のBrowser 4 Caseと#182の共用入力を用意する。製品/GUI変更ではない。Python標準ライブラリと既存`openssl`を使う[fixture](fixtures/browser_fixture.py)を固定sourceから起動し、[CLIテスト](../../scripts/workflow/test_browser_fixture.py)で制御入力を検査する。外部HTTP参照・任意file配信・shell実行endpointはない。禁止schemeの固定リンクは利用者の明示操作用で、外部HTTP資源を読み込まない。

## 起動・記録・終了

Python 3.9以降、`openssl req -addext`が使えるOpenSSL、loopback TCPが必要。指定試験環境のcheckoutで実行する。IDEやJCEFは起動しない。

```sh
python3 docs/verification/fixtures/browser_fixture.py serve
```

標準出力に、このrun専用の`browser-qa-...`ディレクトリが1行表示される。`manifest.json`が`running`となってから出力する。runディレクトリは所有者だけがアクセスできる一時領域に作成され、IPv4 `127.0.0.1`、利用可能ならIPv6 `::1`のHTTP/HTTPSがそれぞれport 0から動的割当される。固定port・外部interface・reverse DNSは使わない。IPv6が利用できなければmanifestへ理由を記録し、IPv6成功とは扱わない。`--ipv4-only`はIPv6を明示的に未実施とする選択肢。

別terminalで表示された実ディレクトリを変数に入れる。以下の`/実際の/browser-qa-...`は表示値に置き換える。架空endpointを試験に使わない。

```sh
browser_qa_run='/実際の/browser-qa-...'
python3 -m json.tool "$browser_qa_run/manifest.json"
```

manifestの`urls.http4/https4/http6/https6`が実際の接続先。`run_id`、fixture source SHA256、CA/server証明書SHA256、stateも記録する。`requests.jsonl`にはrun ID・時刻・要求path・status・held/released/cutを記録する。request headersやbodyは記録しない。runごとのcontrol tokenを使い、他runのreleaseや認証なしの停止を拒否する。manifest/token/private keysは公開ログへ添付しない。GUI証拠には必要なrun ID・source/証明書hash・URLと該当要求行だけを抽出する。

```sh
python3 docs/verification/fixtures/browser_fixture.py release "$browser_qa_run" first
python3 docs/verification/fixtures/browser_fixture.py stop "$browser_qa_run"
python3 docs/verification/fixtures/browser_fixture.py cleanup "$browser_qa_run"
```

releaseはそのrunに既に到着したtagだけを解放する。stopはmanifestのportを無条件に使わず、loopback listenerのrun identityを照合する。起動terminalのCtrl+C/SIGTERMも同じ終了処理。終了は保留接続を解放し、待受と処理threadを終了してからstateを`stopped`にする。cleanupは停止後の所有marker・既知fileのみを検査して削除し、symlink/未知file/稼働中状態では削除しない。証拠を抽出してから片付ける。強制終了等で状態を確認できない資材を、経過時間だけで削除しない。

## routeとCase対応

表のpathは実manifest URLの後ろへ付ける。通常のページに外部subresourceはない。A/Bを別projectへ開くときも、同じfixture内の固定ページを使う。

| 管理入力 | 返す内容と記録 | 共用Case・残るGUI確認 |
|---|---|---|
| `/a`、`/b`、`/a#target` | A↔Bの通常link・anchor、title、reload用の毎要求記録 | BROWSER-ENTRY-NAVIGATION、#182 ACTIONS。about:blankの初期無要求、戻る/進む、URL追従、keyboard・狭幅はGUI待ち |
| `/redirect` | 302、Location `/b` | URL-BOUNDARY。HTTP redirect後のURL/履歴 |
| Aの禁止schemeリンク、`/redirect-file`・`-javascript`・`-data`・`-custom` | 固定の非HTTP targetを明示操作で供給。fileは実ファイルを指さない試験名 | URL-BOUNDARY。取消・日本語案内・現在ページ保持・外部起動なし。自動追従で別資源へ逃がさない |
| Aの「popup B」 | 明示クリック時だけ`window.open('/b', '_blank')` | URL-BOUNDARY、popup未対応の案内。新しいBrowserを今回自動起動しない |
| `/404`、`/500` | 指定HTTP statusと固定body | ERROR-SUPPORT。JCEF status/error表示とAへ回復 |
| `/cut` | Content-Length 65536に対して`partial`だけ返して切断。`cut`記録 | ERROR-SUPPORT。CLIのIncompleteReadとJCEF接続エラーを別に記録 |
| `/delay` | 1秒待って200 | 遅延が発生することの基本確認。厳密なclose順序には次行を使う |
| `/hold/<tag>` | 到着時`held`記録。明示release後200、120秒未解放なら504。tagはASCII英数/`_`/`-`の1–32文字、最大64tag/run | LIFECYCLE-PROJECT、#182 LIFECYCLE。held→content/project close→再open→releaseの順を記録。Bの保持、古いcallback混入、hideとdisposeの区別はGUI待ち |
| `/検索?q=日本語` | Unicode検索ページ200 | URL-BOUNDARY。正規化・IDN/TLS接続・描画を分ける |
| 停止済みrunの直前URL | 待受終了直後に接続拒否をCLIで確認する。別processに再割当されたportは使わない | ERROR-SUPPORT。固定番号を未listenと仮定しない。GUI実施時にも未listenを照合する |

holdは要求先と同じrunディレクトリでreleaseする。毎試験で別tagを使う（例`content-close`、`project-close`）。同じtagへの複数要求は一緒に解放される。held行の到着前にreleaseすると404であり、未到着を成功と記録しない。旧runのreleaseは新runへ配送しない。

## HTTPS・IDN・IPv6

runごとに2日間だけ有効な試験CAとserver証明書を生成する。SANは`localhost`、`127.0.0.1`、`::1`、`xn--r8jz45g.xn--zckzah`（`例え.テスト`）。CAとserverのprivate keyはrun内だけに置き、配布・trustへの登録はしない。GUI再開が証明書期限後なら新runを作り、新しいhash/接続条件で記録する。

CLIではmanifestの実HTTPS IPv4 URLを使い、試験CAを明示する。`-k`/`--insecure`/hostname検証無効化は使わない。

```sh
browser_qa_https=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["urls"]["https4"])' "$browser_qa_run/manifest.json")
curl --noproxy '*' --cacert "$browser_qa_run/ca.pem" "$browser_qa_https/a"
browser_qa_port=${browser_qa_https##*:}
curl --noproxy '*' --cacert "$browser_qa_run/ca.pem" \
  --resolve "xn--r8jz45g.xn--zckzah:$browser_qa_port:127.0.0.1" \
  "https://xn--r8jz45g.xn--zckzah:$browser_qa_port/%E6%A4%9C%E7%B4%A2?q=%E6%97%A5%E6%9C%AC%E8%AA%9E"
```

`--resolve`はこのcurl呼出しだけの名前解決で、通常のhosts/DNS/IDE設定を変更しない。TLSのSNI/hostnameはIDNのASCII名として検証される。CLIテストは試験CA＋正しい名前で成功、未信頼CAまたは違う名前で失敗、IPv6 IP SANも実接続で確認する。

**専用GUI環境へ渡す資材**は固定source、`ca.pem`、証明書hash、`hosts.fragment`、manifestの接続URL、route表。loopbackなのでfixtureもGUIと同じ隔離OS環境で起動する。通常環境のCA/hostsを変更して代用しない。

`hosts.fragment`は`127.0.0.1 xn--r8jz45g.xn--zckzah`という具体的な管理名対応を含む。IPv6だけを検査する隔離環境なら対応を`::1`へ置き換え、IPv4 fallbackと混同しない。指定GUI担当が別途認可された専用環境で名前解決と試験CAの信頼を設定する際の入力であり、このscriptは適用しない。読み込むtrust storeが対象JBR/JCEFで有効かは、その環境でのHTTPS成功で確認する。CLIの`--cacert`成功をJCEF trust成功と扱わない。通常OSのtrust/hostsへ自動適用するcommandは含めない。

## 既存資材と未実施境界

公開#208/PR260 `431abcab9720c52f46ad77cbcb265d4721721a2a` の[BrowserUrlTest](https://github.com/shinma06/cursor-in-android-studio/blob/431abcab9720c52f46ad77cbcb265d4721721a2a/src/test/kotlin/com/cursoragent/ui/browser/BrowserUrlTest.kt)と[provider導入・回復手順](https://github.com/shinma06/cursor-in-android-studio/blob/431abcab9720c52f46ad77cbcb265d4721721a2a/docs/development/jcef-browser.md)、[4 Case](https://github.com/shinma06/cursor-in-android-studio/blob/431abcab9720c52f46ad77cbcb265d4721721a2a/docs/verification/changes/issue-208.json)を再利用する。入力拒否listのexample系hostに接続しない。許可入力のport/pathだけを管理接続先へ合わせる。

P0はprovider不在/disabledの隔離IDE構成、P1は既存静的照合済みのQuail4 `AI-261.26222.65.2614.16204760` / bundled JBR `25.0.3+-15898627-b508.16` / mac-arm64とJCEF provider `261.22158.414-mac-arm64`。provider ZIPの既存SHA256は`923cf9706b7cdc863c4a48bba06d69f50af3ab818c164a67e3e2bcd9691fb64e`。これはnative/描画の成功ではなく、既存配布物・手順の再利用条件。最新版比較・独自JBR差替え・native例外注入を増やさない。

CLI確認は管理入力の到達・値・順序だけ。実Browser/GUI/lease、IDE install/nativeロード、OS clipboard、通常hosts/trust変更、外部サイト試験は行わない。#164の既存製品fail/候補pendingや#182の未実施結果を上書きしない。provider初期化Exception/LinkageError/renderer終了の制御入口を用意したとは報告しない。PMが#261承認後の正式candidateと固定sourceを照合し、独立レビュー/CI/STOPからQA/main・cleanupへ追跡する。
