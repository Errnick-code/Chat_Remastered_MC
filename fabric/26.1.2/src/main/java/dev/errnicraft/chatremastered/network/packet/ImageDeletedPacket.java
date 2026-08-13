package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ImageDeletedPacket(String imageId, boolean byAdmin) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "image_deleted");
    public static final CustomPacketPayload.Type<ImageDeletedPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ImageDeletedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.imageId, 64);
                buf.writeBoolean(pkt.byAdmin);
            },
            buf -> new ImageDeletedPacket(buf.readUtf(64), buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<ImageDeletedPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
    }
}
