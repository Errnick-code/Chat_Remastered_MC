package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record ItemChatPacket(
        String sender,
        String senderComponentJson,
        String itemNamespace,
        String itemPath,
        String itemNbt,
        String caption,
        String groupLabel,
        int bodyRgb
) {

    public static ItemChatPacket read(PacketByteBuf buf) {
        return new ItemChatPacket(
                buf.readUtf(),
                buf.readComponentJsonFromStringTag(),
                buf.readUtf(),
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
        buf.writeUtf(itemNamespace);
        buf.writeUtf(itemPath);
        buf.writeUtf(itemNbt);
        buf.writeUtf(caption);
        buf.writeUtf(groupLabel);
        buf.writeInt(bodyRgb);
        return buf.toByteArray();
    }
    public ItemChatPacket(String sender, String senderComponentJson, String itemNamespace, String itemPath, String itemNbt, String caption) { this(sender, senderComponentJson, itemNamespace, itemPath, itemNbt, caption, "", 0); }
}
