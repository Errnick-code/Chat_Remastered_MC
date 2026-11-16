package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record ImageErrorPacket(String imageId, String reason) {

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(imageId);
        buf.writeUtf(reason);
        return buf.toByteArray();
    }
}
