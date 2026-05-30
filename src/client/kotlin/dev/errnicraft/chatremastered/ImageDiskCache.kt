package dev.errnicraft.chatremastered

import net.minecraft.client.Minecraft
import java.io.File
import java.util.LinkedHashMap

/**
 * Единый кэш изображений.
 *
 * Структура диска:
 *   .minecraft/chat-remastered-cache/<imageId>   (без расширения — универсально)
 *
 * ОЗУ: последние MAX_RAM штук хранятся в LRU-карте (LinkedHashMap с accessOrder=true).
 * Всё что "выпало" из ОЗУ — только на диске.
 *
 * Команда /chat-remastered clearcache — удаляет с диска всё что НЕ в ОЗУ.
 * TTL: файлы старше DISK_TTL_MS помечаются невалидными при следующей попытке load().
 */
object ImageDiskCache {

    /** Количество файлов, которые держим в ОЗУ одновременно */
    const val MAX_RAM = 20

    /** TTL файлов на диске: 1 сутки */
    private const val DISK_TTL_MS = 24L * 60 * 60 * 1000

    /** LRU-кэш в ОЗУ: imageId -> bytes */
    private val ramCache: LinkedHashMap<String, ByteArray> = object :
        LinkedHashMap<String, ByteArray>(MAX_RAM + 4, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ByteArray>): Boolean =
            size > MAX_RAM
    }
    private val ramLock = Any()

    // ─── Путь ─────────────────────────────────────────────────────────────────

    fun cacheDir(): File {
        val dir = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("chat-remastered-cache").toFile()
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun fileFor(imageId: String): File = File(cacheDir(), imageId)

    fun getFile(imageId: String): File = fileFor(imageId)

    // ─── Публичный API ────────────────────────────────────────────────────────

    /**
     * Сохраняет байты:
     * - кладёт в ОЗУ (LRU, вытесняет старый если > MAX_RAM)
     * - пишет на диск если файла ещё нет
     */
    fun save(imageId: String, data: ByteArray) {
        synchronized(ramLock) {
            ramCache[imageId] = data
        }
        try {
            val f = fileFor(imageId)
            if (!f.exists()) f.writeBytes(data)
        } catch (_: Exception) {}
    }

    /**
     * Загружает байты:
     * 1. Сначала ОЗУ
     * 2. Потом диск (проверяет TTL; если протух — удаляет и возвращает null)
     */
    fun load(imageId: String): ByteArray? {
        // 1. ОЗУ
        synchronized(ramLock) {
            ramCache[imageId]?.let { return it }
        }
        // 2. Диск
        return try {
            val f = fileFor(imageId)
            if (!f.exists()) return null
            if (System.currentTimeMillis() - f.lastModified() > DISK_TTL_MS) {
                try { f.delete() } catch (_: Exception) {}
                return null
            }
            val bytes = f.readBytes()
            synchronized(ramLock) { ramCache[imageId] = bytes }
            bytes
        } catch (_: Exception) { null }
    }

    /**
     * Возвращает true если файл есть в ОЗУ или на диске (и не протух).
     * Не читает байты — быстрая проверка для "кнопки скачать".
     */
    fun exists(imageId: String): Boolean {
        synchronized(ramLock) { if (ramCache.containsKey(imageId)) return true }
        val f = fileFor(imageId)
        if (!f.exists()) return false
        if (System.currentTimeMillis() - f.lastModified() > DISK_TTL_MS) {
            try { f.delete() } catch (_: Exception) {}
            return false
        }
        return true
    }

    /** Возвращает список imageId которые сейчас в ОЗУ */
    fun ramIds(): Set<String> = synchronized(ramLock) { ramCache.keys.toSet() }

    /**
     * Удаляет с диска всё что НЕ в текущем ОЗУ.
     * Вызывается командой /chat-remastered clearcache.
     * @return количество удалённых файлов
     */
    fun clearDisk(): Int {
        val keep = ramIds()
        var deleted = 0
        try {
            cacheDir().listFiles()?.forEach { f ->
                if (f.name !in keep) {
                    try { if (f.delete()) deleted++ } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return deleted
    }

    /** Полная очистка ОЗУ + весь диск */
    fun clearAll() {
        synchronized(ramLock) { ramCache.clear() }
        try { cacheDir().listFiles()?.forEach { it.delete() } } catch (_: Exception) {}
    }

    /**
     * Удаляет конкретный imageId из ОЗУ и с диска.
     * Вызывается при получении ImageDeletedPacket, чтобы удалённое фото
     * нельзя было скачать из локального кэша.
     */
    fun delete(imageId: String) {
        synchronized(ramLock) { ramCache.remove(imageId) }
        try { fileFor(imageId).delete() } catch (_: Exception) {}
    }

    /** Статистика для команды */
    fun stats(): CacheStats {
        val ramCount: Int
        val ramBytes: Long
        synchronized(ramLock) {
            ramCount = ramCache.size
            ramBytes = ramCache.values.sumOf { it.size.toLong() }
        }
        var diskCount = 0
        var diskBytes = 0L
        try {
            cacheDir().listFiles()?.forEach { f ->
                diskCount++
                diskBytes += f.length()
            }
        } catch (_: Exception) {}
        return CacheStats(ramCount, ramBytes, diskCount, diskBytes)
    }

    data class CacheStats(
        val ramCount: Int,
        val ramBytes: Long,
        val diskCount: Int,
        val diskBytes: Long
    ) {
        fun ramMb()  = "%.1f".format(ramBytes  / 1_048_576.0)
        fun diskMb() = "%.1f".format(diskBytes / 1_048_576.0)
    }
}
