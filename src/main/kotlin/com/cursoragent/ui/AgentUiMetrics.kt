package com.cursoragent.ui

import com.intellij.util.ui.JBUI
import java.awt.Font

/** Slightly denser than IDE chrome, while respecting the user's IDE font scaling. */
internal object AgentUiMetrics {
    fun textFont(): Font = JBUI.Fonts.label().let { it.deriveFont(it.size2D * 0.92f) }
}
