package dev.errnick.chatremasteredplugin.command;

import dev.errnick.chatremasteredplugin.ChatRemasteredPlugin;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class VersionCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ChatRemasteredPlugin plugin = JavaPlugin.getPlugin(ChatRemasteredPlugin.class);
        String pluginVersion = plugin.getDescription().getVersion();
        sender.sendMessage("Chat Remastered plugin v" + pluginVersion + ", protocol v" + ModProtocol.MOD_PROTOCOL_VERSION);
        return true;
    }
}
