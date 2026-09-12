# #118 合成Subagent実測の入力・公開投影

2026-09-12 / CLI `2026.09.10-fd3934a` / ACP1 turn + print2 run。
これは実測wireの**allowlist投影**であり、生wire全文・合成server応答ではない。
writerが原本との一致をassertした。独立Reviewerの原本閲覧を意味しない。

## 固定入力

使い捨てGit repository、初期branch `fixture`へ2ファイルをcommit。
`fixture.txt`は`I118_FIXTURE_ALPHA`と末尾改行。既存会話や実プロジェクトを対象にしない。
`.cursor/agents/issue118-reader.md`（末尾改行あり、SHA-256 `c11b2dbcbedb5a525e56b3e9d65d0537c48341159de66a6c5e2781639238f65e`）:

```markdown
---
name: issue118-reader
description: Harmless read-only contract fixture. Use only when explicitly requested.
model: inherit
readonly: true
is_background: false
---
Read only fixture.txt in the current workspace. Reply with exactly one line: I118_CHILD| followed by that file's first line without its newline. Do not edit files, run shell commands, access the network, or delegate to any other agents. Do not read other files.
```

ACP/print初回の同一prompt:

```text
Use exactly one issue118-reader subagent in foreground to read the synthetic fixture.txt and return its result. You must delegate this task to that subagent; do not read the file yourself. Do not call any other tools or agents, write files, use shell, or access the network. After the child completes, return its final line exactly.
```

## 起動と順序

ACP: 非TTY `agent acp`、cwdは合成root。JSON-RPC **2.0**、ACP `protocolVersion=1`。
initializeにclientInfo `issue118-fixture`/`1`、fs read/write=false、terminal=false。
session/newはcwdとmcpServers空配列、既存認証のままauthenticateなし。
session/set_config_optionでmode=ask、model optionは初期値のまま。その後session/promptのtext1件。
各requestは120秒上限。未対応のClient requestは-32601を返す採取clientであり、
cursor/taskに成功応答や架空の子実行結果を返さない。終端後に所有ACP process groupを停止した。

print: 非TTY、stdinを閉じ、`agent -p --output-format stream-json --stream-partial-output --trust --mode ask`。
2回目だけ同じ合成親sessionを`--resume`。明示model/force/auto-reviewなし、initはAuto/default。
各process120秒上限、今回は両方自然終了exit0。stdout/stderr別保存、全3実行でstderr空。
Agentの内部読み取り実行をClient提供fs/MCP経由だと解釈しない。

## ACP公開投影

受信53 frames。順序は標準tool pending→in_progress→completed→ID付きcursor/task request→
採取clientの-32601応答→親の本文完了/end_turn。下記最後の要素だけ送信応答、残りは受信。
requestの元idは非null整数で、公開時だけ7001へ一対一置換した。tool/agent IDも一定の別名へ置換。
生session/request IDs、prompt、他候補一覧、思考、usage、rootは省略。省略と元からの欠損を混同しない。
`cursor/task`の元paramsにはpromptがあるが、sessionId/status/result/tokenはなかった。

```json
[
  {
    "sessionUpdate": "tool_call",
    "toolCallId": "acp-call-1",
    "title": "Task: Read fixture.txt",
    "kind": "other",
    "status": "pending",
    "rawInput": {
      "_toolName": "task",
      "description": "Read fixture.txt",
      "subagentType": {
        "custom": {
          "name": "issue118-reader"
        }
      }
    }
  },
  {
    "sessionUpdate": "tool_call_update",
    "toolCallId": "acp-call-1",
    "status": "in_progress"
  },
  {
    "sessionUpdate": "tool_call_update",
    "toolCallId": "acp-call-1",
    "status": "completed",
    "rawOutput": {
      "durationMs": 7303,
      "isBackground": false
    }
  },
  {
    "jsonrpc": "2.0",
    "id": 7001,
    "method": "cursor/task",
    "params": {
      "toolCallId": "acp-call-1",
      "description": "Read fixture.txt",
      "subagentType": {
        "custom": {
          "custom": {
            "name": "issue118-reader"
          }
        }
      },
      "model": "default",
      "agentId": "acp-child-1",
      "durationMs": 7303
    }
  },
  {
    "jsonrpc": "2.0",
    "id": 7001,
    "error": {
      "code": -32601,
      "message": "Fixture has no client tools"
    }
  }
]
```

子本文は標準tool content/rawOutputにもcursor/task paramsにも入っていなかった。
親のagent_message_chunkを順序連結した末尾は`I118_CHILD|I118_FIXTURE_ALPHA`。
このmarkerは入力promptになく、合成ファイル/子定義から応答に反映された。
前置説明もあったため「親全文がmarker1行だけ」とは主張しない。

## print公開投影

初回63 events、再開53 events。各runともtaskToolCall started/completedが1対。
トップのcall_idで対になり、親session_idは両run同一。元tool_call.toolCallIdも対内同一だが
call_idとは別の値なので代用しない。時刻/model_call_id/生成prompt/description等は省略。
子の内部readToolCall・段階的本文streamはこの親streamには出なかった。

