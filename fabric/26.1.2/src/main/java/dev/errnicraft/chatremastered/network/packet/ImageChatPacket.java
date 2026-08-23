package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import dev.errnicraft.chatremastered.network.NetworkComponentJson;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ImageChatPacket(
        String imageId,
        String sender,
        String caption,
        int width,
        int height,
        Component senderComponent,
        String replyToSender,
        String replyToText,
        String replyToImageId,
        String groupId,
        int groupIndex,
        int groupCount,
        String groupLabel,
        int bodyRgb
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chat-remastered", "image_chat");
    public static final CustomPacketPayload.Type<ImageChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ImageChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.imageId);
                buf.writeUtf(pkt.sender);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.caption, 1024));
                buf.writeInt(pkt.width);
                buf.writeInt(pkt.height);
                buf.writeUtf(NetworkComponentJson.toJson(pkt.senderComponent));
                buf.writeUtf(NetworkComponentJson.trunc(pkt.replyToSender, 64), 64);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.replyToText, 256), 256);
                buf.writeUtf(pkt.replyToImageId, 64);
                buf.writeUtf(pkt.groupId, 40);
                buf.writeInt(pkt.groupIndex);
                buf.writeInt(pkt.groupCount);
                buf.writeUtf(NetworkComponentJson.trunc(pkt.groupLabel, 48), 48);
                buf.writeInt(pkt.bodyRgb);
            },
            buf -> new ImageChatPacket(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readInt(),
                    NetworkComponentJson.fromJson(buf.readUtf()),
                    buf.readUtf(64),
                    buf.readUtf(256),
                    buf.readUtf(64),
                    buf.readUtf(40),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(48),
                    buf.readInt()
            )
    );

    public ImageChatPacket(String imageId, String sender, String caption, int width, int height, Component senderComponent,
                           String replyToSender, String replyToText, String replyToImageId,
                           String groupId, int groupIndex, int groupCount) {
        this(imageId, sender, caption, width, height, senderComponent, replyToSender, replyToText, replyToImageId,
                groupId, groupIndex, groupCount, "", 0);
    }

    @Override
    public CustomPacketPayload.Type<ImageChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
    }
}
