package dev.errnicraft.chatremastered.network.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImageUploadedPacket(
        String imageId,
        String sender,
        String caption,
        int width,
        int height,
        String replyToSender,
        String replyToText,
        String replyToImageId,
        String groupId,
        int groupIndex,
        int groupCount
) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("chat-remastered", "image_uploaded");
    public static final CustomPacketPayload.Type<ImageUploadedPacket> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ImageUploadedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.imageId);
                buf.writeUtf(pkt.sender);
                buf.writeUtf(pkt.caption);
                buf.writeInt(pkt.width);
                buf.writeInt(pkt.height);
                buf.writeUtf(pkt.replyToSender, 64);
                buf.writeUtf(pkt.replyToText, 256);
                buf.writeUtf(pkt.replyToImageId, 64);
                buf.writeUtf(pkt.groupId, 40);
                buf.writeInt(pkt.groupIndex);
                buf.writeInt(pkt.groupCount);
            },
            buf -> new ImageUploadedPacket(
                    buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readInt(), buf.readInt(),
                    buf.readUtf(64), buf.readUtf(256), buf.readUtf(64),
                    buf.readUtf(40), buf.readInt(), buf.readInt()
            )
    );

    public ImageUploadedPacket(String imageId, String sender, String caption, int width, int height) {
        this(imageId, sender, caption, width, height, "", "", "", "", 0, 1);
    }

    public ImageUploadedPacket(String imageId, String sender, String caption, int width, int height,
                                String replyToSender, String replyToText, String replyToImageId) {
        this(imageId, sender, caption, width, height, replyToSender, replyToText, replyToImageId, "", 0, 1);
    }

    @Override
    public CustomPacketPayload.Type<ImageUploadedPacket> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
    }
}
