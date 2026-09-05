package com.cursoragent

import com.intellij.openapi.diagnostic.logger

private val LOG = logger<CursorAgentPlugin>()

@Suppress("unused")
class CursorAgentPlugin {
    init {
        LOG.info("${PluginBrand.NAME} plugin loaded")
    }
}
