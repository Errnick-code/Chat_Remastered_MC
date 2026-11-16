package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record HandshakeErrorPacket(String reason) {

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(reason);
        return buf.toByteArray();
    }
}
