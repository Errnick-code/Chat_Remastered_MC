package dev.errnicraft.chatremastered.client;

import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.ImageCache;
import dev.errnicraft.chatremastered.ImageDiskCache;
import dev.errnicraft.chatremastered.TcpImageClient;
import dev.errnicraft.chatremastered.network.ModProtocol;
import dev.errnicraft.chatremastered.network.packet.ClientHelloPacket;
import dev.errnicraft.chatremastered.network.packet.EntityChatPacket;
import dev.errnicraft.chatremastered.network.packet.EntityMobChatPacket;
import dev.errnicraft.chatremastered.network.packet.BlockChatPacket;
import dev.errnicraft.chatremastered.network.packet.HandshakeErrorPacket;
import dev.errnicraft.chatremastered.network.packet.ImageChatPacket;
import dev.errnicraft.chatremastered.network.packet.ImageDeletedPacket;
import dev.errnicraft.chatremastered.network.packet.ImageErrorPacket;
import dev.errnicraft.chatremastered.network.packet.ItemChatPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoUnbannedPacket;
import dev.errnicraft.chatremastered.network.packet.ReplyChatPacket;
import dev.errnicraft.chatremastered.network.packet.ServerConfigPacket;
import dev.errnicraft.chatremastered.network.packet.ServerHelloPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import java.net.InetSocketAddress;

public final class NetworkPacketHandlers {

    private NetworkPacketHandlers() {
    }

    public static void register() {
        registerConnectionEvents();
        registerHandshakePackets();
        registerImagePackets();
        registerModerationPackets();
        registerChatPackets();
    }

    private static final int HANDSHAKE_RETRY_MAX_ATTEMPTS = 5;
    private static final long HANDSHAKE_RETRY_DELAY_MS = 4000L;

    private static void registerConnectionEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Minecraft mc = Minecraft.getInstance();
            String host;
            if (mc.isLocalServer()) {
                host = "127.0.0.1";
            } else {
                var addr = handler.getConnection().getRemoteAddress();
                if (addr instanceof InetSocketAddress inet) {
                    String ip = inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString();
                    host = (ip == null || ip.isBlank()) ? "127.0.0.1" : ip;
                } else {
                    String raw = addr != null ? addr.toString() : "";
                    int slash = raw.lastIndexOf('/');
                    String afterSlash = slash >= 0 ? raw.substring(slash + 1) : raw;
                    int colon = afterSlash.indexOf(':');
                    String parsed = (colon >= 0 ? afterSlash.substring(0, colon) : afterSlash).trim();
                    host = parsed.isBlank() ? "127.0.0.1" : parsed;
                }
            }
            ChatRemasteredConfig.setServerHost(host);
            System.out.println("[Chat Remastered] Server host resolved: " + ChatRemasteredConfig.getServerHost());

