package dev.errnicraft.chatremastered.mixin;

import dev.errnicraft.chatremastered.ChatRemasteredClient;
import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ChatRemasteredConfigScreen;
import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.ImageCache;
import dev.errnicraft.chatremastered.ImageViewerScreen;
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

    private static final int CAM_BTN_W = 18;
    private static final int CAM_BTN_H = 18;
    private static final int CFG_BTN_W = 18;
    private static final int CFG_BTN_H = 18;

    private static final Identifier PLACEHOLDER_TEX =
            Identifier.fromNamespaceAndPath("chat-remastered", "textures/placeholder.png");

    // ── Inline context menu state ──
    private boolean cr$menuOpen = false;
    private int cr$menuX, cr$menuY;
    private int cr$menuComputedW = 160;
    private long cr$menuOpenTime;
    private String[] cr$menuLabels;
    private String[] cr$menuIcons;
    private Runnable[] cr$menuActions;
    private int[] cr$menuColors;   // null = default, per-item override (e.g. red for delete)
    private int cr$menuHoveredLast = -1;
    private float[] cr$menuItemAnim; // 0..1 hover brightness per item

    // Hover state for message rows
    private int cr$hoveredMsgLine = -1; // index into trimmed messages (from bottom), -1 = none

    // ── Reply state ──
    // replyAddedTime: addedTime строки на которую отвечаем (-1 = нет ответа)
    private int cr$replyAddedTime = -1;
    private String cr$replySenderName = null;   // plain имя отправителя (для пакета на сервер)
    private net.minecraft.network.chat.Component cr$replySenderComp = null; // компонент с цветом ника
    private String cr$replyText = null;          // текст сообщения (или caption для фото)
    private String cr$replyImageId = null;       // imageId если ответ на фото, иначе null
    // Анимации
    private float cr$replyBarAnim = 0f;          // 0..1 появление reply bar (выезд снизу)
    private float cr$replyBarXAnim = 0f;         // 0..1 горизонтальный сдвиг при фото
    private float cr$photoPreviewAnim = 0f;      // 0..1 появление превью фото (выезд снизу)
    private static final float ANIM_SPEED = 0.2f;
    // Подсветка при прокрутке к сообщению
    private int cr$highlightAddedTime = -1;
    private long cr$highlightStartMs = 0L;
    private static final long HIGHLIGHT_DURATION_MS = 2000L;

    private static final int MENU_W = 160;
    private static final int MENU_ITEM_H = 14; // высота пункта: lineHeight(9) + 5px паддинг
    private static final int MENU_ITEM_PAD = 5; // vertical pad inside item
    private static final int MENU_PAD = 4;
    private static final int MENU_ICON_W = 10; // fixed icon column width
    private static final int MENU_ANIM_MS = 100;

    @Shadow
    protected EditBox input;

    private boolean canSendPhoto() {
        return ChatRemasteredConfig.INSTANCE.getServerHasModVersion() != null
                && !ChatRemasteredConfig.INSTANCE.getUploadToken().isEmpty()
                && ChatRemasteredConfig.INSTANCE.getServerReachable()
                && !ChatRemasteredConfig.INSTANCE.getBanned()
                && !ChatRemasteredConfig.INSTANCE.getMuted()
                && ChatRemasteredConfig.INSTANCE.cooldownRemainingMs() <= 0L;
    }

    private String getButtonHint() {
        if (ChatRemasteredConfig.INSTANCE.getServerHasModVersion() == null)
            return ChatRemasteredConfig.INSTANCE.tr("chat-remastered.btn_no_server_mod");
        if (ChatRemasteredConfig.INSTANCE.getBanned())
            return ChatRemasteredConfig.INSTANCE.tr("chat-remastered.btn_banned");
        if (ChatRemasteredConfig.INSTANCE.getMuted())
            return ChatRemasteredConfig.INSTANCE.tr("chat-remastered.btn_muted");
        long cooldownMs = ChatRemasteredConfig.INSTANCE.cooldownRemainingMs();
        if (cooldownMs > 0L) {
            long totalSec = (cooldownMs + 999L) / 1000L;
            if (totalSec >= 60L) {
                long m = totalSec / 60L, s = totalSec % 60L;
                return ChatRemasteredConfig.INSTANCE.tr("chat-remastered.cooldown_minutes", m, s);
            } else {
                return ChatRemasteredConfig.INSTANCE.tr("chat-remastered.cooldown_seconds", totalSec);
            }
        }
        return ChatRemasteredConfig.INSTANCE.tr("chat-remastered.attach");
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
            // 9px fixed icon column + 2px gap, only if icon present
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
        // Подсветка при прокрутке к сообщению
        cr$renderHighlight(graphics, mc);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void chatremastered$render(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatScreen self = (ChatScreen)(Object)this;
        Minecraft mc = Minecraft.getInstance();

        // ── 1. Кнопка камеры ──
        // Кнопки всегда на фиксированной позиции, поле ввода не сдвигается
        int inputBarTop = self.height - 12;
        int camBtnX = self.width - CAM_BTN_W - 2;
        int camBtnY = inputBarTop - CAM_BTN_H - 2;

        boolean canSend = canSendPhoto();
        boolean hoverCam = mouseX >= camBtnX && mouseX < camBtnX + CAM_BTN_W
                && mouseY >= camBtnY && mouseY < camBtnY + CAM_BTN_H;

        int camBgColor = !canSend ? 0xCC111111 : (hoverCam ? 0xCC444444 : 0xCC1A1A1A);
        int camBorderColor = !canSend ? 0xFF2A2A2A : 0xFF666666;
        int camIconColor = !canSend ? 0xFF555555 : 0xFFFFFFFF;

        graphics.fill(camBtnX, camBtnY, camBtnX + CAM_BTN_W, camBtnY + CAM_BTN_H, camBgColor);
        graphics.fill(camBtnX, camBtnY, camBtnX + CAM_BTN_W, camBtnY + 1, camBorderColor);
        graphics.fill(camBtnX, camBtnY + CAM_BTN_H - 1, camBtnX + CAM_BTN_W, camBtnY + CAM_BTN_H, camBorderColor);
        graphics.fill(camBtnX, camBtnY, camBtnX + 1, camBtnY + CAM_BTN_H, camBorderColor);
        graphics.fill(camBtnX + CAM_BTN_W - 1, camBtnY, camBtnX + CAM_BTN_W, camBtnY + CAM_BTN_H, camBorderColor);
        int camIconY = camBtnY + (CAM_BTN_H - mc.font.lineHeight) / 2;
        graphics.drawCenteredString(mc.font, "📁", camBtnX + CAM_BTN_W / 2, camIconY, camIconColor);

        if (hoverCam) {
            String hint = getButtonHint();
            Component hintComp = canSend ? Component.literal(hint) : Component.literal("§c" + hint);
            List<ClientTooltipComponent> lines = Collections.singletonList(
                    ClientTooltipComponent.create(hintComp.getVisualOrderText()));
            graphics.renderTooltip(mc.font, lines, camBtnX - 2, camBtnY + CAM_BTN_H / 2,
                    (sw, sh, x, y, w, h) -> new org.joml.Vector2i(x - w, y - h / 2), null);
        }

        // ── 1б. Кнопка конфига ──
        int cfgBtnX = self.width - CFG_BTN_W - 2;
        int cfgBtnY = camBtnY - CFG_BTN_H - 2;
        boolean hoverCfg = mouseX >= cfgBtnX && mouseX < cfgBtnX + CFG_BTN_W
                && mouseY >= cfgBtnY && mouseY < cfgBtnY + CFG_BTN_H;

        int cfgBgColor = hoverCfg ? 0xCC444444 : 0xCC1A1A1A;
        graphics.fill(cfgBtnX, cfgBtnY, cfgBtnX + CFG_BTN_W, cfgBtnY + CFG_BTN_H, cfgBgColor);
        graphics.fill(cfgBtnX, cfgBtnY, cfgBtnX + CFG_BTN_W, cfgBtnY + 1, 0xFF666666);
        graphics.fill(cfgBtnX, cfgBtnY + CFG_BTN_H - 1, cfgBtnX + CFG_BTN_W, cfgBtnY + CFG_BTN_H, 0xFF666666);
        graphics.fill(cfgBtnX, cfgBtnY, cfgBtnX + 1, cfgBtnY + CFG_BTN_H, 0xFF666666);
        graphics.fill(cfgBtnX + CFG_BTN_W - 1, cfgBtnY, cfgBtnX + CFG_BTN_W, cfgBtnY + CFG_BTN_H, 0xFF666666);
        int cfgIconY = cfgBtnY + (CFG_BTN_H - mc.font.lineHeight) / 2;
        graphics.drawCenteredString(mc.font, "⚙", cfgBtnX + CFG_BTN_W / 2, cfgIconY, 0xFFCCCCCC);

        if (hoverCfg) {
            List<ClientTooltipComponent> cfgLines = Collections.singletonList(
                    ClientTooltipComponent.create(Component.literal(
                            ChatRemasteredConfig.INSTANCE.tr("chat-remastered.open_config")).getVisualOrderText()));
            graphics.renderTooltip(mc.font, cfgLines, cfgBtnX - 2, cfgBtnY + CFG_BTN_H / 2,
                    (sw, sh, x, y, w, h) -> new org.joml.Vector2i(x - w, y - h / 2), null);
        }

        // ── 2. Превью фото ──
        PendingImageState.PendingImage pending = PendingImageState.getPending();
        if (pending == null) {
            // Reply bar рисуется над сообщением в чате через cr$renderReplyOverMessage
            cr$renderReplyOverMessage(graphics, mc, mouseX, mouseY);
            chatremastered$updateCursorAndHover(graphics, mc, mouseX, mouseY);
            chatremastered$renderMenu(graphics, mc, mouseX, mouseY);
            return;
        }

        boolean isLoaded = pending.isLoaded();
        Identifier tex = pending.getTextureId();
        int dispW = pending.getWidth();
        int dispH = pending.getHeight();
        int previewBottom = inputBarTop - 6;
        int previewTop = previewBottom - dispH;
        int previewLeft = 4;

        graphics.fill(previewLeft - 2, previewTop - 2, previewLeft + dispW + 2, previewBottom + 1, 0xAA000000);

        if (!isLoaded || tex == null) {
            boolean sizeKnown = pending.getSizeKnown();
            graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewBottom, 0xFF2A2A2A);
            int borderColor = 0xFF555555;
            graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewTop + 1, borderColor);
            graphics.fill(previewLeft, previewBottom - 1, previewLeft + dispW, previewBottom, borderColor);
            graphics.fill(previewLeft, previewTop, previewLeft + 1, previewBottom, borderColor);
            graphics.fill(previewLeft + dispW - 1, previewTop, previewLeft + dispW, previewBottom, borderColor);

            float progress = PendingImageState.INSTANCE.uploadProgress;
            if (progress < 0f && dispH >= 6) {
                int barH = Math.max(3, dispH / 8);
                int barTop = previewBottom - barH;
                int barFull = dispW - 2;
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barFull, previewBottom - 1, 0xFF1A1A1A);
                long now = System.currentTimeMillis();
                float phase = (now % 1200L) / 1200f;
                int dotW = Math.max(4, barFull / 3);
                int dotStart = Math.round((barFull - dotW) * phase);
                graphics.fill(previewLeft + 1 + dotStart, barTop, previewLeft + 1 + dotStart + dotW, previewBottom - 1, 0xFF3399EE);
                if (dispW >= 40 && barTop - previewTop >= mc.font.lineHeight + 2) {
                    String waitStr = "...";
                    int textX = previewLeft + (dispW - mc.font.width(waitStr)) / 2;
                    int textY = barTop - mc.font.lineHeight - 1;
                    if (textY >= previewTop + 1)
                        graphics.drawString(mc.font, waitStr, textX, textY, 0xFFAAAAAA, false);
                }
            } else if (progress >= 0f && dispH >= 6) {
                int barH = Math.max(3, dispH / 8);
                int barTop = previewBottom - barH;
                int barFull = dispW - 2;
                int barW = Math.max(1, Math.round(barFull * progress));
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barFull, previewBottom - 1, 0xFF1A1A1A);
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barW, previewBottom - 1, 0xFF3399EE);
                if (dispW >= 40 && barTop - previewTop >= mc.font.lineHeight + 2) {
                    int pct = Math.round(progress * 100f);
                    String pctStr = pct + "%";
                    int textX = previewLeft + (dispW - mc.font.width(pctStr)) / 2;
                    int textY = barTop - mc.font.lineHeight - 1;
                    if (textY >= previewTop + 1)
                        graphics.drawString(mc.font, pctStr, textX, textY, 0xFFFFFFFF, false);
                }
            }

            int barAreaH = (dispH >= 6) ? Math.max(3, dispH / 8) + 2 : 0;
            int iconAreaH = dispH - barAreaH;
            if (iconAreaH >= 12) {
                float iconScale = iconAreaH * 0.38f / 9.0f;
                iconScale = Math.min(iconScale, dispW * 0.55f / mc.font.lineHeight);
                iconScale = Math.max(iconScale, 1.0f);
                String icon = !sizeKnown ? "?" : "🖼";
                int iconColor = !sizeKnown ? 0xFF888888 : 0xFFCCCCCC;
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
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, texW, texH, texW, texH, -1);
            graphics.pose().popMatrix();
        }

        int btnX = previewLeft + dispW + 4;
        int cancelY = previewTop;
        int sendY = cancelY + 20;
        boolean hoverCancel = mouseX >= btnX && mouseX < btnX + 18 && mouseY >= cancelY && mouseY < cancelY + 18;
        graphics.fill(btnX, cancelY, btnX + 18, cancelY + 18, hoverCancel ? 0xCCFF4444 : 0xCC661111);
        graphics.drawCenteredString(mc.font, "✕", btnX + 9, cancelY + 5, 0xFFFFFFFF);
        boolean hoverSend = mouseX >= btnX && mouseX < btnX + 18 && mouseY >= sendY && mouseY < sendY + 18;
        int sendColor = isLoaded ? (hoverSend ? 0xCC44FF44 : 0xCC116611) : 0xCC555555;
        graphics.fill(btnX, sendY, btnX + 18, sendY + 18, sendColor);
        graphics.drawCenteredString(mc.font, "✔", btnX + 9, sendY + 5, isLoaded ? 0xFFFFFFFF : 0xFF888888);

        // Reply bar рисуется над сообщением в чате
        cr$renderReplyOverMessage(graphics, mc, mouseX, mouseY);

        chatremastered$updateCursorAndHover(graphics, mc, mouseX, mouseY);
        chatremastered$renderMenu(graphics, mc, mouseX, mouseY);
    }

    // Рендер hover-подсветки строки чата и контекстного меню
    private void chatremastered$updateCursorAndHover(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        // Сначала курсор/tooltip для изображений (оригинальная логика)
        List<ChatRemasteredStore.ImageMessage> imgs = ChatRemasteredStore.getMessageList();
        for (ChatRemasteredStore.ImageMessage msg : imgs) {
            if (msg.getDismissed() || !msg.hasScreenBounds()) continue;
            if (mouseX >= msg.getBoundsX0() && mouseX < msg.getBoundsX1()
                    && mouseY >= msg.getBoundsY0() && mouseY < msg.getBoundsY1()) {
                boolean isDeleted = ImageCache.INSTANCE.isDeleted(msg.getImageId());
                boolean isError = ImageCache.INSTANCE.isError(msg.getImageId());
                String hint;
                String colorPrefix;
                if (isDeleted) { hint = ChatRemasteredConfig.INSTANCE.tr("chat-remastered.hover_deleted"); colorPrefix = "§c"; }
                else if (isError) { hint = ChatRemasteredConfig.INSTANCE.tr("chat-remastered.hover_error"); colorPrefix = "§e"; }
                else { hint = ChatRemasteredConfig.INSTANCE.tr("chat-remastered.click_to_open"); colorPrefix = ""; }
                List<ClientTooltipComponent> lines = Collections.singletonList(
                        ClientTooltipComponent.create(Component.literal(colorPrefix + hint).getVisualOrderText()));
                graphics.renderTooltip(mc.font, lines, mouseX, mouseY,
                        (sw, sh, x, y, w, h) -> new org.joml.Vector2i(x, y - h - 4), null);
                // Выделяем строку чата при наведении на фото (фикс: раньше return не давал это сделать)
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

        // Hover по изображению — выделяем соответствующую строку чата
        boolean mouseOverImage = false;
        for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
            if (!imgMsg.getDismissed() && imgMsg.hasScreenBounds()
                    && mouseX >= imgMsg.getBoundsX0() && mouseX < imgMsg.getBoundsX1()
                    && mouseY >= imgMsg.getBoundsY0() && mouseY < imgMsg.getBoundsY1()) {
                mouseOverImage = true;
                if (!cr$menuOpen) {
                    // Найти строку чата по imageId
                    cr$hoveredMsgLine = cr$findLineIndexForImageId(mc, imgMsg.getImageId());
                }
                break;
            }
        }
        // Также не показываем на области превью загружаемого фото
        PendingImageState.PendingImage pend = PendingImageState.getPending();
        if (pend != null) {
            int inputBarTopH = ((ChatScreen)(Object)this).height - 12;
            int prevBottom = inputBarTopH - 2;
            int prevTop = prevBottom - pend.getHeight();
            if (mouseY >= prevTop - 2 && mouseY <= prevBottom + 18 && mouseX <= 4 + pend.getWidth() + 4 + 18) {
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

        // Перевод GUI-мыши в scaled-пространство чата
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

    // Рисует подсветку всех строк сообщения в GUI-координатах (без scale-transform)
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

        // GUI-ширина чата: от x=0 до (chatWidthPx+8)*scale (translate(4)+4 отступ с каждой стороны)
        int guiLeft  = 0;
        int guiRight = Math.round((chatWidthPx + 8) * scale);
        // Тёплый синевато-голубой, заметный но не аляпистый
        // Тёмный overlay: не перебивает текст, только чуть затемняет фон
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

        // Анимация: выезжает сверху вниз (clip по высоте) + лёгкий fade
        float elapsed = System.currentTimeMillis() - cr$menuOpenTime;
        float t = Math.min(1f, elapsed / MENU_ANIM_MS);
        // ease out cubic
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        int visibleH = Math.round(menuH * ease);
        float alpha = Math.min(1f, t * 2f);

        int mx0 = cr$menuX, my0 = cr$menuY;
        int mx1 = mx0 + cr$menuComputedW, my1 = my0 + menuH;

        // Clip: рисуем только видимую (анимированную) часть
        int clipBot = my0 + visibleH;

        // === Тень (многослойная, мягкая) ===
        int shadowAlpha = Math.round(0x55 * alpha);
        for (int s = 4; s >= 1; s--) {
            int sa = shadowAlpha / s;
            cr$fillClipped(graphics, mx0 + s, my0 + s, mx1 + s, Math.min(clipBot + s, my1 + s),
                    (sa << 24), my0, clipBot + s);
        }

        // === Фон: тёмный акрил Win11 (#202020 с лёгкой прозрачностью) ===
        int bgAlpha = Math.round(0xEC * alpha);
        cr$fillClipped(graphics, mx0, my0, mx1, clipBot, (bgAlpha << 24) | 0x1E1E1E, my0, clipBot);

        // === Рамка: тонкая 1px, цвет #3A3A3A ===
        int borderAlpha = Math.round(0xFF * alpha);
        int borderColor = (borderAlpha << 24) | 0x3A3A3A;
        // top
        cr$fillClipped(graphics, mx0, my0, mx1, my0 + 1, borderColor, my0, clipBot);
        // bottom
        cr$fillClipped(graphics, mx0, my1 - 1, mx1, my1, borderColor, my0, clipBot);
        // left/right
        cr$fillClipped(graphics, mx0, my0, mx0 + 1, my1, borderColor, my0, clipBot);
        cr$fillClipped(graphics, mx1 - 1, my0, mx1, my1, borderColor, my0, clipBot);
        // закругления (4px срезы по углам)
        int bg = (bgAlpha << 24) | 0x1E1E1E;
        // top-left
        graphics.fill(mx0, my0, mx0 + 3, my0 + 1, 0); graphics.fill(mx0, my0, mx0 + 1, my0 + 3, 0);
        // top-right
        graphics.fill(mx1 - 3, my0, mx1, my0 + 1, 0); graphics.fill(mx1 - 1, my0, mx1, my0 + 3, 0);
        if (clipBot >= my1) {
            // bottom-left
            graphics.fill(mx0, my1 - 1, mx0 + 3, my1, 0); graphics.fill(mx0, my1 - 3, mx0 + 1, my1, 0);
            // bottom-right
            graphics.fill(mx1 - 3, my1 - 1, mx1, my1, 0); graphics.fill(mx1 - 1, my1 - 3, mx1, my1, 0);
        }

        // === Пункты меню ===
        for (int i = 0; i < n; i++) {
            int iy = my0 + PAD + i * (ITEM_H + 2);
            int iy2 = iy + ITEM_H;
            if (iy >= clipBot) break; // ниже clip

            boolean hovered = mouseX >= mx0 + 2 && mouseX < mx1 - 2 && mouseY >= iy && mouseY < iy2;

            // Hover: насыщенный синий Win11 #0078D4 с закруглением (2px отступ)
            float target = hovered ? 1f : 0f;
            cr$menuItemAnim[i] += (target - cr$menuItemAnim[i]) * 0.3f;
            float a = cr$menuItemAnim[i];
            if (a > 0.01f) {
                int hAlpha = Math.round(0xFF * a * alpha);
                boolean isRedItem = cr$menuColors != null && i < cr$menuColors.length && cr$menuColors[i] == 0xFF4444;
                int hR, hG, hB;
                if (isRedItem) {
                    // Красный hover: #2B2B2B → #C0392B
                    hR = (int)(0x2B + (0xC0 - 0x2B) * a);
                    hG = (int)(0x2B + (0x39 - 0x2B) * a);
                    hB = (int)(0x2B + (0x2B - 0x2B) * a);
                } else {
                    // Win11 синий hover: #2B2B2B → #0078D4
                    hR = (int)(0x2B + (0x00 - 0x2B) * a);
                    hG = (int)(0x2B + (0x78 - 0x2B) * a);
                    hB = (int)(0x2B + (0xD4 - 0x2B) * a);
                }
                int hoverColor = (hAlpha << 24) | (hR << 16) | (hG << 8) | hB;
                // закруглённый hover (2px corner срезы)
                int hx0 = mx0 + 3, hx1 = mx1 - 3;
                int clipIy2 = Math.min(iy2, clipBot);
                cr$fillClipped(graphics, hx0, iy, hx1, clipIy2, hoverColor, my0, clipBot);
                cr$fillClipped(graphics, hx0 + 1, iy - 1, hx1 - 1, iy, hoverColor, my0, clipBot);
                cr$fillClipped(graphics, hx0 + 1, iy2, hx1 - 1, iy2 + 1, hoverColor, my0, clipBot);
                cr$fillClipped(graphics, hx0 - 1, iy + 1, hx0, iy2 - 1, hoverColor, my0, clipBot);
                cr$fillClipped(graphics, hx1, iy + 1, hx1 + 1, iy2 - 1, hoverColor, my0, clipBot);
            }

            if (iy2 > clipBot) continue; // текст не рисуем если срезан

            int textAlpha = Math.round(0xFF * alpha);
            // Цвет пункта: если задан cr$menuColors — используем его (для красного «Удалить»)
            int baseColor = (cr$menuColors != null && i < cr$menuColors.length && cr$menuColors[i] != 0)
                    ? cr$menuColors[i] : 0xE0E0E0;
            int textColor = hovered
                    ? (textAlpha << 24 | 0xFFFFFF)
                    : (textAlpha << 24 | baseColor);
            int textY = iy + (ITEM_H - mc.font.lineHeight) / 2 + 1; // +1: компенсация baseline в MC шрифте
            String icon = cr$menuIcons[i];
            if (!icon.isEmpty()) {
                graphics.drawString(mc.font, icon, mx0 + PAD + 2, textY, textColor, false);
                graphics.drawString(mc.font, cr$menuLabels[i], mx0 + PAD + 11, textY, textColor, false);
            } else {
                graphics.drawString(mc.font, cr$menuLabels[i], mx0 + PAD + 2, textY, textColor, false);
            }
        }
    }

    // Рисует прямоугольник с ограничением по вертикальному clipBottom
    private void cr$fillClipped(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int clipTop, int clipBot) {
        y0 = Math.max(y0, clipTop);
        y1 = Math.min(y1, clipBot);
        if (y0 >= y1 || x0 >= x1) return;
        g.fill(x0, y0, x1, y1, color);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void chatremastered$handleKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
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
                        // Проверяем лимит только если реально вставляем картинку
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
        PendingImageState.PendingImage pending = PendingImageState.getPending();
        if (pending == null) {
            // Если активен reply — шлём один ReplyMetaPacket с текстом (sendChat НЕ вызываем)
            if (cr$replyAddedTime >= 0 && input != null && !input.getValue().trim().isEmpty()) {
                // Проверяем мут на клиенте — не отправляем если замучены
                if (ChatRemasteredConfig.INSTANCE.getMuted()) {
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
                        net.minecraft.network.chat.Component.literal("§c[Chat Remastered] " +
                            ChatRemasteredConfig.INSTANCE.tr("chat-remastered.muted"))
                    );
                    cir.setReturnValue(true);
                    return;
                }
                String text = input.getValue().trim();
                String replyToSender = cr$replySenderName != null ? cr$replySenderName : "";
                String replyToText   = cr$replyText     != null ? cr$replyText     : "";
                String replyToImgId  = cr$replyImageId  != null ? cr$replyImageId  : "";
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.errnicraft.chatremastered.ReplyMetaPacket(text, replyToSender, replyToText, replyToImgId)
                );
                input.setValue("");
                cr$clearReply();
                cir.setReturnValue(true);
                return;
            }
            // Нет reply — ванильный Enter отправит текст сам
            cr$clearReply();
            return;
        }
        if (!pending.isLoaded()) { cir.setReturnValue(true); return; }

        String caption = (input != null && !input.getValue().trim().isEmpty())
                ? input.getValue().trim() : null;
        ChatRemasteredClient.sendPendingImageWithCaption(caption);
        if (input != null) input.setValue("");
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

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chatremastered$handleMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0 && event.button() != 1) return;
        boolean isRightClick = event.button() == 1;
        double mx = event.x();
        double my = event.y();
        ChatScreen self = (ChatScreen)(Object)this;
        Minecraft mc = Minecraft.getInstance();

        // Если меню открыто — обрабатываем клик по нему
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

        // 1. Кнопка конфига — фиксированная позиция
        int inputBarTop = self.height - 12;
        int camBtnX = self.width - CAM_BTN_W - 2;
        int camBtnY = inputBarTop - CAM_BTN_H - 2;
        int cfgBtnX = self.width - CFG_BTN_W - 2;
        int cfgBtnY = camBtnY - CFG_BTN_H - 2;

        // 0. Клик по reply bar над полем ввода
        if (!isRightClick && cr$replyAddedTime >= 0) {
            int[] replyBarBounds = cr$getReplyBarBounds(mc);
            if (replyBarBounds != null) {
                int rbX = replyBarBounds[0], rbY = replyBarBounds[1], rbW = replyBarBounds[2], rbH = replyBarBounds[3];
                if (mx >= rbX && mx < rbX + rbW && my >= rbY && my < rbY + rbH) {
                    int closeX = rbX + rbW - 12;
                    if (mx >= closeX - 2) {
                        // Крестик — закрыть reply
                        cr$clearReply();
                    } else {
                        // Клик по остальной части bar — скролл к оригинальному сообщению
                        cr$scrollToMessage(mc, cr$replyAddedTime);
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (mx >= cfgBtnX && mx < cfgBtnX + CFG_BTN_W && my >= cfgBtnY && my < cfgBtnY + CFG_BTN_H) {
            mc.setScreen(new ChatRemasteredConfigScreen(self));
            cir.setReturnValue(true);
            return;
        }

        // 2. Кнопка камеры
        if (mx >= camBtnX && mx < camBtnX + CAM_BTN_W && my >= camBtnY && my < camBtnY + CAM_BTN_H) {
            if (!canSendPhoto()) { ChatRemasteredClient.canSendPhoto(mc); cir.setReturnValue(true); return; }
            openFileDialog();
            cir.setReturnValue(true);
            return;
        }

        // 3. Кнопки превью
        PendingImageState.PendingImage pending = PendingImageState.getPending();
        if (pending != null) {
            int dispW = pending.getWidth();
            int dispH = pending.getHeight();
            int previewBottom = inputBarTop - 6;
            int previewTop = previewBottom - dispH;
            int btnX = 4 + dispW + 4;
            int cancelY = previewTop;
            int sendY = cancelY + 20;
            if (mx >= btnX && mx < btnX + 18 && my >= cancelY && my < cancelY + 18) {
                PendingImageState.clear(); cir.setReturnValue(true); return;
            } else if (mx >= btnX && mx < btnX + 18 && my >= sendY && my < sendY + 18) {
                if (!pending.isLoaded()) { cir.setReturnValue(true); return; }
                String caption = (input != null && !input.getValue().trim().isEmpty()) ? input.getValue().trim() : null;
                ChatRemasteredClient.sendPendingImageWithCaption(caption);
                if (input != null) input.setValue("");
                cir.setReturnValue(true);
                return;
            }
        }

        // 4. Клик по reply bar над сообщениями в чате
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

            // Ищем reply bar — это строка с текстом начинающимся на \n (спейсер перед ником)
            int prevAddedTime2 = -1;
            for (int i = 0; i < Math.min(trimmed2.size() - scrollPos2, lpp2); i++) {
                int idx = i + scrollPos2;
                GuiMessage.Line line2 = trimmed2.get(idx);
                int addedTime2 = line2.addedTime();
                // Плашка рисуется над строкой с \n — найдём её по тому что предыдущая строка
                // имеет другой addedTime (начало нового сообщения-блока с ответом)
                // Проще: ищем строки где content начинается с \n в allMessages
                boolean isSpacerLine = false;
                for (GuiMessage gm : acc2.getAllMessages()) {
                    if (gm.addedTime() == addedTime2 && gm.content().getString().startsWith("\n")) {
                        isSpacerLine = true;
                        break;
                    }
                }
                if (!isSpacerLine) { prevAddedTime2 = addedTime2; continue; }

                // Это строка-спейсер — плашка рисуется на её месте
                int entryBottom2 = chatBottom2 - i * entryH2;
                int entryTop2 = entryBottom2 - entryH2;
                int guiBarTop2    = chatBottomGui2 - (int)((chatBottom2 - entryTop2)    * chatScale2);
                int guiBarBottom2 = chatBottomGui2 - (int)((chatBottom2 - entryBottom2) * chatScale2);

                if (guiBarTop2 < chatTopGui2 || guiBarBottom2 > chatBottomGui2) { prevAddedTime2 = addedTime2; continue; }
                if (mx >= 0 && mx < guiBarRight2 && my >= guiBarTop2 && my < guiBarBottom2) {
                    // Ищем ReplyMessage по addedTime этой строки-спейсера
                    int replyTargetTime = -1;
                    for (ChatRemasteredStore.ReplyMessage rm : ChatRemasteredStore.getRepliesList()) {
                        if (rm.getAddedTime() == addedTime2) {
                            replyTargetTime = rm.getReplyToAddedTime();
                            // Если replyToAddedTime ещё не resolved — пробуем найти сами
                            if (replyTargetTime < 0) {
                                if (!rm.getReplyToImageId().isEmpty()) {
                                    // Ответ на фото: ищем по imageId в ImageMessage Store
                                    for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
                                        if (imgMsg.getImageId().equals(rm.getReplyToImageId()) && imgMsg.getAddedTime() >= 0) {
                                            replyTargetTime = imgMsg.getAddedTime();
                                            rm.setReplyToAddedTime(replyTargetTime);
                                            break;
                                        }
                                    }
                                } else if (!rm.getReplyToText().isEmpty()) {
                                    // Ответ на текст: ищем в allMessages
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

        // 5. Клик по изображению в чате
        List<ChatRemasteredStore.ImageMessage> imgs = ChatRemasteredStore.getMessageList();
        for (ChatRemasteredStore.ImageMessage msg : imgs) {
            if (msg.getDismissed() || !msg.hasScreenBounds()) continue;
            if (mx >= msg.getBoundsX0() && mx < msg.getBoundsX1()
                    && my >= msg.getBoundsY0() && my < msg.getBoundsY1()) {

                if (isRightClick) {
                    cr$openImageMenu(mc, self, (int) mx, (int) my, msg.getImageId(), msg.getAddedTime());
                    cir.setReturnValue(true);
                    return;
                }

                // ЛКМ
                // Если фото помечено как ошибка — не пытаемся качать снова (бесконечный цикл)
                if (ImageCache.INSTANCE.isError(msg.getImageId())) { cir.setReturnValue(true); return; }
                if (!ChatRemasteredConfig.INSTANCE.getAutoDownload()) {
                    ImageCache.DownloadState dlState = ImageCache.INSTANCE.getDownloadState(msg.getImageId());
                    if (dlState == ImageCache.DownloadState.IDLE) {
                        ChatRemasteredClient.fetchFullImageManual(msg.getImageId()); cir.setReturnValue(true); return;
                    } else if (dlState == ImageCache.DownloadState.IN_PROGRESS) {
                        cir.setReturnValue(true); return;
                    }
                }

                Identifier tex = ImageCache.INSTANCE.getTexture(msg.getImageId());
                kotlin.Pair<Integer, Integer> size = ImageCache.INSTANCE.getSize(msg.getImageId());
                if (tex != null && size != null) {
                    kotlin.Pair<Integer, Integer> texSize = ImageCache.INSTANCE.getTexSize(msg.getImageId());
                    java.io.File originalFile = ChatRemasteredStore.INSTANCE.getOriginalFile(msg.getImageId());
                    int w = texSize != null ? texSize.getFirst() : size.getFirst();
                    int h = texSize != null ? texSize.getSecond() : size.getSecond();
                    mc.setScreen(new ImageViewerScreen(tex, msg.getImageId(), w, h, originalFile));
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        // 5. ПКМ по тексту сообщения
        if (isRightClick) {
            String msgText = cr$getMessageTextAt(mc, (int) mx, (int) my);
            if (msgText != null) {
                int msgAddedTime = cr$getAddedTimeAt(mc, (int) mx, (int) my);
                String linkedImageId = cr$getImageIdForMessageAt(mc, (int) mx, (int) my);
                if (linkedImageId != null) {
                    cr$openImageMenu(mc, self, (int) mx, (int) my, linkedImageId, msgAddedTime);
                } else {
                    String replyLabel = ChatRemasteredConfig.INSTANCE.tr("chat-remastered.ctx_reply");
                    String copyLabel = ChatRemasteredConfig.INSTANCE.tr("chat-remastered.ctx_copy_message");
                    final String textToCopy = msgText;
                    final int replyTime = msgAddedTime;
                    cr$openMenu((int) mx, (int) my, self.width, self.height,
                            new String[]{ replyLabel, copyLabel },
                            new String[]{ "↩", "📋" },
                            new Runnable[]{
                                    () -> cr$startReply(mc, replyTime, null, textToCopy, null),
                                    () -> mc.keyboardHandler.setClipboard(textToCopy)
                            });
                }
                cir.setReturnValue(true);
            }
        }
    }

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
                for (GuiMessage msg : all) {
                    if (msg.addedTime() == line.addedTime()) {
                        String text = msg.content().getString();
                        if (text.startsWith("\n")) text = text.substring(1);
                        text = stripHeadPlaceholders(text);
                        if (!text.isBlank()) return text;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Поле ввода НЕ сдвигается — reply bar рисуется над сообщением в чате.
     */
    private void cr$updateInputY(ChatScreen self) {
        if (input == null) return;
        // Восстанавливаем ванильную позицию на случай если была сдвинута ранее
        input.setY(self.height - 12);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void chatremastered$afterInit(CallbackInfo ci) {
        cr$updateInputY((ChatScreen)(Object)this);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void chatremastered$renderHead(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        cr$updateInputY((ChatScreen)(Object)this);
        // Тикаем анимации
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
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), 0L);
    }

    // Единый контекст для фото-сообщений
    private void cr$openImageMenu(Minecraft mc, ChatScreen self, int ax, int ay, String imageId, int msgAddedTime) {
        String textOfMsg = cr$getMessageTextForImageId(mc, imageId);
        String replyLabel = ChatRemasteredConfig.INSTANCE.tr("chat-remastered.ctx_reply");
        boolean isGifImage = dev.errnicraft.chatremastered.ImageCache.INSTANCE.isGif(imageId);
        String saveLabel = ChatRemasteredConfig.INSTANCE.tr(
                isGifImage ? "chat-remastered.ctx_save_as_gif" : "chat-remastered.ctx_save_as");
        String copyIdLabel = ChatRemasteredConfig.INSTANCE.tr("chat-remastered.ctx_copy_id");
        String copyMsgLabel = ChatRemasteredConfig.INSTANCE.tr("chat-remastered.ctx_copy_message");
        String deleteLabel = isGifImage ? "Удалить GIF" : "Удалить фото";
        boolean hasText = textOfMsg != null && !textOfMsg.isBlank();
        final int replyTime = msgAddedTime;

        boolean isDeleted   = dev.errnicraft.chatremastered.ImageCache.INSTANCE.isDeleted(imageId);
        boolean isError     = dev.errnicraft.chatremastered.ImageCache.INSTANCE.isError(imageId);
        ImageCache.DownloadState dlState = dev.errnicraft.chatremastered.ImageCache.INSTANCE.getDownloadState(imageId);
        boolean isLoading   = dlState == ImageCache.DownloadState.IN_PROGRESS;
        boolean isIdle      = dlState == ImageCache.DownloadState.IDLE;
        // «Не загружено» — либо ещё идёт загрузка, либо режим ручного скачивания и не начали
        boolean unavailable = isDeleted || isError || isLoading || isIdle;

        boolean isOwnPhoto = ChatRemasteredStore.INSTANCE.getOriginalFile(imageId) != null;

        Runnable deleteAction = () -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new dev.errnicraft.chatremastered.DeleteImagePacket(imageId)
            );
        };

        Runnable copyIdAction = () -> {
            mc.keyboardHandler.setClipboard(imageId);
            mc.gui.getChat().addMessage(Component.literal(
                    "§8[Chat Remastered] §7" + ChatRemasteredConfig.INSTANCE.tr("chat-remastered.id_copied", imageId, imageId)));
        };

        if (unavailable) {
            // Меню без кнопки «Сохранить» — фото недоступно локально
            if (hasText) {
                final String txt = textOfMsg;
                if (isOwnPhoto && !isDeleted) {
                    // Своё фото в состоянии ошибки/загрузки — можно удалить
                    cr$openMenu(ax, ay, self.width, self.height,
                            new String[]{ replyLabel, copyMsgLabel, copyIdLabel, deleteLabel },
                            new String[]{ "↩", "📋", "🔗", "🗑" },
                            new Runnable[]{
                                    () -> cr$startReply(mc, replyTime, imageId, txt, imageId),
                                    () -> mc.keyboardHandler.setClipboard(txt),
                                    copyIdAction,
                                    deleteAction
                            },
                            new int[]{ 0, 0, 0, 0xFF4444 });
                } else {
                    cr$openMenu(ax, ay, self.width, self.height,
                            new String[]{ replyLabel, copyMsgLabel, copyIdLabel },
                            new String[]{ "↩", "📋", "🔗" },
                            new Runnable[]{
                                    () -> cr$startReply(mc, replyTime, imageId, txt, imageId),
                                    () -> mc.keyboardHandler.setClipboard(txt),
                                    copyIdAction
                            });
                }
            } else {
                if (isOwnPhoto && !isDeleted) {
                    cr$openMenu(ax, ay, self.width, self.height,
                            new String[]{ replyLabel, copyIdLabel, deleteLabel },
                            new String[]{ "↩", "🔗", "🗑" },
                            new Runnable[]{
                                    () -> cr$startReply(mc, replyTime, imageId, null, imageId),
                                    copyIdAction,
                                    deleteAction
                            },
                            new int[]{ 0, 0, 0xFF4444 });
                } else {
                    cr$openMenu(ax, ay, self.width, self.height,
                            new String[]{ replyLabel, copyIdLabel },
                            new String[]{ "↩", "🔗" },
                            new Runnable[]{
                                    () -> cr$startReply(mc, replyTime, imageId, null, imageId),
                                    copyIdAction
                            });
                }
            }
            return;
        }

        // Обычное меню — фото доступно, можно сохранить
        if (hasText) {
            final String txt = textOfMsg;
            if (isOwnPhoto) {
                cr$openMenu(ax, ay, self.width, self.height,
                        new String[]{ replyLabel, copyMsgLabel, saveLabel, copyIdLabel, deleteLabel },
                        new String[]{ "↩", "📋", "💾", "🔗", "🗑" },
                        new Runnable[]{
                                () -> cr$startReply(mc, replyTime, imageId, txt, imageId),
                                () -> mc.keyboardHandler.setClipboard(txt),
                                () -> ChatRemasteredClient.saveImageAs(imageId),
                                copyIdAction,
                                deleteAction
                        },
                        new int[]{ 0, 0, 0, 0, 0xFF4444 });
            } else {
                cr$openMenu(ax, ay, self.width, self.height,
                        new String[]{ replyLabel, copyMsgLabel, saveLabel, copyIdLabel },
                        new String[]{ "↩", "📋", "💾", "🔗" },
                        new Runnable[]{
                                () -> cr$startReply(mc, replyTime, imageId, txt, imageId),
                                () -> mc.keyboardHandler.setClipboard(txt),
                                () -> ChatRemasteredClient.saveImageAs(imageId),
                                copyIdAction
                        });
            }
        } else {
            if (isOwnPhoto) {
                cr$openMenu(ax, ay, self.width, self.height,
                        new String[]{ replyLabel, saveLabel, copyIdLabel, deleteLabel },
                        new String[]{ "↩", "💾", "🔗", "🗑" },
                        new Runnable[]{
                                () -> cr$startReply(mc, replyTime, imageId, null, imageId),
                                () -> ChatRemasteredClient.saveImageAs(imageId),
                                copyIdAction,
                                deleteAction
                        },
                        new int[]{ 0, 0, 0, 0xFF4444 });
            } else {
                cr$openMenu(ax, ay, self.width, self.height,
                        new String[]{ replyLabel, saveLabel, copyIdLabel },
                        new String[]{ "↩", "💾", "🔗" },
                        new Runnable[]{
                                () -> cr$startReply(mc, replyTime, imageId, null, imageId),
                                () -> ChatRemasteredClient.saveImageAs(imageId),
                                copyIdAction
                        });
            }
        }
    }

    // Находит индекс строки trimmedMessages по imageId — использует addedTime из Store
    private int cr$findLineIndexForImageId(Minecraft mc, String imageId) {
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        int scrollPos = acc.getChatScrollbarPos();
        int linesPerPage = mc.gui.getChat().getLinesPerPage();

        // Получаем addedTime из ImageMessage в Store
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

    // Возвращает caption из Store для данного imageId (не ищет в тексте GuiMessage)
    private String cr$getMessageTextForImageId(Minecraft mc, String imageId) {
        for (ChatRemasteredStore.ImageMessage img : ChatRemasteredStore.getMessageList()) {
            if (img.getImageId().equals(imageId)) {
                String caption = img.getCaption();
                return (caption == null || caption.isBlank()) ? null : caption;
            }
        }
        return null;
    }

    // Если сообщение под курсором является сообщением с фото — возвращает imageId.
    // Ищем по addedTime строки, а не по содержимому текста (imageId не вставляется в текст сообщения).
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
                // Ищем ImageMessage с таким же addedTime
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
                            ChatRemasteredConfig.INSTANCE.tr("chat-remastered.select_image"),
                            "", filters, "Image Files (*.png, *.jpg, *.jpeg, *.webp, *.bmp, *.tiff, *.gif)", false);
                }
                if (path != null) {
                    File file = new File(path);
                    if (!file.exists()) { mc.execute(() -> restoreChat(mc)); return; }
                    if (file.length() > 10L * 1024 * 1024) {
                        mc.execute(() -> {
                            restoreChat(mc);
                            mc.gui.getChat().addMessage(Component.literal(
                                    "§c[Chat Remastered] " + ChatRemasteredConfig.INSTANCE.tr("chat-remastered.file_too_large")));
                        });
                        return;
                    }
                    mc.execute(() -> {
                        ChatRemasteredClient.stageImage(file);
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

    // ── Reply: helpers ──

    /** Начать ответ на сообщение. imageId != null если отвечаем на фото. */
    private void cr$startReply(Minecraft mc, int addedTime, String imageIdForLookup,
                               String text, String replyImageId) {
        cr$replyAddedTime = addedTime;
        cr$replyImageId = replyImageId;
        // Имя отправителя: ищем в GuiMessage
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        String sender = null;
        net.minecraft.network.chat.Component senderComp = null;
        boolean isPlayerMessage = false;
        for (GuiMessage msg : acc.getAllMessages()) {
            if (msg.addedTime() == addedTime) {
                net.minecraft.network.chat.Component content = msg.content();

                // Убираем \n-wrapper: если первый sibling — "\n", берём второй
                if (!content.getSiblings().isEmpty()) {
                    var sibs = content.getSiblings();
                    if (sibs.get(0).getString().equals("\n") && sibs.size() >= 2) {
                        content = sibs.get(1);
                    }
                }

                // Стратегия 1: TranslatableContents ("chat.type.text")
                // При enforce-secure-profile=false ник всегда в args[0]
                if (content.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                    Object[] args = tc.getArgs();
                    if (args.length >= 1 && args[0] instanceof net.minecraft.network.chat.Component nickComp) {
                        sender = stripHeadPlaceholders(nickComp.getString());
                        senderComp = nickComp;   // несёт цвет от мода на кастом-ники!
                        isPlayerMessage = true;
                    }
                }

                // Стратегия 2: plain "<name> text" (стандартный онлайн-режим)
                if (sender == null) {
                    String raw = content.getString();
                    if (raw.startsWith("<")) {
                        int end = raw.indexOf('>');
                        if (end > 0) {
                            sender = raw.substring(1, end);
                            // Пытаемся найти цветной sibling с именем
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
        // Стратегия 3: если ответ на фото — берём senderComponent прямо из ImageMessage Store.
        // Это самый надёжный источник цвета ника: сервер передаёт его явно в ImageChatPacket.
        // Перекрывает Стратегию 2 которая может дать белый literal если sibling не найден.
        if (replyImageId != null && isPlayerMessage) {
            for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
                if (imgMsg.getImageId().equals(replyImageId)) {
                    net.minecraft.network.chat.Component stored = imgMsg.getSenderComponent();
                    if (stored != null && !stored.getString().isBlank()) {
                        senderComp = stored;
                    }
                    break;
                }
            }
        }
        // Для системных сообщений (без <name>) sender остаётся null
        cr$replySenderName = isPlayerMessage ? stripHeadPlaceholders(sender) : null;
        cr$replySenderComp = isPlayerMessage ? senderComp : null;
        // Очищаем текст от [head]-плейсхолдеров и от возможного "<ник> " префикса
        String cleanText = (text != null && !text.isBlank()) ? stripHeadPlaceholders(text) : null;
        if (cleanText != null && isPlayerMessage && sender != null) {
            // Убираем "<ник> " или "<ник>: " из начала текста (Chat Heads может оставить их)
            String nickPrefix1 = sender + "> ";
            String nickPrefix2 = "<" + sender + "> ";
            if (cleanText.startsWith(nickPrefix2)) cleanText = cleanText.substring(nickPrefix2.length()).trim();
            else if (cleanText.startsWith(nickPrefix1)) cleanText = cleanText.substring(nickPrefix1.length()).trim();
        }
        cr$replyText = (cleanText != null && !cleanText.isBlank()) ? cleanText : null;
        // Фокус на поле ввода
        if (input != null) input.setFocused(true);
    }

    /** Сбросить состояние ответа. */
    private void cr$clearReply() {
        cr$replyAddedTime = -1;
        cr$replySenderName = null;
        cr$replySenderComp = null;
        cr$replyText = null;
        cr$replyImageId = null;
    }

    /** Высота панели ответа в GUI-пикселях (0 если нет активного ответа). */
    private int cr$replyBarHeight() {
        return cr$replyAddedTime >= 0 ? 14 : 0;
    }

    /**
     * Возвращает [x, y, w, h] reply bar над сообщением, или null если не видно.
     */
    /**
     * Возвращает [x, y, w, h] reply bar — над полем ввода, на всю ширину чата.
     * При наличии прикреплённого фото — сдвинут вправо (плавно).
     * barY учитывает анимацию выезда снизу.
     */
    private int[] cr$getReplyBarBounds(Minecraft mc) {
        ChatScreen self = (ChatScreen)(Object)this;
        float scale = (float) mc.options.chatScale().get().doubleValue();
        if (scale < 0.01f) scale = 1f;
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
        int barH = 13;  // нечётная высота → текст точно по центру при lineHeight=9: (13-9)/2=2 сверху, 2 снизу
        // Левый край = x=2 (фон поля ввода: fill(2, height-14, ...) в ChatScreen.render)
        // input.getX() = 4 (сам EditBox), но фон начинается с 2 — выравниваем по фону
        int barLeft = 2;
        // Правый край = правый край области сообщений чата в GUI-coords
        // Чат рендерится с translate(4,0)*scale → правый край = (4 + chatWidthPx) * scale
        int barRight = Math.round((4 + chatWidthPx) * scale);
        int fullW    = barRight - barLeft;
        // Базовый Y — над полем ввода (height-12), вплотную без зазора
        int baseBarY = self.height - 12 - barH - 2;
        // Анимация выезда снизу (ease-out)
        float ease = 1f - (1f - cr$replyBarAnim) * (1f - cr$replyBarAnim);
        int animOffsetY = Math.round((barH + 4) * (1f - ease));
        int barY = baseBarY + animOffsetY;
        // Горизонтальный сдвиг при фото
        PendingImageState.PendingImage pending = PendingImageState.getPending();
        int photoOffset = 0;
        if (pending != null) {
            int photoAreaW = 4 + pending.getWidth() + 4 + 18 + 4;
            photoOffset = Math.round(photoAreaW * cr$replyBarXAnim);
        }
        int barX = barLeft + photoOffset;
        int adjustedW = fullW - photoOffset;
        if (adjustedW < 20) return null;
        return new int[]{barX, barY, adjustedW, barH};
    }

    /**
     * Рисует reply bar над полем ввода с анимацией появления и сдвига.
     */
    private void cr$renderReplyOverMessage(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        if (cr$replyBarAnim < 0.01f) return;
        int[] bounds = cr$getReplyBarBounds(mc);
        if (bounds == null) return;
        int barX = bounds[0], barY = bounds[1], barW = bounds[2], barH = bounds[3];

        // Прозрачность по анимации (ease-out)
        float ease = 1f - (1f - cr$replyBarAnim) * (1f - cr$replyBarAnim);
        int alpha = Math.round(0xCC * ease);
        int bgColor     = (alpha << 24) | 0x1A1A1A;
        int accentColor = (Math.round(0xFF * ease) << 24) | 0x3366CC;  // синяя полоска = ответ
        int textAlpha   = Math.round(0xFF * ease);

        graphics.fill(barX, barY, barX + barW, barY + barH, bgColor);
        graphics.fill(barX, barY, barX + 2, barY + barH, accentColor);

        int textY = barY + (barH - mc.font.lineHeight) / 2 + 1;  // +1: MC шрифт визуально выше baseline

        int closeX = barX + barW - 12;
        boolean hoverClose = mouseX >= closeX - 2 && mouseX < closeX + 10
                && mouseY >= barY && mouseY < barY + barH;
        int closeColor = (textAlpha << 24) | (hoverClose ? 0xFF4444 : 0x888888);
        graphics.drawString(mc.font, "✕", closeX, textY, closeColor, false);

        int contentX = barX + 5;
        int maxW = closeX - contentX - 4;

        // Если ответ на фото: значок ↩ + мини-превью + синий ник + текст
        // Если ответ на текст: только значок ↩ + текст (ник не нужен — он есть в <name>)
        String full;
        if (cr$replyImageId != null) {
            // Фото-reply: показываем миниатюру
            Identifier tex = ImageCache.INSTANCE.getTexture(cr$replyImageId);
            if (tex != null) {
                String arrowStr = "↩ ";
                graphics.drawString(mc.font, arrowStr, contentX, textY, (textAlpha << 24) | 0xFFFFFF, false);
                contentX += mc.font.width(arrowStr);
                // Мини-превью фото (10x10 на экране, масштабируется через pose)
                int photoW = 10, photoH = 10;
                kotlin.Pair<Integer,Integer> ts = ImageCache.INSTANCE.getTexSize(cr$replyImageId);
                int srcW = ts != null && ts.getFirst() > 0  ? ts.getFirst()  : photoW;
                int srcH = ts != null && ts.getSecond() > 0 ? ts.getSecond() : photoH;
                // Равномерный масштаб — вписываем в photoW x photoH с сохранением пропорций
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
            // Ник с цветом (от мода на кастом-ники) + текст (если есть)
            String suffix = cr$replyText != null ? cr$replyText : "";
            if (cr$replySenderComp != null) {
                // Рендерим ник как Component — сохраняются цвета от LuckPerms/кастом-ников
                net.minecraft.network.chat.MutableComponent label = net.minecraft.network.chat.Component.empty();
                label.append(stripObjectContentsComponent(cr$replySenderComp));
                if (!suffix.isEmpty()) {
                    label.append(net.minecraft.network.chat.Component.literal("§r§7: " + suffix));
                }
                // Обрезаем если не влезает; также удаляем [Player head]-плейсхолдеры от Chat Heads
                String labelStr = stripHeadPlaceholders(label.getString());
                // Если stripHeadPlaceholders что-то удалил — пересоздаём Component из очищенной строки
                // (цвета теряются, но зато нет мусора "[Errnick_ Head]" в плашке ответа)
                if (!labelStr.equals(label.getString())) {
                    label = net.minecraft.network.chat.Component.literal(labelStr);
                }
                if (mc.font.width(label) > maxW) {
                    String ellipsis = "…";
                    int ellW = mc.font.width(ellipsis);
                    while (!labelStr.isEmpty() && mc.font.width(labelStr) + ellW > maxW)
                        labelStr = labelStr.substring(0, labelStr.length() - 1);
                    labelStr += ellipsis;
                    label = net.minecraft.network.chat.Component.literal(labelStr);
                }
                graphics.drawString(mc.font, label, contentX, textY, (textAlpha << 24) | 0xFFFFFF, false);
                // Не используем full — выходим сразу
                return;
            } else {
                full = cr$replySenderName != null
                        ? "§b" + cr$replySenderName + "§r§7" + (suffix.isEmpty() ? "" : ": " + suffix)
                        : "§7" + suffix;
            }
        } else {
            // Текст-reply: только ↩ + текст сообщения, без ника
            String suffix = cr$replyText != null ? cr$replyText : "";
            full = "↩ §7" + suffix;
        }
        graphics.drawString(mc.font, cr$truncateFormatted(mc, full, maxW),
                contentX, textY, (textAlpha << 24) | 0xFFFFFF, false);
    }


    /** Обрезает строку (с §-кодами) до maxWidth пикселей, добавляет "…" если не влезло. */
    private String cr$truncateFormatted(Minecraft mc, String text, int maxWidth) {
        // Используем Component для корректного измерения с §-кодами
        net.minecraft.network.chat.Component comp = net.minecraft.network.chat.Component.literal(text);
        if (mc.font.width(comp) <= maxWidth) return text;
        // Обрезаем plain-текст (без §) до нужной ширины, сохраняя форматирование в начале
        String plain = comp.getString();
        String ellipsis = "…";
        int ellW = mc.font.width(ellipsis);
        // Срезаем с конца пока не войдёт
        while (plain.length() > 0 && mc.font.width(plain) + ellW > maxWidth)
            plain = plain.substring(0, plain.length() - 1);
        return plain + ellipsis;
    }

    /** Обрезает строку до maxWidth пикселей, добавляет "…" если не влезло. */
    private String cr$truncate(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellW = mc.font.width(ellipsis);
        while (!text.isEmpty() && mc.font.width(text) + ellW > maxWidth)
            text = text.substring(0, text.length() - 1);
        return text + ellipsis;
    }

    /** Возвращает addedTime строки под курсором, -1 если нет. */
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

    /**
     * Прокручивает чат до сообщения с данным addedTime и запускает подсветку.
     * Вызывается по ЛКМ на панели ответа.
     */
    private void cr$scrollToMessage(Minecraft mc, int targetAddedTime) {
        if (targetAddedTime < 0) return;
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage.Line> trimmed = acc.getTrimmedMessages();
        int linesPerPage = mc.gui.getChat().getLinesPerPage();

        // Берём максимальный индекс среди всех строк с targetAddedTime.
        // В trimmedMessages меньший индекс = нижняя строка, больший = верхняя.
        // Сообщение с reply-плашкой начинается со строки-спейсера (\n) которая имеет
        // наибольший индекс. Чтобы плашка была видна, нужно прокрутить именно до неё.
        int maxIdx = -1;
        for (int i = 0; i < trimmed.size(); i++) {
            GuiMessage.Line line = trimmed.get(i);
            if (line.addedTime() != targetAddedTime) continue;
            maxIdx = i; // не break — берём последний (наибольший) индекс
        }

        if (maxIdx < 0) return;

        int maxScroll = Math.max(0, trimmed.size() - linesPerPage);
        int newPos = Math.min(maxIdx, maxScroll);
        acc.setChatScrollbarPos(newPos);

        cr$highlightAddedTime = targetAddedTime;
        cr$highlightStartMs = System.currentTimeMillis();
    }

    /**
     * Рисует синюю подсветку с затуханием для сообщения cr$highlightAddedTime.
     * Вызывается из chatremastered$renderHighlight (перед основным рендером чата).
     */
    private void cr$renderHighlight(GuiGraphics graphics, Minecraft mc) {
        if (cr$highlightAddedTime < 0) return;
        long elapsed = System.currentTimeMillis() - cr$highlightStartMs;
        if (elapsed >= HIGHLIGHT_DURATION_MS) { cr$highlightAddedTime = -1; return; }
        float progress = (float) elapsed / HIGHLIGHT_DURATION_MS;

        // Fade-in первые 20%, fade-out остальные 80%
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
        // chatBottom в scaled-единицах чата
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        // GUI-координата нижнего края чата
        int chatBottomGui = mc.getWindow().getGuiScaledHeight() - 40;
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
        int guiRight = Math.round((chatWidthPx + 8) * scale);

        for (int i = 0; i < Math.min(trimmed.size() - scrollPos, linesPerPage); i++) {
            int idx = i + scrollPos;
            if (idx >= trimmed.size()) break;
            GuiMessage.Line line = trimmed.get(idx);
            if (line.addedTime() != cr$highlightAddedTime) continue;
            // Пропускаем пустые спейсер-строки
            boolean[] hasChars = {false};
            line.content().accept((charIdx, style, codePoint) -> { hasChars[0] = true; return false; });
            if (!hasChars[0]) continue;
            // Переводим из scaled-координат чата в GUI-координаты
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

}