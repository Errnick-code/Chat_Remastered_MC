package dev.errnicraft.chatremastered.server.tgbridge;

import dev.errnicraft.chatremastered.server.ChatRemasteredState;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class TgRecipientsFilter {

    static void register(TgBridgeState state) throws Exception {
        Class<?> eventsClass = state.eventsObj.getClass();
        Object handler = eventsClass.getMethod("getRECIPIENTS").invoke(state.eventsObj);
        Class<?> handlerClass = handler.getClass();

        Class<?> priorityClass = Class.forName("dev.vanutp.tgbridge.common.EventPriority");
        Object lowestPriority = priorityClass.getField("LOWEST").get(null);

        Method addListenerMethod = null;
        for (Method m : handlerClass.getMethods()) {
            if ("addListener".equals(m.getName()) && m.getParameterCount() == 2
                    && m.getParameterTypes()[0] == priorityClass
                    && m.getParameterTypes()[1].getName().equals("java.util.function.Consumer")) {
                addListenerMethod = m;
                break;
            }
        }

        InvocationHandler invocationHandler = (proxy, method, args) -> {
            if ("accept".equals(method.getName()) && args != null && args.length > 0) {
                try {
                    filterRecipientsEvent(args[0]);
                } catch (Exception e) {
                    System.out.println("[Chat Remastered] tgbridge RECIPIENTS filter error: " + e.getMessage());
                }
            }
            return null;
        };
        Object consumer = Proxy.newProxyInstance(
                TgRecipientsFilter.class.getClassLoader(), new Class<?>[]{Consumer.class}, invocationHandler);
        addListenerMethod.invoke(handler, lowestPriority, consumer);
    }

    private static void filterRecipientsEvent(Object event) throws Exception {
        Class<?> eventClass = event.getClass();
        Method originalEventGetter;
        try {
            originalEventGetter = eventClass.getMethod("getOriginalEvent");
        } catch (NoSuchMethodException e) {
            return;
        }
        Object originalEvent = originalEventGetter.invoke(event);
        if (originalEvent == null) {
            return;
        }

        if (!originalEvent.getClass().getName().equals("dev.vanutp.tgbridge.common.models.TgbridgeTgChatMessageEvent")) {
            return;
        }

        Object message = originalEvent.getClass().getMethod("getMessage").invoke(originalEvent);
        if (message == null) {
            return;
        }
        Object replyToMessage = message.getClass().getMethod("getReplyToMessage").invoke(message);
        if (replyToMessage == null) {
            return;
        }

        Method getRecipients = eventClass.getMethod("getRecipients");
        Method setRecipients = eventClass.getMethod("setRecipients", List.class);

        @SuppressWarnings("unchecked")
        List<Object> recipients = (List<Object>) getRecipients.invoke(event);
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        List<Object> filtered = recipients.stream()
                .filter(player -> {
                    try {
                        UUID uuid = (UUID) player.getClass().getMethod("getUuid").invoke(player);
                        return uuid == null || !hasModInstalledSafe(uuid);
                    } catch (Exception e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());

        if (filtered.size() != recipients.size()) {
            setRecipients.invoke(event, filtered);
        }
    }

    private static boolean hasModInstalledSafe(UUID uuid) {
        try {
            return ChatRemasteredState.hasModInstalled(uuid);
        } catch (Exception e) {
            return false;
        }
    }

    private TgRecipientsFilter() {
    }
}
