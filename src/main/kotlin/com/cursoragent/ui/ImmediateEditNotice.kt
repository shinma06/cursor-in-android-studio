package com.cursoragent.ui

import com.intellij.ui.components.JBLabel

/** Shared explanation of the headless CLI's observed edit behavior. */
class ImmediateEditNotice : JBLabel(
    "<html>Edits can apply immediately,<br>" +
        "even with Ask Every Time.<br>" +
        "File-card Revert can undo applied edits.</html>",
)
