package dev.errnick.chatremasteredplugin.net.packet;

import dev.errnick.chatremasteredplugin.net.PacketByteBuf;

public record ServerConfigPacket(
        String resolution,
        int imagePublicPort,
        String uploadToken,
        int photoCooldownSeconds,
        boolean gifEnabled,
        int gifMaxDim,
        int maxPhotosPerMessage,
        String imageHost
) {

    public byte[] write() {
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf(resolution);
        buf.writeInt(imagePublicPort);
        buf.writeUtf(uploadToken);
        buf.writeInt(photoCooldownSeconds);
        buf.writeBoolean(gifEnabled);
        buf.writeInt(gifMaxDim);
        buf.writeInt(maxPhotosPerMessage);
        buf.writeUtf(imageHost == null ? "" : imageHost);
        return buf.toByteArray();
    }
}
