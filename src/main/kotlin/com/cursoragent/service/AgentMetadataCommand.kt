package com.cursoragent.service

import com.cursoragent.settings.detectAgentExecutable
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.nio.charset.StandardCharsets

/** Existing CLI metadata runner shared by project actions and application settings. Call off EDT. */
internal fun runAgentMetadataCommand(configuredPath: String, workspace: String, timeoutMs: Int, vararg args: String): String? =
    try {
        val commandLine = GeneralCommandLine(resolveAgentExecutable(configuredPath), *args)
            .withWorkDirectory(File(workspace))
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment(System.getenv())
        val output = ExecUtil.execAndGetOutput(commandLine, timeoutMs)
        output.stdout.takeIf { output.exitCode == 0 && !output.isTimeout && !output.isCancelled }
    } catch (e: Exception) {
        logger<AgentProcessService>().warn("agent ${args.joinToString(" ")} failed", e)
        null
    }

internal fun resolveAgentExecutable(configuredPath: String): String {
    if (configuredPath.isNotBlank() && File(configuredPath).canExecute()) return configuredPath
    return detectAgentExecutable() ?: "agent"
}
