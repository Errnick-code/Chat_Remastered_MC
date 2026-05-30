package dev.errnicraft.chatremastered;

import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Method;

/**
 * Совместимость с модом Chat Heads (dzwdz.chat_heads).
 * Использует reflection — не требует chat_heads как зависимость.
 *
 * Chat Heads хранит данные о текущей голове в статическом поле ChatHeads.lastSenderData.
 * Когда наш mixin делает ci.cancel() + повторный addMessage(),
 * нам нужно сохранить lastSenderData и восстановить его перед повторным вызовом,
 * иначе chat heads теряет привязку головы к сообщению.
 */
public class ChatHeadsCompat {

    private static boolean initialized = false;
    private static boolean available = false;

    // ChatHeads.getLineData() / setLineData(HeadData)
    private static Method getLineData = null;
    private static Method setLineData = null;
    // HeadData.EMPTY
    private static Object headDataEmpty = null;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> chatHeads = Class.forName("dzwdz.chat_heads.ChatHeads");
            Class<?> headData  = Class.forName("dzwdz.chat_heads.HeadData");
            getLineData   = chatHeads.getMethod("getLineData");
            setLineData   = chatHeads.getMethod("setLineData", headData);
            headDataEmpty = headData.getField("EMPTY").get(null);
            available = true;
        } catch (Throwable t) {
            // chat_heads не установлен — всё нормально
        }
    }

    /** Возвращает текущий HeadData из chat heads, или null если мод не установлен. */
    public static @Nullable Object captureLineData() {
        init();
        if (!available) return null;
        try {
            return getLineData.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Восстанавливает HeadData в chat heads перед повторным addMessage().
     * Если data == null или мод не установлен — ничего не делает.
     */
    public static void restoreLineData(@Nullable Object data) {
        init();
        if (!available || data == null) return;
        try {
            setLineData.invoke(null, data);
        } catch (Throwable ignored) {}
    }

    /**
     * Сбрасывает lineData в EMPTY после того как addMessage() завершился.
     * Это нужно чтобы случайно не "протечь" голову на следующее сообщение.
     */
    public static void clearLineData() {
        init();
        if (!available) return;
        try {
            setLineData.invoke(null, headDataEmpty);
        } catch (Throwable ignored) {}
    }
}
