package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import dev.errnicraft.chatremastered.network.NetworkComponentJson;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BlockChatPacket(
        String sender,
        Component senderComponent,
        String blockNamespace,
        String blockPath,
        String blockState,
        String caption
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "block_chat");
    public static final CustomPacketPayload.Type<BlockChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, BlockChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.sender, 64);
                buf.writeUtf(NetworkComponentJson.toJson(pkt.senderComponent));
                buf.writeUtf(pkt.blockNamespace, 64);
                buf.writeUtf(pkt.blockPath, 128);
                buf.writeUtf(pkt.blockState, 512);
                buf.writeUtf(pkt.caption, 256);
            },
            buf -> new BlockChatPacket(
                    buf.readUtf(64),
                    NetworkComponentJson.fromJson(buf.readUtf()),
                    buf.readUtf(64),
                    buf.readUtf(128),
                    buf.readUtf(512),
                    buf.readUtf(256)
            )
    );

    public BlockChatPacket(String blockNamespace, String blockPath, String blockState, String caption) {
        this("", Component.literal(""), blockNamespace, blockPath, blockState, caption);
    }

    @Override
    public CustomPacketPayload.Type<BlockChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
    }
}
