package com.cursoragent.ui.composer.image

import com.cursoragent.ui.composer.image.ImageAttachmentStore.ImageAttachment
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.concurrent.Executor
import javax.imageio.ImageIO

/** One tab's draft, called on EDT. Disk/decode/release work uses the supplied serial background executor. */
internal class ImageDraft(
    private val worker: Executor,
    private val deliver: ((() -> Unit) -> Unit),
    private val store: () -> ImageAttachmentStore,
    private val changed: () -> Unit,
    private val retained: (String) -> Unit = {},
    private val scope: () -> Any? = { null },
) : AutoCloseable {
    private var generation = 0L
    private var closed = false
    val hasUnsent: Boolean get() = importing || attachment != null
    var importing = false
        private set
    var attachment: ImageAttachment? = null
        private set
    var preview: BufferedImage? = null
        private set
    var error: String? = null
        private set

    /** Reject another image explicitly; never replace the existing or pending attachment implicitly. */
    fun import(read: () -> ValidatedImage): Boolean {
        if (closed) return false
        if (importing || attachment != null) {
            error = "画像は1枚までです。現在の画像を取り除いてから添付してください。"
            changed()
            return false
        }
        val ticket = ++generation
        val owner = scope()
        importing = true
        error = null
        changed()
        worker.execute {
            var saved: ImageAttachment? = null
            var decoded: BufferedImage? = null
            var failure: String? = null
            try {
                val image = read()
                decoded = javax.imageio.stream.MemoryCacheImageInputStream(ByteArrayInputStream(image.bytes())).use { input ->
                    val reader = ImageIO.getImageReadersByFormatName("png").next()
                    try { reader.input = input; reader.read(0) } finally { reader.dispose() }
                }
                check(decoded != null)
                saved = store().save(image)
            } catch (problem: Exception) {
                // Validation supplies controlled messages; filesystem/clipboard exceptions may contain paths.
                failure = if (problem is ImageInputException) problem.message
                    else "画像を添付できませんでした。もう一度添付してください。"
            }
            deliver {
                if (closed || ticket != generation || owner != scope()) {
                    saved?.let(::release)
                    if (!closed && ticket == generation) {
                        importing = false
                        error = "読込み中に会話または設定が変わりました。もう一度添付してください。"
                        changed()
                    }
                }
                else {
                    importing = false
                    attachment = saved
                    preview = decoded.takeIf { saved != null }
                    error = failure
                    changed()
                }
            }
        }
        return true
    }

    /** Use for explicit removal, draft replacement, or a changed transport/session/model during import. */
    fun invalidateImport() {
        ++generation
        importing = false
        changed()
    }

    fun clear() {
        ++generation
        importing = false
        attachment?.let(::release)
        attachment = null
        preview = null
        error = null
        changed()
    }

    /** The caller owns a separate immutable lease for its queue item or in-flight turn. */
    fun retain(): ImageAttachment? = attachment?.retain()

    private fun release(image: ImageAttachment) {
        // A project service may already have closed the store and stopped its executor.
        runCatching { worker.execute {
            runCatching { image.close() }.onFailure {
                retained("解放できなかった添付一時データを保持しました。所有プロセス終了後に再確認します。")
            }
        } }
    }

    override fun close() {
        if (closed) return
        closed = true
        clear()
    }
}
