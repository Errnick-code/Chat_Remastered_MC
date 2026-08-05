package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteImagePacket(String imageId) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "delete_image");
    public static final CustomPacketPayload.Type<DeleteImagePacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, DeleteImagePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeUtf(pkt.imageId, 64),
            buf -> new DeleteImagePacket(buf.readUtf(64))
    );

    @Override
    public CustomPacketPayload.Type<DeleteImagePacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
    }
}
