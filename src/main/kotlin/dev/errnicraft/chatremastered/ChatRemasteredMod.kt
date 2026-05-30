package dev.errnicraft.chatremastered

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.arguments.EntityArgument
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.util.Collections
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ChatRemasteredMod : ModInitializer {

    companion object {
        private val GSON = Gson()

        /** UUID игроков у которых есть мод (handshake завершён) */
        private val modPlayers: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

        /** Токен загрузки для каждого игрока. Ключ — UUID игрока, значение — uploadToken */
        private val playerTokens: MutableMap<UUID, String> = Collections.synchronizedMap(mutableMapOf())

        /** UUID игроков которым запрещена отправка фото (персистентный, хранится на диске) */
        private val bannedPlayers: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

        /** UUID игроков которым запрещены фото И ответы (мут, персистентный) */
        private val mutedPlayers: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

        /**
         * UUID игроков, замученных нашим модом через BanHammer (постоянный или временной мут).
         * Нужен чтобы tick-опрос мог отследить снятие BH-мута (в т.ч. по истечению времени)
         * и отправить клиенту PhotoUnbannedPacket.
         */
        private val bhMutedByUs: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

        /** Путь к файлу бан-листа (устанавливается при старте сервера) */
        @Volatile private var banListFile: Path? = null

        /** Путь к файлу мут-листа */
        @Volatile private var muteListFile: Path? = null

        /** Ожидающие рассылки: imageId → данные. Заполняется при ImageUploadedPacket,
         *  очищается когда onImageReady callback получил файл по TCP. */
        data class PendingBroadcast(val sender: String, val caption: String, val senderUuid: UUID, val width: Int, val height: Int)
        private val pendingBroadcasts: MutableMap<String, PendingBroadcast> =
            Collections.synchronizedMap(mutableMapOf())

        /** imageId → UUID владельца (кто загрузил фото) */
        private val imageOwners: MutableMap<String, UUID> =
            Collections.synchronizedMap(mutableMapOf())

        @Volatile var currentServer: net.minecraft.server.MinecraftServer? = null

        /** Закэшированный конфиг сервера (обновляется при старте) */
        @Volatile var cachedConfig: ServerConfig? = null

        /** Парсит legacy &-форматирование (&c, &l, &r и т.д.) в правильный Component.
         *  Нужно для совместимости с модами на ники (например, TAB), которые хранят
         *  displayName в виде "§c§lErrnick_" или "&c&lErrnick_". */
        /** Парсит legacy &/§-форматирование в правильный Component.
         *  Работает без Component.Serializer (убран в 1.21.1).
         *  Разбивает строку на сегменты по §X-кодам и собирает MutableComponent. */
        /** Рекурсивно копирует компонент, выбрасывая ObjectContents (PlayerSprite и др.) */
        private fun stripObjectContents(comp: net.minecraft.network.chat.Component): net.minecraft.network.chat.MutableComponent {
            val contents = comp.contents
            val copy: net.minecraft.network.chat.MutableComponent = when {
                contents is net.minecraft.network.chat.contents.PlainTextContents ->
                    net.minecraft.network.chat.Component.literal(contents.text())
                contents is net.minecraft.network.chat.contents.TranslatableContents ->
                    net.minecraft.network.chat.Component.translatable(contents.key, *contents.args)
                else ->
                    net.minecraft.network.chat.Component.empty()
            }
            copy.setStyle(comp.style)
            comp.siblings.forEach { sib -> copy.append(stripObjectContents(sib)) }
            return copy
        }

        fun parseLegacyNick(comp: net.minecraft.network.chat.Component, plainName: String): net.minecraft.network.chat.Component {
            // Сначала убираем ObjectContents (PlayerSprite от Chat Heads и подобных),
            // иначе comp.string даёт "[Player head]Errnick_" и мусор попадает в пакет.
            val cleaned = stripObjectContents(comp)
            val raw = cleaned.string.ifBlank { plainName }
            val hasSectionCodes = raw.contains('§')
            val hasAmpCodes = raw.contains('&') && raw.length > 1 &&
                raw.indexOfFirst { it == '&' }.let { i ->
                    i + 1 < raw.length && "0123456789abcdefklmnor".contains(raw[i + 1], ignoreCase = true)
                }
            if (!hasSectionCodes && !hasAmpCodes) return comp
            val text = if (hasAmpCodes && !hasSectionCodes) raw.replace('&', '§') else raw
            return parseSectionCodes(text)
        }

        private fun parseSectionCodes(text: String): net.minecraft.network.chat.Component {
            val root = net.minecraft.network.chat.Component.empty() as net.minecraft.network.chat.MutableComponent
            var currentStyle = net.minecraft.network.chat.Style.EMPTY
            var i = 0
            val sb = StringBuilder()
            while (i < text.length) {
                if (text[i] == '§' && i + 1 < text.length) {
                    // Flush pending text
                    if (sb.isNotEmpty()) {
                        root.append(net.minecraft.network.chat.Component.literal(sb.toString()).withStyle(currentStyle))
                        sb.clear()
                    }
                    val code = text[i + 1].lowercaseChar()
                    val fmt = net.minecraft.ChatFormatting.getByCode(code)
                    if (fmt != null) {
                        currentStyle = if (fmt == net.minecraft.ChatFormatting.RESET) {
                            net.minecraft.network.chat.Style.EMPTY
                        } else {
                            currentStyle.applyLegacyFormat(fmt)
                        }
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

        fun hasModInstalled(uuid: UUID) = uuid in modPlayers
        fun isPhotoBanned(uuid: UUID) = uuid in bannedPlayers
        fun isMuted(uuid: UUID) = uuid in mutedPlayers

        /** Проверяет мут через BanHammer если он установлен (опциональная зависимость) */
        fun isBanHammerMuted(uuid: UUID): Boolean {
            return try {
                val clazz = Class.forName("eu.pb4.banhammer.api.BanHammer")
                val ptClazz = Class.forName("eu.pb4.banhammer.api.PunishmentType")
                val muteConst = ptClazz.getField("MUTE").get(null)
                val method = clazz.getMethod("isPunished", UUID::class.java, ptClazz)
                method.invoke(null, uuid, muteConst) as Boolean
            } catch (_: ClassNotFoundException) { false }
              catch (_: Exception) { false }
        }

        /** Итоговая проверка: заблокированы ли фото+ответы (мут нашим модом ИЛИ BanHammer) */
        fun isEffectivelyMuted(uuid: UUID) = isMuted(uuid) || isBanHammerMuted(uuid)

        // ── Персистентный бан-лист ────────────────────────────────────────────

        fun loadBanList(serverDir: Path) {
            val file = serverDir.resolve("config/chat-remastered-bans.json")
            banListFile = file
            if (!file.exists()) return
            try {
                val arr = GSON.fromJson(file.readText(), com.google.gson.JsonArray::class.java) ?: return
                bannedPlayers.clear()
                arr.forEach { el ->
                    val uuidStr = el.asJsonObject?.get("uuid")?.asString ?: return@forEach
                    try { bannedPlayers.add(UUID.fromString(uuidStr)) } catch (_: Exception) {}
                }
                println("[Chat Remastered] Загружен бан-лист: ${bannedPlayers.size} игроков")
            } catch (e: Exception) {
                println("[Chat Remastered] Ошибка загрузки бан-листа: ${e.message}")
            }
        }

        fun saveBanList(server: net.minecraft.server.MinecraftServer) {
            val file = banListFile ?: return
            try {
                file.parent?.createDirectories()
                val arr = com.google.gson.JsonArray()
                bannedPlayers.forEach { uuid ->
                    val name = server.playerList.getPlayer(uuid)?.name?.string ?: uuid.toString()
                    arr.add(com.google.gson.JsonObject().apply {
                        addProperty("uuid", uuid.toString())
                        addProperty("name", name)
                    })
                }
                file.writeText(GSON.newBuilder().setPrettyPrinting().create().toJson(arr))
            } catch (e: Exception) {
                println("[Chat Remastered] Ошибка сохранения бан-листа: ${e.message}")
            }
        }

        fun loadMuteList(serverDir: Path) {
            val file = serverDir.resolve("config/chat-remastered-mutes.json")
            muteListFile = file
            if (!file.exists()) return
            try {
                val arr = GSON.fromJson(file.readText(), com.google.gson.JsonArray::class.java) ?: return
                mutedPlayers.clear()
                arr.forEach { el ->
                    val uuidStr = el.asJsonObject?.get("uuid")?.asString ?: return@forEach
                    try { mutedPlayers.add(UUID.fromString(uuidStr)) } catch (_: Exception) {}
                }
                println("[Chat Remastered] Загружен мут-лист: ${mutedPlayers.size} игроков")
            } catch (e: Exception) {
                println("[Chat Remastered] Ошибка загрузки мут-листа: ${e.message}")
            }
        }

        fun saveMuteList(server: net.minecraft.server.MinecraftServer) {
            val file = muteListFile ?: return
            try {
                file.parent?.createDirectories()
                val arr = com.google.gson.JsonArray()
                mutedPlayers.forEach { uuid ->
                    val name = server.playerList.getPlayer(uuid)?.name?.string ?: uuid.toString()
                    arr.add(com.google.gson.JsonObject().apply {
                        addProperty("uuid", uuid.toString())
                        addProperty("name", name)
                    })
                }
                file.writeText(GSON.toJson(arr))
            } catch (e: Exception) {
                println("[Chat Remastered] Ошибка сохранения мут-листа: ${e.message}")
            }
        }

        fun mutePlayer(uuid: UUID, server: net.minecraft.server.MinecraftServer) {
            val player = server.playerList.getPlayer(uuid)
            val usedBanHammer = try {
                val bhClass  = Class.forName("eu.pb4.banhammer.api.BanHammer")
                val ptClass  = Class.forName("eu.pb4.banhammer.api.PunishmentType")
                val muteType = ptClass.getField("MUTE").get(null)
                val pdClass  = Class.forName("eu.pb4.banhammer.api.PunishmentData")
                val playerName    = player?.gameProfile?.name ?: uuid.toString()
                val playerIp      = player?.ipAddress ?: ""
                val playerDisplay = player?.displayName ?: net.minecraft.network.chat.Component.literal(playerName)
                val adminSource   = server.createCommandSourceStack()
                // PunishmentData.create(UUID, ip, displayName, playerName, CommandSourceStack, reason, duration, type)
                val createMethod  = pdClass.getMethod(
                    "create",
                    UUID::class.java,
                    String::class.java,
                    net.minecraft.network.chat.Component::class.java,
                    String::class.java,
                    net.minecraft.commands.CommandSourceStack::class.java,
                    String::class.java,
                    Long::class.javaPrimitiveType,
                    ptClass
                )
                val punishData = createMethod.invoke(
                    null, uuid, playerIp, playerDisplay, playerName,
                    adminSource, "Muted via Chat Remastered", -1L, muteType
                )
                // silent=false — BanHammer сам покажет mute-сообщение игроку
                val punishMethod = bhClass.getMethod("punish",
                    Class.forName("eu.pb4.banhammer.api.PunishmentData"), Boolean::class.javaPrimitiveType)
                punishMethod.invoke(null, punishData, false)
                bhMutedByUs.add(uuid)
                true
            } catch (_: ClassNotFoundException) { false }
              catch (e: Exception) {
                println("[Chat Remastered] BanHammer mute error: ${e.message}")
                false
            }

            if (!usedBanHammer) {
                // Fallback без BanHammer — используем свой список и уведомляем клиента сами
                mutedPlayers.add(uuid)
                saveMuteList(server)
                println("[Chat Remastered] Muted $uuid (fallback, no BanHammer)")
                server.execute {
                    player?.let { ServerPlayNetworking.send(it, PhotoDeniedPacket("muted")) }
                }
            }
            // Если BH установлен — PhotoDeniedPacket придёт из onPunishment-события
        }
        fun unmutePlayer(uuid: UUID, server: net.minecraft.server.MinecraftServer) {
            val player = server.playerList.getPlayer(uuid)
            // Снимаем через BanHammer если установлен
            try {
                val bhClass  = Class.forName("eu.pb4.banhammer.api.BanHammer")
                val ptClass  = Class.forName("eu.pb4.banhammer.api.PunishmentType")
                val muteType = ptClass.getField("MUTE").get(null)
                val removeMethod = bhClass.getMethod("removePunishment", UUID::class.java, ptClass)
                removeMethod.invoke(null, uuid, muteType)
                bhMutedByUs.remove(uuid)
            } catch (_: ClassNotFoundException) {}
              catch (e: Exception) {
                println("[Chat Remastered] BanHammer unmute error: ${e.message}")
            }
            // Снимаем из своего списка (fallback-мут)
            mutedPlayers.remove(uuid)
            saveMuteList(server)
            // Если не забанен — восстанавливаем токен и уведомляем клиента
            if (!isPhotoBanned(uuid)) {
                val newToken = java.util.UUID.randomUUID().toString()
                playerTokens[uuid] = newToken
                ImageHttpServer.addToken(newToken)
                server.execute {
                    player?.let { ServerPlayNetworking.send(it, PhotoUnbannedPacket(newToken)) }
                }
            }
        }
        fun banPlayer(uuid: UUID, server: net.minecraft.server.MinecraftServer) {
            bannedPlayers.add(uuid)
            // Отзываем токен — TCP upload вернёт forbidden
            playerTokens[uuid]?.let { token -> ImageHttpServer.removeToken(token) }
            saveBanList(server)
        }

        fun unbanPlayer(uuid: UUID, server: net.minecraft.server.MinecraftServer) {
            bannedPlayers.remove(uuid)
            // Выдаём новый токен и добавляем в TCP сервер
            val newToken = java.util.UUID.randomUUID().toString()
            playerTokens[uuid] = newToken
            ImageHttpServer.addToken(newToken)
            saveBanList(server)
            // Уведомляем клиента — он сбросит isBanned и получит свежий uploadToken
            server.execute {
                server.playerList.getPlayer(uuid)?.let { player ->
                    ServerPlayNetworking.send(player, PhotoUnbannedPacket(newToken))
                }
            }
        }

        data class ServerConfig(
            val resolution: String,
            val imagePort: Int,
            val autoDownload: Boolean,
            val photoCooldownSeconds: Int,
            val gifEnabled: Boolean,
            val gifMaxDim: Int,         // максимальное разрешение GIF при отправке (240..1920)
            val mutedMessage: String    // сообщение игроку при попытке написать/отправить фото/ответ когда замучен
        )

        fun loadOrCreateServerConfig(serverDir: Path): ServerConfig {
            val configFile = serverDir.resolve("config/chat-remastered-server.json")
            val currentConfigVersion = 1

            val defaults = ServerConfig(
                resolution = "720",
                imagePort = 5050,
                autoDownload = false,
                photoCooldownSeconds = 5,
                gifEnabled = true,
                gifMaxDim = 480,
                mutedMessage = "You are muted and cannot send messages, photos or replies."
            )

            if (!configFile.exists()) {
                try {
                    configFile.parent.createDirectories()
                    val default = JsonObject().apply {
                        addProperty("configVersion", currentConfigVersion)
                        addProperty("_comment_resolution", "Image resolution: 360 | 480 | 720 | HD | 2K")
                        addProperty("resolution", "720")
                        addProperty("_comment_imagePort",
                            "TCP server port for images. Open it in firewall! " +
                            "If port is busy — change imagePort here and restart.")
                        addProperty("imagePort", 5050)
                        addProperty("_comment_autoDownload",
                            "If true — clients automatically download full image in background when received. " +
                            "Default: false (client opens image manually).")
                        addProperty("autoDownload", false)
                        addProperty("_comment_photoCooldownSeconds",
                            "Cooldown in seconds between photo sends per player. 0 = no cooldown. Default: 5.")
                        addProperty("photoCooldownSeconds", 5)
                        addProperty("_comment_gifEnabled",
                            "If true — players can send animated GIFs. Default: true.")
                        addProperty("gifEnabled", true)
                        addProperty("_comment_gifMaxDim",
                            "Maximum GIF resolution (width/height) players can upload. Range: 240..1920. Default: 480.")
                        addProperty("gifMaxDim", 480)
                        addProperty("_comment_mutedMessage",
                            "Message sent to a muted player when they try to send a chat message, photo or reply. Supports UTF-8.")
                        addProperty("mutedMessage", "You are muted and cannot send messages, photos or replies.")
                    }
                    configFile.parent.createDirectories()
                    java.nio.file.Files.writeString(configFile.toAbsolutePath(), GSON.newBuilder().setPrettyPrinting().create().toJson(default), Charsets.UTF_8)
                    println("[Chat Remastered] Config created: ${configFile.toAbsolutePath()}")
                    println("[Chat Remastered] TCP port: 5050. If busy — change imagePort in config.")
                    return defaults
                } catch (e: Exception) {
                    println("[Chat Remastered] Could not create config: ${e.message}")
                    return defaults
                }
            }

            return try {
                val json = GSON.fromJson(configFile.readText(), JsonObject::class.java)

                // Автомиграция старых версий конфига
                val configVersion = json.get("configVersion")?.asInt ?: 1
                if (configVersion < currentConfigVersion) {
                    println("[Chat Remastered] Config v$configVersion → v$currentConfigVersion: migrating")
                    if (!json.has("gifEnabled")) json.addProperty("gifEnabled", true)
                    if (!json.has("gifMaxDim")) json.addProperty("gifMaxDim", 480)
                    json.addProperty("_comment_resolution", "Image resolution: 360 | 480 | 720 | HD | 2K")
                    json.addProperty("configVersion", currentConfigVersion)
                    try { java.nio.file.Files.writeString(configFile.toAbsolutePath(), GSON.newBuilder().setPrettyPrinting().create().toJson(json), Charsets.UTF_8) } catch (_: Exception) {}
                }

                val res = json.get("resolution")?.asString?.trim()
                    .let { if (it == "360" || it == "480" || it == "720" || it == "HD" || it == "2K") it else "720" }!!
                val port = json.get("imagePort")?.asInt?.takeIf { it in 1024..65535 } ?: 5050
                val autoDownload = json.get("autoDownload")?.asBoolean ?: false
                val cooldown = json.get("photoCooldownSeconds")?.asInt?.coerceAtLeast(0) ?: 5
                val gifEnabled = json.get("gifEnabled")?.asBoolean ?: true
                val gifMaxDim = json.get("gifMaxDim")?.asInt?.coerceIn(240, 1920) ?: 480
                val mutedMsg = json.get("mutedMessage")?.asString
                    ?.takeIf { it.isNotBlank() }
                    ?: "You are muted and cannot send messages, photos or replies."

                // Автодобавление поля если конфиг старый
                if (!json.has("mutedMessage")) {
                    json.addProperty("_comment_mutedMessage",
                        "Message sent to a muted player when they try to send a chat message, photo or reply. Supports UTF-8.")
                    json.addProperty("mutedMessage", mutedMsg)
                    try { java.nio.file.Files.writeString(configFile.toAbsolutePath(), GSON.newBuilder().setPrettyPrinting().create().toJson(json), Charsets.UTF_8) } catch (_: Exception) {}
                }

                ServerConfig(res, port, autoDownload, cooldown, gifEnabled, gifMaxDim, mutedMsg)
            } catch (e: Exception) {
                println("[Chat Remastered] Config read error: ${e.message}. Using defaults.")
                defaults
            }
        }
    }

    override fun onInitialize() {
        // ── Блокировка чат-сообщений замученных игроков ───────────────────────
        // Сообщение клиенту уже показано один раз при получении PhotoDeniedPacket("muted").
        // Здесь только тихо блокируем — без повторного уведомления.
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
            net.fabricmc.fabric.api.message.v1.ServerMessageEvents.AllowChatMessage { _, sender, _ ->
                val uuid = sender.uuid
                when {
                    // Замучен через BanHammer (нашим модом или напрямую через BH).
                    // BH сам заблокирует сообщение и покажет своё уведомление игроку.
                    // Шлём "muted_silent" — клиент поставит флаг isMuted без дублирования текста.
                    isBanHammerMuted(uuid) -> {
                        ServerPlayNetworking.send(sender, PhotoDeniedPacket("muted_silent"))
                        false
                    }
                    // Замучен только нашим модом (BH не установлен или не участвует).
                    // Показываем наше уведомление.
                    isMuted(uuid) -> {
                        ServerPlayNetworking.send(sender, PhotoDeniedPacket("muted"))
                        false
                    }
                    else -> true
                }
            }
        )

        ImageChatPacket.register()
        ServerHelloPacket.register()
        ClientHelloPacket.register()
        ServerConfigPacket.register()
        HandshakeErrorPacket.register()
        ImageUploadedPacket.register()
        PhotoDeniedPacket.register()
        ImageDeletedPacket.register()
        DeleteImagePacket.register()
        ImageErrorPacket.register()
        PhotoUnbannedPacket.register()
        ReplyPacket.register()
        ReplyMetaPacket.register()
        ReplyChatPacket.register()

        // ─── onImageReady: TCP загрузка завершена ────────────────────────────────
        // Рассылка уже была сделана мгновенно при ImageUploadedPacket.
        // Здесь просто очищаем очередь (отменяем таймаут) и evictOld.
        ImageHttpServer.onImageReady = handler@{ imageId ->
            pendingBroadcasts.remove(imageId) ?: return@handler
            ImageHttpServer.evictOld(100)
        }

        // ─── Создаём конфиг и стартуем TCP сервер при запуске ────────────────
        // Сервер доступен на net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register { server ->
            currentServer = server
            val config = loadOrCreateServerConfig(server.serverDirectory)
            cachedConfig = config
            loadBanList(server.serverDirectory)
                loadMuteList(server.serverDirectory)

                // ── Интеграция с BanHammer (опциональная зависимость) ──────────────
                try {
                    val bhClass = Class.forName("eu.pb4.banhammer.api.BanHammer")
                    val ptClass = Class.forName("eu.pb4.banhammer.api.PunishmentType")
                    val muteType = ptClass.getField("MUTE").get(null)
                    val eventIface = Class.forName("eu.pb4.banhammer.api.BanHammer\$PunishmentEvent")
                    val registerMethod = bhClass.getMethod("registerPunishmentEvent", eventIface)
                    val proxy = java.lang.reflect.Proxy.newProxyInstance(
                        eventIface.classLoader, arrayOf(eventIface)
                    ) { _, method, args ->
                        if (method.name == "onPunishment" && args != null && args.size >= 1) {
                            val data = args[0]
                            val typeField = data.javaClass.getField("type")
                            val punishType = typeField.get(data)
                            if (punishType == muteType) {
                                val uuidField = data.javaClass.getField("playerUUID")
                                val uuid = uuidField.get(data) as? UUID
                                if (uuid != null) {
                                    // BanHammer замутил — добавляем в трекинг (для tick-размута)
                                    // и уведомляем клиента Chat Remastered без текста (BH покажет своё)
                                    bhMutedByUs.add(uuid)
                                    server.execute {
                                        server.playerList.getPlayer(uuid)?.let { player ->
                                            // "muted_silent" = только флаг на клиенте, без текстового сообщения
                                            ServerPlayNetworking.send(player, PhotoDeniedPacket("muted_silent"))
                                        }
                                    }
                                }
                            }
                        }
                        null
                    }
                    registerMethod.invoke(null, proxy)
                    println("[Chat Remastered] BanHammer интеграция активна")
                } catch (_: ClassNotFoundException) {
                    println("[Chat Remastered] BanHammer не найден — интеграция отключена")
                } catch (e: Exception) {
                    println("[Chat Remastered] Ошибка BanHammer интеграции: ${e.message}")
                }
            // Инициализируем дисковый кэш (очищаем при старте)
            ImageHttpServer.initCacheDir(server.serverDirectory.toFile())
            // Устанавливаем лимиты загрузки
            ImageHttpServer.maxUploadBytes = 8L * 1024 * 1024
            ImageHttpServer.startIfNeeded(config.imagePort)
            println("[Chat Remastered] TCP сервер запущен при старте на порту ${config.imagePort}")
        }

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            currentServer = null
            pendingBroadcasts.clear()
            ImageHttpServer.stop()
        }

        // ─── Tick-опрос снятия мута BanHammer (каждые 100 тиков = 5 сек) ────────
        // BanHammer не имеет события на снятие мута, поэтому опрашиваем isPunished().
        // Когда /unmute снимает мут через BH — мы это замечаем здесь и шлём PhotoUnbannedPacket.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % 100 != 0) return@register
            val bhPresent = try {
                Class.forName("eu.pb4.banhammer.api.BanHammer")
                true
            } catch (_: ClassNotFoundException) { false }
            if (!bhPresent) return@register

            // Итерируемся только по игрокам, которых МЫ замутили через BH.
            // Покрывает оба случая: ручной /unmute через BH и истечение временного мута.
            val iterator = bhMutedByUs.iterator()
            while (iterator.hasNext()) {
                val uuid = iterator.next()
                // BH ещё держит мут — пропускаем
                if (isBanHammerMuted(uuid)) continue
                // BH снял мут (вручную или по таймеру) — убираем из трекинга
                iterator.remove()
                // Снимаем fallback-запись на случай если был и в нашем списке
                val wasInOurList = mutedPlayers.remove(uuid)
                if (wasInOurList) saveMuteList(server)
                val player = server.playerList.getPlayer(uuid) ?: continue
                if (!isPhotoBanned(uuid)) {
                    val newToken = java.util.UUID.randomUUID().toString()
                    playerTokens[uuid] = newToken
                    ImageHttpServer.addToken(newToken)
                    ServerPlayNetworking.send(player, PhotoUnbannedPacket(newToken))
                    println("[Chat Remastered] BanHammer unmute detected for \${player.name.string} — sent PhotoUnbannedPacket")
                }
            }
        }

        // ─── Команды ─────────────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("chat-remastered-admin")
                    // Только операторы: проверяем через playerList.isOp
                    // getServer() может быть null при построении дерева команд — защищаемся
                    .requires { src ->
                        val server = src.server ?: return@requires false
                        val p = src.getPlayer()
                        if (p != null) server.playerList.isOp(p.nameAndId())
                        else true // консоль сервера
                    }
                    .then(
                        Commands.literal("block-photo")
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes { ctx ->
                                        val target = EntityArgument.getPlayer(ctx, "player")
                                        banPlayer(target.uuid, ctx.source.server)
                                        ServerPlayNetworking.send(target, PhotoDeniedPacket("banned"))
                                        ctx.source.sendSuccess({
                                            Component.literal("§8[Chat Remastered] §c${target.name.string} §7— отправка фото заблокирована.")
                                        }, true)
                                        1
                                    }
                            )
                    )
                    .then(
                        Commands.literal("unblock-photo")
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes { ctx ->
                                        val target = EntityArgument.getPlayer(ctx, "player")
                                        unbanPlayer(target.uuid, ctx.source.server)
                                        ctx.source.sendSuccess({
                                            Component.literal("§8[Chat Remastered] §a${target.name.string} §7— отправка фото разблокирована.")
                                        }, true)
                                        1
                                    }
                            )
                    )
                    .then(
                        Commands.literal("mute")
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes { ctx ->
                                        val target = EntityArgument.getPlayer(ctx, "player")
                                        mutePlayer(target.uuid, ctx.source.server)
                                        ctx.source.sendSuccess({
                                            Component.literal("§8[Chat Remastered] §c${target.name.string} §7— фото и ответы заблокированы.")
                                        }, true)
                                        1
                                    }
                            )
                    )
                    .then(
                        Commands.literal("unmute")
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes { ctx ->
                                        val target = EntityArgument.getPlayer(ctx, "player")
                                        unmutePlayer(target.uuid, ctx.source.server)
                                        ctx.source.sendSuccess({
                                            Component.literal("§8[Chat Remastered] §a${target.name.string} §7— фото и ответы разблокированы.")
                                        }, true)
                                        1
                                    }
                            )
                    )
                    .then(
                        Commands.literal("delete")
                            .then(
                                Commands.argument("imageId", StringArgumentType.word())
                                    .executes { ctx ->
                                        val imageId = StringArgumentType.getString(ctx, "imageId")
                                        val server = ctx.source.server
                                        ImageHttpServer.deleteImage(imageId)
                                        val packet = ImageDeletedPacket(imageId)
                                        server.execute {
                                            server.playerList.players.forEach { player ->
                                                if (hasModInstalled(player.uuid)) {
                                                    ServerPlayNetworking.send(player, packet)
                                                }
                                            }
                                        }
                                        ctx.source.sendSuccess({
                                            Component.literal("§8[Chat Remastered] §7Фото §f$imageId §7удалено.")
                                        }, true)
                                        1
                                    }
                            )
                    )
                    .then(
                        // /chat-remastered test <player> — проверить подключение игрока к моду
                        Commands.literal("test")
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes { ctx ->
                                        val target = EntityArgument.getPlayer(ctx, "player")
                                        val targetName = target.name.string
                                        val hasMod = hasModInstalled(target.uuid)
                                        val isBanned = isPhotoBanned(target.uuid)
                                        val token = playerTokens[target.uuid]

                                        ctx.source.sendSuccess({
                                            Component.literal("§8[Chat Remastered] §7--- Проверка игрока §f$targetName §7---")
                                        }, false)
                                        ctx.source.sendSuccess({
                                            if (hasMod)
                                                Component.literal("§8[Chat Remastered] §aMod: §aустановлен ✔")
                                            else
                                                Component.literal("§8[Chat Remastered] §cMod: §cне установлен ✘")
                                        }, false)
                                        ctx.source.sendSuccess({
                                            if (isBanned)
                                                Component.literal("§8[Chat Remastered] §cБан: §cзаблокирован ✘")
                                            else
                                                Component.literal("§8[Chat Remastered] §aБан: §aнет ✔")
                                        }, false)
                                        ctx.source.sendSuccess({
                                            if (!token.isNullOrEmpty())
                                                Component.literal("§8[Chat Remastered] §aТокен: §fполучен (${token.length} символов)")
                                            else
                                                Component.literal("§8[Chat Remastered] §cТокен: §cне выдан")
                                        }, false)
                                        1
                                    }
                            )
                    )
            )
        }

        // ─── Команда /chat-remastered — плашка мода ──────────────────────────
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("chat-remastered")
                    .executes { ctx ->
                        val src = ctx.source
                        val mcVersion = try {
                            net.fabricmc.loader.api.FabricLoader.getInstance()
                                .getModContainer("minecraft")
                                .map { it.metadata.version.friendlyString }
                                .orElse("?")
                        } catch (_: Exception) { "?" }
                        val fullVersion = "$MOD_VERSION+$mcVersion"

                        src.sendSystemMessage(
                            net.minecraft.network.chat.Component.empty()
                                .append(net.minecraft.network.chat.Component.literal("Chat Remastered").withStyle { it.withColor(0xFF6B00).withBold(true) })
                                .append(net.minecraft.network.chat.Component.literal("  Version: ").withStyle(net.minecraft.ChatFormatting.GRAY))
                                .append(net.minecraft.network.chat.Component.literal(fullVersion).withStyle(net.minecraft.ChatFormatting.AQUA))
                                .append(net.minecraft.network.chat.Component.literal("  Protocol: V$MOD_PROTOCOL_VERSION").withStyle(net.minecraft.ChatFormatting.AQUA))
                        )
                        1
                    }
            )
        }

        // ─── Шаг 2 handshake: клиент ответил на ServerHello ──────────────────
        ServerPlayNetworking.registerGlobalReceiver(ClientHelloPacket.TYPE) { payload, context ->
            val player = context.player()
            val server = context.server()
            val clientVersion = payload.clientProtocolVersion

            if (clientVersion != MOD_PROTOCOL_VERSION) {
                ServerPlayNetworking.send(
                    player,
                    HandshakeErrorPacket(
                        "§cВерсия мода несовместима: сервер v$MOD_PROTOCOL_VERSION, клиент v$clientVersion. Обновите мод."
                    )
                )
                println("[Chat Remastered] ${player.name.string} has incompatible mod version: client v$clientVersion, server v$MOD_PROTOCOL_VERSION")
                return@registerGlobalReceiver
            }

            modPlayers.add(player.uuid)
            val uploadToken = UUID.randomUUID().toString()
            playerTokens[player.uuid] = uploadToken
            // Если игрок забанен — не добавляем токен в TCP сервер (upload будет forbidden)
            if (!isPhotoBanned(player.uuid)) {
                ImageHttpServer.addToken(uploadToken)
            }

            val config = loadOrCreateServerConfig(server.serverDirectory)
            ServerPlayNetworking.send(
                player,
                ServerConfigPacket(config.resolution, config.imagePort, uploadToken, config.autoDownload,
                    config.photoCooldownSeconds, config.gifEnabled, config.gifMaxDim)
            )
            // Если игрок забанен — сразу уведомляем клиента
            if (isPhotoBanned(player.uuid)) {
                ServerPlayNetworking.send(player, PhotoDeniedPacket("banned"))
            } else if (isEffectivelyMuted(player.uuid)) {
                ServerPlayNetworking.send(player, PhotoDeniedPacket("muted"))
            }
            println("[Chat Remastered] Handshake complete with ${player.name.string} (protocol v$clientVersion)")
        }

        // ─── Клиент загрузил фото ─────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(ImageUploadedPacket.TYPE) { payload, context ->
            if (!hasModInstalled(context.player().uuid)) {
                println("[Chat Remastered] ${context.player().name.string} sent ImageUploadedPacket without handshake — ignoring")
                return@registerGlobalReceiver
            }

            // Проверяем бан на отправку
            if (isPhotoBanned(context.player().uuid)) {
                println("[Chat Remastered] ${context.player().name.string} tried to send photo but is banned")
                ServerPlayNetworking.send(
                    context.player(),
                    PhotoDeniedPacket("banned")
                )
                return@registerGlobalReceiver
            }
            // Проверяем мут (свой + BanHammer)
            if (isEffectivelyMuted(context.player().uuid)) {
                println("[Chat Remastered] ${context.player().name.string} tried to send photo but is muted")
                return@registerGlobalReceiver
            }

            val server = context.server()
            val imageId = payload.imageId
            // Берём имя напрямую с сервера — игнорируем sender из пакета клиента.
            // displayName несёт цвета и кастомный ник от других модов (LuckPerms и т.д.)
            val senderPlayer = context.player()
            val sender = senderPlayer.name.string
            val rawComp = senderPlayer.displayName ?: net.minecraft.network.chat.Component.literal(sender)
            val senderComp = parseLegacyNick(rawComp, sender)
            val caption = payload.caption
            val width = payload.width
            val height = payload.height
            val senderUuid = senderPlayer.uuid

            // Отправляем disguised chat-сообщение — выглядит как обычный чат игрока,
            // не требует подписи и не ломает цепочку верификации на клиентах.
            val chatText = if (!caption.isNullOrEmpty()) caption else "[photo]"
            server.execute {
                // sendDisguisedChatMessage с plain именем в ChatType.Bound.
                // Chat Heads перехватывает disguised chat и добавляет голову через эвристику
                // (ищет <НикИгрока> в тексте) — это работает корректно.
                // Если передать displayName с головой в Bound — chat heads добавит голову дважды.
                val chatType = net.minecraft.network.chat.ChatType.bind(
                    net.minecraft.network.chat.ChatType.CHAT,
                    senderPlayer.level().registryAccess(),
                    net.minecraft.network.chat.Component.literal(sender)  // plain ник без голов
                )
                server.playerList.players.forEach { p: ServerPlayer ->
                    // Игрокам с модом ванильное сообщение не нужно — они получат ImageChatPacket
                    if (!hasModInstalled(p.uuid)) {
                        p.connection.sendDisguisedChatMessage(
                            net.minecraft.network.chat.Component.literal(chatText),
                            chatType
                        )
                    }
                }
            }

            // Рассылаем ImageChatPacket игрокам с модом (кроме отправителя) —
            // они покажут превью вместо текстового сообщения.
            val packet = ImageChatPacket(imageId, sender, caption, width, height, senderComp)
            server.execute {
                server.playerList.players.forEach { player: ServerPlayer ->
                    if (hasModInstalled(player.uuid) && player.uuid != senderUuid) {
                        ServerPlayNetworking.send(player, packet)
                    }
                }
            }

            // Ставим в очередь — onImageReady не нужен для рассылки,
            // но таймаут нужен чтобы уведомить отправителя об ошибке TCP.
            pendingBroadcasts[imageId] = PendingBroadcast(sender, caption, senderUuid, width, height)
            imageOwners[imageId] = senderUuid  // запоминаем владельца для проверки при удалении

            // onImageReady просто очищает pendingBroadcasts и evictOld — рассылка уже сделана выше.
            // Таймаут 120 сек на случай если TCP соединение вообще не установилось.
            Thread {
                Thread.sleep(120_000L)
                val stillPending = pendingBroadcasts.remove(imageId) ?: return@Thread
                // Двойная проверка: файл мог прийти по TCP прямо перед таймаутом (гонка потоков).
                // Если файл уже есть на сервере — не отправляем ошибку.
                if (ImageHttpServer.hasCached(imageId)) {
                    println("[Chat Remastered] Timeout race resolved: $imageId already cached, skipping error")
                    return@Thread
                }
                println("[Chat Remastered] Timeout (120s) waiting for upload $imageId from ${stillPending.sender}")
                server.execute {
                    server.playerList.getPlayer(stillPending.senderUuid)?.let { senderPlayer ->
                        ServerPlayNetworking.send(senderPlayer, ImageErrorPacket(imageId, "timeout"))
                    }
                }
            }.also { it.isDaemon = true }.start()
        }

        // ─── Reply: один пакет C→S со всем (текст + метаданные) ─────────────
        // Клиент НЕ вызывает sendChat() — сервер сам рассылает сообщение.
        // Игрокам с модом → ReplyChatPacket, без мода → системное сообщение.
        ServerPlayNetworking.registerGlobalReceiver(ReplyMetaPacket.TYPE) { payload, context ->
            if (!hasModInstalled(context.player().uuid)) return@registerGlobalReceiver
            val player = context.player()
            // Проверяем мут (свой + BanHammer)
            if (isEffectivelyMuted(player.uuid)) {
                println("[Chat Remastered] ${player.name.string} tried to send reply but is muted")
                // Отправляем PhotoDeniedPacket — клиент покажет локализованный текст сам
                ServerPlayNetworking.send(player, PhotoDeniedPacket("muted"))
                return@registerGlobalReceiver
            }
            val senderName = player.name.string
            val rawComp = player.displayName ?: net.minecraft.network.chat.Component.literal(senderName)
            val senderComp = parseLegacyNick(rawComp, senderName)
            val text = payload.text.take(256)
            val replyPacket = ReplyChatPacket(
                sender          = senderName,
                senderComponent = senderComp,
                text            = text,
                replyToSender   = payload.replyToSender,
                replyToText     = payload.replyToText,
                replyToImageId  = payload.replyToImageId
            )
            val plainMsg = net.minecraft.network.chat.Component.empty()
                .append(net.minecraft.network.chat.Component.literal("<"))
                .append(senderComp)
                .append(net.minecraft.network.chat.Component.literal("> $text"))
            context.server().playerList.players.forEach { p ->
                if (p.getChatVisibility() == net.minecraft.world.entity.player.ChatVisiblity.FULL) {
                    if (hasModInstalled(p.uuid)) {
                        ServerPlayNetworking.send(p, replyPacket)
                    } else {
                        p.sendSystemMessage(plainMsg)
                    }
                }
            }
        }

        // ─── DeleteImagePacket (C→S) — игрок удаляет своё фото ───────────────────
        ServerPlayNetworking.registerGlobalReceiver(DeleteImagePacket.TYPE) { payload, context ->
            val player = context.player()
            val imageId = payload.imageId
            // Проверяем что imageId принадлежит этому игроку
            val owner = imageOwners[imageId]
            if (owner == null || owner != player.uuid) {
                // Не владелец — молча игнорируем
                return@registerGlobalReceiver
            }
            // Удаляем
            imageOwners.remove(imageId)
            ImageHttpServer.deleteImage(imageId)
            // Рассылаем всем игрокам с модом
            val packet = ImageDeletedPacket(imageId)
            context.server().execute {
                context.server().playerList.players.forEach { p ->
                    if (hasModInstalled(p.uuid)) {
                        ServerPlayNetworking.send(p, packet)
                    }
                }
            }
        }

        // ─── JOIN ─────────────────────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.getPlayer()

            Thread {
                // Небольшая задержка — клиент должен полностью загрузить мир
                // прежде чем сможет принимать кастомные пакеты.
                // Без задержки ServerHelloPacket приходит слишком рано и дропается,
                // из-за чего handshake не проходит и игрок с модом получает сообщение
                // "установите мод".
                Thread.sleep(2000L)
                server.execute {
                    ServerPlayNetworking.send(player, ServerHelloPacket(MOD_PROTOCOL_VERSION))
                }

                // Ждём завершения handshake (до 10 секунд)
                Thread.sleep(10_000L)
                if (!hasModInstalled(player.uuid)) {
                    server.execute {
                        val modrinthUrl = "https://modrinth.com/mod/chat-remastered"
                        val msg = Component.empty()
                            .append(Component.literal("[").withStyle { it.withColor(0x555555) })
                            .append(Component.literal("Chat Remastered").withStyle { it.withColor(0x00b0f0).withBold(true) })
                            .append(Component.literal("] ").withStyle { it.withColor(0x555555) })
                            .append(Component.literal("This server has Chat Remastered — an enhanced chat mod with photos, replies and more. ").withStyle { it.withColor(0xaaaaaa) })
                            .append(
                                Component.literal("⬇ Download here").withStyle { s ->
                                    s.withColor(0x1bd96a)
                                     .withBold(true)
                                     .withUnderlined(true)
                                     .withClickEvent(net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(modrinthUrl)))
                                     .withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(modrinthUrl).withStyle { it.withColor(0x888888) }))
                                }
                            )
                        player.sendSystemMessage(msg)
                    }
                }
            }.also { it.isDaemon = true }.start()
        }

        // ─── DISCONNECT ───────────────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val uuid = handler.getPlayer().uuid
            modPlayers.remove(uuid)
            playerTokens.remove(uuid)?.let { token ->
                ImageHttpServer.removeToken(token)
            }
        }
    }
}
