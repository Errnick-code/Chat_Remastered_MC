package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record EntityChatPacket(
        String sender,
        String senderComponentJson,
        String targetPlayerName,
        String behavior,
        String caption,
        String groupLabel,
        int bodyRgb
) {

    public static EntityChatPacket read(PacketByteBuf buf) {
        return new EntityChatPacket(
                buf.readUtf(),
                buf.readComponentJsonFromStringTag(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt()
        );
    }

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(sender);
        buf.writeComponentAsPlainStringTag(senderComponentJson);
        buf.writeUtf(targetPlayerName);
        buf.writeUtf(behavior);
        buf.writeUtf(caption);
        buf.writeUtf(groupLabel);
        buf.writeInt(bodyRgb);
        return buf.toByteArray();
    }
    public EntityChatPacket(String sender, String senderComponentJson, String targetPlayerName, String behavior, String caption) { this(sender, senderComponentJson, targetPlayerName, behavior, caption, "", 0); }
}
