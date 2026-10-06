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

## Rabbit / Java 25のmain反映（#470）

[#470](https://github.com/shinma06/cursor-in-android-studio/issues/470)は上記限定経路を再利用する。事前計画は[issue-470.json](../verification/scopes/issue-470.json)。mainのprint構成を保ち、SDK/JVM/ビルド依存、単一RabbitのCIと保存済みVFSのdisk境界だけを対象にする。ACP、会話本文保存、JCEF Browserをこの候補へ追加しない。

移行準備の#471では既存mainのQuail検査と次のRabbit計画を別枠で固定した。#470統合後の#474で現行policyをRabbit単一対象へ切り替え、現行ZIPの検査から一時的な計画選択を除去した。過去のRCを読む場合だけ、その固定sourceのpolicyと検証資料を使う。QuailのAPI警告承認をRabbitへ転用せず、旧RCのbytes/manifestは変更しない。

新候補の必要CaseはRabbit/JBR25・Terminal ON/OFFと非同期保存境界の3件。過去の#409やdevelopの#466での結果は転用しない。#470では`scoped_candidate`で固定mainからの全commitを検査し、標準`buildPlugin`のSNAPSHOT ZIPをsealして、同一ZIPでVerifierとGUIを実施する。上記のversion 0.1.0によるRC生成は#409の履歴であり、#470の前提にしない。正式version・Release公開は今回のmain反映とは別の指示を必要とする。既存のscope=main gate、線形候補、候補後の2 JSONだけの更新、独立レビューと4 checksを維持する。

## 元C＋局所PCEの新限定候補（#562）

#562は親#485の2段階の作業。段階AはGUI不要のmain toolingで、段階BはA統合後の最新mainから別branch/worktreeを作る。旧#485の3候補・原証拠・Case結果は保存する。後続developの新機能は含めない。隔離preflightのsource/ZIPを正式candidateやGUI合格と扱わない。

既存scope gateを次の3点だけ拡張する。`files` のmode `000000` は固定baseに実在するregular fileの宣言削除に限る。symlink/gitlink削除、未宣言削除、削除後の再追加とrevertも拒否する。`scripts/workflow/plugin_compatibility.json` だけは `compatibility_policy` に固定されたJSON全体と完全一致する変更を認め、各commitと最終treeを検査する。他のgate・workflow・受入JSONは候補から変更できない。

歴史CaseのRabbit環境移行は、信頼するmainの `environments/rabbit1.json` のhashを `environment_revision`、新Case ID→元Case keyを `environment_cases` へ固定する。`case_origins` の原本本文/hash/merged PR・merge SHA・pathと照合し、candidate SDKも一致させる。結果は新candidate/hashに加えて同じenvironment revision/runtimeへ拘束する。生成された一覧もこの条件を満たさなければCase合格を表示しない。元sourceを実行したり、移動するdevelopの最新Caseを使ったりしない。

active main policyは段階Aでは変更しない。C＋局所PCEの同一ZIP preflightで得たreport hashと判断をscope計画へ固定し、段階Bで製品sourceと一緒にcandidateへ配置する。既存Verifier/RC/CIの通常policy読込を維持する。Case原本395件の対応と承認済み訂正の競合解消を独立レビューし、新buildの受入は最低175件すべてpendingから開始する。
