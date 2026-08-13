package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import dev.errnicraft.chatremastered.network.NetworkComponentJson;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EntityChatPacket(
        String sender,
        Component senderComponent,
        String targetPlayerName,
        String behavior,
        String caption
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "entity_chat");
    public static final CustomPacketPayload.Type<EntityChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, EntityChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.sender, 64);
                buf.writeUtf(NetworkComponentJson.toJson(pkt.senderComponent));
                buf.writeUtf(pkt.targetPlayerName, 16);
                buf.writeUtf(pkt.behavior, 16);
                buf.writeUtf(pkt.caption, 256);
            },
            buf -> new EntityChatPacket(
                    buf.readUtf(64),
                    NetworkComponentJson.fromJson(buf.readUtf()),
                    buf.readUtf(16),
                    buf.readUtf(16),
                    buf.readUtf(256)
            )
    );

    public EntityChatPacket(String targetPlayerName, String behavior, String caption) {
        this("", Component.literal(""), targetPlayerName, behavior, caption);
    }

    @Override
    public CustomPacketPayload.Type<EntityChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
    }
}
