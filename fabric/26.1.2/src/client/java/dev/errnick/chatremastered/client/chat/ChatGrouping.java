package dev.errnick.chatremastered.client.chat;

import dev.errnick.chatremastered.ChatRemasteredConfig;
import dev.errnick.chatremastered.ChatRemasteredStore;
import dev.errnick.chatremastered.ChatTimeHolder;
import dev.errnick.chatremastered.mixin.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.ObjectContents;

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

    public static synchronized boolean shouldSuppress(Component message, GuiMessageSource source) {
        if (!ChatRemasteredConfig.getGroupIdenticalMessages()) {
            reset();
            return false;
        }
        if (!eligible(message, source)) {
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
        int count = COUNTS.merge(headAddedTime, 1, Integer::sum);
        if (!cr$chatOpen()) {
            cr$bumpGroup(count);
        }
        return true;
    }

    public static synchronized void remember(Component message, GuiMessageSource source) {
        if (!ChatRemasteredConfig.getGroupIdenticalMessages()) {
            reset();
            return;
        }
        if (!eligible(message, source)) {
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


    private static boolean cr$chatOpen() {
        return Minecraft.getInstance().screen instanceof ChatScreen;
    }


    private static void cr$bumpGroup(int count) {
        List<GuiMessage.Line> trimmed = trimmedLines();
        if (trimmed == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return;
        }
        final int oldTime = headAddedTime;
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage> all = acc.getAllMessages();
        GuiMessage root = null;
        for (GuiMessage message : all) {
            if (message.addedTime() == oldTime) {
                root = message;
                break;
            }
        }
        if (root == null) {
            return;
        }
        int newTime = Math.max(mc.gui.getGuiTicks(), oldTime + 1);
        while (ChatLineIndex.contains(trimmed, newTime)) {
            newTime++;
        }
        GuiMessage fresh = new GuiMessage(newTime, root.content(), root.signature(), root.source(), root.tag());
        trimmed.removeIf(line -> line.addedTime() == oldTime);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).addedTime() == oldTime) {
                all.remove(i);
            }
        }
        acc.cr$addToDisplayQueue(fresh);
        acc.cr$addToQueue(fresh);
        ChatLineIndex.invalidate();
        COUNTS.remove(oldTime);
        COUNTS.put(newTime, count);
        headAddedTime = newTime;
    }
    private static boolean eligible(Component message, GuiMessageSource source) {
        if (message == null || source == GuiMessageSource.SYSTEM_CLIENT) {
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
        if (component.getContents() instanceof ObjectContents) {
            return true;
        }
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