```json
[
  {
    "run": "foreground",
    "type": "tool_call",
    "subtype": "started",
    "call_id": "print-call-1",
    "session_id": "print-parent-1",
    "taskToolCall": {
      "args": {
        "subagentType": {
          "custom": {
            "name": "issue118-reader"
          }
        },
        "model": "default",
        "mode": "TASK_MODE_UNSPECIFIED",
        "environment": "SUBAGENT_EXECUTION_ENVIRONMENT_UNSPECIFIED",
        "machine": {
          "sameMachine": {}
        },
        "agentId": "print-argument-agent-1"
      }
    }
  },
  {
    "run": "foreground",
    "type": "tool_call",
    "subtype": "completed",
    "call_id": "print-call-1",
    "session_id": "print-parent-1",
    "taskToolCall": {
      "args": {
        "subagentType": {
          "custom": {
            "name": "issue118-reader"
          }
        },
        "model": "default",
        "mode": "TASK_MODE_UNSPECIFIED",
        "environment": "SUBAGENT_EXECUTION_ENVIRONMENT_UNSPECIFIED",
        "machine": {
          "sameMachine": {}
        },
        "agentId": "print-argument-agent-1"
      },
      "result": {
        "success": {
          "conversationSteps": [
            {
              "assistantMessage": {
                "text": "契約どおり `fixture.txt` だけ読みます。I118_CHILD|I118_FIXTURE_ALPHA"
              }
            }
          ],
          "agentId": "print-child-1",
          "isBackground": false,
          "durationMs": "8376",
          "backgroundReason": "SUBAGENT_BACKGROUND_REASON_UNSPECIFIED"
        }
      }
    }
  },
  {
    "run": "resume",
    "type": "tool_call",
    "subtype": "started",
    "call_id": "print-call-2",
    "session_id": "print-parent-1",
    "taskToolCall": {
      "args": {
        "subagentType": {
          "custom": {
            "name": "issue118-reader"
          }
        },
        "model": "default",
        "resume": "print-child-1",
        "mode": "TASK_MODE_UNSPECIFIED",
        "environment": "SUBAGENT_EXECUTION_ENVIRONMENT_UNSPECIFIED",
        "machine": {
          "sameMachine": {}
        },
        "agentId": "print-child-1"
      }
    }
  },
  {
    "run": "resume",
    "type": "tool_call",
    "subtype": "completed",
    "call_id": "print-call-2",
    "session_id": "print-parent-1",
    "taskToolCall": {
      "args": {
        "subagentType": {
          "custom": {
            "name": "issue118-reader"
          }
        },
        "model": "default",
        "resume": "print-child-1",
        "mode": "TASK_MODE_UNSPECIFIED",
        "environment": "SUBAGENT_EXECUTION_ENVIRONMENT_UNSPECIFIED",
        "machine": {
          "sameMachine": {}
        },
        "agentId": "print-child-1"
      },
      "result": {
        "error": {
          "error": "Request blocked We are unable to complete this request because it was blocked under the model provider's usage guidelines. Try a less sensitive prompt."
        }
      }
    }
  }
]
```

初回のargs.agentIdとsuccess.agentIdは別。再開promptへ渡したのは**success.agentId**。
その値が再開Task args.agentIdへ入った。再開promptは次の`<returned-agent-id>`のみ原本IDを置換:

```text
Resume exactly the completed issue118-reader subagent with agent ID <returned-agent-id>. Ask it to return its previous final marker line from its preserved conversation without reading files again. Do not start a fresh subagent or any other task. Use only the Task tool to resume this agent, and no other tools. Do not write files, use shell, or access the network. Return the child result.
```

再開子はproviderのRequest blockedエラーとなった。回避する言換え・別modelで再試行していない。
親はエラーを文章で報告し、外側resultはsuccess/is_error=false/exit0だった。
再開の要求IDの一致と子error形状は実測、**再開成功・context保持は未確認**。
エラーの文言を解析して汎用provider分類を作らず、result.error構造で失敗として扱う。

## 原本照合と限界

writerのassertで同一tool ID、順序、request ID型と対応応答、終端、marker、
print引数ID/結果IDの相違と再開IDの一致、親session一致、子errorと親successの併存、
durationMsのACP整数/print文字列、stderr空、合成Git rootのcleanを照合した。
公開投影は全欄を原本から選択し、上記ID以外の掲載値は変更していない。
rawの認証情報・個人名・root・思考・全候補を公開しない。#146原本は参照していない。

readonly frontmatterは公開設定を使用した事実であり、全書込み防止の侵入テストではない。
foreground成功はbackground/親Stop/切断/再開成功/全modelの保証ではない。
この3実行を自動テストのたびに再推論しない。通常の回帰は公開契約の制御server/投影を使い、
必要な追加liveは後続の有限契約と固定Caseに限定する。
