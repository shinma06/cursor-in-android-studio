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
ドラッグ中Escapeで取消、端でauto-scroll。自身・祖先の非表示とremoveNotifyでドラッグを取り消しtimerを停止する。
同じ選択IDのまま並べ替え等で選択タブの位置・幅が変わった場合も可視化する。

レビュー指摘への修正で、自身・祖先の非表示時のtimer停止とcallback抑止、狭幅での
キーボード左右並べ替え後の選択タブ可視化を検証する回帰テストを追加した。
実装workerはテスト・buildPluginを実行していない。実行と独立再レビューはcoordinatorが担当する。
実機GUI受入は引き続きpending。

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

## 部品fixtureの起動（GUI lease取得後のみ）

`src/test/kotlin/com/cursoragent/ui/session/SessionTabStripFixture.kt` は手動起動用で、
unit suiteからは起動せず、plugin ZIPにも含めない。20個のタブ・close・並べ替え・選択を接続した
使い捨てwindowで部品のGUIを確認できる。会話本文やCLIは使わない。

専用worktreeの固定HEADとGUI予約を確認して、次の一時Gradle init scriptを作る。

```groovy
// /tmp/session-tab-fixture.gradle
allprojects {
    afterEvaluate {
        tasks.register('sessionTabFixture', JavaExec) {
            dependsOn tasks.named('testClasses')
            classpath = sourceSets.test.runtimeClasspath
            mainClass = 'com.cursoragent.ui.session.SessionTabStripFixture'
            javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(21)
            }
            jvmArgs '-Djava.awt.headless=false'
        }
    }
}
```

`JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew -I /tmp/session-tab-fixture.gradle sessionTabFixture`
で起動する。leaseなしで実行しない。終了時はこのfixtureのwindowだけを閉じ、起動process終了を確認。
証拠にはpluginをロードしたと記載せず、fixtureの実行class/hash・classpath・source HEADを記録する。
この部品fixture結果の登録形式は進行役が確認し、#65の実IDE/CLI受入へ流用しない。
