package com.cursoragent.service

/** Server-advertised names are invocation IDs, not local filenames or inferred skill provenance. */
data class AgentCommand(val name: String, val description: String, val hint: String? = null)

sealed interface CommandCatalog {
    data object Unavailable : CommandCatalog
    data object Loading : CommandCatalog
    data object Awaiting : CommandCatalog
    data class Ready(val commands: List<AgentCommand>) : CommandCatalog
    data object Invalid : CommandCatalog
    data object Failed : CommandCatalog
}

fun CommandCatalog.containsCommand(name: String): Boolean = this is CommandCatalog.Ready && commands.any { it.name == name }

/** Preserve the advertised name and every argument character; never evaluate or quote it locally. */
fun commandPrompt(name: String?, arguments: String): String =
    if (name == null) arguments else "/$name" + if (arguments.isEmpty()) "" else " $arguments"
