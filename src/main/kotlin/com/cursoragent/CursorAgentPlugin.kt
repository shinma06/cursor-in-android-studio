package com.cursoragent

import com.intellij.openapi.diagnostic.logger

private val LOG = logger<CursorAgentPlugin>()

@Suppress("unused")
class CursorAgentPlugin {
    init {
        LOG.info("Cursor Agent plugin loaded")
    }
}
