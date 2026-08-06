package dev.errnicraft.chatremastered;

import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Method;

public class ChatHeadsCompat {

    private static boolean initialized = false;
    private static boolean available = false;

    private static Method getLineData = null;
    private static Method setLineData = null;

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

        }
    }

    public static @Nullable Object captureLineData() {
        init();
        if (!available) return null;
        try {
            return getLineData.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void restoreLineData(@Nullable Object data) {
        init();
        if (!available || data == null) return;
        try {
            setLineData.invoke(null, data);
        } catch (Throwable ignored) {}
    }

    public static void clearLineData() {
        init();
        if (!available) return;
        try {
            setLineData.invoke(null, headDataEmpty);
        } catch (Throwable ignored) {}
    }
}
