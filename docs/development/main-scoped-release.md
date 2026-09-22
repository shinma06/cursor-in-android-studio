# main起点のモダン化限定取り込み

2026-09-22 / #408・#409。ユーザー承認により、モダン化のmain受入にdevelop未反映の新機能を含めない。従来のdevelop全体promotionと別の候補を作る。旧RC/PR #402の結果は保持し、限定候補へ転用しない。#205/#404/#146は一律の前提とせず、今回mainとの差分・更新による影響から必要性を判断する。

## 信頼する計画と差分

先にGUI不要のtooling PRで、validator・既存RC/Verifier工具・`docs/verification/scopes/issue-409.json` をmainへ入れる。計画は対象Issue、具体的な許可ファイルとmode、全必須Caseの本文を持つ。候補側で計画や受入/公開コードを変更しない。計画の変更が必要なら別のmain向けtooling PRでレビューしてから、候補を固定し直す。

計画の許可ファイル内にも無関係な機能変更を混ぜない。独立レビューで元PR #396/#398/#400との選択差分を照合する。取り込まないものはdevelop専用の試験fixture、ACP等の未反映機能、Tab/@修正の自動取り込み。製品Kotlinは現在mainのままを原則とする。許可ファイルの一覧は機械検証できる外枠であり、内容の目的適合性は固定HEAD/baseの独立レビューで確認する。

## 候補と受入

1. 最新mainを取得し、そのSHAから専用Issue branchを作る。計画内の変更だけを線形履歴でcommitする。mainが変わったら既存候補を自動merge/rebaseで受入済みにせず、新しいmain基点と新候補の検証をやり直す。
2. 全既存mainテスト、必要tooling/CI、標準ZIP/構造、SDK入力拒否、同梱JAR/stdlib/JVMを確認する。CLI/Verifier成功と実IDE受入を区別する。
3. version 0.1.0、候補SHA、出力先を固定し、[既存RC生成手順](plugin-zip-delivery.md#正式候補rcと同一zipの公開)のbuildへ `--scope-issue 409` を付ける。buildはその時点のorigin/mainにある計画と全commitを検査してから生成する。初回検証用buildは正式RCではなく、正式RCのディレクトリを再生成しない。
4. 同じZIPで両IDE/JBRのVerifier、保存/取得hash照合、計画にあるQuail1/Quail4×Terminal ON/OFFの4Caseを実施する。main既存のチャット・設定・履歴の互換を含む。GUI lease・人間非操作確認・ロードbuild識別を保持し、未確認をpassにしない。
5. `Integration: promotion` / `GUI: required` のmain向けPRに、計画内 `acceptance` と完全一致する `docs/verification/changes/issue-409.json`、および `docs/verification/promotion.json` を追加する。候補より後に変更できるのはこの2ファイルだけ。Caseの観察はpromotionのresultsへ記録し、計画本文を削減しない。

promotion JSONはschema=1、scope="main"、base=固定main SHA、candidate=固定source SHA、artifact_sha256=同一ZIP hash、results=計画の全 `409:Case-ID` の観察結果。develop用のchanges配列は含めない。scope省略/"develop"は従来の全develop候補を検査し、未知scopeは拒否する。main限定はPRのIssueから計画pathを導出し、PR側の任意pathや新しい計画を信頼しない。

両履歴区間は1親の直線に限定し、各commitの差分をrename省略なしで検査する。範囲外変更後のrevert、merge経由の別機能、symlink/gitlink、許可以外のmode、候補後の製品変更を拒否する。Case結果は計画と完全一致し、同candidate/hashのpass・観察者・時刻・証拠・ロード識別を必要とする。4必須checks・独立レビュー・main保護・merge commitは従来どおり。

## 公開と引継ぎ

main受入後は既存 `release_candidate.py publish` が同じvalidatorを実行する。公開時の計画はpromotion mergeの第1親に固定し、公開後にmainが進んでもその候補を別のmainで再解釈しない。正式tagはmain merge、実build sourceはmanifestのcandidate。保存RCの同一bytesを公開し、全assetを再取得してhash照合する。再build・再resolve・再圧縮・version書換えは禁止。

限定候補の互換性/4Case/main受入は#409 / PR #411で完了。Phase 6 #393が同一ZIP公開/最終報告、#394が監査を追跡する。[正式0.1.0の結果](../releases/0.1.0.md)を参照。旧Phase 5 #392 / PR #402はsupersededの履歴で、develop全体の未反映機能/全Case受入は別TODOとして維持する。この限定公開の必須条件に戻さず、既存QAをまとめてcloseしない。既存owner/enrollment/PAUSED定期処理は自動変更しない。

## main候補の警告policy

#409の開発preflight（正式RC前、製品Kotlinはmainと同一）は171クラスで、両SDKともAPI互換性検査が完走した。任意依存はXPathView/Python/IDEA Community/trainingの4件。mainにないJCEF providerの例外は持ち込まない。両SDKで同一の非推奨8利用（うち削除予定1）・experimental 26利用のレポートhashをpolicyへ固定し、internal API例外は追加しない。API分類と限定許容の判断を示す記録であり、正式RC/GUI合格の証拠には転用しない。正式候補では再検査し、差があれば自動許容せず調査する。
