package dev.errnick.chatremasteredplugin;

import dev.errnick.chatremasteredplugin.command.AdminCommand;
import dev.errnick.chatremasteredplugin.command.GroupCommand;
import dev.errnick.chatremasteredplugin.groups.Groups;
import dev.errnick.chatremasteredplugin.command.VersionCommand;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.handler.AdminDeleteImageHandler;
import dev.errnick.chatremasteredplugin.net.handler.BlockChatHandler;
import dev.errnick.chatremasteredplugin.net.handler.DeleteImageHandler;
import dev.errnick.chatremasteredplugin.net.handler.EntityByUuidChatHandler;
import dev.errnick.chatremasteredplugin.net.handler.EntityChatHandler;
import dev.errnick.chatremasteredplugin.net.handler.EntityMobChatHandler;
import dev.errnick.chatremasteredplugin.net.handler.HandshakeHandler;
import dev.errnick.chatremasteredplugin.net.handler.ImageUploadHandler;
import dev.errnick.chatremasteredplugin.net.handler.ItemChatHandler;
import dev.errnick.chatremasteredplugin.net.handler.ReplyHandler;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatRemasteredPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        MessengerService.init(this);
        Groups.registerIncoming();

        HandshakeHandler.register(this);
        ImageUploadHandler.register(this);
        DeleteImageHandler.register();
        AdminDeleteImageHandler.register();
        EntityChatHandler.register();
        EntityMobChatHandler.register();
        EntityByUuidChatHandler.register();
        ItemChatHandler.register();
        BlockChatHandler.register();
        ReplyHandler.register();

        ChatMessageGuard.register(this);
        PluginLifecycle.register(this);
        PluginLifecycle.onEnable(this);

        ImageReadyBridge.register();
        Errnick.register(this);

        getCommand("chat-remastered").setExecutor(new VersionCommand());
        getCommand("chat-remastered-admin").setExecutor(new AdminCommand());
        getCommand("chatgroup").setExecutor(new GroupCommand());
    }

    @Override
    public void onDisable() {
        PluginLifecycle.onDisable();
    }
}
