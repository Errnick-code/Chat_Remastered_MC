package dev.errnick.chatremastered.client.chatscreen;

import dev.errnick.chatremastered.ChatRemasteredConfig;
import dev.errnick.chatremastered.mixin.ChatComponentAccessor;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ChatSelection {

    private static final int SELECTION_COLOR = 0x800000FF;
    private static final int SELECTED_TEXT_COLOR = 0xFFFFFF;

    private static int anchorLine = -1;
    private static int anchorCol = 0;
    private static int focusLine = -1;
    private static int focusCol = 0;
    private static int pressLine = -1;
    private static int pressCol = 0;
    private static double pressX;
    private static double pressY;
    private static boolean dragging;

    private ChatSelection() {
    }

    public static boolean isEnabled() {
        return ChatRemasteredConfig.getChatSelectionEnabled();
    }

    public static boolean hasSelection() {
        if (anchorLine < 0 || focusLine < 0) return false;
        return anchorLine != focusLine || anchorCol != focusCol;
    }

    public static boolean handlePress(double mouseX, double mouseY) {
        if (!isEnabled()) return false;
        if (hasSelection()) {
            clear();
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        int line = ChatLineLocator.getLineIndexAt(mc, (int) mouseX, (int) mouseY);
        if (line < 0) return false;
        if (ChatLineLocator.isClickableAt(mc, (int) mouseX, (int) mouseY)) return false;
        if (ChatLineLocator.getImageIdForMessageAt(mc, (int) mouseX, (int) mouseY) != null) return false;
        pressLine = line;
        pressCol = colAt(mc, line, localX(mc, mouseX));
        pressX = mouseX;
        pressY = mouseY;
        dragging = false;
        return false;
    }

    public static void tick(Minecraft mc, double mouseX, double mouseY) {
        if (!isEnabled()) {
            clear();
            return;
        }
        if (pressLine < 0) return;
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!down) {
            pressLine = -1;
            dragging = false;
            return;
        }
        if (!dragging && (Math.abs(mouseX - pressX) > 2.0 || Math.abs(mouseY - pressY) > 2.0)) {
            dragging = true;
            anchorLine = pressLine;
            anchorCol = pressCol;
            focusLine = pressLine;
            focusCol = pressCol;
        }
        if (!dragging) return;

        int line = ChatLineLocator.getLineIndexAt(mc, (int) mouseX, (int) mouseY);
        if (line < 0) {
            line = clampLine(mc, mouseY);
            if (line < 0) return;
            focusLine = line;
            focusCol = mouseY < chatTopGui(mc) ? 0 : Math.max(0, colStarts(mc, line).length - 1);
            return;
        }
        focusLine = line;
        focusCol = colAt(mc, line, localX(mc, mouseX));
    }

    private static int clampLine(Minecraft mc, double mouseY) {
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        int total = acc.getTrimmedMessages().size();
        if (total == 0) return -1;
        int scrollPos = acc.getChatScrollbarPos();
        int linesPerPage = mc.gui.getChat().getLinesPerPage();
        int lastVisible = Math.min(total - 1, scrollPos + linesPerPage - 1);
        if (mouseY < chatTopGui(mc)) return Math.max(0, lastVisible);
        return Mth.clamp(scrollPos, 0, total - 1);
    }

    public static boolean copy(Minecraft mc) {
        if (!hasSelection()) return false;
        List<GuiMessage.Line> trimmed = ((ChatComponentAccessor) mc.gui.getChat()).getTrimmedMessages();
        if (trimmed.isEmpty()) return false;

        int fromLine = Mth.clamp(firstLine(), 0, trimmed.size() - 1);
        int toLine = Mth.clamp(lastLine(), 0, trimmed.size() - 1);
        int fromCol = firstCol();
        int toCol = lastCol();

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        boolean prevWrapped = false;
        for (int i = fromLine; i >= toLine; i--) {
            String text = lineText(mc, i);
            int len = codePointCount(text);
            int start = (i == fromLine) ? Math.min(fromCol, len) : 0;
            int end = (i == toLine) ? Math.min(toCol, len) : len;
            if (end < start) {
                int swap = start;
                start = end;
                end = swap;
            }
            String segment = subByCols(text, start, end);
            if (start == 0) segment = ChatLineLocator.stripHeadPlaceholders(segment);
            if (segment == null) segment = "";
            while (segment.startsWith("\n")) segment = segment.substring(1);
            if (segment.isEmpty()) continue;
            if (!first && !prevWrapped) sb.append('\n');
            sb.append(segment);
            first = false;
            prevWrapped = !trimmed.get(i).endOfEntry();
        }

        String result = sb.toString().stripTrailing();
        if (result.isEmpty()) return false;
        mc.keyboardHandler.setClipboard(result);
        return true;
    }

    public static void clear() {
        anchorLine = -1;
        anchorCol = 0;
        focusLine = -1;
        focusCol = 0;
        pressLine = -1;
        pressCol = 0;
        dragging = false;
    }

    public static void renderOverlay(GuiGraphics graphics, Minecraft mc) {
        if (!isEnabled() || !hasSelection()) return;
        if (!(mc.screen instanceof ChatScreen)) return;
        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        if (trimmed.isEmpty()) return;

        int scrollPos = acc.getChatScrollbarPos();
        int linesPerPage = chat.getLinesPerPage();
        int fromLine = firstLine();
        int fromCol = firstCol();
        int toLine = lastLine();
        int toCol = lastCol();
        int entryHeight = entryHeight(mc);
        int chatBottom = chatBottomLocal(mc);
        int chatBottomGui = mc.getWindow().getGuiScaledHeight() - 40;
        int textOffset = entryBottomToMessageY(mc);
        float scale = guiScale(mc);

        int visible = Math.min(trimmed.size() - scrollPos, linesPerPage);

        for (int i = 0; i < visible; i++) {
            int idx = i + scrollPos;
            if (idx > fromLine || idx < toLine) continue;
            int[] xs = colStarts(mc, idx);
            int len = xs.length - 1;
            int start = (idx == fromLine) ? Math.min(fromCol, len) : 0;
            int end = (idx == toLine) ? Math.min(toCol, len) : len;
            if (end <= start) continue;
            int x0 = xs[start];
            int x1 = xs[end];
            if (x1 <= x0) continue;
            int entryBottom = chatBottom - i * entryHeight;
            int entryTop = entryBottom - entryHeight;
            int guiTop = chatBottomGui - Math.round((chatBottom - entryBottom) * scale);
            int guiBottom = chatBottomGui - Math.round((chatBottom - entryTop) * scale);
            graphics.fill(Math.round((x0 + 4) * scale), guiTop, Math.round((x1 + 4) * scale), guiBottom,
                    SELECTION_COLOR);
        }

        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(4f, 0f);

        for (int i = 0; i < visible; i++) {
            int idx = i + scrollPos;
            if (idx > fromLine || idx < toLine) continue;
            int[] xs = colStarts(mc, idx);
            int len = xs.length - 1;
            int start = (idx == fromLine) ? Math.min(fromCol, len) : 0;
            int end = (idx == toLine) ? Math.min(toCol, len) : len;
            if (end <= start) continue;
            int textTop = chatBottom - i * entryHeight - textOffset;
            drawSelectedRun(graphics, mc, trimmed.get(idx).content(), lineText(mc, idx), xs, start, end, textTop);
        }

        graphics.pose().popMatrix();
    }

    private static void drawSelectedRun(GuiGraphics graphics, Minecraft mc, FormattedCharSequence seq,
                                        String text, int[] xs, int start, int end, int y) {
        List<Style> styles = new ArrayList<>();
        seq.accept((index, style, codePoint) -> {
            styles.add(style);
            return true;
        });
        int limit = Math.min(end, styles.size());
        int i = Math.max(0, start);
        while (i < limit) {
            Style style = styles.get(i);
            int j = i + 1;
            while (j < limit && styles.get(j).equals(style)) {
                j++;
            }
            String run = subByCols(text, i, j);
            graphics.drawString(mc.font, FormattedCharSequence.forward(run, style.withColor(SELECTED_TEXT_COLOR)),
                    xs[i], y, 0xFFFFFFFF, false);
            i = j;
        }
    }

    private static int[] colStarts(Minecraft mc, int lineIdx) {
        List<GuiMessage.Line> trimmed = ((ChatComponentAccessor) mc.gui.getChat()).getTrimmedMessages();
        if (lineIdx < 0 || lineIdx >= trimmed.size()) return new int[]{0};
        FormattedCharSequence seq = trimmed.get(lineIdx).content();
        int[] count = {0};
        seq.accept((index, style, codePoint) -> {
            count[0]++;
            return true;
        });
        int[] xs = new int[count[0] + 1];
        int[] pos = {0};
        seq.accept((index, style, codePoint) -> {
            int width = mc.font.width(new String(Character.toChars(codePoint)));
            if (style.isBold()) width++;
            xs[pos[0] + 1] = xs[pos[0]] + width;
            pos[0]++;
            return true;
        });
        return xs;
    }

    private static int colAt(Minecraft mc, int lineIdx, float targetX) {
        if (targetX <= 0f) return 0;
        int[] xs = colStarts(mc, lineIdx);
        int len = xs.length - 1;
        for (int c = 0; c < len; c++) {
            int x0 = xs[c];
            int x1 = xs[c + 1];
            if (targetX < x1) return targetX < (x0 + x1) / 2f ? c : c + 1;
        }
        return len;
    }

    private static int firstLine() {
        return isForward() ? anchorLine : focusLine;
    }

    private static int firstCol() {
        return isForward() ? anchorCol : focusCol;
    }

    private static int lastLine() {
        return isForward() ? focusLine : anchorLine;
    }

    private static int lastCol() {
        return isForward() ? focusCol : anchorCol;
    }

    private static boolean isForward() {
        return anchorLine > focusLine || (anchorLine == focusLine && anchorCol <= focusCol);
    }

    private static String lineText(Minecraft mc, int lineIdx) {
        List<GuiMessage.Line> trimmed = ((ChatComponentAccessor) mc.gui.getChat()).getTrimmedMessages();
        if (lineIdx < 0 || lineIdx >= trimmed.size()) return "";
        String text = ChatLineLocator.formattedCharSequenceToString(trimmed.get(lineIdx).content());
        return text == null ? "" : text;
    }

    private static float localX(Minecraft mc, double mouseX) {
        return (float) (mouseX / guiScale(mc)) - 4f;
    }

    private static int codePointCount(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String subByCols(String text, int startCol, int endCol) {
        int len = codePointCount(text);
        int s = Mth.clamp(startCol, 0, len);
        int e = Mth.clamp(endCol, s, len);
        int startIdx = text.offsetByCodePoints(0, s);
        int endIdx = text.offsetByCodePoints(startIdx, e - s);
        return text.substring(startIdx, endIdx);
    }

    private static float guiScale(Minecraft mc) {
        float scale = (float) mc.options.chatScale().get().doubleValue();
        return scale < 0.01f ? 1f : scale;
    }

    private static int entryHeight(Minecraft mc) {
        double spacing = mc.options.chatLineSpacing().get();
        return (int) (9.0 * (spacing + 1.0));
    }

    private static int entryBottomToMessageY(Minecraft mc) {
        double spacing = mc.options.chatLineSpacing().get();
        return (int) Math.round(8.0 * (spacing + 1.0) - 4.0 * spacing);
    }

    private static int chatBottomLocal(Minecraft mc) {
        return Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / guiScale(mc));
    }

    private static int chatTopGui(Minecraft mc) {
        int linesPerPage = mc.gui.getChat().getLinesPerPage();
        return mc.getWindow().getGuiScaledHeight() - 40 - Math.round(linesPerPage * entryHeight(mc) * guiScale(mc));
    }
}