            startHandshakeWatchdog();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ImageCache.clear();
            ChatRemasteredStore.clear();
            ChatRemasteredConfig.reset();
        });
    }

    private static void startHandshakeWatchdog() {
        Thread thread = new Thread(() -> {
            for (int attempt = 1; attempt <= HANDSHAKE_RETRY_MAX_ATTEMPTS; attempt++) {
                try {
                    Thread.sleep(HANDSHAKE_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (ChatRemasteredConfig.isHandshakeComplete() || ChatRemasteredConfig.isHandshakeIncompatible()) {
                    return;
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.getConnection() == null) {
                    return;
                }
                System.out.println("[Chat Remastered] Handshake not completed yet — retrying ClientHello ("
                        + attempt + "/" + HANDSHAKE_RETRY_MAX_ATTEMPTS + ")");
                mc.execute(() -> {
                    if (mc.getConnection() != null && !ChatRemasteredConfig.isHandshakeComplete()
                            && !ChatRemasteredConfig.isHandshakeIncompatible()) {
                        ClientPlayNetworking.send(new ClientHelloPacket(ModProtocol.MOD_PROTOCOL_VERSION));
                    }
                });
            }
            if (!ChatRemasteredConfig.isHandshakeComplete() && !ChatRemasteredConfig.isHandshakeIncompatible()) {
                System.out.println("[Chat Remastered] Handshake still not completed after "
                        + HANDSHAKE_RETRY_MAX_ATTEMPTS + " retries — giving up for this session.");
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void registerHandshakePackets() {
        ClientPlayNetworking.registerGlobalReceiver(ServerHelloPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    String serverVersion = payload.serverProtocolVersion();
                    ChatRemasteredConfig.setServerHasModVersion(serverVersion);
                    ClientPlayNetworking.send(new ClientHelloPacket(ModProtocol.MOD_PROTOCOL_VERSION));
                    if (!serverVersion.equals(ModProtocol.MOD_PROTOCOL_VERSION)) {
                        ChatComponent chat = Minecraft.getInstance().gui.getChat();

                        boolean serverNewer = NickFormatting.compareModVersions(serverVersion, ModProtocol.MOD_PROTOCOL_VERSION) > 0;
                        if (serverNewer) {
                            chat.addMessage(Component.literal(
                                    "§8[Chat Remastered] §e⚠ На сервере установлена более новая версия мода (v" + serverVersion
                                            + "), у вас v" + ModProtocol.getModVersion() + ". Рекомендуется обновить мод."));
                        } else {
                            chat.addMessage(Component.literal(
                                    "§8[Chat Remastered] §e⚠ Протокол сервера устарел (v" + serverVersion
                                            + "), у вас v" + ModProtocol.getModVersion()
                                            + ". Часть функций может работать нестабильно, пока сервер не обновит мод."));
                        }
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ChatRemasteredConfig.setHandshakeComplete(true);
                    String res = payload.resolution();
                    if (res.equals("360") || res.equals("480") || res.equals("720") || res.equals("HD") || res.equals("2K")) {
                        ChatRemasteredConfig.setResolution(res);
                    }
                    ChatRemasteredConfig.setImagePort(payload.imagePort());
                    ChatRemasteredConfig.setUploadToken(payload.uploadToken());
                    ChatRemasteredConfig.setCooldownSeconds(Math.max(payload.photoCooldownSeconds(), 0));
                    ChatRemasteredConfig.setGifEnabled(payload.gifEnabled());

                    ChatRemasteredConfig.setGifMaxDimServer(payload.gifMaxDim());
                    ChatRemasteredConfig.setMaxPhotosPerMessage(payload.maxPhotosPerMessage());
                    Thread thread = new Thread(() -> pingTcpServer(context.client()));
                    thread.setDaemon(true);
                    thread.start();
                }));

        ClientPlayNetworking.registerGlobalReceiver(HandshakeErrorPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ChatRemasteredConfig.setHandshakeIncompatible(true);
                    Minecraft.getInstance().gui.getChat().addMessage(
                            Component.literal("§8[§bChat Remastered§8] §c❌ " + payload.reason()));
                }));
    }

    private static void pingTcpServer(Minecraft mc) {
        boolean ok = TcpImageClient.ping();
        ChatRemasteredConfig.setServerReachable(ok);
        mc.execute(() -> {
            if (ok) {
                mc.gui.getChat().addMessage(Component.literal("§8[Chat Remastered] §a✔ " + ChatRemasteredConfig.tr(
                        "chat-remastered.connected", (Object) ("§7" + ChatRemasteredConfig.getResolution() + "§a"))));
            } else {
                mc.gui.getChat().addMessage(Component.literal("§8[Chat Remastered] §c✘ " + ChatRemasteredConfig.tr(
                        "chat-remastered.no_tcp_connect", (Object) String.valueOf(ChatRemasteredConfig.getImagePort()))));
            }
        });
    }

    private static void registerImagePackets() {
        ClientPlayNetworking.registerGlobalReceiver(ImageDeletedPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ImageCache.markDeleted(payload.imageId(), payload.byAdmin());
                    if (context.client().screen instanceof dev.errnicraft.chatremastered.ImageViewerScreen viewer) {
                        viewer.cr$onImageDeletedExternally(payload.imageId());
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(ImageErrorPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    String imageId = payload.imageId();
                    String reason = payload.reason();

                    boolean hasLocalData = ImageCache.getFullData(imageId) != null
                            || ImageDiskCache.exists(imageId);
                    if (!hasLocalData) {
                        ImageCache.markError(imageId);
                    }

                    if (hasLocalData && ChatRemasteredStore.shouldSuppressImageErrorPacket(imageId)) {
                        return;
                    }
                    String msg;
                    switch (reason) {
                        case "timeout" -> msg = hasLocalData
                                ? "§eФото отправлено локально, но сервер не подтвердил получение файла. Другие игроки могут не увидеть его."
                                : "§cФото не удалось загрузить: сервер не получил файл вовремя.";
                        case "decode_error" -> msg = "§cФото не удалось обработать: ошибка декодирования.";
                        case "too_many_photos" -> msg = "§cСервер отклонил группу фото: превышен лимит фото за раз.";
                        default -> msg = hasLocalData
                                ? "§eСервер прислал неизвестный ответ (возможно, версии мода расходятся)."
                                : "§cСервер прислал неизвестный ответ (возможно, версии мода расходятся).";
                    }
                    Minecraft.getInstance().gui.getChat().addMessage(Component.literal("§8[Chat Remastered] " + msg));
                }));
    }

    private static void registerModerationPackets() {
        ClientPlayNetworking.registerGlobalReceiver(PhotoUnbannedPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ChatRemasteredConfig.setBanned(false);
                    ChatRemasteredConfig.setMuted(false);
                    ChatRemasteredConfig.setUploadToken(payload.newUploadToken());
                    Minecraft.getInstance().gui.getChat().addMessage(
                            Component.literal("§8[Chat Remastered] §a✔ " + ChatRemasteredConfig.tr("chat-remastered.unmuted")));
                }));

        ClientPlayNetworking.registerGlobalReceiver(PhotoDeniedPacket.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    switch (payload.reason()) {
                        case "banned" -> {
                            ChatRemasteredConfig.setBanned(true);
                            Minecraft.getInstance().gui.getChat().addMessage(
                                    Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.banned")));
                        }
                        case "muted" -> {
                            ChatRemasteredConfig.setMuted(true);
                            Minecraft.getInstance().gui.getChat().addMessage(
                                    Component.literal("§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.muted")));
                        }

                        case "muted_silent" -> ChatRemasteredConfig.setMuted(true);

                        default -> Minecraft.getInstance().gui.getChat().addMessage(
                                Component.literal("§8[Chat Remastered] §e⚠ Сервер прислал неизвестный ответ — недоступно в этой версии мода."));
                    }
                }));
    }

    private static final int GROUP_ATTACH_MAX_ATTEMPTS = 20;
    private static final long GROUP_ATTACH_RETRY_DELAY_MS = 150L;

    private static void attachGroupedPhotoWithRetry(String groupId, String imageId, int width, int height, int attempt) {
        boolean attached = ChatRemasteredStore.attachToGroup(groupId, imageId);
        if (attached) {
            ChatMessageRenderer.registerAndDownloadGroupedImage(imageId, width, height);
            return;
        }
        if (attempt >= GROUP_ATTACH_MAX_ATTEMPTS) {

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ChatMessageRenderer.addImageToChat(mc, imageId, "", "", width, height, Component.empty());
            }
            return;
        }
        new Thread(() -> {
            try {
                Thread.sleep(GROUP_ATTACH_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Minecraft.getInstance().execute(() ->
                    attachGroupedPhotoWithRetry(groupId, imageId, width, height, attempt + 1));
        }).start();
    }

    private static void registerChatPackets() {
        ClientPlayNetworking.registerGlobalReceiver(ImageChatPacket.TYPE, (payload, context) -> {

            boolean alreadyShown = ChatRemasteredStore.getMessageList().stream()
                    .anyMatch(m -> m.getImageId().equals(payload.imageId()));
            if (!alreadyShown) {
                ChatRemasteredStore.markSuppressPhotoMessage(payload.sender(), payload.caption());
            }
            boolean isGroupedFollower = !payload.groupId().isEmpty() && payload.groupIndex() > 0;
            context.client().execute(() -> {
                boolean alreadyShown2 = ChatRemasteredStore.getMessageList().stream()
                        .anyMatch(m -> m.getImageId().equals(payload.imageId()));
                if (alreadyShown2) {
                    return;
                }
                if (isGroupedFollower) {

                    attachGroupedPhotoWithRetry(payload.groupId(), payload.imageId(), payload.width(), payload.height(), 0);
                    return;
                }
                if (!payload.replyToSender().isEmpty() || !payload.replyToImageId().isEmpty()) {

                    ChatMessageRenderer.addImageReplyToChat(context.client(), payload.imageId(), payload.sender(),
                            payload.caption(), payload.width(), payload.height(), payload.senderComponent(),
                            payload.replyToSender(), payload.replyToText(), payload.replyToImageId(), payload.groupId());
                } else {
                    ChatMessageRenderer.addImageToChat(context.client(), payload.imageId(), payload.sender(),
                            payload.caption(), payload.width(), payload.height(), payload.senderComponent(),
                            false, payload.groupId());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ReplyChatPacket.TYPE, (payload, context) ->
                context.client().execute(() -> ChatMessageRenderer.addReplyToChat(context.client(), payload.sender(),
                        payload.text(), payload.senderComponent(), payload.replyToSender(), payload.replyToText(),
                        payload.replyToImageId())));

        ClientPlayNetworking.registerGlobalReceiver(EntityChatPacket.TYPE, (payload, context) ->
                context.client().execute(() -> EntityChatRenderer.addEntityToChat(context.client(), payload.sender(),
                        payload.senderComponent(), payload.targetPlayerName(), payload.behavior(), payload.caption())));

        ClientPlayNetworking.registerGlobalReceiver(EntityMobChatPacket.TYPE, (payload, context) ->
                context.client().execute(() -> EntityChatRenderer.addEntityMobToChat(context.client(), payload.sender(),
                        payload.senderComponent(), payload.entityNamespace(), payload.entityPath(), payload.entityNbt(),
                        payload.behavior(), payload.size(), payload.offsetX(), payload.offsetY(), payload.caption())));

        ClientPlayNetworking.registerGlobalReceiver(ItemChatPacket.TYPE, (payload, context) ->
                context.client().execute(() -> EntityChatRenderer.addItemToChat(context.client(), payload.sender(),
                        payload.senderComponent(), payload.itemNamespace(), payload.itemPath(), payload.itemNbt(),
                        payload.caption())));

        ClientPlayNetworking.registerGlobalReceiver(BlockChatPacket.TYPE, (payload, context) ->
                context.client().execute(() -> EntityChatRenderer.addBlockToChat(context.client(), payload.sender(),
                        payload.senderComponent(), payload.blockNamespace(), payload.blockPath(), payload.blockState(),
                        payload.caption())));
    }
}
