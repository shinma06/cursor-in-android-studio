package com.cursoragent.ui.composer.mention

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.EditorTextField

/**
 * Watches an [EditorTextField]'s document for a lone `@` keystroke and offers a
 * filterable popup of mention candidates (files/folders from the project tree,
 * plus fixed Git diff/Terminal/Docs/Web entries). Selecting one replaces that `@`
 * with `@<token> ` — filtering happens in the popup's own speed search (focus
 * moves there while it's open) rather than by tracking a live query in the
 * composer's document, which keeps this robust without needing to reconcile two
 * text sources.
 */
class MentionPopupController(
    private val project: Project,
    private val field: EditorTextField,
) {
    fun install() {
        field.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (event.newLength == 1 && event.newFragment.toString() == "@") {
                    showPopup(triggerOffset = event.offset)
                }
            }
        })
    }

    private fun showPopup(triggerOffset: Int) {
        val candidates = MentionCandidateSource.buildCandidates(project)

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(candidates)
            .setTitle("Add context")
            .setRenderer(MentionListCellRenderer())
            .setItemChosenCallback { mention -> insertMention(triggerOffset, mention) }
            .createPopup()
            .showUnderneathOf(field)
    }

    private fun insertMention(triggerOffset: Int, mention: Mention) {
        val document = field.document
        if (triggerOffset < 0 || triggerOffset >= document.textLength || document.charsSequence[triggerOffset] != '@') {
            return
        }
        ApplicationManager.getApplication().runWriteAction {
            document.replaceString(triggerOffset, triggerOffset + 1, "@${mention.insertToken} ")
        }
    }
}
