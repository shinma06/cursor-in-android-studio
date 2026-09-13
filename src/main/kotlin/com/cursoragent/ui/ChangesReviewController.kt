package com.cursoragent.ui

/** Dialog lifecycle only; the existing IDE dialog and synthetic tests use the same review actions. */
internal interface ChangesReviewView {
    fun show()
    fun cancel()
}

internal class ChangesReviewController(
    private val changes: ConversationChanges,
    private val pauseQueue: () -> Unit,
    private val isAlive: () -> Boolean,
    private val captureCurrent: () -> (() -> Boolean),
    private val createView: (ChangesSnapshot, (FileChangeGroup) -> Unit, (FileChangeGroup) -> Unit, () -> Unit) -> ChangesReviewView,
    private val onDiff: (FileChangeGroup) -> Unit,
    private val onRevert: (FileChangeGroup, () -> Boolean) -> Unit,
    private val onConversation: () -> Unit,
) {
    private var view: ChangesReviewView? = null

    fun show() {
        if (!isAlive()) return
        pauseQueue()
        view?.let { it.cancel(); view = null; return }
        val snapshot = changes.snapshot()
        val ownerIsCurrent = captureCurrent()
        val isCurrent = { ownerIsCurrent() && changes.snapshot() == snapshot }
        val opened = createView(snapshot,
            { file -> if (isAlive() && file.canShowDiff) onDiff(file) },
            { file ->
                pauseQueue()
                if (isAlive() && file.revertRejection == null) onRevert(file, isCurrent)
            },
            { if (isAlive()) onConversation() },
        )
        view = opened
        try { opened.show() } finally { if (view === opened) view = null }
    }

    fun dispose() {
        view?.cancel()
        view = null
    }
}
