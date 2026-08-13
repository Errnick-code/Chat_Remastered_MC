package dev.errnicraft.chatremastered.network;

import dev.errnicraft.chatremastered.network.packet.AdminDeleteImagePacket;
import dev.errnicraft.chatremastered.network.packet.BlockChatPacket;
import dev.errnicraft.chatremastered.network.packet.ClientHelloPacket;
import dev.errnicraft.chatremastered.network.packet.DeleteImagePacket;
import dev.errnicraft.chatremastered.network.packet.EntityByUuidChatPacket;
import dev.errnicraft.chatremastered.network.packet.EntityChatPacket;
import dev.errnicraft.chatremastered.network.packet.EntityMobChatPacket;
import dev.errnicraft.chatremastered.network.packet.HandshakeErrorPacket;
import dev.errnicraft.chatremastered.network.packet.ImageChatPacket;
import dev.errnicraft.chatremastered.network.packet.ImageDeletedPacket;
import dev.errnicraft.chatremastered.network.packet.ImageErrorPacket;
import dev.errnicraft.chatremastered.network.packet.ImageUploadedPacket;
import dev.errnicraft.chatremastered.network.packet.ItemChatPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoUnbannedPacket;
import dev.errnicraft.chatremastered.network.packet.ReplyChatPacket;
import dev.errnicraft.chatremastered.network.packet.ReplyMetaPacket;
import dev.errnicraft.chatremastered.network.packet.ReplyPacket;
import dev.errnicraft.chatremastered.network.packet.ServerConfigPacket;
import dev.errnicraft.chatremastered.network.packet.ServerHelloPacket;

public final class ChatRemasteredPackets {

    public static void registerAll() {
        ImageChatPacket.register();
        EntityChatPacket.register();
        EntityMobChatPacket.register();
        EntityByUuidChatPacket.register();
        ItemChatPacket.register();
        BlockChatPacket.register();
        ServerHelloPacket.register();
        ClientHelloPacket.register();
        ServerConfigPacket.register();
        HandshakeErrorPacket.register();
        ImageUploadedPacket.register();
        PhotoDeniedPacket.register();
        PhotoUnbannedPacket.register();
        ImageDeletedPacket.register();
        DeleteImagePacket.register();
        AdminDeleteImagePacket.register();
        ImageErrorPacket.register();
        ReplyPacket.register();
        ReplyMetaPacket.register();
        ReplyChatPacket.register();
    }

    private ChatRemasteredPackets() {
    }
}
