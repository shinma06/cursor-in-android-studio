# 会話本文の保存契約（#44）

対象: PRINT/ACPで今後観測し表示した会話。providerの全履歴を取得する機能ではない。

## 変更前・目標・適用する構造

| 変更前 | 目標と実配置 | 理由 |
| --- | --- | --- |
| settings/ChatHistoryStateにprint ID/previewのみ | 同じclass/XML/登録を保持。旧metadataの読取・明示削除 | 保存XML名・旧provider IDを無断変更しない |
| SessionTabsのtab UUID・run token | conversationIdを独立追加、token.turnIdを追加 | tabの再作成、provider ID、run参照同一性を混同しない |
| controller/listener → timelineだけ | history/Conversation（値・Recorder）→ ConversationHistory（project worker）→ ConversationStore（JSON IO） | UIと保存を分離、既存Gson/JDKでatomic書込み |
| root/PastChatsCoordinatorはmetadataを選ぶ | 保存本文選択・閲覧・明示削除。IOはworker、描画はEDT | provider通信なしで再表示、旧履歴は本文なし |

既存ファイルは移動しない。登録XML/永続型の互換性と既存探索経路を保持し、保存責務だけhistoryへ置く。新しいDB、Event Sourcing、汎用repository interfaceは追加しない。src/mainとsrc/test配下なのでChange Impactの既存runtime/test分類で検証する。

## ID・形式・更新

v1のConversationはid(UUID)、transport、nullable providerId、root/worktreeMode、updatedMs、turns。SavedTurnはid(UUID)、state、messages。ChatMessageはid(UUID)、role(user/assistant/tool/error)、text。配列が順序。会話ID、tab ID、turn UUID、run tokenの参照同一性、provider IDは別物。provider IDはPRINT/ACPと組で解釈する。providerから取得できないIDを生成して補わない。

root/worktreeModeは直近の実行先来歴でありturn別の復元権限ではない。履歴を開くsnapshotはそのview/controllerへ渡すだけでrootにキャッシュせず、closeで解放する。

同じassistant全文置換はmessage IDを維持。ACP startsMessageで新ID、tool更新はturn内のprovider call IDをlocal message IDへ対応付ける。call ID自体や実行可能な要求は保存しない。printは#254のservice内正規化と同じ全文を保存する。実測版・partial指定・未知版fallbackの範囲は[event-contracts](event-contracts.md)に従い、保存側で再度dedupeしない。

controller/listenerのisCurrent/token/停止guard後だけRecorderを更新。close/disposeはcallbackを待たずinterruptedで終える。workerは最新の会話snapshotを200msごとにまとめ、順に書く。書込み完了したrevisionだけ「保存済み」、保留は「保存中」、例外/上限は保存失敗。保存失敗はAgent応答失敗とは別。突然のprocess停止は最後の200ms以内の未完了保存を失い得る。OS電源断の完全な耐久性は保証しない。

IDE config配下のcursor-agent-conversations/project.locationHashへ1会話1ファイル。repoやSettings Syncの登録XMLへ本文を置かない。POSIXではdirectory0700/file0600。一時ファイルへUTF-8を書きforceしatomic replace、非対応/失敗は旧ファイルを保全する。v1以外・不正schema・破損したファイルはそのまま隔離扱いで件数を通知し、他会話をロードする。旧XMLはversion0相当のmetadata-onlyとして継続表示し、本文の空履歴を生成しない。今後v2が必要ならv1 fixtureを使った明示移行を追加する。

## 保存範囲と保持・削除

元入力と表示済みassistant本文はそのまま保存する（ユーザー/Agent自身が本文に書いた秘密を自動検出・完全除去する保証はない）。注入context・環境変数・credential設定・raw wire・思考・tool引数/command/stdout/stderr/diff全文・permission回答/実行callbackは保存しない。toolは許可した種別/状態、errorは安全な固定文/終了コードのみ。タイトル取得は#66を待ち、履歴previewから正式な会話名を作らない。

