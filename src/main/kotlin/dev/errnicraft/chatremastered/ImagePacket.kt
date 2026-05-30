package dev.errnicraft.chatremastered

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

// ─── MOD VERSION для handshake ────────────────────────────────────────────────
// Увеличивай при несовместимых изменениях протокола
const val MOD_PROTOCOL_VERSION = "1"   // протокол (инкрементируем при breaking-change)
// Версия берётся из fabric.mod.json (gradle.properties) динамически
val MOD_VERSION: String by lazy {
    try {
        net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("chat-remastered")
            .map { it.metadata.version.friendlyString }
            .orElse("?.?.?")
    } catch (_: Exception) { "?.?.?" }
}

// ─── Пакет с изображением (S→C) ──────────────────────────────────────────────
// thumbnailData убран: клиент сам качает PNG по TCP и генерирует превью.
// width/height передаются чтобы сразу показать плейсхолдер правильного размера.
data class ImageChatPacket(
    val imageId: String,
    val sender: String,       // plain-имя для обратной совместимости
    val caption: String,
    val width: Int,
    val height: Int,
    val senderComponent: net.minecraft.network.chat.Component = net.minecraft.network.chat.Component.literal("")
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ImageChatPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "image_chat")
        val TYPE = CustomPacketPayload.Type<ImageChatPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ImageChatPacket> =
            StreamCodec.of(
                { buf, pkt ->
                    buf.writeUtf(pkt.imageId)
                    buf.writeUtf(pkt.sender)
                    buf.writeUtf(pkt.caption)
                    buf.writeInt(pkt.width)
                    buf.writeInt(pkt.height)
                    net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, pkt.senderComponent)
                },
                { buf ->
                    ImageChatPacket(
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readInt(),
                        buf.readInt(),
                        net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf)
                    )
                }
            )

        fun register() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── Шаг 1: ServerHello (S→C) ────────────────────────────────────────────────
