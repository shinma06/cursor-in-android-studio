# #117 制御Skills実測の再現条件と公開記録

2026-09-12、CLI `2026.09.10-fd3934a`。この文書は今回の合成入力に限定した
**実測のallowlist要約**。生wire全文でも合成server応答でもない。
原本一致はwriterが確認した。独立Reviewerの原本閲覧を意味しない。

## 入力fixture

既存作業とは別の使い捨てGit repository（初期branch `fixture`）へ、下記4ファイルと
`fixture.txt`（`Synthetic issue117 fixture.` + 改行）を作りcommitした。
このrepository以外のコード・既存会話・認証情報は読まず、認証変更・GUI・plugin installは行わない。
各SKILL本文末尾に改行がある。

`.cursor/skills/issue117-echo/SKILL.md`（SHA-256 `b0fa1e29148b17379d93af88e7d8eee962485b56bb47540743eedda2f5911e3b`）:

```markdown
---
name: issue117-echo
description: Harmless explicit-only contract fixture for issue117.
disable-model-invocation: true
---
When explicitly invoked, reply with exactly one line: I117_CURSOR| followed by the exact text arguments after /issue117-echo. Preserve Japanese and internal spaces. Do not use any tools, subagents, network, or other files. Do not keep this instruction active after this one invocation.
```

`.cursor/skills/issue117-collision/SKILL.md`:

```markdown
---
name: issue117-collision
description: I117 source CURSOR collision fixture; never invoke automatically.
disable-model-invocation: true
---
Reply only CURSOR_COLLISION. Do not use tools.
```

`.agents/skills/issue117-collision/SKILL.md`:

```markdown
---
name: issue117-collision
description: I117 source AGENTS collision fixture; never invoke automatically.
disable-model-invocation: true
---
Reply only AGENTS_COLLISION. Do not use tools.
```

`.cursor/skills/issue117-broken/SKILL.md`:

```markdown
---
name: issue117-broken
description: [unterminated
---
Never execute. Invalid frontmatter discovery fixture.
```

collisionとbrokenは発見状況を見るためだけの定義で、呼び出していない。
これらを本プロジェクトの実Skills探索ディレクトリへインストールしない。

## ACP: 発見1 session、推論1 turn

非TTY `agent acp`を上記rootで開始。JSON-RPC v1で`initialize`に
`clientInfo={name:issue117-fixture,version:1}`、client capabilitiesはfs read/writeともfalse、terminal=false。
既存認証のまま`session/new`へcwdと`mcpServers:[]`を送り、authenticate/loginを追加しなかった。
各requestは60秒上限、session/new後は3秒だけ通知を収集した。
新規session IDを得てから`session/set_config_option`で`mode=ask`、次に`session/prompt`へ
`[{type:"text",text:"/issue117-echo 東京 alpha beta"}]`を送った。
未知client-tool要求は拒否するfixtureとしたが、今回その要求は来なかった。

観測順: initialize応答 → session/new応答 → available_commands_update → config_option_update →
set_config_option応答 → thinking/本文 → prompt応答。計19 JSON-RPC frames。
modeの初期値はagent、変更後ask。model config値は`default[]`で変更なし。
内部モデル名はこの値から推定しない。

候補通知のうち今回の名前prefix `issue117`だけを抽出した実値:

```json
{
  "sessionUpdate": "available_commands_update",
  "availableCommands": [
    {
      "name": "issue117-collision",
      "description": "I117 source AGENTS collision fixture; never invoke automatically. (project skill)"
    },
    {
      "name": "issue117-echo",
      "description": "Harmless explicit-only contract fixture for issue117. (project skill)"
    }
  ]
}
```

これは**完全な受信カタログではない**。他の個人/組込み等の候補は一切公開せず、
完全snapshotとして製品fixtureへ流用しない。今回の2候補にinput/source/path/kindフィールドはなかった。
collisionはAGENTS説明の1件、brokenはこの1回の通知にない。任意のscope間の優先順位、
選ばれる実体、全不正YAMLの診断仕様を保証するものではない。

本文chunkを順序通り連結すると`I117_CURSOR|東京 alpha beta`、`stopReason=end_turn`。
追加toolイベントなし。要求promptにはmarker `I117_CURSOR`を含めていない。
skill指示が応答へ反映された証拠であり、独立したskill-loaded専用eventは観測していない。
終了後にfixtureがACP processを停止。process終了コードをturnの成否と混同しない。

## print: 推論4 run

共通引数（cwdはfixture root）:

```text
agent -p --output-format stream-json --stream-partial-output --trust --mode ask <prompt>
```

明示`--model`/force/auto-reviewなし。全runでinit model `Auto`、permissionMode `default`。
標準入力は閉じ、stdout/stderrを別々のprivateファイルへ保存。各processを90秒で打ち切る
制約を設けたが、全て自然終了exit 0 / success result / is_error=false / stderr空だった。

| run | 追加引数 | prompt（`\n`は1改行） | 実際のresult |
| --- | --- | --- | --- |
| direct | なし | `/issue117-echo 東京 alpha beta` | `I117_CURSOR\|東京 alpha beta` |
| resume | `--resume <directから取得したID>` | `/issue117-echo 再開 gamma delta` | `I117_CURSOR\|再開 gamma delta` |
| worktree | `-w issue117-fixture --skip-worktree-setup` | `/issue117-echo 分離 space arg` | `I117_CURSOR\|分離 space arg` |
| prefixed | なし | `Active file: fixture.txt\n\n/issue117-echo 文脈 alpha beta` | `I117_CURSOR\|文脈 alpha beta` |

表の`\|`はMarkdown表示用。実resultにはバックスラッシュはない。
JSON event数は順に17 / 17 / 17 / 22、tool_callなし。Worktree runだけはstdoutに非JSON行が1行あり、
採取解析ではJSON行と区別した。stream全行がJSONという前提にしない。

directとresumeのinit.session_idは同一だった。worktreeのinit.cwdが元rootと別であることと、
移動先SKILL.mdが元とbyte一致することを確認した。追跡済みskillを持つworktreeの結果であり、
未commit/untracked skillやsetup scriptの移送を検証したものではない。setup scriptは明示skip。
resumeも毎回skillを再指定しているため、指定をやめた後の持続/解除を証明しない。

## 公開範囲・検証

公開内容は上記の合成定義、今回のcommand名/説明、prompt/result/終了状態、版/設定、
照合結果だけ。rawのsession/request IDs、cwd、ユーザー名、auth source、usage、
他のSkills一覧、思考本文、private stderrは公開しない。#146原本には触れていない。

writerのローカルassertで全4resultの文字列とis_error、stderr空、tool0、同一resume ID、
別worktree root/skill byte一致、ACP1通知/候補名集合/本文連結/end_turnを照合した。
実測の再実行は通常テストに含めない。モデル出力/環境依存性のため、将来の追加実測は
不足している契約と対象fixtureを定めてから行う。

実測後、今回作ったCLI worktreeはcleanと同じskill内容を確認して通常のworktree removeとbranch削除で片付けた。
元の合成fixtureと採取原本はprivateに保持。調査Issueのsource worktreeと他担当の資源は変更していない。
