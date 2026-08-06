package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import dev.errnicraft.chatremastered.network.NetworkComponentJson;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EntityMobChatPacket(
        String sender,
        Component senderComponent,
        String entityNamespace,
        String entityPath,
        String entityNbt,
        String behavior,
        int size,
        int offsetX,
        int offsetY,
        String caption
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "entity_mob_chat");
    public static final CustomPacketPayload.Type<EntityMobChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, EntityMobChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.sender, 64);
                buf.writeUtf(NetworkComponentJson.toJson(pkt.senderComponent));
                buf.writeUtf(pkt.entityNamespace, 64);
                buf.writeUtf(pkt.entityPath, 128);
                buf.writeUtf(pkt.entityNbt, 512);
                buf.writeUtf(pkt.behavior, 16);
                buf.writeVarInt(pkt.size);
                buf.writeVarInt(pkt.offsetX);
                buf.writeVarInt(pkt.offsetY);
                buf.writeUtf(pkt.caption, 256);
            },
            buf -> new EntityMobChatPacket(
                    buf.readUtf(64),
                    NetworkComponentJson.fromJson(buf.readUtf()),
                    buf.readUtf(64),
                    buf.readUtf(128),
                    buf.readUtf(512),
                    buf.readUtf(16),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(256)
            )
    );

    public EntityMobChatPacket(String entityNamespace, String entityPath, String entityNbt,
                                String behavior, int size, int offsetX, int offsetY, String caption) {
        this("", Component.literal(""), entityNamespace, entityPath, entityNbt, behavior,
                size, offsetX, offsetY, caption);
    }

    @Override
    public CustomPacketPayload.Type<EntityMobChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