// Сервер ПЕРВЫМ отправляет этот пакет при входе игрока.
// Клиент без мода просто проигнорирует его (Fabric отклонит неизвестный канал).
// Клиент с модом ответит ClientHelloPacket.
data class ServerHelloPacket(
    val serverProtocolVersion: String  // MOD_PROTOCOL_VERSION сервера
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ServerHelloPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "server_hello")
        val TYPE = CustomPacketPayload.Type<ServerHelloPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ServerHelloPacket> =
            StreamCodec.of(
                { buf, pkt -> buf.writeUtf(pkt.serverProtocolVersion, 16) },
                { buf -> ServerHelloPacket(buf.readUtf(16)) }
            )

        fun register() {
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── Шаг 2: ClientHello (C→S) ────────────────────────────────────────────────
// Клиент отвечает серверу — подтверждает наличие мода и версию протокола.
// Сервер регистрирует игрока как "с модом" только после получения этого пакета.
data class ClientHelloPacket(
    val clientProtocolVersion: String  // MOD_PROTOCOL_VERSION клиента
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ClientHelloPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "client_hello")
        val TYPE = CustomPacketPayload.Type<ClientHelloPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ClientHelloPacket> =
            StreamCodec.of(
                { buf, pkt -> buf.writeUtf(pkt.clientProtocolVersion, 16) },
                { buf -> ClientHelloPacket(buf.readUtf(16)) }
            )

        fun register() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── Шаг 3: ServerConfigPacket (S→C) ─────────────────────────────────────────
// Отправляется только если версии совместимы.
// uploadToken — одноразовый токен сессии.
// Без него TCP-сервер отклонит загрузку.
// Порт передаётся, но токен защищает от случайных запросов.
// autoDownload — если true, клиент сразу качает полный файл фоном при получении пакета.
data class ServerConfigPacket(
    val resolution: String,
    val imagePort: Int,
    val uploadToken: String,
    val autoDownload: Boolean,
    val photoCooldownSeconds: Int,
    val gifEnabled: Boolean,
    val gifMaxDim: Int         // максимальное разрешение GIF при отправке (240..1920)
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ServerConfigPacket> = TYPE

    companion object {
        val ID   = Identifier.fromNamespaceAndPath("chat-remastered", "server_config")
        val TYPE = CustomPacketPayload.Type<ServerConfigPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ServerConfigPacket> =
            StreamCodec.of(
                { buf, pkt ->
                    buf.writeUtf(pkt.resolution, 8)
                    buf.writeInt(pkt.imagePort)
                    buf.writeUtf(pkt.uploadToken, 64)
                    buf.writeBoolean(pkt.autoDownload)
                    buf.writeInt(pkt.photoCooldownSeconds)
                    buf.writeBoolean(pkt.gifEnabled)
                    buf.writeInt(pkt.gifMaxDim)
                },
                { buf ->
                    ServerConfigPacket(
                        buf.readUtf(8),
                        buf.readInt(),
                        buf.readUtf(64),
                        buf.readBoolean(),
                        buf.readInt(),
                        buf.readBoolean(),
                        buf.readInt()
                    )
                }
            )

        fun register() {
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── HandshakeErrorPacket (S→C) ───────────────────────────────────────────────
// Сервер отправляет при несовместимости версий.
// Клиент показывает ошибку в чате.
data class HandshakeErrorPacket(
    val reason: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<HandshakeErrorPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "handshake_error")
        val TYPE = CustomPacketPayload.Type<HandshakeErrorPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, HandshakeErrorPacket> =
            StreamCodec.of(
                { buf, pkt -> buf.writeUtf(pkt.reason, 256) },
                { buf -> HandshakeErrorPacket(buf.readUtf(256)) }
            )

        fun register() {
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── ImageUploadedPacket (C→S) ────────────────────────────────────────────────
// Клиент сообщает серверу что загрузил фото по TCP.
// Сервер делает миниатюру и рассылает ImageChatPacket всем.
data class ImageUploadedPacket(
    val imageId: String,
    val sender: String,
    val caption: String,
    val width: Int,
    val height: Int
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ImageUploadedPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "image_uploaded")
        val TYPE = CustomPacketPayload.Type<ImageUploadedPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ImageUploadedPacket> =
            StreamCodec.of(
                { buf, pkt ->
                    buf.writeUtf(pkt.imageId)
                    buf.writeUtf(pkt.sender)
                    buf.writeUtf(pkt.caption)
                    buf.writeInt(pkt.width)
                    buf.writeInt(pkt.height)
                },
                { buf ->
                    ImageUploadedPacket(
                        buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readInt(), buf.readInt()
                    )
                }
            )

        fun register() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── PhotoDeniedPacket (S→C) ──────────────────────────────────────────────────
// Сервер шлёт клиенту когда тот пытается отправить фото но не имеет права
data class PhotoDeniedPacket(
    val reason: String  // причина на русском/английском
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<PhotoDeniedPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "photo_denied")
        val TYPE = CustomPacketPayload.Type<PhotoDeniedPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PhotoDeniedPacket> =
            StreamCodec.of(
                { buf, pkt -> buf.writeUtf(pkt.reason, 512) },
                { buf -> PhotoDeniedPacket(buf.readUtf(512)) }
            )

        fun register() {
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}


// ─── PhotoUnbannedPacket (S→C) ───────────────────────────────────────────────
// Сервер шлёт клиенту когда оператор снял бан на отправку фото.
// Клиент сбрасывает isBanned и обновляет uploadToken.
data class PhotoUnbannedPacket(
    val newUploadToken: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<PhotoUnbannedPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "photo_unbanned")
        val TYPE = CustomPacketPayload.Type<PhotoUnbannedPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PhotoUnbannedPacket> =
            StreamCodec.of(
                { buf, pkt -> buf.writeUtf(pkt.newUploadToken, 64) },
                { buf -> PhotoUnbannedPacket(buf.readUtf(64)) }
            )

        fun register() {
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── ImageDeletedPacket (S→C) ─────────────────────────────────────────────────
// Сервер рассылает всем клиентам когда оператор удалил фото командой /chat-remastered delete
data class ImageDeletedPacket(
    val imageId: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ImageDeletedPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "image_deleted")
        val TYPE = CustomPacketPayload.Type<ImageDeletedPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ImageDeletedPacket> =
            StreamCodec.of(
                { buf, pkt -> buf.writeUtf(pkt.imageId, 64) },
                { buf -> ImageDeletedPacket(buf.readUtf(64)) }
            )

        fun register() {
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── DeleteImagePacket (C→S) ──────────────────────────────────────────────────
// Игрок просит удалить своё фото. Сервер проверяет что imageId принадлежит этому игроку.
data class DeleteImagePacket(
    val imageId: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<DeleteImagePacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "delete_image")
        val TYPE = CustomPacketPayload.Type<DeleteImagePacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, DeleteImagePacket> =
            StreamCodec.of(
                { buf, pkt -> buf.writeUtf(pkt.imageId, 64) },
                { buf -> DeleteImagePacket(buf.readUtf(64)) }
            )

        fun register() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── ImageErrorPacket (S→C) ───────────────────────────────────────────────────
// Сервер сообщает клиенту что фото не удалось обработать (ошибка загрузки/таймаут)
data class ImageErrorPacket(
    val imageId: String,
    val reason: String   // "timeout" | "decode_error" | "server_error"
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ImageErrorPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "image_error")
        val TYPE = CustomPacketPayload.Type<ImageErrorPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ImageErrorPacket> =
            StreamCodec.of(
                { buf, pkt ->
                    buf.writeUtf(pkt.imageId, 64)
                    buf.writeUtf(pkt.reason, 32)
                },
                { buf -> ImageErrorPacket(buf.readUtf(64), buf.readUtf(32)) }
            )

        fun register() {
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── ReplyPacket (C→S) ────────────────────────────────────────────────────────
// Клиент отправляет reply-сообщение на сервер.
// replyToSender — имя того, кому отвечают (пустая строка если неизвестно)
// replyToText   — текст оригинального сообщения (или "" если фото без подписи)
// replyToImageId — imageId если ответ на фото, иначе ""
data class ReplyPacket(
    val text: String,
    val replyToSender: String,
    val replyToText: String,
    val replyToImageId: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ReplyPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "reply")
        val TYPE = CustomPacketPayload.Type<ReplyPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ReplyPacket> =
            StreamCodec.of(
                { buf, pkt ->
                    buf.writeUtf(pkt.text, 256)
                    buf.writeUtf(pkt.replyToSender, 64)
                    buf.writeUtf(pkt.replyToText, 256)
                    buf.writeUtf(pkt.replyToImageId, 64)
                },
                { buf ->
                    ReplyPacket(
                        buf.readUtf(256),
                        buf.readUtf(64),
                        buf.readUtf(256),
                        buf.readUtf(64)
                    )
                }
            )

        fun register() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── ReplyMetaPacket (C→S) ────────────────────────────────────────────────────
// Клиент отправляет reply одним пакетом: текст + метаданные.
// Сервер сам рассылает сообщение — sendChat() на клиенте НЕ вызывается.
data class ReplyMetaPacket(
    val text: String,
    val replyToSender: String,
    val replyToText: String,
    val replyToImageId: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ReplyMetaPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "reply_meta")
        val TYPE = CustomPacketPayload.Type<ReplyMetaPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ReplyMetaPacket> =
            StreamCodec.of(
                { buf, pkt ->
                    buf.writeUtf(pkt.text, 256)
                    buf.writeUtf(pkt.replyToSender, 64)
                    buf.writeUtf(pkt.replyToText, 256)
                    buf.writeUtf(pkt.replyToImageId, 64)
                },
                { buf ->
                    ReplyMetaPacket(
                        buf.readUtf(256),
                        buf.readUtf(64),
                        buf.readUtf(256),
                        buf.readUtf(64)
                    )
                }
            )

        fun register() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
        }
    }
}

// ─── ReplyChatPacket (S→C) ────────────────────────────────────────────────────
// Сервер рассылает всем: кто ответил, текст ответа, на кого/что ответили.
// Клиент рисует плашку reply над сообщением в чате.
data class ReplyChatPacket(
    val sender: String,
    val senderComponent: net.minecraft.network.chat.Component,
    val text: String,
    val replyToSender: String,
    val replyToText: String,
    val replyToImageId: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ReplyChatPacket> = TYPE

    companion object {
        val ID = Identifier.fromNamespaceAndPath("chat-remastered", "reply_chat")
        val TYPE = CustomPacketPayload.Type<ReplyChatPacket>(ID)

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ReplyChatPacket> =
            StreamCodec.of(
                { buf, pkt ->
                    buf.writeUtf(pkt.sender, 64)
                    net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, pkt.senderComponent)
                    buf.writeUtf(pkt.text, 256)
                    buf.writeUtf(pkt.replyToSender, 64)
                    buf.writeUtf(pkt.replyToText, 256)
                    buf.writeUtf(pkt.replyToImageId, 64)
                },
                { buf ->
                    ReplyChatPacket(
                        buf.readUtf(64),
                        net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf),
                        buf.readUtf(256),
                        buf.readUtf(64),
                        buf.readUtf(256),
                        buf.readUtf(64)
                    )
                }
            )

        fun register() {
            PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC)
            PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC)
        }
    }
}


