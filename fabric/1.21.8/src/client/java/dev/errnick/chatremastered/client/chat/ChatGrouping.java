package dev.errnick.chatremastered.client.chat;

import dev.errnick.chatremastered.ChatRemasteredConfig;
import dev.errnick.chatremastered.ChatRemasteredStore;
import dev.errnick.chatremastered.ChatTimeHolder;
import dev.errnick.chatremastered.mixin.ChatComponentAccessor;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatGrouping {

    private static final int MAX_GROUPS = 512;

    private static final Map<Integer, Integer> COUNTS = new LinkedHashMap<>(64, 0.75f) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
            return size() > MAX_GROUPS;
        }
    };

    private static String headText;
    private static int headAddedTime = Integer.MIN_VALUE;

    private ChatGrouping() {
    }

    public static synchronized boolean shouldSuppress(Component message, GuiMessageTag tag) {
        if (!ChatRemasteredConfig.getGroupIdenticalMessages()) {
            reset();
            return false;
        }
        if (!eligible(message, tag)) {
            return false;
        }
        if (headText == null || !headText.equals(message.getString())) {
            return false;
        }
        List<GuiMessage.Line> trimmed = trimmedLines();
        if (trimmed == null || !ChatLineIndex.contains(trimmed, headAddedTime)) {
            return false;
        }
        if (ChatRemasteredStore.getReplyForAddedTime(headAddedTime) != null) {
            return false;
        }
        COUNTS.merge(headAddedTime, 1, Integer::sum);
        return true;
    }

    public static synchronized void remember(Component message, GuiMessageTag tag) {
        if (!ChatRemasteredConfig.getGroupIdenticalMessages()) {
            reset();
            return;
        }
        if (!eligible(message, tag)) {
            headText = null;
            headAddedTime = Integer.MIN_VALUE;
            return;
        }
        headText = message.getString();
        headAddedTime = ChatTimeHolder.lastAddedTime;
        COUNTS.putIfAbsent(headAddedTime, 1);
    }

    public static synchronized Map<Integer, Integer> counts() {
        return COUNTS;
    }

    public static synchronized void prune(List<GuiMessage.Line> trimmed) {
        Iterator<Map.Entry<Integer, Integer>> iterator = COUNTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            if (!ChatLineIndex.contains(trimmed, entry.getKey())) {
                iterator.remove();
            }
        }
    }

    public static synchronized void reset() {
        COUNTS.clear();
        headText = null;
        headAddedTime = Integer.MIN_VALUE;
    }

    private static boolean eligible(Component message, GuiMessageTag tag) {
        if (message == null) {
            return false;
        }
        if (tag != null && "System".equals(tag.logTag())) {
            return false;
        }
        if (hasObjectContents(message)) {
            return false;
        }
        String text = message.getString();
        if (text.isEmpty()) {
            return false;
        }
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return false;
        }
        return !text.contains("[\uD83D\uDCF7]");
    }

    private static boolean hasObjectContents(Component component) {
        if (component.getStyle().getClickEvent() != null) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (hasObjectContents(sibling)) {
                return true;
            }
        }
        return false;
    }

    private static List<GuiMessage.Line> trimmedLines() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return null;
        }
        return ((ChatComponentAccessor) mc.gui.getChat()).getTrimmedMessages();
    }
}
