# #150 Android選択対象の統合: 資料・固定SDKの事前照合

2026-09-12 / 調査base `9979266b4e99e7d4dc46689dac1f61f33f09b88a`。

資料scopeの調査中。#150の実IDE比較・2module/2Variant/2device実測・最初の機能選定は未完了。
GUI/install/restart/認証変更/MCP公開は行わず、製品source・共有architectureを変更しない。

対象installed IDEはAndroid Studio build `AI-261.26222.65.2614.16204760`。
APIの存在、公開契約、実IDEでの対象正確性、競合との差を区別して記録する。
Document/PSI・project model/Variant/sync・run/execution・device/ADB・Logcat/debuggerを照合し、
T17/T18の比較用fixtureと手順を具体化する。#152のGUI未達は資料調査の停止理由にしない。

初回実装の採用先は実比較後に決定する。本PRだけで#150をcloseしない。
