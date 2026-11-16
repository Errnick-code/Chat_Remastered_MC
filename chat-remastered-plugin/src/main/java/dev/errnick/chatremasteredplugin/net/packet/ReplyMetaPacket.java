package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record ReplyMetaPacket(
        String text,
        String replyToSender,
        String replyToText,
        String replyToImageId
) {

    public static ReplyMetaPacket read(PacketByteBuf buf) {
        return new ReplyMetaPacket(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
    }
}
