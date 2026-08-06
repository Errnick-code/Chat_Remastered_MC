package dev.errnicraft.chatremastered.server.tgbridge;

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

        String chatName = (String) eventClass.getMethod("getChatName").invoke(event);

        Integer idBefore;
        try {
            idBefore = state.readLastMessageId(chatName);
        } catch (Exception e) {
            System.out.println("[Chat Remastered] tgbridge lastMessages read error (before): " + e);
            idBefore = null;
        }

        final Integer idBeforeFinal = idBefore;
        Thread thread = new Thread(() -> {
            Integer tgMessageId = null;
            int attempts = 0;
            while (attempts < 20) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    return;
                }
                attempts++;
                try {
                    Integer current = state.readLastMessageId(chatName);
                    if (current != null && !current.equals(idBeforeFinal)) {
                        tgMessageId = current;
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("[Chat Remastered] tgbridge lastMessages read error: " + e);
                }
            }
            if (tgMessageId != null) {
                state.lastTgMessageId.put(name, tgMessageId);
            } else {
                System.out.println("[Chat Remastered] tgbridge: failed to detect tgMessageId for '"
                        + name + "' after " + attempts + " attempts");
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private TgMcListener() {
    }
}
