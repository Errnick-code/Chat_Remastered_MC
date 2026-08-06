package dev.errnicraft.chatremastered.server.tgbridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TgBridgeState {

    static final String TGBRIDGE_MOD_ID = "tgbridge";

    volatile boolean available = false;

    Class<?> telegramBridgeClass;
    Object eventsObj;
    Object mcChatMessageHandler;
    Object tgChatMessageHandler;

    final Map<String, Integer> lastTgMessageId = new ConcurrentHashMap<>();

    final Map<Integer, String> tgMessageIdToPlayerSender = new ConcurrentHashMap<>();

    final Map<String, Integer> imageIdToTgMessageId = new ConcurrentHashMap<>();

    final Map<String, UUID> nameToUuid = Collections.synchronizedMap(new java.util.HashMap<>());

    final java.util.Set<String> tgGroupStubSent = Collections.synchronizedSet(new java.util.HashSet<>());

    public boolean isAvailable() {
        return available;
    }

    private static final String TG_INCOMING_PREFIX = "tg:";

    static String tgIncomingKey(String tgSenderName) {
        return TG_INCOMING_PREFIX + tgSenderName;
    }

    public void putLastTgMessageId(String senderName, int tgMessageId) {
        lastTgMessageId.put(senderName, tgMessageId);
    }

    public Integer getLastTgMessageId(String senderName) {
        return lastTgMessageId.get(senderName);
    }

    public void putIncomingTgMessageId(String tgSenderName, int tgMessageId) {
        lastTgMessageId.put(tgIncomingKey(tgSenderName), tgMessageId);
    }

    public void putImageIdToTgMessageId(String imageId, int tgMessageId) {
        imageIdToTgMessageId.put(imageId, tgMessageId);
    }

    public void putTgMessageIdPlayerSender(int tgMessageId, String playerSenderName) {
        tgMessageIdToPlayerSender.put(tgMessageId, playerSenderName);
    }

    public String getTgMessageIdPlayerSender(int tgMessageId) {
        return tgMessageIdToPlayerSender.get(tgMessageId);
    }

    public static int getSenderColorPublic() {
        return getSenderColor();
    }

    public void reset() {
        available = false;
        lastTgMessageId.clear();
        imageIdToTgMessageId.clear();
        tgMessageIdToPlayerSender.clear();
        nameToUuid.clear();
        tgGroupStubSent.clear();
    }

    Object getCompanionInstance() throws ReflectiveOperationException {
        Class<?> bridgeClass = telegramBridgeClass;
        if (bridgeClass == null) {
            return null;
        }
        Object companion = bridgeClass.getField("Companion").get(null);
        return companion.getClass().getMethod("getINSTANCE").invoke(companion);
    }

    Object getBotObject() {
        try {
            Object instance = getCompanionInstance();
            if (instance == null) {
                return null;
            }
            return telegramBridgeClass.getMethod("getBot").invoke(instance);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    Object getDefaultChatObject() {
        try {
            Class<?> configManagerClass = Class.forName("dev.vanutp.tgbridge.common.ConfigManager");
            Object configManagerInstance = configManagerClass.getField("INSTANCE").get(null);
            Object config = configManagerClass.getMethod("getConfig").invoke(configManagerInstance);
            if (config == null) {
                return null;
            }
            return config.getClass().getMethod("getDefaultChat").invoke(config);
        } catch (Exception e) {
            System.out.println("[Chat Remastered] tgbridge DEBUG getDefaultChatObject error: " + e);
            return null;
        }
    }

    String getDefaultChatName() {
        Object defaultChat = getDefaultChatObject();
        if (defaultChat == null) {
            return null;
        }
        try {
            return (String) defaultChat.getClass().getMethod("getName").invoke(defaultChat);
        } catch (ReflectiveOperationException e) {
            System.out.println("[Chat Remastered] tgbridge DEBUG getDefaultChatName error: " + e);
            return null;
        }
    }

    Integer readLastMessageId(String chatName) {
        try {
            Object instance = getCompanionInstance();
            if (instance == null) {
                System.out.println("[Chat Remastered] tgbridge DEBUG readLastMessageId: TelegramBridge.INSTANCE == null");
                return null;
            }
            Object chatManager = telegramBridgeClass.getMethod("getChatManager").invoke(instance);
            if (chatManager == null) {
                System.out.println("[Chat Remastered] tgbridge DEBUG readLastMessageId: chatManager == null");
                return null;
            }

            Field lastMessagesField = chatManager.getClass().getDeclaredField("lastMessages");
            lastMessagesField.setAccessible(true);
            Object rawMap = lastMessagesField.get(chatManager);
            if (!(rawMap instanceof Map)) {
                System.out.println("[Chat Remastered] tgbridge DEBUG readLastMessageId: lastMessages field is not a Map");
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> lastMessages = (Map<String, Object>) rawMap;

            String key = chatName != null ? chatName : getDefaultChatName();
            if (key == null) {
                System.out.println("[Chat Remastered] tgbridge DEBUG readLastMessageId: could not resolve default chat name");
                return null;
            }
            Object tgMessage = lastMessages.get(key);
            if (tgMessage == null) {
                System.out.println("[Chat Remastered] tgbridge DEBUG readLastMessageId: no entry for key='"
                        + key + "', known keys=" + lastMessages.keySet());
                return null;
            }
            return (Integer) tgMessage.getClass().getMethod("getId").invoke(tgMessage);
        } catch (ReflectiveOperationException e) {
            System.out.println("[Chat Remastered] tgbridge DEBUG readLastMessageId error: " + e);
            return null;
        }
    }

    void sendTelegramReply(String senderName, String text, Integer replyToMessageId) throws Exception {
        Object instance = getCompanionInstance();
        if (instance == null) {
            return;
        }
        Object chat = getDefaultChatObject();
        if (chat == null) {
            return;
        }

        String telegramFormat = (String) chat.getClass().getMethod("getTelegramFormat").invoke(chat);

        Class<?> placeholdersClass = Class.forName("dev.vanutp.tgbridge.common.Placeholders");
        Map<String, String> emptyMap = Collections.emptyMap();
        Map<String, String> plainMap = Map.of("username", senderName, "text", text);
        Constructor<?> placeholdersCtor = placeholdersClass.getConstructor(Map.class, Map.class);
        Object placeholders = placeholdersCtor.newInstance(plainMap, emptyMap);

        Class<?> utilsClass = Class.forName("dev.vanutp.tgbridge.common.UtilsKt");
        Method formatMethod = utilsClass.getMethod("formatMiniMessage", String.class, placeholdersClass);
        Object component = formatMethod.invoke(null, telegramFormat, placeholders);

        Class<?> messageContentTextClass = Class.forName("dev.vanutp.tgbridge.common.MessageContentText");
        Constructor<?> ctor = null;
        for (Constructor<?> c : messageContentTextClass.getDeclaredConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 3
                    && params[0].isInstance(component)
                    && params[1] == Integer.class
                    && (params[2] == boolean.class || params[2] == Boolean.class)) {
                ctor = c;
                break;
            }
        }
        if (ctor == null) {
            throw new NoSuchMethodException("MessageContentText(Component, Integer, boolean) constructor not found");
        }
        ctor.setAccessible(true);
        Object content = ctor.newInstance(component, replyToMessageId, false);

        Object chatManager = telegramBridgeClass.getMethod("getChatManager").invoke(instance);
        Class<?> messageContentClass = Class.forName("dev.vanutp.tgbridge.common.MessageContent");
        Class<?> chatConfigClass = Class.forName("dev.vanutp.tgbridge.common.models.ChatConfig");
        Method sendAsyncMethod = chatManager.getClass().getMethod("sendMessageAsync", chatConfigClass, messageContentClass);
        sendAsyncMethod.invoke(chatManager, chat, content);
    }

    static String stripLegacyFormatting(String s) {
        return s.replaceAll("[§&][0-9a-fklmnor]", "");
    }

    private static volatile int cachedSenderColor = -1;
    private static volatile String cachedFormatSource = null;

    static int getSenderColor() {
        String minecraftFormat = readMinecraftFormat();
        if (minecraftFormat == null) {
            return 0x55FFFF;
        }
        if (minecraftFormat.equals(cachedFormatSource) && cachedSenderColor != -1) {
            return cachedSenderColor;
        }
        int color = parseSenderColor(minecraftFormat);
        cachedFormatSource = minecraftFormat;
        cachedSenderColor = color;
        return color;
    }

    private static String readMinecraftFormat() {
        try {
            Class<?> configManagerClass = Class.forName("dev.vanutp.tgbridge.common.ConfigManager");
            Object configManagerInstance = configManagerClass.getField("INSTANCE").get(null);
            Object config = configManagerClass.getMethod("getConfig").invoke(configManagerInstance);
            if (config == null) {
                return null;
            }
            Object defaultChat = config.getClass().getMethod("getDefaultChat").invoke(config);
            if (defaultChat == null) {
                return null;
            }
            return (String) defaultChat.getClass().getMethod("getMinecraftFormat").invoke(defaultChat);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseSenderColor(String miniMessageFormat) {
        java.util.regex.Matcher hexBeforeSender = java.util.regex.Pattern
                .compile("(?i)<#([0-9a-f]{6})>[^<]*<sender>")
                .matcher(miniMessageFormat);
        if (hexBeforeSender.find()) {
            try {
                return Integer.parseInt(hexBeforeSender.group(1), 16);
            } catch (NumberFormatException ignored) {
            }
        }

        java.util.regex.Matcher namedBeforeSender = java.util.regex.Pattern
                .compile("(?i)<(black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray"
                        + "|blue|green|aqua|red|light_purple|yellow|white)>[^<]*<sender>")
                .matcher(miniMessageFormat);
        if (namedBeforeSender.find()) {
            Integer color = namedMiniMessageColor(namedBeforeSender.group(1).toLowerCase(java.util.Locale.ROOT));
            if (color != null) {
                return color;
            }
        }

        return 0x55FFFF;
    }

    private static Integer namedMiniMessageColor(String name) {
        return switch (name) {
            case "black" -> 0x000000;
            case "dark_blue" -> 0x0000AA;
            case "dark_green" -> 0x00AA00;
            case "dark_aqua" -> 0x00AAAA;
            case "dark_red" -> 0xAA0000;
            case "dark_purple" -> 0xAA00AA;
            case "gold" -> 0xFFAA00;
            case "gray" -> 0xAAAAAA;
            case "dark_gray" -> 0x555555;
            case "blue" -> 0x5555FF;
            case "green" -> 0x55FF55;
            case "aqua" -> 0x55FFFF;
            case "red" -> 0xFF5555;
            case "light_purple" -> 0xFF55FF;
            case "yellow" -> 0xFFFF55;
            case "white" -> 0xFFFFFF;
            default -> null;
        };
    }
}
