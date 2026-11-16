package dev.errnick.chatremasteredplugin.moderation;

import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.packet.PhotoDeniedPacket;
import dev.errnick.chatremasteredplugin.net.packet.PhotoUnbannedPacket;
import dev.errnick.chatremasteredplugin.tcp.ImageTcpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class ModerationActions {

    public static boolean isEffectivelyMuted(UUID uuid) {
        return PluginState.isMuted(uuid);
    }

    public static void mutePlayer(UUID uuid) {
        PluginState.muteList.add(uuid);
        PluginState.muteList.save();
        Bukkit.getLogger().info("[Chat Remastered] Muted " + uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            MessengerService.send(player, dev.errnick.chatremasteredplugin.net.ModProtocol.CH_PHOTO_DENIED,
                    new PhotoDeniedPacket("muted").write());
        }
    }

    public static void unmutePlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);

        PluginState.muteList.remove(uuid);
        PluginState.muteList.save();

        if (!PluginState.isPhotoBanned(uuid)) {
            String newToken = UUID.randomUUID().toString();
            PluginState.playerTokens.put(uuid, newToken);
            ImageTcpServer.addToken(newToken);
            if (player != null) {
                MessengerService.send(player, dev.errnick.chatremasteredplugin.net.ModProtocol.CH_PHOTO_UNBANNED,
                        new PhotoUnbannedPacket(newToken).write());
            }
        }
    }

    public static void banPlayer(UUID uuid) {
        PluginState.banList.add(uuid);

        String token = PluginState.playerTokens.get(uuid);
        if (token != null) {
            ImageTcpServer.removeToken(token);
        }
        PluginState.banList.save();
    }

    public static void unbanPlayer(UUID uuid) {
        PluginState.banList.remove(uuid);

        String newToken = UUID.randomUUID().toString();
        PluginState.playerTokens.put(uuid, newToken);
        ImageTcpServer.addToken(newToken);
        PluginState.banList.save();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            MessengerService.send(player, dev.errnick.chatremasteredplugin.net.ModProtocol.CH_PHOTO_UNBANNED,
                    new PhotoUnbannedPacket(newToken).write());
        }
    }

    private ModerationActions() {
    }
}
