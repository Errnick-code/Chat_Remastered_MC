package dev.errnicraft.chatremastered.server.network;

import dev.errnicraft.chatremastered.network.ModProtocol;
import dev.errnicraft.chatremastered.network.packet.ClientHelloPacket;
import dev.errnicraft.chatremastered.network.packet.HandshakeErrorPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.network.packet.ServerConfigPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import dev.errnicraft.chatremastered.server.config.ServerConfig;
import dev.errnicraft.chatremastered.server.config.ServerConfigLoader;
import dev.errnicraft.chatremastered.server.moderation.ModerationActions;
import dev.errnicraft.chatremastered.tcp.ImageTcpServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class HandshakeHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ClientHelloPacket.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var server = context.server();
            String clientVersion = payload.clientProtocolVersion();

            boolean versionMismatch = !clientVersion.equals(ModProtocol.MOD_PROTOCOL_VERSION);
            if (versionMismatch) {

                ServerPlayNetworking.send(
                        player,
                        new HandshakeErrorPacket(
                                "§eВерсия мода отличается: сервер v" + ModProtocol.MOD_PROTOCOL_VERSION
                                        + ", клиент v" + clientVersion
                                        + ". Часть функций может работать нестабильно."
                        )
                );
                System.out.println("[Chat Remastered] " + player.getName().getString()
                        + " has mismatched mod version (continuing anyway): client v" + clientVersion
                        + ", server v" + ModProtocol.MOD_PROTOCOL_VERSION);
            }

            ChatRemasteredState.modPlayers.add(player.getUUID());
            String uploadToken = UUID.randomUUID().toString();
            ChatRemasteredState.playerTokens.put(player.getUUID(), uploadToken);

            if (!ChatRemasteredState.isPhotoBanned(player.getUUID())) {
                ImageTcpServer.addToken(uploadToken);
            }

            ServerConfig config = ServerConfigLoader.loadOrCreate(server.getServerDirectory());
            ServerPlayNetworking.send(
                    player,
                    new ServerConfigPacket(config.resolution(), config.imagePort(), uploadToken,
                            config.photoCooldownSeconds(), config.gifEnabled(), config.gifMaxDim(),
                            config.maxPhotosPerMessage())
            );

            if (ChatRemasteredState.isPhotoBanned(player.getUUID())) {
                ServerPlayNetworking.send(player, new PhotoDeniedPacket("banned"));
            } else if (ModerationActions.isEffectivelyMuted(player.getUUID())) {
                ServerPlayNetworking.send(player, new PhotoDeniedPacket("muted"));
            }
            System.out.println("[Chat Remastered] Handshake complete with " + player.getName().getString()
                    + " (protocol v" + clientVersion + ")");
        });
    }

    private HandshakeHandler() {
    }
}
