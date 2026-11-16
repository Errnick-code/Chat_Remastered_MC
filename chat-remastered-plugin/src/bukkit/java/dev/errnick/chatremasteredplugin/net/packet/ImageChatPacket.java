package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record ImageChatPacket(
        String imageId,
        String sender,
        String caption,
        int width,
        int height,
        String senderComponentJson,
        String replyToSender,
        String replyToText,
        String replyToImageId,
        String groupId,
        int groupIndex,
        int groupCount,
        String groupLabel,
        int bodyRgb
) {

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(imageId);
        buf.writeUtf(sender);
        buf.writeUtf(caption);
        buf.writeInt(width);
        buf.writeInt(height);
        buf.writeComponentAsPlainStringTag(senderComponentJson);
        buf.writeUtf(replyToSender);
        buf.writeUtf(replyToText);
        buf.writeUtf(replyToImageId);
        buf.writeUtf(groupId);
        buf.writeInt(groupIndex);
        buf.writeInt(groupCount);
        buf.writeUtf(groupLabel);
        buf.writeInt(bodyRgb);
        return buf.toByteArray();
    }
}
