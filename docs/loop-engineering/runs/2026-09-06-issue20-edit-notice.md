# #20: 即時編集と事後Revertの説明

GPTがIssue #1の推奨順と#20のコメントを確認し、P0 #20を選択した。未完了claimはなく、[claim](https://github.com/shinma06/cursor-agent-plugin/issues/20#issuecomment-5553027061)と[ファイル範囲の確定](https://github.com/shinma06/cursor-agent-plugin/issues/20#issuecomment-5553057187)を記録。GPTが主担当・唯一のComputer Use操作者、Claude Proが独立資料レビュー担当。

対象は#20の最初の受入条件（入力付近とSettingsの即時編集・事後Revert説明）に限定した。Worktree復元整合性、現在設定値の一覧、CLI対応表は未完了。ユーザーはGPTの本体修正・記録更新を明示許可し、Cursor/プラグインへの編集依頼は今回生成するfixtureに限定した。

## 結果

- 実装SHA: `66fd0e957458018c66fc325cc372763813c24be1`。ComposerとSettingsに同じ文言を表示する`ImmediateEditNotice`を追加。権限enum・保存値・デフォルト・CLI引数・復元ロジックは変更していない。
- 文言: “Edits can apply immediately, / even with Ask Every Time. / File-card Revert can undo applied edits.”（/は画面の改行）。ファイル編集カードの事後undoを説明し、復元成功を保証しない。
- `./gradlew test buildPlugin`成功。40テスト、失敗・エラー0。修正後のclean SHAから`loop.py build`も成功。
- 独立レビューはClaude Proのclaude.ai認証で、`claude -p --tools '' --output-format text`に限定資料を渡して2回実施。追加のAPI課金経路は使用していない。
- **GUIループはblocked。Cursor送信0回、プラグイン送信0回。編集・Diff・Revertの実行比較は未実施。GUI受入条件は完了していない。**

## Runとビルド

| run | ソースSHA | ZIP SHA-256 | 用途・結果 |
|---|---|---|---|
| `20260906-issue20-r1` | `c20d0a5c638cd3dfa0a347543fc08f5ccb8304e1` | `325c8f8017494c02ed443673fecf513fe51c63f71f4a68a40d3659980a39f290` | 修正前。両面MV-024/021/023を事前宣言。fixture切替がblocked |
| `20260906-issue20-r2` | `66fd0e957458018c66fc325cc372763813c24be1` | `b3c5dc67d91ca99e1a92618bed2a54592d89f9303fef7ed4b07cc16335361cd3` | 修正後。前記にplugin MV-037を追加。runIde起動後のGUI取得がblocked |

環境: Cursor 3.19.7、Android Studio 2026.1（AI-261.26222.65.2614.16204760）。CLI `/Users/shinma/.local/bin/agent` は `2026.09.02-c22c1a3`。通常版プラグインの画面はAgent/Autoを表示していたが、起動中ソースSHA、fixture上のモデル・権限・sandbox・worktreeは未確認。モデル条件を揃えた比較ではない。

修正後ZIP中のプラグインJARと`build/idea-sandbox/.../plugins/cursor-agent-plugin/lib/`のJARはSHA-256 `43499cc73f7ad5ac8e13163c85dff7b424492c93db23983a0afc0b3f4e40f27f`で一致した。これは準備したsandbox実体の一致だけで、GUIでロード中の同一性確認を代替しない。新しいsandbox IDEは`runIde --args=<r2/pluginの絶対パス>`で起動し、再開用に残している。

## Computer Useで観察したこと

観察日: 2026-09-06 JST。このタスク（`01a0724b-f011-7d53-ad2e-70a97985d3f0`）の`mcp__cua_repl`出力が一次証拠。ローカルの各runの`evidence/`に操作とAX抜粋・エラーを保存した。

