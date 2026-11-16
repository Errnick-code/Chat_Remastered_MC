package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record ServerHelloPacket(String serverProtocolVersion) {

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(serverProtocolVersion);
        return buf.toByteArray();
    }
}
