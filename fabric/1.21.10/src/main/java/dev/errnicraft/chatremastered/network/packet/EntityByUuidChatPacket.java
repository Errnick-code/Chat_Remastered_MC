package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EntityByUuidChatPacket(
        String uuid,
        String caption
) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("chat-remastered", "entity_by_uuid_chat");
    public static final CustomPacketPayload.Type<EntityByUuidChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, EntityByUuidChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.uuid, 36);
                buf.writeUtf(pkt.caption, 256);
            },
            buf -> new EntityByUuidChatPacket(
                    buf.readUtf(36),
                    buf.readUtf(256)
            )
    );

    @Override
    public CustomPacketPayload.Type<EntityByUuidChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
    }
}
