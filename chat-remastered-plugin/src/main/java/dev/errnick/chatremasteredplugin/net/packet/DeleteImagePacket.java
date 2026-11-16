package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record DeleteImagePacket(String imageId) {

    public static DeleteImagePacket read(PacketByteBuf buf) {
        return new DeleteImagePacket(buf.readUtf());
    }
}
