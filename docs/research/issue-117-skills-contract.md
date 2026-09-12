# #117 SkillsのACP・CLI契約と入力UX

2026-09-12 / base `f1d84cbc4006f76805fda22904a91fa1b490c841`。

**採用: ACPが広告するコマンドを会話単位で選び、1メッセージだけへ付ける入力機能。**
ACPで発見と明示実行、printで日本語/空白引数・resume・Worktree・IDE context前置の
限定成功を確認した。現在のpluginには候補/添付UIがなく、実装は専用機能Issueへ分離する。
Custom Modeの持続は未観測の別scope。製品・GUI・全Skills互換性の合格ではない。

## 公開契約と未確定部分

一次情報は2026-09-12に確認。公開範囲と今回の実測範囲を混同しない。

| 対象 | 公開内容 / 採用する判断 | 根拠 |
| --- | --- | --- |
| scope | projectの`.cursor/skills`/`.agents/skills`、対応するuser領域、互換`.claude`/`.codex`。入れ子探索・ファイルscopeもある | [Cursor Skills](https://cursor.com/docs/skills) |
| 定義 | SKILL.mdのname/descriptionが基本。明示専用はdisable-model-invocation。paths等の拡張もある | 同上 / [Agent Skills仕様](https://agentskills.io/specification) |
| 名前 | 仕様上は親folderと一致する小文字名、長さ等の制約。製品の受理挙動と全体優先順位は別 | Agent Skills仕様 |
| headless | Skillsの読み込みと`/skill-name`の-p呼出しが公開されている。単なるflag広告より強いが、全版/全modelの実測ではない | [CLI changelog](https://cursor.com/docs/cli/changelog) |
| plugin由来 | Skillsをpluginへ同梱しセッションへ読み込む経路がある。install/marketplace操作UIは本scope外 | [Cursor Plugins](https://cursor.com/docs/plugins) / CLI changelog |
| ACP候補 | session/new後にoptionalなavailable_commands_update。name/description、任意input.hint。session中に変更/削除でき、実行は通常promptのslashテキスト | [ACP Slash commands](https://agentclientprotocol.com/protocol/v1/slash-commands) |
| 1turn / mode | slash選択は1メッセージ、Custom Modeは明示的に持続しbadgeと解除を持つ。modeの公開対象はAgents Window/CLI | [Cursor Prompting](https://cursor.com/docs/agent/prompting) |

Cursor探索ルート間・plugin/ローカル間の**全重複優先順位**を、上記仕様の確認範囲では
確定できなかった。filesystem一覧を「Agentが採用したカタログ」と表示しない。
ACP AvailableCommandには標準でskill専用kind、正規ファイルパス、plugin ID、同名優先順位はない。
説明文字列の`(project skill)`を機械的に解析して出所の正本にしない。

Custom ModeをAsk/Agent/Planへ勝手に変換せず、`--mode <skill>`や未知のACP optionを生成しない。
Agents Window向け機能をIDE内panelの必須再現と判断しない。print呼出し間の持続/解除や
ACPでのsticky skill設定契約を確認したときだけ、別の有限scopeで採用を再評価する。

## 現行コードの境界

- [GrowingPromptField](../../src/main/kotlin/com/cursoragent/ui/composer/GrowingPromptField.kt)は
  `Plan, Build, / for skills, @ for context`を表示するが、Skills候補機能の実装ではない。
- [ComposerPanel](../../src/main/kotlin/com/cursoragent/ui/composer/ComposerPanel.kt)が登録するのは
  MentionPopupControllerの`@`候補とEnter送信。slash候補、skill添付state、badge/解除はない。
  inputTextは外側の空白をtrimする。IME未確定文字の送信抑制をこのコードだけで保証できない。
- [AcpProtocol.update](../../src/main/kotlin/com/cursoragent/acp/AcpProtocol.kt)は
  available_commands_updateをnullへ落とす。AgentEventにも候補リスト用の型がない。
- [AcpSession](../../src/main/kotlin/com/cursoragent/acp/AcpSession.kt)は初回send時に接続/sessionを作る。
  入力前の候補取得には接続寿命とUIへの配送が必要。turn終了後の一般通知も現状は配送されないため、
  コマンド一覧をturn本文のbufferに持たせずsessionの状態として扱う。
- [PromptContextBuilder.assemble](../../src/main/kotlin/com/cursoragent/ui/PromptContextBuilder.kt)は
  IDE contextをユーザー本文の前へ付け、serviceは全promptを引数/ACP textで送る。
  今回の限定print実測では先頭以外のslashも成功したが、任意のコードblockや複数コマンドまで保証しない。
- headerの`/summarize`送信と通常文字列入力はSkills選択UIではない。既存保存/ID・print本文修正は
  #44/#254の専有範囲を維持し、本調査で触れない。

## 実測結果

完全な合成SKILL定義・引数・順序・公開抽出値・検証範囲は[実測記録](issue-117-fixture.md)。
CLI `2026.09.10-fd3934a`、ACPはmodel option `default[]`、printはinit `Auto`。
全てask、既存認証のまま。内部モデルIDは未特定。ACP1推論＋print4runで終了し、追加反復しなかった。

| 観点 | 観測結果 | 言えないこと |
| --- | --- | --- |
| ACP発見 | new応答後に1回の候補通知。今回のecho/collisionを広告 | Skills全件・全scopeの完全一覧、idle時再通知/削除 |
| 同名 | `.agents`由来の説明が1件だけ広告された | 全優先順位、同じ名の別実体を指定して実行する契約 |
| 不正YAML | brokenは今回未広告、正常echoの実行は継続できた | 全壊れ方で同じ診断/回復、なぜ未発見かの確定 |
| ACP明示実行 | `/issue117-echo 東京 alpha beta`→skill内にだけ置いたmarker付き本文、end_turn、tool0 | 専用skill-loaded通知、持続mode |
| print直接 | 日本語/空白引数を保った期待result、exit0/tool0 | plugin画面に同じ文字列が描画されること（#254は別） |
| resume | 元と同じsession IDで、skillを再指定して期待result | 再指定なしでの持続/解除 |
| Worktree | 別cwdの追跡済みskillがbyte一致し、期待result | untracked skillやsetup script、別rootへresume |
| IDE context前置 | `Active file: fixture.txt`を先頭へ付けても期待result | 任意prefix、複数skill、ACPの同条件、IME/GUI |

旧公開ACP成果は[移行契約K14/T11](acp-feature-migration-2026-09-09.md)と
`src/test/resources/acp/fake_agent.py`を確認した。前者は設計/公開契約、後者は合成serverであり、
今回のSkills実ロードを補う実証として扱わない。#146の未公開実fixtureは参照しない。

## 入力UXの採用範囲

| 操作/状態 | 初回実装の期待動作 |
| --- | --- |
| `/`検索 | 入力先会話の候補を絞り込む。取得前は「候補を取得中」、未広告は「この会話の候補は未取得」。空の確認済み一覧と区別 |
| 名前/説明/出所 | nameをそのまま表示。description/input hintは提供値を保ち、日本語の補助ラベルを付ける。出所は「この会話のAgent」、確認できたローカル候補だけproject相対位置。未知plugin名を作らない |
| 選択/取消 | Enterは候補選択だけ、即送信しない。Escは候補を閉じ、入力/draftを保持。IME変換確定Enterは選択/送信をしない |
| 1メッセージ添付 | 選んだコマンドを入力上の解除可能なskill/command表示にし、送信時だけslash文字列へ落とす。既存引数・日本語・内側空白とcontextを保持 |
| 解除/送信後 | ×/キーボードで選択だけ解除し、本文を捨てない。送信成功後の次turnへ暗黙に再添付しない。送信前の取消/失敗/別tab移動でdraftを壊さない |
| 更新/削除 | sessionの通知を一覧全置換で反映。空配列は全削除。他session/旧接続の通知を混ぜず、選択済み候補が消えた場合は未確認として再選択可能にする |
| 未発見/壊れた定義 | 「未取得」「候補なし」「定義を確認できない」を分け、普通のテキスト送信は使えるままにする。見つからないだけで壊れていると断定しない |
| 同名 | serverの広告IDを優先。print側で候補が曖昧なら出所の違いを表示して確定扱いしない。表示上の選択だけでCLIがそのファイルを読むと保証しない |

既存JBPopupFactory/EditorTextFieldの候補・書込み操作を利用し、新たな入力frameworkや汎用event busは不要。
ファイル内容を実行する前提で自動import/installしない。大量ディレクトリの同期走査はEDTへ置かず、
破棄後/別tabの完了通知を配送しない。単発選択stateと将来の持続mode stateは別にする。

ACPではサーバーの確定候補を主経路とし、未知/未広告のコマンドを「利用可能」と捏造しない。
printの補助として公開scope内のローカル候補を読み取り提示する案は採用可能だが、
正規化・重複・symlink/入れ子scope・壊れたYAML・権限/サイズ制限を検証する必要がある。
初回は確認したproject scopeを明示し、user/plugin全探索や完全カタログを完成条件に広げない。
候補UIはAgentのskill実行を補助するもので、Skill本文を独自system promptへ常時展開しない。

## 競合比較とIDE統合の意味

Cursor IDEのSkills呼出しと、[JetBrains AI Assistant + Cursor ACP](https://cursor.com/docs/integrations/jetbrains)を比較対象にする。
[JetBrainsのACP設定](https://www.jetbrains.com/help/ai-assistant/acp.html)にはcustom MCPとIntelliJ MCPを
渡す既存能力があり、[IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)自体も既存。
Skills実行・IDEファイル/terminal操作・MCP利用そのものを本Pluginの独自機能と呼ばない。

[JetBrains Skills管理](https://www.jetbrains.com/help/ai-assistant/agent-skills.html)の存在も確認するが、
管理画面の対応範囲をCursor ACPの全候補表示/持続modeへ読み替えない。本調査は両製品の
実IDEを操作していないため、UX同等以上を合格と判定しない。直接IDE統合で加える対象は、
このpluginのtab/draft/contextと矛盾しない選択・解除、出所の確認、IME誤送信防止であり、
競合に同等能力がないとの主張ではない。固定buildで操作数/保持/誤送信を比較する。

## 機能Issueへの引継ぎとCase案

採用実装先は[機能Issue #258](https://github.com/shinma06/cursor-in-android-studio/issues/258)。
ACP通知のsession状態→候補検索→単発選択→送信/解除を初回範囲にする。
source基盤は専用担当と調整。plugin install/marketplace、Custom Mode持続/解除の実装、#44の保存schema、
#254の本文正規化は対象外。持続mode/終了と一般plugin導入の残条件は[#26](https://github.com/shinma06/cursor-in-android-studio/issues/26)の別採否へ残す。
#24/#97のComposer・キー操作との境界は実装担当が照合し、PMが#44/#254後の同一writerへ順次割り当てる。

調査変更はGUI不要で`issue-117.json`のcasesは空。機能Issueへ渡す以下のCase案は
**GPT pending / human pending**であり、本調査のpassではない。

1. 固定候補SHA/ZIP hash/loaded JAR/CLI版とtransportを記録し、指定GUI lease担当が使い捨てskillを用意。
2. `/`検索で名前/説明/出所を確認。Enterで選択して未送信、Escで取消、×で解除し、本文/draftが残ること。
3. 日本語IME変換確定、空白引数、先頭/文中slash、選択後のcontext追加、空/削除/不正候補を確認。
   一覧更新/削除は制御serverで再生する合成Case、実skill実行は今回と同等の合成定義を実Agentで実施するCaseと分ける。
4. A/B会話で候補/draftを分離し、応答中・Stop/close・再接続後の旧通知で新しい選択を汚さない。
   送信後の次turnは未選択。printのresume/Worktreeは実行rootと実定義を照合する。
5. 本文/結果・候補・badge状態の証拠を正本JSONへ。失敗は専用fix Issue/PRと再確認buildへ追跡する。
   実測のmarker成功を画面操作・コピー/保存の成功へ転記しない。

## 受入と未観測

| #117受入 | 対応する成果 |
| --- | --- |
| scope/重複/frontmatter/plugin契約 | 公開契約表、限定衝突/不正定義観測、未保証境界 |
| harmless -p/日本語・空白/resume/Worktree/版 | 実測記録の4runとID/root/byte照合 |
| 検索/説明/出所/選択取消/添付解除/IME | 入力UX表と固定build Case案 |
| 単発とCustom Modeの分離 | 公開対象と未観測持続契約、別scope判断 |
| 実装切出し | 採用実装#258、残条件#26へ引継ぎ。install/marketplace除外 |

未観測: plugin由来実skillの実ロード、user/全互換ディレクトリ/入れ子の優先順位、
その他の不正定義、未知slashのengine挙動、動的候補削除/idle通知、複数添付、引数引用符/emoji、
自動invoke、Custom Mode持続/解除、全CLI版/model、GUI/IME。未観測は不存在という結論ではない。
固定入力のmarker一致は観測版・今回の環境に限り、chunk分割や将来の推論の完全再現を保証しない。

検証: 文書内リンク・受入JSON・公開fixture定義と原本の一致を確認。共通Change Impactの
workflow 158件 / loop 11件が成功し、製品変更がないためGradle/ZIPは対象外。
独立レビューは固定HEAD/baseの公開資料・実装・実測要約を対象とし、非公開原本の確認はwriter実施と区別する。
