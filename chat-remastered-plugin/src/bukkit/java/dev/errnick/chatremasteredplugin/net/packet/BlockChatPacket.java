package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record BlockChatPacket(
        String sender,
        String senderComponentJson,
        String blockNamespace,
        String blockPath,
        String blockState,
        String caption,
        String groupLabel,
        int bodyRgb
) {

    public static BlockChatPacket read(PacketByteBuf buf) {
        return new BlockChatPacket(
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
        buf.writeUtf(blockNamespace);
        buf.writeUtf(blockPath);
        buf.writeUtf(blockState);
        buf.writeUtf(caption);
        buf.writeUtf(groupLabel);
        buf.writeInt(bodyRgb);
        return buf.toByteArray();
    }
    public BlockChatPacket(String sender, String senderComponentJson, String blockNamespace, String blockPath, String blockState, String caption) { this(sender, senderComponentJson, blockNamespace, blockPath, blockState, caption, "", 0); }
}
