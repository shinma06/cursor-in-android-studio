# SHAごとのPlugin ZIP配布

切替先のfull SHAに対応した既成ZIPを取得する。checkout側ではGradleを起動しない。
初回CI・未公開commit・offline cache missは即時提供できず、`build/distributions/manifest.json` の
`unavailable` と再試行手順を表示する。別SHAのZIPを残さない。インストール・IDE再起動は行わない。

## 初回導入（PMが信頼したmainから各worktreeで一度実行）

```
python3 scripts/workflow/plugin_zip.py setup
python3 scripts/workflow/plugin_zip.py sync
```

runtimeは共通Git dirの `plugin-zip/runtime` へ保存され、旧checkoutでも残る。
`extensions.worktreeConfig` と各worktreeの `core.hooksPath` を使い、旧Gradleがlocal設定を
`.githooks` へ上書きしてもworktree設定が優先する。他repository/global設定は変更しない。
core.worktree/bare/sparseCheckout設定に移行が必要なら変更前に停止する。
clone/new worktreeは設定を継承しないので一度setupする。既存hookはその元パスへ委譲する。
旧commitで保護hookが存在しなくなる場合、pre-commit/pre-pushは保護を省略せず拒否する。
取得post-hookは取得runtimeのみで動作する。setupは信頼した版の明示更新操作でもある。

post-checkout（file checkout含む）/post-merge/post-rewriteで同期する。resetにはGit hookがないため
手動syncが必要。通常のファイル編集自体にもhookはないので、編集後の利用前にsyncでdirty判定する。
取得後のmanifestは取得時点の証明であり、その後の編集を監視するものではない。
Git操作と別プロセスのファイル編集を同時実行しない。Git操作そのものは取得失敗では取り消さない。

## 配布側

`Plugin ZIP delivery` はtrusted mainで動作し、main push・定期scan・明示dispatchで到達履歴を走査する。
古いbranchにworkflowがなくても、GITHUB_TOKEN mergeがpush eventを生成しなくても次scanで捕捉する。
現branch tipを優先し、残りの履歴を最大10件ずつ古い順に処理する。build不能SHAが枠を占める場合はPMが他SHAを明示dispatchし、
不能理由をIssueへ記録する。GitHub scheduleは遅延し得るので即時保証ではない。
一括push中間commitもrev-list対象。fresh runnerのremote refsを使い、削除済local stale refsは対象にしない。
公開後はbranch削除に関係なくReleaseを保持する。初回85commit等の大量backfillはPMが担当する。

build jobはwrite tokenなしで対象SHAのGradleを実行する。publisherは別runner、trusted mainのみを
checkoutし、artifactをデータとして検証してReleaseへ公開する。fork PRイベントは使わない。
write tokenをbuild codeへ渡さない。Actions artifactsは2日間の配送用で、永続正本は
`plugin-build-<full SHA>` prerelease（latest=false）のZIPとmanifest。
既存公開資産は上書きしない。異なるdigest/manifestは停止する。draft中に失敗した場合は
資産を点検して不足分を運用者が補完し、同じpublishを再実行する。資産自動削除はしない。

```
# clean exact sourceのcwd。scriptはtrusted mainの絶対パスを指定する
python3 /trusted/scripts/workflow/plugin_zip.py record build/distributions/plugin.zip /delivery/SHA
# trusted mainのcwd。必要なSHAをfetchしておく
python3 scripts/workflow/plugin_zip_publish.py publish --sha FULL_SHA --directory /delivery/SHA
# default branch workflowを明示起動（merge直後/過去branch）
gh workflow run plugin-zip.yml --ref main -f sha=FULL_SHA
```

manifest schema 1: repository、commit、tree、sha256、recipe。ZIPは
`cursor-in-android-studio-<full SHA>.zip`。ZIP CRC・安全なentry path・plugin.xmlのIDも確認する。
SHA256は内容整合検証であり署名ではない。信頼境界はこのrepositoryのpublisher/Release権限。
pre-pushは従来test/build後にexact HEAD cacheを記録する。切替時はcacheを再検証し、
cache missのみpublic Releaseをtimeout付き取得する。cacheは共通、出力/状態はworktree別lock。
途中ファイルはatomic renameで配置し、manifest readyはZIP配置後に書く。

## PMの残作業

main統合・develop同期後、公開履歴backfill、実Release取得、利用中worktree setup、代表SHAの
実ZIP照合を実施してIssueへ記録する。それまでは本Issue全体を完了としない。

根拠: [Git worktreeの設定優先と移行](https://git-scm.com/docs/git-worktree)、
[GITHUB_TOKENによるeventとdispatch](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow)。
