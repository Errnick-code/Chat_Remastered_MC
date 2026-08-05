package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerConfigPacket(
        String resolution,
        int imagePort,
        String uploadToken,
        int photoCooldownSeconds,
        boolean gifEnabled,
        int gifMaxDim,
        int maxPhotosPerMessage
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "server_config");
    public static final CustomPacketPayload.Type<ServerConfigPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ServerConfigPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.resolution, 8);
                buf.writeInt(pkt.imagePort);
                buf.writeUtf(pkt.uploadToken, 64);
                buf.writeInt(pkt.photoCooldownSeconds);
                buf.writeBoolean(pkt.gifEnabled);
                buf.writeInt(pkt.gifMaxDim);
                buf.writeInt(pkt.maxPhotosPerMessage);
            },
            buf -> new ServerConfigPacket(
                    buf.readUtf(8),
                    buf.readInt(),
                    buf.readUtf(64),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt()
            )
    );

    @Override
    public CustomPacketPayload.Type<ServerConfigPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
