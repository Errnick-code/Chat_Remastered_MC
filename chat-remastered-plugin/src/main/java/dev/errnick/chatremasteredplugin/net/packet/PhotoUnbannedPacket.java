package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record PhotoUnbannedPacket(String newUploadToken) {

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(newUploadToken);
        return buf.toByteArray();
    }
}
