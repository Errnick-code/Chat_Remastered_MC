package dev.errnick.chatremasteredplugin.net;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class MessengerService {

    private static Plugin plugin;

    public static void init(Plugin owner) {
        plugin = owner;
        for (String channel : ModProtocol.ALL_CHANNELS) {
            Bukkit.getMessenger().registerOutgoingPluginChannel(owner, channel);
        }
    }

    public static void registerIncoming(String channel, PluginMessageListener listener) {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, listener);
    }

    public static void send(Player player, String channel, byte[] data) {
        player.sendPluginMessage(plugin, channel, data);
    }

    private MessengerService() {
    }
}
