package dev.errnick.chatremasteredplugin.net.handler;

import dev.errnick.chatremasteredplugin.ChatRemasteredPlugin;
import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.config.ServerConfig;
import dev.errnick.chatremasteredplugin.config.ServerConfigLoader;
import dev.errnick.chatremasteredplugin.moderation.ModerationActions;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import dev.errnick.chatremasteredplugin.net.packet.ImageChatPacket;
import dev.errnick.chatremasteredplugin.net.packet.ImageErrorPacket;
import dev.errnick.chatremasteredplugin.net.packet.ImageUploadedPacket;
import dev.errnick.chatremasteredplugin.net.packet.PhotoDeniedPacket;
import dev.errnick.chatremasteredplugin.text.LegacyNickParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;

public final class ImageUploadHandler implements PluginMessageListener {

    private final ChatRemasteredPlugin plugin;

    public ImageUploadHandler(ChatRemasteredPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(ChatRemasteredPlugin plugin) {
        MessengerService.registerIncoming(ModProtocol.CH_IMAGE_UPLOADED, new ImageUploadHandler(plugin));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player senderPlayer, byte[] message) {
        ImageUploadedPacket payload = ImageUploadedPacket.read(new PacketByteBuf(message));

        if (!PluginState.hasModInstalled(senderPlayer.getUniqueId())) {
            Bukkit.getLogger().info("[Chat Remastered] " + senderPlayer.getName()
                    + " sent ImageUploadedPacket without handshake — ignoring");
            return;
        }

        if (PluginState.isPhotoBanned(senderPlayer.getUniqueId())) {
            Bukkit.getLogger().info("[Chat Remastered] " + senderPlayer.getName() + " tried to send photo but is banned");
            MessengerService.send(senderPlayer, ModProtocol.CH_PHOTO_DENIED, new PhotoDeniedPacket("banned").write());
            return;
        }

        if (ModerationActions.isEffectivelyMuted(senderPlayer.getUniqueId())) {
            Bukkit.getLogger().info("[Chat Remastered] " + senderPlayer.getName() + " tried to send photo but is muted");
            return;
        }

        String imageId = payload.imageId();
        int groupCount = Math.max(1, payload.groupCount());
        ServerConfig cfg = ServerConfigLoader.loadOrCreate(plugin.getDataFolder().toPath());
        if (groupCount > cfg.maxPhotosPerMessage()) {
            Bukkit.getLogger().info("[Chat Remastered] " + senderPlayer.getName()
                    + " tried to send a group of " + groupCount + " photos, server limit is " + cfg.maxPhotosPerMessage());
            MessengerService.send(senderPlayer, ModProtocol.CH_IMAGE_ERROR,
                    new ImageErrorPacket(imageId, "too_many_photos").write());
            return;
        }

        String sender = senderPlayer.getName();
        String senderComponentJson = LegacyNickParser.parseLegacyNickToJson(sender);
        String caption = payload.caption();
        int width = payload.width();
        int height = payload.height();
        UUID senderUuid = senderPlayer.getUniqueId();

        broadcastVanillaChat(sender, caption, payload.groupId(), groupCount);
        broadcastImagePacket(imageId, sender, caption, width, height, senderComponentJson, payload, senderUuid);

        PluginState.PendingBroadcast pendingBroadcast = new PluginState.PendingBroadcast(
                sender, caption, senderUuid, width, height, payload.replyToSender(),
                payload.groupId(), payload.groupIndex(), groupCount);
        PluginState.pendingBroadcasts.put(imageId, pendingBroadcast);
        PluginState.imageOwners.put(imageId, senderUuid);

        scheduleUploadTimeout(imageId);
    }

    private void broadcastVanillaChat(String sender, String caption, String groupId, int groupCount) {
        if (groupCount > 1 && !groupId.isEmpty()) {
            if (!PluginState.groupStubSent.add(groupId)) {
                return;
            }
        }
        String photoLabel = groupCount > 1 ? "[" + groupCount + " photo]" : "[photo]";
        String chatText = (caption != null && !caption.isEmpty()) ? caption : photoLabel;
        Component line = Component.text("<" + sender + "> " + chatText);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!PluginState.hasModInstalled(p.getUniqueId())) {
                p.sendMessage(line);
            }
        }
        if (groupCount > 1 && !groupId.isEmpty()) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin,
                    () -> PluginState.groupStubSent.remove(groupId), 20L * 30);
        }
    }

    private void broadcastImagePacket(
            String imageId,
            String sender,
            String caption,
            int width,
            int height,
            String senderComponentJson,
            ImageUploadedPacket payload,
            UUID senderUuid
    ) {
        byte[] data = new ImageChatPacket(imageId, sender, caption, width, height, senderComponentJson,
                payload.replyToSender(), payload.replyToText(), payload.replyToImageId(),
                payload.groupId(), payload.groupIndex(), payload.groupCount(), "", 0).write();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (PluginState.hasModInstalled(player.getUniqueId()) && !player.getUniqueId().equals(senderUuid)) {
                MessengerService.send(player, ModProtocol.CH_IMAGE_CHAT, data);
            }
        }
    }

    private void scheduleUploadTimeout(String imageId) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            PluginState.PendingBroadcast stillPending = PluginState.pendingBroadcasts.remove(imageId);
            if (stillPending == null) {
                return;
            }

            if (dev.errnick.chatremasteredplugin.tcp.ImageTcpServer.hasCached(imageId)) {
                Bukkit.getLogger().info("[Chat Remastered] Timeout race resolved: " + imageId + " already cached, skipping error");
                return;
            }
            Bukkit.getLogger().info("[Chat Remastered] Timeout (120s) waiting for upload " + imageId + " from " + stillPending.sender());
            Player senderPlayer = Bukkit.getPlayer(stillPending.senderUuid());
            if (senderPlayer != null) {
                MessengerService.send(senderPlayer, ModProtocol.CH_IMAGE_ERROR,
                        new ImageErrorPacket(imageId, "timeout").write());
            }
        }, 20L * 120);
    }
}
