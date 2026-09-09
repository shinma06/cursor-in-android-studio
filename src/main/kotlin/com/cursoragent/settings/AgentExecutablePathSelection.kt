package com.cursoragent.settings

import java.io.File

/** Keeps the displayed automatic path separate from the empty persisted override. */
internal class AgentExecutablePathSelection(
    private val detectExecutable: () -> String? = ::detectAgentExecutable,
) {
    var configuredPath: String = ""
        private set
    var displayedPath: String = ""
        private set
    private var detectedPath: String? = null

    val description: String
        get() = when {
            configuredPath.isNotBlank() -> "手動指定。「自動検出に戻す」で標準のCLIに戻せます。"
            displayedPath.isBlank() -> "適用すると自動検出に戻り、検出したパスを表示します。"
            detectedPath == null -> "自動検出：固定候補なし。実行時にPATHからagentを検索します（未確認）。"
            else -> "自動検出したパスを表示中。空欄で適用しても自動検出に戻せます。"
        }

    fun reset(savedPath: String) {
        if (savedPath.isBlank()) {
            useAutomatic()
        } else {
            configuredPath = savedPath
            displayedPath = savedPath
        }
    }

    fun useAutomatic() {
        configuredPath = ""
        detectedPath = detectExecutable()
        displayedPath = detectedPath ?: "agent"
    }

    fun edit(text: String) {
        if (text == displayedPath) return
        displayedPath = text
        configuredPath = text.takeIf { it.isNotBlank() }.orEmpty()
    }
}

/** Shared fixed-candidate lookup; null leaves PATH resolution to process launch. */
internal fun detectAgentExecutable(): String? = listOf(
    "/usr/local/bin/agent",
    "/opt/homebrew/bin/agent",
    "${System.getProperty("user.home")}/.local/bin/agent",
).firstOrNull { File(it).canExecute() }
