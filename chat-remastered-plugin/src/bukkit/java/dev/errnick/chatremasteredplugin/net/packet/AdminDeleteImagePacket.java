package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record AdminDeleteImagePacket(String imageId) {

    public static AdminDeleteImagePacket read(PacketByteBuf buf) {
        return new AdminDeleteImagePacket(buf.readUtf());
    }
}
