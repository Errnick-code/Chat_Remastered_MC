package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import dev.errnicraft.chatremastered.network.NetworkComponentJson;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
        int groupCount
) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("chat-remastered", "image_chat");
    public static final CustomPacketPayload.Type<ImageChatPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ImageChatPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.imageId);
                buf.writeUtf(pkt.sender);
                buf.writeUtf(pkt.caption);
                buf.writeInt(pkt.width);
                buf.writeInt(pkt.height);
                buf.writeUtf(NetworkComponentJson.toJson(pkt.senderComponent));
                buf.writeUtf(pkt.replyToSender, 64);
                buf.writeUtf(pkt.replyToText, 256);
                buf.writeUtf(pkt.replyToImageId, 64);
                buf.writeUtf(pkt.groupId, 40);
                buf.writeInt(pkt.groupIndex);
                buf.writeInt(pkt.groupCount);
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
                    buf.readInt()
            )
    );

    public ImageChatPacket(String imageId, String sender, String caption, int width, int height) {
        this(imageId, sender, caption, width, height, Component.literal(""), "", "", "", "", 0, 1);
    }

    public ImageChatPacket(String imageId, String sender, String caption, int width, int height, Component senderComponent) {
        this(imageId, sender, caption, width, height, senderComponent, "", "", "", "", 0, 1);
    }

    public ImageChatPacket(String imageId, String sender, String caption, int width, int height, Component senderComponent,
                            String replyToSender, String replyToText, String replyToImageId) {
        this(imageId, sender, caption, width, height, senderComponent, replyToSender, replyToText, replyToImageId, "", 0, 1);
    }

    @Override
    public CustomPacketPayload.Type<ImageChatPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