保持期間は無期限、上限100会話/各8 MiB。超過は既存会話を自動削除せず保存失敗として表示する。破損/未来形式も会話ファイル件数に数えて保護する。履歴から明示確認で1会話ずつ削除し、開いている会話は先に閉じる。元XMLに同print IDがあれば同時にmetadataも削除する。外部providerの会話を削除する操作ではない。未送信draftは保存対象外。Close Savedを新設せず、bulk closeはdraft/保存失敗データの消失を確認する。

## 本文・再開・Revertを別々に判定

| 来歴 | 本文表示 | provider再開 | 保存カードのRevert |
| --- | --- | --- | --- |
| v1 PRINT、既知root/DEFAULT一致、provider IDあり | 可 | 次送信で試行、外部失敗は表示 | 不可 |
| PRINT root不明/異なる/ISOLATED | 可 | 不可、閲覧専用 | 不可 |
| ACPの保存本文 | 可 | 現行接続外のload/resume未接続のため不可 | 不可 |
| 旧XML metadata | 本文なし明示 | 来歴不明のため不可 | 不可 |
| 未完turn | interruptedとして保存された分だけ表示 | 自動再送しない | 不可 |

UI再表示はuser/assistant/tool要約/errorと終端状態だけ。過去tool/permissionは再実行しない。現行serviceのRestoreTarget/WorkspaceOperationGateを保存IDから復活させない。再開可否のUI案内は背景threadで実体パスを確認し、送信前にも固定commandTargetと保存rootを既存RestorePolicyで再照合する。正当なsymlink経由は同じ実rootなら許可し、準備中のsymlink置換やroot変更は拒否する。新規PRINTの実行後でも、過去保存カードからRevertはできない。

## 公式比較と採用根拠

Cursor IDE panelに[会話履歴](https://docs.cursor.com/en/agent/chat/history)があり、JetBrains AI Assistantも[project単位・IDE sessionを跨ぐ履歴](https://www.jetbrains.com/help/ai-assistant/chat-mode.html)を備えるため、本文保存は同等UXへ向けた不足解消であって独自機能ではない。比較相手は[Cursor ACP統合](https://cursor.com/docs/integrations/jetbrains)に[IDE integration / IntelliJ MCP Server](https://www.jetbrains.com/help/idea/mcp-server.html)を含む構成。IDE/MCPの能力はローカル本文の保存保証とは別で、Android Studio上の実操作成功を文書から推定しない。

ACP標準の[session/loadとsession/resume](https://agentclientprotocol.com/protocol/v1/session-setup)はcapability確認を要求する。現行AcpSessionはsession/newのみであり、本変更は自前の表示データ保存を接続する。外部履歴取得を再実装せず、provider能力の接続/実wire検証は既存#115/#146/#141で扱う。直接IDE統合による根拠はproject分離と既存root/復元排他の維持であり、競合を超える能力は実測していない。

## 検証・後続担当

Case正本は[issue-44.json](../verification/changes/issue-44.json)。移行・破損・容量/書込み失敗・ID/順序・未完turn・PRINT/ACP非互換はConversationStoreTestとConversationResumeRootTest、イベント/Stop guardは既存dispatch/text/tab回帰と組み合わせる。実IDE再起動は同じ固定buildを指定GUI lease担当が確認し、未観察はpendingでQAへ引き継ぐ。

#45/#47/#48へConversation/SavedTurn/ChatMessageと独立IDを渡す。共有controller/listener/ComposerはEngineer Bが直列writer、scripts/workflowはEngineer A、#116は研究担当。次回の保存型/ID/配置/イベント境界変更時はwriterと独立reviewerが旧XML/v1 fixture、失敗隔離、秘密の保存範囲、再実行禁止を既存レビューで確認する。運用効果は次回の実変更まで未測定。High Impact監査はPMの#251へ統合する。
