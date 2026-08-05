package dev.errnicraft.chatremastered.mixin;

import dev.errnicraft.chatremastered.ChatRemasteredClient;
import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ChatRemasteredConfigScreen;
import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.ImageCache;
import dev.errnicraft.chatremastered.ImageViewerScreen;
import dev.errnicraft.chatremastered.PendingCardAnimator;
import dev.errnicraft.chatremastered.PendingImageState;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    private static final int CAM_BTN_W = 20;
    private static final int CAM_BTN_H = 20;
    private static final int CFG_BTN_W = 20;
    private static final int CFG_BTN_H = 20;
    private static final int SCR_BTN_W = 20;
    private static final int SCR_BTN_H = 20;

    private static final int PENDING_STRIP_THUMB_H = 28;
    private static final int PENDING_STRIP_GAP = 4;

    private int cr$pendingStripScrollX = 0;

    private final PendingCardAnimator cr$pendingAnim = new PendingCardAnimator();

    private static final Identifier PLACEHOLDER_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/placeholder.png");

    private static final Identifier FOLDER_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/folder.png");
    private static final Identifier FOLDER_ACTIVE_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/folder_active.png");
    private static final Identifier FOLDER_BLOCK_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/folder_block.png");
    private static final Identifier SETTINGS_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/settings.png");
    private static final Identifier SETTINGS_ACTIVE_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/settings_active.png");
    private static final Identifier SCREENSHOTS_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/screenshots.png");
    private static final Identifier SCREENSHOTS_ACTIVE_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/gui/screenshots_active.png");

    private boolean cr$menuOpen = false;
    private int cr$menuX, cr$menuY;
    private int cr$menuComputedW = 160;
    private long cr$menuOpenTime;
    private String[] cr$menuLabels;
    private String[] cr$menuIcons;
    private Runnable[] cr$menuActions;
    private int[] cr$menuColors;
    private int cr$menuHoveredLast = -1;
    private float[] cr$menuItemAnim;

    private int cr$hoveredMsgLine = -1;

    private int cr$replyAddedTime = -1;
    private String cr$replySenderName = null;
    private net.minecraft.network.chat.Component cr$replySenderComp = null;
    private String cr$replyText = null;
    private String cr$replyImageId = null;

    private float cr$replyBarAnim = 0f;
    private float cr$replyBarXAnim = 0f;
    private float cr$photoPreviewAnim = 0f;
    private static final float ANIM_SPEED = 0.2f;

    private int cr$highlightAddedTime = -1;
    private long cr$highlightStartMs = 0L;
    private static final long HIGHLIGHT_DURATION_MS = 2000L;

    private static final int MENU_W = 160;
    private static final int MENU_ITEM_H = 14;
    private static final int MENU_ITEM_PAD = 5;
    private static final int MENU_PAD = 4;
    private static final int MENU_ICON_W = 10;
    private static final int MENU_ANIM_MS = 100;

    private net.minecraft.client.gui.components.CommandSuggestions cr$tagCommandSuggestions;
    private String cr$tagSuggestionsForText = null;

    private net.minecraft.client.gui.components.CommandSuggestions cr$getTagCommandSuggestions(ChatScreen self) {
        if (cr$tagCommandSuggestions == null && input != null) {
            cr$tagCommandSuggestions = new net.minecraft.client.gui.components.CommandSuggestions(
                    Minecraft.getInstance(), self, input, Minecraft.getInstance().font,
                    false, false, 1, 10, true, -805306368);
        }
        return cr$tagCommandSuggestions;
    }

    private void cr$resetTagSuggestions() {
        if (cr$tagCommandSuggestions != null) {
            cr$tagCommandSuggestions.hide();
        }
        cr$tagSuggestionsForText = null;
    }

    private void cr$updateTagSuggestions(ChatScreen self) {
        if (input == null) {
            return;
        }
        net.minecraft.client.gui.components.CommandSuggestions cs = cr$getTagCommandSuggestions(self);
        String value = input.getValue();
        int cursor = input.getCursorPosition();
        if (value.isEmpty() || cursor <= 0 || cursor > value.length()) {

            cr$tagSuggestionsForText = value + "\u0000" + cursor;
            cs.hide();
            return;
        }
        String key = value + "\u0000" + cursor;
        if (key.equals(cr$tagSuggestionsForText)) {
            return;
        }
        cr$tagSuggestionsForText = key;

        java.util.List<dev.errnicraft.chatremastered.client.EntityTagSuggestions.Suggestion> options =
                dev.errnicraft.chatremastered.client.EntityTagSuggestions.compute(value, cursor);
        if (options.isEmpty()) {
            cs.hide();
            return;
        }

        com.mojang.brigadier.context.StringRange range = com.mojang.brigadier.context.StringRange.between(cursor, cursor);
        java.util.List<com.mojang.brigadier.suggestion.Suggestion> brigadierSuggestions = new java.util.ArrayList<>();
        for (var opt : options) {
            if (opt.insertText().isEmpty()) {
                continue;
            }
            brigadierSuggestions.add(new com.mojang.brigadier.suggestion.Suggestion(range, opt.insertText()));
        }
        if (brigadierSuggestions.isEmpty()) {
            cs.hide();
            return;
        }

        com.mojang.brigadier.suggestion.Suggestions suggestions =
                new com.mojang.brigadier.suggestion.Suggestions(range, brigadierSuggestions);
        ((dev.errnicraft.chatremastered.mixin.CommandSuggestionsAccessor) cs)
                .setPendingSuggestions(java.util.concurrent.CompletableFuture.completedFuture(suggestions));
        cs.setAllowSuggestions(true);
        cs.showSuggestions(false);
    }

    @Shadow
    protected EditBox input;

    private final dev.errnicraft.chatremastered.ScreenshotsPanel cr$screenshotsPanel = new dev.errnicraft.chatremastered.ScreenshotsPanel();

    private boolean canSendPhoto() {
        return ChatRemasteredConfig.getServerHasModVersion() != null
                && !ChatRemasteredConfig.getUploadToken().isEmpty()
                && ChatRemasteredConfig.getServerReachable()
                && !ChatRemasteredConfig.getBanned()
                && !ChatRemasteredConfig.getMuted()
                && ChatRemasteredConfig.cooldownRemainingMs() <= 0L;
    }

    private String getButtonHint() {
        if (ChatRemasteredConfig.getServerHasModVersion() == null)
            return ChatRemasteredConfig.tr("chat-remastered.btn_no_server_mod");
        if (ChatRemasteredConfig.getBanned())
            return ChatRemasteredConfig.tr("chat-remastered.btn_banned");
        if (ChatRemasteredConfig.getMuted())
            return ChatRemasteredConfig.tr("chat-remastered.btn_muted");
        long cooldownMs = ChatRemasteredConfig.cooldownRemainingMs();
        if (cooldownMs > 0L) {
            long totalSec = (cooldownMs + 999L) / 1000L;
            if (totalSec >= 60L) {
                long m = totalSec / 60L, s = totalSec % 60L;
                return ChatRemasteredConfig.tr("chat-remastered.cooldown_minutes", m, s);
            } else {
                return ChatRemasteredConfig.tr("chat-remastered.cooldown_seconds", totalSec);
            }
        }
        return ChatRemasteredConfig.tr("chat-remastered.attach");
    }

    private void cr$openMenu(int ax, int ay, int screenW, int screenH,
                             String[] labels, String[] icons, Runnable[] actions) {
        cr$openMenu(ax, ay, screenW, screenH, labels, icons, actions, null);
    }

    private void cr$openMenu(int ax, int ay, int screenW, int screenH,
                             String[] labels, String[] icons, Runnable[] actions, int[] colors) {
        Minecraft mc = Minecraft.getInstance();
        cr$menuLabels = labels;
        cr$menuIcons = icons;
        cr$menuActions = actions;
        cr$menuColors = colors;
        cr$menuItemAnim = new float[labels.length];
        cr$menuHoveredLast = -1;
        int maxTextW = 0;
        for (int i = 0; i < labels.length; i++) {

            int iconCol = icons[i].isEmpty() ? 0 : 11;
            int w = iconCol + mc.font.width(labels[i]);
            if (w > maxTextW) maxTextW = w;
        }
        cr$menuComputedW = maxTextW + MENU_PAD * 2 + 6;
        int menuH = MENU_PAD * 2 + labels.length * MENU_ITEM_H + (labels.length - 1) * 2;
        cr$menuX = Math.min(ax, screenW - cr$menuComputedW - 4);
        cr$menuY = Math.min(ay, screenH - menuH - 4);
        cr$menuOpenTime = System.currentTimeMillis();
        cr$menuOpen = true;
    }

    private void cr$closeMenu() {
        cr$menuOpen = false;
        cr$hoveredMsgLine = -1;
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V"))
    private void chatremastered$renderHighlight(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (cr$hoveredMsgLine >= 0) {
            cr$drawLineHighlight(graphics, mc, cr$hoveredMsgLine);
        }

        cr$renderHighlight(graphics, mc);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void chatremastered$render(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatScreen self = (ChatScreen)(Object)this;
        Minecraft mc = Minecraft.getInstance();

        int inputBarTop = self.height - 12;
        int camBtnX = self.width - CAM_BTN_W - 2;
        int camBtnY = inputBarTop - CAM_BTN_H - 4;

        boolean canSend = canSendPhoto();
        boolean hoverCam = mouseX >= camBtnX && mouseX < camBtnX + CAM_BTN_W
                && mouseY >= camBtnY && mouseY < camBtnY + CAM_BTN_H;

        Identifier camTex = !canSend ? FOLDER_BLOCK_TEX : (hoverCam ? FOLDER_ACTIVE_TEX : FOLDER_TEX);
        graphics.blit(RenderPipelines.GUI_TEXTURED, camTex, camBtnX, camBtnY, 0f, 0f, CAM_BTN_W, CAM_BTN_H, CAM_BTN_W, CAM_BTN_H);

        int cfgBtnX = self.width - CFG_BTN_W - 2;
        int cfgBtnY = camBtnY - CFG_BTN_H - 2;
        boolean hoverCfg = mouseX >= cfgBtnX && mouseX < cfgBtnX + CFG_BTN_W
                && mouseY >= cfgBtnY && mouseY < cfgBtnY + CFG_BTN_H;

        Identifier cfgTex = hoverCfg ? SETTINGS_ACTIVE_TEX : SETTINGS_TEX;
        graphics.blit(RenderPipelines.GUI_TEXTURED, cfgTex, cfgBtnX, cfgBtnY, 0f, 0f, CFG_BTN_W, CFG_BTN_H, CFG_BTN_W, CFG_BTN_H);

        int scrBtnX = camBtnX - SCR_BTN_W - 2;
        int scrBtnY = camBtnY;
        boolean hoverScr = mouseX >= scrBtnX && mouseX < scrBtnX + SCR_BTN_W
                && mouseY >= scrBtnY && mouseY < scrBtnY + SCR_BTN_H;

        Identifier scrTex = hoverScr ? SCREENSHOTS_ACTIVE_TEX : SCREENSHOTS_TEX;
        graphics.blit(RenderPipelines.GUI_TEXTURED, scrTex, scrBtnX, scrBtnY, 0f, 0f, SCR_BTN_W, SCR_BTN_H, SCR_BTN_W, SCR_BTN_H);

        cr$pendingAnim.tickRemovals();
        java.util.List<PendingImageState.PendingImage> pendingAllRaw = PendingImageState.getAll();
        java.util.List<PendingImageState.PendingImage> pendingAll = new java.util.ArrayList<>();
        for (PendingImageState.PendingImage p : pendingAllRaw) {
            if (!cr$pendingAnim.isFlying(p.getUid())) {
                pendingAll.add(p);
            }
        }
        java.util.List<PendingCardAnimator.RemoveState> activeRemovals = cr$pendingAnim.getActiveRemovals(self.height);
        cr$pendingAnim.syncSpawns(pendingAll);

        if (pendingAll.isEmpty() && activeRemovals.isEmpty()) {
            cr$pendingStripScrollX = 0;

            cr$renderReplyOverMessage(graphics, mc, mouseX, mouseY);
            chatremastered$updateCursorAndHover(graphics, mc, mouseX, mouseY);
            chatremastered$renderMenu(graphics, mc, mouseX, mouseY);
            cr$screenshotsPanel.render(graphics, self.width, self.height, mouseX, mouseY);
            return;
        }

        int rowBottom = inputBarTop - 6 - cr$getPendingPreviewAreaHeight(mc);
        int rowLeft = 4;
        int rowRight = self.width - 4;
        int totalRowW = cr$pendingRowTotalWidth(pendingAll);
        int maxScrollX = Math.max(0, totalRowW - (rowRight - rowLeft));
        cr$pendingStripScrollX = Mth.clamp(cr$pendingStripScrollX, 0, maxScrollX);

        int maxCardH = 0;
        for (PendingImageState.PendingImage p : pendingAll) maxCardH = Math.max(maxCardH, p.getHeight());
        for (PendingCardAnimator.RemoveState r : activeRemovals) maxCardH = Math.max(maxCardH, r.cardH);
        boolean needsScissorBase = totalRowW > (rowRight - rowLeft);
        if (needsScissorBase) {
            graphics.enableScissor(rowLeft, rowBottom - maxCardH - 4, rowRight, rowBottom + 4);
        }
        int cardX = rowLeft - cr$pendingStripScrollX;
        for (int i = 0; i < pendingAll.size(); i++) {
            PendingImageState.PendingImage card = pendingAll.get(i);
            int smoothedX = cr$pendingAnim.smoothX(card.getUid(), cardX);
            PendingCardAnimator.SpawnState spawn = cr$pendingAnim.getSpawn(card.getUid());
            int cardW = cr$renderPendingCard(graphics, mc, mouseX, mouseY, card, smoothedX, rowBottom, spawn);
            cardX += cardW + PENDING_STRIP_GAP;
        }
        if (needsScissorBase) {
            graphics.disableScissor();
        }

        for (PendingCardAnimator.RemoveState r : activeRemovals) {
            cr$renderRemovingCard(graphics, mc, r, self.height);
        }

        cr$renderReplyOverMessage(graphics, mc, mouseX, mouseY);

        chatremastered$updateCursorAndHover(graphics, mc, mouseX, mouseY);
        chatremastered$renderMenu(graphics, mc, mouseX, mouseY);
        if (cr$tagCommandSuggestions != null) {
            cr$tagCommandSuggestions.render(graphics, mouseX, mouseY);
        }
        cr$screenshotsPanel.render(graphics, self.width, self.height, mouseX, mouseY);
    }

    private int cr$pendingRowTotalWidth(java.util.List<PendingImageState.PendingImage> pendingAll) {
        int total = 0;
        for (int i = 0; i < pendingAll.size(); i++) {
            total += pendingAll.get(i).getWidth();
            if (i < pendingAll.size() - 1) total += PENDING_STRIP_GAP;
        }
        return total;
    }

    private int cr$renderPendingCard(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY,
                                     PendingImageState.PendingImage pending, int previewLeft, int previewBottom,
                                     PendingCardAnimator.SpawnState spawn) {
        int dispW = pending.getWidth();
        int dispH = pending.getHeight();
        int previewTop = previewBottom - dispH;

        if (spawn != null && !spawn.isDone()) {
            float scale = Math.max(0.02f, spawn.scale());
            int cx = previewLeft + dispW / 2;
            int cy = previewBottom - dispH / 2;
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-dispW / 2f, -dispH / 2f);
            cr$renderPendingCardContent(graphics, mc, mouseX, mouseY, pending, 0, 0, dispH, dispW, dispH, 0xFFFFFFFF);
            graphics.pose().popMatrix();
            return dispW;
        }

        cr$renderPendingCardContent(graphics, mc, mouseX, mouseY, pending, previewLeft, previewTop, previewBottom, dispW, dispH, 0xFFFFFFFF);
        return dispW;
    }

    private void cr$renderPendingCardContent(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY,
                                              PendingImageState.PendingImage pending, int previewLeft, int previewTop, int previewBottom,
                                              int dispW, int dispH, int alphaTint) {
        boolean isLoaded = pending.isLoaded();
        Identifier tex = pending.getTextureId();
        int a = (alphaTint >>> 24) & 0xFF;

        graphics.fill(previewLeft - 1, previewTop - 2, previewLeft + dispW + 1, previewBottom + 1, cr$tintAlpha(0xAA000000, a));

        if (!isLoaded || tex == null) {
            boolean sizeKnown = pending.getSizeKnown();
            graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewBottom, cr$tintAlpha(0xFF2A2A2A, a));
            int borderColor = cr$tintAlpha(0xFF555555, a);
            graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewTop + 1, borderColor);
            graphics.fill(previewLeft, previewBottom - 1, previewLeft + dispW, previewBottom, borderColor);
            graphics.fill(previewLeft, previewTop, previewLeft + 1, previewBottom, borderColor);
            graphics.fill(previewLeft + dispW - 1, previewTop, previewLeft + dispW, previewBottom, borderColor);

            float progress = pending.getProgress();
            if (progress < 0f && dispH >= 6) {
                int barH = Math.max(3, dispH / 8);
                int barTop = previewBottom - barH;
                int barFull = dispW - 2;
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barFull, previewBottom - 1, cr$tintAlpha(0xFF1A1A1A, a));
                long now = System.currentTimeMillis();
                float phase = (now % 1200L) / 1200f;
                int dotW = Math.max(4, barFull / 3);
                int dotStart = Math.round((barFull - dotW) * phase);
                graphics.fill(previewLeft + 1 + dotStart, barTop, previewLeft + 1 + dotStart + dotW, previewBottom - 1, cr$tintAlpha(0xFF3399EE, a));
                if (dispW >= 40 && barTop - previewTop >= mc.font.lineHeight + 2) {
                    String waitStr = "...";
                    int textX = previewLeft + (dispW - mc.font.width(waitStr)) / 2;
                    int textY = barTop - mc.font.lineHeight - 1;
                    if (textY >= previewTop + 1)
                        graphics.drawString(mc.font, waitStr, textX, textY, cr$tintAlpha(0xFFAAAAAA, a), false);
                }
            } else if (progress >= 0f && dispH >= 6) {
                int barH = Math.max(3, dispH / 8);
                int barTop = previewBottom - barH;
                int barFull = dispW - 2;
                int barW = Math.max(1, Math.round(barFull * progress));
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barFull, previewBottom - 1, cr$tintAlpha(0xFF1A1A1A, a));
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barW, previewBottom - 1, cr$tintAlpha(0xFF3399EE, a));
                if (dispW >= 40 && barTop - previewTop >= mc.font.lineHeight + 2) {
                    int pct = Math.round(progress * 100f);
                    String pctStr = pct + "%";
                    int textX = previewLeft + (dispW - mc.font.width(pctStr)) / 2;
                    int textY = barTop - mc.font.lineHeight - 1;
                    if (textY >= previewTop + 1)
                        graphics.drawString(mc.font, pctStr, textX, textY, cr$tintAlpha(0xFFFFFFFF, a), false);
                }
            }

            int barAreaH = (dispH >= 6) ? Math.max(3, dispH / 8) + 2 : 0;
            int iconAreaH = dispH - barAreaH;
            if (iconAreaH >= 12) {
                float iconScale = iconAreaH * 0.38f / 9.0f;
                iconScale = Math.min(iconScale, dispW * 0.55f / mc.font.lineHeight);
                iconScale = Math.max(iconScale, 1.0f);
                String icon = !sizeKnown ? "?" : "🖼";
                int iconColor = cr$tintAlpha(!sizeKnown ? 0xFF888888 : 0xFFCCCCCC, a);
                int iconPxW = Math.round(mc.font.width(icon) * iconScale);
                int iconPxH = Math.round(mc.font.lineHeight * iconScale);
                int iconX = previewLeft + (dispW - iconPxW) / 2;
                int iconY = previewTop + (iconAreaH - iconPxH) / 2;
                graphics.pose().pushMatrix();
                graphics.pose().translate(iconX, iconY);
                graphics.pose().scale(iconScale, iconScale);
                graphics.drawString(mc.font, icon, 0, 0, iconColor, false);
                graphics.pose().popMatrix();
            }
        } else {
            int texW = pending.getTextureWidth();
            int texH = pending.getTextureHeight();
            float scaleX = (float) dispW / texW;
            float scaleY = (float) dispH / texH;
            float s = Math.min(scaleX, scaleY);
            int fitW = Math.max(1, Math.round(texW * s));
            int fitH = Math.max(1, Math.round(texH * s));
            int offsetX = (dispW - fitW) / 2;
            int offsetY = (dispH - fitH) / 2;
            graphics.pose().pushMatrix();
            graphics.pose().translate(previewLeft + offsetX, previewTop + offsetY);
            graphics.pose().scale(s, s);
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, texW, texH, texW, texH, cr$tintAlpha(0xFFFFFFFF, a));
            graphics.pose().popMatrix();
        }

        int closeSize = 14;
        int closeX = previewLeft + dispW - closeSize - 2;
        int closeY = previewTop + 2;
        boolean hoverClose = mouseX >= closeX && mouseX < closeX + closeSize && mouseY >= closeY && mouseY < closeY + closeSize;
        int closeColor = cr$tintAlpha(hoverClose ? 0xFFFF5555 : 0x88FFFFFF, a);
        graphics.drawCenteredString(mc.font, "✕", closeX + closeSize / 2, closeY + (closeSize - mc.font.lineHeight) / 2, closeColor);
    }

    private static int cr$tintAlpha(int argb, int tintA) {
        int origA = (argb >>> 24) & 0xFF;
        int newA = Math.min(origA, tintA);
        return (newA << 24) | (argb & 0x00FFFFFF);
    }

    private void cr$renderRemovingCard(GuiGraphics graphics, Minecraft mc, PendingCardAnimator.RemoveState r, int screenH) {
        if (r.shards != null) {
            cr$renderShatterCard(graphics, mc, r);
            return;
        }
        int top = r.currentY(screenH);
        float alpha = r.alpha(screenH);
        int a = Math.round(alpha * 255f);
        if (a <= 0) return;

        int centerX = r.currentX() + r.cardW / 2;
        int centerY = top + r.cardH / 2;
        float angleRad = (float) Math.toRadians(r.currentRotation());

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate(angleRad);
        graphics.pose().translate(-r.cardW / 2f, -r.cardH / 2f);
        cr$renderRemovingCardContent(graphics, mc, r, 0, 0, r.cardW, r.cardH, a);
        graphics.pose().popMatrix();
    }

    private void cr$renderShatterCard(GuiGraphics graphics, Minecraft mc, PendingCardAnimator.RemoveState r) {
        if (!r.isLoaded || r.tex == null) {

            float p = Math.min(1f, r.elapsedMs() / (float) PendingCardAnimator.SHATTER_MS);
            int a = Math.round(255 * (1f - p));
            if (a > 0) {
                cr$renderRemovingCardContent(graphics, mc, r, r.cardX, r.cardTop, r.cardW, r.cardH, a);
            }
            return;
        }
        for (PendingCardAnimator.Shard s : r.shards) {
            float alpha = r.shardAlpha(s);
            int a = Math.round(alpha * 255f);
            if (a <= 0) continue;

            int offX = Math.round(r.shardOffsetX(s));
            int offY = Math.round(r.shardOffsetY(s));
            float angleRad = (float) Math.toRadians(r.shardRotation(s));

            float pivotX = (s.minX + s.maxX) / 2f;
            float pivotY = (s.minY + s.maxY) / 2f;
            int centerX = r.cardX + Math.round(pivotX) + offX;
            int centerY = r.cardTop + Math.round(pivotY) + offY;

            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY);
            graphics.pose().rotate(angleRad);
            graphics.pose().translate(-(pivotX - s.minX), -(pivotY - s.minY));
            cr$renderShardTexture(graphics, r, s, a);
            graphics.pose().popMatrix();
        }
    }

    private void cr$renderShardTexture(GuiGraphics graphics, PendingCardAnimator.RemoveState r, PendingCardAnimator.Shard s, int alphaByte) {
        int texW = r.texW;
        int texH = r.texH;
        float scaleX = (float) r.cardW / texW;
        float scaleY = (float) r.cardH / texH;
        float scale = Math.min(scaleX, scaleY);
        int fitW = Math.max(1, Math.round(texW * scale));
        int fitH = Math.max(1, Math.round(texH * scale));
        int fitOffX = (r.cardW - fitW) / 2;
        int fitOffY = (r.cardH - fitH) / 2;

        int tint = cr$tintAlpha(0xFFFFFFFF, alphaByte);
        int maskW = s.maskW;
        int maskH = s.maskH;
        boolean[] mask = s.mask;

        for (int my = 0; my < maskH; my++) {
            int rowStart = -1;
            for (int mx = 0; mx <= maskW; mx++) {
                boolean filled = mx < maskW && mask[my * maskW + mx];
                if (filled && rowStart < 0) {
                    rowStart = mx;
                } else if (!filled && rowStart >= 0) {
                    cr$blitShardRun(graphics, r, s, rowStart, my, mx - rowStart, texW, texH, scale, fitOffX, fitOffY, tint);
                    rowStart = -1;
                }
            }
        }
    }

    private void cr$blitShardRun(GuiGraphics graphics, PendingCardAnimator.RemoveState r, PendingCardAnimator.Shard s,
                                  int runX, int runY, int runLen, int texW, int texH, float scale,
                                  int fitOffX, int fitOffY, int tint) {
        int cardPxX = s.minX + runX;
        int cardPxY = s.minY + runY;

        float srcU0 = (cardPxX - fitOffX) / scale;
        float srcV0 = (cardPxY - fitOffY) / scale;
        float srcU1 = (cardPxX + runLen - fitOffX) / scale;
        float srcV1 = (cardPxY + 1 - fitOffY) / scale;
        srcU0 = Math.max(0, Math.min(texW, srcU0));
        srcV0 = Math.max(0, Math.min(texH, srcV0));
        srcU1 = Math.max(0, Math.min(texW, srcU1));
        srcV1 = Math.max(0, Math.min(texH, srcV1));
        float srcW = Math.max(1f, srcU1 - srcU0);
        float srcH = Math.max(1f, srcV1 - srcV0);

        int destX = runX;
        int destY = runY;
        int destW = runLen;
        int destH = 1;

        graphics.pose().pushMatrix();
        graphics.pose().translate(destX, destY);
        graphics.pose().scale((float) destW / srcW, (float) destH / srcH);
        graphics.blit(RenderPipelines.GUI_TEXTURED, r.tex, 0, 0, srcU0, srcV0, (int) srcW, (int) srcH, texW, texH, tint);
        graphics.pose().popMatrix();
    }

    private void cr$renderRemovingCardContent(GuiGraphics graphics, Minecraft mc, PendingCardAnimator.RemoveState r,
                                               int previewLeft, int previewTop, int dispW, int dispH, int alphaByte) {
        int previewBottom = previewTop + dispH;
        graphics.fill(previewLeft - 1, previewTop - 2, previewLeft + dispW + 1, previewBottom + 1, cr$tintAlpha(0xAA000000, alphaByte));

        if (r.isLoaded && r.tex != null) {
            int texW = r.texW;
            int texH = r.texH;
            float scaleX = (float) dispW / texW;
            float scaleY = (float) dispH / texH;
            float s = Math.min(scaleX, scaleY);
            int fitW = Math.max(1, Math.round(texW * s));
            int fitH = Math.max(1, Math.round(texH * s));
            int offsetX = (dispW - fitW) / 2;
            int offsetY = (dispH - fitH) / 2;
            graphics.pose().pushMatrix();
            graphics.pose().translate(previewLeft + offsetX, previewTop + offsetY);
            graphics.pose().scale(s, s);
            graphics.blit(RenderPipelines.GUI_TEXTURED, r.tex, 0, 0, 0f, 0f, texW, texH, texW, texH, cr$tintAlpha(0xFFFFFFFF, alphaByte));
            graphics.pose().popMatrix();
        } else {
            graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewBottom, cr$tintAlpha(0xFF2A2A2A, alphaByte));

            float progress = r.progressSnapshot;
            int barAreaH = (dispH >= 6) ? Math.max(3, dispH / 8) + 2 : 0;
            if (dispH >= 6) {
                int barH = Math.max(3, dispH / 8);
                int barTop = previewBottom - barH;
                int barFull = dispW - 2;
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barFull, previewBottom - 1, cr$tintAlpha(0xFF1A1A1A, alphaByte));
                if (progress >= 0f) {
                    int barW = Math.max(1, Math.round(barFull * progress));
                    graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barW, previewBottom - 1, cr$tintAlpha(0xFF3399EE, alphaByte));
                } else {
                    int dotW = Math.max(4, barFull / 3);
                    graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + dotW, previewBottom - 1, cr$tintAlpha(0xFF3399EE, alphaByte));
                }
            }

            int iconAreaH = dispH - barAreaH;
            if (iconAreaH >= 12) {
                float iconScale = iconAreaH * 0.38f / 9.0f;
                iconScale = Math.min(iconScale, dispW * 0.55f / mc.font.lineHeight);
                iconScale = Math.max(iconScale, 1.0f);
                String icon = !r.sizeKnown ? "?" : "🖼";
                int iconColor = cr$tintAlpha(!r.sizeKnown ? 0xFF888888 : 0xFFCCCCCC, alphaByte);
                int iconPxW = Math.round(mc.font.width(icon) * iconScale);
                int iconPxH = Math.round(mc.font.lineHeight * iconScale);
                int iconX = previewLeft + (dispW - iconPxW) / 2;
                int iconY = previewTop + (iconAreaH - iconPxH) / 2;
                graphics.pose().pushMatrix();
                graphics.pose().translate(iconX, iconY);
                graphics.pose().scale(iconScale, iconScale);
                graphics.drawString(mc.font, icon, 0, 0, iconColor, false);
                graphics.pose().popMatrix();
            }
        }

        int borderColor = cr$tintAlpha(0xFF555555, alphaByte);
        graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewTop + 1, borderColor);
        graphics.fill(previewLeft, previewBottom - 1, previewLeft + dispW, previewBottom, borderColor);
        graphics.fill(previewLeft, previewTop, previewLeft + 1, previewBottom, borderColor);
        graphics.fill(previewLeft + dispW - 1, previewTop, previewLeft + dispW, previewBottom, borderColor);
    }

    private static PendingImageState.PendingImage pending() {
        return PendingImageState.getPending();
    }

    private void chatremastered$updateCursorAndHover(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {

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
                    graphics.renderTooltip(mc.font, lines, mouseX, mouseY,
                            (sw, sh, x, y, w, h) -> new org.joml.Vector2i(x, y - h - 4), null);
                    if (!cr$menuOpen) {
                        cr$hoveredMsgLine = cr$findLineIndexForImageId(mc, msg.getImageId());
                    }
                    if (!isDeleted && !isError)
                        GLFW.glfwSetCursor(mc.getWindow().handle(), GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR));
                    else
                        GLFW.glfwSetCursor(mc.getWindow().handle(), 0L);
                    return;
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
                graphics.renderTooltip(mc.font, lines, mouseX, mouseY,
                        (sw, sh, x, y, w, h) -> new org.joml.Vector2i(x, y - h - 4), null);

                if (!cr$menuOpen) {
                    cr$hoveredMsgLine = cr$findLineIndexForImageId(mc, msg.getImageId());
                }
                if (!isDeleted && !isError)
                    GLFW.glfwSetCursor(mc.getWindow().handle(), GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR));
                else
                    GLFW.glfwSetCursor(mc.getWindow().handle(), 0L);
                return;
            }
        }

        boolean mouseOverImage = false;
        for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
            if (!imgMsg.getDismissed() && imgMsg.hasScreenBounds()
                    && mouseX >= imgMsg.getBoundsX0() && mouseX < imgMsg.getBoundsX1()
                    && mouseY >= imgMsg.getBoundsY0() && mouseY < imgMsg.getBoundsY1()) {
                mouseOverImage = true;
                if (!cr$menuOpen) {

                    cr$hoveredMsgLine = cr$findLineIndexForImageId(mc, imgMsg.getImageId());
                }
                break;
            }
            if (!imgMsg.getRowCardBounds().isEmpty()) {
                for (int[] b : imgMsg.getRowCardBounds().values()) {
                    if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                        mouseOverImage = true;
                        if (!cr$menuOpen) {
                            cr$hoveredMsgLine = cr$findLineIndexForImageId(mc, imgMsg.getImageId());
                        }
                        break;
                    }
                }
                if (mouseOverImage) break;
            }
        }

        List<PendingImageState.PendingImage> pendAll = PendingImageState.getAll();
        if (!pendAll.isEmpty()) {
            ChatScreen self2 = (ChatScreen)(Object)this;
            int inputBarTopH = self2.height - 12;
            int rowBottom = inputBarTopH - 6 - cr$getPendingPreviewAreaHeight(mc);
            int maxDispH = 0;
            for (PendingImageState.PendingImage p : pendAll) {
                maxDispH = Math.max(maxDispH, p.getHeight());
            }
            int rowTop = rowBottom - maxDispH;
            int rowLeft = 4;
            int rowRight = self2.width - 4;
            if (mouseY >= rowTop - 2 && mouseY <= rowBottom + 2 && mouseX >= rowLeft && mouseX <= rowRight) {
                mouseOverImage = true;
            }
        }
        if (!cr$menuOpen && !mouseOverImage) {
            cr$hoveredMsgLine = cr$getLineIndexAt(mc, mouseX, mouseY);
        } else if (!cr$menuOpen && !mouseOverImage) {
            cr$hoveredMsgLine = -1;
        }
        GLFW.glfwSetCursor(mc.getWindow().handle(), 0L);
    }

    private int cr$getLineIndexAt(Minecraft mc, int mouseX, int mouseY) {
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

    private void cr$drawLineHighlight(GuiGraphics graphics, Minecraft mc, int lineIdx) {
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

    private void chatremastered$renderMenu(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        if (!cr$menuOpen) return;
        int n = cr$menuLabels.length;
        int ITEM_H = MENU_ITEM_H;
        int PAD = MENU_PAD;
        int menuH = PAD * 2 + n * ITEM_H + (n - 1) * 2;

        float elapsed = System.currentTimeMillis() - cr$menuOpenTime;
        float t = Math.min(1f, elapsed / MENU_ANIM_MS);

        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        int visibleH = Math.round(menuH * ease);
        float alpha = Math.min(1f, t * 2f);

        int mx0 = cr$menuX, my0 = cr$menuY;
        int mx1 = mx0 + cr$menuComputedW, my1 = my0 + menuH;

        int clipBot = my0 + visibleH;

        int shadowAlpha = Math.round(0x55 * alpha);
        for (int s = 4; s >= 1; s--) {
            int sa = shadowAlpha / s;
            cr$fillClipped(graphics, mx0 + s, my0 + s, mx1 + s, Math.min(clipBot + s, my1 + s),
                    (sa << 24), my0, clipBot + s);
        }

        int bgAlpha = Math.round(0xEC * alpha);
        cr$fillClipped(graphics, mx0, my0, mx1, clipBot, (bgAlpha << 24) | 0x1E1E1E, my0, clipBot);

        int borderAlpha = Math.round(0xFF * alpha);
        int borderColor = (borderAlpha << 24) | 0x3A3A3A;

        cr$fillClipped(graphics, mx0, my0, mx1, my0 + 1, borderColor, my0, clipBot);

        cr$fillClipped(graphics, mx0, my1 - 1, mx1, my1, borderColor, my0, clipBot);

        cr$fillClipped(graphics, mx0, my0, mx0 + 1, my1, borderColor, my0, clipBot);
        cr$fillClipped(graphics, mx1 - 1, my0, mx1, my1, borderColor, my0, clipBot);

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
            cr$menuItemAnim[i] += (target - cr$menuItemAnim[i]) * 0.3f;
            float a = cr$menuItemAnim[i];
            if (a > 0.01f) {
                int hAlpha = Math.round(0xFF * a * alpha);
                boolean isRedItem = cr$menuColors != null && i < cr$menuColors.length && cr$menuColors[i] == 0xFF4444;
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
                cr$fillClipped(graphics, hx0, iy, hx1, clipIy2, hoverColor, my0, clipBot);
                cr$fillClipped(graphics, hx0 + 1, iy - 1, hx1 - 1, iy, hoverColor, my0, clipBot);
                cr$fillClipped(graphics, hx0 + 1, iy2, hx1 - 1, iy2 + 1, hoverColor, my0, clipBot);
                cr$fillClipped(graphics, hx0 - 1, iy + 1, hx0, iy2 - 1, hoverColor, my0, clipBot);
                cr$fillClipped(graphics, hx1, iy + 1, hx1 + 1, iy2 - 1, hoverColor, my0, clipBot);
            }

            if (iy2 > clipBot) continue;

            int textAlpha = Math.round(0xFF * alpha);

            int baseColor = (cr$menuColors != null && i < cr$menuColors.length && cr$menuColors[i] != 0)
                    ? cr$menuColors[i] : 0xE0E0E0;
            int textColor = hovered
                    ? (textAlpha << 24 | 0xFFFFFF)
                    : (textAlpha << 24 | baseColor);
            int textY = iy + (ITEM_H - mc.font.lineHeight) / 2 + 1;
            String icon = cr$menuIcons[i];
            if (!icon.isEmpty()) {
                graphics.drawString(mc.font, icon, mx0 + PAD + 2, textY, textColor, false);
                graphics.drawString(mc.font, cr$menuLabels[i], mx0 + PAD + 11, textY, textColor, false);
            } else {
                graphics.drawString(mc.font, cr$menuLabels[i], mx0 + PAD + 2, textY, textColor, false);
            }
        }
    }

    private void cr$fillClipped(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int clipTop, int clipBot) {
        y0 = Math.max(y0, clipTop);
        y1 = Math.min(y1, clipBot);
        if (y0 >= y1 || x0 >= x1) return;
        g.fill(x0, y0, x1, y1, color);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void chatremastered$handleKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (cr$tagCommandSuggestions != null && cr$tagCommandSuggestions.isVisible()
                && cr$tagCommandSuggestions.keyPressed(event)) {
            cir.setReturnValue(true);
            return;
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE && cr$menuOpen) {
            cr$closeMenu();
            cir.setReturnValue(true);
            return;
        }

        if (event.isPaste()) {
            cir.setReturnValue(true);
            Thread t = new Thread(() -> {
                boolean hasImage = chatremastered$clipboardHasImage();
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (hasImage) {

                        if (!ChatRemasteredClient.canSendPhoto(mc)) return;
                        ChatRemasteredClient.pasteImageFromClipboard();
                    } else {
                        String text = mc.keyboardHandler.getClipboard();
                        if (text != null && !text.isEmpty() && input != null)
                            input.insertText(text);
                    }
                });
            });
            t.setDaemon(true);
            t.setName("Chat Remastered-ClipboardCheck");
            t.start();
            return;
        }

        int key = event.key();
        if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) return;

        if (input != null) {
            String rawValue = input.getValue();
            dev.errnicraft.chatremastered.EntityChatParser.ParsedCommand parsed =
                    dev.errnicraft.chatremastered.EntityChatParser.parse(rawValue);
            dev.errnicraft.chatremastered.EntityChatParser.ParsedEntityCommand parsedEntity =
                    parsed == null ? dev.errnicraft.chatremastered.EntityChatParser.parseEntity(rawValue) : null;
            dev.errnicraft.chatremastered.EntityChatParser.ParsedItemCommand parsedItem =
                    (parsed == null && parsedEntity == null)
                            ? dev.errnicraft.chatremastered.EntityChatParser.parseItem(rawValue) : null;
            dev.errnicraft.chatremastered.EntityChatParser.ParsedHandItemCommand parsedHandItem =
                    (parsed == null && parsedEntity == null && parsedItem == null)
                            ? dev.errnicraft.chatremastered.EntityChatParser.parseHandItem(rawValue) : null;
            dev.errnicraft.chatremastered.EntityChatParser.ParsedLookEntityCommand parsedLookEntity =
                    (parsed == null && parsedEntity == null && parsedItem == null && parsedHandItem == null)
                            ? dev.errnicraft.chatremastered.EntityChatParser.parseLookEntity(rawValue) : null;
            dev.errnicraft.chatremastered.EntityChatParser.ParsedShortPlayerCommand parsedShortPlayer =
                    (parsed == null && parsedEntity == null && parsedItem == null && parsedHandItem == null
                            && parsedLookEntity == null)
                            ? dev.errnicraft.chatremastered.EntityChatParser.parseShortPlayer(rawValue) : null;
            dev.errnicraft.chatremastered.EntityChatParser.ParsedUuidCommand parsedUuid =
                    (parsed == null && parsedEntity == null && parsedItem == null && parsedHandItem == null
                            && parsedLookEntity == null && parsedShortPlayer == null)
                            ? dev.errnicraft.chatremastered.EntityChatParser.parseUuid(rawValue) : null;
            if (parsed != null) {
                if (ChatRemasteredConfig.getMuted()) {
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
                            net.minecraft.network.chat.Component.literal("§c[Chat Remastered] " +
                                    ChatRemasteredConfig.tr("chat-remastered.muted"))
                    );
                    cir.setReturnValue(true);
                    return;
                }
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.errnicraft.chatremastered.network.packet.EntityChatPacket(
                                parsed.targetPlayerName(), parsed.behavior(), parsed.caption())
                );
                net.minecraft.client.Minecraft.getInstance().gui.getChat().addRecentChat(rawValue);
                input.setValue("");
                cr$resetTagSuggestions();
                cr$clearReply();
                cir.setReturnValue(true);
                return;
            }
            if (parsedEntity != null) {
                if (ChatRemasteredConfig.getMuted()) {
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
                            net.minecraft.network.chat.Component.literal("§c[Chat Remastered] " +
                                    ChatRemasteredConfig.tr("chat-remastered.muted"))
                    );
                    cir.setReturnValue(true);
                    return;
                }
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.errnicraft.chatremastered.network.packet.EntityMobChatPacket(
                                parsedEntity.entityNamespace(), parsedEntity.entityPath(), parsedEntity.entityNbt(),
                                parsedEntity.behavior(),
                                parsedEntity.size() != null ? Math.round(parsedEntity.size() * 1000f) : -1,
                                parsedEntity.offsetX() != null ? parsedEntity.offsetX() : 0,
                                parsedEntity.offsetY() != null ? parsedEntity.offsetY() : 0,
                                parsedEntity.caption())
                );
                net.minecraft.client.Minecraft.getInstance().gui.getChat().addRecentChat(rawValue);
                input.setValue("");
                cr$resetTagSuggestions();
                cr$clearReply();
                cir.setReturnValue(true);
                return;
            }
            if (parsedItem != null) {
                if (ChatRemasteredConfig.getMuted()) {
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
                            net.minecraft.network.chat.Component.literal("§c[Chat Remastered] " +
                                    ChatRemasteredConfig.tr("chat-remastered.muted"))
                    );
                    cir.setReturnValue(true);
                    return;
                }
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.errnicraft.chatremastered.network.packet.ItemChatPacket(
                                parsedItem.itemNamespace(), parsedItem.itemPath(), parsedItem.itemNbt(),
                                parsedItem.caption())
                );
                net.minecraft.client.Minecraft.getInstance().gui.getChat().addRecentChat(rawValue);
                input.setValue("");
                cr$resetTagSuggestions();
                cr$clearReply();
                cir.setReturnValue(true);
                return;
            }
            if (parsedHandItem != null) {
                if (ChatRemasteredConfig.getMuted()) {
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
                            net.minecraft.network.chat.Component.literal("§c[Chat Remastered] " +
                                    ChatRemasteredConfig.tr("chat-remastered.muted"))
                    );
                    cir.setReturnValue(true);
                    return;
                }
                net.minecraft.client.Minecraft handMc = net.minecraft.client.Minecraft.getInstance();
                net.minecraft.world.item.ItemStack handStack =
                        handMc.player != null ? handMc.player.getMainHandItem() : net.minecraft.world.item.ItemStack.EMPTY;
                if (handStack.isEmpty()) {
                    cir.setReturnValue(true);
                    return;
                }
                net.minecraft.resources.Identifier handItemId =
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(handStack.getItem());
                String handNbt = "";
                net.minecraft.core.component.DataComponentPatch handPatch = handStack.getComponentsPatch();
                if (!handPatch.isEmpty()) {
                    try {
                        net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops =
                                handMc.level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                        net.minecraft.nbt.Tag encoded =
                                net.minecraft.core.component.DataComponentPatch.CODEC.encodeStart(ops, handPatch).getOrThrow();
                        if (encoded instanceof net.minecraft.nbt.CompoundTag compound) {
                            handNbt = compound.toString();
                        }
                    } catch (Exception ignored) {
                    }
                }
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.errnicraft.chatremastered.network.packet.ItemChatPacket(
                                handItemId.getNamespace(), handItemId.getPath(), handNbt,
                                parsedHandItem.caption())
                );
                net.minecraft.client.Minecraft.getInstance().gui.getChat().addRecentChat(rawValue);
                input.setValue("");
                cr$resetTagSuggestions();
                cr$clearReply();
                cir.setReturnValue(true);
                return;
            }
            if (parsedLookEntity != null) {
                if (ChatRemasteredConfig.getMuted()) {
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
                            net.minecraft.network.chat.Component.literal("§c[Chat Remastered] " +
                                    ChatRemasteredConfig.tr("chat-remastered.muted"))
                    );
                    cir.setReturnValue(true);
                    return;
                }
                net.minecraft.client.Minecraft lookMc = net.minecraft.client.Minecraft.getInstance();
                net.minecraft.world.entity.Entity lookTarget = lookMc.crosshairPickEntity;
                if (lookTarget == null) {
                    cir.setReturnValue(true);
                    return;
                }
                if (lookTarget instanceof net.minecraft.client.player.AbstractClientPlayer lookPlayer) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new dev.errnicraft.chatremastered.network.packet.EntityChatPacket(
                                    lookPlayer.getGameProfile().name(), "rotate", parsedLookEntity.caption())
                    );
                } else {
                    net.minecraft.resources.Identifier lookEntityId =
                            net.minecraft.world.entity.EntityType.getKey(lookTarget.getType());
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new dev.errnicraft.chatremastered.network.packet.EntityMobChatPacket(
                                    lookEntityId.getNamespace(), lookEntityId.getPath(), "",
                                    "rotate", -1, 0, 0, parsedLookEntity.caption())
                    );
                }
                net.minecraft.client.Minecraft.getInstance().gui.getChat().addRecentChat(rawValue);
                input.setValue("");
                cr$resetTagSuggestions();
                cr$clearReply();
                cir.setReturnValue(true);
                return;
            }
            if (parsedShortPlayer != null) {
                if (ChatRemasteredConfig.getMuted()) {
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
                            net.minecraft.network.chat.Component.literal("§c[Chat Remastered] " +
                                    ChatRemasteredConfig.tr("chat-remastered.muted"))
                    );
                    cir.setReturnValue(true);
                    return;
                }
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.errnicraft.chatremastered.network.packet.EntityChatPacket(
                                parsedShortPlayer.targetPlayerName(), "rotate", parsedShortPlayer.caption())
                );
                net.minecraft.client.Minecraft.getInstance().gui.getChat().addRecentChat(rawValue);
                input.setValue("");
                cr$resetTagSuggestions();
                cr$clearReply();
                cir.setReturnValue(true);
                return;
            }
            if (parsedUuid != null) {
                if (ChatRemasteredConfig.getMuted()) {
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
                            net.minecraft.network.chat.Component.literal("§c[Chat Remastered] " +
                                    ChatRemasteredConfig.tr("chat-remastered.muted"))
                    );
                    cir.setReturnValue(true);
                    return;
                }
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.errnicraft.chatremastered.network.packet.EntityByUuidChatPacket(
                                parsedUuid.uuid(), parsedUuid.caption())
                );
                net.minecraft.client.Minecraft.getInstance().gui.getChat().addRecentChat(rawValue);
                input.setValue("");
                cr$resetTagSuggestions();
                cr$clearReply();
                cir.setReturnValue(true);
                return;
            }
        }

        PendingImageState.PendingImage pending = PendingImageState.getPending();
        if (pending == null) {

            if (cr$replyAddedTime >= 0 && input != null && !input.getValue().trim().isEmpty()) {

                if (ChatRemasteredConfig.getMuted()) {
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
                            net.minecraft.network.chat.Component.literal("§c[Chat Remastered] " +
                                    ChatRemasteredConfig.tr("chat-remastered.muted"))
                    );
                    cir.setReturnValue(true);
                    return;
                }
                String text = input.getValue().trim();
                String replyToSender = cr$replySenderName != null ? cr$replySenderName : "";
                String replyToText   = cr$replyText     != null ? cr$replyText     : "";
                String replyToImgId  = cr$replyImageId  != null ? cr$replyImageId  : "";
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.errnicraft.chatremastered.network.packet.ReplyMetaPacket(text, replyToSender, replyToText, replyToImgId)
                );
                net.minecraft.client.Minecraft.getInstance().gui.getChat().addRecentChat(text);
                input.setValue("");
                cr$resetTagSuggestions();
                cr$clearReply();
                cir.setReturnValue(true);
                return;
            }

            if (cr$replyAddedTime < 0) {
                cr$clearReply();
            }
            return;
        }
        if (!pending.isLoaded()) { cir.setReturnValue(true); return; }

        String caption = (input != null && !input.getValue().trim().isEmpty())
                ? input.getValue().trim() : null;

        if (cr$replyAddedTime >= 0) {
            String rSender = cr$replySenderName  != null ? cr$replySenderName  : "";
            String rText   = cr$replyText        != null ? cr$replyText        : "";
            String rImgId  = cr$replyImageId     != null ? cr$replyImageId     : "";
            ChatRemasteredClient.sendPendingImageWithCaptionAndReply(caption, rSender, rText, rImgId);
        } else {
            ChatRemasteredClient.sendPendingImageWithCaption(caption);
        }
        if (input != null) { input.setValue(""); cr$resetTagSuggestions(); }
        cr$clearReply();
        cir.setReturnValue(true);
    }

    private boolean chatremastered$clipboardHasImage() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                String script = "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; " +
                        "if ([System.Windows.Forms.Clipboard]::GetImage() -ne $null) { exit 0 } else { exit 1 }";
                Process proc = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                        .redirectErrorStream(true).start();
                return proc.waitFor() == 0;
            } else if (os.contains("mac")) {
                String script = "try\n  set x to the clipboard as «class PNGf»\n  return \"ok\"\non error\n  return \"no\"\nend try";
                Process proc = new ProcessBuilder("osascript", "-e", script).redirectErrorStream(true).start();
                String result = new String(proc.getInputStream().readAllBytes()).trim();
                proc.waitFor();
                return result.equals("ok");
            } else {
                Process proc = new ProcessBuilder("xclip", "-selection", "clipboard", "-t", "TARGETS", "-o")
                        .redirectErrorStream(true).start();
                String targets = new String(proc.getInputStream().readAllBytes());
                proc.waitFor();
                return targets.contains("image/png") || targets.contains("image/jpeg") || targets.contains("image/gif");
            }
        } catch (Exception e) {
            System.out.println("[Chat Remastered] clipboardHasImage error: " + e);
            return false;
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void chatremastered$handleMouseScrolled(double mx, double my, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (cr$screenshotsPanel.isOpen()) {
            ChatScreen selfPanel = (ChatScreen)(Object)this;
            if (cr$screenshotsPanel.mouseScrolled(selfPanel.width, selfPanel.height, mx, my, scrollY)) {
                cir.setReturnValue(true);
                return;
            }
        }
        if (cr$tagCommandSuggestions != null && cr$tagCommandSuggestions.mouseScrolled(scrollY)) {
            cir.setReturnValue(true);
            return;
        }

        {
            java.util.List<PendingImageState.PendingImage> pendAll = PendingImageState.getAll();
            if (!pendAll.isEmpty()) {
                ChatScreen self = (ChatScreen)(Object)this;
                Minecraft mcLocal = Minecraft.getInstance();
                int inputBarTopL = self.height - 12;
                int rowBottomL = inputBarTopL - 6 - cr$getPendingPreviewAreaHeight(mcLocal);
                int maxDispHL = 0;
                for (PendingImageState.PendingImage p : pendAll) maxDispHL = Math.max(maxDispHL, p.getHeight());
                int rowTopL = rowBottomL - maxDispHL;
                int rowLeftL = 4;
                int rowRightL = self.width - 4;
                int totalRowWL = cr$pendingRowTotalWidth(pendAll);
                if (my >= rowTopL - 4 && my <= rowBottomL + 4 && mx >= rowLeftL && mx <= rowRightL
                        && totalRowWL > (rowRightL - rowLeftL)) {
                    int maxScrollXL = Math.max(0, totalRowWL - (rowRightL - rowLeftL));
                    int step = 30;
                    int delta = scrollY > 0 ? -step : (scrollY < 0 ? step : 0);
                    cr$pendingStripScrollX = Mth.clamp(cr$pendingStripScrollX + delta, 0, maxScrollXL);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        boolean rowMode = ChatRemasteredConfig.getGroupPhotosRowMode();
        for (ChatRemasteredStore.ImageMessage msg : ChatRemasteredStore.getMessageList()) {
            if (msg.getDismissed() || !msg.isGroup()) continue;
            if (rowMode) {
                if (msg.getRowCardBounds().isEmpty()) continue;

                int areaY0 = Integer.MAX_VALUE, areaY1 = Integer.MIN_VALUE;
                for (int[] b : msg.getRowCardBounds().values()) {
                    areaY0 = Math.min(areaY0, b[1]);
                    areaY1 = Math.max(areaY1, b[3]);
                }
                Minecraft mcLocal2 = Minecraft.getInstance();
                double chatLineSpacing2 = mcLocal2.options.chatLineSpacing().get();
                float scale2 = (float) mcLocal2.options.chatScale().get().doubleValue();
                if (scale2 < 0.01f) scale2 = 1f;
                int chatWidthPx2 = Mth.floor(mcLocal2.options.chatWidth().get() * 280.0 + 40.0);
                int areaX0 = (int) (4 * scale2);
                int areaX1 = (int) ((4 + chatWidthPx2) * scale2);
                if (mx >= areaX0 && mx < areaX1 && my >= areaY0 && my < areaY1) {
                    int step = 30;
                    int delta = scrollY > 0 ? -step : (scrollY < 0 ? step : 0);
                    msg.setRowScrollX(msg.getRowScrollX() + delta);
                    cir.setReturnValue(true);
                    return;
                }
            } else {
                if (!msg.hasScreenBounds()) continue;
                if (mx >= msg.getBoundsX0() && mx < msg.getBoundsX1()
                        && my >= msg.getBoundsY0() && my < msg.getBoundsY1()) {

                    int delta = scrollY > 0 ? 1 : (scrollY < 0 ? -1 : 0);
                    if (delta != 0) {
                        msg.scrollStrip(delta);
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chatremastered$handleMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0 && event.button() != 1) return;
        boolean isRightClick = event.button() == 1;
        double mx = event.x();
        double my = event.y();
        ChatScreen self = (ChatScreen)(Object)this;
        Minecraft mc = Minecraft.getInstance();

        if (cr$screenshotsPanel.isOpen()
                && cr$screenshotsPanel.mouseClicked(self.width, self.height, mx, my, event.button())) {
            cir.setReturnValue(true);
            return;
        }

        if (!isRightClick && cr$tagCommandSuggestions != null && cr$tagCommandSuggestions.mouseClicked(event)) {
            cir.setReturnValue(true);
            return;
        }

        if (cr$menuOpen) {
            int n = cr$menuLabels.length;
            int menuH = MENU_PAD * 2 + n * MENU_ITEM_H + (n - 1) * 2;
            boolean inside = mx >= cr$menuX && mx <= cr$menuX + cr$menuComputedW
                    && my >= cr$menuY && my <= cr$menuY + menuH;
            if (inside && event.button() == 0) {
                for (int i = 0; i < n; i++) {
                    int iy = cr$menuY + MENU_PAD + i * (MENU_ITEM_H + 2);
                    if (mx >= cr$menuX + 1 && mx < cr$menuX + cr$menuComputedW - 1 && my >= iy && my < iy + MENU_ITEM_H) {
                        cr$menuActions[i].run();
                        break;
                    }
                }
            }
            cr$closeMenu();
            cir.setReturnValue(true);
            return;
        }

        int inputBarTop = self.height - 12;
        int camBtnX = self.width - CAM_BTN_W - 2;
        int camBtnY = inputBarTop - CAM_BTN_H - 4;
        int cfgBtnX = self.width - CFG_BTN_W - 2;
        int cfgBtnY = camBtnY - CFG_BTN_H - 2;
        int scrBtnX = camBtnX - SCR_BTN_W - 2;
        int scrBtnY = camBtnY;

        if (!isRightClick && cr$replyAddedTime >= 0) {
            int[] replyBarBounds = cr$getReplyBarBounds(mc);
            if (replyBarBounds != null) {
                int rbX = replyBarBounds[0], rbY = replyBarBounds[1], rbW = replyBarBounds[2], rbH = replyBarBounds[3];
                if (mx >= rbX && mx < rbX + rbW && my >= rbY && my < rbY + rbH) {
                    int closeX = rbX + rbW - 12;
                    if (mx >= closeX - 2) {

                        cr$clearReply();
                    } else {

                        cr$scrollToMessage(mc, cr$replyAddedTime);
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (mx >= cfgBtnX && mx < cfgBtnX + CFG_BTN_W && my >= cfgBtnY && my < cfgBtnY + CFG_BTN_H) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            mc.setScreen(ChatRemasteredConfigScreen.build(self));
            cir.setReturnValue(true);
            return;
        }

        if (mx >= scrBtnX && mx < scrBtnX + SCR_BTN_W && my >= scrBtnY && my < scrBtnY + SCR_BTN_H) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            cr$screenshotsPanel.toggle();
            cir.setReturnValue(true);
            return;
        }

        if (mx >= camBtnX && mx < camBtnX + CAM_BTN_W && my >= camBtnY && my < camBtnY + CAM_BTN_H) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            if (!canSendPhoto()) { ChatRemasteredClient.canSendPhoto(mc); cir.setReturnValue(true); return; }
            openFileDialog();
            cir.setReturnValue(true);
            return;
        }

        List<PendingImageState.PendingImage> pendingAllRaw3 = PendingImageState.getAll();
        List<PendingImageState.PendingImage> pendingAll = new java.util.ArrayList<>();
        for (PendingImageState.PendingImage p : pendingAllRaw3) {
            if (!cr$pendingAnim.isFlying(p.getUid())) {
                pendingAll.add(p);
            }
        }
        if (!pendingAll.isEmpty()) {
            int rowBottom = inputBarTop - 6 - cr$getPendingPreviewAreaHeight(mc);
            int cardX = 4 - cr$pendingStripScrollX;
            boolean removalInProgress = !cr$pendingAnim.getActiveRemovals(self.height).isEmpty();
            for (int i = 0; i < pendingAll.size(); i++) {
                PendingImageState.PendingImage card = pendingAll.get(i);
                int dispW = card.getWidth();
                int dispH = card.getHeight();
                int smoothedCardX = cr$pendingAnim.peekSmoothX(card.getUid(), cardX);
                int cardTop = rowBottom - dispH;
                int closeSize = 14;
                int closeX = smoothedCardX + dispW - closeSize - 2;
                int closeY = cardTop + 2;
                if (mx >= closeX && mx < closeX + closeSize && my >= closeY && my < closeY + closeSize) {
                    if (removalInProgress) {
                        cir.setReturnValue(true);
                        return;
                    }
                    cr$pendingAnim.startRemoval(card.getUid(), smoothedCardX, cardTop, dispW, dispH,
                            card.getTextureId(), card.getTextureWidth(), card.getTextureHeight(), card.isLoaded(),
                            card.getSizeKnown(), card.getProgress());
                    cir.setReturnValue(true);
                    return;
                }
                cardX += dispW + PENDING_STRIP_GAP;
            }
        }

        if (!isRightClick) {
            ChatComponent chat = mc.gui.getChat();
            ChatComponentAccessor acc2 = (ChatComponentAccessor) chat;
            List<GuiMessage.Line> trimmed2 = acc2.getTrimmedMessages();
            int scrollPos2 = acc2.getChatScrollbarPos();
            float chatScale2 = (float) mc.options.chatScale().get().doubleValue();
            if (chatScale2 < 0.01f) chatScale2 = 1f;
            double lineSpacing2 = mc.options.chatLineSpacing().get();
            int entryH2 = (int)(9.0 * (lineSpacing2 + 1.0));
            int chatBottom2 = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / chatScale2);
            int lpp2 = chat.getLinesPerPage();
            int chatWidthPx2 = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
            int chatTopGui2 = self.height - 40 - Math.round(lpp2 * entryH2 * chatScale2);
            int chatBottomGui2 = self.height - 40;
            int guiBarRight2 = Math.round((chatWidthPx2 + 8) * chatScale2);

            int prevAddedTime2 = -1;
            for (int i = 0; i < Math.min(trimmed2.size() - scrollPos2, lpp2); i++) {
                int idx = i + scrollPos2;
                GuiMessage.Line line2 = trimmed2.get(idx);
                int addedTime2 = line2.addedTime();

                boolean isSpacerLine = false;
                for (GuiMessage gm : acc2.getAllMessages()) {
                    if (gm.addedTime() == addedTime2 && gm.content().getString().startsWith("\n")) {
                        isSpacerLine = true;
                        break;
                    }
                }
                if (!isSpacerLine) { prevAddedTime2 = addedTime2; continue; }

                int entryBottom2 = chatBottom2 - i * entryH2;
                int entryTop2 = entryBottom2 - entryH2;
                int guiBarTop2    = chatBottomGui2 - (int)((chatBottom2 - entryTop2)    * chatScale2);
                int guiBarBottom2 = chatBottomGui2 - (int)((chatBottom2 - entryBottom2) * chatScale2);

                if (guiBarTop2 < chatTopGui2 || guiBarBottom2 > chatBottomGui2) { prevAddedTime2 = addedTime2; continue; }
                if (mx >= 0 && mx < guiBarRight2 && my >= guiBarTop2 && my < guiBarBottom2) {

                    int replyTargetTime = -1;
                    for (ChatRemasteredStore.ReplyMessage rm : ChatRemasteredStore.getRepliesList()) {
                        if (rm.getAddedTime() == addedTime2) {
                            replyTargetTime = rm.getReplyToAddedTime();

                            if (replyTargetTime < 0) {
                                if (!rm.getReplyToImageId().isEmpty()) {

                                    for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
                                        if (imgMsg.getImageId().equals(rm.getReplyToImageId()) && imgMsg.getAddedTime() >= 0) {
                                            replyTargetTime = imgMsg.getAddedTime();
                                            rm.setReplyToAddedTime(replyTargetTime);
                                            break;
                                        }
                                    }
                                } else if (!rm.getReplyToText().isEmpty()) {

                                    String expectedText = "<" + rm.getReplyToSender() + "> " + rm.getReplyToText();
                                    for (GuiMessage gm : acc2.getAllMessages()) {
                                        String gmRaw = gm.content().getString();
                                        String gmStripped = gmRaw.startsWith("\n") ? gmRaw.substring(1) : gmRaw;
                                        if (gmStripped.equals(expectedText)) {
                                            replyTargetTime = gm.addedTime();
                                            rm.setReplyToAddedTime(replyTargetTime);
                                            break;
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                    if (replyTargetTime >= 0) {
                        cr$scrollToMessage(mc, replyTargetTime);
                    }
                    cir.setReturnValue(true);
                    return;
                }
                prevAddedTime2 = addedTime2;
            }
        }

        for (ChatRemasteredStore.ImageMessage msg : ChatRemasteredStore.getMessageList()) {
            if (msg.getDismissed() || msg.getRowCardBounds().isEmpty()) continue;
            for (var entry : msg.getRowCardBounds().entrySet()) {
                String otherId = entry.getKey();
                int[] b = entry.getValue();
                if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                    if (isRightClick) {
                        cr$openImageMenu(mc, self, (int) mx, (int) my, otherId, msg.getAddedTime());
                        cir.setReturnValue(true);
                        return;
                    }
                    if (ImageCache.isError(otherId)) { cir.setReturnValue(true); return; }
                    Identifier tex = ImageCache.getTexture(otherId);
                    dev.errnicraft.chatremastered.IntPair size = ImageCache.getSize(otherId);
                    dev.errnicraft.chatremastered.IntPair orig = ImageCache.getOrigSize(otherId);
                    if (tex != null && orig != null) {
                        dev.errnicraft.chatremastered.IntPair texSize = ImageCache.getTexSize(otherId);
                        java.io.File originalFile = ChatRemasteredStore.getOriginalFile(otherId);
                        int w = texSize != null ? texSize.getFirst() : orig.getFirst();
                        int h = texSize != null ? texSize.getSecond() : orig.getSecond();
                        ImageViewerScreen viewer = new ImageViewerScreen(tex, otherId, w, h, originalFile);
                        java.util.List<String> allIds = new java.util.ArrayList<>();
                        allIds.add(msg.getImageId());
                        allIds.addAll(msg.getGroupImageIds());
                        int idx = allIds.indexOf(otherId);
                        viewer.setGroupContext(allIds, Math.max(0, idx));
                        mc.setScreen(viewer);
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }

        List<ChatRemasteredStore.ImageMessage> imgs = ChatRemasteredStore.getMessageList();
        for (ChatRemasteredStore.ImageMessage msg : imgs) {
            if (msg.getDismissed() || !msg.hasScreenBounds()) continue;
            if (mx >= msg.getBoundsX0() && mx < msg.getBoundsX1()
                    && my >= msg.getBoundsY0() && my < msg.getBoundsY1()) {

                String activeImageId = msg.getActiveStripImageId();

                if (isRightClick) {
                    cr$openImageMenu(mc, self, (int) mx, (int) my, activeImageId, msg.getAddedTime());
                    cir.setReturnValue(true);
                    return;
                }

                if (ImageCache.isError(activeImageId)) { cir.setReturnValue(true); return; }

                Identifier tex = ImageCache.getTexture(activeImageId);
                dev.errnicraft.chatremastered.IntPair size = ImageCache.getSize(activeImageId);
                if (tex != null && size != null) {
                    dev.errnicraft.chatremastered.IntPair texSize = ImageCache.getTexSize(activeImageId);
                    java.io.File originalFile = ChatRemasteredStore.getOriginalFile(activeImageId);
                    int w = texSize != null ? texSize.getFirst() : size.getFirst();
                    int h = texSize != null ? texSize.getSecond() : size.getSecond();
                    ImageViewerScreen viewer = new ImageViewerScreen(tex, activeImageId, w, h, originalFile);
                    if (msg.isGroup()) {
                        java.util.List<String> allIds = new java.util.ArrayList<>();
                        allIds.add(msg.getImageId());
                        allIds.addAll(msg.getGroupImageIds());
                        viewer.setGroupContext(allIds, msg.getStripScrollOffset());
                    }
                    mc.setScreen(viewer);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (isRightClick) {
            String msgText = cr$getMessageTextAt(mc, (int) mx, (int) my);
            if (msgText != null) {
                int msgAddedTime = cr$getAddedTimeAt(mc, (int) mx, (int) my);
                String linkedImageId = cr$getImageIdForMessageAt(mc, (int) mx, (int) my);
                ChatRemasteredStore.EntityMessage linkedEntityMsg = null;
                for (ChatRemasteredStore.EntityMessage em : ChatRemasteredStore.getEntityMessageList()) {
                    if (!em.getDismissed() && em.getAddedTime() == msgAddedTime) {
                        linkedEntityMsg = em;
                        break;
                    }
                }
                if (linkedImageId != null) {
                    cr$openImageMenu(mc, self, (int) mx, (int) my, linkedImageId, msgAddedTime);
                } else {
                    String replyLabel = ChatRemasteredConfig.tr("chat-remastered.ctx_reply");
                    String copyLabel = ChatRemasteredConfig.tr("chat-remastered.ctx_copy_message");
                    final String textToCopy = msgText;
                    final int replyTime = msgAddedTime;
                    java.util.List<String> menuLabels = new java.util.ArrayList<>();
                    java.util.List<String> menuIcons = new java.util.ArrayList<>();
                    java.util.List<Runnable> menuActions = new java.util.ArrayList<>();
                    menuLabels.add(replyLabel); menuIcons.add("↩");
                    menuActions.add(() -> cr$startReply(mc, replyTime, null, textToCopy, null));
                    menuLabels.add(copyLabel); menuIcons.add("📋");
                    menuActions.add(() -> mc.keyboardHandler.setClipboard(textToCopy));
                    if (linkedEntityMsg != null) {
                        String copyCodeLabel = ChatRemasteredConfig.tr("chat-remastered.ctx_copy_code");
                        final String codeToCopy = linkedEntityMsg.buildFullCode();
                        menuLabels.add(copyCodeLabel); menuIcons.add("⧉");
                        menuActions.add(() -> mc.keyboardHandler.setClipboard(codeToCopy));
                    }
                    cr$openMenu((int) mx, (int) my, self.width, self.height,
                            menuLabels.toArray(new String[0]),
                            menuIcons.toArray(new String[0]),
                            menuActions.toArray(new Runnable[0]));
                }
                cir.setReturnValue(true);
            }
        }
    }

    private static String cr$formattedCharSequenceToString(net.minecraft.util.FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    private GuiMessage cr$lastResolvedMessage = null;

    private String cr$getMessageTextAt(Minecraft mc, int mouseX, int mouseY) {
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

                String lineStripped = stripHeadPlaceholders(cr$formattedCharSequenceToString(line.content()));
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
                        cr$lastResolvedMessage = msg;
                        return candidateStripped;
                    }
                }
                if (fallback != null) {
                    cr$lastResolvedMessage = fallback;
                    return fallbackText;
                }
            }
        }
        cr$lastResolvedMessage = null;
        return null;
    }

    private void cr$updateInputY(ChatScreen self) {
        if (input == null) return;

        input.setY(self.height - 12);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void chatremastered$afterInit(CallbackInfo ci) {
        cr$updateInputY((ChatScreen)(Object)this);
        if (input != null) {
            input.addFormatter((fragment, offset) ->
                    dev.errnicraft.chatremastered.client.EntityTagHighlighter.highlight(
                            input.getValue(), fragment, offset));
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void chatremastered$renderHead(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        cr$updateInputY((ChatScreen)(Object)this);
        cr$updateTagSuggestions((ChatScreen)(Object)this);

        boolean replyActive = cr$replyAddedTime >= 0;
        boolean photoActive = PendingImageState.getPending() != null;
        float targetReply = replyActive ? 1f : 0f;
        float targetPhoto = photoActive ? 1f : 0f;
        float targetXShift = photoActive ? 1f : 0f;
        cr$replyBarAnim += (targetReply - cr$replyBarAnim) * ANIM_SPEED;
        cr$photoPreviewAnim += (targetPhoto - cr$photoPreviewAnim) * ANIM_SPEED;
        cr$replyBarXAnim += (targetXShift - cr$replyBarXAnim) * ANIM_SPEED;
        if (cr$replyBarAnim < 0.001f) cr$replyBarAnim = 0f;
        if (cr$photoPreviewAnim < 0.001f) cr$photoPreviewAnim = 0f;
    }

    @Inject(method = "onClose", at = @At("TAIL"))
    private void chatremastered$onClose(CallbackInfo ci) {
        cr$closeMenu();
        cr$clearReply();
        if (cr$tagCommandSuggestions != null) {
            cr$tagCommandSuggestions.hide();
        }
        cr$tagSuggestionsForText = null;
        cr$screenshotsPanel.close();
        cr$screenshotsPanel.releaseAll();
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0L);
    }

    private void cr$openImageMenu(Minecraft mc, ChatScreen self, int ax, int ay, String imageId, int msgAddedTime) {
        String textOfMsg = cr$getMessageTextForImageId(mc, imageId);
        String replyLabel = ChatRemasteredConfig.tr("chat-remastered.ctx_reply");
        boolean isGifImage = dev.errnicraft.chatremastered.ImageCache.isGif(imageId);
        String saveLabel = ChatRemasteredConfig.tr(
                isGifImage ? "chat-remastered.ctx_save_as_gif" : "chat-remastered.ctx_save_as");
        String copyIdLabel = ChatRemasteredConfig.tr("chat-remastered.ctx_copy_id");
        String copyMsgLabel = ChatRemasteredConfig.tr("chat-remastered.ctx_copy_message");
        String deleteLabel = ChatRemasteredConfig.tr(isGifImage ? "chat-remastered.ctx_delete_gif" : "chat-remastered.ctx_delete_photo");
        String deleteOpLabel = ChatRemasteredConfig.tr(isGifImage ? "chat-remastered.ctx_delete_gif_op" : "chat-remastered.ctx_delete_photo_op");
        boolean hasText = textOfMsg != null && !textOfMsg.isBlank();
        final int replyTime = msgAddedTime;
        final String txt = textOfMsg;

        boolean isDeleted   = dev.errnicraft.chatremastered.ImageCache.isDeleted(imageId);
        boolean isError     = dev.errnicraft.chatremastered.ImageCache.isError(imageId);
        ImageCache.DownloadState dlState = dev.errnicraft.chatremastered.ImageCache.getDownloadState(imageId);
        boolean isLoading   = dlState == ImageCache.DownloadState.IN_PROGRESS;
        boolean isIdle      = dlState == ImageCache.DownloadState.IDLE;

        boolean unavailable = isDeleted || isError || isLoading || isIdle;

        boolean isOwnPhoto = ChatRemasteredStore.getOriginalFile(imageId) != null;
        boolean isOp = mc.player != null && mc.player.canUseGameMasterBlocks();

        Runnable deleteAction = () -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.errnicraft.chatremastered.network.packet.DeleteImagePacket(imageId)
            );
        };

        Runnable adminDeleteAction = () -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.errnicraft.chatremastered.network.packet.AdminDeleteImagePacket(imageId)
            );
        };

        Runnable copyIdAction = () -> {
            mc.keyboardHandler.setClipboard(imageId);
            mc.gui.getChat().addMessage(Component.literal(
                    "§8[Chat Remastered] §7" + ChatRemasteredConfig.tr("chat-remastered.id_copied", imageId, imageId)));
        };

        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<String> icons = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();

        labels.add(replyLabel); icons.add("↩");
        actions.add(() -> cr$startReply(mc, replyTime, imageId, hasText ? txt : null, imageId));
        colors.add(0);

        if (hasText) {
            labels.add(copyMsgLabel); icons.add("📋");
            actions.add(() -> mc.keyboardHandler.setClipboard(txt));
            colors.add(0);
        }

        if (!unavailable) {
            labels.add(saveLabel); icons.add("💾");
            actions.add(() -> ChatRemasteredClient.saveImageAs(imageId));
            colors.add(0);
        }

        labels.add(copyIdLabel); icons.add("🔗");
        actions.add(copyIdAction);
        colors.add(0);

        if (isOwnPhoto && !isDeleted) {
            labels.add(deleteLabel); icons.add("🗑");
            actions.add(deleteAction);
            colors.add(0xFF4444);
        }

        if (isOp && !isOwnPhoto && !isDeleted) {
            labels.add(deleteOpLabel); icons.add("🛡");
            actions.add(adminDeleteAction);
            colors.add(0xFF4444);
        }

        int[] colorsArr = new int[colors.size()];
        for (int i = 0; i < colorsArr.length; i++) colorsArr[i] = colors.get(i);

        cr$openMenu(ax, ay, self.width, self.height,
                labels.toArray(new String[0]),
                icons.toArray(new String[0]),
                actions.toArray(new Runnable[0]),
                colorsArr);
    }

    private int cr$findLineIndexForImageId(Minecraft mc, String imageId) {
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

    private String cr$getMessageTextForImageId(Minecraft mc, String imageId) {
        for (ChatRemasteredStore.ImageMessage img : ChatRemasteredStore.getMessageList()) {
            if (img.getImageId().equals(imageId)) {
                String caption = img.getCaption();
                return (caption == null || caption.isBlank()) ? null : caption;
            }
        }
        return null;
    }

    private String cr$getImageIdForMessageAt(Minecraft mc, int mouseX, int mouseY) {
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

    private void openFileDialog() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().preserveCurrentChatScreen();
        mc.setScreen(null);

        Thread t = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            try {
                String path;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer filters = stack.mallocPointer(8);
                    filters.put(stack.UTF8("*.png")); filters.put(stack.UTF8("*.jpg"));
                    filters.put(stack.UTF8("*.jpeg")); filters.put(stack.UTF8("*.webp"));
                    filters.put(stack.UTF8("*.bmp")); filters.put(stack.UTF8("*.tiff"));
                    filters.put(stack.UTF8("*.tif")); filters.put(stack.UTF8("*.gif"));
                    filters.flip();
                    path = TinyFileDialogs.tinyfd_openFileDialog(
                            ChatRemasteredConfig.tr("chat-remastered.select_image"),
                            "", filters, "Image Files (*.png, *.jpg, *.jpeg, *.webp, *.bmp, *.tiff, *.gif)", true);
                }
                if (path != null) {
                    String[] paths = path.split("\\|");
                    java.util.List<File> validFiles = new java.util.ArrayList<>();
                    boolean anyTooLarge = false;
                    for (String p : paths) {
                        File file = new File(p);
                        if (!file.exists()) continue;
                        if (file.length() > 10L * 1024 * 1024) {
                            anyTooLarge = true;
                            continue;
                        }
                        validFiles.add(file);
                    }
                    boolean fAnyTooLarge = anyTooLarge;
                    mc.execute(() -> {
                        for (File file : validFiles) {
                            ChatRemasteredClient.stageImage(file);
                        }
                        if (fAnyTooLarge) {
                            mc.gui.getChat().addMessage(Component.literal(
                                    "§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large")));
                        }
                        ChatScreen restored = mc.gui.getChat().restoreChatScreen();
                        mc.setScreen(restored != null ? restored : new ChatScreen("", false));
                    });
                } else {
                    mc.execute(() -> restoreChat(mc));
                }
            } catch (Exception e) {
                e.printStackTrace();
                mc.execute(() -> restoreChat(mc));
            }
        });
        t.setDaemon(true);
        t.setName("Chat Remastered-FileDialog");
        t.start();
    }

    private void restoreChat(Minecraft mc) {
        ChatScreen restored = mc.gui.getChat().restoreChatScreen();
        mc.setScreen(restored != null ? restored : new ChatScreen("", false));
    }

    private void cr$startReply(Minecraft mc, int addedTime, String imageIdForLookup,
                               String text, String replyImageId) {
        cr$replyAddedTime = addedTime;
        cr$replyImageId = replyImageId;
        String sender = null;
        net.minecraft.network.chat.Component senderComp = null;
        boolean isPlayerMessage = false;

        dev.errnicraft.chatremastered.ChatRemasteredStore.ReplyMessage storedReply =
                dev.errnicraft.chatremastered.ChatRemasteredStore.getReplyForAddedTime(addedTime);
        if (storedReply != null) {
            sender = storedReply.getSenderName();
            senderComp = storedReply.getSenderComponent();
            isPlayerMessage = sender != null && !sender.isBlank();
        }

        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage> resolvedSource;
        if (sender != null) {
            resolvedSource = java.util.Collections.emptyList();
        } else if (cr$lastResolvedMessage != null && cr$lastResolvedMessage.addedTime() == addedTime) {
            resolvedSource = java.util.Collections.singletonList(cr$lastResolvedMessage);
        } else {
            resolvedSource = acc.getAllMessages();
        }
        for (GuiMessage msg : resolvedSource) {
            if (msg.addedTime() == addedTime) {
                net.minecraft.network.chat.Component content = msg.content();

                if (!content.getSiblings().isEmpty()) {
                    var sibs = content.getSiblings();
                    if (sibs.get(0).getString().equals("\n") && sibs.size() >= 2) {
                        if (sibs.size() == 2) {
                            content = sibs.get(1);
                        } else {

                            net.minecraft.network.chat.MutableComponent rebuilt =
                                    net.minecraft.network.chat.Component.empty();
                            for (int si = 1; si < sibs.size(); si++) rebuilt.append(sibs.get(si));
                            content = rebuilt;
                        }
                    }
                }

                if (content.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                    Object[] args = tc.getArgs();
                    if (args.length >= 1 && args[0] instanceof net.minecraft.network.chat.Component nickComp) {
                        sender = stripHeadPlaceholders(nickComp.getString()).trim();
                        senderComp = nickComp;
                        isPlayerMessage = true;
                    }
                }

                if (sender == null) {
                    String raw = content.getString();
                    if (raw.startsWith("<")) {
                        int end = raw.indexOf('>');
                        if (end > 0) {
                            sender = raw.substring(1, end);

                            for (net.minecraft.network.chat.Component sib : content.getSiblings()) {
                                if (sib.getString().equals(sender)) { senderComp = sib; break; }
                            }
                            if (senderComp == null) senderComp = net.minecraft.network.chat.Component.literal(sender);
                            isPlayerMessage = true;
                        }
                    }
                }
                break;
            }
        }

        if (replyImageId != null) {
            for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
                if (imgMsg.getImageId().equals(replyImageId)) {
                    net.minecraft.network.chat.Component stored = imgMsg.getSenderComponent();
                    if (stored != null && !stored.getString().isBlank()) {
                        senderComp = stored;
                    }

                    if (sender == null || sender.isBlank()) {
                        sender = imgMsg.getSender();
                        isPlayerMessage = sender != null && !sender.isBlank();
                    }
                    break;
                }
            }
        }

        cr$replySenderName = isPlayerMessage ? stripHeadPlaceholders(sender) : null;
        cr$replySenderComp = isPlayerMessage ? senderComp : null;

        String cleanText = (text != null && !text.isBlank()) ? stripHeadPlaceholders(text) : null;
        if (cleanText != null && isPlayerMessage && sender != null) {

            String nickPrefix1 = sender + "> ";
            String nickPrefix2 = "<" + sender + "> ";
            if (cleanText.startsWith(nickPrefix2)) cleanText = cleanText.substring(nickPrefix2.length()).trim();
            else if (cleanText.startsWith(nickPrefix1)) cleanText = cleanText.substring(nickPrefix1.length()).trim();
        }

        if (cleanText != null && cleanText.startsWith("<")) {
            int closeAngle = cleanText.indexOf("> ");
            if (closeAngle > 1) {
                cleanText = cleanText.substring(closeAngle + 2).trim();
            }
        }
        cr$replyText = (cleanText != null && !cleanText.isBlank()) ? cleanText : null;

        if (input != null) input.setFocused(true);
    }

    private void cr$clearReply() {
        cr$replyAddedTime = -1;
        cr$replySenderName = null;
        cr$replySenderComp = null;
        cr$replyText = null;
        cr$replyImageId = null;
    }

    private int cr$replyBarHeight() {
        return cr$replyAddedTime >= 0 ? 14 : 0;
    }

    private int cr$getPendingPreviewAreaHeight(Minecraft mc) {
        if (cr$replyBarAnim < 0.01f) return 0;
        int barH = 13;
        float ease = 1f - (1f - cr$replyBarAnim) * (1f - cr$replyBarAnim);
        int animOffsetY = Math.round((barH + 4) * (1f - ease));
        int visibleH = (barH + 2) - animOffsetY;
        return Math.max(0, visibleH);
    }

    private int[] cr$getReplyBarBounds(Minecraft mc) {
        ChatScreen self = (ChatScreen)(Object)this;
        float scale = (float) mc.options.chatScale().get().doubleValue();
        if (scale < 0.01f) scale = 1f;
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
        int barH = 13;

        int barLeft = 2;

        int barRight = Math.round((4 + chatWidthPx) * scale);
        int fullW    = barRight - barLeft;

        int baseBarY = self.height - 12 - barH - 2;

        float ease = 1f - (1f - cr$replyBarAnim) * (1f - cr$replyBarAnim);
        int animOffsetY = Math.round((barH + 4) * (1f - ease));
        int barY = baseBarY + animOffsetY;
        int barX = barLeft;
        int adjustedW = fullW;
        if (adjustedW < 20) return null;
        return new int[]{barX, barY, adjustedW, barH};
    }

    private void cr$renderReplyOverMessage(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        if (cr$replyBarAnim < 0.01f) return;
        int[] bounds = cr$getReplyBarBounds(mc);
        if (bounds == null) return;
        int barX = bounds[0], barY = bounds[1], barW = bounds[2], barH = bounds[3];

        float ease = 1f - (1f - cr$replyBarAnim) * (1f - cr$replyBarAnim);
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
        graphics.drawString(mc.font, "✕", closeX, textY, closeColor, false);

        int contentX = barX + 5;
        int maxW = closeX - contentX - 4;

        String full;
        if (cr$replyImageId != null) {

            Identifier tex = ImageCache.getTexture(cr$replyImageId);
            if (tex != null) {
                String arrowStr = "↩ ";
                graphics.drawString(mc.font, arrowStr, contentX, textY, (textAlpha << 24) | 0xBBBBBB, false);
                contentX += mc.font.width(arrowStr);

                int photoW = 10, photoH = 10;
                dev.errnicraft.chatremastered.IntPair ts = ImageCache.getTexSize(cr$replyImageId);
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

            String suffix = cr$replyText != null ? cr$replyText : "";
            if (cr$replySenderName != null) {
                net.minecraft.network.chat.MutableComponent label = buildReplyInputLabel(cr$replySenderName, suffix, maxW, mc);
                graphics.drawString(mc.font, label, contentX, textY, (textAlpha << 24) | 0xAAAAAA, false);
                return;
            } else {
                full = "§7" + suffix;
            }
        } else {

            String suffix = cr$replyText != null ? cr$replyText : "";
            if (cr$replySenderName != null) {
                net.minecraft.network.chat.MutableComponent label2 = net.minecraft.network.chat.Component.empty();
                label2.append(net.minecraft.network.chat.Component.literal("↩ ")
                        .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xBBBBBB)));
                label2.append(net.minecraft.network.chat.Component.literal(cr$replySenderName)
                        .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x3399EE)));
                if (!suffix.isEmpty()) label2.append(net.minecraft.network.chat.Component.literal(": " + suffix)
                        .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAAAAA)));
                label2 = truncateInputLabel(label2, maxW, mc);
                graphics.drawString(mc.font, label2, contentX, textY, (textAlpha << 24) | 0xAAAAAA, false);
                return;
            } else {
                full = "↩ §7" + suffix;
            }
        }
        graphics.drawString(mc.font, cr$truncateFormatted(mc, full, maxW),
                contentX, textY, (textAlpha << 24) | 0xBBBBBB, false);
    }

    private String cr$truncateFormatted(Minecraft mc, String text, int maxWidth) {

        net.minecraft.network.chat.Component comp = net.minecraft.network.chat.Component.literal(text);
        if (mc.font.width(comp) <= maxWidth) return text;

        String plain = comp.getString();
        String ellipsis = "…";
        int ellW = mc.font.width(ellipsis);

        while (plain.length() > 0 && mc.font.width(plain) + ellW > maxWidth)
            plain = plain.substring(0, plain.length() - 1);
        return plain + ellipsis;
    }

    private String cr$truncate(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellW = mc.font.width(ellipsis);
        while (!text.isEmpty() && mc.font.width(text) + ellW > maxWidth)
            text = text.substring(0, text.length() - 1);
        return text + ellipsis;
    }

    private int cr$getAddedTimeAt(Minecraft mc, int mouseX, int mouseY) {
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

    private void cr$scrollToMessage(Minecraft mc, int targetAddedTime) {
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

        cr$highlightAddedTime = targetAddedTime;
        cr$highlightStartMs = System.currentTimeMillis();
    }

    private void cr$renderHighlight(GuiGraphics graphics, Minecraft mc) {
        if (cr$highlightAddedTime < 0) return;
        long elapsed = System.currentTimeMillis() - cr$highlightStartMs;
        if (elapsed >= HIGHLIGHT_DURATION_MS) { cr$highlightAddedTime = -1; return; }
        float progress = (float) elapsed / HIGHLIGHT_DURATION_MS;

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
        if (alpha <= 0) { cr$highlightAddedTime = -1; return; }
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
            if (line.addedTime() != cr$highlightAddedTime) continue;

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

    private static String stripHeadPlaceholders(String text) {
        if (text == null) return null;
        return text.replaceAll("\\[[^\\]]*\\s*head\\]", "").trim();
    }

    private static net.minecraft.network.chat.MutableComponent stripObjectContentsComponent(net.minecraft.network.chat.Component component) {
        net.minecraft.network.chat.ComponentContents contents = component.getContents();
        net.minecraft.network.chat.MutableComponent copy;
        if (contents instanceof net.minecraft.network.chat.contents.PlainTextContents plain) {
            copy = net.minecraft.network.chat.Component.literal(plain.text());
        } else if (contents instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            copy = net.minecraft.network.chat.Component.translatable(tc.getKey(), tc.getArgs());
        } else if (contents instanceof net.minecraft.network.chat.contents.ObjectContents) {
            copy = net.minecraft.network.chat.Component.empty();
        } else {
            copy = net.minecraft.network.chat.Component.empty();
        }
        copy.setStyle(component.getStyle());
        for (net.minecraft.network.chat.Component sib : component.getSiblings()) {
            copy.append(stripObjectContentsComponent(sib));
        }
        return copy;
    }

    private static net.minecraft.network.chat.MutableComponent buildReplyInputLabel(
            String sender, String suffix, int maxW, net.minecraft.client.Minecraft mc) {
        net.minecraft.network.chat.MutableComponent label = net.minecraft.network.chat.Component.empty();
        label.append(net.minecraft.network.chat.Component.literal(sender)
                .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x3399EE)));
        if (!suffix.isEmpty()) label.append(net.minecraft.network.chat.Component.literal(": " + suffix)
                .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAAAAA)));
        return truncateInputLabel(label, maxW, mc);
    }

    private static net.minecraft.network.chat.MutableComponent truncateInputLabel(
            net.minecraft.network.chat.MutableComponent comp, int maxW, net.minecraft.client.Minecraft mc) {
        if (mc.font.width(comp) <= maxW) return comp;
        String plain = comp.getString();
        String ellipsis = "…";
        int ellW = mc.font.width(ellipsis);
        while (!plain.isEmpty() && mc.font.width(plain) + ellW > maxW)
            plain = plain.substring(0, plain.length() - 1);
        net.minecraft.network.chat.MutableComponent result = net.minecraft.network.chat.Component.empty();
        int remaining = plain.length();
        for (net.minecraft.network.chat.Component sib : comp.getSiblings()) {
            if (remaining <= 0) break;
            String sibText = sib.getString();
            int take = Math.min(sibText.length(), remaining);
            result.append(net.minecraft.network.chat.Component.literal(sibText.substring(0, take)).setStyle(sib.getStyle()));
            remaining -= take;
        }
        result.append(net.minecraft.network.chat.Component.literal(ellipsis)
                .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAAAAA)));
        return result;
    }

}