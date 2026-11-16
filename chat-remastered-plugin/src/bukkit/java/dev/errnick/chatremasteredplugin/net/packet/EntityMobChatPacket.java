package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record EntityMobChatPacket(
        String sender,
        String senderComponentJson,
        String entityNamespace,
        String entityPath,
        String entityNbt,
        String behavior,
        int size,
        int offsetX,
        int offsetY,
        String caption,
        String groupLabel,
        int bodyRgb
) {

    public static EntityMobChatPacket read(PacketByteBuf buf) {
        return new EntityMobChatPacket(
                buf.readUtf(),
                buf.readComponentJsonFromStringTag(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt()
        );
    }

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(sender);
        buf.writeComponentAsPlainStringTag(senderComponentJson);
        buf.writeUtf(entityNamespace);
        buf.writeUtf(entityPath);
        buf.writeUtf(entityNbt);
        buf.writeUtf(behavior);
        buf.writeVarInt(size);
        buf.writeVarInt(offsetX);
        buf.writeVarInt(offsetY);
        buf.writeUtf(caption);
        buf.writeUtf(groupLabel);
        buf.writeInt(bodyRgb);
        return buf.toByteArray();
    }
    public EntityMobChatPacket(String sender, String senderComponentJson, String entityNamespace, String entityPath, String entityNbt, String behavior, int size, int offsetX, int offsetY, String caption) { this(sender, senderComponentJson, entityNamespace, entityPath, entityNbt, behavior, size, offsetX, offsetY, caption, "", 0); }
}
