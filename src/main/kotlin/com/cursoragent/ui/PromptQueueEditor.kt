package com.cursoragent.ui

import com.cursoragent.ui.composer.ComposerDraft
import com.cursoragent.ui.composer.ComposerPanel
import com.cursoragent.ui.composer.context.PromptContextSnapshot
import com.cursoragent.ui.composer.image.ImageAttachmentStore.ImageAttachment

/** Hold one tab's edited queue item; the hidden draft and queue retain independent image leases. */
internal class PromptQueueEditor(
    private val queue: PromptQueue,
    private val composer: ComposerPanel,
    private val isCurrent: () -> Boolean,
    private val releaseImage: (ImageAttachment) -> Unit,
    private val changed: () -> Unit,
    private val error: (String) -> Unit,
    private val onQueueReady: () -> Unit,
    private val onSend: (QueuedPrompt) -> Unit,
) : AutoCloseable {
    private data class Edit(val expected: QueuedPrompt, val draft: ComposerDraft)
    private var edit: Edit? = null
    private var closed = false
    val isEditing: Boolean get() = edit != null

    fun begin(id: String) {
        if (closed || !isCurrent() || isEditing || !composer.inputArea.isEnabled || !composer.panelShortcutAvailable ||
            composer.images?.importing == true) return
        val entry = queue.snapshot().find { it.id == id } ?: return
        val saved = try { composer.captureDraft() } catch (_: IllegalStateException) {
            error("下書きの画像を保持できません。再添付してください。")
            return
        }
        val image = try { entry.image?.retain() } catch (_: IllegalStateException) {
            saved.image?.let(releaseImage)
            error("予約の画像を読み取れません。予約一覧で画像を確認してください。")
            return
        }
        if (!queue.beginEdit(entry)) {
            saved.image?.let(releaseImage)
            image?.let(releaseImage)
            return
        }
        edit = Edit(entry, saved)
        composer.showQueueEdit(true)
        composer.restoreDraft(ComposerDraft(entry.text, null, entry.mode, entry.model,
            entry.context ?: PromptContextSnapshot(emptyList(), emptyList(), true), entry.command, image,
            modelConfiguration = entry.modelConfiguration, modelParameters = entry.modelParameters))
        changed()
        onQueueReady()
        if (composer.isShowing) composer.inputArea.requestFocusInWindow()
    }

    fun submit() {
        val session = edit ?: return
        if (!isCurrent() || !composer.queueEditAvailable || composer.images?.importing == true) return
        val context = try {
            // Existing selections belong to the queued snapshot; only newly added/changed ones are revalidated.
            composer.promptContext.snapshot(session.expected.context?.selections.orEmpty())
        } catch (problem: IllegalArgumentException) {
            error(problem.message ?: "追加したcontextを確認してください。")
            return
        }
        val command = composer.commands.selectedName
        if (command != null && command != session.expected.command && !composer.commands.canInvoke(command)) {
            error("選択したコマンドを確認できません。候補から再選択してください。")
            return
        }
        val image = try { composer.images?.retain() } catch (_: IllegalStateException) {
            error("画像を読み取れません。再添付してください。")
            return
        }
        val replacement = session.expected.copy(text = composer.inputArea.text, mode = composer.selection.mode,
            model = composer.selection.selectedModel, context = context, command = command, image = image,
            modelParameters = composer.modelSelector.parameterValues.toMap(), modelConfiguration = composer.modelSelector.acpConfiguration)
        if (!queue.replace(session.expected, replacement)) {
            image?.let(releaseImage)
            error(if (replacement.text.isBlank() && command == null && image == null) "空の入力は予約できません。"
                else "予約が変更されています。編集内容をコピーしてからキャンセルしてください。")
            return
        }
        finish(session, replacement.takeUnless { composer.isRunning })
    }

    fun cancel() {
        val session = edit ?: return
        if (isCurrent() && composer.queueEditAvailable) finish(session)
    }

    private fun finish(session: Edit, send: QueuedPrompt? = null) {
        // Transfer the saved lease back exactly once; clear/replacement releases the temporary editing lease.
        edit = null
        composer.restoreDraft(session.draft)
        composer.showQueueEdit(false)
        queue.endEdit(session.expected.id)
        changed()
        if (send != null) onSend(send) else onQueueReady()
        if (composer.isShowing) composer.inputArea.requestFocusInWindow()
    }

    override fun close() {
        closed = true
        edit?.draft?.image?.let(releaseImage)
        edit?.expected?.id?.let(queue::endEdit)
        edit = null
        composer.showQueueEdit(false)
    }
}
