package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record EntityByUuidChatPacket(String uuid, String caption) {

    public static EntityByUuidChatPacket read(PacketByteBuf buf) {
        return new EntityByUuidChatPacket(buf.readUtf(), buf.readUtf());
    }
}
