package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record ImageDeletedPacket(String imageId, boolean byAdmin) {

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(imageId);
        buf.writeBoolean(byAdmin);
        return buf.toByteArray();
    }
}
