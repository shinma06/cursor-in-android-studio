package com.cursoragent.ui.composer.image

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.zip.CRC32
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ImageInputTest {
    @TempDir lateinit var directory: Path
    private fun image() = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().let { g -> try { g.color = java.awt.Color.RED; g.fillRect(0, 0, width, height) } finally { g.dispose() } }
    }
    private fun encoded(format: String = "png") = ByteArrayOutputStream().also { ImageIO.write(image(), format, it) }.toByteArray()

    @Test
    fun `PNG JPEG and clipboard produce the same-size immutable PNG snapshot`() {
        for (format in listOf("png", "jpeg")) {
            val original = encoded(format)
            val validated = ImageInput.encoded(original)
            original.fill(0)
            val bytes = validated.bytes()
            val parsed = ImageIO.read(bytes.inputStream())
            assertEquals(40, parsed.width)
            assertEquals(30, parsed.height)
            assertEquals(40, validated.width)
            bytes.fill(0)
            assertNotEquals(0, validated.bytes()[0].toInt())
        }
        val source = image()
        val clipboard = ImageInput.clipboard(source)
        source.setRGB(0, 0, java.awt.Color.BLUE.rgb)
        assertEquals(java.awt.Color.RED.rgb, ImageIO.read(clipboard.bytes().inputStream()).getRGB(0, 0))
    }

    @Test
    fun `reject unsupported broken and over-compressed-limit input`() {
        assertThrows(IllegalArgumentException::class.java) { ImageInput.encoded(encoded("gif")) }
        assertThrows(IllegalArgumentException::class.java) { ImageInput.encoded("not a PNG".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { ImageInput.encoded(encoded().copyOf(35)) }
        val error = assertThrows(IllegalArgumentException::class.java) { ImageInput.encoded(ByteArray(ImageInput.MAX_INPUT_BYTES + 1)) }
        assertTrue(error.message!!.contains("10 MiB"))
    }

    @Test
    fun `header dimensions are rejected before allocating the advertised pixels`() {
        for ((width, height) in listOf(4097 to 1, 1 to 4097, 4096 to 2000)) {
            val png = encoded()
            ByteBuffer.wrap(png, 16, 8).putInt(width).putInt(height)
            val crc = CRC32().apply { update(png, 12, 17) }.value.toInt()
            ByteBuffer.wrap(png, 29, 4).putInt(crc)
            val error = assertThrows(IllegalArgumentException::class.java) { ImageInput.encoded(png) }
            assertTrue(error.message!!.contains("4096"))
        }
    }

    @Test
    fun `normalization refuses a noisy image instead of silently resizing it`() {
        val random = Random(313)
        val large = BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until large.height) for (x in 0 until large.width) large.setRGB(x, y, random.nextInt())
        val error = assertThrows(IllegalArgumentException::class.java) { ImageInput.clipboard(large) }
        assertTrue(error.message!!.contains("512 KiB"))
    }

    @Test
    fun `Japanese source and symlink remain untouched after normalized snapshot creation`() {
        val original = directory.resolve("日本語 画像.png")
        Files.write(original, encoded())
        val link = directory.resolve("別名.png")
        Files.createSymbolicLink(link, original)
        val first = ImageInput.file(link)
        val captured = first.bytes()
        Files.delete(link)
        Files.write(original, byteArrayOf(0, 1, 2))
        assertArrayEquals(captured, first.bytes())
        assertEquals(java.awt.Color.RED.rgb, ImageIO.read(first.bytes().inputStream()).getRGB(0, 0))
        assertTrue(Files.exists(original))
    }
}
