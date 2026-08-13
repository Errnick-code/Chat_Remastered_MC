package dev.errnicraft.chatremastered.server;

import dev.errnicraft.chatremastered.server.tgbridge.TgBridgeCompat;
import dev.errnicraft.chatremastered.network.ModProtocol;
import dev.errnicraft.chatremastered.network.packet.PhotoUnbannedPacket;
import dev.errnicraft.chatremastered.network.packet.ServerHelloPacket;
import dev.errnicraft.chatremastered.server.config.ServerConfig;
import dev.errnicraft.chatremastered.server.config.ServerConfigLoader;
import dev.errnicraft.chatremastered.server.moderation.BanHammerCompat;
import dev.errnicraft.chatremastered.tcp.ImageTcpServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.util.Iterator;
import java.util.UUID;

public final class ChatRemasteredLifecycle {

    public static void register() {
        registerServerStarted();
        registerServerStopping();
        registerBanHammerUnmutePoll();
        registerJoin();
        registerDisconnect();
    }

    private static void registerServerStarted() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ChatRemasteredState.currentServer = server;
            ServerConfig config = ServerConfigLoader.loadOrCreate(server.getServerDirectory());
            ChatRemasteredState.cachedConfig = config;
            ChatRemasteredState.banList.load(server.getServerDirectory());
            ChatRemasteredState.muteList.load(server.getServerDirectory());

            BanHammerCompat.registerPunishmentListener(uuid -> {
                ChatRemasteredState.bhMutedByUs.add(uuid);
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        ServerPlayNetworking.send(player,
                                new dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket("muted_silent"));
                    }
                });
            });

            if (server.isDedicatedServer()) {
                ImageTcpServer.initCacheDir(server.getServerDirectory().toFile());
            }
            ImageTcpServer.setMaxUploadBytes(8L * 1024 * 1024);
            ImageTcpServer.startIfNeeded(config.imagePort());
            System.out.println("[Chat Remastered] TCP server started at boot on port " + config.imagePort());

            TgBridgeCompat.init(server);
        });
    }

    private static void registerServerStopping() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ChatRemasteredState.currentServer = null;
            ChatRemasteredState.pendingBroadcasts.clear();
            ChatRemasteredState.externalPendingBroadcasts.clear();
            ChatRemasteredState.groupStubSent.clear();
            ImageTcpServer.stop();
            TgBridgeCompat.reset();
        });
    }

    private static void registerBanHammerUnmutePoll() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) {
                return;
            }
            if (!BanHammerCompat.isPresent()) {
                return;
            }

            Iterator<UUID> iterator = ChatRemasteredState.bhMutedByUs.iterator();
            while (iterator.hasNext()) {
                UUID uuid = iterator.next();
                if (BanHammerCompat.isMuted(uuid)) {
                    continue;
                }
                iterator.remove();
                boolean wasInOurList = ChatRemasteredState.muteList.remove(uuid);
                if (wasInOurList) {
                    ChatRemasteredState.muteList.save(server);
                }
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                if (!ChatRemasteredState.isPhotoBanned(uuid)) {
                    String newToken = UUID.randomUUID().toString();
                    ChatRemasteredState.playerTokens.put(uuid, newToken);
                    ImageTcpServer.addToken(newToken);
                    ServerPlayNetworking.send(player, new PhotoUnbannedPacket(newToken));
                    System.out.println("[Chat Remastered] BanHammer unmute detected for " + player.getName().getString()
                            + " — sent PhotoUnbannedPacket");
                }
            }
        });
    }

    private static void registerJoin() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();

            Thread thread = new Thread(() -> {
                try {

                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    return;
                }
                server.execute(() ->
                        ServerPlayNetworking.send(player, new ServerHelloPacket(ModProtocol.MOD_PROTOCOL_VERSION)));

                try {
                    Thread.sleep(10_000L);
                } catch (InterruptedException e) {
                    return;
                }
                if (!ChatRemasteredState.hasModInstalled(player.getUUID())) {
                    server.execute(() -> sendDownloadHint(player));
                }
            });
            thread.setDaemon(true);
            thread.start();
        });
    }

    private static void sendDownloadHint(ServerPlayer player) {
        String modrinthUrl = "https://modrinth.com/mod/chat-remastered";
        Component msg = Component.empty()
                .append(Component.literal("[").withStyle(s -> s.withColor(0x555555)))
                .append(Component.literal("Chat Remastered").withStyle(s -> s.withColor(0x00b0f0).withBold(true)))
                .append(Component.literal("] ").withStyle(s -> s.withColor(0x555555)))
                .append(Component.literal("This server has Chat Remastered — an enhanced chat mod with photos, replies and more. ")
                        .withStyle(s -> s.withColor(0xaaaaaa)))
                .append(Component.literal("⬇ Download here").withStyle(s -> s
                        .withColor(0x1bd96a)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(modrinthUrl)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(modrinthUrl).withStyle(s2 -> s2.withColor(0x888888))))));
        player.sendSystemMessage(msg);
    }

    private static void registerDisconnect() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, sender) -> {
            UUID uuid = handler.getPlayer().getUUID();
            ChatRemasteredState.modPlayers.remove(uuid);
            String token = ChatRemasteredState.playerTokens.remove(uuid);
            if (token != null) {
                ImageTcpServer.removeToken(token);
            }
        });
    }

    private ChatRemasteredLifecycle() {
    }
}
