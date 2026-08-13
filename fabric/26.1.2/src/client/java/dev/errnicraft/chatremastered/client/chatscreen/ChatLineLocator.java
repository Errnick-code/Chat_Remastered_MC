package dev.errnicraft.chatremastered.client.chatscreen;

import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.ImageCache;
import dev.errnicraft.chatremastered.PendingImageState;
import dev.errnicraft.chatremastered.mixin.ChatComponentAccessor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class ChatLineLocator {

    private ChatLineLocator() {
    }

    public static String stripHeadPlaceholders(String text) {
        if (text == null) return null;
        return text.replaceAll("\\[[^\\]]*\\s*head\\]", "").trim();
    }

    public static String formattedCharSequenceToString(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    public static int getLineIndexAt(Minecraft mc, int mouseX, int mouseY) {
        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        int scrollPos = acc.getChatScrollbarPos();

        float scale = (float) mc.options.chatScale().get().doubleValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        int linesPerPage = chat.getLinesPerPage();
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);

        float localX = (mouseX / scale) - 4f;
        float localY = mouseY / scale;

        for (int i = 0; i < Math.min(trimmed.size() - scrollPos, linesPerPage); i++) {
            int entryBottom = chatBottom - i * entryHeight;
            int entryTop = entryBottom - entryHeight;
            if (localY >= entryTop && localY < entryBottom && localX >= -4f && localX < chatWidthPx + 4f)
                return i + scrollPos;
        }
        return -1;
    }

    public static void drawLineHighlight(GuiGraphicsExtractor graphics, Minecraft mc, int lineIdx) {
        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        int scrollPos = acc.getChatScrollbarPos();
        if (lineIdx < 0 || lineIdx >= trimmed.size()) return;

        int targetTime = trimmed.get(lineIdx).addedTime();

        float scale = (float) mc.options.chatScale().get().doubleValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        int linesPerPage = chat.getLinesPerPage();
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);

        int guiLeft  = 0;
        int guiRight = Math.round((chatWidthPx + 8) * scale);

        int color = 0x38000000;

        for (int i = 0; i < Math.min(trimmed.size() - scrollPos, linesPerPage); i++) {
            int idx = i + scrollPos;
            if (trimmed.get(idx).addedTime() != targetTime) continue;
            int entryBottom = chatBottom - i * entryHeight;
            int entryTop    = entryBottom - entryHeight;
            int screenTop    = Math.round(entryTop    * scale);
            int screenBottom = Math.round(entryBottom * scale);
            graphics.fill(guiLeft, screenTop, guiRight, screenBottom, color);
        }
    }

    public static int getAddedTimeAt(Minecraft mc, int mouseX, int mouseY) {
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        int scrollPos = acc.getChatScrollbarPos();

        float scale = (float) mc.options.chatScale().get().doubleValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        int linesPerPage = mc.gui.getChat().getLinesPerPage();
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
        float localX = (mouseX / scale) - 4f;
        float localY = mouseY / scale;

        for (int i = 0; i < Math.min(trimmed.size() - scrollPos, linesPerPage); i++) {
            int lineIdx = i + scrollPos;
            if (lineIdx >= trimmed.size()) break;
            GuiMessage.Line line = trimmed.get(lineIdx);
            int entryBottom = chatBottom - i * entryHeight;
            int entryTop = entryBottom - entryHeight;
            if (localY >= entryTop && localY < entryBottom && localX >= 0 && localX < chatWidthPx)
                return line.addedTime();
        }
        return -1;
    }

    public static int findLineIndexForImageId(Minecraft mc, String imageId) {
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        int scrollPos = acc.getChatScrollbarPos();
        int linesPerPage = mc.gui.getChat().getLinesPerPage();

        int targetTime = -1;
        for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
            if (imgMsg.getImageId().equals(imageId)) {
                targetTime = imgMsg.getAddedTime();
                break;
            }
        }
        if (targetTime < 0) return -1;

        final int tt = targetTime;
        for (int i = 0; i < Math.min(trimmed.size() - scrollPos, linesPerPage); i++) {
            if (trimmed.get(i + scrollPos).addedTime() == tt) return i + scrollPos;
        }
        return -1;
    }

    public static String getMessageTextForImageId(Minecraft mc, String imageId) {
        for (ChatRemasteredStore.ImageMessage img : ChatRemasteredStore.getMessageList()) {
            if (img.getImageId().equals(imageId)) {
                String caption = img.getCaption();
                return (caption == null || caption.isBlank()) ? null : caption;
            }
        }
        return null;
    }

    public static String getImageIdForMessageAt(Minecraft mc, int mouseX, int mouseY) {
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        int scrollPos = acc.getChatScrollbarPos();

        float scale = (float) mc.options.chatScale().get().doubleValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        int linesPerPage = mc.gui.getChat().getLinesPerPage();
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);

        float localX = (mouseX / scale) - 4f;
        float localY = mouseY / scale;

        for (int i = 0; i < Math.min(trimmed.size() - scrollPos, linesPerPage); i++) {
            int lineIdx = i + scrollPos;
            if (lineIdx >= trimmed.size()) break;
            GuiMessage.Line line = trimmed.get(lineIdx);
            int entryBottom = chatBottom - i * entryHeight;
            int entryTop = entryBottom - entryHeight;
            if (localY >= entryTop && localY < entryBottom && localX >= 0 && localX < chatWidthPx) {
                int lineTime = line.addedTime();

                for (ChatRemasteredStore.ImageMessage img : ChatRemasteredStore.getMessageList()) {
                    if (!img.getDismissed() && img.getAddedTime() == lineTime)
                        return img.getImageId();
                }
            }
        }
        return null;
    }

    public static void scrollToMessage(Minecraft mc, int targetAddedTime, HighlightState highlight) {
        if (targetAddedTime < 0) return;
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        int linesPerPage = mc.gui.getChat().getLinesPerPage();

        int maxIdx = -1;
        for (int i = 0; i < trimmed.size(); i++) {
            GuiMessage.Line line = trimmed.get(i);
            if (line.addedTime() != targetAddedTime) continue;
            maxIdx = i;
        }

        if (maxIdx < 0) return;

        int maxScroll = Math.max(0, trimmed.size() - linesPerPage);
        int newPos = Math.min(maxIdx, maxScroll);
        acc.setChatScrollbarPos(newPos);

        highlight.highlightAddedTime = targetAddedTime;
        highlight.highlightStartMs = System.currentTimeMillis();
    }

    public static void renderHighlight(GuiGraphicsExtractor graphics, Minecraft mc, HighlightState highlight, int highlightDurationMs) {
        if (highlight.highlightAddedTime < 0) return;
        long elapsed = System.currentTimeMillis() - highlight.highlightStartMs;
        if (elapsed >= highlightDurationMs) { highlight.highlightAddedTime = -1; return; }
        float progress = (float) elapsed / highlightDurationMs;

        float alpha01;
        float FADE_IN = 0.20f;
        if (progress < FADE_IN) {
            float t = progress / FADE_IN;
            alpha01 = t * t;
        } else {
            float t = 1f - (progress - FADE_IN) / (1f - FADE_IN);
            alpha01 = t * t;
        }
        int alpha = Math.round(0x88 * alpha01);
        if (alpha <= 0) { highlight.highlightAddedTime = -1; return; }
        int color = (alpha << 24) | 0x1E6FD4;

        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        int scrollPos = acc.getChatScrollbarPos();
        int linesPerPage = mc.gui.getChat().getLinesPerPage();

        float scale = (float) mc.options.chatScale().get().doubleValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));

        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);

        int chatBottomGui = mc.getWindow().getGuiScaledHeight() - 40;
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
        int guiRight = Math.round((chatWidthPx + 8) * scale);

        for (int i = 0; i < Math.min(trimmed.size() - scrollPos, linesPerPage); i++) {
            int idx = i + scrollPos;
            if (idx >= trimmed.size()) break;
            GuiMessage.Line line = trimmed.get(idx);
            if (line.addedTime() != highlight.highlightAddedTime) continue;

            boolean[] hasChars = {false};
            line.content().accept((charIdx, style, codePoint) -> { hasChars[0] = true; return false; });
            if (!hasChars[0]) continue;

            int entryBottom = chatBottom - i * entryHeight;
            int entryTop    = entryBottom - entryHeight;
            int guiTop    = chatBottomGui - (int)(entryBottom * scale);
            int guiBottom = chatBottomGui - (int)(entryTop    * scale);
            graphics.fill(0, guiTop, guiRight, guiBottom, color);
        }
    }

    public static final class HighlightState {
        public int highlightAddedTime = -1;
        public long highlightStartMs = 0L;
    }

    public static int updateCursorAndHover(GuiGraphicsExtractor graphics, Minecraft mc, ChatScreen self, int mouseX, int mouseY,
                                            boolean menuOpen, int hoveredMsgLineIn,
                                            int pendingRowLeft, int pendingRowRight, int pendingRowTop, int pendingRowBottom,
                                            boolean pendingRowNonEmpty) {
        int hoveredMsgLine = hoveredMsgLineIn;

        for (ChatRemasteredStore.ImageMessage msg : ChatRemasteredStore.getMessageList()) {
            if (msg.getDismissed() || msg.getRowCardBounds().isEmpty()) continue;
            for (var entry : msg.getRowCardBounds().entrySet()) {
                String otherId = entry.getKey();
                int[] b = entry.getValue();
                if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                    boolean isDeleted = ImageCache.isDeleted(otherId);
                    boolean isError = ImageCache.isError(otherId);
                    String hint;
                    String colorPrefix;
                    if (isDeleted) {
                        hint = ImageCache.isDeletedByAdmin(otherId)
                                ? ChatRemasteredConfig.tr("chat-remastered.hover_deleted")
                                : ChatRemasteredConfig.tr("chat-remastered.hover_deleted_by_author");
                        colorPrefix = "§c";
                    }
                    else if (isError) { hint = ChatRemasteredConfig.tr("chat-remastered.hover_error"); colorPrefix = "§e"; }
                    else { hint = ChatRemasteredConfig.tr("chat-remastered.click_to_open"); colorPrefix = ""; }
                    List<ClientTooltipComponent> lines = new java.util.ArrayList<>();
                    lines.add(ClientTooltipComponent.create(Component.literal(colorPrefix + hint).getVisualOrderText()));
                    if (!isDeleted && !isError) {
                        dev.errnicraft.chatremastered.client.ChatComponentTooltipHelper.appendMetadataLines(lines, otherId);
                    }
                    graphics.tooltip(mc.font, lines, mouseX, mouseY,
                            (sw, sh, x, y, w, h) -> new org.joml.Vector2i(x, y - h - 4), null);
                    if (!menuOpen) {
                        hoveredMsgLine = findLineIndexForImageId(mc, msg.getImageId());
                    }
                    if (!isDeleted && !isError)
                        GLFW.glfwSetCursor(mc.getWindow().handle(), GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR));
                    else
                        GLFW.glfwSetCursor(mc.getWindow().handle(), 0L);
                    return hoveredMsgLine;
                }
            }
        }

        List<ChatRemasteredStore.ImageMessage> imgs = ChatRemasteredStore.getMessageList();
        for (ChatRemasteredStore.ImageMessage msg : imgs) {
            if (msg.getDismissed() || !msg.hasScreenBounds()) continue;
            if (mouseX >= msg.getBoundsX0() && mouseX < msg.getBoundsX1()
                    && mouseY >= msg.getBoundsY0() && mouseY < msg.getBoundsY1()) {
                boolean isDeleted = ImageCache.isDeleted(msg.getImageId());
                boolean isError = ImageCache.isError(msg.getImageId());
                String hint;
                String colorPrefix;
                if (isDeleted) {
                    hint = ImageCache.isDeletedByAdmin(msg.getImageId())
                            ? ChatRemasteredConfig.tr("chat-remastered.hover_deleted")
                            : ChatRemasteredConfig.tr("chat-remastered.hover_deleted_by_author");
                    colorPrefix = "§c";
                }
                else if (isError) { hint = ChatRemasteredConfig.tr("chat-remastered.hover_error"); colorPrefix = "§e"; }
                else { hint = ChatRemasteredConfig.tr("chat-remastered.click_to_open"); colorPrefix = ""; }
                List<ClientTooltipComponent> lines = new java.util.ArrayList<>();
                lines.add(ClientTooltipComponent.create(Component.literal(colorPrefix + hint).getVisualOrderText()));
                if (!isDeleted && !isError) {
                    dev.errnicraft.chatremastered.client.ChatComponentTooltipHelper.appendMetadataLines(lines, msg.getImageId());
                }
                graphics.tooltip(mc.font, lines, mouseX, mouseY,
                        (sw, sh, x, y, w, h) -> new org.joml.Vector2i(x, y - h - 4), null);

                if (!menuOpen) {
                    hoveredMsgLine = findLineIndexForImageId(mc, msg.getImageId());
                }
                if (!isDeleted && !isError)
                    GLFW.glfwSetCursor(mc.getWindow().handle(), GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR));
                else
                    GLFW.glfwSetCursor(mc.getWindow().handle(), 0L);
                return hoveredMsgLine;
            }
        }

        boolean mouseOverImage = false;
        for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
            if (!imgMsg.getDismissed() && imgMsg.hasScreenBounds()
                    && mouseX >= imgMsg.getBoundsX0() && mouseX < imgMsg.getBoundsX1()
                    && mouseY >= imgMsg.getBoundsY0() && mouseY < imgMsg.getBoundsY1()) {
                mouseOverImage = true;
                if (!menuOpen) {
                    hoveredMsgLine = findLineIndexForImageId(mc, imgMsg.getImageId());
                }
                break;
            }
            if (!imgMsg.getRowCardBounds().isEmpty()) {
                for (int[] b : imgMsg.getRowCardBounds().values()) {
                    if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                        mouseOverImage = true;
                        if (!menuOpen) {
                            hoveredMsgLine = findLineIndexForImageId(mc, imgMsg.getImageId());
                        }
                        break;
                    }
                }
                if (mouseOverImage) break;
            }
        }

        if (pendingRowNonEmpty) {
            if (mouseY >= pendingRowTop - 2 && mouseY <= pendingRowBottom + 2 && mouseX >= pendingRowLeft && mouseX <= pendingRowRight) {
                mouseOverImage = true;
            }
        }
        if (!menuOpen && !mouseOverImage) {
            hoveredMsgLine = getLineIndexAt(mc, mouseX, mouseY);
        } else if (!menuOpen && !mouseOverImage) {
            hoveredMsgLine = -1;
        }
        GLFW.glfwSetCursor(mc.getWindow().handle(), 0L);
        return hoveredMsgLine;
    }

    public static String getMessageTextAt(Minecraft mc, int mouseX, int mouseY, GuiMessage[] resolvedOut) {
        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        List<GuiMessage> all = acc.getAllMessages();
        int scrollPos = acc.getChatScrollbarPos();

        float scale = (float) mc.options.chatScale().get().doubleValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        int linesPerPage = chat.getLinesPerPage();
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);

        float localX = (mouseX / scale) - 4f;
        float localY = mouseY / scale;

        for (int i = 0; i < Math.min(trimmed.size() - scrollPos, linesPerPage); i++) {
            int lineIdx = i + scrollPos;
            if (lineIdx >= trimmed.size()) break;
            GuiMessage.Line line = trimmed.get(lineIdx);
            int entryBottom = chatBottom - i * entryHeight;
            int entryTop = entryBottom - entryHeight;
            if (localY >= entryTop && localY < entryBottom && localX >= 0 && localX < chatWidthPx) {
                String lineStripped = stripHeadPlaceholders(formattedCharSequenceToString(line.content()));
                GuiMessage fallback = null;
                String fallbackText = null;
                for (GuiMessage msg : all) {
                    if (msg.addedTime() != line.addedTime()) continue;
                    String rawText = msg.content().getString();
                    String candidateNoPrefix = rawText.startsWith("\n") ? rawText.substring(1) : rawText;
                    String candidateStripped = stripHeadPlaceholders(candidateNoPrefix);
                    if (candidateStripped.isBlank()) continue;
                    if (fallback == null) { fallback = msg; fallbackText = candidateStripped; }
                    if (lineStripped.isBlank() || candidateStripped.contains(lineStripped)
                            || lineStripped.contains(candidateStripped)) {
                        if (resolvedOut != null) resolvedOut[0] = msg;
                        return candidateStripped;
                    }
                }
                if (fallback != null) {
                    if (resolvedOut != null) resolvedOut[0] = fallback;
                    return fallbackText;
                }
            }
        }
        if (resolvedOut != null) resolvedOut[0] = null;
        return null;
    }
}
