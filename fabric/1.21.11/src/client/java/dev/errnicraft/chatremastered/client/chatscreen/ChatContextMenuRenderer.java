package dev.errnicraft.chatremastered.client.chatscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class ChatContextMenuRenderer {

    public static final int MENU_ITEM_H = 14;
    public static final int MENU_PAD = 4;
    public static final int MENU_ANIM_MS = 100;

    private ChatContextMenuRenderer() {
    }

    public static void render(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY,
                               int menuX, int menuY, int menuComputedW, long menuOpenTime,
                               String[] menuLabels, String[] menuIcons, int[] menuColors,
                               float[] menuItemAnim) {
        int n = menuLabels.length;
        int ITEM_H = MENU_ITEM_H;
        int PAD = MENU_PAD;
        int menuH = PAD * 2 + n * ITEM_H + (n - 1) * 2;

        float elapsed = System.currentTimeMillis() - menuOpenTime;
        float t = Math.min(1f, elapsed / MENU_ANIM_MS);

        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        int visibleH = Math.round(menuH * ease);
        float alpha = Math.min(1f, t * 2f);

        int mx0 = menuX, my0 = menuY;
        int mx1 = mx0 + menuComputedW, my1 = my0 + menuH;

        int clipBot = my0 + visibleH;

        int shadowAlpha = Math.round(0x55 * alpha);
        for (int s = 4; s >= 1; s--) {
            int sa = shadowAlpha / s;
            fillClipped(graphics, mx0 + s, my0 + s, mx1 + s, Math.min(clipBot + s, my1 + s),
                    (sa << 24), my0, clipBot + s);
        }

        int bgAlpha = Math.round(0xEC * alpha);
        fillClipped(graphics, mx0, my0, mx1, clipBot, (bgAlpha << 24) | 0x1E1E1E, my0, clipBot);

        int borderAlpha = Math.round(0xFF * alpha);
        int borderColor = (borderAlpha << 24) | 0x3A3A3A;

        fillClipped(graphics, mx0, my0, mx1, my0 + 1, borderColor, my0, clipBot);

        fillClipped(graphics, mx0, my1 - 1, mx1, my1, borderColor, my0, clipBot);

        fillClipped(graphics, mx0, my0, mx0 + 1, my1, borderColor, my0, clipBot);
        fillClipped(graphics, mx1 - 1, my0, mx1, my1, borderColor, my0, clipBot);

        int bg = (bgAlpha << 24) | 0x1E1E1E;

        graphics.fill(mx0, my0, mx0 + 3, my0 + 1, 0); graphics.fill(mx0, my0, mx0 + 1, my0 + 3, 0);

        graphics.fill(mx1 - 3, my0, mx1, my0 + 1, 0); graphics.fill(mx1 - 1, my0, mx1, my0 + 3, 0);
        if (clipBot >= my1) {
            graphics.fill(mx0, my1 - 1, mx0 + 3, my1, 0); graphics.fill(mx0, my1 - 3, mx0 + 1, my1, 0);

            graphics.fill(mx1 - 3, my1 - 1, mx1, my1, 0); graphics.fill(mx1 - 1, my1 - 3, mx1, my1, 0);
        }

        for (int i = 0; i < n; i++) {
            int iy = my0 + PAD + i * (ITEM_H + 2);
            int iy2 = iy + ITEM_H;
            if (iy >= clipBot) break;

            boolean hovered = mouseX >= mx0 + 2 && mouseX < mx1 - 2 && mouseY >= iy && mouseY < iy2;

            float target = hovered ? 1f : 0f;
            menuItemAnim[i] += (target - menuItemAnim[i]) * 0.3f;
            float a = menuItemAnim[i];
            if (a > 0.01f) {
                int hAlpha = Math.round(0xFF * a * alpha);
                boolean isRedItem = menuColors != null && i < menuColors.length && menuColors[i] == 0xFF4444;
                int hR, hG, hB;
                if (isRedItem) {
                    hR = (int)(0x2B + (0xC0 - 0x2B) * a);
                    hG = (int)(0x2B + (0x39 - 0x2B) * a);
                    hB = (int)(0x2B + (0x2B - 0x2B) * a);
                } else {
                    hR = (int)(0x2B + (0x00 - 0x2B) * a);
                    hG = (int)(0x2B + (0x78 - 0x2B) * a);
                    hB = (int)(0x2B + (0xD4 - 0x2B) * a);
                }
                int hoverColor = (hAlpha << 24) | (hR << 16) | (hG << 8) | hB;

                int hx0 = mx0 + 3, hx1 = mx1 - 3;
                int clipIy2 = Math.min(iy2, clipBot);
                fillClipped(graphics, hx0, iy, hx1, clipIy2, hoverColor, my0, clipBot);
                fillClipped(graphics, hx0 + 1, iy - 1, hx1 - 1, iy, hoverColor, my0, clipBot);
                fillClipped(graphics, hx0 + 1, iy2, hx1 - 1, iy2 + 1, hoverColor, my0, clipBot);
                fillClipped(graphics, hx0 - 1, iy + 1, hx0, iy2 - 1, hoverColor, my0, clipBot);
                fillClipped(graphics, hx1, iy + 1, hx1 + 1, iy2 - 1, hoverColor, my0, clipBot);
            }

            if (iy2 > clipBot) continue;

            int textAlpha = Math.round(0xFF * alpha);

            int baseColor = (menuColors != null && i < menuColors.length && menuColors[i] != 0)
                    ? menuColors[i] : 0xE0E0E0;
            int textColor = hovered
                    ? (textAlpha << 24 | 0xFFFFFF)
                    : (textAlpha << 24 | baseColor);
            int textY = iy + (ITEM_H - mc.font.lineHeight) / 2 + 1;
            String icon = menuIcons[i];
            if (!icon.isEmpty()) {
                graphics.drawString(mc.font, icon, mx0 + PAD + 2, textY, textColor, false);
                graphics.drawString(mc.font, menuLabels[i], mx0 + PAD + 11, textY, textColor, false);
            } else {
                graphics.drawString(mc.font, menuLabels[i], mx0 + PAD + 2, textY, textColor, false);
            }
        }
    }

    public static void fillClipped(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int clipTop, int clipBot) {
        y0 = Math.max(y0, clipTop);
        y1 = Math.min(y1, clipBot);
        if (y0 >= y1 || x0 >= x1) return;
        g.fill(x0, y0, x1, y1, color);
    }
}
