# #149 ACP/print usage契約の実測調査

2026-09-12 / base `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。

調査中。stable `usage_update`、unstable `PromptResponse.usage`、print `result.usage`を分け、
合成最小fixtureで固定CLI/model/sessionを実測する。欠落を0とせず、context/turn/cost/account枠を混同しない。
製品source・既存履歴・#146原本・GUI・認証/課金設定は変更しない。#149全受入の完了とは区別する。
