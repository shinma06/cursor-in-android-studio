package com.cursoragent.settings

import com.cursoragent.PluginBrand
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import java.io.File
import java.util.Properties

internal fun readPluginDiagnostics(): String {
    val identity = Properties()
    runCatching {
        PluginBrand::class.java.getResourceAsStream("/cursor-agent-build.properties")?.use(identity::load)
    }
    val version = PluginManagerCore.getPlugin(PluginId.getId("com.cursoragent.plugin"))?.version
    return pluginDiagnosticsText(
        version = version,
        commit = identity.getProperty("source.commit"),
        sourceState = identity.getProperty("source.state"),
        ideBuild = ApplicationInfo.getInstance().build.asString(),
        configuredPath = AgentSettingsState.getInstance().agentExecutablePath,
        detectedPath = detectAgentExecutable(),
    )
}

/** Only these explicit fields can enter the clipboard; no process, account, environment or chat reads. */
internal fun pluginDiagnosticsText(
    version: String?,
    commit: String?,
    sourceState: String?,
    ideBuild: String?,
    configuredPath: String,
    detectedPath: String?,
): String {
    val build = when {
        sourceState == "dirty" -> "未特定（未commit変更を含む開発ビルド）"
        sourceState != "clean" || commit == null || !Regex("[0-9a-f]{40}").matches(commit) -> "未特定（識別情報なし）"
        else -> commit
    }
    val manual = configuredPath.takeIf { it.isNotBlank() }
    // Match the existing launcher's manual override condition; fixed lookup is shared.
    val manualUsable = manual != null && File(manual).canExecute()
    val selected = if (manualUsable) manual else detectedPath
    val candidate = when {
        selected == null -> "未解決（実行時にPATHからagentを検索。未確認）"
        !File(selected).isAbsolute -> "${diagnosticLine(selected)}（相対パス。解決先・起動は未確認）"
        !File(selected).isFile || !File(selected).canExecute() -> "${diagnosticLine(selected)}（実行可能ファイルを確認できません）"
        else -> "${diagnosticLine(selected)}（ファイル確認のみ。CLI起動は未確認）"
    }
    val mode = when {
        manual == null -> "自動探索（空欄設定）"
        manualUsable -> "手動指定: ${diagnosticLine(manual)}"
        else -> "手動指定を利用できません: ${diagnosticLine(manual)}。既存の自動探索へ戻ります"
    }
    return """
        ${PluginBrand.NAME}
        プラグインversion: ${diagnosticLine(version?.takeIf { it.isNotBlank() } ?: "未特定")}
        ビルドID: $build
        IDE build: ${diagnosticLine(ideBuild?.takeIf { it.isNotBlank() } ?: "未特定")}
        CLI設定: $mode
        CLI解決候補: $candidate
        CLI版・認証状態: 未取得（CLI未実行）
    """.trimIndent()
}

private fun diagnosticLine(value: String): String = value.map {
    if (it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()) ' ' else it
}.joinToString("")
