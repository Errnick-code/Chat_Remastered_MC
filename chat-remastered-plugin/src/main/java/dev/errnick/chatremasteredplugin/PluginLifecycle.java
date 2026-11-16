package dev.errnick.chatremasteredplugin;

import dev.errnick.chatremasteredplugin.config.ServerConfig;
import dev.errnick.chatremasteredplugin.config.ServerConfigLoader;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.packet.ServerHelloPacket;
import dev.errnick.chatremasteredplugin.tcp.ImageTcpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PluginLifecycle implements Listener {

    private final ChatRemasteredPlugin plugin;

    private PluginLifecycle(ChatRemasteredPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(ChatRemasteredPlugin plugin) {
        PluginLifecycle instance = new PluginLifecycle(plugin);
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    public static void onEnable(ChatRemasteredPlugin plugin) {
        ServerConfig config = ServerConfigLoader.loadOrCreate(plugin.getDataFolder().toPath());
        PluginState.cachedConfig = config;
        PluginState.banList.load(plugin.getDataFolder().toPath());
        PluginState.muteList.load(plugin.getDataFolder().toPath());

        ImageTcpServer.initCacheDir(plugin.getDataFolder());
        ImageTcpServer.setMaxUploadBytes(8L * 1024 * 1024);
        if (ImageTcpServer.startIfNeeded(config.imagePort())) {
            Bukkit.getLogger().info("[Chat Remastered] TCP server started at boot on port " + config.imagePort());
        } else {
            Bukkit.getLogger().warning("[Chat Remastered] TCP server NOT started: port " + config.imagePort()
                    + " is busy. Change imagePort in chat-remastered-server.json5"
                    + " (it must not be the Minecraft server port).");
        }
        Bukkit.getLogger().info("[Chat Remastered] Clients will use image endpoint "
                + (config.imageHost().isBlank() ? "<server address>" : config.imageHost())
                + ":" + config.imagePublicPort());
    }

    public static void onDisable() {
        PluginState.pendingBroadcasts.clear();
        PluginState.groupStubSent.clear();
        ImageTcpServer.stop();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            MessengerService.send(player, ModProtocol.CH_SERVER_HELLO,
                    new ServerHelloPacket(ModProtocol.MOD_PROTOCOL_VERSION).write());
        }, 40L);

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!PluginState.hasModInstalled(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> sendDownloadHint(player));
            }
        }, 200L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        PluginState.modPlayers.remove(uuid);
        String token = PluginState.playerTokens.remove(uuid);
        if (token != null) {
            ImageTcpServer.removeToken(token);
        }
    }

    private static void sendDownloadHint(Player player) {
        String modrinthUrl = "https://modrinth.com/mod/chat-remastered";
        Component msg = Component.empty()
                .append(Component.text("[").color(TextColor.color(0x555555)))
                .append(Component.text("Chat Remastered").color(TextColor.color(0x00b0f0)).decorate(TextDecoration.BOLD))
                .append(Component.text("] ").color(TextColor.color(0x555555)))
                .append(Component.text("This server has Chat Remastered — an enhanced chat mod with photos, replies and more. ")
                        .color(TextColor.color(0xaaaaaa)))
                .append(Component.text("\u2B07 Download here")
                        .color(TextColor.color(0x1bd96a))
                        .decorate(TextDecoration.BOLD, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(modrinthUrl))
                        .hoverEvent(HoverEvent.showText(Component.text(modrinthUrl).color(TextColor.color(0x888888)))));
        player.sendMessage(msg);
    }
}
