package com.cursoragent.ui.composer.image

import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

/** Call read off EDT. Ordinary text is deliberately left to the native editor paste/drop handler. */
internal object ImageTransfer {
    fun accepts(flavors: Array<DataFlavor>): Boolean = flavors.any {
        it == DataFlavor.javaFileListFlavor || it == DataFlavor.imageFlavor
    }

    fun read(value: Transferable): ValidatedImage {
        if (value.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val files = value.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: throw ImageInputException("画像ファイルを読み取れませんでした。")
            require(files.size == 1 && files.single() is File) { "画像は1枚ずつ添付してください。" }
            return ImageInput.file((files.single() as File).toPath())
        }
        require(value.isDataFlavorSupported(DataFlavor.imageFlavor)) { "PNGまたはJPEGの画像を選んでください。" }
        val image = value.getTransferData(DataFlavor.imageFlavor) as? Image ?: throw ImageInputException("クリップボードの画像を読み取れませんでした。")
        return ImageInput.clipboard(image)
    }
}

private inline fun require(value: Boolean, message: () -> String) {
    if (!value) throw ImageInputException(message())
}
