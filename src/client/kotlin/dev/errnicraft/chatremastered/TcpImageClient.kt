package dev.errnicraft.chatremastered

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * TCP-клиент для общения с ImageHttpServer.
 * Все методы — синхронные, вызывать в отдельном потоке!
 */
object TcpImageClient {

    private const val CMD_PING         = 0x01.toByte()
    private const val CMD_UPLOAD       = 0x02.toByte()
    private const val CMD_GET_FULL     = 0x03.toByte()
    private const val CMD_GET_THUMB    = 0x04.toByte()

    private const val RES_OK        = 0x00.toByte()
    private const val RES_NOT_FOUND = 0x01.toByte()
    private const val RES_FORBIDDEN = 0x01.toByte()

    private const val UPLOAD_MAX_ATTEMPTS   = 4
    private const val UPLOAD_BASE_DELAY_MS  = 1_000L
    private const val DOWNLOAD_MAX_ATTEMPTS = 5
    private const val DOWNLOAD_BASE_DELAY_MS = 500L

    private fun connect(timeout: Int = 5000): Socket {
        val s = Socket()
        s.connect(
            java.net.InetSocketAddress(ChatRemasteredConfig.serverHost, ChatRemasteredConfig.imagePort),
            timeout
        )
        s.soTimeout = timeout
        return s
    }

    // ── Ping ─────────────────────────────────────────────────────────────────

    fun ping(): Boolean {
        return try {
            connect(5000).use { socket ->
                val dout = DataOutputStream(socket.getOutputStream())
                val din  = DataInputStream(socket.getInputStream())
                dout.writeByte(CMD_PING.toInt())
                dout.flush()
                val cmd = din.readByte()
                if (cmd != CMD_PING) return false
                val len = din.readShort().toInt() and 0xFFFF
                val body = String(din.readNBytes(len), Charsets.UTF_8)
                body == "chatmedia-ok"
            }
        } catch (_: Exception) { false }
    }

    // ── Изображения ───────────────────────────────────────────────────────────

    fun upload(imageId: String, token: String, data: ByteArray): String {
        var lastError = "unknown"
        for (attempt in 1..UPLOAD_MAX_ATTEMPTS) {
            if (attempt > 1) {
                val delayMs = UPLOAD_BASE_DELAY_MS * (1L shl (attempt - 2))
                println("[Chat Remastered] Upload attempt $attempt/$UPLOAD_MAX_ATTEMPTS for $imageId (retry in ${delayMs}ms)")
                Thread.sleep(delayMs)
            }
            val result = uploadOnce(imageId, token, data)
            when (result) {
                "ok"        -> return "ok"
                "forbidden" -> return "forbidden"
                "too_large" -> return "too_large"
                else        -> { lastError = result; println("[Chat Remastered] Upload error (attempt $attempt): $result") }
            }
        }
        println("[Chat Remastered] Upload failed after $UPLOAD_MAX_ATTEMPTS attempts for $imageId: $lastError")
        return lastError
    }

    private fun uploadOnce(imageId: String, token: String, data: ByteArray): String {
        return try {
            connect(10_000).use { socket ->
                socket.soTimeout = 30_000
                val dout = DataOutputStream(socket.getOutputStream())
                val din  = DataInputStream(socket.getInputStream())

                val tokenBytes = token.toByteArray(Charsets.UTF_8)
                val idBytes    = imageId.toByteArray(Charsets.UTF_8)

                dout.writeByte(CMD_UPLOAD.toInt())
                dout.writeShort(tokenBytes.size)
                dout.write(tokenBytes)
                dout.writeByte(idBytes.size)
                dout.write(idBytes)
                dout.writeInt(data.size)
                dout.write(data)
                dout.flush()

                when (din.readByte()) {
                    0x00.toByte() -> "ok"
                    0x01.toByte() -> "forbidden"
                    0x02.toByte() -> "too_large"
                    else          -> "error"
                }
            }
        } catch (e: Exception) { println("[Chat Remastered] Upload exception: ${e}"); "exception: ${e.message}" }
    }

    /**
     * Скачивает полный файл с сервера, репортуя прогресс через ImageCache.
     * onProgress вызывается в фоновом потоке с [0..1].
     */
    fun getFull(imageId: String, onProgress: ((Float) -> Unit)? = null): ByteArray? {
        for (attempt in 1..DOWNLOAD_MAX_ATTEMPTS) {
            if (attempt > 1) {
                val delayMs = DOWNLOAD_BASE_DELAY_MS * (1L shl (attempt - 2))
                println("[Chat Remastered] Download attempt $attempt/$DOWNLOAD_MAX_ATTEMPTS for $imageId (retry in ${delayMs}ms)")
                Thread.sleep(delayMs)
            }
            val result = getFullOnce(imageId, onProgress)
            if (result != null) return result
            println("[Chat Remastered] Download miss (attempt $attempt): $imageId not ready yet")
        }
        println("[Chat Remastered] Download failed after $DOWNLOAD_MAX_ATTEMPTS attempts for $imageId")
        return null
    }

    private fun getFullOnce(imageId: String, onProgress: ((Float) -> Unit)? = null): ByteArray? {
        return try {
            connect(5000).use { socket ->
                socket.soTimeout = 60_000
                val dout = DataOutputStream(socket.getOutputStream())
                val din  = DataInputStream(socket.getInputStream())

                val idBytes = imageId.toByteArray(Charsets.UTF_8)
                dout.writeByte(CMD_GET_FULL.toInt())
                dout.writeByte(idBytes.size)
                dout.write(idBytes)
                dout.flush()

                if (din.readByte() != RES_OK) return null
                val len = din.readLong()

                // Сохраняем размер файла для UI
                if (len > 0) ImageCache.setFileSizeBytes(imageId, len)

                if (onProgress == null || len <= 0L) {
                    // Быстрый путь без прогресса
                    din.readNBytes(len.toInt())
                } else {
                    // Чтение по чанкам с прогрессом
                    val buf = ByteArray(len.toInt())
                    var received = 0
                    val chunk = 65536
                    while (received < len) {
                        val toRead = minOf(chunk, len.toInt() - received)
                        val n = din.read(buf, received, toRead)
                        if (n < 0) break
                        received += n
                        onProgress(received.toFloat() / len)
                    }
                    if (received < len) null else buf
                }
            }
        } catch (_: Exception) { null }
    }

    fun getThumb(imageId: String): ByteArray? {
        return try {
            connect(5000).use { socket ->
                socket.soTimeout = 10_000
                val dout = DataOutputStream(socket.getOutputStream())
                val din  = DataInputStream(socket.getInputStream())

                val idBytes = imageId.toByteArray(Charsets.UTF_8)
                dout.writeByte(CMD_GET_THUMB.toInt())
                dout.writeByte(idBytes.size)
                dout.write(idBytes)
                dout.flush()

                if (din.readByte() != RES_OK) return null
                null // сервер не шлёт данные — клиент генерирует превью сам
            }
        } catch (_: Exception) { null }
    }

    // ── Видео ─────────────────────────────────────────────────────────────────

    /**
     * Загружает видео на сервер чанками (не грузит в RAM).
     * @return "ok" | "forbidden" | "too_large" | "error" | "exception: ..."
     */
}
