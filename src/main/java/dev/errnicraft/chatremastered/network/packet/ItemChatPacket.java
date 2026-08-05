package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ItemChatPacket(
        String sender,
        Component senderComponent,
        String itemNamespace,
        String itemPath,
        String itemNbt,
        String caption
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "item_chat");
    public static final CustomPacketPayload.Type<ItemChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ItemChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.sender, 64);
                ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, pkt.senderComponent);
                buf.writeUtf(pkt.itemNamespace, 64);
                buf.writeUtf(pkt.itemPath, 128);
                buf.writeUtf(pkt.itemNbt, 512);
                buf.writeUtf(pkt.caption, 256);
            },
            buf -> new ItemChatPacket(
                    buf.readUtf(64),
                    ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf),
                    buf.readUtf(64),
                    buf.readUtf(128),
                    buf.readUtf(512),
                    buf.readUtf(256)
            )
    );

    public ItemChatPacket(String itemNamespace, String itemPath, String itemNbt, String caption) {
        this("", Component.literal(""), itemNamespace, itemPath, itemNbt, caption);
    }

    @Override
    public CustomPacketPayload.Type<ItemChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
