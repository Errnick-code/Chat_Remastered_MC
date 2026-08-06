package dev.errnicraft.chatremastered;

import dev.errnicraft.chatremastered.network.ChatRemasteredPackets;
import dev.errnicraft.chatremastered.server.ChatMessageGuard;
import dev.errnicraft.chatremastered.server.ChatRemasteredLifecycle;
import dev.errnicraft.chatremastered.server.ImageReadyBridge;
import dev.errnicraft.chatremastered.server.command.AdminCommand;
import dev.errnicraft.chatremastered.server.command.VersionCommand;
import dev.errnicraft.chatremastered.server.network.AdminDeleteImageHandler;
import dev.errnicraft.chatremastered.server.network.DeleteImageHandler;
import dev.errnicraft.chatremastered.server.network.HandshakeHandler;
import dev.errnicraft.chatremastered.server.network.ImageUploadHandler;
import dev.errnicraft.chatremastered.server.network.EntityChatHandler;
import dev.errnicraft.chatremastered.server.network.EntityMobChatHandler;
import dev.errnicraft.chatremastered.server.network.EntityByUuidChatHandler;
import dev.errnicraft.chatremastered.server.network.ItemChatHandler;
import dev.errnicraft.chatremastered.server.network.ReplyHandler;
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
        DeleteImageHandler.register();
        AdminDeleteImageHandler.register();

        AdminCommand.register();
        VersionCommand.register();

        ChatRemasteredLifecycle.register();
    }
}
