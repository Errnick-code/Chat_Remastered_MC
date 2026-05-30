package dev.errnicraft.chatremastered

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.commands.Commands
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import dev.errnicraft.chatremastered.ChatTimeHolder
import kotlin.math.ceil
import kotlin.math.roundToInt

object ChatRemasteredConfig {
    @Volatile var resolution: String = "720"
    @Volatile var serverHost: String = ""
    @Volatile var imagePort: Int = 5050
    @Volatile var uploadToken: String = ""
    @Volatile var serverHasModVersion: String? = null
    @Volatile var serverReachable: Boolean = false

    /** Если сервер включил autoDownload — клиент автоматически качает полный файл фоном */
    @Volatile var autoDownload: Boolean = false
        @JvmName("getAutoDownload") get

    /** Игрок забанен на этом сервере (получено PhotoDeniedPacket reason=banned) */
    @Volatile var isBanned: Boolean = false
        @JvmName("getBanned") get

    /** Игрок замучен на этом сервере (получено PhotoDeniedPacket reason=muted) */
    @Volatile var isMuted: Boolean = false
        @JvmName("getMuted") get

    /** Cooldown в секундах между отправками фото (из конфига, default 5) */
    @Volatile var cooldownSeconds: Int = 5

    /** Время (System.currentTimeMillis) окончания кулдауна, 0 = не активен */
    @Volatile var cooldownUntilMs: Long = 0L

    /** Возвращает оставшиеся миллисекунды кулдауна (0 если не активен) */
    @JvmName("cooldownRemainingMs")
    fun cooldownRemainingMs(): Long = (cooldownUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)

    /** Активирует кулдаун на cooldownSeconds секунд */
    fun startCooldown() {
        cooldownUntilMs = System.currentTimeMillis() + cooldownSeconds * 1000L
    }

    /** Масштаб превью фото в чате: 1.0 = базовый, 0.5–2.0 */
    @Volatile var previewScale: Float = 1.0f
        set(value) { field = value.coerceIn(0.5f, 2.0f) }

    /** Масштаб карточки над полем ввода (pending image): 1.0 = базовый, 0.5–2.0 */
    @Volatile var inputPreviewScale: Float = 1.0f
        set(value) { field = value.coerceIn(0.5f, 2.0f) }

    /** Высота открытого чата: false = ванильная (~половина экрана), true = на весь экран */
    @Volatile var fullscreenChat: Boolean = false
        @JvmName("getFullscreenChat") get

    /** Кол-во строк закрытого (затухающего) чата: 8–20, default 10 (как ваниль) */
    @Volatile var closedChatLines: Int = 10
        @JvmName("getClosedChatLines") get

    /**
     * Максимальная ширина/высота GIF в пикселях при загрузке.
     * Настраивается командой /chat-remastered gifres <240..1920>.
     * По умолчанию 480 — экономит память (GIF кадры тяжёлые).
     */
    @Volatile var gifMaxDim: Int = 480
        set(value) { field = value.coerceIn(240, 1920) }

    /** Максимальное разрешение GIF при ОТПРАВКЕ — диктует сервер через ServerConfigPacket.
     *  Клиент не может его превысить. gifMaxDim — только для локального отображения. */
    @Volatile var gifMaxDimServer: Int = 480
        set(value) { field = value.coerceIn(240, 1920) }

    /** Сервер разрешает отправку GIF (из ServerConfigPacket). Только читаем, не сохраняем. */
    @Volatile var gifEnabled: Boolean = true

    private val configFile: File get() {
        val mc = try { Minecraft.getInstance() } catch (_: Exception) { null }
        val gameDir = mc?.gameDirectory ?: File(".")
        return File(gameDir, "config/chat-remastered.json")
    }

    fun saveConfig() {
        try {
            val f = configFile
            f.parentFile?.mkdirs()
            val scale = previewScale.coerceIn(0.5f, 2.0f)
            val inputScale = inputPreviewScale.coerceIn(0.5f, 2.0f)
            val json = com.google.gson.JsonObject().apply {
                addProperty("configVersion", CURRENT_CONFIG_VERSION)
                addProperty("previewScale", scale)
                addProperty("inputPreviewScale", inputScale)
                addProperty("gifMaxDim", gifMaxDim.coerceIn(240, 1920))
                addProperty("fullscreenChat", fullscreenChat)
                addProperty("closedChatLines", closedChatLines.coerceIn(8, 20))
            }
            f.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json))
        } catch (e: Exception) {
            println("[Chat Remastered] Config save error: ${e.message}")
        }
    }

    fun loadConfig() {
        try {
            val f = configFile
            if (!f.exists()) return
            val text = f.readText()
            val json = try {
                com.google.gson.Gson().fromJson(text, com.google.gson.JsonObject::class.java)
            } catch (_: Exception) { null } ?: return

            previewScale = json.get("previewScale")?.asFloat?.coerceIn(0.5f, 2.0f) ?: 1.0f
            inputPreviewScale = json.get("inputPreviewScale")?.asFloat?.coerceIn(0.5f, 2.0f) ?: 1.0f
            gifMaxDim = json.get("gifMaxDim")?.asInt?.coerceIn(240, 1920) ?: 480
            fullscreenChat = json.get("fullscreenChat")?.asBoolean ?: false
            closedChatLines = json.get("closedChatLines")?.asInt?.coerceIn(8, 20) ?: 10
        } catch (e: Exception) {
            println("[Chat Remastered] Config load error: ${e.message}")
        }
    }

    val previewMaxW: Int get() {
        val base = when (resolution) {
            "360" -> 84
            "480" -> 112
            "HD"  -> 187
            else  -> 140
        }
        return (base * previewScale).roundToInt().coerceAtLeast(16)
    }
    val previewMaxH: Int get() {
        val base = when (resolution) {
            "360" -> 32
            "480" -> 42
            "HD"  -> 70
            else  -> 52
        }
        return (base * previewScale).roundToInt().coerceAtLeast(8)
    }

    /** Размер карточки pending image над полем ввода */
    val inputPreviewMaxW: Int get() {
        val base = when (resolution) {
            "360" -> 84
            "480" -> 112
            "HD"  -> 187
            else  -> 140
        }
        return (base * inputPreviewScale).roundToInt().coerceAtLeast(16)
    }
    val inputPreviewMaxH: Int get() {
        val base = when (resolution) {
            "360" -> 32
            "480" -> 42
            "HD"  -> 70
            else  -> 52
        }
        return (base * inputPreviewScale).roundToInt().coerceAtLeast(8)
    }

    val maxDim: Int get() = when (resolution) {
        "360" -> 480
        "480" -> 640
        "HD"  -> 1920
        "2K"  -> 2560
        else  -> 1280
    }

    fun reset() {
        resolution = "720"
        serverHost = ""
        imagePort = 5050
        uploadToken = ""
        serverHasModVersion = null
        serverReachable = false
        autoDownload = false
        isBanned = false
        isMuted = false
        gifEnabled = true
        gifMaxDimServer = 480
        cooldownSeconds = 5
        cooldownUntilMs = 0L
        // previewScale, inputPreviewScale, gifMaxDim НЕ сбрасываем — это локальные настройки клиента
    }

    /** Возвращает переведённую строку через систему локализации Minecraft */
    fun tr(key: String, vararg args: Any): String {
        return try {
            Component.translatable(key, *args).string
        } catch (_: Exception) {
            key
        }
    }

    private const val CURRENT_CONFIG_VERSION = 1
}

class ChatRemasteredClient : ClientModInitializer {

    override fun onInitializeClient() {
        ChatRemasteredConfig.loadConfig()
        DragDropHandler.register()

        ClientPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val mc = Minecraft.getInstance()
            ChatRemasteredConfig.serverHost = if (mc.isLocalServer) {
                "127.0.0.1"
            } else {
                val addr = handler.connection.remoteAddress
                when (addr) {
                    is java.net.InetSocketAddress -> {
                        val ip = addr.address?.hostAddress ?: addr.hostString
                        ip.ifBlank { "127.0.0.1" }
                    }
                    else -> {
                        val raw = addr?.toString() ?: ""
                        raw.substringAfterLast("/").substringBefore(":").trim().ifBlank { "127.0.0.1" }
                    }
                }
            }
            println("[Chat Remastered] Server host resolved: ${ChatRemasteredConfig.serverHost}")
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ImageCache.clear()
            ChatRemasteredStore.clear()
            ChatRemasteredConfig.reset()
        }

