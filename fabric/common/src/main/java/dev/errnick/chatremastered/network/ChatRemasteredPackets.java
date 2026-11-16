package dev.errnick.chatremastered.network;

import dev.errnick.chatremastered.network.packet.AdminDeleteImagePacket;
import dev.errnick.chatremastered.network.packet.BlockChatPacket;
import dev.errnick.chatremastered.network.packet.ClientHelloPacket;
import dev.errnick.chatremastered.network.packet.DeleteImagePacket;
import dev.errnick.chatremastered.network.packet.EntityByUuidChatPacket;
import dev.errnick.chatremastered.network.packet.EntityChatPacket;
import dev.errnick.chatremastered.network.packet.EntityMobChatPacket;
import dev.errnick.chatremastered.network.packet.HandshakeErrorPacket;
import dev.errnick.chatremastered.network.packet.ImageChatPacket;
import dev.errnick.chatremastered.network.packet.ImageDeletedPacket;
import dev.errnick.chatremastered.network.packet.ImageErrorPacket;
import dev.errnick.chatremastered.network.packet.ImageUploadedPacket;
import dev.errnick.chatremastered.network.packet.ItemChatPacket;
import dev.errnick.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnick.chatremastered.network.packet.PhotoUnbannedPacket;
import dev.errnick.chatremastered.network.packet.GroupChatPacket;
import dev.errnick.chatremastered.network.packet.GroupModePacket;
import dev.errnick.chatremastered.network.packet.ReplyChatPacket;
import dev.errnick.chatremastered.network.packet.ReplyMetaPacket;
import dev.errnick.chatremastered.network.packet.ReplyPacket;
import dev.errnick.chatremastered.network.packet.ServerConfigPacket;
import dev.errnick.chatremastered.network.packet.ServerHelloPacket;

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
        GroupChatPacket.register();
        GroupModePacket.register();
    }

    private ChatRemasteredPackets() {
    }
}
