package dev.errnicraft.chatremastered.mixin;

import dev.errnicraft.chatremastered.ChatRemasteredClient;
import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ChatRemasteredConfigScreen;
import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.ImageCache;
import dev.errnicraft.chatremastered.ImageViewerScreen;
import dev.errnicraft.chatremastered.PendingCardAnimator;
import dev.errnicraft.chatremastered.PendingImageState;
import dev.errnicraft.chatremastered.client.chatscreen.ChatContextMenuRenderer;
import dev.errnicraft.chatremastered.client.chatscreen.ChatLineLocator;
import dev.errnicraft.chatremastered.client.chatscreen.PendingCardRenderer;
import dev.errnicraft.chatremastered.client.chatscreen.ReplyBarRenderer;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    private static final ResourceLocation PLACEHOLDER_TEX =
            ResourceLocation.fromNamespaceAndPath("chat-remastered", "textures/placeholder.png");

    private static final ResourceLocation FOLDER_TEX =
            ResourceLocation.fromNamespaceAndPath("chat-remastered", "textures/gui/folder.png");
    private static final ResourceLocation FOLDER_ACTIVE_TEX =
            ResourceLocation.fromNamespaceAndPath("chat-remastered", "textures/gui/folder_active.png");
    private static final ResourceLocation FOLDER_BLOCK_TEX =
            ResourceLocation.fromNamespaceAndPath("chat-remastered", "textures/gui/folder_block.png");
    private static final ResourceLocation SETTINGS_TEX =
            ResourceLocation.fromNamespaceAndPath("chat-remastered", "textures/gui/settings.png");
    private static final ResourceLocation SETTINGS_ACTIVE_TEX =
            ResourceLocation.fromNamespaceAndPath("chat-remastered", "textures/gui/settings_active.png");
    private static final ResourceLocation SCREENSHOTS_TEX =
            ResourceLocation.fromNamespaceAndPath("chat-remastered", "textures/gui/screenshots.png");
    private static final ResourceLocation SCREENSHOTS_ACTIVE_TEX =
            ResourceLocation.fromNamespaceAndPath("chat-remastered", "textures/gui/screenshots_active.png");

    private boolean cr$menuOpen = false;
    private String cr$preservedChatInput = "";
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

    private final ChatLineLocator.HighlightState cr$highlight = new ChatLineLocator.HighlightState();
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
            target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V"))
    private void chatremastered$renderHighlight(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (cr$hoveredMsgLine >= 0) {
            ChatLineLocator.drawLineHighlight(graphics, mc, cr$hoveredMsgLine);
        }

        ChatLineLocator.renderHighlight(graphics, mc, cr$highlight, (int) HIGHLIGHT_DURATION_MS);
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

        ResourceLocation camTex = !canSend ? FOLDER_BLOCK_TEX : (hoverCam ? FOLDER_ACTIVE_TEX : FOLDER_TEX);
        graphics.blit(RenderPipelines.GUI_TEXTURED, camTex, camBtnX, camBtnY, 0f, 0f, CAM_BTN_W, CAM_BTN_H, CAM_BTN_W, CAM_BTN_H);

        int cfgBtnX = self.width - CFG_BTN_W - 2;
        int cfgBtnY = camBtnY - CFG_BTN_H - 2;
        boolean hoverCfg = mouseX >= cfgBtnX && mouseX < cfgBtnX + CFG_BTN_W
                && mouseY >= cfgBtnY && mouseY < cfgBtnY + CFG_BTN_H;

        ResourceLocation cfgTex = hoverCfg ? SETTINGS_ACTIVE_TEX : SETTINGS_TEX;
        graphics.blit(RenderPipelines.GUI_TEXTURED, cfgTex, cfgBtnX, cfgBtnY, 0f, 0f, CFG_BTN_W, CFG_BTN_H, CFG_BTN_W, CFG_BTN_H);

        int scrBtnX = camBtnX - SCR_BTN_W - 2;
        int scrBtnY = camBtnY;
        boolean hoverScr = mouseX >= scrBtnX && mouseX < scrBtnX + SCR_BTN_W
                && mouseY >= scrBtnY && mouseY < scrBtnY + SCR_BTN_H;

        ResourceLocation scrTex = hoverScr ? SCREENSHOTS_ACTIVE_TEX : SCREENSHOTS_TEX;
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

            ReplyBarRenderer.renderReplyOverMessage(graphics, mc, self, mouseX, mouseY, cr$replyBarAnim, cr$replyImageId, cr$replyText, cr$replySenderName);
            chatremastered$updateCursorAndHover(graphics, mc, mouseX, mouseY);
            chatremastered$renderMenu(graphics, mc, mouseX, mouseY);
            cr$screenshotsPanel.render(graphics, self.width, self.height, mouseX, mouseY);
            return;
        }

        int rowBottom = inputBarTop - 6 - ReplyBarRenderer.getPendingPreviewAreaHeight(cr$replyBarAnim);
        int rowLeft = 4;
        int rowRight = self.width - 4;
        int totalRowW = PendingCardRenderer.pendingRowTotalWidth(pendingAll);
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
            int cardW = PendingCardRenderer.renderPendingCard(graphics, mc, mouseX, mouseY, card, smoothedX, rowBottom, spawn);
            cardX += cardW + PENDING_STRIP_GAP;
        }
        if (needsScissorBase) {
            graphics.disableScissor();
        }

        for (PendingCardAnimator.RemoveState r : activeRemovals) {
            PendingCardRenderer.renderRemovingCard(graphics, mc, r, self.height);
        }

        ReplyBarRenderer.renderReplyOverMessage(graphics, mc, self, mouseX, mouseY, cr$replyBarAnim, cr$replyImageId, cr$replyText, cr$replySenderName);

        chatremastered$updateCursorAndHover(graphics, mc, mouseX, mouseY);
        chatremastered$renderMenu(graphics, mc, mouseX, mouseY);
        if (cr$tagCommandSuggestions != null) {
            cr$tagCommandSuggestions.render(graphics, mouseX, mouseY);
        }
        cr$screenshotsPanel.render(graphics, self.width, self.height, mouseX, mouseY);
    }


    private static PendingImageState.PendingImage pending() {
        return PendingImageState.getPending();
    }

    private void chatremastered$updateCursorAndHover(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        ChatScreen self2 = (ChatScreen)(Object)this;
        List<PendingImageState.PendingImage> pendAll = PendingImageState.getAll();
        boolean pendingRowNonEmpty = !pendAll.isEmpty();
        int rowLeft = 4, rowRight = self2.width - 4, rowTop = 0, rowBottom = 0;
        if (pendingRowNonEmpty) {
            int inputBarTopH = self2.height - 12;
            rowBottom = inputBarTopH - 6 - ReplyBarRenderer.getPendingPreviewAreaHeight(cr$replyBarAnim);
            int maxDispH = 0;
            for (PendingImageState.PendingImage p : pendAll) {
                maxDispH = Math.max(maxDispH, p.getHeight());
            }
            rowTop = rowBottom - maxDispH;
        }
        cr$hoveredMsgLine = ChatLineLocator.updateCursorAndHover(graphics, mc, self2, mouseX, mouseY,
                cr$menuOpen, cr$hoveredMsgLine, rowLeft, rowRight, rowTop, rowBottom, pendingRowNonEmpty);
    }

    private void chatremastered$renderMenu(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        if (!cr$menuOpen) return;
        ChatContextMenuRenderer.render(graphics, mc, mouseX, mouseY,
                cr$menuX, cr$menuY, cr$menuComputedW, cr$menuOpenTime,
                cr$menuLabels, cr$menuIcons, cr$menuColors, cr$menuItemAnim);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void chatremastered$handleKeys(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (cr$tagCommandSuggestions != null && cr$tagCommandSuggestions.isVisible()
                && cr$tagCommandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && cr$menuOpen) {
            cr$closeMenu();
            cir.setReturnValue(true);
            return;
        }

        if (((Screen) (Object) this).isPaste(keyCode)) {
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

        int key = keyCode;
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
                net.minecraft.resources.ResourceLocation handItemId =
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
                                    lookPlayer.getGameProfile().getName(), "rotate", parsedLookEntity.caption())
                    );
                } else {
                    net.minecraft.resources.ResourceLocation lookEntityId =
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
                int rowBottomL = inputBarTopL - 6 - ReplyBarRenderer.getPendingPreviewAreaHeight(cr$replyBarAnim);
                int maxDispHL = 0;
                for (PendingImageState.PendingImage p : pendAll) maxDispHL = Math.max(maxDispHL, p.getHeight());
                int rowTopL = rowBottomL - maxDispHL;
                int rowLeftL = 4;
                int rowRightL = self.width - 4;
                int totalRowWL = PendingCardRenderer.pendingRowTotalWidth(pendAll);
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
    private void chatremastered$handleMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 && button != 1) return;
        boolean isRightClick = button == 1;
        double mx = mouseX;
        double my = mouseY;
        ChatScreen self = (ChatScreen)(Object)this;
        Minecraft mc = Minecraft.getInstance();

        if (cr$screenshotsPanel.isOpen()
                && cr$screenshotsPanel.mouseClicked(self.width, self.height, mx, my, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (!isRightClick && cr$tagCommandSuggestions != null && cr$tagCommandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (cr$menuOpen) {
            int n = cr$menuLabels.length;
            int menuH = MENU_PAD * 2 + n * MENU_ITEM_H + (n - 1) * 2;
            boolean inside = mx >= cr$menuX && mx <= cr$menuX + cr$menuComputedW
                    && my >= cr$menuY && my <= cr$menuY + menuH;
            if (inside && button == 0) {
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
            int[] replyBarBounds = ReplyBarRenderer.getReplyBarBounds(mc, self, cr$replyBarAnim);
            if (replyBarBounds != null) {
                int rbX = replyBarBounds[0], rbY = replyBarBounds[1], rbW = replyBarBounds[2], rbH = replyBarBounds[3];
                if (mx >= rbX && mx < rbX + rbW && my >= rbY && my < rbY + rbH) {
                    int closeX = rbX + rbW - 12;
                    if (mx >= closeX - 2) {

                        cr$clearReply();
                    } else {

                        ChatLineLocator.scrollToMessage(mc, cr$replyAddedTime, cr$highlight);
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
            int rowBottom = inputBarTop - 6 - ReplyBarRenderer.getPendingPreviewAreaHeight(cr$replyBarAnim);
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
                        ChatLineLocator.scrollToMessage(mc, replyTargetTime, cr$highlight);
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
                    ResourceLocation tex = ImageCache.getTexture(otherId);
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

                ResourceLocation tex = ImageCache.getTexture(activeImageId);
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
            GuiMessage[] resolvedOut = new GuiMessage[1];
            String msgText = ChatLineLocator.getMessageTextAt(mc, (int) mx, (int) my, resolvedOut);
            cr$lastResolvedMessage = resolvedOut[0];
            if (msgText != null) {
                int msgAddedTime = ChatLineLocator.getAddedTimeAt(mc, (int) mx, (int) my);
                String linkedImageId = ChatLineLocator.getImageIdForMessageAt(mc, (int) mx, (int) my);
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

    private GuiMessage cr$lastResolvedMessage = null;

    private void cr$updateInputY(ChatScreen self) {
        if (input == null) return;

        input.setY(self.height - 12);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void chatremastered$afterInit(CallbackInfo ci) {
        cr$updateInputY((ChatScreen)(Object)this);
        if (input != null) {
            input.setFormatter((fragment, offset) ->
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

    @Inject(method = "removed", at = @At("TAIL"))
    private void chatremastered$onClose(CallbackInfo ci) {
        cr$closeMenu();
        cr$clearReply();
        if (cr$tagCommandSuggestions != null) {
            cr$tagCommandSuggestions.hide();
        }
        cr$tagSuggestionsForText = null;
        cr$screenshotsPanel.close();
        cr$screenshotsPanel.releaseAll();
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), 0L);
    }

    private void cr$openImageMenu(Minecraft mc, ChatScreen self, int ax, int ay, String imageId, int msgAddedTime) {
        String textOfMsg = ChatLineLocator.getMessageTextForImageId(mc, imageId);
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

    private void openFileDialog() {
        Minecraft mc = Minecraft.getInstance();
        cr$preservedChatInput = input != null ? input.getValue() : "";
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
                        mc.setScreen(new ChatScreen(cr$preservedChatInput));
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
        mc.setScreen(new ChatScreen(cr$preservedChatInput));
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
                        sender = ChatLineLocator.stripHeadPlaceholders(nickComp.getString()).trim();
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

        cr$replySenderName = isPlayerMessage ? ChatLineLocator.stripHeadPlaceholders(sender) : null;
        cr$replySenderComp = isPlayerMessage ? senderComp : null;

        String cleanText = (text != null && !text.isBlank()) ? ChatLineLocator.stripHeadPlaceholders(text) : null;
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




    private static net.minecraft.network.chat.MutableComponent stripObjectContentsComponent(net.minecraft.network.chat.Component component) {
        net.minecraft.network.chat.ComponentContents contents = component.getContents();
        net.minecraft.network.chat.MutableComponent copy;
        if (contents instanceof net.minecraft.network.chat.contents.PlainTextContents plain) {
            copy = net.minecraft.network.chat.Component.literal(plain.text());
        } else if (contents instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            copy = net.minecraft.network.chat.Component.translatable(tc.getKey(), tc.getArgs());
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