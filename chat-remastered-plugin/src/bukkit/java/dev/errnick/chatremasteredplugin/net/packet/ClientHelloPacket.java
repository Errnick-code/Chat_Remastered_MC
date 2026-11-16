package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record ClientHelloPacket(String clientProtocolVersion) {

    public static ClientHelloPacket read(PacketByteBuf buf) {
        return new ClientHelloPacket(buf.readUtf());
    }
}
