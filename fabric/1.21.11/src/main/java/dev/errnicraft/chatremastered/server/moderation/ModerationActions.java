package dev.errnicraft.chatremastered.server.moderation;

import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoUnbannedPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import dev.errnicraft.chatremastered.tcp.ImageTcpServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ModerationActions {

    public static boolean isBanHammerMuted(UUID uuid) {
        return BanHammerCompat.isMuted(uuid);
    }

    public static boolean isEffectivelyMuted(UUID uuid) {
        return ChatRemasteredState.isMuted(uuid) || isBanHammerMuted(uuid);
    }

    public static void mutePlayer(UUID uuid, MinecraftServer server) {
        boolean usedBanHammer = BanHammerCompat.mute(uuid, server);
        if (usedBanHammer) {
            ChatRemasteredState.bhMutedByUs.add(uuid);

            return;
        }

        ChatRemasteredState.muteList.add(uuid);
        ChatRemasteredState.muteList.save(server);
        System.out.println("[Chat Remastered] Muted " + uuid + " (fallback, no BanHammer)");
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        server.execute(() -> {
            if (player != null) {
                ServerPlayNetworking.send(player, new PhotoDeniedPacket("muted"));
            }
        });
    }

    public static void unmutePlayer(UUID uuid, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);

        BanHammerCompat.unmute(uuid);
        ChatRemasteredState.bhMutedByUs.remove(uuid);

        ChatRemasteredState.muteList.remove(uuid);
        ChatRemasteredState.muteList.save(server);

        if (!ChatRemasteredState.isPhotoBanned(uuid)) {
            String newToken = UUID.randomUUID().toString();
            ChatRemasteredState.playerTokens.put(uuid, newToken);
            ImageTcpServer.addToken(newToken);
            server.execute(() -> {
                if (player != null) {
                    ServerPlayNetworking.send(player, new PhotoUnbannedPacket(newToken));
                }
            });
        }
    }

    public static void banPlayer(UUID uuid, MinecraftServer server) {
        ChatRemasteredState.banList.add(uuid);

        String token = ChatRemasteredState.playerTokens.get(uuid);
        if (token != null) {
            ImageTcpServer.removeToken(token);
        }
        ChatRemasteredState.banList.save(server);
    }

    public static void unbanPlayer(UUID uuid, MinecraftServer server) {
        ChatRemasteredState.banList.remove(uuid);

        String newToken = UUID.randomUUID().toString();
        ChatRemasteredState.playerTokens.put(uuid, newToken);
        ImageTcpServer.addToken(newToken);
        ChatRemasteredState.banList.save(server);

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                ServerPlayNetworking.send(player, new PhotoUnbannedPacket(newToken));
            }
        });
    }

    private ModerationActions() {
    }
}
