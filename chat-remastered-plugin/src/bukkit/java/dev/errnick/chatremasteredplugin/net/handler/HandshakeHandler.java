package dev.errnick.chatremasteredplugin.net.handler;

import dev.errnick.chatremasteredplugin.ChatRemasteredPlugin;
import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.config.ServerConfig;
import dev.errnick.chatremasteredplugin.config.ServerConfigLoader;
import dev.errnick.chatremasteredplugin.moderation.ModerationActions;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import dev.errnick.chatremasteredplugin.net.packet.ClientHelloPacket;
import dev.errnick.chatremasteredplugin.net.packet.HandshakeErrorPacket;
import dev.errnick.chatremasteredplugin.net.packet.PhotoDeniedPacket;
import dev.errnick.chatremasteredplugin.net.packet.ServerConfigPacket;
import dev.errnick.chatremasteredplugin.tcp.ImageTcpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;

public final class HandshakeHandler implements PluginMessageListener {

    private final ChatRemasteredPlugin plugin;

    public HandshakeHandler(ChatRemasteredPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(ChatRemasteredPlugin plugin) {
        MessengerService.registerIncoming(ModProtocol.CH_CLIENT_HELLO, new HandshakeHandler(plugin));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        ClientHelloPacket payload = ClientHelloPacket.read(new PacketByteBuf(message));
        String clientVersion = payload.clientProtocolVersion();

        boolean versionMismatch = !clientVersion.equals(ModProtocol.MOD_PROTOCOL_VERSION);
        if (versionMismatch) {
            MessengerService.send(player, ModProtocol.CH_HANDSHAKE_ERROR,
                    new HandshakeErrorPacket(
                            "\u00A7e\u0412\u0435\u0440\u0441\u0438\u044F \u043C\u043E\u0434\u0430 \u043E\u0442\u043B\u0438\u0447\u0430\u0435\u0442\u0441\u044F: \u0441\u0435\u0440\u0432\u0435\u0440 v"
                                    + ModProtocol.MOD_PROTOCOL_VERSION + ", \u043A\u043B\u0438\u0435\u043D\u0442 v" + clientVersion
                                    + ". \u0427\u0430\u0441\u0442\u044C \u0444\u0443\u043D\u043A\u0446\u0438\u0439 \u043C\u043E\u0436\u0435\u0442 \u0440\u0430\u0431\u043E\u0442\u0430\u0442\u044C \u043D\u0435\u0441\u0442\u0430\u0431\u0438\u043B\u044C\u043D\u043E."
                    ).write());
            Bukkit.getLogger().info("[Chat Remastered] " + player.getName()
                    + " has mismatched mod version (continuing anyway): client v" + clientVersion
                    + ", server v" + ModProtocol.MOD_PROTOCOL_VERSION);
        }

        PluginState.modPlayers.add(player.getUniqueId());
        String uploadToken = UUID.randomUUID().toString();
        PluginState.playerTokens.put(player.getUniqueId(), uploadToken);

        if (!PluginState.isPhotoBanned(player.getUniqueId())) {
            ImageTcpServer.addToken(uploadToken);
        }

        ServerConfig config = ServerConfigLoader.loadOrCreate(plugin.getDataFolder().toPath());
        MessengerService.send(player, ModProtocol.CH_SERVER_CONFIG,
                new ServerConfigPacket(config.resolution(), config.imagePublicPort(), uploadToken,
                        config.photoCooldownSeconds(), config.gifEnabled(), config.gifMaxDim(),
                        config.maxPhotosPerMessage(), config.imageHost()).write());
        Bukkit.getLogger().info("[Chat Remastered] Image endpoint sent to " + player.getName()
                + ": " + (config.imageHost().isBlank() ? "<auto>" : config.imageHost())
                + ":" + config.imagePublicPort() + " (local bind " + config.imagePort() + ")");

        if (PluginState.isPhotoBanned(player.getUniqueId())) {
            MessengerService.send(player, ModProtocol.CH_PHOTO_DENIED, new PhotoDeniedPacket("banned").write());
        } else if (ModerationActions.isEffectivelyMuted(player.getUniqueId())) {
            MessengerService.send(player, ModProtocol.CH_PHOTO_DENIED, new PhotoDeniedPacket("muted").write());
        }
        Bukkit.getLogger().info("[Chat Remastered] Handshake complete with " + player.getName()
                + " (protocol v" + clientVersion + ")");
    }
}