        ClientPlayNetworking.registerGlobalReceiver(ServerHelloPacket.TYPE) { payload, context ->
            context.client().execute {
                val serverVersion = payload.serverProtocolVersion
                ChatRemasteredConfig.serverHasModVersion = serverVersion
                ClientPlayNetworking.send(ClientHelloPacket(MOD_PROTOCOL_VERSION))
                if (serverVersion != MOD_PROTOCOL_VERSION) {
                    val chat = Minecraft.getInstance().gui.getChat()
                    // Сравниваем semver: если сервер новее — предупреждаем об обновлении
                    val serverNewer = compareModVersions(serverVersion, MOD_PROTOCOL_VERSION) > 0
                    if (serverNewer) {
                        chat.addMessage(Component.literal(
                            "§8[Chat Remastered] §e⚠ На сервере установлена более новая версия мода (v$serverVersion), " +
                            "у вас v${MOD_VERSION}. Рекомендуется обновить мод."
                        ))
                    } else {
                        chat.addMessage(Component.literal("§8[Chat Remastered] §e" + ChatRemasteredConfig.tr(
                            "chat-remastered.version_warn", serverVersion, MOD_VERSION
                        )))
                    }
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPacket.TYPE) { payload, context ->
            context.client().execute {
                val res = payload.resolution
                if (res == "360" || res == "480" || res == "720" || res == "HD" || res == "2K") ChatRemasteredConfig.resolution = res
                ChatRemasteredConfig.imagePort = payload.imagePort
                ChatRemasteredConfig.uploadToken = payload.uploadToken
                ChatRemasteredConfig.autoDownload = payload.autoDownload
                ChatRemasteredConfig.cooldownSeconds = payload.photoCooldownSeconds.coerceAtLeast(0)
                ChatRemasteredConfig.gifEnabled = payload.gifEnabled
                // gifMaxDim от сервера — ограничение при отправке, перезаписывает локальное значение
                ChatRemasteredConfig.gifMaxDimServer = payload.gifMaxDim
                Thread { pingTcpServer(context.client()) }.also { it.isDaemon = true }.start()
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(HandshakeErrorPacket.TYPE) { payload, context ->
            context.client().execute {
                Minecraft.getInstance().gui.getChat().addMessage(
                    Component.literal("§8[§bChat Remastered§8] §c❌ ${payload.reason}")
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ImageDeletedPacket.TYPE) { payload, context ->
            context.client().execute {
                ImageCache.markDeleted(payload.imageId)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ImageErrorPacket.TYPE) { payload, context ->
            context.client().execute {
                val imageId = payload.imageId
                val reason = payload.reason
                // Если у нас есть локальные данные (мы отправители) — не помечаем ошибкой.
                // Фото видно у нас из fullDataMap. Сервер просто не получил файл вовремя,
                // но это не значит что фото исчезло — оно ещё может дойти или уже у нас.
                val hasLocalData = ImageCache.getFullData(imageId) != null ||
                                   dev.errnicraft.chatremastered.ImageDiskCache.exists(imageId)
                if (!hasLocalData) {
                    ImageCache.markError(imageId)
                }
                // Если upload уже завершился с ошибкой и мы уже показали сообщение —
                // подавляем дублирующий ImageErrorPacket от сервера.
                if (hasLocalData && ChatRemasteredStore.shouldSuppressImageErrorPacket(imageId)) return@execute
                val msg = when (reason) {
                    "timeout" -> if (hasLocalData)
                        "§eФото отправлено локально, но сервер не подтвердил получение файла. Другие игроки могут не увидеть его."
                    else
                        "§cФото не удалось загрузить: сервер не получил файл вовремя."
                    "decode_error" -> "§cФото не удалось обработать: ошибка декодирования."
                    else -> if (hasLocalData) "§eОшибка связи с сервером при загрузке фото." else "§cФото не удалось загрузить."
                }
                Minecraft.getInstance().gui.getChat().addMessage(
                    Component.literal("§8[Chat Remastered] $msg")
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(PhotoUnbannedPacket.TYPE) { payload, context ->
            context.client().execute {
                ChatRemasteredConfig.isBanned = false
                ChatRemasteredConfig.isMuted = false
                ChatRemasteredConfig.uploadToken = payload.newUploadToken
                Minecraft.getInstance().gui.getChat().addMessage(
                    Component.literal("§8[Chat Remastered] §a✔ " + ChatRemasteredConfig.tr("chat-remastered.unmuted"))
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(PhotoDeniedPacket.TYPE) { payload, context ->
            context.client().execute {
                when (payload.reason) {
                    "banned" -> {
                        ChatRemasteredConfig.isBanned = true
                        Minecraft.getInstance().gui.getChat().addMessage(
                            Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.banned"))
                        )
                    }
                    "muted" -> {
                        ChatRemasteredConfig.isMuted = true
                        Minecraft.getInstance().gui.getChat().addMessage(
                            Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.muted"))
                        )
                    }
                    // "muted_silent" — мут через BanHammer: только ставим флаг,
                    // текстовое уведомление уже показал BanHammer, дублировать не нужно.
                    "muted_silent" -> {
                        ChatRemasteredConfig.isMuted = true
                    }
                    // других reason не показываем в чате — сервер уже отправил системное сообщение
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ImageChatPacket.TYPE) { payload, context ->
            // Помечаем подавление НЕМЕДЛЕННО (до execute) — ванильное сообщение может
            // прийти в том же тике раньше чем выполнится execute-блок
            val alreadyShown = ChatRemasteredStore.messages.any { it.imageId == payload.imageId }
            if (!alreadyShown) {
                ChatRemasteredStore.markSuppressPhotoMessage(payload.sender, payload.caption)
            }
            context.client().execute {
                val alreadyShown2 = ChatRemasteredStore.messages.any { it.imageId == payload.imageId }
                if (!alreadyShown2) {
                    addImageToChat(context.client(), payload.imageId, payload.sender, payload.caption, payload.width, payload.height, payload.senderComponent)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ReplyChatPacket.TYPE) { payload, context ->
            context.client().execute {
                addReplyToChat(
                    mc              = context.client(),
                    sender          = payload.sender,
                    text            = payload.text,
                    senderComponent = payload.senderComponent,
                    replyToSender   = payload.replyToSender,
                    replyToText     = payload.replyToText,
                    replyToImageId  = payload.replyToImageId
                )
            }
        }

        // ── Клиентские команды для дебага ────────────────────────────────────
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->

            // Пресеты aspect ratio: имя команды → (rw, rh)
            val presets = linkedMapOf(
                "1_1"   to Pair(1, 1),
                "4_3"   to Pair(4, 3),
                "3_2"   to Pair(3, 2),
                "16_9"  to Pair(16, 9),
                "16_10" to Pair(16, 10),
                "21_9"  to Pair(21, 9),
                "9_16"  to Pair(9, 16),
                "3_4"   to Pair(3, 4),
                "2_3"   to Pair(2, 3)
            )
            // Читаемые метки для чата (с двоеточием)
            val presetLabels = mapOf(
                "1_1" to "1:1", "4_3" to "4:3", "3_2" to "3:2",
                "16_9" to "16:9", "16_10" to "16:10", "21_9" to "21:9",
                "9_16" to "9:16", "3_4" to "3:4", "2_3" to "2:3"
            )

            // Строим узел placeholder — без аргументов запускает 16:9 по умолчанию
            var placeholderNode = literal("placeholder")
                .executes { _ ->
                    val mc = Minecraft.getInstance()
                    mc.execute { debugShowPlaceholder(mc, 16.0 / 9.0, "16:9") }
                    1
                }

            // Подкоманда для каждого пресета
            for ((cmdName, ratio) in presets) {
                val (rw, rh) = ratio
                val label = presetLabels[cmdName] ?: cmdName
                placeholderNode = placeholderNode.then(
                    literal(cmdName).executes { _ ->
                        val mc = Minecraft.getInstance()
                        mc.execute { debugShowPlaceholder(mc, rw.toDouble() / rh.toDouble(), label) }
                        1
                    }
                )
            }

            // Подкоманда custom <width> <height>
            placeholderNode = placeholderNode.then(
                literal("custom").then(
                    argument("width", IntegerArgumentType.integer(1, 7680)).then(
                        argument("height", IntegerArgumentType.integer(1, 4320))
                            .executes { ctx ->
                                val w = IntegerArgumentType.getInteger(ctx, "width")
                                val h = IntegerArgumentType.getInteger(ctx, "height")
                                val mc = Minecraft.getInstance()
                                mc.execute { debugShowPlaceholder(mc, w.toDouble() / h.toDouble(), "${w}x${h}") }
                                1
                            }
                    )
                )
            )

            val testNode = literal("test")
                .executes { _ ->
                    val mc = Minecraft.getInstance()
                    mc.execute { debugTestConnection(mc) }
                    1
                }

            // placeholder_deleted — показывает плейсхолдер удалённого фото (красный ✗)
            var placeholderDeletedNode = literal("placeholder_deleted")
                .executes { _ ->
                    val mc = Minecraft.getInstance()
                    mc.execute { debugShowPlaceholderState(mc, "deleted") }
                    1
                }
            for ((cmdName, ratio) in presets) {
                val (rw, rh) = ratio
                val label = presetLabels[cmdName] ?: cmdName
                placeholderDeletedNode = placeholderDeletedNode.then(
                    literal(cmdName).executes { _ ->
                        val mc = Minecraft.getInstance()
                        mc.execute { debugShowPlaceholderState(mc, "deleted", rw.toDouble() / rh.toDouble(), label) }
                        1
                    }
                )
            }

            // placeholder_error — показывает плейсхолдер ошибки загрузки (жёлтый !)
            var placeholderErrorNode = literal("placeholder_error")
                .executes { _ ->
                    val mc = Minecraft.getInstance()
                    mc.execute { debugShowPlaceholderState(mc, "error") }
                    1
                }
            for ((cmdName, ratio) in presets) {
                val (rw, rh) = ratio
                val label = presetLabels[cmdName] ?: cmdName
                placeholderErrorNode = placeholderErrorNode.then(
                    literal(cmdName).executes { _ ->
                        val mc = Minecraft.getInstance()
                        mc.execute { debugShowPlaceholderState(mc, "error", rw.toDouble() / rh.toDouble(), label) }
                        1
                    }
                )
            }

            // ── /chat-remastered gifres <120..1920> — настройка разрешения GIF ──────
            val gifresNode = literal("gifres")
                .executes { _ ->
                    val mc = Minecraft.getInstance()
                    mc.execute {
                        mc.gui.getChat().addMessage(Component.literal(
                            "§8[Chat Remastered] §7GIF разрешение: §f${ChatRemasteredConfig.gifMaxDim}§7px. " +
                            "Используй §f/chat-remastered gifres <240..1920>§7 для изменения."
                        ))
                    }
                    1
                }
                .then(
                    argument("maxDim", IntegerArgumentType.integer(240, 1920))
                        .executes { ctx ->
                            val dim = IntegerArgumentType.getInteger(ctx, "maxDim")
                            ChatRemasteredConfig.gifMaxDim = dim
                            ChatRemasteredConfig.saveConfig()
                            val mc = Minecraft.getInstance()
                            mc.execute {
                                mc.gui.getChat().addMessage(Component.literal(
                                    "§8[Chat Remastered] §7GIF разрешение установлено: §f${dim}px §7(вступит в силу при следующей загрузке GIF)"
                                ))
                            }
                            1
                        }
                )

            // ── /chat-remastered clearcache — удаляет кэш с диска (кроме ОЗУ) ──────
            val clearcacheNode = literal("clearcache")
                .executes { _ ->
                    val mc = Minecraft.getInstance()
                    Thread {
                        val deleted = ImageDiskCache.clearDisk()
                        val stats = ImageDiskCache.stats()
                        mc.execute {
                            mc.gui.getChat().addMessage(Component.literal(
                                "§8[Chat Remastered] §7Кэш очищен: удалено §f${deleted}§7 файлов. " +
                                "В ОЗУ: §f${stats.ramCount}§7 (${stats.ramMb()} МБ). " +
                                "На диске: §f${stats.diskCount}§7 (${stats.diskMb()} МБ)."
                            ))
                        }
                    }.also { it.isDaemon = true }.start()
                    1
                }

            // ── /chat-remastered delete <imageId> — пересылаем на сервер ──────────
            // Клиентский диспетчер перехватывает /chat-remastered раньше сервера.
            // Отправляем ServerboundChatCommandPacket напрямую — минуя клиентский диспетчер
            // (sendCommand() рекурсивно проходит через него и вызывает бесконечный цикл).
            val deleteNode = literal("delete")
                .then(
                    argument("imageId", StringArgumentType.word())
                        .executes { ctx ->
                            val imageId = StringArgumentType.getString(ctx, "imageId")
                            val mc = Minecraft.getInstance()
                            val conn = mc.connection ?: return@executes 0
                            // Отправляем пакет команды напрямую на сервер
                            conn.send(
                                net.minecraft.network.protocol.game.ServerboundChatCommandPacket(
                                    "chat-remastered delete $imageId"
                                )
                            )
                            1
                        }
                )

            dispatcher.register(
                literal("chat-remastered")
                    .then(gifresNode)
                    .then(clearcacheNode)
                    .then(deleteNode)
            )

            dispatcher.register(
                literal("chatremastereddebug")
                    .then(placeholderNode)
                    .then(placeholderDeletedNode)
                    .then(placeholderErrorNode)
                    .then(testNode)
            )
        }
    }

    companion object {
        private const val MAX_FULL_BYTES = 8 * 1024 * 1024

        /**
         * Рекурсивно копирует компонент, выбрасывая ObjectContents-узлы (PlayerSprite и др.).
         * Стиль и siblings сохраняются. PlainTextContents и TranslatableContents остаются как есть.
         * Нужно чтобы comp.string не давал мусор вида "[Errnick_ Head]" от Chat Heads.
         */
        /** Удаляет паттерны вида "[Player head]", "[ head]", "[unknown player head]"
         *  из строки — на случай если ObjectContents не был отфильтрован на уровне компонента. */
        fun stripHeadPlaceholders(text: String): String =
            text.replace(Regex("""\[[^\]]*\s*head\]""", RegexOption.IGNORE_CASE), "").trim()

        private fun stripObjectContents(comp: net.minecraft.network.chat.Component): net.minecraft.network.chat.MutableComponent {
            val contents = comp.contents
            val copy: net.minecraft.network.chat.MutableComponent = when {
                contents is net.minecraft.network.chat.contents.PlainTextContents ->
                    net.minecraft.network.chat.Component.literal(contents.text())
                contents is net.minecraft.network.chat.contents.TranslatableContents ->
                    net.minecraft.network.chat.Component.translatable(contents.key, *contents.args)
                else ->
                    // ObjectContents (PlayerSprite и др.) — заменяем пустым узлом
                    net.minecraft.network.chat.Component.empty()
            }
            copy.setStyle(comp.style)
            comp.siblings.forEach { sib -> copy.append(stripObjectContents(sib)) }
            return copy
        }

        /** Парсит legacy &/§-форматирование в правильный Component.
         *  Работает без Component.Serializer (убран в 1.21.1).
         *  Нужно для совместимости с модами на ники (TAB, LuckPerms и др.). */
        fun parseLegacyNick(comp: net.minecraft.network.chat.Component): net.minecraft.network.chat.Component {
            // Сначала убираем ObjectContents (Chat Heads PlayerSprite и т.п.),
            // иначе comp.string даёт "[Errnick_ Head]Errnick_" и мусор попадает в ник.
            val cleaned = stripObjectContents(comp)
            val raw = stripHeadPlaceholders(cleaned.string)
            val hasSectionCodes = raw.contains('§')
            val hasAmpCodes = raw.contains('&') && raw.length > 1 &&
                raw.indexOfFirst { it == '&' }.let { i ->
                    i + 1 < raw.length && "0123456789abcdefklmnor".contains(raw[i + 1], ignoreCase = true)
                }
            if (!hasSectionCodes && !hasAmpCodes) return cleaned
            val text = if (hasAmpCodes && !hasSectionCodes) raw.replace('&', '§') else raw
            val root = net.minecraft.network.chat.Component.empty() as net.minecraft.network.chat.MutableComponent
            var currentStyle = net.minecraft.network.chat.Style.EMPTY
            var i = 0
            val sb = StringBuilder()
            while (i < text.length) {
                if (text[i] == '§' && i + 1 < text.length) {
                    if (sb.isNotEmpty()) {
                        root.append(net.minecraft.network.chat.Component.literal(sb.toString()).withStyle(currentStyle))
                        sb.clear()
                    }
                    val code = text[i + 1].lowercaseChar()
                    val fmt = net.minecraft.ChatFormatting.getByCode(code)
                    if (fmt != null) {
                        currentStyle = if (fmt == net.minecraft.ChatFormatting.RESET)
                            net.minecraft.network.chat.Style.EMPTY
                        else
                            currentStyle.applyLegacyFormat(fmt)
                    }
                    i += 2
                } else {
                    sb.append(text[i])
                    i++
                }
            }
            if (sb.isNotEmpty()) {
                root.append(net.minecraft.network.chat.Component.literal(sb.toString()).withStyle(currentStyle))
            }
            return root
        }

        @JvmStatic
        /** Сравнивает версии протокола как semver. Возвращает >0 если a > b */
        fun compareModVersions(a: String, b: String): Int {
            fun parse(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
            val pa = parse(a); val pb = parse(b)
            val len = maxOf(pa.size, pb.size)
            for (i in 0 until len) {
                val diff = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
                if (diff != 0) return diff
            }
            return 0
        }

        fun debugShowPlaceholder(mc: Minecraft, aspectRatio: Double, label: String) {
            val imageId = "debug_placeholder_${System.currentTimeMillis()}"
            addImageToChat(mc, imageId, "Debug", "placeholder $label", (aspectRatio * 100).toInt(), 100)
        }

        fun debugShowPlaceholderState(mc: Minecraft, state: String, aspectRatio: Double = 16.0 / 9.0, label: String = "16:9") {
            val imageId = "debug_${state}_${System.currentTimeMillis()}"
            addImageToChat(mc, imageId, "Debug", "$state $label", (aspectRatio * 100).toInt(), 100)
        }

        fun debugTestConnection(mc: Minecraft) {
            val chat = mc.gui.getChat()
            val host = ChatRemasteredConfig.serverHost
            val port = ChatRemasteredConfig.imagePort
            val hasMod = ChatRemasteredConfig.serverHasModVersion
            val token = ChatRemasteredConfig.uploadToken

            chat.addMessage(Component.literal("§8[Chat Remastered] §7--- Connection test ---"))

            // 1. Server mod handshake
            if (hasMod == null) {
                chat.addMessage(Component.literal("§8[Chat Remastered] §cServer mod: §cnot detected (no handshake)"))
            } else {
                chat.addMessage(Component.literal("§8[Chat Remastered] §aServer mod: §fv$hasMod"))
            }

            // 2. Upload token
            if (token.isEmpty()) {
                chat.addMessage(Component.literal("§8[Chat Remastered] §cUpload token: §cnot received"))
            } else {
                chat.addMessage(Component.literal("§8[Chat Remastered] §aUpload token: §freceived (${token.length} chars)"))
            }

            // 3. TCP ping (in background)
            chat.addMessage(Component.literal("§8[Chat Remastered] §7TCP $host:$port — pinging..."))
            Thread {
                val start = System.currentTimeMillis()
                val ok = TcpImageClient.ping()
                val ms = System.currentTimeMillis() - start
                ChatRemasteredConfig.serverReachable = ok
                mc.execute {
                    if (ok) {
                        chat.addMessage(Component.literal("§8[Chat Remastered] §aTCP: §aOK §7(${ms}ms)"))
                        chat.addMessage(Component.literal("§8[Chat Remastered] §a✔ " + ChatRemasteredConfig.tr("chat-remastered.tcp_ok")))
                    } else {
                        chat.addMessage(Component.literal("§8[Chat Remastered] §cTCP: §ccannot connect to $host:$port"))
                        chat.addMessage(Component.literal("§8[Chat Remastered] §c✘ " + ChatRemasteredConfig.tr("chat-remastered.tcp_fail")))
                    }
                }
            }.also { it.isDaemon = true }.start()
        }

        private fun pingTcpServer(mc: Minecraft) {
            val ok = TcpImageClient.ping()
            ChatRemasteredConfig.serverReachable = ok
            mc.execute {
                if (ok) {
                    mc.gui.getChat().addMessage(
                        Component.literal("§8[Chat Remastered] §a✔ " + ChatRemasteredConfig.tr(
                            "chat-remastered.connected", "§7${ChatRemasteredConfig.resolution}§a"
                        ))
                    )
                } else {
                    mc.gui.getChat().addMessage(
                        Component.literal("§8[Chat Remastered] §c✘ " + ChatRemasteredConfig.tr(
                            "chat-remastered.no_tcp_connect", ChatRemasteredConfig.imagePort.toString()
                        ))
                    )
                }
            }
        }

        @JvmStatic
        fun addReplyToChat(
            mc: Minecraft,
            sender: String,
            text: String,
            senderComponent: net.minecraft.network.chat.Component?,
            replyToSender: String,
            replyToText: String,
            replyToImageId: String
        ) {
            val rawComp: net.minecraft.network.chat.Component = senderComponent
                ?.takeIf { it.string.isNotEmpty() }
                ?: mc.connection?.onlinePlayers
                    ?.firstOrNull { it.profile.name == sender }
                    ?.tabListDisplayName
                ?: net.minecraft.network.chat.Component.literal(sender)
            val senderComp = parseLegacyNick(rawComp)

            // Сообщение в формате <Nick> text с \n-отступом над ником для плашки reply.
            val msgText = net.minecraft.network.chat.MutableComponent.create(
                net.minecraft.network.chat.contents.PlainTextContents.EMPTY
            ).also { base ->
                base.append(net.minecraft.network.chat.Component.literal("\n"))
                base.append(net.minecraft.network.chat.Component.literal("<"))
                base.append(senderComp)
                base.append(net.minecraft.network.chat.Component.literal("> $text"))
            }

            mc.gui.getChat().addMessage(msgText)
            val addedTime = ChatTimeHolder.lastAddedTime
            // Регистрируем подавление ванильного дубля ПОСЛЕ addMessage —
            // ваниль приходит асинхронно чуть позже по сети, поэтому успеваем.
            ChatRemasteredStore.markSuppressReplyMessage(sender, text)
            ChatRemasteredStore.addReply(
                senderName      = sender,
                text            = text,
                replyToSender   = replyToSender,
                replyToText     = replyToText,
                replyToImageId  = replyToImageId,
                senderComponent = senderComp,
                addedTime       = addedTime
            )
        }

        fun addImageToChat(mc: Minecraft, imageId: String, sender: String, caption: String?, width: Int, height: Int, senderComponent: net.minecraft.network.chat.Component? = null) {
            // Если пришёл senderComponent — используем напрямую (несёт цвета от сервера).
            // Иначе — ищем в tab-листе (fallback для локального показа).
            val rawComp: net.minecraft.network.chat.Component = senderComponent
                ?.takeIf { it.string.isNotEmpty() }
                ?: mc.connection?.onlinePlayers
                    ?.firstOrNull { it.profile.name == sender }
                    ?.tabListDisplayName
                ?: net.minecraft.network.chat.Component.literal(sender)
            val senderComp = parseLegacyNick(rawComp)

            // ── Шаг 1: сразу вычисляем dispW/dispH из переданных width/height ──
            // Не нужно ждать декодирования thumbnail — размер известен мгновенно.
            val maxW = ChatRemasteredConfig.previewMaxW
            val maxH = ChatRemasteredConfig.previewMaxH
            val aspect = if (height > 0) width.toDouble() / height.toDouble() else 16.0 / 9.0
            val (dispW, dispH) = if (aspect >= maxW.toDouble() / maxH.toDouble()) {
                val w = maxW
                val h = (w / aspect).toInt().coerceAtLeast(1)
                Pair(w, h)
            } else {
                val h = maxH
                val w = (h * aspect).toInt().coerceAtLeast(1)
                Pair(w, h)
            }

            val chatLineSpacing = mc.options.chatLineSpacing().get()
            val entryHeight = (9.0 * (chatLineSpacing + 1.0)).toInt()
            val extraLines = ceil(dispH.toDouble() / entryHeight).toInt()

            // Строим одно сообщение: сначала ник (он окажется внизу — чат растёт снизу вверх),
            // затем extraLines пустых строк через \n (они окажутся выше ника, резервируя место
            // под превью). Всё это один GuiMessage — один addedTime,
            // поэтому ПКМ по любой строке (включая «пустые» отступы) работает корректно.
            val msgText = net.minecraft.network.chat.MutableComponent.create(
                net.minecraft.network.chat.contents.PlainTextContents.EMPTY
            ).also { base ->
                base.append(net.minecraft.network.chat.Component.literal("<"))
                base.append(senderComp)
                base.append(net.minecraft.network.chat.Component.literal(">"))
                if (!caption.isNullOrEmpty()) {
                    base.append(net.minecraft.network.chat.Component.literal(" $caption"))
                }
                base.append(net.minecraft.network.chat.Component.literal(" §7[📷]"))
                // Пустые строки-отступы (место под фото) идут после ника,
                // чтобы в чате они оказались выше него (над фото).
                repeat(extraLines) {
                    base.append(net.minecraft.network.chat.Component.literal("\n"))
                }
            }

            // ── Шаг 2: мгновенно показываем плейсхолдер и добавляем в чат ──
            ImageCache.registerPlaceholder(imageId, dispW, dispH)
            mc.gui.getChat().addMessage(msgText)
            // Берём addedTime из последнего GuiMessage (с учётом уникального смещения),
            // чтобы поиск по line.addedTime() в ChatComponentMixin работал корректно.
            val addedTime = ChatTimeHolder.lastAddedTime
            ChatRemasteredStore.addMessage(imageId, sender, caption ?: "", addedTime, senderComp)
            // Пустые строки теперь внутри msgText через \n — отдельные addMessage не нужны.

            // ── Шаг 3: авто или ручное скачивание (зависит от autoDownload) ──
            // Debug-плейсхолдеры (команды /chatremastereddebug) не имеют файла на сервере —
            // пропускаем скачивание чтобы избежать markError через время.
            val isDebugImage = imageId.startsWith("debug_")
            if (isDebugImage) return

            if (ChatRemasteredConfig.autoDownload) {
                // AUTO: качаем полный файл сразу, thumbnail генерируем из него
                Thread {
                    fetchFullImage(imageId) { bytes ->
                        if (GifDecoder.isGif(bytes)) {
                            val gifMaxDim = ChatRemasteredConfig.gifMaxDim
                            val scaledBytes = scaleGifBytes(bytes, gifMaxDim) ?: bytes
                            ImageCache.storeFullData(imageId, scaledBytes)
                            val (dw, dh) = ImageCache.getSize(imageId) ?: Pair(dispW, dispH)
                            ImageCache.loadGif(imageId, scaledBytes, dw, dh)
                        } else {
                            val thumbnail = generateThumbnail(bytes, dispW, dispH) ?: return@fetchFullImage
                            ImageCache.loadThumbnail(imageId, thumbnail)
                            ImageCache.loadAndUpgradeHiRes(imageId, bytes)
                        }
                    }
                }.also { it.isDaemon = true }.start()
            } else {
                // Превью берём из fullDataMap (отправитель) или качаем полный файл (получатель).
                // getThumb не используется — сервер не шлёт данных, клиент генерирует превью сам.
                Thread {
                    val localBytes = ImageCache.getFullData(imageId)
                    if (localBytes != null) {
                        if (GifDecoder.isGif(localBytes)) {
                            val gifMaxDim = ChatRemasteredConfig.gifMaxDim
                            val scaledBytes = scaleGifBytes(localBytes, gifMaxDim) ?: localBytes
                            ImageCache.storeFullData(imageId, scaledBytes)
                            val (dw, dh) = ImageCache.getSize(imageId) ?: Pair(dispW, dispH)
                            ImageCache.loadGif(imageId, scaledBytes, dw, dh)
                        } else {
                            val thumbnail = generateThumbnail(localBytes, dispW, dispH) ?: return@Thread
                            ImageCache.loadThumbnail(imageId, thumbnail)
                        }
                    } else {
                        fetchFullImage(imageId) { bytes ->
                            if (GifDecoder.isGif(bytes)) {
                                val gifMaxDim = ChatRemasteredConfig.gifMaxDim
                                val scaledBytes = scaleGifBytes(bytes, gifMaxDim) ?: bytes
                                ImageCache.storeFullData(imageId, scaledBytes)
                                val (dw, dh) = ImageCache.getSize(imageId) ?: Pair(dispW, dispH)
                                ImageCache.loadGif(imageId, scaledBytes, dw, dh)
                            } else {
                                val thumbnail = generateThumbnail(bytes, dispW, dispH) ?: return@fetchFullImage
                                ImageCache.loadThumbnail(imageId, thumbnail)
                            }
                        }
                    }
                }.also { it.isDaemon = true }.start()
            }
        }

        /**
         * Генерирует PNG-превью из полного PNG, масштабируя под dispW×dispH GUI-единиц.
         * Вызывается в фоновом потоке.
         */
        private fun generateThumbnail(fullBytes: ByteArray, dispW: Int, dispH: Int): ByteArray? {
            return try {
                val original = ImageIO.read(ByteArrayInputStream(fullBytes)) ?: return null
                val mc = Minecraft.getInstance()
                // Целевой размер в физических пикселях = dispW/H * guiScale монитора.
                // chatScale влияет только на матрицу рендера, guiScale определяет физические пиксели.
                val guiScale = try { mc.window.guiScale.toFloat().coerceAtLeast(1f) } catch (_: Exception) { 1f }
                val chatScale = try { mc.options.chatScale().get().toFloat().coerceIn(0.01f, 1f) } catch (_: Exception) { 1f }
                val targetTexW = (dispW * guiScale * chatScale).toInt().coerceAtLeast(1)
                val targetTexH = (dispH * guiScale * chatScale).toInt().coerceAtLeast(1)
                val scaleX = targetTexW.toDouble() / original.width
                val scaleY = targetTexH.toDouble() / original.height
                val scale  = minOf(scaleX, scaleY).coerceAtMost(1.0)
                val scaled = scaleImage(original, scale, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                toPng(scaled)
            } catch (e: Exception) {
                println("[Chat Remastered] generateThumbnail error: ${e.message}")
                null
            }
        }

        @JvmStatic
        fun fetchFullImage(imageId: String, onReady: (ByteArray) -> Unit) {
            if (imageId.startsWith("debug_")) return  // debug-плейсхолдеры не скачиваем и не помечаем ошибкой
            if (ImageCache.isDeleted(imageId)) return  // удалённые фото не скачиваем
            val diskCached = ImageDiskCache.load(imageId)
            if (diskCached != null) { ImageCache.storeFullData(imageId, diskCached); onReady(diskCached); return }
            val cached = ImageCache.getFullData(imageId)
            if (cached != null) { onReady(cached); return }
            Thread {
                ImageCache.startDownload(imageId)
                val bytes = TcpImageClient.getFull(imageId) { p ->
                    ImageCache.setDownloadProgress(imageId, p)
                }
                if (bytes != null) {
                    ImageCache.storeFullData(imageId, bytes)
                    ImageDiskCache.save(imageId, bytes)
                    ImageCache.finishDownload(imageId)
                    onReady(bytes)
                } else {
                    ImageCache.resetDownload(imageId)
                    println("[Chat Remastered] Download failed for $imageId after all retries — marking as error")
                    Minecraft.getInstance().execute { ImageCache.markError(imageId) }
                }
            }.also { it.isDaemon = true }.start()
        }

        /**
         * Ручное скачивание (кнопка Download) — только когда autoDownload=false.
         * После скачивания апгрейдит текстуру на hi-res.
         */
        @JvmStatic
        fun fetchFullImageManual(imageId: String) {
            if (imageId.startsWith("debug_")) return  // debug-плейсхолдеры не скачиваем
            if (ImageCache.getDownloadState(imageId) == ImageCache.DownloadState.IN_PROGRESS) return
            if (ImageCache.getDownloadState(imageId) == ImageCache.DownloadState.DONE) return
            fetchFullImage(imageId) { bytes ->
                if (GifDecoder.isGif(bytes)) {
                    val gifMaxDim = ChatRemasteredConfig.gifMaxDim
                    val scaledBytes = scaleGifBytes(bytes, gifMaxDim) ?: bytes
                    val (dw, dh) = ImageCache.getSize(imageId) ?: return@fetchFullImage
                    ImageCache.loadGif(imageId, scaledBytes, dw, dh)
                } else {
                    val (dispW, dispH) = ImageCache.getSize(imageId) ?: return@fetchFullImage
                    val thumbnail = generateThumbnail(bytes, dispW, dispH) ?: return@fetchFullImage
                    ImageCache.loadThumbnail(imageId, thumbnail)
                    ImageCache.loadAndUpgradeHiRes(imageId, bytes)
                }
            }
        }

        @JvmStatic
        fun stageImage(file: File) {
            val mc = Minecraft.getInstance()

            // Если уже идёт загрузка — блокируем (пользователь должен нажать ✕)
            val existingPending = PendingImageState.getPending()
            if (existingPending != null && PendingImageState.uploadProgress in 0f..0.99f) {
                mc.gui.getChat().addMessage(Component.literal(
                    "§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.image_loading_wait")
                ))
                return
            }

            if (!canSendPhoto(mc)) return

            val maxPW = ChatRemasteredConfig.inputPreviewMaxW
            val maxPH = ChatRemasteredConfig.inputPreviewMaxH

            // ── ШАГ 1 (ГЛАВНЫЙ ПОТОК): МГНОВЕННО показываем 16:9 плейсхолдер ──
            // Размер ещё неизвестен (sizeKnown=false) — рендерер покажет иконку "?"
            // Чтение заголовка и всё тяжёлое — целиком в фоновом потоке
            // Начальный плейсхолдер: вписываем 16:9 в maxPW × maxPH
            val disp16x9W: Int
            val disp16x9H: Int
            if (maxPW * 9 <= maxPH * 16) {
                // ширина — лимитирующая сторона
                disp16x9W = maxPW
                disp16x9H = (maxPW * 9.0 / 16.0).toInt().coerceAtLeast(1)
            } else {
                // высота — лимитирующая сторона
                disp16x9H = maxPH
                disp16x9W = (maxPH * 16.0 / 9.0).toInt().coerceAtLeast(1)
            }
            val dispW0 = disp16x9W
            val dispH0 = disp16x9H

            val cancelToken = PendingImageState.newCancelToken()
            PendingImageState.setPending(
                PendingImageState.PendingImage(
                    file = file, textureId = null,
                    width = dispW0, height = dispH0,
                    textureWidth = dispW0, textureHeight = dispH0,
                    previewBytes = ByteArray(0), rawBytes = ByteArray(0),
                    rawReady = false, origWidth = 0, origHeight = 0,
                    sizeKnown = false
                )
            )
            PendingImageState.setProgress(-1f)  // бегущий огонёк пока читаем размер

            // ── ШАГ 2 (ФОНОВЫЙ ПОТОК): читаем размер заголовка, потом весь файл ──
            Thread {
                try {
                    if (PendingImageState.isCancelled(cancelToken)) return@Thread

                    // Быстро читаем только заголовок для определения реального размера
                    val headerSize = readImageSizeFromHeader(file)
                    val origW = headerSize?.first?.takeIf { it > 0 } ?: 1280
                    val origH = headerSize?.second?.takeIf { it > 0 } ?: 720
                    val aspect = origW.toDouble() / origH.toDouble()
                    val dispW: Int
                    val dispH: Int
                    if (aspect >= maxPW.toDouble() / maxPH.toDouble()) {
                        dispW = maxPW; dispH = (maxPW / aspect).toInt().coerceAtLeast(1)
                    } else {
                        dispH = maxPH; dispW = (maxPH * aspect).toInt().coerceAtLeast(1)
                    }

                    // Обновляем карточку с реальным размером и sizeKnown=true
                    if (!PendingImageState.isCancelled(cancelToken)) {
                        mc.execute {
                            val cur = PendingImageState.getPending()
                            if (cur != null && cur.file == file) {
                                PendingImageState.setPending(cur.copy(
                                    width = dispW, height = dispH,
                                    textureWidth = dispW, textureHeight = dispH,
                                    origWidth = origW, origHeight = origH,
                                    sizeKnown = true
                                ))
                            }
                        }
                    }
                    PendingImageState.setProgress(0.05f)

                    if (PendingImageState.isCancelled(cancelToken)) return@Thread

                    val fileBytes = file.readBytes()
                    if (PendingImageState.isCancelled(cancelToken)) return@Thread
                    PendingImageState.setProgress(0.2f)

                    val isGif = GifDecoder.isGif(fileBytes)

                    if (isGif) {
                        if (!ChatRemasteredConfig.gifEnabled) {
                            mc.execute {
                                PendingImageState.clear()
                                mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.gif_disabled_server")))
                            }
                            return@Thread
                        }

                        // При ОТПРАВКЕ используем gifMaxDimServer (ограничение сервера).
                        // gifMaxDim — только для локального отображения чужих GIF.
                        val sendMaxDim = ChatRemasteredConfig.gifMaxDimServer
                        val scaledGifBytes = scaleGifBytes(fileBytes, sendMaxDim)
                        val rawBytes = scaledGifBytes ?: fileBytes

                        if (PendingImageState.isCancelled(cancelToken)) return@Thread

                        if (rawBytes.size > MAX_FULL_BYTES) {
                            mc.execute {
                                PendingImageState.clear()
                                mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large_compress")))
                            }
                            return@Thread
                        }

                        // ── rawBytes готовы — МГНОВЕННО разрешаем отправку ──
                        mc.execute {
                            val cur = PendingImageState.getPending()
                            if (cur != null && cur.file == file) {
                                PendingImageState.setPending(cur.copy(rawBytes = rawBytes, rawReady = true))
                            }
                        }
                        PendingImageState.setProgress(0.5f)

                        // Дальше готовим текстуру превью (не блокирует отправку)
                        if (PendingImageState.isCancelled(cancelToken)) return@Thread
                        val firstFrame = GifDecoder.decode(rawBytes).firstOrNull()
                        val previewBytes: ByteArray
                        val previewTexW: Int
                        val previewTexH: Int

                        if (firstFrame != null) {
                            val guiScale = try { mc.window.guiScale.toFloat().coerceAtLeast(1f) } catch (_: Exception) { 1f }
                            val chatScaleVal = mc.options.chatScale().get().toFloat().coerceIn(0.01f, 1f)
                            val cur = PendingImageState.getPending()
                            val texDispW = cur?.width ?: dispW0
                            val texDispH = cur?.height ?: dispH0
                            val targetTexW = (texDispW * guiScale * chatScaleVal).toInt().coerceAtLeast(1)
                            val targetTexH = (texDispH * guiScale * chatScaleVal).toInt().coerceAtLeast(1)
                            val s = minOf(targetTexW.toFloat() / firstFrame.image.width, targetTexH.toFloat() / firstFrame.image.height).coerceAtMost(1f)
                            val fw = (firstFrame.image.width * s).toInt().coerceAtLeast(1)
                            val fh = (firstFrame.image.height * s).toInt().coerceAtLeast(1)
                            val scaled = scaleImage(firstFrame.image, s.toDouble(), java.awt.image.BufferedImage.TYPE_INT_ARGB)
                            val bos = ByteArrayOutputStream(); ImageIO.write(scaled, "png", bos)
                            previewBytes = bos.toByteArray(); previewTexW = fw; previewTexH = fh
                        } else {
                            previewBytes = ByteArray(0); previewTexW = dispW0; previewTexH = dispH0
                        }

                        PendingImageState.setProgress(0.9f)
                        if (PendingImageState.isCancelled(cancelToken)) return@Thread

                        mc.execute {
                            val cur = PendingImageState.getPending()
                            if (cur == null || cur.file != file) return@execute
                            try {
                                if (previewBytes.isNotEmpty()) {
                                    val nativeImage = NativeImage.read(ByteArrayInputStream(previewBytes))
                                    val previewId = Identifier.fromNamespaceAndPath("chat-remastered", "preview_${System.currentTimeMillis()}")
                                    mc.textureManager.register(previewId, DynamicTexture({ "chat-remastered-preview" }, nativeImage))
                                    PendingImageState.updateTexture(
                                        textureId = previewId, textureWidth = previewTexW, textureHeight = previewTexH,
                                        previewBytes = previewBytes, rawBytes = cur.rawBytes
                                    )
                                } else {
                                    PendingImageState.updateTexture(
                                        textureId = Identifier.fromNamespaceAndPath("chat-remastered", "gif_pending"),
                                        textureWidth = dispW0, textureHeight = dispH0,
                                        previewBytes = ByteArray(1), rawBytes = cur.rawBytes
                                    )
                                }
                                PendingImageState.setProgress(1f)
                            } catch (e: Exception) {
                                // Текстура не загрузилась — не беда, rawBytes уже готовы, отправить можно
                                println("[Chat Remastered] GIF preview texture error: ${e.message}")
                                PendingImageState.setProgress(1f)
                            }
                        }
                        return@Thread
                    }

                    // ── Обычное изображение (PNG/JPEG/WebP/BMP) ──
                    PendingImageState.setProgress(0.3f)
                    val original: java.awt.image.BufferedImage = ImageIO.read(file) ?: run {
                        mc.execute {
                            PendingImageState.clear()
                            mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.cannot_read")))
                        }
                        return@Thread
                    }

                    if (PendingImageState.isCancelled(cancelToken)) return@Thread
                    PendingImageState.setProgress(0.4f)

                    // Подгоняем под лимиты сервера
                    val maxDim = ChatRemasteredConfig.maxDim
                    val fullScale = if (original.width > maxDim || original.height > maxDim)
                        minOf(maxDim.toDouble() / original.width, maxDim.toDouble() / original.height)
                    else 1.0

                    val fullScaled = scaleImage(original, fullScale, java.awt.image.BufferedImage.TYPE_INT_RGB)
                    val fullBytes = toPng(fullScaled)

                    if (fullBytes.size > MAX_FULL_BYTES) {
                        mc.execute {
                            PendingImageState.clear()
                            mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large_compress")))
                        }
                        return@Thread
                    }

                    if (PendingImageState.isCancelled(cancelToken)) return@Thread
                    PendingImageState.setProgress(0.6f)

                    // ── rawBytes готовы — МГНОВЕННО разрешаем отправку ──
                    // Реальный размер для пакета — из масштабированного изображения
                    val scaledOrigW = fullScaled.width
                    val scaledOrigH = fullScaled.height
                    mc.execute {
                        val cur = PendingImageState.getPending()
                        if (cur != null && cur.file == file) {
                            PendingImageState.setPending(cur.copy(
                                rawBytes = fullBytes, rawReady = true,
                                origWidth = scaledOrigW, origHeight = scaledOrigH
                            ))
                        }
                    }

                    // Дальше готовим текстуру превью (не блокирует отправку)
                    val guiScale = try { mc.window.guiScale.toFloat().coerceAtLeast(1f) } catch (_: Exception) { 1f }
                    val chatScaleVal = mc.options.chatScale().get().toFloat().coerceIn(0.01f, 1f)
                    val cur2 = PendingImageState.getPending()
                    val texDispW = cur2?.width ?: dispW0
                    val texDispH = cur2?.height ?: dispH0
                    val targetTexW = (texDispW * guiScale * chatScaleVal).toInt().coerceAtLeast(1)
                    val targetTexH = (texDispH * guiScale * chatScaleVal).toInt().coerceAtLeast(1)
                    val previewScale = minOf(
                        targetTexW.toDouble() / fullScaled.width,
                        targetTexH.toDouble() / fullScaled.height
                    ).coerceAtMost(1.0)

                    val previewScaled = scaleImage(fullScaled, previewScale, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    val previewBytes  = toPng(previewScaled)

                    PendingImageState.setProgress(0.9f)
                    if (PendingImageState.isCancelled(cancelToken)) return@Thread

                    mc.execute {
                        val cur = PendingImageState.getPending()
                        if (cur == null || cur.file != file) return@execute
                        try {
                            val nativeImage = NativeImage.read(ByteArrayInputStream(previewBytes))
                            val previewId = Identifier.fromNamespaceAndPath("chat-remastered", "preview_${System.currentTimeMillis()}")
                            mc.textureManager.register(previewId, DynamicTexture({ "chat-remastered-preview" }, nativeImage))
                            PendingImageState.updateTexture(
                                textureId = previewId,
                                textureWidth = previewScaled.width, textureHeight = previewScaled.height,
                                previewBytes = previewBytes, rawBytes = cur.rawBytes
                            )
                            PendingImageState.setProgress(1f)
                        } catch (e: Exception) {
                            // Текстура не загрузилась — не беда, rawBytes уже готовы
                            println("[Chat Remastered] preview texture error: ${e.message}")
                            PendingImageState.setProgress(1f)
                        }
                    }
                } catch (e: Exception) {
                    mc.execute {
                        PendingImageState.clear()
                        mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.error", e.message ?: "?")))
                    }
                }
            }.also { it.isDaemon = true }.start()
        }


        @JvmStatic
        fun canSendPhoto(mc: Minecraft): Boolean {
            if (ChatRemasteredConfig.serverHasModVersion == null) {
                mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.no_server_mod")))
                return false
            }
            if (ChatRemasteredConfig.isBanned) {
                mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.banned")))
                return false
            }
            if (ChatRemasteredConfig.isMuted) {
                mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.muted")))
                return false
            }
            val cooldownMs = ChatRemasteredConfig.cooldownRemainingMs()
            if (cooldownMs > 0L) {
                val totalSec = (cooldownMs + 999L) / 1000L
                val cooldownMsg = if (totalSec >= 60L) {
                    val m = totalSec / 60L; val s = totalSec % 60L
                    ChatRemasteredConfig.tr("chat-remastered.cooldown_minutes", m, s)
                } else {
                    ChatRemasteredConfig.tr("chat-remastered.cooldown_seconds", totalSec)
                }
                mc.gui.getChat().addMessage(Component.literal("§e[Chat Remastered] $cooldownMsg"))
                return false
            }
            if (ChatRemasteredConfig.uploadToken.isEmpty()) {
                mc.gui.getChat().addMessage(Component.literal("§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.handshake_wait")))
                return false
            }
            if (!ChatRemasteredConfig.serverReachable) {
                mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.no_tcp")))
                return false
            }
            return true
        }

        @JvmStatic
        fun sendPendingImageWithCaption(caption: String?) {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return
            val pending = PendingImageState.getPending() ?: return
            if (!canSendPhoto(mc)) return

            if (!pending.canSend()) {
                // rawBytes ещё не готовы — показываем что идёт подготовка
                mc.gui.getChat().addMessage(Component.literal("§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.image_loading_wait")))
                return
            }

            val imageId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            val sender = player.gameProfile.name
            val rawComp = player.displayName ?: net.minecraft.network.chat.Component.literal(sender)
            val senderComp = parseLegacyNick(rawComp)
            val fullBytes = pending.rawBytes
            val token = ChatRemasteredConfig.uploadToken
            val captionSafe = caption ?: ""

            // Размеры берём из pending — они уже посчитаны правильно в stageImage
            val sendW = pending.origWidth.takeIf { it > 0 } ?: 1280
            val sendH = pending.origHeight.takeIf { it > 0 } ?: 720

            // ── Шаг 1: МГНОВЕННО показываем в чате для отправителя и очищаем превью ──
            // Используем rawBytes как fullData — остальные клиенты скачают по TCP
            PendingImageState.setPending(null)
            ChatRemasteredStore.storeOriginalFile(imageId, pending.file)
            ImageCache.storeFullData(imageId, fullBytes)
            // Подавляем реальное chat-сообщение которое сервер будет broadcast-ить:
            // отправитель не получает ImageChatPacket (исключён серверной рассылкой),
            // поэтому markSuppressPhotoMessage нужно вызвать здесь явно.
            // ВАЖНО: вызываем ДО отправки пакета чтобы успеть до прихода ванильного сообщения.
            ChatRemasteredStore.markSuppressPhotoMessage(sender, captionSafe.ifEmpty { null })
            addImageToChat(mc, imageId, sender, captionSafe, sendW, sendH, senderComp)
            ChatRemasteredConfig.startCooldown()
            ClientPlayNetworking.send(ImageUploadedPacket(imageId, sender, captionSafe, sendW, sendH))

            // ── Шаг 3: В фоне — сохраняем на диск и загружаем на TCP-сервер ──
            Thread {
                ImageDiskCache.save(imageId, fullBytes)
                val result = TcpImageClient.upload(imageId, token, fullBytes)
                when (result) {
                    "ok" -> { /* Файл на сервере — остальные клиенты скачают по TCP */ }
                    "forbidden" -> mc.execute {
                        mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.banned")))
                        // НЕ помечаем ошибкой — отправитель видит своё фото локально
                    }
                    "too_large" -> mc.execute {
                        mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.too_large_upload")))
                        // НЕ помечаем ошибкой — отправитель видит своё фото локально
                    }
                    else -> mc.execute {
                        // Сетевая ошибка загрузки: НЕ показываем ошибку на карточке отправителя —
                        // у него фото есть локально. Только сообщение в чат.
                        // Запоминаем что уже показали ошибку — чтобы подавить дублирующий ImageErrorPacket от сервера.
                        ChatRemasteredStore.markUploadErrorShown(imageId)
                        mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.upload_error", result)))
                        // ImageCache.markError(imageId) — убрано намеренно: отправитель видит фото из fullDataMap
                    }
                }
            }.also { it.isDaemon = true }.start()
        }

        @JvmStatic
        fun sendPendingImage() = sendPendingImageWithCaption(null)

        @JvmStatic
        fun pasteImageFromClipboard() {
            val mc = Minecraft.getInstance()
            if (!canSendPhoto(mc)) return

            // Если уже идёт загрузка — блокируем
            val existingPending = PendingImageState.getPending()
            if (existingPending != null && PendingImageState.uploadProgress in 0f..0.99f) {
                mc.gui.getChat().addMessage(Component.literal(
                    "§e[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.image_loading_wait")
                ))
                return
            }

            // ── МГНОВЕННО показываем плейсхолдер ещё до запуска PowerShell/xclip ──
            // Размер пока неизвестен — показываем 16:9, уточним как только придут байты
            val maxPW = ChatRemasteredConfig.inputPreviewMaxW
            val maxPH = ChatRemasteredConfig.inputPreviewMaxH
            val disp16x9W: Int
            val disp16x9H: Int
            if (maxPW * 9 <= maxPH * 16) {
                disp16x9W = maxPW
                disp16x9H = (maxPW * 9.0 / 16.0).toInt().coerceAtLeast(1)
            } else {
                disp16x9H = maxPH
                disp16x9W = (maxPH * 16.0 / 9.0).toInt().coerceAtLeast(1)
            }
            val dispW0 = disp16x9W
            val dispH0 = disp16x9H

            // Используем виртуальный файл-заглушку — stageFromBytes обойдётся без реального файла
            val placeholderFile = java.io.File(System.getProperty("java.io.tmpdir"), "chat-remastered-clipboard-pending.png")
            val cancelToken = PendingImageState.newCancelToken()
            PendingImageState.setPending(
                PendingImageState.PendingImage(
                    file = placeholderFile, textureId = null,
                    width = dispW0, height = dispH0,
                    textureWidth = dispW0, textureHeight = dispH0,
                    previewBytes = ByteArray(0), rawBytes = ByteArray(0),
                    rawReady = false, origWidth = 0, origHeight = 0,
                    sizeKnown = false
                )
            )
            // -1 = неопределённый прогресс (прогресс-бар не рисуется пока ждём буфер обмена)
            PendingImageState.setProgress(-1f)

            // ── Фоном: читаем буфер обмена (PowerShell/xclip), потом обрабатываем ──
            Thread {
                try {
                    if (PendingImageState.isCancelled(cancelToken)) return@Thread

                    val bytes = readImageFromClipboardNative()
                    if (bytes == null || bytes.isEmpty()) {
                        mc.execute {
                            PendingImageState.clear()
                            mc.gui.getChat().addMessage(Component.literal("§7[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.clipboard_empty")))
                        }
                        return@Thread
                    }

                    if (PendingImageState.isCancelled(cancelToken)) return@Thread
                    PendingImageState.setProgress(0.15f)

                    // Размер из байтов PNG (уже в памяти — без IO)
                    val headerSize = readImageSizeFromBytes(bytes)
                    val origW = headerSize?.first?.takeIf { it > 0 } ?: 1280
                    val origH = headerSize?.second?.takeIf { it > 0 } ?: 720
                    val aspect = origW.toDouble() / origH.toDouble()
                    val dispW: Int
                    val dispH: Int
                    if (aspect >= maxPW.toDouble() / maxPH.toDouble()) {
                        dispW = maxPW; dispH = (maxPW / aspect).toInt().coerceAtLeast(1)
                    } else {
                        dispH = maxPH; dispW = (maxPH * aspect).toInt().coerceAtLeast(1)
                    }

                    // Обновляем размер карточки — всегда, чтобы выставить sizeKnown=true
                    mc.execute {
                        val cur = PendingImageState.getPending()
                        if (cur != null && cur.file == placeholderFile) {
                            PendingImageState.setPending(cur.copy(
                                width = dispW, height = dispH,
                                textureWidth = dispW, textureHeight = dispH,
                                origWidth = origW, origHeight = origH,
                                sizeKnown = true
                            ))
                        }
                    }

                    // Сохраняем байты во временный файл (нужен для stageImage-совместимости)
                    val tmpFile = java.io.File.createTempFile("chat-remastered-paste-", ".png")
                        .also { it.deleteOnExit(); it.writeBytes(bytes) }

                    // Обновляем file в pending на реальный tmpFile
                    mc.execute {
                        val cur = PendingImageState.getPending()
                        if (cur != null && cur.file == placeholderFile) {
                            PendingImageState.setPending(cur.copy(file = tmpFile, origWidth = origW, origHeight = origH))
                        }
                    }

                    if (PendingImageState.isCancelled(cancelToken)) return@Thread
                    PendingImageState.setProgress(0.2f)

                    // Дальше обрабатываем байты напрямую — не нужно читать файл снова
                    processBytesForPending(mc, tmpFile, bytes, dispW, dispH, origW, origH, cancelToken)

                } catch (e: Exception) {
                    println("[Chat Remastered] Clipboard paste error: ${e.message}")
                    mc.execute {
                        PendingImageState.clear()
                        mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.clipboard_error", e.message ?: "?")))
                    }
                }
            }.also { it.isDaemon = true }.start()
        }

        /**
         * Читает размер изображения прямо из байтов в памяти (без IO).
         * Дублирует логику readImageSizeFromHeader но работает с ByteArray.
         */
        private fun readImageSizeFromBytes(bytes: ByteArray): Pair<Int, Int>? {
            if (bytes.size < 8) return null
            // PNG
            if (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
                && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()) {
                if (bytes.size < 24) return null
                val w = ((bytes[16].toInt() and 0xFF) shl 24) or ((bytes[17].toInt() and 0xFF) shl 16) or
                        ((bytes[18].toInt() and 0xFF) shl 8) or (bytes[19].toInt() and 0xFF)
                val h = ((bytes[20].toInt() and 0xFF) shl 24) or ((bytes[21].toInt() and 0xFF) shl 16) or
                        ((bytes[22].toInt() and 0xFF) shl 8) or (bytes[23].toInt() and 0xFF)
                return if (w > 0 && h > 0) Pair(w, h) else null
            }
            // JPEG
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                var i = 2
                while (i + 3 < bytes.size) {
                    if (bytes[i] != 0xFF.toByte()) break
                    val marker = bytes[i + 1].toInt() and 0xFF
                    val segLen = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                    if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB)) {
                        if (i + 8 < bytes.size) {
                            val h = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                            val w = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
                            return if (w > 0 && h > 0) Pair(w, h) else null
                        }
                    }
                    i += 2 + segLen
                }
            }
            return null
        }

        /**
         * Обрабатывает уже прочитанные байты изображения: масштабирует, готовит rawBytes и текстуру.
         * Вызывается из pasteImageFromClipboard после чтения буфера обмена.
         * Эквивалент второй половины stageImage, но без повторного чтения файла.
         */
        private fun processBytesForPending(
            mc: net.minecraft.client.Minecraft,
            file: java.io.File,
            bytes: ByteArray,
            dispW: Int, dispH: Int,
            origW: Int, origH: Int,
            cancelToken: java.util.concurrent.atomic.AtomicBoolean
        ) {
            if (PendingImageState.isCancelled(cancelToken)) return

            val isGif = GifDecoder.isGif(bytes)
            if (isGif) {
                if (!ChatRemasteredConfig.gifEnabled) {
                    mc.execute {
                        PendingImageState.clear()
                        mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.gif_disabled_server")))
                    }
                    return
                }
                // При ОТПРАВКЕ — ограничение сервера, не локальный gifMaxDim
                val sendMaxDim = ChatRemasteredConfig.gifMaxDimServer
                val rawBytes = scaleGifBytes(bytes, sendMaxDim) ?: bytes
                if (rawBytes.size > MAX_FULL_BYTES) {
                    mc.execute {
                        PendingImageState.clear()
                        mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large_compress")))
                    }
                    return
                }
                mc.execute {
                    val cur = PendingImageState.getPending()
                    if (cur != null && cur.file == file) {
                        PendingImageState.setPending(cur.copy(rawBytes = rawBytes, rawReady = true))
                    }
                }
                PendingImageState.setProgress(0.5f)
                if (PendingImageState.isCancelled(cancelToken)) return
                val firstFrame = GifDecoder.decode(rawBytes).firstOrNull()
                if (firstFrame != null) {
                    val guiScale = try { mc.window.guiScale.toFloat().coerceAtLeast(1f) } catch (_: Exception) { 1f }
                    val chatScaleVal = mc.options.chatScale().get().toFloat().coerceIn(0.01f, 1f)
                    val targetTexW = (dispW * guiScale * chatScaleVal).toInt().coerceAtLeast(1)
                    val targetTexH = (dispH * guiScale * chatScaleVal).toInt().coerceAtLeast(1)
                    val s = minOf(targetTexW.toFloat() / firstFrame.image.width, targetTexH.toFloat() / firstFrame.image.height).coerceAtMost(1f)
                    val fw = (firstFrame.image.width * s).toInt().coerceAtLeast(1)
                    val fh = (firstFrame.image.height * s).toInt().coerceAtLeast(1)
                    val scaled = scaleImage(firstFrame.image, s.toDouble(), java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    val bos = ByteArrayOutputStream(); ImageIO.write(scaled, "png", bos)
                    val previewBytes = bos.toByteArray()
                    if (PendingImageState.isCancelled(cancelToken)) return
                    mc.execute {
                        val cur = PendingImageState.getPending() ?: return@execute
                        if (cur.file != file) return@execute
                        try {
                            val nativeImage = NativeImage.read(ByteArrayInputStream(previewBytes))
                            val previewId = Identifier.fromNamespaceAndPath("chat-remastered", "preview_${System.currentTimeMillis()}")
                            mc.textureManager.register(previewId, DynamicTexture({ "chat-remastered-preview" }, nativeImage))
                            PendingImageState.updateTexture(previewId, fw, fh, previewBytes, rawBytes)
                            PendingImageState.setProgress(1f)
                        } catch (e: Exception) { PendingImageState.setProgress(1f) }
                    }
                } else {
                    PendingImageState.setProgress(1f)
                }
                return
            }

            // Обычное изображение
            PendingImageState.setProgress(0.3f)
            val original: java.awt.image.BufferedImage = try {
                ImageIO.read(ByteArrayInputStream(bytes))
            } catch (_: Exception) { null } ?: run {
                mc.execute {
                    PendingImageState.clear()
                    mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.cannot_read")))
                }
                return
            }

            if (PendingImageState.isCancelled(cancelToken)) return
            PendingImageState.setProgress(0.45f)

            val maxDim = ChatRemasteredConfig.maxDim
            val fullScale = if (original.width > maxDim || original.height > maxDim)
                minOf(maxDim.toDouble() / original.width, maxDim.toDouble() / original.height)
            else 1.0
            val fullScaled = scaleImage(original, fullScale, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val fullBytes = toPng(fullScaled)

            if (fullBytes.size > MAX_FULL_BYTES) {
                mc.execute {
                    PendingImageState.clear()
                    mc.gui.getChat().addMessage(Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large_compress")))
                }
                return
            }

            if (PendingImageState.isCancelled(cancelToken)) return
            PendingImageState.setProgress(0.6f)

            // rawBytes готовы — разрешаем отправку
            mc.execute {
                val cur = PendingImageState.getPending()
                if (cur != null && cur.file == file) {
                    PendingImageState.setPending(cur.copy(
                        rawBytes = fullBytes, rawReady = true,
                        origWidth = fullScaled.width, origHeight = fullScaled.height
                    ))
                }
            }

            val guiScale = try { mc.window.guiScale.toFloat().coerceAtLeast(1f) } catch (_: Exception) { 1f }
            val chatScaleVal = mc.options.chatScale().get().toFloat().coerceIn(0.01f, 1f)
            val targetTexW = (dispW * guiScale * chatScaleVal).toInt().coerceAtLeast(1)
            val targetTexH = (dispH * guiScale * chatScaleVal).toInt().coerceAtLeast(1)
            val previewScale = minOf(
                targetTexW.toDouble() / fullScaled.width,
                targetTexH.toDouble() / fullScaled.height
            ).coerceAtMost(1.0)
            val previewScaled = scaleImage(fullScaled, previewScale, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val previewBytes = toPng(previewScaled)

            PendingImageState.setProgress(0.9f)
            if (PendingImageState.isCancelled(cancelToken)) return

            mc.execute {
                val cur = PendingImageState.getPending() ?: return@execute
                if (cur.file != file) return@execute
                try {
                    val nativeImage = NativeImage.read(ByteArrayInputStream(previewBytes))
                    val previewId = Identifier.fromNamespaceAndPath("chat-remastered", "preview_${System.currentTimeMillis()}")
                    mc.textureManager.register(previewId, DynamicTexture({ "chat-remastered-preview" }, nativeImage))
                    PendingImageState.updateTexture(previewId, previewScaled.width, previewScaled.height, previewBytes, fullBytes)
                    PendingImageState.setProgress(1f)
                } catch (e: Exception) { PendingImageState.setProgress(1f) }
            }
        }

        /**
         * Читает изображение из буфера обмена через нативные инструменты ОС.
         * Возвращает PNG-байты или null если в буфере нет изображения.
         */
        private fun readImageFromClipboardNative(): ByteArray? {
            val os = System.getProperty("os.name", "").lowercase()
            return when {
                os.contains("win") -> readClipboardWindows()
                os.contains("mac") -> readClipboardMac()
                else               -> readClipboardLinux()
            }
        }

        private fun readClipboardWindows(): ByteArray? {
            // PowerShell: читаем изображение из буфера и сохраняем как PNG в temp-файл
            val tmp = java.io.File.createTempFile("chat-remastered-clip-", ".png").also { it.deleteOnExit() }
            val script = """
                Add-Type -AssemblyName System.Windows.Forms;
                ${'$'}img = [System.Windows.Forms.Clipboard]::GetImage();
                if (${'$'}img -eq ${'$'}null) { exit 1 }
                ${'$'}img.Save('${tmp.absolutePath.replace("\\", "\\\\")}', [System.Drawing.Imaging.ImageFormat]::Png);
                exit 0
            """.trimIndent()
            val proc = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start()
            val exit = proc.waitFor()
            if (exit != 0 || !tmp.exists() || tmp.length() == 0L) return null
            return tmp.readBytes()
        }

        private fun readClipboardMac(): ByteArray? {
            // osascript: сохраняем изображение из буфера в temp PNG
            val tmp = java.io.File.createTempFile("chat-remastered-clip-", ".png").also { it.deleteOnExit() }
            val script = """
                set filePath to "${tmp.absolutePath}"
                try
                    set theImage to the clipboard as «class PNGf»
                    set fileRef to open for access POSIX file filePath with write permission
                    write theImage to fileRef
                    close access fileRef
                on error
                    error "no image"
                end try
            """.trimIndent()
            val proc = ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true).start()
            val exit = proc.waitFor()
            if (exit != 0 || !tmp.exists() || tmp.length() == 0L) return null
            return tmp.readBytes()
        }

        private fun readClipboardLinux(): ByteArray? {
            // xclip: читаем image/png из буфера обмена
            val tools = listOf(
                listOf("xclip", "-selection", "clipboard", "-t", "image/png", "-o"),
                listOf("xsel", "--clipboard", "--output")
            )
            for (cmd in tools) {
                try {
                    val proc = ProcessBuilder(cmd).redirectErrorStream(false).start()
                    val bytes = proc.inputStream.readBytes()
                    proc.waitFor()
                    if (bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) {
                        return bytes // PNG magic bytes: 0x89 P N G
                    }
                } catch (_: Exception) {}
            }
            return null
        }


        /**
         * Читает размеры изображения из заголовка файла без полного декодирования.
         * PNG: байты 16-23 содержат ширину и высоту (big-endian int).
         * JPEG: ищем маркер SOF0/SOF2 (0xFFC0/0xFFC2).
         * Возвращает Pair(width, height) или null если не удалось.
         */
        private fun readImageSizeFromHeader(file: File): Pair<Int, Int>? {
            return try {
                file.inputStream().use { stream ->
                    val header = stream.readNBytes(26)
                    if (header.size < 8) return null
                    // PNG: сигнатура 8 байт, затем IHDR чанк: 4 length + 4 type + 4 width + 4 height
                    if (header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte()
                        && header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte()) {
                        if (header.size < 24) return null
                        val w = ((header[16].toInt() and 0xFF) shl 24) or
                                ((header[17].toInt() and 0xFF) shl 16) or
                                ((header[18].toInt() and 0xFF) shl 8) or
                                 (header[19].toInt() and 0xFF)
                        val h = ((header[20].toInt() and 0xFF) shl 24) or
                                ((header[21].toInt() and 0xFF) shl 16) or
                                ((header[22].toInt() and 0xFF) shl 8) or
                                 (header[23].toInt() and 0xFF)
                        return if (w > 0 && h > 0) Pair(w, h) else null
                    }
                    // JPEG: сигнатура FF D8
                    if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
                        // Сканируем маркеры — нужен SOF (0xFFC0..0xFFC3, 0xFFC5..0xFFC7, 0xFFC9..0xFFCB)
                        val buf = file.readBytes()
                        var i = 2
                        while (i + 3 < buf.size) {
                            if (buf[i] != 0xFF.toByte()) break
                            val marker = buf[i + 1].toInt() and 0xFF
                            val segLen = ((buf[i + 2].toInt() and 0xFF) shl 8) or (buf[i + 3].toInt() and 0xFF)
                            if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB)) {
                                if (i + 8 < buf.size) {
                                    val h = ((buf[i + 5].toInt() and 0xFF) shl 8) or (buf[i + 6].toInt() and 0xFF)
                                    val w = ((buf[i + 7].toInt() and 0xFF) shl 8) or (buf[i + 8].toInt() and 0xFF)
                                    return if (w > 0 && h > 0) Pair(w, h) else null
                                }
                            }
                            i += 2 + segLen
                        }
                    }
                    // WebP: RIFF????WEBP + VP8 /VP8L/VP8X
                    if (header.size >= 12
                        && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte()
                        && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()
                        && header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte()
                        && header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()) {
                        val buf = file.readBytes()
                        if (buf.size >= 30 && buf[12] == 'V'.code.toByte()
                            && buf[13] == 'P'.code.toByte() && buf[14] == '8'.code.toByte()) {
                            when (buf[15].toInt().toChar()) {
                                ' ' -> { // VP8 lossy: ширина/высота с 14-битными полями в байтах 26-29
                                    if (buf.size >= 30) {
                                        val w = ((buf[26].toInt() and 0xFF) or ((buf[27].toInt() and 0x3F) shl 8))
                                        val h = ((buf[28].toInt() and 0xFF) or ((buf[29].toInt() and 0x3F) shl 8))
                                        return if (w > 0 && h > 0) Pair(w, h) else null
                                    }
                                }
                                'L' -> { // VP8L lossless: первые 28 бит после сигнатуры
                                    if (buf.size >= 25) {
                                        val bits = (buf[21].toLong() and 0xFF) or
                                                   ((buf[22].toLong() and 0xFF) shl 8) or
                                                   ((buf[23].toLong() and 0xFF) shl 16) or
                                                   ((buf[24].toLong() and 0xFF) shl 24)
                                        val w = ((bits and 0x3FFF) + 1).toInt()
                                        val h = (((bits shr 14) and 0x3FFF) + 1).toInt()
                                        return if (w > 0 && h > 0) Pair(w, h) else null
                                    }
                                }
                                'X' -> { // VP8X extended: 3 байта LE ширина-1, 3 байта LE высота-1
                                    if (buf.size >= 30) {
                                        val w = ((buf[24].toInt() and 0xFF) or ((buf[25].toInt() and 0xFF) shl 8) or ((buf[26].toInt() and 0xFF) shl 16)) + 1
                                        val h = ((buf[27].toInt() and 0xFF) or ((buf[28].toInt() and 0xFF) shl 8) or ((buf[29].toInt() and 0xFF) shl 16)) + 1
                                        return if (w > 0 && h > 0) Pair(w, h) else null
                                    }
                                }
                            }
                        }
                    }
                    // BMP: сигнатура "BM", ширина в байтах 18-21, высота в 22-25 (little-endian, высота может быть отрицательной)
                    if (header.size >= 26
                        && header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()) {
                        val w = (header[18].toInt() and 0xFF) or
                                ((header[19].toInt() and 0xFF) shl 8) or
                                ((header[20].toInt() and 0xFF) shl 16) or
                                ((header[21].toInt() and 0xFF) shl 24)
                        val h = Math.abs(
                            (header[22].toInt() and 0xFF) or
                            ((header[23].toInt() and 0xFF) shl 8) or
                            ((header[24].toInt() and 0xFF) shl 16) or
                            ((header[25].toInt() and 0xFF) shl 24)
                        )
                        return if (w > 0 && h > 0) Pair(w, h) else null
                    }
                    // TIFF и прочие форматы — размер вытащить из заголовка сложно,
                    // возвращаем null и используем дефолтный aspect 16:9 для плейсхолдера
                    null
                }
            } catch (_: Exception) { null }
        }
        private fun scaleImage(src: BufferedImage, scale: Double, type: Int): BufferedImage {
            if (scale >= 1.0) return if (src.type == type) src else {
                val c = BufferedImage(src.width, src.height, type)
                c.createGraphics().also { it.drawImage(src, 0, 0, null); it.dispose() }
                c
            }
            val nw = (src.width * scale).toInt().coerceAtLeast(1)
            val nh = (src.height * scale).toInt().coerceAtLeast(1)
            val out = BufferedImage(nw, nh, type)
            val g = out.createGraphics().also {
                it.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                it.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
                it.drawImage(src, 0, 0, nw, nh, null)
            }
            g.dispose()
            return out
        }

        private fun toPng(image: BufferedImage): ByteArray {
            val bos = ByteArrayOutputStream()
            val img = if (image.type != BufferedImage.TYPE_INT_ARGB) {
                val c = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
                c.createGraphics().also { it.drawImage(image, 0, 0, null); it.dispose() }
                c
            } else image
            ImageIO.write(img, "png", bos)
            return bos.toByteArray()
        }

        /**
         * Масштабирует каждый кадр GIF под maxDim и записывает обратно в GIF.
         * Если исходный GIF уже меньше maxDim — возвращает null (использовать оригинал).
         * Использует javax.imageio для кодирования GIF (без внешних зависимостей).
         */
        fun scaleGifBytes(data: ByteArray, maxDim: Int): ByteArray? {
            return try {
                val frames = GifDecoder.decode(data)
                if (frames.isEmpty()) return null
                val srcW = frames[0].image.width
                val srcH = frames[0].image.height
                if (srcW <= maxDim && srcH <= maxDim) return null  // не нужно масштабировать

                val scale = minOf(maxDim.toDouble() / srcW, maxDim.toDouble() / srcH)
                val targetW = (srcW * scale).toInt().coerceAtLeast(1)
                val targetH = (srcH * scale).toInt().coerceAtLeast(1)

                val bos = ByteArrayOutputStream()
                val writers = javax.imageio.ImageIO.getImageWritersByFormatName("gif")
                if (!writers.hasNext()) return null
                val writer = writers.next()

                val ios = javax.imageio.stream.MemoryCacheImageOutputStream(bos)
                writer.output = ios

                writer.prepareWriteSequence(null)

                for (frame in frames) {
                    val scaled = if (frame.image.width != targetW || frame.image.height != targetH) {
                        val out = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB)
                        val g = out.createGraphics()
                        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        g.drawImage(frame.image, 0, 0, targetW, targetH, null)
                        g.dispose()
                        out
                    } else frame.image

                    val writeParam = writer.defaultWriteParam
                    val iio = javax.imageio.IIOImage(scaled, null, null)
                    writer.writeToSequence(iio, writeParam)
                }

                writer.endWriteSequence()
                ios.close()
                writer.dispose()

                bos.toByteArray()
            } catch (e: Exception) {
                println("[Chat Remastered] scaleGifBytes error: ${e.message}")
                null
            }
        }
        @JvmStatic
        fun saveImageAs(imageId: String) {
            if (ImageCache.isDeleted(imageId)) return
            val mc = Minecraft.getInstance()
            val dlState = ImageCache.getDownloadState(imageId)

            fun doSaveDialog(bytes: ByteArray) {
                Thread {
                    try {
                        val isGif = GifDecoder.isGif(bytes)
                        val ext = if (isGif) "gif" else "png"
                        val defaultName = "$imageId.$ext"
                        val path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(
                            ChatRemasteredConfig.tr("chat-remastered.save_as_title"),
                            defaultName,
                            null, null
                        ) ?: return@Thread
                        val dest = java.io.File(if (path.contains(".")) path else "$path.$ext")
                        if (isGif) {
                            dest.writeBytes(bytes)
                        } else {
                            val img = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
                            if (img != null) javax.imageio.ImageIO.write(img, "png", dest)
                            else dest.writeBytes(bytes)
                        }
                    } catch (e: Exception) {
                        println("[Chat Remastered] saveImageAs error: ${e.message}")
                    }
                }.also { it.isDaemon = true; it.name = "Chat Remastered-SaveAs" }.start()
            }

            if (dlState == ImageCache.DownloadState.DONE) {
                val bytes = ImageDiskCache.load(imageId) ?: ImageCache.getFullData(imageId)
                if (bytes != null) { doSaveDialog(bytes); return }
            }

            fetchFullImage(imageId) { bytes ->
                mc.execute { doSaveDialog(bytes) }
            }
        }
    }

}