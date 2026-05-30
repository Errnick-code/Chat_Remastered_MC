package dev.errnicraft.chatremastered

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

/**
 * Декодирует анимированный GIF в список кадров + задержек.
 * Использует стандартный javax.imageio (входит в JDK, зависимостей нет).
 *
 * ВАЖНО: javax.imageio возвращает дельта-кадры (только изменившиеся пиксели).
 * Мы вручную композитируем их на холст, чтобы неизменившиеся части не были прозрачными.
 */
object GifDecoder {

    data class GifFrame(val image: BufferedImage, val delayMs: Int)

    /**
     * Читает все кадры из GIF и возвращает ПОЛНЫЕ (composited) кадры.
     * @return список кадров или пустой список при ошибке.
     */
    fun decode(data: ByteArray): List<GifFrame> {
        return try {
            val readers = ImageIO.getImageReadersByFormatName("gif")
            if (!readers.hasNext()) return emptyList()
            val reader = readers.next()

            val iis = MemoryCacheImageInputStream(data.inputStream())
            reader.input = iis

            val count = reader.getNumImages(true)
            if (count == 0) return emptyList()

            val frames = mutableListOf<GifFrame>()

            // Читаем первый кадр чтобы узнать размер GIF
            val firstRaw = reader.read(0)
            val gifW = firstRaw.width
            val gifH = firstRaw.height

            // Холст для композитинга — хранит текущее состояние анимации
            val canvas = BufferedImage(gifW, gifH, BufferedImage.TYPE_INT_ARGB)
            val canvasG: Graphics2D = canvas.createGraphics()

            for (i in 0 until count) {
                val rawFrame = if (i == 0) firstRaw else reader.read(i)

                // Читаем метаданные: задержку и disposal method
                val meta = reader.getImageMetadata(i)
                val root = meta.getAsTree("javax_imageio_gif_image_1.0")

                var delayMs = 100
                var disposalMethod = "doNotDispose"
                var frameX = 0
                var frameY = 0

                val children = root.childNodes
                for (j in 0 until children.length) {
                    val node = children.item(j)
                    when (node.nodeName) {
                        "GraphicControlExtension" -> {
                            val delayAttr = node.attributes.getNamedItem("delayTime")
                            if (delayAttr != null) {
                                val raw = delayAttr.nodeValue.toIntOrNull() ?: 10
                                delayMs = (if (raw < 2) 10 else raw) * 10
                            }
                            val dispAttr = node.attributes.getNamedItem("disposalMethod")
                            if (dispAttr != null) {
                                disposalMethod = dispAttr.nodeValue
                            }
                        }
                        "ImageDescriptor" -> {
                            frameX = node.attributes.getNamedItem("imageLeftPosition")?.nodeValue?.toIntOrNull() ?: 0
                            frameY = node.attributes.getNamedItem("imageTopPosition")?.nodeValue?.toIntOrNull() ?: 0
                        }
                    }
                }

                // Рисуем дельта-кадр поверх холста
                canvasG.drawImage(rawFrame, frameX, frameY, null)

                // Снимаем снимок текущего состояния холста как готовый кадр
                val snapshot = BufferedImage(gifW, gifH, BufferedImage.TYPE_INT_ARGB)
                val snapG = snapshot.createGraphics()
                snapG.drawImage(canvas, 0, 0, null)
                snapG.dispose()

                frames.add(GifFrame(snapshot, delayMs))

                // Применяем disposal method для следующего кадра
                when (disposalMethod) {
                    "restoreToBackgroundColor" -> {
                        // Очищаем область текущего кадра до прозрачного
                        val composite = canvasG.composite
                        canvasG.composite = java.awt.AlphaComposite.Clear
                        canvasG.fillRect(frameX, frameY, rawFrame.width, rawFrame.height)
                        canvasG.composite = composite
                    }
                    // "doNotDispose" — оставляем холст как есть (самый частый случай)
                    else -> { /* ничего не делаем */ }
                }
            }

            canvasG.dispose()
            reader.dispose()
            frames
        } catch (e: Exception) {
            println("[Chat Remastered] GifDecoder error: ${e.message}")
            emptyList()
        }
    }

    /** Проверяет GIF magic bytes */
    fun isGif(data: ByteArray): Boolean =
        data.size >= 6 &&
        data[0] == 'G'.code.toByte() &&
        data[1] == 'I'.code.toByte() &&
        data[2] == 'F'.code.toByte() &&
        data[3] == '8'.code.toByte()
}
