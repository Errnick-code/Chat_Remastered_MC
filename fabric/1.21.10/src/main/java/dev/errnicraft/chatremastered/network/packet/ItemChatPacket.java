package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import dev.errnicraft.chatremastered.network.NetworkComponentJson;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ItemChatPacket(
        String sender,
        Component senderComponent,
        String itemNamespace,
        String itemPath,
        String itemNbt,
        String caption
) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("chat-remastered", "item_chat");
    public static final CustomPacketPayload.Type<ItemChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ItemChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.sender, 64);
                buf.writeUtf(NetworkComponentJson.toJson(pkt.senderComponent));
                buf.writeUtf(pkt.itemNamespace, 64);
                buf.writeUtf(pkt.itemPath, 128);
                buf.writeUtf(pkt.itemNbt, 512);
                buf.writeUtf(pkt.caption, 256);
            },
            buf -> new ItemChatPacket(
                    buf.readUtf(64),
                    NetworkComponentJson.fromJson(buf.readUtf()),
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
