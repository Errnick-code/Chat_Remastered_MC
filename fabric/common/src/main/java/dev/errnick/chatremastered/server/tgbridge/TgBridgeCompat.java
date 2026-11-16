package dev.errnick.chatremastered.server.tgbridge;

import dev.errnick.chatremastered.server.ChatRemasteredState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public final class TgBridgeCompat {

    private static final TgBridgeState STATE = new TgBridgeState();

    public static boolean isAvailable() {
        return STATE.isAvailable();
    }

    public static TgBridgeState getState() {
        return STATE;
    }

    public static String resolveLoginNameByAnyNamePublic(String name) {
        return resolveLoginNameByAnyName(name);
    }

    public static void init(MinecraftServer server) {
        if (!FabricLoader.getInstance().isModLoaded(TgBridgeState.TGBRIDGE_MOD_ID)) {
            System.out.println("[Chat Remastered] tgbridge not found — reply MC<->TG integration disabled");
            return;
        }
        try {
            STATE.telegramBridgeClass = Class.forName("dev.vanutp.tgbridge.common.TelegramBridge");
            Class<?> eventsClass = Class.forName("dev.vanutp.tgbridge.common.TgbridgeEvents");
            STATE.eventsObj = eventsClass.getField("INSTANCE").get(null);

            TgMcListener.register(STATE);
            TgIncomingListener.register(STATE);
            TgRecipientsFilter.register(STATE);

            STATE.available = true;
            System.out.println("[Chat Remastered] tgbridge found — reply MC<->TG integration active");
        } catch (Exception e) {
            STATE.available = false;
            System.out.println("[Chat Remastered] tgbridge integration init error: " + e.getMessage());
        }
    }

    public static void reset() {
        STATE.reset();
    }

    public static void onReplySent(String replyToSenderName, String replyToText, String text, String senderName) {
        onReplySent(replyToSenderName, replyToText, "", text, senderName);
    }

    public static void onReplySent(String replyToSenderName, String replyToText, String replyToImageId,
                                   String text, String senderName) {
        if (!STATE.available) {
            return;
        }

        String plainSender = TgBridgeState.stripLegacyFormatting(replyToSenderName);
        String plainText = TgBridgeState.stripLegacyFormatting(replyToText);
        plainText = plainText.replaceAll("[​‌‍﻿⁨⁩]", "").trim();
        String loginName = resolveLoginNameByAnyName(plainSender);
        Integer tgMessageId = resolveByImageId(replyToImageId);
        if (tgMessageId != null) {
            System.out.println("[Chat Remastered] tgbridge: reply target resolved by imageId="
                    + replyToImageId + " -> tgMessageId=" + tgMessageId);
        }
        if (tgMessageId == null && loginName != null) {
            tgMessageId = STATE.findMirrorReplyId(loginName, plainText);
        }
        if (tgMessageId == null) {
            tgMessageId = STATE.findMirrorReplyId(plainSender, plainText);
        }
        if (tgMessageId == null) {
            tgMessageId = STATE.findMirrorIdByText(plainText);
        }
        if (tgMessageId == null && loginName != null) {
            tgMessageId = STATE.findUniqueMirrorReplyIdByText(plainText);
        }
        if (tgMessageId == null && loginName != null) {
            tgMessageId = STATE.findIncomingReplyId(loginName, plainText);
        }
        if (tgMessageId == null) {
            tgMessageId = STATE.findIncomingReplyId(plainSender, plainText);
        }
        if (tgMessageId == null) {
            tgMessageId = STATE.findUniqueReplyIdByText(plainText);
        }
        if (tgMessageId == null) {
            System.out.println("[Chat Remastered] tgbridge: target not found for '"
                    + plainSender + "' / '" + plainText + "' - sending without reply");
        }

        try {
            String chatName = STATE.getDefaultChatName();
            Integer idBefore;
            try {
                idBefore = STATE.readLastMessageId(chatName);
            } catch (Exception e) {
                idBefore = null;
            }

            STATE.sendTelegramReply(senderName, text, tgMessageId);

            final Integer idBeforeFinal = idBefore;
            Thread thread = new Thread(() -> {
                Integer newTgMessageId = null;
                int attempts = 0;
                while (attempts < 20) {
                    try {
                        Thread.sleep(250L);
                    } catch (InterruptedException e) {
                        return;
                    }
                    attempts++;
                    try {
                        Integer current = STATE.readLastMessageId(chatName);
                        if (current != null && !current.equals(idBeforeFinal)) {
                            newTgMessageId = current;
                            break;
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
                if (newTgMessageId != null) {
                    STATE.lastTgMessageId.put(senderName, newTgMessageId);
                    STATE.tgMessageIdToPlayerSender.put(newTgMessageId, senderName);
                }
            });
            thread.setDaemon(true);
            thread.start();
        } catch (Exception e) {
            System.out.println("[Chat Remastered] Error sending reply to Telegram: " + e.getMessage());
        }
    }

    private static Integer resolveByImageId(String replyToImageId) {
        if (replyToImageId == null || replyToImageId.isEmpty()) {
            return null;
        }
        if (replyToImageId.startsWith("tg_")) {
            try {
                return Integer.parseInt(replyToImageId.substring(3));
            } catch (NumberFormatException ignored) {
            }
        }
        return STATE.imageIdToTgMessageId.get(replyToImageId);
    }

    private static String resolveLoginNameByAnyName(String name) {
        MinecraftServer server = ChatRemasteredState.currentServer;
        if (server == null) {
            return null;
        }
        String plain = TgBridgeState.stripLegacyFormatting(name);
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> {
                    String loginName = p.getName().getString();
                    String displayName = p.getDisplayName() != null ? p.getDisplayName().getString() : loginName;
                    return loginName.equals(name)
                            || loginName.equals(plain)
                            || TgBridgeState.stripLegacyFormatting(displayName).equals(plain);
                })
                .map(p -> p.getName().getString())
                .findFirst()
                .orElse(null);
    }

    public static void sendPlainTextStub(String senderName, String text) {
        if (!STATE.available) {
            return;
        }
        try {
            STATE.sendTelegramReply(senderName, text, null);
        } catch (Exception e) {
            System.out.println("[Chat Remastered] tgbridge: error sending plain text stub to Telegram: " + e);
        }
    }

    public static void onImageSent(String imageId, String senderName, String caption, byte[] fileBytes,
                                    String fileName, String replyToSenderName, String groupId, int groupCount) {

        if (FabricLoader.getInstance().isModLoaded("tgphotobridge")) {
            return;
        }
        if (!STATE.available) {
            return;
        }
        boolean isGroup = groupCount > 1 && !groupId.isEmpty();
        if (isGroup && !STATE.tgGroupStubSent.add(groupId)) {
            return;
        }
        try {
            String photoLabel = isGroup ? "[" + groupCount + " photo]" : "[Photo]";
            String stubText = !caption.isEmpty()
                    ? photoLabel + " " + TgBridgeState.stripLegacyFormatting(caption)
                    : photoLabel;
            Integer tgMessageId = null;
            if (!replyToSenderName.isEmpty()) {
                tgMessageId = STATE.lastTgMessageId.get(replyToSenderName);
                if (tgMessageId == null) {
                    String loginName = resolveLoginNameByAnyName(replyToSenderName);
                    if (loginName != null) {
                        tgMessageId = STATE.lastTgMessageId.get(loginName);
                    }
                }
                if (tgMessageId == null) {
                    tgMessageId = STATE.lastTgMessageId.get(TgBridgeState.tgIncomingKey(replyToSenderName));
                }
            }
            STATE.sendTelegramReply(senderName, stubText, tgMessageId);
        } catch (Exception e) {
            System.out.println("[Chat Remastered] tgbridge: error sending photo stub to Telegram: " + e);
        } finally {
            if (isGroup) {
                Thread cleanup = new Thread(() -> {
                    try {
                        Thread.sleep(30_000L);
                    } catch (InterruptedException ignored) {
                    }
                    STATE.tgGroupStubSent.remove(groupId);
                });
                cleanup.setDaemon(true);
                cleanup.start();
            }
        }
    }

    public static void onImageDeleted(String imageId) {
        if (!STATE.available) {
            return;
        }
        Integer tgMessageId = STATE.imageIdToTgMessageId.remove(imageId);
        if (tgMessageId == null) {
            return;
        }

        try {
            Object chat = STATE.getDefaultChatObject();
            if (chat == null) {
                return;
            }
            Object bot = STATE.getBotObject();
            if (bot == null) {
                return;
            }
            long chatId = (long) chat.getClass().getMethod("getChatId").invoke(chat);

            var deleteMessageAsync = bot.getClass().getMethod(
                    "deleteMessageAsync", long.class, int.class);
            deleteMessageAsync.invoke(bot, chatId, tgMessageId);
        } catch (Exception e) {
            System.out.println("[Chat Remastered] tgbridge: error deleting photo/gif message from Telegram: " + e);
        }
    }

    private TgBridgeCompat() {
    }
}