1. Cursorは最初に開発リポジトリを表示。Cmd+Oはファイル用ダイアログだったため取消し、File → フォルダーを開くを使用。Cmd+Shift+Gでr1/cursorを指定し、AXのselected URLと場所はfixtureを示したが、スクリーンショットのOpenは無効のまま。Returnで遷移せず、横スクロールで`noWindowsAvailable`。マーカー本文は確認できず、プロンプトは入力・送信していない。
2. 通常版Android Studioの実画面では、空のComposerに即時編集・事後Revertの説明がなかった。ただしinstalled SHAは未特定で、これは限定的な表示の再現記録。新ビルドの製品fail/passには使わない。ソースでもComposer/Settings双方の説明欠落を確認した。
3. Android StudioもFile → Openでr1/pluginのAXパス選択まで到達したがOpenは無効。Raise試行はinvalid secondary actionだった。初期配置だけ人間へ依頼した。
4. 修正後のrunIde起動後、通常版のOpenが有効になったためクリックしたが`timeoutReached`、続く状態再取得も同じエラー。遷移先は未確認。アプリ一覧に新しい`MainWrapper`（`net.java.openjdk.java`）が現れたが、取得は`Invalid app`で拒否された。連続取得失敗のためGUI操作を停止した。
5. r1/r2両面のfixtureはGit差分なし、`greeting.txt`は全て`Hello loop\n`のまま。課題の実行成功を示す証跡はない。

`loop.py check`は両runとも非0（INCOMPLETE）。未完了ケース、ロード同一性不足、観察とビルドの未対応を残しており、ゲートを通すためにケースや証拠を削除していない。

## Claude ProレビューとGPTの判断

| 指摘 | 採否・理由 |
|---|---|
| 3行説明の常時表示でチャット領域を圧迫する可能性 | 仮説として採用。約350pxと広幅、入力/Send、SettingsのApply状態をMV-037へ追加。`noWindowsAvailable`はCUA環境エラーであり、このレイアウト回帰の根拠にはしない |
| Revertの説明がcheckpointも含む復元保証に読める | 文言をFile-card Revert / can undo applied editsへ変更（修正2回目）。Worktree/checkpointの既知問題をこの表示修正で解決したとはしない |
| SharedというKDocが同一インスタンス共有に読める | 不採用。文言定義の共通化を意味し、実体は別インスタンスで正しい。最終レビューも修正不要と判断 |
| 最終文言の「事後」の明示度・SettingsでのFile-cardの分かりやすさ | 軽微・非ブロッカー。既に適用された編集（applied edits）のundoと明示しており、実機の受入で評価する |

最終レビューは追加実装修正不要。設定・保存・CLI引数・Stop経路へのロジック回帰は見当たらないという静的所見。Claude自身のGUI操作・テスト実行はなく、GPTの観察と自動テストから独立して報告している。

## 予算と再開

開始は01:00:27 JST、上限01:45:27。修正2/3回、送信0/8回。終了時刻と残り時間はIssue引継ぎコメントに記載。GUI環境がblockedのため、残予算を使い切るまで再試行しない。

次の操作:

1. 人間が通常版のファイルダイアログを整理し、Computer Useで対象IDE/ウィンドウを取得できる状態にする。sandboxのMainWrapperが対象外なら、今回のZIPを通常版へインストール・再起動し、ロード実体を照合する。
2. Issue #20の最新claim・HEAD・現在起動中ビルドを確認し、新しいrunでfixtureを生成。両IDEで`LOOP_FIXTURE.txt`のrun/面/パスを画面で確認する。
3. MV-037を狭幅/広幅とSettingsで確認後、同じ小課題でMV-024/021/023を実送信・編集・復元まで比較する。復元先を確認してから操作する。
4. 合格した範囲だけmatrix/Issueを更新する。次の機能修正も引き続きP0 #20のWorktree復元方針と古い復元ボタンの整合性を優先する。

#20/#19/#1の未完了条件、#29の両面GUI初回完走は完了扱いにしていない。

## 再開: r3（2026-09-06 01:27 JST〜）

