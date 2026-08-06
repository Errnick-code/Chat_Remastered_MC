package dev.errnicraft.chatremastered;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ChatRemasteredContextMenu extends Screen {

    public static final class MenuItem {
        private final String label;
        private final String icon;
        private final Runnable action;

        public MenuItem(String label, String icon, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.action = action;
        }

        public MenuItem(String label, Runnable action) {
            this(label, "", action);
        }

        public String getLabel() {
            return label;
        }

        public String getIcon() {
            return icon;
        }

        public Runnable getAction() {
            return action;
        }
    }

    private final List<MenuItem> items;
    private final int anchorX;
    private final int anchorY;
    private final Screen parent;

    public ChatRemasteredContextMenu(List<MenuItem> items, int anchorX, int anchorY, Screen parent) {
        super(Component.empty());
        this.items = items;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.parent = parent;
        this.itemColors = new int[items.size()];
    }

    public static ChatRemasteredContextMenu forImage(String imageId, int ax, int ay, Screen parent) {
        return new ChatRemasteredContextMenu(
                List.of(
                        new MenuItem(ChatRemasteredConfig.tr("chat-remastered.ctx_save_as"), "💾", () ->
                                ChatRemasteredClient.saveImageAs(imageId)),
                        new MenuItem(ChatRemasteredConfig.tr("chat-remastered.ctx_copy_id"), "📋", () -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.keyboardHandler.setClipboard(imageId);
                            mc.gui.getChat().addMessage(
                                    Component.literal("§8[Chat Remastered] §7" + ChatRemasteredConfig.tr("chat-remastered.id_copied", imageId, imageId))
                            );
                        })
                ), ax, ay, parent
        );
    }

    public static ChatRemasteredContextMenu forMessage(String text, int ax, int ay, Screen parent) {
        return new ChatRemasteredContextMenu(
                List.of(
                        new MenuItem(ChatRemasteredConfig.tr("chat-remastered.ctx_copy_message"), "📋", () ->
                                Minecraft.getInstance().keyboardHandler.setClipboard(text))
                ), ax, ay, parent
        );
    }

    private final int ITEM_W = 180;
    private final int ITEM_H = 20;
    private final int PAD = 6;
    private final float RADIUS = 6f;
    private final long ANIM_MS = 120L;

    private int menuX = 0;
    private int menuY = 0;
    private long openTime = 0L;
    private int hoveredIndex = -1;

    private final int[] itemColors;

    @Override
    protected void init() {
        openTime = System.currentTimeMillis();
        int menuH = PAD * 2 + items.size() * ITEM_H + (items.size() - 1) * 2;
        menuX = Math.min(anchorX, width - ITEM_W - PAD * 2);
        menuY = Math.min(anchorY, height - menuH - PAD * 2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        int menuH = PAD * 2 + items.size() * ITEM_H + (items.size() - 1) * 2;

        float elapsed = (System.currentTimeMillis() - openTime);
        float t = Math.min(elapsed / ANIM_MS, 1f);
        float ease = 1f - (1f - t) * (1f - t);

        graphics.pose().pushMatrix();
        float cx = menuX + ITEM_W / 2f;
        float cy = menuY;
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(ease, ease);
        graphics.pose().translate(-cx, -cy);

        graphics.fill(menuX + 3, menuY + 3, menuX + ITEM_W + 3, menuY + menuH + 3, 0x44000000);
        graphics.fill(menuX + 2, menuY + 2, menuX + ITEM_W + 2, menuY + menuH + 2, 0x22000000);

        graphics.fill(menuX, menuY, menuX + ITEM_W, menuY + menuH, (int) 0xF52C2C2C);
        graphics.fill(menuX, menuY, menuX + ITEM_W, menuY + 1, (int) 0xFF555555);
        graphics.fill(menuX, menuY + menuH - 1, menuX + ITEM_W, menuY + menuH, (int) 0xFF555555);
        graphics.fill(menuX, menuY, menuX + 1, menuY + menuH, (int) 0xFF555555);
        graphics.fill(menuX + ITEM_W - 1, menuY, menuX + ITEM_W, menuY + menuH, (int) 0xFF555555);

        hoveredIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            int iy = menuY + PAD + i * (ITEM_H + 2);
            boolean hovered = mouseX >= menuX + 1 && mouseX < menuX + ITEM_W - 1 && mouseY >= iy && mouseY < iy + ITEM_H;

            if (hovered) {
                hoveredIndex = i;
            }

            int targetBg = hovered ? (int) 0xFF3D6099 : 0x002C2C2C;
            itemColors[i] = lerpColor(itemColors[i], targetBg, 0.25f);
            if (hovered || (itemColors[i] >>> 24) > 5) {
                graphics.fill(menuX + 2, iy, menuX + ITEM_W - 2, iy + ITEM_H, itemColors[i]);
            }

            int textColor = hovered ? (int) 0xFFFFFFFF : (int) 0xFFDDDDDD;
            int textX = menuX + PAD + (!item.getIcon().isEmpty() ? 14 : 0);
            int textY = iy + (ITEM_H - mc.font.lineHeight) / 2;
            if (!item.getIcon().isEmpty()) {
                graphics.drawString(mc.font, item.getIcon(), menuX + PAD - 1, textY, textColor, false);
            }
            graphics.drawString(mc.font, item.getLabel(), textX, textY, textColor, false);
        }

        graphics.pose().popMatrix();
    }

    private int lerpColor(int from, int to, float t) {
        int fa = (from >>> 24) & 0xFF;
        int fr = (from >>> 16) & 0xFF;
        int fg = (from >>> 8) & 0xFF;
        int fb = from & 0xFF;
        int ta = (to >>> 24) & 0xFF;
        int tr = (to >>> 16) & 0xFF;
        int tg = (to >>> 8) & 0xFF;
        int tb = to & 0xFF;
        int a = clamp((int) (fa + (ta - fa) * t), 0, 255);
        int r = clamp((int) (fr + (tr - fr) * t), 0, 255);
        int g = clamp((int) (fg + (tg - fg) * t), 0, 255);
        int b = clamp((int) (fb + (tb - fb) * t), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public boolean mouseClicked(double mouseXD, double mouseYD, int button) {
        int mouseX = (int) mouseXD;
        int mouseY = (int) mouseYD;
        int menuH = PAD * 2 + items.size() * ITEM_H + (items.size() - 1) * 2;
        boolean inside = mouseX >= menuX && mouseX <= menuX + ITEM_W && mouseY >= menuY && mouseY <= menuY + menuH;
        if (!inside) {
            minecraft.setScreen(parent);
            return true;
        }
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            int iy = menuY + PAD + i * (ITEM_H + 2);
            if (mouseX >= menuX + 1 && mouseX < menuX + ITEM_W - 1 && mouseY >= iy && mouseY < iy + ITEM_H) {
                item.getAction().run();
                minecraft.setScreen(parent);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}