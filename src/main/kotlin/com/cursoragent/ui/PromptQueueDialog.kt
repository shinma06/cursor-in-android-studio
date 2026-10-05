package com.cursoragent.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/** Opening management pauses automatic sends. Only the explicit OK action resumes them. */
internal class PromptQueueDialog(
    private val project: Project,
    private val queue: PromptQueue,
    private val isCurrent: () -> Boolean,
    private val onChanged: () -> Unit,
    private val onResume: () -> Unit,
    private val canSubmit: () -> Boolean,
    private val onSubmit: (QueuedPrompt) -> Boolean,
) : DialogWrapper(project, false) {
    private val list: PromptQueueList = PromptQueueList(queue,
        isCurrent = { !isDisposed && isCurrent() },
        shortcutAvailable = { listHasFocus() },
        onEdit = { editQueuedPrompt(project, queue, isCurrent, it) },
        onChanged = { isOKActionEnabled = queue.size > 0; onChanged() },
        onReturnToInput = { close(CANCEL_EXIT_CODE) },
        canSubmit = canSubmit,
        onSubmit = { if (onSubmit(it)) close(CANCEL_EXIT_CODE) },
    )

    private fun listHasFocus() = list.isFocusOwner && !JBPopupFactory.getInstance().isChildPopupFocused(list)
    private fun button(text: String, action: (QueuedPrompt) -> Unit) = JButton(text).apply {
        addActionListener {
            list.withSelected(action)
        }
    }

    init {
        title = "予約した入力（一時停止中）"
        setOKButtonText("予約送信を再開")
        setCancelButtonText("一時停止のまま閉じる")
        // Enter on a selected queue row must not silently resume every queued prompt.
        getOKAction().putValue(DEFAULT_ACTION, null)
        init()
        list.installShortcuts(disposable)
        list.refresh()
        isOKActionEnabled = queue.size > 0
    }

    override fun getPreferredFocusedComponent(): JComponent = list

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(8))).apply {
        preferredSize = JBUI.size(640, 440)
        add(JLabel("mode/model/追加設定と明示選択・添付は登録時に固定。自動context・参照内容と権限などは送信開始時です。"), BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(JPanel(java.awt.GridLayout(0, 3, JBUI.scale(6), JBUI.scale(6))).apply {
            add(button("編集…") { editQueuedPrompt(project, queue, isCurrent, it) })
            add(button("context…") { entry ->
                val snapshot = entry.context
                val details = buildString {
                    entry.image?.let { append("画像: ${it.width} × ${it.height}（登録時の画像を保持）\n") }
                    append("登録時のコマンド: ").append(entry.command?.let { "/$it" } ?: "なし").append("\n登録時の明示context\n")
                    snapshot?.selections?.forEach { append(it.block()).append("\n\n") }
                    snapshot?.terminals?.forEach { append(it.block()).append("\n\n") }
                    snapshot?.mentions?.forEach { append(it.displayLabel).append(" — ").append(it.insertToken).append("\n") }
                    append("\n参照内容と自動contextは次turn開始時。自動context: ")
                    append(if (snapshot?.automaticEnabled != false) "有効" else "無効")
                }
                com.intellij.openapi.ui.Messages.showInfoMessage(project, details.take(4_000), "予約項目のcontext")
            })
            add(button("画像プレビュー…") { entry ->
                val image = entry.image
                if (image == null) com.intellij.openapi.ui.Messages.showInfoMessage(project, "この予約に画像はありません。", "予約した入力")
                else com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                    val decoded = runCatching {
                        javax.imageio.stream.MemoryCacheImageInputStream(java.io.ByteArrayInputStream(image.bytes())).use { input ->
                            val reader = javax.imageio.ImageIO.getImageReadersByFormatName("png").next()
                            try { reader.input = input; reader.read(0) } finally { reader.dispose() }
                        }
                    }.getOrNull()
                    javax.swing.SwingUtilities.invokeLater {
                        if (!isDisposed && isCurrent() && queue.snapshot().any { it.id == entry.id && it.image === image }) {
                            if (decoded == null) com.intellij.openapi.ui.Messages.showInfoMessage(project, "画像を読み取れません。再添付してください。", "予約した入力")
                            else com.cursoragent.ui.composer.image.showImagePreview(decoded, list)
                        }
                    }
                }
            })
            add(button("画像を取り除く") { queue.removeImage(it.id) })
            add(button("削除") { queue.remove(it.id) })
            add(button("上へ") { queue.move(it.id, -1) })
            add(button("下へ") { queue.move(it.id, 1) })
        }, BorderLayout.SOUTH)
    }

    override fun doOKAction() {
        if (!isCurrent()) return
        close(OK_EXIT_CODE)
        onResume()
    }
}

/** Management keeps its simple text-only edit; the inline composer uses PromptQueueEditor. */
internal fun editQueuedPrompt(project: Project, queue: PromptQueue, isCurrent: () -> Boolean, entry: QueuedPrompt) {
    if (!isCurrent() || queue.snapshot().none { it.id == entry.id }) return
    object : DialogWrapper(project, false) {
        private val text = JBTextArea(entry.text, 8, 50).apply { lineWrap = true; wrapStyleWord = true }
        init {
            title = "予約した入力を編集" + (entry.command?.let { "（/$it の引数）" } ?: "")
            setOKButtonText("保存")
            setCancelButtonText("キャンセル")
            init()
        }
        override fun getPreferredFocusedComponent(): JComponent = text
        override fun createCenterPanel(): JComponent = JBScrollPane(text)
        override fun doOKAction() {
            if (!isCurrent()) return
            if (text.text.isBlank() && entry.command == null && entry.image == null) {
                com.intellij.openapi.ui.Messages.showInfoMessage(project, "空の入力は予約できません。削除する場合は一覧の「削除」を使ってください。", "予約した入力")
                return
            }
            if (queue.edit(entry.id, text.text)) super.doOKAction()
        }
    }.show()
}
