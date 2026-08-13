package dev.errnicraft.chatremastered.server.tgbridge;

import dev.errnicraft.chatremastered.network.packet.ReplyChatPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

final class TgIncomingListener {

    static void register(TgBridgeState state) throws Exception {
        Class<?> eventsClass = state.eventsObj.getClass();
        Object handler = eventsClass.getMethod("getTG_CHAT_MESSAGE").invoke(state.eventsObj);
        Class<?> handlerClass = handler.getClass();

        Method addListenerMethod = null;
        for (Method m : handlerClass.getMethods()) {
            if ("addListener".equals(m.getName()) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].getName().equals("java.util.function.Consumer")) {
                addListenerMethod = m;
                break;
            }
        }

        InvocationHandler invocationHandler = (proxy, method, args) -> {
            if ("accept".equals(method.getName()) && args != null && args.length > 0) {
                try {
                    onTgChatMessageEvent(state, args[0]);
                } catch (Exception e) {
                    System.out.println("[Chat Remastered] tgbridge TG_CHAT_MESSAGE hook error: " + e.getMessage());
                }
            }
            return null;
        };
        Object consumer = Proxy.newProxyInstance(
                TgIncomingListener.class.getClassLoader(), new Class<?>[]{Consumer.class}, invocationHandler);
        state.tgChatMessageHandler = handler;
        addListenerMethod.invoke(handler, consumer);
    }

    private static void onTgChatMessageEvent(TgBridgeState state, Object event) throws Exception {
        Class<?> eventClass = event.getClass();
        Object message = eventClass.getMethod("getMessage").invoke(event);
        if (message == null) {
            return;
        }
        Class<?> messageClass = message.getClass();

        String senderNameRaw = (String) messageClass.getMethod("getSenderName").invoke(message);
        String senderName = senderNameRaw != null ? senderNameRaw : "Telegram";
        String effectiveTextRaw = (String) messageClass.getMethod("getEffectiveText").invoke(message);
        String effectiveText = effectiveTextRaw != null ? effectiveTextRaw : "";
        int tgMessageId = (int) messageClass.getMethod("getMessageId").invoke(message);

        state.lastTgMessageId.put(TgBridgeState.tgIncomingKey(senderName), tgMessageId);

        if (hasMedia(messageClass, message)) {
            eventClass.getMethod("setCancelled", boolean.class).invoke(event, true);
            return;
        }

        Object replyToMessage = messageClass.getMethod("getReplyToMessage").invoke(message);
        if (replyToMessage == null) {
            return;
        }
        Class<?> replyClass = replyToMessage.getClass();

        Object pinnedMessage = messageClass.getMethod("getPinnedMessage").invoke(message);
        int replyMessageId = (int) replyClass.getMethod("getMessageId").invoke(replyToMessage);
        Object replyThreadIdObj = replyClass.getMethod("getMessageThreadId").invoke(replyToMessage);
        if (pinnedMessage != null || (replyThreadIdObj instanceof Integer && replyMessageId == (Integer) replyThreadIdObj)) {
            return;
        }

        if (hasMedia(replyClass, replyToMessage)) {
            String replySenderName = resolveReplySenderName(state, replyClass, replyToMessage, replyMessageId);

            MinecraftServer server = ChatRemasteredState.currentServer;
            if (server == null) {
                return;
            }
            String replyImageId = "tg_" + replyMessageId;
            server.execute(() -> broadcastImageReplyFromTelegram(
                    server, senderName, effectiveText, replySenderName, replyImageId));
            return;
        }

        String replySenderName = resolveReplySenderName(state, replyClass, replyToMessage, replyMessageId);
        String replyEffectiveTextRaw = (String) replyClass.getMethod("getEffectiveText").invoke(replyToMessage);
        String replyEffectiveTextUnstripped = replyEffectiveTextRaw != null ? replyEffectiveTextRaw : "";

        final String replyEffectiveText = stripMirroredNickPrefix(replyEffectiveTextUnstripped, replySenderName);

        MinecraftServer server = ChatRemasteredState.currentServer;
        if (server == null) {
            return;
        }

        eventClass.getMethod("setCancelled", boolean.class).invoke(event, true);
        server.execute(() ->
                broadcastReplyFromTelegram(server, senderName, effectiveText, replySenderName, replyEffectiveText));
    }

    private static String resolveReplySenderName(TgBridgeState state, Class<?> replyClass, Object replyToMessage,
                                                   int replyMessageId) throws Exception {
        String fromBot = state.getTgMessageIdPlayerSender(replyMessageId);
        if (fromBot != null) {
            return fromBot;
        }
        String replySenderNameRaw = (String) replyClass.getMethod("getSenderName").invoke(replyToMessage);
        return replySenderNameRaw != null ? replySenderNameRaw : "";
    }

    private static String stripMirroredNickPrefix(String text, String senderName) {
        if (text == null || text.isEmpty() || senderName == null || senderName.isEmpty()) {
            return text;
        }
        String bracketPrefix = "[" + senderName + "] ";
        if (text.startsWith(bracketPrefix)) {
            return text.substring(bracketPrefix.length());
        }
        String colonPrefix = senderName + ": ";
        if (text.startsWith(colonPrefix)) {
            return text.substring(colonPrefix.length());
        }
        String anglePrefix = "<" + senderName + "> ";
        if (text.startsWith(anglePrefix)) {
            return text.substring(anglePrefix.length());
        }
        return text;
    }

    private static boolean hasMedia(Class<?> messageClass, Object message) throws Exception {
        Object photo = messageClass.getMethod("getPhoto").invoke(message);
        if (photo instanceof java.util.List<?> photoList && !photoList.isEmpty()) {
            return true;
        }
        Object animation = messageClass.getMethod("getAnimation").invoke(message);
        if (animation != null) {
            return true;
        }
        Object document = messageClass.getMethod("getDocument").invoke(message);
        return document != null;
    }

    private static void broadcastImageReplyFromTelegram(MinecraftServer server, String senderName, String text,
                                                          String replyToSender, String replyToImageId) {
        int senderColor = TgBridgeState.getSenderColor();
        Component senderComp = Component.literal(senderName).withStyle(s -> s.withColor(senderColor));
        ReplyChatPacket replyPacket = new ReplyChatPacket(senderName, senderComp, text, replyToSender, "", replyToImageId);
        Component plainMsg = Component.empty()
                .append(Component.literal("<").withStyle(s -> s.withColor(senderColor)))
                .append(senderComp)
                .append(Component.literal(">").withStyle(s -> s.withColor(senderColor)))
                .append(Component.literal(" " + text));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getChatVisibility() == ChatVisiblity.FULL) {
                if (ChatRemasteredState.hasModInstalled(p.getUUID())) {
                    ServerPlayNetworking.send(p, replyPacket);
                } else {
                    p.sendSystemMessage(plainMsg);
                }
            }
        }
    }

    private static void broadcastReplyFromTelegram(MinecraftServer server, String senderName, String text,
                                                     String replyToSender, String replyToText) {
        int senderColor = TgBridgeState.getSenderColor();
        Component senderComp = Component.literal(senderName).withStyle(s -> s.withColor(senderColor));
        ReplyChatPacket replyPacket = new ReplyChatPacket(senderName, senderComp, text, replyToSender, replyToText, "");
        Component plainMsg = Component.empty()
                .append(Component.literal("<").withStyle(s -> s.withColor(senderColor)))
                .append(senderComp)
                .append(Component.literal(">").withStyle(s -> s.withColor(senderColor)))
                .append(Component.literal(" " + text));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getChatVisibility() == ChatVisiblity.FULL) {
                if (ChatRemasteredState.hasModInstalled(p.getUUID())) {
                    ServerPlayNetworking.send(p, replyPacket);
                } else {
                    p.sendSystemMessage(plainMsg);
                }
            }
        }
    }

    private TgIncomingListener() {
    }
}
