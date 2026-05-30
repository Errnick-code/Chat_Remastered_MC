package dev.errnicraft.chatremastered

import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * TCP-сервер для приёма и раздачи фото.
 * Протокол (клиент → сервер):
 *   0x01  PING  → сервер отвечает 0x01 + u16(len) + "chatmedia-ok"
 *   0x02  UPLOAD  token(u16+bytes) id(u8+bytes) data(u32+bytes) → 0x00 ok | 0x01 forbidden | 0x02 too_large
 *   0x03  GET_FULL  id(u8+bytes) → 0x00 + i64(len) + bytes | 0x01 not_found
 *   0x04  GET_THUMB → 0x00 (данные не шлём — клиент генерирует сам) | 0x01
 */
object ImageHttpServer {

    private const val CMD_PING      = 0x01.toByte()
    private const val CMD_UPLOAD    = 0x02.toByte()
    private const val CMD_GET_FULL  = 0x03.toByte()
    private const val CMD_GET_THUMB = 0x04.toByte()

    private const val RES_OK        = 0x00.toByte()
    private const val RES_NOT_FOUND = 0x01.toByte()
    private const val RES_FORBIDDEN = 0x01.toByte()
    private const val RES_TOO_LARGE = 0x02.toByte()

    /** Токены, выданные игрокам (upload разрешён) */
    private val validTokens = ConcurrentHashMap.newKeySet<String>()

    /** Хранилище: imageId → байты */
    private val cache = ConcurrentHashMap<String, ByteArray>()

    /** Директория для дискового кэша (инициализируется при старте сервера) */
    private var cacheDir: File? = null

    /** Максимальный размер загружаемого файла в байтах */
    var maxUploadBytes: Long = 8L * 1024 * 1024

    /** Callback, вызывается когда фото успешно принято (в потоке обработки) */
    var onImageReady: ((imageId: String) -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "ChatRemastered-TCP").also { it.isDaemon = true }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun startIfNeeded(port: Int) {
        if (serverSocket?.isClosed == false) return
        try {
            val ss = ServerSocket(port)
            serverSocket = ss
            println("[Chat Remastered] TCP server started on port $port")
            executor.submit {
                while (!ss.isClosed) {
                    try {
                        val client = ss.accept()
                        executor.submit { handleClient(client) }
                    } catch (_: SocketException) {
                        break
                    } catch (e: Exception) {
                        println("[Chat Remastered] TCP accept error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("[Chat Remastered] Failed to start TCP server on port $port: ${e.message}")
        }
    }

    fun stop() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        println("[Chat Remastered] TCP server stopped")
    }

    fun initCacheDir(serverDir: File) {
        val dir = File(serverDir, "chat-remastered-images")
        dir.mkdirs()
        cacheDir = dir
        // Загружаем уже существующие файлы в RAM кэш
        dir.listFiles()?.forEach { f ->
            if (f.isFile && !cache.containsKey(f.name)) {
                try { cache[f.name] = f.readBytes() } catch (_: Exception) {}
            }
        }
    }

    // ── Token management ──────────────────────────────────────────────────────

    fun addToken(token: String) { validTokens.add(token) }
    fun removeToken(token: String) { validTokens.remove(token) }

    // ── Cache management ──────────────────────────────────────────────────────

    fun hasCached(imageId: String): Boolean = cache.containsKey(imageId)

    fun deleteImage(imageId: String) {
        cache.remove(imageId)
        cacheDir?.let { dir ->
            try { File(dir, imageId).delete() } catch (_: Exception) {}
        }
    }

    /**
     * Удаляет старые записи, оставляя только [keepCount] последних.
     */
    fun evictOld(keepCount: Int) {
        if (cache.size <= keepCount) return
        val keys = cache.keys().toList()
        val toRemove = keys.dropLast(keepCount)
        toRemove.forEach { deleteImage(it) }
    }

    // ── Client handler ────────────────────────────────────────────────────────

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 60_000
            val din  = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val dout = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            when (din.readByte()) {
                CMD_PING      -> handlePing(dout)
                CMD_UPLOAD    -> handleUpload(din, dout)
                CMD_GET_FULL  -> handleGetFull(din, dout)
                CMD_GET_THUMB -> handleGetThumb(din, dout)
                else          -> {}
            }
            dout.flush()
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handlePing(dout: DataOutputStream) {
        val body = "chatmedia-ok".toByteArray(Charsets.UTF_8)
        dout.writeByte(CMD_PING.toInt())
        dout.writeShort(body.size)
        dout.write(body)
    }

    private fun handleUpload(din: DataInputStream, dout: DataOutputStream) {
        // token: u16 + bytes
        val tokenLen = din.readShort().toInt() and 0xFFFF
        val token = String(din.readNBytes(tokenLen), Charsets.UTF_8)

        // imageId: u8 + bytes
        val idLen = din.readByte().toInt() and 0xFF
        val imageId = String(din.readNBytes(idLen), Charsets.UTF_8)

        // data: u32 + bytes
        val dataLen = din.readInt()

        if (!validTokens.contains(token)) {
            // Читаем и выбрасываем данные чтобы не сломать поток
            din.skipNBytes(dataLen.toLong())
            dout.writeByte(RES_FORBIDDEN.toInt())
            return
        }

        if (dataLen.toLong() > maxUploadBytes) {
            din.skipNBytes(dataLen.toLong())
            dout.writeByte(RES_TOO_LARGE.toInt())
            return
        }

        val data = din.readNBytes(dataLen)
        cache[imageId] = data

        // Сохраняем на диск
        cacheDir?.let { dir ->
            try { File(dir, imageId).writeBytes(data) } catch (_: Exception) {}
        }

        dout.writeByte(RES_OK.toInt())
        dout.flush()

        // Уведомляем сервер
        try { onImageReady?.invoke(imageId) } catch (_: Exception) {}
    }

    private fun handleGetFull(din: DataInputStream, dout: DataOutputStream) {
        val idLen = din.readByte().toInt() and 0xFF
        val imageId = String(din.readNBytes(idLen), Charsets.UTF_8)

        val data = cache[imageId] ?: run {
            // Пробуем с диска
            cacheDir?.let { dir ->
                val f = File(dir, imageId)
                if (f.exists()) {
                    val bytes = f.readBytes()
                    cache[imageId] = bytes
                    bytes
                } else null
            }
        }

        if (data == null) {
            dout.writeByte(RES_NOT_FOUND.toInt())
            return
        }

        dout.writeByte(RES_OK.toInt())
        dout.writeLong(data.size.toLong())
        dout.write(data)
    }

    private fun handleGetThumb(din: DataInputStream, dout: DataOutputStream) {
        val idLen = din.readByte().toInt() and 0xFF
        val imageId = String(din.readNBytes(idLen), Charsets.UTF_8)
        // Клиент генерирует превью сам — просто подтверждаем что файл есть
        val exists = cache.containsKey(imageId) ||
            cacheDir?.let { File(it, imageId).exists() } == true
        dout.writeByte(if (exists) RES_OK.toInt() else RES_NOT_FOUND.toInt())
    }
}
