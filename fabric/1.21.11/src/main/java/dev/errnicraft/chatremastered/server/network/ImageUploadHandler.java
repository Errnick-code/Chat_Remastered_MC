package dev.errnicraft.chatremastered.server.network;

import dev.errnicraft.chatremastered.network.packet.ImageChatPacket;
import dev.errnicraft.chatremastered.network.packet.ImageErrorPacket;
import dev.errnicraft.chatremastered.network.packet.ImageUploadedPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import dev.errnicraft.chatremastered.server.moderation.ModerationActions;
import dev.errnicraft.chatremastered.tcp.ImageTcpServer;
import dev.errnicraft.chatremastered.text.LegacyNickParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ImageUploadHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ImageUploadedPacket.TYPE, (payload, context) -> {
            ServerPlayer senderPlayer = context.player();

            if (!ChatRemasteredState.hasModInstalled(senderPlayer.getUUID())) {
                System.out.println("[Chat Remastered] " + senderPlayer.getName().getString()
                        + " sent ImageUploadedPacket without handshake — ignoring");
                return;
            }

            if (ChatRemasteredState.isPhotoBanned(senderPlayer.getUUID())) {
                System.out.println("[Chat Remastered] " + senderPlayer.getName().getString()
                        + " tried to send photo but is banned");
                ServerPlayNetworking.send(senderPlayer, new PhotoDeniedPacket("banned"));
                return;
            }

            if (ModerationActions.isEffectivelyMuted(senderPlayer.getUUID())) {
                System.out.println("[Chat Remastered] " + senderPlayer.getName().getString()
                        + " tried to send photo but is muted");
                return;
            }

            MinecraftServer server = context.server();
            String imageId = payload.imageId();
            int groupCount = Math.max(1, payload.groupCount());
            dev.errnicraft.chatremastered.server.config.ServerConfig cfg =
                    dev.errnicraft.chatremastered.server.config.ServerConfigLoader.loadOrCreate(server.getServerDirectory());
            if (groupCount > cfg.maxPhotosPerMessage()) {
                System.out.println("[Chat Remastered] " + senderPlayer.getName().getString()
                        + " tried to send a group of " + groupCount + " photos, server limit is " + cfg.maxPhotosPerMessage());
                ServerPlayNetworking.send(senderPlayer, new ImageErrorPacket(imageId, "too_many_photos"));
                return;
            }

            String sender = senderPlayer.getName().getString();
            Component rawComp = senderPlayer.getDisplayName() != null
                    ? senderPlayer.getDisplayName()
                    : Component.literal(sender);
            Component senderComp = LegacyNickParser.parseLegacyNick(rawComp, sender);
            String caption = payload.caption();
            int width = payload.width();
            int height = payload.height();
            var senderUuid = senderPlayer.getUUID();

            broadcastVanillaChat(server, senderPlayer, sender, caption, payload.groupId(), groupCount);
            broadcastImagePacket(server, imageId, sender, caption, width, height, senderComp, payload, senderUuid);

            ChatRemasteredState.PendingBroadcast pendingBroadcast = new ChatRemasteredState.PendingBroadcast(
                    sender, caption, senderUuid, width, height, payload.replyToSender(),
                    payload.groupId(), payload.groupIndex(), groupCount);
            ChatRemasteredState.pendingBroadcasts.put(imageId, pendingBroadcast);
            ChatRemasteredState.externalPendingBroadcasts.put(imageId, pendingBroadcast);
            ChatRemasteredState.imageOwners.put(imageId, senderUuid);

            scheduleUploadTimeout(server, imageId);
        });
    }

    private static void broadcastVanillaChat(MinecraftServer server, ServerPlayer senderPlayer, String sender,
                                              String caption, String groupId, int groupCount) {
        if (groupCount > 1 && !groupId.isEmpty()) {
            if (!ChatRemasteredState.groupStubSent.add(groupId)) {
                return;
            }
        }
        String photoLabel = groupCount > 1 ? "[" + groupCount + " photo]" : "[photo]";
        String chatText = (caption != null && !caption.isEmpty()) ? caption : photoLabel;
        server.execute(() -> {

            ChatType.Bound chatType = ChatType.bind(
                    ChatType.CHAT,
                    senderPlayer.level().registryAccess(),
                    Component.literal(sender)
            );
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {

                if (!ChatRemasteredState.hasModInstalled(p.getUUID())) {
                    p.connection.sendDisguisedChatMessage(Component.literal(chatText), chatType);
                }
            }
        });
        if (groupCount > 1 && !groupId.isEmpty()) {

            Thread cleanup = new Thread(() -> {
                try {
                    Thread.sleep(30_000L);
                } catch (InterruptedException ignored) {
                }
                ChatRemasteredState.groupStubSent.remove(groupId);
            });
            cleanup.setDaemon(true);
            cleanup.start();
        }
    }

    private static void broadcastImagePacket(
            MinecraftServer server,
            String imageId,
            String sender,
            String caption,
            int width,
            int height,
            Component senderComp,
            ImageUploadedPacket payload,
            java.util.UUID senderUuid
    ) {
        ImageChatPacket packet = new ImageChatPacket(imageId, sender, caption, width, height, senderComp,
                payload.replyToSender(), payload.replyToText(), payload.replyToImageId(),
                payload.groupId(), payload.groupIndex(), payload.groupCount());
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ChatRemasteredState.hasModInstalled(player.getUUID()) && !player.getUUID().equals(senderUuid)) {
                    ServerPlayNetworking.send(player, packet);
                }
            }
        });
    }

    private static void scheduleUploadTimeout(MinecraftServer server, String imageId) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(120_000L);
            } catch (InterruptedException e) {
                return;
            }
            ChatRemasteredState.PendingBroadcast stillPending = ChatRemasteredState.pendingBroadcasts.remove(imageId);
            ChatRemasteredState.externalPendingBroadcasts.remove(imageId);
            if (stillPending == null) {
                return;
            }

            if (ImageTcpServer.hasCached(imageId)) {
                System.out.println("[Chat Remastered] Timeout race resolved: " + imageId + " already cached, skipping error");
                return;
            }
            System.out.println("[Chat Remastered] Timeout (120s) waiting for upload " + imageId + " from " + stillPending.sender());
            server.execute(() -> {
                ServerPlayer senderPlayer = server.getPlayerList().getPlayer(stillPending.senderUuid());
                if (senderPlayer != null) {
                    ServerPlayNetworking.send(senderPlayer, new ImageErrorPacket(imageId, "timeout"));
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private ImageUploadHandler() {
    }
}
