package dev.errnick.chatremasteredplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class ChatMessageGuard implements Listener {

    public static void register(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new ChatMessageGuard(), plugin);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        UUID uuid = sender.getUniqueId();

        if (PluginState.isMuted(uuid)) {
            event.setCancelled(true);
            sender.sendMessage(Component.text(
                    PluginState.cachedConfig != null ? PluginState.cachedConfig.mutedMessage()
                            : "You are muted and cannot send messages, photos or replies."));
            return;
        }

        Long lastReply = PluginState.recentModReply.get(uuid);
        if (lastReply != null && System.currentTimeMillis() - lastReply < PluginState.REPLY_SUPPRESS_WINDOW_MS) {
            event.setCancelled(true);
        }
    }

    private ChatMessageGuard() {
    }
}
