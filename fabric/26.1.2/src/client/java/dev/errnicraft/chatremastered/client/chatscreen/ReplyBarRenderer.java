package dev.errnicraft.chatremastered.client.chatscreen;

import dev.errnicraft.chatremastered.ImageCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class ReplyBarRenderer {

    private ReplyBarRenderer() {
    }

    public static int replyBarHeight(int replyAddedTime) {
        return replyAddedTime >= 0 ? 14 : 0;
    }

    public static int getPendingPreviewAreaHeight(float replyBarAnim) {
        if (replyBarAnim < 0.01f) return 0;
        int barH = 13;
        float ease = 1f - (1f - replyBarAnim) * (1f - replyBarAnim);
        int animOffsetY = Math.round((barH + 4) * (1f - ease));
        int visibleH = (barH + 2) - animOffsetY;
        return Math.max(0, visibleH);
    }

    public static int[] getReplyBarBounds(Minecraft mc, ChatScreen self, float replyBarAnim) {
        float scale = (float) mc.options.chatScale().get().doubleValue();
        if (scale < 0.01f) scale = 1f;
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
        int barH = 13;

        int barLeft = 2;

        int barRight = Math.round((4 + chatWidthPx) * scale);
        int fullW    = barRight - barLeft;

        int baseBarY = self.height - 12 - barH - 2;

        float ease = 1f - (1f - replyBarAnim) * (1f - replyBarAnim);
        int animOffsetY = Math.round((barH + 4) * (1f - ease));
        int barY = baseBarY + animOffsetY;
        int barX = barLeft;
        int adjustedW = fullW;
        if (adjustedW < 20) return null;
        return new int[]{barX, barY, adjustedW, barH};
    }

    public static void renderReplyOverMessage(GuiGraphicsExtractor graphics, Minecraft mc, ChatScreen self, int mouseX, int mouseY,
                                               float replyBarAnim, String replyImageId, String replyText, String replySenderName) {
        if (replyBarAnim < 0.01f) return;
        int[] bounds = getReplyBarBounds(mc, self, replyBarAnim);
        if (bounds == null) return;
        int barX = bounds[0], barY = bounds[1], barW = bounds[2], barH = bounds[3];

        float ease = 1f - (1f - replyBarAnim) * (1f - replyBarAnim);
        int alpha = Math.round(0xCC * ease);
        int bgColor     = (alpha << 24) | 0x1A1A1A;
        int accentColor = (Math.round(0xFF * ease) << 24) | 0x3366CC;
        int textAlpha   = Math.round(0xFF * ease);

        graphics.fill(barX, barY, barX + barW, barY + barH, bgColor);
        graphics.fill(barX, barY, barX + 2, barY + barH, accentColor);

        int textY = barY + (barH - mc.font.lineHeight) / 2 + 1;

        int closeX = barX + barW - 12;
        boolean hoverClose = mouseX >= closeX - 2 && mouseX < closeX + 10
                && mouseY >= barY && mouseY < barY + barH;
        int closeColor = (textAlpha << 24) | (hoverClose ? 0xFF4444 : 0x888888);
        graphics.text(mc.font, "✕", closeX, textY, closeColor, false);

        int contentX = barX + 5;
        int maxW = closeX - contentX - 4;

        String full;
        if (replyImageId != null) {
            Identifier tex = ImageCache.getTexture(replyImageId);
            if (tex != null) {
                String arrowStr = "↩ ";
                graphics.text(mc.font, arrowStr, contentX, textY, (textAlpha << 24) | 0xBBBBBB, false);
                contentX += mc.font.width(arrowStr);

                int photoW = 10, photoH = 10;
                dev.errnicraft.chatremastered.IntPair ts = ImageCache.getTexSize(replyImageId);
                int srcW = ts != null && ts.getFirst() > 0  ? ts.getFirst()  : photoW;
                int srcH = ts != null && ts.getSecond() > 0 ? ts.getSecond() : photoH;

                float scale = Math.min((float) photoW / srcW, (float) photoH / srcH);
                int drawW = Math.round(srcW * scale);
                int drawH = Math.round(srcH * scale);
                int photoY = barY + (barH - drawH) / 2;
                graphics.pose().pushMatrix();
                graphics.pose().translate(contentX, photoY);
                graphics.pose().scale(scale, scale);
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        tex,
                        0, 0,
                        0f, 0f,
                        srcW, srcH,
                        srcW, srcH,
                        -1
                );
                graphics.pose().popMatrix();
                contentX += drawW + 3;
                maxW = closeX - contentX - 4;
            }

            String suffix = replyText != null ? replyText : "";
            if (replySenderName != null) {
                MutableComponent label = buildReplyInputLabel(replySenderName, suffix, maxW, mc);
                graphics.text(mc.font, label, contentX, textY, (textAlpha << 24) | 0xAAAAAA, false);
                return;
            } else {
                full = "§7" + suffix;
            }
        } else {
            String suffix = replyText != null ? replyText : "";
            if (replySenderName != null) {
                MutableComponent label2 = Component.empty();
                label2.append(Component.literal("↩ ")
                        .setStyle(Style.EMPTY.withColor(0xBBBBBB)));
                label2.append(Component.literal(replySenderName)
                        .setStyle(Style.EMPTY.withColor(0x3399EE)));
                if (!suffix.isEmpty()) label2.append(Component.literal(": " + suffix)
                        .setStyle(Style.EMPTY.withColor(0xAAAAAA)));
                label2 = truncateInputLabel(label2, maxW, mc);
                graphics.text(mc.font, label2, contentX, textY, (textAlpha << 24) | 0xAAAAAA, false);
                return;
            } else {
                full = "↩ §7" + suffix;
            }
        }
        graphics.text(mc.font, truncateFormatted(mc, full, maxW),
                contentX, textY, (textAlpha << 24) | 0xBBBBBB, false);
    }

    public static String truncateFormatted(Minecraft mc, String text, int maxWidth) {
        Component comp = Component.literal(text);
        if (mc.font.width(comp) <= maxWidth) return text;

        String plain = comp.getString();
        String ellipsis = "…";
        int ellW = mc.font.width(ellipsis);

        while (plain.length() > 0 && mc.font.width(plain) + ellW > maxWidth)
            plain = plain.substring(0, plain.length() - 1);
        return plain + ellipsis;
    }

    public static String truncate(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellW = mc.font.width(ellipsis);
        while (!text.isEmpty() && mc.font.width(text) + ellW > maxWidth)
            text = text.substring(0, text.length() - 1);
        return text + ellipsis;
    }

    public static MutableComponent buildReplyInputLabel(String sender, String suffix, int maxW, Minecraft mc) {
        MutableComponent label = Component.empty();
        label.append(Component.literal(sender)
                .setStyle(Style.EMPTY.withColor(0x3399EE)));
        if (!suffix.isEmpty()) label.append(Component.literal(": " + suffix)
                .setStyle(Style.EMPTY.withColor(0xAAAAAA)));
        return truncateInputLabel(label, maxW, mc);
    }

    public static MutableComponent truncateInputLabel(MutableComponent comp, int maxW, Minecraft mc) {
        if (mc.font.width(comp) <= maxW) return comp;
        String plain = comp.getString();
        String ellipsis = "…";
        int ellW = mc.font.width(ellipsis);
        while (!plain.isEmpty() && mc.font.width(plain) + ellW > maxW)
            plain = plain.substring(0, plain.length() - 1);
        MutableComponent result = Component.empty();
        int remaining = plain.length();
        for (Component sib : comp.getSiblings()) {
            if (remaining <= 0) break;
            String sibText = sib.getString();
            int take = Math.min(sibText.length(), remaining);
            result.append(Component.literal(sibText.substring(0, take)).setStyle(sib.getStyle()));
            remaining -= take;
        }
        result.append(Component.literal(ellipsis)
                .setStyle(Style.EMPTY.withColor(0xAAAAAA)));
        return result;
    }
}
