package dev.errnick.chatremastered.client.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChatLineIndex {

    private static List<GuiMessage.Line> cachedList;
    private static int cachedSize = -1;
    private static int cachedFirstTime;
    private static int cachedLastTime;
    private static Map<Integer, int[]> blocks = new HashMap<>();

    private ChatLineIndex() {
    }

    public static synchronized int headIndex(List<GuiMessage.Line> trimmed, int addedTime) {
        int[] block = block(trimmed, addedTime);
        return block == null ? -1 : block[1];
    }

    public static synchronized int blockMin(List<GuiMessage.Line> trimmed, int addedTime) {
        int[] block = block(trimmed, addedTime);
        return block == null ? -1 : block[0];
    }

    public static synchronized boolean contains(List<GuiMessage.Line> trimmed, int addedTime) {
        return block(trimmed, addedTime) != null;
    }

    public static synchronized void invalidate() {
        cachedList = null;
        cachedSize = -1;
    }

    private static int[] block(List<GuiMessage.Line> trimmed, int addedTime) {
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        ensure(trimmed);
        return blocks.get(addedTime);
    }

    private static void ensure(List<GuiMessage.Line> trimmed) {
        int size = trimmed.size();
        int firstTime = trimmed.get(0).addedTime();
        int lastTime = trimmed.get(size - 1).addedTime();
        if (trimmed == cachedList && size == cachedSize
                && firstTime == cachedFirstTime && lastTime == cachedLastTime) {
            return;
        }

        Map<Integer, int[]> rebuilt = new HashMap<>(Math.max(16, size / 2));
        for (int i = 0; i < size; i++) {
            int time = trimmed.get(i).addedTime();
            int[] block = rebuilt.get(time);
            if (block == null) {
                rebuilt.put(time, new int[]{i, i});
            } else {
                if (i < block[0]) {
                    block[0] = i;
                }
                if (i > block[1]) {
                    block[1] = i;
                }
            }
        }

        blocks = rebuilt;
        cachedList = trimmed;
        cachedSize = size;
        cachedFirstTime = firstTime;
        cachedLastTime = lastTime;
    }
}
