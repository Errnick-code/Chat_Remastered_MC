package dev.errnick.chatremastered.server.tgbridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Consumer;

final class TgMcListener {

    static void register(TgBridgeState state) throws Exception {
        Class<?> eventsClass = state.eventsObj.getClass();
        Object handler = eventsClass.getMethod("getMC_CHAT_MESSAGE").invoke(state.eventsObj);
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
                    onMcChatMessageEvent(state, args[0]);
                } catch (Exception e) {
                    System.out.println("[Chat Remastered] tgbridge MC_CHAT_MESSAGE hook error: " + e);
                }
            }
            return null;
        };
        Object consumer = Proxy.newProxyInstance(
                TgMcListener.class.getClassLoader(), new Class<?>[]{Consumer.class}, invocationHandler);
        state.mcChatMessageHandler = handler;
        addListenerMethod.invoke(handler, consumer);
    }

    private static void onMcChatMessageEvent(TgBridgeState state, Object event) throws Exception {
        Class<?> eventClass = event.getClass();
        Object sender = eventClass.getMethod("getSender").invoke(event);
        if (sender == null) {
            return;
        }

        Class<?> senderClass = sender.getClass();
        UUID uuid = (UUID) senderClass.getMethod("getUuid").invoke(sender);
        String name = (String) senderClass.getMethod("getName").invoke(sender);
        if (uuid == null || name == null) {
            return;
        }
        state.nameToUuid.put(name, uuid);

        String messageText = "";
        try {
            Object t = eventClass.getMethod("getText").invoke(event);
            messageText = t != null ? t.toString() : "";
        } catch (Exception ignored) {
        }

        state.enqueueMcMessage(name, messageText);
    }

    private TgMcListener() {
    }
}
