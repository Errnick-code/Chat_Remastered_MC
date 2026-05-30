package dev.errnicraft.chatremastered

import net.minecraft.resources.Identifier
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object PendingImageState {

    data class PendingImage(
        val file: File,
        /** null пока текстура ещё грузится (показываем заглушку) */
        val textureId: Identifier?,
        val width: Int,
        val height: Int,
        val textureWidth: Int,
        val textureHeight: Int,
        val previewBytes: ByteArray,
        val rawBytes: ByteArray,
        /** true = это видео-файл */
        /**
         * true = rawBytes уже готовы (подогнаны под лимиты сервера) и можно отправлять.
         * Текстура превью при этом ещё может грузиться — это нормально.
         */
        val rawReady: Boolean = false,
        /** Реальный размер оригинального файла (из заголовка) — для ImageChatPacket */
        val origWidth: Int = 0,
        val origHeight: Int = 0,
        /**
         * true = реальный размер файла уже известен (width/height обновлены из заголовка).
         * false = показываем временный 16:9 плейсхолдер, размер ещё определяется.
         */
        val sizeKnown: Boolean = false
    ) {
        /** Текстура готова для рендера карточки */
        fun isLoaded() = textureId != null
        /** Байты готовы — можно отправлять не дожидаясь текстуры */
        fun canSend() = rawReady && rawBytes.isNotEmpty()
    }

    @Volatile private var _pending: PendingImage? = null

    /**
     * Прогресс загрузки: 0.0 = начало, 1.0 = готово.
     * -1 = нет активной загрузки / не применимо.
     */
    @JvmField
    @Volatile var uploadProgress: Float = -1f

    /**
     * Токен отмены текущей фоновой обработки.
     * Когда пользователь жмёт ✕ — мы flipим cancelled=true,
     * и фоновый поток проверяет его перед каждым тяжёлым шагом.
     */
    private val _cancelToken = AtomicReference(AtomicBoolean(false))

    /** Возвращает свежий cancel-токен для новой загрузки. Предыдущий автоматически отменяется. */
    @JvmStatic
    fun newCancelToken(): AtomicBoolean {
        val token = AtomicBoolean(false)
        _cancelToken.getAndSet(token).set(true) // отменяем предыдущий
        return token
    }

    /** Проверяет: был ли токен отменён (т.е. появился новый pending или нажат ✕) */
    @JvmStatic
    fun isCancelled(token: AtomicBoolean): Boolean = token.get()

    @JvmStatic
    fun getPending(): PendingImage? = _pending

    @JvmStatic
    fun setPending(img: PendingImage?) {
        val old = _pending
        if (old != null && old.textureId != null && (img == null || img.textureId != old.textureId)) {
            try {
                net.minecraft.client.Minecraft.getInstance().textureManager.release(old.textureId)
            } catch (_: Exception) {}
        }
        if (img == null) uploadProgress = -1f
        _pending = img
    }

    @JvmStatic
    fun clear() {
        _cancelToken.get().set(true) // отменяем текущую фоновую обработку
        val img = _pending
        _pending = null
        uploadProgress = -1f
        if (img?.textureId != null) {
            try {
                net.minecraft.client.Minecraft.getInstance().textureManager.release(img.textureId)
            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    fun updateTexture(
        textureId: Identifier,
        textureWidth: Int,
        textureHeight: Int,
        previewBytes: ByteArray,
        rawBytes: ByteArray
    ) {
        val cur = _pending ?: return
        _pending = cur.copy(
            textureId     = textureId,
            textureWidth  = textureWidth,
            textureHeight = textureHeight,
            previewBytes  = previewBytes,
            rawBytes      = if (rawBytes.isNotEmpty()) rawBytes else cur.rawBytes,
            // rawReady, origWidth, origHeight — не трогаем, они уже выставлены
            rawReady      = cur.rawReady || rawBytes.isNotEmpty()
        )
    }

    /** Обновляет прогресс (0..1). Вызывается из фонового потока. -1 = неопределённое ожидание. */
    @JvmStatic
    fun setProgress(value: Float) {
        uploadProgress = if (value < 0f) -1f else value.coerceIn(0f, 1f)
    }
}
