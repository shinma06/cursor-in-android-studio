# #117 SkillsのACP・CLI契約と入力UX

調査中 / 2026-09-12 / base `f1d84cbc4006f76805fda22904a91fa1b490c841`。
製品共有ファイルは変更しない。公開仕様・実測・合成回帰・GUI Case案を区別する。

## 現行境界

- `GrowingPromptField`のplaceholderは`/ for skills`だが、`ComposerPanel`が登録する候補は`@`のMentionPopupControllerのみ。
- `AcpProtocol.update`は`available_commands_update`をnullにし、コマンド候補をUIへ渡さない。
- ACP接続は最初の送信時に開始する。入力前のサーバー候補取得は未実装。
- `PromptContextBuilder.assemble`はIDE contextをユーザー本文より前に置く。slash呼出しが先頭でなくても有効かは未実測。
- Custom Modeの継続badge/解除は未実装。Ask/Agent/Planとは別の機能として扱う。

## 一次情報

- [Cursor Skills](https://cursor.com/docs/skills)
- [CLI changelog](https://cursor.com/docs/cli/changelog)
- [ACP slash commands](https://agentclientprotocol.com/protocol/v1/slash-commands)
- [Cursor prompting](https://cursor.com/docs/agent/prompting)

発見scope・衝突/不正定義・明示呼出し/引数・resume/Worktree・入力UX・採否と未観測を追記する。
