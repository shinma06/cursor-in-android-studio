# Android選択対象の新fixture

#150の三経路比較用。旧candidate `55a4e28a05c2756a36046f395cc6d1991cdfa58a` の復元ではなく、別source/4 APKを固定する。
[比較手順](../../../research/issue-150-comparison.md)の仕様・8選択組・C01–C10を維持する。
[Case正本](../../changes/issue-150.json)はA/B/Cの30件。ビルド/静的検証はGUI成功を示さない。

## 固定入力

| 入力 | 値 |
|---|---|
| Gradle / AGP | Wrapper 9.3.1（distribution SHA256を検証）/ 9.1.1 |
| Kotlin | AGP組込みKotlin（AGP 9.1.1のKGP 2.2.10） |
| JDK / bytecode target | 21 / 21 |
| compileSDK / targetSDK / minSDK | 37.0 / 37 / 26 |
| Build Tools | 36.0.0 |
| UI | Activity + XML View/ViewBinding、Composeなし |
| modules / variants | appRed・appBlue / debug・release |
| 署名 | fixture専用debug key。releaseも同じ鍵で署名、releaseは非debuggable |

AGPの[公式互換条件](https://developer.android.com/build/releases/agp-9-1-0-release-notes)に基づく。
NDK、ネットワーク権限、DB、外部アカウントは不要。Kotlin標準ライブラリとViewBindingのAGP生成依存を使い、UIライブラリは追加しない。

## 生成・検証・保全

fixture単独を作業ディレクトリにし、JDK21と既存Android SDK（platform 37.0 / Build Tools36.0.0）を指定する。
SDKの追加/変更が必要なら、その操作を別途調整してから進める。下記buildはSDK自動取得を無効にする。
このGradle projectは製品pluginのbuildとは独立している。

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)" # macOSの例。他OSは実JDK21を指定
export ANDROID_HOME="<既存Android SDKの絶対パス>"
mkdir -p .fixture-signing
# 初回だけ。既存のfixture鍵を上書きしない。
"$JAVA_HOME/bin/keytool" -genkeypair -keystore .fixture-signing/debug.keystore \
  -storepass android -keypass android -alias androiddebugkey -keyalg RSA \
  -validity 3650 -dname "CN=Issue150 Fixture" -noprompt
./gradlew --no-daemon --console=plain -Pandroid.builder.sdkDownload=false \
  :appRed:assembleDebug :appRed:assembleRelease :appBlue:assembleDebug :appBlue:assembleRelease
# 固定source commitからの再buildは、依存取得後 --offline --rerun-tasks を加える。
python3 verify.py --sdk "$ANDROID_HOME" --output "<一時領域以外の新しい保全先>"
```

`verify.py`はfixture配下がcommit済み/cleanであること、4 APKのpackage/最小SDK/debuggable/権限なし、
識別markerとViewBindingのDEX文字列、署名を検査する。source archiveはrepository rootから生成し、全tracked fileの一覧と内容がGitの固定treeに一致することを検査してから、4 APK・manifest・検証出力・fixture鍵とともに保全する。
既存保全先への上書きは禁止。`source_commit`/`source_tree`はfixtureが属するrepositoryの固定値、archive/hashは保全したbytesの識別値。
APKがどのsourceからbuildされたかは、この検査単独では証明できない。固定commitでのbuild成功記録とclean状態を合わせて受領する。
ログや鍵をGitHubに投稿しない。再buildのbytesが異なれば新候補として固定し、旧候補へ上書きしない。

PMはsource archive/4 APK/manifest・build記録/依存取得済みcacheと再実行手順を受け取り、SHA256をreadbackする。archiveは別の空ディレクトリへ展開し、全tracked fileの一覧と内容hashも照合する。
Gitの固定sourceを正本とし、archiveはGUI用コピーを渡す補助成果物とする。`verify.py`はGit checkout内で実行する。
privateな保全場所はローカルhandoffへ記録し、原本を一時領域だけに置かない。GUI用は原本から作った別コピーを使う。

## IDEでの開始条件

IDE import/sync、`I150 Red`/`I150 Blue` run configuration、D1/D2は未設定・未検証。
同一deviceの2つのpackageと同一packageの2deviceを取り違えず、全経路で同じsource/4 APKを使う。
Android SDK/model/CLIの固定、plugin ZIPとロードJAR一致、指定GUI担当とqueue/host共通leaseが必要。
`targetLabel`とログは `module|variant|applicationId|runNonce|pid`。runNonce/counterはprocess内で保持し構成変更では再生成しない。
ActivityのView参照はonCreate内のみ、listenerはView所有、外部Context/Activity保持・coroutine・DBはない。
`観測ログを出す`は同じmarkerとcounterを1行出し、`この実行だけ失敗させる`は明示的な合成crashを起こす。
releaseへのdebug要求は非対応が期待結果。C08は合成PID再利用境界であり、実OSのPID再利用を観測した扱いにしない。
