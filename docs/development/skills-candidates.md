# 入力前のSkills・コマンド候補 (#258)

ACP会話のserver広告を入力前に取得し、選んだnameを1メッセージだけへ付ける。
本文やSkills定義を独自に展開せず、既存ACP・入力・予約機能を使う。

## 固定根拠と依存

- [#117固定調査](https://github.com/shinma06/cursor-in-android-studio/blob/6ed83d4a7a85ba4884bf1f22a0f1873558bf8a15/docs/research/issue-117-skills-contract.md)の契約を採用。実Agentで観測したACP1回・print4条件の成功と、製品/GUIの未確認を分ける。
- develop比較baseは`9979266b4e99e7d4dc46689dac1f61f33f09b88a`。#24実commit `b9f8275d08cb19c66d69c65ec157ba8a381ece65`を通常mergeし、#48 `dd655160e136a44250f990932a3fe77d686f951f`も祖先に保持。#258固有レビューbaseは#24固定版。
- #24/#48統合後、origin/developを通常同期し、実target差分・Case・CI・独立レビューを更新する。それまではenroll/ready/mergeしない。#117の統合前提・#251監査・PR #261保留も維持。
- #43のLoading/Failed/Loaded設計を照合したが、モデル取得の未統合差分は取り込まない。コマンド候補は短い型付き状態をsession単位で保持し、別のCLI一覧取得処理を増やさない。

## 公開能力との比較（2026-09-12確認）

| 基準 | 今回の対応と未確認 |
| --- | --- |
| [Cursor IDE内panelのSkills](https://cursor.com/docs/skills) | `/`検索と1メッセージの選択を採用。Custom Mode持続、管理・marketplace、全scope探索は別scope。 |
| [Cursor + JetBrains ACP](https://cursor.com/docs/integrations/jetbrains) | 既存ACP接続を使用。Skills呼出しやIDE操作自体を独自機能としない。 |
| [JetBrains Skills管理](https://www.jetbrains.com/help/ai-assistant/agent-skills.html) | 名前・説明・出所とsource管理は既存能力。管理画面の対応をCursor ACPの全候補/全動的更新の実測へ読み替えない。 |
| [JetBrains ACP + IntelliJ MCP](https://www.jetbrains.com/help/ai-assistant/acp.html) | custom MCP/IntelliJ MCPの受渡しも既存能力。今回MCPやprovider機構を追加せず、直接IDE context・tab・queueとの保持を受入で比較する。 |
| [ACP Slash Commands](https://agentclientprotocol.com/protocol/v1/slash-commands) | session/new後の任意通知、session内の全置換、name/description/input.hint、通常promptでの呼出しを使用。 |

GUI/実Agentを今回操作していないため、競合とのUX同等以上や全CLI版の成功は未確認。
#117のprint context前置実測はACPへ一般化しない。今回のACP送信はコマンドを先頭text block、IDE contextを別text blockに分け、name/引数を変更しない。実Agentでのcontext併用成功はCaseで確認する。

## 寿命・状態と操作

- ACP選択で背景接続し、initialize/session/newだけを行う。prompt・推論・checkpoint・transport固定は送信時まで行わない。通知はturn listener/usageを経由しない。
- 接続中、接続済み未通知、確認済み0件、取得失敗、不正通知を区別。不正な全置換は以前の候補を利用可能として残さない。未広告を壊れたローカル定義と断定しない。
- 既定上限は1通知2,000候補、name256文字、説明16,384文字、hint4,096文字、既存wire1MiB。上限超過・重複name・呼出しtokenにできないname・型不正は一覧全体を未確認にする。nameは正規化せず、namespaceを含む内部slashも保持する。
- 初回送信前にroot/executable/permission/sandbox/worktreeが変わると古い接続を閉じ、候補を無効化。mode/modelは既存の各prompt設定確認へ渡す。初回送信前のみ明示的に再接続できる。print切替・tab close・disposeでも世代を無効化し、古い通知が新しい候補へ届かない。
- `/`またはボタンから名前/説明を検索。上下・Enterは選択だけ、Escapeは閉じるだけ。IME変換中/確定イベント直後は選択・送信しない。選択は解除可能な表示となり、本文/日本語/空白引数を保持する。出所は「この会話のAgent（server広告）」であり、ローカルfile/plugin IDを推定しない。
- 一覧から消えた選択は未確認表示。送信時のUIとwire直前の両方で確認する。printではserver候補を使わず、従来の手入力slashは継続できる。local探索・優先順位・壊れた定義の診断は実装しない。
- 受理時に通常入力をclearし、成功後の次メッセージへ再添付しない。frame上限/送信queue拒否/write開始前取消では未送信を保ち、実write開始時だけ送信済み・chat bindingを確定する。write開始後の部分送信失敗は不確定を維持する。prompt未送信の失敗/停止なら元下書きを復元する。新下書きがあるときは上書きせず、未送信snapshotを停止中の予約一覧へ保持する。自動再送しない。
- queueは登録時のコマンドname/引数と#24明示contextを固定。編集は引数だけ、並べ替えでもidentityを保持。dispatchで現在の広告を再照合する。未送信の失敗は同じ項目を停止中で戻し、別draftをclearしない。参照内容と自動contextの時点は#24/#48契約を維持。
- Skills本文・資格情報を保存/ログへ追加しない。会話の通常保存にはユーザーの明示slash文字列だけを既存どおり記録する。

## 検証・引継ぎ

合成server回帰: promptなしの取得、通常送信との同時接続、複数session、全置換/空/不正/回復、foreign通知無視、接続中close、失敗、削除済み候補のwire直前拒否、exact name/引数と別context block、次turnに暗黙持越しなし。
状態/UI回帰: 初回送信前設定変更・再接続・transport/close世代、検索/0件/多数/keyboard/IME、予約のsnapshot/編集/順序/未送信復元。

[Case正本](../verification/changes/issue-258.json)の4件はGPT/human pending。#5のGUI/QA入口を維持し、統合時に専用QAへ全Caseとmain未反映を双方向link/readbackして引き継ぐ。旧#24/#48/研究のCaseや過去passを変更しない。
