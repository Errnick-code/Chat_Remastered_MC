package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PhotoDeniedPacket(String reason) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "photo_denied");
    public static final CustomPacketPayload.Type<PhotoDeniedPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, PhotoDeniedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeUtf(pkt.reason, 512),
            buf -> new PhotoDeniedPacket(buf.readUtf(512))
    );

    @Override
    public CustomPacketPayload.Type<PhotoDeniedPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
