package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerHelloPacket(String serverProtocolVersion) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "server_hello");
    public static final CustomPacketPayload.Type<ServerHelloPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ServerHelloPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeUtf(pkt.serverProtocolVersion, 16),
            buf -> new ServerHelloPacket(buf.readUtf(16))
    );

    @Override
    public CustomPacketPayload.Type<ServerHelloPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
