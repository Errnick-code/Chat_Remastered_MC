package dev.errnicraft.chatremastered

import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ChatRemasteredStore {
    data class ImageMessage(
        val imageId: String,
        val sender: String,
        val caption: String,
        val addedTime: Int,
        var dismissed: Boolean = false,
        /** Цветной компонент ника (от сервера) — используется в reply-плашке вместо белого literal */
        val senderComponent: net.minecraft.network.chat.Component? = null
    ) {
        var boundsX0 = 0; var boundsY0 = 0
        var boundsX1 = 0; var boundsY1 = 0
        private var boundsSet = false

        fun setScreenBounds(x0: Int, y0: Int, x1: Int, y1: Int) {
            boundsX0 = x0; boundsY0 = y0
            boundsX1 = x1; boundsY1 = y1
            boundsSet = true
        }
        fun hasScreenBounds() = boundsSet
    }

    /**
     * Данные о reply-сообщении.
     * addedTime заполняется позже — когда GuiMessage с совпадающим текстом появляется в чате.
     * replyToImageId — непустой если ответ на фото.
     */
    data class ReplyMessage(
        val senderName: String,
        val text: String,
        val replyToSender: String,
        val replyToText: String,
        val replyToImageId: String,
        var addedTime: Int = -1,        // -1 = ещё не привязано к GuiMessage
        var replyToAddedTime: Int = -1, // addedTime оригинального сообщения (-1 = не найдено)
        var consumed: Boolean = false,  // true = \n-отступ уже добавлен, повторно не перехватываем
        var expectedAddedTime: Int = -1, // addedTime GuiMessage добавленного нами в addReplyToChat
        val createdAtMs: Long = System.currentTimeMillis(), // для TTL-очистки
        /** Цветной компонент ника отправителя (из ReplyChatPackет) */
        val senderComponent: net.minecraft.network.chat.Component? = null,
        /** Монотонный порядковый номер — для строгой FIFO-привязки, чтобы два reply
         *  с похожим текстом не "съедали" друг друга и не привязывались к одному addedTime. */
        val seq: Long = nextSeq()
    ) {
        companion object {
            private val seqCounter = java.util.concurrent.atomic.AtomicLong(0)
            fun nextSeq() = seqCounter.incrementAndGet()
        }
    }

    val messages = ArrayDeque<ImageMessage>(50)
    private const val MAX_MESSAGES = 50

    // Отправители чьё реальное chat-сообщение нужно подавить: senderName → (expectedText, timestamp)
    val suppressNextPhotoMessage = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    @JvmStatic
    fun markSuppressPhotoMessage(senderName: String, caption: String?) {
        val text = if (!caption.isNullOrEmpty()) caption else "[photo]"
        suppressNextPhotoMessage[senderName] = Pair(text, System.currentTimeMillis())
    }

    @JvmStatic
    fun shouldSuppressMessage(senderName: String, text: String): Boolean {
        val now = System.currentTimeMillis()
        suppressNextPhotoMessage.entries.removeIf { now - it.value.second > 5000 }
        val entry = suppressNextPhotoMessage[senderName] ?: return false
        val expected = entry.first
        // Точное совпадение или текст начинается с ожидаемого (на случай суффиксов сервера)
        val matches = text == expected || text.startsWith(expected)
        return if (matches) {
            suppressNextPhotoMessage.remove(senderName)
            true
        } else false
    }

    /**
     * Нечёткое подавление для нестандартных форматов чата (когда формат не "<Nick> text").
     * Проверяет: содержит ли raw-строка сообщения текст из любого активного pending suppress.
     * Используется как fallback если стандартный формат не распознан.
     */
    @JvmStatic
    fun shouldSuppressMessageFuzzy(raw: String): Boolean {
        val now = System.currentTimeMillis()
        suppressNextPhotoMessage.entries.removeIf { now - it.value.second > 5000 }
        val iter = suppressNextPhotoMessage.entries.iterator()
        while (iter.hasNext()) {
            val (senderName, pair) = iter.next()
            val expected = pair.first
            // Сообщение должно содержать и имя отправителя, и ожидаемый текст
            if (raw.contains(senderName) && (raw.contains(expected) || raw.endsWith(expected))) {
                iter.remove()
                return true
            }
        }
        return false
    }

    // Подавление ванильного дубля reply-сообщений: senderName → (expectedText, timestamp)
    // Заполняется из addReplyToChat ДО того как ваниль добавит своё сообщение.
    private val suppressNextReplyMessage = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    @JvmStatic
    fun markSuppressReplyMessage(senderName: String, text: String) {
        suppressNextReplyMessage[senderName] = Pair(text, System.currentTimeMillis())
    }

    @JvmStatic
    fun shouldSuppressReplyMessage(senderName: String, text: String): Boolean {
        val now = System.currentTimeMillis()
        suppressNextReplyMessage.entries.removeIf { now - it.value.second > 10_000L }
        val entry = suppressNextReplyMessage[senderName] ?: return false
        val expected = entry.first
        val matches = text == expected || text.startsWith(expected) || expected.startsWith(text)
        return if (matches) {
            suppressNextReplyMessage.remove(senderName)
            true
        } else false
    }

    // Список reply — хранится отдельно, привязывается к GuiMessage по тексту
    val replies = ArrayDeque<ReplyMessage>(200)
    private const val MAX_REPLIES = 200

    // Оригинальный файл для своих картинок — для сохранения без потерь
    private val originalFileMap = ConcurrentHashMap<String, File>()

    /**
     * imageId-ы для которых мы уже показали сообщение об ошибке загрузки через TCP
     * (ветка "else" в sendPendingImageWithCaption). Если сервер пришлёт ImageErrorPacket
     * для того же imageId — подавляем, чтобы не показывать дублирующее сообщение.
     */
    private val uploadErrorShown = ConcurrentHashMap.newKeySet<String>()

    fun markUploadErrorShown(imageId: String) { uploadErrorShown.add(imageId) }

    @JvmStatic
    fun shouldSuppressImageErrorPacket(imageId: String): Boolean = uploadErrorShown.contains(imageId)


    fun addMessage(imageId: String, sender: String, caption: String, addedTime: Int,
                   senderComponent: net.minecraft.network.chat.Component? = null) {
        messages.addLast(ImageMessage(imageId, sender, caption, addedTime, senderComponent = senderComponent))
        if (messages.size > MAX_MESSAGES) {
            val removed = messages.removeFirst()
            originalFileMap.remove(removed.imageId)
        }
    }

    /**
     * Добавляет reply-запись. addedTime пока -1 — будет проставлен в ChatComponentMixin
     * когда соответствующий GuiMessage появится в trimmedMessages.
     */
    fun addReply(senderName: String, text: String, replyToSender: String,
                 replyToText: String, replyToImageId: String,
                 senderComponent: net.minecraft.network.chat.Component? = null,
                 addedTime: Int = -1) {
        val reply = ReplyMessage(senderName, text, replyToSender, replyToText, replyToImageId,
            senderComponent = senderComponent)
        if (addedTime >= 0) {
            reply.addedTime = addedTime
            reply.expectedAddedTime = addedTime
        }
        replies.addLast(reply)
        if (replies.size > MAX_REPLIES) replies.removeFirst()
    }

    /**
     * Возвращает reply для сообщения с данным addedTime, или null.
     */
    @JvmStatic
    fun getReplyForAddedTime(addedTime: Int): ReplyMessage? =
        replies.lastOrNull { it.addedTime == addedTime }

    /**
     * Возвращает все reply у которых addedTime ещё не проставлен (-1) и NOT consumed
     * (ещё не получили 
-отступ в ChatSuppressMixin — используется только там).
     */
    @JvmStatic
    fun getPendingReplies(): List<ReplyMessage> = replies.filter { it.addedTime < 0 && !it.consumed }

    /**
     * Помечает pending reply как consumed (чтобы не перехватить addMessage дважды).
     * Возвращает true если нашёл и пометил.
     */
    /**
     * Очищает зависшие (не привязанные за 10 секунд) pending replies,
     * чтобы они не "прилипали" к будущим обычным сообщениям.
     */
    private fun pruneStaleReplies() {
        val now = System.currentTimeMillis()
        replies.removeAll { it.addedTime < 0 && now - it.createdAtMs > 10_000L }
    }

    @JvmStatic
    fun consumePendingReply(senderName: String, text: String): Boolean {
        pruneStaleReplies()
        val now = System.currentTimeMillis()
        // senderName и text — из ChatSuppressMixin: senderName=ник, text=тело сообщения (без "<Nick> ").
        // it.text — полный raw текст сохранённый при ПКМ: "<Nick> тело" или просто "тело".
        //
        // ВАЖНО: используем firstOrNull (наименьший seq) — FIFO-порядок.
        // lastOrNull приводил к тому что новый reply "съедал" старый при похожем тексте,
        // и старый reply потом цеплялся к следующему сообщению чата.
        val reply = replies
            .filter { !it.consumed && it.addedTime < 0 && it.senderName == senderName
                      && (now - it.createdAtMs) < 10_000L }
            .minByOrNull { it.seq }
            ?.takeIf { candidate ->
                if (text.isEmpty()) return@takeIf true
                // Извлекаем тело из сохранённого it.text (убираем "<Nick> " если есть)
                val itBody = if (candidate.text.startsWith("<") && candidate.text.contains("> "))
                    candidate.text.substring(candidate.text.indexOf("> ") + 2)
                else candidate.text
                // Строгое совпадение тела — избегаем ложных совпадений
                itBody == text || itBody.startsWith(text) || text.startsWith(itBody)
            }
        if (reply != null) { reply.consumed = true; return true }
        return false
    }

    /**
     * Возвращает все reply (для рендера).
     */
    @JvmStatic
    fun getRepliesList(): List<ReplyMessage> = replies.toList()

    /** Сохраняет ссылку на оригинальный файл (только для своих картинок) */
    fun storeOriginalFile(imageId: String, file: File) {
        originalFileMap[imageId] = file
    }

    @JvmStatic
    fun getOriginalFile(imageId: String): File? = originalFileMap[imageId]

    @JvmStatic
    fun dismiss(imageId: String) {
        messages.find { it.imageId == imageId }?.dismissed = true
    }

    @JvmStatic
    fun dismissMessage(imageId: String) = dismiss(imageId)

    @JvmStatic
    fun getMessageList(): List<ImageMessage> = messages.toList()

    fun clear() {
        messages.clear()
        replies.clear()
        originalFileMap.clear()
        uploadErrorShown.clear()
    }
}
