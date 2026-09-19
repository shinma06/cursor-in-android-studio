package com.cursoragent.ui.composer.image

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

/** Present only while importing, attached, or reporting a failed import. Native buttons support keyboard use. */
internal class ImageAttachmentPanel(private val draft: ImageDraft) : JPanel(BorderLayout()) {
    private val preview = JButton().apply {
        accessibleContext.accessibleName = "添付画像をプレビュー"
        toolTipText = "送信する画像をプレビュー"
        addActionListener {
            draft.preview?.let { image ->
                showImagePreview(image, this)
            }
        }
    }
    private val remove = JButton("画像を取り除く").apply {
        accessibleContext.accessibleName = text
        addActionListener { draft.clear() }
    }
    private val message = JLabel()

    init {
        isOpaque = false
        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            isOpaque = false
            add(preview)
            add(remove)
        }, BorderLayout.CENTER)
        add(message, BorderLayout.SOUTH)
        refresh()
    }

    fun refresh() {
        val image = draft.preview
        preview.isVisible = image != null
        remove.isVisible = image != null || draft.importing
        preview.icon = image?.let {
            // Paint a thumbnail from the already decoded snapshot; the original preview/sent pixels remain intact.
            val scale = minOf(1.0, 80.0 / it.width, 80.0 / it.height)
            object : javax.swing.Icon {
                override fun getIconWidth() = maxOf(1, (it.width * scale).toInt())
                override fun getIconHeight() = maxOf(1, (it.height * scale).toInt())
                override fun paintIcon(component: java.awt.Component?, graphics: java.awt.Graphics, x: Int, y: Int) {
                    graphics.drawImage(it, x, y, iconWidth, iconHeight, null)
                }
            }
        }
        message.text = draft.error ?: if (draft.importing) "画像を読み込んでいます…" else ""
        message.isVisible = message.text.isNotEmpty()
        isVisible = image != null || draft.importing || draft.error != null
        revalidate()
        repaint()
    }
}

internal fun showImagePreview(image: java.awt.image.BufferedImage, anchor: javax.swing.JComponent) {
    val content = JScrollPane(JLabel(ImageIcon(image)).apply {
        accessibleContext.accessibleName = "添付画像の原寸プレビュー"
    }).apply { preferredSize = JBUI.size(minOf(image.width + 24, 700), minOf(image.height + 24, 500)) }
    val copy = JButton("画像をコピー").apply {
        toolTipText = "再添付するためにプレビューの画像をクリップボードへコピーします。送信は行いません。"
        addActionListener {
            com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(object : java.awt.datatransfer.Transferable {
                override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.imageFlavor)
                override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor) = flavor == java.awt.datatransfer.DataFlavor.imageFlavor
                override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any {
                    if (!isDataFlavorSupported(flavor)) throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
                    return image
                }
            })
        }
    }
    val panel = JPanel(BorderLayout()).apply { add(content, BorderLayout.CENTER); add(copy, BorderLayout.SOUTH) }
    JBPopupFactory.getInstance().createComponentPopupBuilder(panel, copy)
        .setTitle("添付画像（${image.width} × ${image.height}）")
        .setFocusable(true).setRequestFocus(true).setResizable(true)
        .createPopup().showUnderneathOf(anchor)
}
