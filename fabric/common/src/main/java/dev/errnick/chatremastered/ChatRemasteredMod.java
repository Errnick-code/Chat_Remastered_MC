package dev.errnick.chatremastered;

import dev.errnick.chatremastered.network.ChatRemasteredPackets;
import dev.errnick.chatremastered.server.ChatMessageGuard;
import dev.errnick.chatremastered.server.ChatRemasteredLifecycle;
import dev.errnick.chatremastered.server.ImageReadyBridge;
import dev.errnick.chatremastered.server.command.AdminCommand;
import dev.errnick.chatremastered.server.groups.GroupCommand;
import dev.errnick.chatremastered.server.groups.GroupNetwork;
import dev.errnick.chatremastered.server.command.VersionCommand;
import dev.errnick.chatremastered.server.network.AdminDeleteImageHandler;
import dev.errnick.chatremastered.server.network.BlockChatHandler;
import dev.errnick.chatremastered.server.network.DeleteImageHandler;
import dev.errnick.chatremastered.server.network.HandshakeHandler;
import dev.errnick.chatremastered.server.network.ImageUploadHandler;
import dev.errnick.chatremastered.server.network.EntityChatHandler;
import dev.errnick.chatremastered.server.network.EntityMobChatHandler;
import dev.errnick.chatremastered.server.network.EntityByUuidChatHandler;
import dev.errnick.chatremastered.server.network.ItemChatHandler;
import dev.errnick.chatremastered.server.network.ReplyHandler;
import net.fabricmc.api.ModInitializer;

public final class ChatRemasteredMod implements ModInitializer {

    @Override
    public void onInitialize() {
        ChatRemasteredPackets.registerAll();

        ChatMessageGuard.register();
        ImageReadyBridge.register();

        HandshakeHandler.register();
        ImageUploadHandler.register();
        ReplyHandler.register();
        EntityChatHandler.register();
        EntityMobChatHandler.register();
        EntityByUuidChatHandler.register();
        ItemChatHandler.register();
        BlockChatHandler.register();
        DeleteImageHandler.register();
        AdminDeleteImageHandler.register();

        AdminCommand.register();
        GroupCommand.register();
        GroupNetwork.register();

        VersionCommand.register();

        ChatRemasteredLifecycle.register();
    }
}
