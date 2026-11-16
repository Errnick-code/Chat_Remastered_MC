package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record ReplyChatPacket(
        String sender,
        String senderComponentJson,
        String text,
        String replyToSender,
        String replyToText,
        String replyToImageId,
        String groupLabel,
        int bodyRgb
) {

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(sender);
        buf.writeComponentAsPlainStringTag(senderComponentJson);
        buf.writeUtf(text);
        buf.writeUtf(replyToSender);
        buf.writeUtf(replyToText);
        buf.writeUtf(replyToImageId);
        buf.writeUtf(groupLabel);
        buf.writeInt(bodyRgb);
        return buf.toByteArray();
    }
    public ReplyChatPacket(String sender, String senderComponentJson, String text, String replyToSender, String replyToText, String replyToImageId) { this(sender, senderComponentJson, text, replyToSender, replyToText, replyToImageId, "", 0); }
}
