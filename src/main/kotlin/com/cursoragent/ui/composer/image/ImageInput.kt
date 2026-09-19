package com.cursoragent.ui.composer.image

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

/** Client policy, not a claim about provider limits. The sent image is never silently resized. */
internal object ImageInput {
    const val MAX_INPUT_BYTES = 10 * 1024 * 1024
    const val MAX_EDGE = 4096
    const val MAX_PIXELS = 8_000_000L
    const val MAX_PNG_BYTES = 512 * 1024

    fun file(path: Path): ValidatedImage {
        val before = Files.readAttributes(path, BasicFileAttributes::class.java)
        require(before.isRegularFile) { "PNGまたはJPEGの画像ファイルを選んでください。" }
        require(before.size() <= MAX_INPUT_BYTES) { "画像は10 MiB以下にしてください。" }
        val bytes = Files.newInputStream(path).use { it.readNBytes(MAX_INPUT_BYTES + 1) }
        val after = Files.readAttributes(path, BasicFileAttributes::class.java)
        require(before.fileKey() == after.fileKey() && before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime()) {
            "読取り中に画像が変わりました。もう一度添付してください。"
        }
        return encoded(bytes)
    }

    fun encoded(bytes: ByteArray): ValidatedImage {
        require(bytes.size <= MAX_INPUT_BYTES) { "画像は10 MiB以下にしてください。" }
        try {
            MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val readers = ImageIO.getImageReaders(input)
                require(readers.hasNext()) { "PNGまたはJPEGとして読み取れる画像を選んでください。" }
                val reader = readers.next()
                try {
                    require(reader.formatName.lowercase() in setOf("png", "jpeg", "jpg")) { "対応形式はPNGとJPEGです。" }
                    reader.setInput(input, false, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    dimensions(width, height)
                    require(reader.getNumImages(true) == 1) { "画像は1枚ずつ添付してください。" }
                    reader.addIIOReadWarningListener { _, _ -> throw ImageInputException("画像が破損しています。別の画像を選んでください。") }
                    val image = reader.read(0)
                    require(image.width == width && image.height == height) { "画像の寸法を確認できませんでした。" }
                    return clipboard(image)
                } finally { reader.dispose() }
            }
        } catch (error: ImageInputException) { throw error }
        catch (_: Exception) { throw ImageInputException("画像を読み取れませんでした。別のPNGまたはJPEGを選んでください。") }
    }

    fun clipboard(image: Image): ValidatedImage {
        val width = image.getWidth(null)
        val height = image.getHeight(null)
        dimensions(width, height)
        val normalized = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = normalized.createGraphics()
        try {
            require(graphics.drawImage(image, 0, 0, null)) { "画像の読取りが完了していません。もう一度貼り付けてください。" }
        } finally { graphics.dispose() }
        val bytes = ByteArrayOutputStream()
        var tooLarge = false
        val bounded = object : OutputStream() {
            override fun write(value: Int) {
                if (bytes.size() >= MAX_PNG_BYTES) { tooLarge = true; throw java.io.IOException("PNG limit") }
                bytes.write(value)
            }
            override fun write(data: ByteArray, offset: Int, length: Int) {
                if (length > MAX_PNG_BYTES - bytes.size()) { tooLarge = true; throw java.io.IOException("PNG limit") }
                bytes.write(data, offset, length)
            }
        }
        try {
            MemoryCacheImageOutputStream(bounded).use { output ->
                check(ImageIO.write(normalized, "png", output))
                output.flush()
            }
        } catch (error: Exception) {
            if (tooLarge) {
                throw ImageInputException("PNG変換後の画像が512 KiBを超えます。小さい画像に差し替えてください。")
            }
            throw ImageInputException("画像をPNGへ変換できませんでした。別の画像を選んでください。")
        }
        return ValidatedImage(bytes.toByteArray(), width, height)
    }

    private fun dimensions(width: Int, height: Int) {
        require(width > 0 && height > 0) { "画像の寸法を読み取れませんでした。" }
        require(width <= MAX_EDGE && height <= MAX_EDGE && width.toLong() * height <= MAX_PIXELS) {
            "画像は各辺4096ピクセル以下、合計800万画素以下にしてください。自動縮小は行いません。"
        }
    }
}

/** The encoded bytes cannot be changed through a caller's buffer. Never stringify image data. */
internal class ValidatedImage(bytes: ByteArray, val width: Int, val height: Int) {
    private val png = bytes.copyOf()
    fun bytes(): ByteArray = png.copyOf()

    /** Display-only thumbnail, produced off EDT. The original PNG sent to ACP stays unchanged. */
    fun thumbnail(): BufferedImage = MemoryCacheImageInputStream(ByteArrayInputStream(png)).use { input ->
        val reader = ImageIO.getImageReadersByFormatName("png").next()
        val image = try { reader.input = input; reader.read(0) } finally { reader.dispose() }
        val scale = minOf(1.0, 80.0 / width, 80.0 / height)
        BufferedImage(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()), BufferedImage.TYPE_INT_ARGB).also {
            val graphics = it.createGraphics()
            try { graphics.drawImage(image, 0, 0, it.width, it.height, null) } finally { graphics.dispose() }
        }
    }
}

private inline fun require(value: Boolean, message: () -> String) {
    if (!value) throw ImageInputException(message())
}

internal class ImageInputException(message: String) : IllegalArgumentException(message)