ユーザーの「次のステップに進んでください」で再開。[再開claim](https://github.com/shinma06/cursor-agent-plugin/issues/20#issuecomment-5553189023)を登録し、cleanな`310acbf5823790f74a31771ab3950fc9c38dfb29`から`20260906-issue20-r3`をprepare/buildした。選択ケースはr2と同じ。追加実装はなく、既存の修正2/3回と01:45:27の上限を継承した。

- **新しい前進:** 通常版Android Studioでr3/pluginを開き、`LOOP_FIXTURE.txt`のrun・面・絶対パスをAXとウィンドウタイトルで照合。スクリーンショットでもrun/面を確認できた。長いパス行は画面で省略されているため、全文はAXとプロジェクトのパスで照合した。
- **ビルド照合:** インストール済みプラグインJARはr3 ZIP内のJARと同じ`43499cc7…f4e40f27f`。インストール先ファイルのmtimeは01:24:18.259 JST、通常版の最新起動ログは01:24:18.375にCursor Agentロードを記録。GUIにも今回の新文言が表示されており、同じバイナリをロードしていることを照合した。r3 ZIPのSHA-256はr2と同じ`b3c5dc67…5361cd3`。インストール・再起動そのものをGPTがGUI操作したという意味ではない。
- **表示の限界:** 開発リポジトリのウィンドウでは3行説明と入力/Sendが画面で見えた。fixtureの約350px幅の画面ではIDE errorトーストが説明の一部に重なった。Settings・広幅表示・Apply状態は未確認なのでMV-037全体はblockedを維持する。最新のIDE起動以後にERRORログ行はなく、トーストの原因は未特定。
- **再発した環境障害:** プラグインの⋯を開くとAXにメニュー項目だけが返り、選択状態は得られず、スクリーンショットは取得不能。Escape、全AX再取得、アプリ再取得、CUAセッション再初期化後もウィンドウ名だけで本文/画像が戻らなかった。人間にメニュー終了・入力欄へのフォーカス復帰を依頼し、以後のGUI操作を停止した。
- **Cursor側:** フォルダ選択に到達。パス入力はAX上で完全パスを表示したが確定時に`/`へ変化し、貼り付けは`Timed out waiting for the application to read the clipboard`。fixtureのマーカー確認は未了。新しい課題は入力も送信もしていない。
- **送信・fixture:** r3は両面とも送信0、Git差分なし、`Hello loop\n`のまま。通常版ログには前回終了後・今回再開前の01:26台にr1/pluginへの2送信があるが、操作者とGUI結果は未確認で、GPTのMV実施とは扱わない。予算は保守的にこの2送信を含めて残り6/8回とする。

ローカル証拠: r3の`evidence/gui-observations.txt`、`evidence/installed-identity.txt`、`build.log`、`check.log`。`loop.py check`は全7ケースがblockedのためINCOMPLETE。MV-037および両面のMV-024/021/023をpassにしていない。

再開に必要な操作は、**Android Studioのr3/pluginウィンドウで⋯メニューを閉じて入力欄へフォーカスを戻し、Cursorでr3/cursorをフォルダとして開くこと**。操作を戻した旨の返信後にGPTが最新の画面を取得して判断する。古い座標・要素番号で送信しない。上限時刻を越えて再開する場合は、予算の扱いも先に確定する。

r3のClaude Pro独立レビュー（ツール無効、資料のみ）は報告範囲に誇張なしと評価し、(1)source↔ZIPの証拠を資料に含めること、(2)メニュー項目の列挙と選択状態確認を区別することを指摘した。両方採用した。前者は`loop.py build`がclean `310acbf`で`test buildPlugin`を実行し、前後のHEAD/dirty状態を検査して生成したr3の`run.json`（`source_clean=true`、`built_at`、ZIP SHA-256）と`build.log`で補完する。この紐付け検証の実施者はGPTであり、Claudeがビルドしたとはしない。後者は**権限・sandbox・Worktreeの実行時選択は未確認**と明記し、保存XMLにoverrideがないことやソースのデフォルトから実行時設定を断定しない。モデル表示もAXと画像が一致せず未確定。実送信前にこれらの状態を確認する必要がある。
