package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

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
) {

    public static ImageUploadedPacket read(PacketByteBuf buf) {
        return new ImageUploadedPacket(
                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readInt(), buf.readInt(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readInt(), buf.readInt()
        );
    }
}
