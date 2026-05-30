package dev.errnicraft.chatremastered.mixin;

import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.ChatRemasteredClient;
import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ImageCache;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.screens.ChatScreen;

import net.minecraft.util.FormattedCharSequence;
import java.util.List;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    private static final int IMG_LEFT_MARGIN = 2;
    private static final int IMG_VERT_PAD = 2;     // зазор снизу фото (от фото до строки выше)
    private static final int IMG_TOP_GAP  = 2;     // зазор сверху фото (от предыдущей строки)

    @Inject(method = "render", at = @At("TAIL"))
    private void chatremastered$renderImages(
            GuiGraphics guiGraphics, Font font, int ticks, int mouseX, int mouseY,
            boolean isChatting, boolean changeCursorOnInsertions,
            CallbackInfo ci
    ) {
        Minecraft mc = Minecraft.getInstance();
        List<ChatRemasteredStore.ImageMessage> msgs = ChatRemasteredStore.getMessageList();
        if (msgs.isEmpty()) return;

        float scale = mc.options.chatScale().get().floatValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);

        ChatComponentAccessor accessor = (ChatComponentAccessor) this;
        List<GuiMessage.Line> trimmed = accessor.getTrimmedMessages();
        int scrollPos = accessor.getChatScrollbarPos();
        int linesPerPage = ((ChatComponent)(Object) this).getLinesPerPage();

        int chatBottomGui = mc.getWindow().getGuiScaledHeight() - 40;
        int chatTopGui = chatBottomGui - (int)(linesPerPage * entryHeight * scale);

        // GUI-координаты для scissor и screenBounds
        final int chatTopScaled = chatBottom - linesPerPage * entryHeight;

        for (ChatRemasteredStore.ImageMessage msg : msgs) {
            if (msg.getDismissed()) continue;

            kotlin.Pair<Integer, Integer> size = ImageCache.INSTANCE.getSize(msg.getImageId());
            if (size == null) continue;  // карточка ещё не зарегистрирована

            int dispW = size.getFirst();
            int dispH = size.getSecond();

            Identifier tex = ImageCache.INSTANCE.getTexture(msg.getImageId()); // null пока грузит
            boolean isLoaded = tex != null;

            kotlin.Pair<Integer, Integer> orig = ImageCache.INSTANCE.getOrigSize(msg.getImageId());
            if (orig == null) continue;
            int origW = orig.getFirst();
            int origH = orig.getSecond();

            // Ищем строку ника — это наибольший индекс (самая нижняя строка) с нашим addedTime.
            // Строки \n идут после ника в тексте → они выше в чате → меньший индекс в trimmed.
            int nickLineIndex = -1;
            for (int i = trimmed.size() - 1; i >= 0; i--) {
                GuiMessage.Line line = trimmed.get(i);
                if (line.addedTime() == msg.getAddedTime()) {
                    nickLineIndex = i;
                    break;
                }
            }
            if (nickLineIndex == -1) { msg.setScreenBounds(0, 0, 0, 0); continue; }

            // Строки-отступы (\n) того же GuiMessage — они выше ника (меньший индекс).
            // Ищем самую верхнюю из них (минимальный индекс с тем же addedTime).
            int topBlankIndex = nickLineIndex;
            for (int i = nickLineIndex - 1; i >= 0; i--) {
                GuiMessage.Line line = trimmed.get(i);
                if (line.addedTime() == msg.getAddedTime()) {
                    topBlankIndex = i;
                } else {
                    break;
                }
            }

            int lineIndexFromBottom = topBlankIndex - scrollPos;
            if (lineIndexFromBottom < 0 || lineIndexFromBottom >= linesPerPage) {
                msg.setScreenBounds(0, 0, 0, 0);
                continue;
            }

            // Alpha как у ванильного чата
            float alpha;
            if (isChatting) {
                alpha = mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f;
            } else {
                double t = (ticks - msg.getAddedTime()) / 200.0;
                t = (1.0 - t) * 10.0;
                t = Math.max(0.0, Math.min(1.0, t));
                alpha = (float)(t * t) * (mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f);
                if (alpha <= 1e-5f) { msg.setScreenBounds(0, 0, 0, 0); continue; }
            }

            int alphaInt  = (int)(alpha * 255) << 24;
            int blitColor = alphaInt | 0x00FFFFFF;

            // Нижний край верхней пустой строки слота (в pose-relative scaled coords)
            int slotTopLineBottom = chatBottom - lineIndexFromBottom * entryHeight;

            // Оригинальная формула: фото рисуется выше верхней строки слота на dispH
            // IMG_VERT_PAD — зазор снизу (между фото и строкой выше), IMG_TOP_GAP — зазор сверху
            int imgBottom = slotTopLineBottom - IMG_VERT_PAD;
            int imgTop    = imgBottom - dispH + IMG_TOP_GAP;

            // Полностью вне видимой области — скрываем
            if (imgBottom <= chatTopScaled) { msg.setScreenBounds(0, 0, 0, 0); continue; }
            if (imgTop >= chatBottom)       { msg.setScreenBounds(0, 0, 0, 0); continue; }

            // Частичная видимость — clamp
            int clampedImgTop    = Math.max(imgTop,    chatTopScaled);
            int clampedImgBottom = Math.min(imgBottom, chatBottom);
            if (clampedImgBottom <= clampedImgTop) { msg.setScreenBounds(0, 0, 0, 0); continue; }

            int drawH = clampedImgBottom - clampedImgTop;

            // Scissor в GUI-координатах
            guiGraphics.enableScissor(
                    (int)(4 * scale), Math.max(0, chatTopGui),
                    (int)(4 * scale) + (int)(chatWidthPx * scale), chatBottomGui
            );

            // Применяем масштаб и сдвиг X (Y уже правильный через внешнюю трансляцию Gui)
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(scale, scale);
            guiGraphics.pose().translate(4.0f, 0.0f);

            if (!isLoaded) {
                boolean isDeleted = ImageCache.INSTANCE.isDeleted(msg.getImageId());
                boolean isError   = ImageCache.INSTANCE.isError(msg.getImageId());

                int bgColor;
                int borderColor;
                if (isDeleted) {
                    bgColor     = ((int)(alpha * 180) << 24) | 0x00110000;
                    borderColor = ((int)(alpha * 160) << 24) | 0x00662222;
                } else if (isError) {
                    bgColor     = ((int)(alpha * 180) << 24) | 0x00111100;
                    borderColor = ((int)(alpha * 160) << 24) | 0x00664400;
                } else {
                    bgColor     = ((int)(alpha * 200) << 24) | 0x00111111;
                    borderColor = ((int)(alpha * 160) << 24) | 0x00444444;
                }

                guiGraphics.fill(
                        IMG_LEFT_MARGIN, clampedImgTop,
                        IMG_LEFT_MARGIN + dispW, clampedImgBottom,
                        bgColor
                );
                // Рамка
                guiGraphics.fill(IMG_LEFT_MARGIN, clampedImgTop, IMG_LEFT_MARGIN + dispW, clampedImgTop + 1, borderColor);
                guiGraphics.fill(IMG_LEFT_MARGIN, clampedImgBottom - 1, IMG_LEFT_MARGIN + dispW, clampedImgBottom, borderColor);
                guiGraphics.fill(IMG_LEFT_MARGIN, clampedImgTop, IMG_LEFT_MARGIN + 1, clampedImgBottom, borderColor);
                guiGraphics.fill(IMG_LEFT_MARGIN + dispW - 1, clampedImgTop, IMG_LEFT_MARGIN + dispW, clampedImgBottom, borderColor);

                // ── Иконка по центру ──────────────────────────────────────────
                if (dispH >= 12) {
                    int cardCenterX = IMG_LEFT_MARGIN + dispW / 2;
                    int cardCenterY = imgTop + dispH / 2;

                    if (isError) {
                        String icon = "⚠";
                        int iconTint = ((int)(alpha * 255) << 24) | 0x00FFAA00;
                        float iconScale = dispH * 0.40f / 9.0f;
                        iconScale = Math.min(iconScale, dispW * 0.60f / mc.font.lineHeight);
                        iconScale = Math.max(iconScale, 1.0f);
                        int iconPxW = Math.round(mc.font.width(icon) * iconScale);
                        int iconPxH = Math.round(mc.font.lineHeight * iconScale);
                        int iconX = cardCenterX - iconPxW / 2;
                        int iconY = cardCenterY - iconPxH / 2;
                        if (iconY < clampedImgBottom && iconY + iconPxH > clampedImgTop) {
                            guiGraphics.pose().pushMatrix();
                            guiGraphics.pose().translate(iconX, iconY);
                            guiGraphics.pose().scale(iconScale, iconScale);
                            guiGraphics.drawString(mc.font, icon, 0, 0, iconTint, false);
                            guiGraphics.pose().popMatrix();
                        }
                    } else {
                        String icon = isDeleted ? "✗" : "🖼";
                        int iconTint = isDeleted
                                ? (((int)(alpha * 255) << 24) | 0x00AA4444) : (((int)(alpha * 255) << 24) | 0x00888888);

                        float iconScale = dispH * 0.40f / 9.0f;
                        iconScale = Math.min(iconScale, dispW * 0.60f / mc.font.lineHeight);
                        iconScale = Math.max(iconScale, 1.0f);

                        int iconPxW = Math.round(mc.font.width(icon) * iconScale);
                        int iconPxH = Math.round(mc.font.lineHeight * iconScale);
                        int iconX = cardCenterX - iconPxW / 2;
                        int iconY = cardCenterY - iconPxH / 2;

                        if (iconY < clampedImgBottom && iconY + iconPxH > clampedImgTop) {
                            guiGraphics.pose().pushMatrix();
                            guiGraphics.pose().translate(iconX, iconY);
                            guiGraphics.pose().scale(iconScale, iconScale);
                            guiGraphics.drawString(mc.font, icon, 0, 0, iconTint, false);
                            guiGraphics.pose().popMatrix();
                        }
                    }
                }
            } else {
                // ── Рендер загруженной текстуры — статика или GIF ──────────────
                boolean isGif = ImageCache.INSTANCE.isGif(msg.getImageId());

                if (isGif) {
                    dev.errnicraft.chatremastered.GifFrameEntry frame =
                            ImageCache.INSTANCE.getCurrentGifFrame(msg.getImageId());
                    if (frame != null) {
                        tex    = frame.getTextureId();
                        origW  = frame.getWidth();
                        origH  = frame.getHeight();
                    }
                }

                float scaleX = (float) dispW / origW;
                float scaleY = (float) dispH / origH;

                // Если верх фото обрезан (вышел за chatTopScaled), сдвигаем UV
                float vOffsetOrig = (imgTop < chatTopScaled)
                        ? (float)(chatTopScaled - imgTop) / scaleY
                        : 0f;
                vOffsetOrig = Math.max(0f, vOffsetOrig);
                int drawHOrig = Math.round(drawH / scaleY);

                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(IMG_LEFT_MARGIN, clampedImgTop);
                guiGraphics.pose().scale(scaleX, scaleY);

                guiGraphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        tex,
                        0, 0,
                        0.0f, vOffsetOrig,
                        origW, drawHOrig,
                        origW, origH,
                        blitColor
                );

                guiGraphics.pose().popMatrix();
            }

            guiGraphics.pose().popMatrix();
            guiGraphics.disableScissor();

            // screenBounds в GUI-пикселях для hit-test кликов
            int guiX0 = (int)((IMG_LEFT_MARGIN + 4) * scale);
            int guiX1 = (int)((IMG_LEFT_MARGIN + 4 + dispW) * scale);
            int guiY0 = chatBottomGui - (int)((chatBottom - clampedImgTop)    * scale);
            int guiY1 = chatBottomGui - (int)((chatBottom - clampedImgBottom) * scale);
            msg.setScreenBounds(guiX0, guiY0, guiX1, guiY1);

            // ── Кнопка Download (только если autoDownload=false) ─────────────
            if (!ChatRemasteredConfig.INSTANCE.getAutoDownload() && ImageCache.INSTANCE.getTexture(msg.getImageId()) != null) {
                ImageCache.DownloadState dlState = ImageCache.INSTANCE.getDownloadState(msg.getImageId());
                if (dlState != ImageCache.DownloadState.DONE) {
                    guiGraphics.enableScissor(
                            (int)(4 * scale), Math.max(0, chatTopGui),
                            (int)(4 * scale) + (int)(chatWidthPx * scale), chatBottomGui
                    );
                    guiGraphics.pose().pushMatrix();
                    guiGraphics.pose().scale(scale, scale);
                    guiGraphics.pose().translate(4.0f, 0.0f);

                    int cardCenterX = IMG_LEFT_MARGIN + dispW / 2;
                    int btnH = 11;
                    int btnY  = clampedImgBottom - btnH - 2;
                    int btnW  = Math.min(60, dispW - 4);
                    int btnX  = cardCenterX - btnW / 2;

                    if (btnY >= clampedImgTop && btnY + btnH <= clampedImgBottom && dispH >= 20) {
                        if (dlState == ImageCache.DownloadState.IN_PROGRESS) {
                            float progress = ImageCache.INSTANCE.getDownloadProgress(msg.getImageId());
                            int barBg   = ((int)(alpha * 160) << 24) | 0x00222222;
                            int barFill = ((int)(alpha * 220) << 24) | 0x0044AA44;
                            guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, barBg);
                            guiGraphics.fill(btnX, btnY, btnX + (int)(btnW * progress), btnY + btnH, barFill);

                            int pct = (int)(progress * 100);
                            String pctStr = pct + "%";
                            int textW = mc.font.width(pctStr);
                            int textX = btnX + btnW / 2 - textW / 2;
                            int textY = btnY + (btnH - mc.font.lineHeight) / 2;
                            int textColor = ((int)(alpha * 255) << 24) | 0x00FFFFFF;
                            guiGraphics.drawString(mc.font, pctStr, textX, textY, textColor, false);

                            long sizeB = ImageCache.INSTANCE.getFileSizeBytes(msg.getImageId());
                            if (sizeB > 0 && btnY + btnH + 2 < clampedImgBottom) {
                                String sizeStr = String.format("%.1f MB", sizeB / 1_048_576.0);
                                int sW = mc.font.width(sizeStr);
                                int sizeColor = ((int)(alpha * 180) << 24) | 0x00AAAAAA;
                                guiGraphics.drawString(mc.font, sizeStr,
                                        btnX + btnW / 2 - sW / 2, btnY + btnH + 2, sizeColor, false);
                            }
                        } else {
                            int iconColor = ((int)(alpha * 220) << 24) | 0x00DDDDDD;

                            String arrow = "↓";
                            int arrowW = mc.font.width(arrow);
                            float arrowScale = btnH / (float) mc.font.lineHeight;
                            arrowScale = Math.max(1f, Math.min(arrowScale, 2.0f));
                            int arrowPxW = Math.round(arrowW * arrowScale);
                            int arrowPxH = Math.round(mc.font.lineHeight * arrowScale);
                            int arrowX = cardCenterX - arrowPxW / 2;
                            int arrowY = clampedImgBottom - arrowPxH - 4;

                            if (arrowY >= clampedImgTop) {
                                guiGraphics.pose().pushMatrix();
                                guiGraphics.pose().translate(arrowX, arrowY);
                                guiGraphics.pose().scale(arrowScale, arrowScale);
                                guiGraphics.drawString(mc.font, arrow, 0, 0, iconColor, false);
                                guiGraphics.pose().popMatrix();
                            }

                            long sizeB = ImageCache.INSTANCE.getFileSizeBytes(msg.getImageId());
                            if (sizeB > 0) {
                                String sizeStr = String.format("%.1f MB", sizeB / 1_048_576.0);
                                int sW = mc.font.width(sizeStr);
                                int sizeColor = ((int)(alpha * 180) << 24) | 0x00AAAAAA;
                                int sizeY = arrowY + arrowPxH + 1;
                                if (sizeY + mc.font.lineHeight <= clampedImgBottom) {
                                    guiGraphics.drawString(mc.font, sizeStr,
                                            cardCenterX - sW / 2, sizeY, sizeColor, false);
                                }
                            }
                        }
                    }

                    guiGraphics.pose().popMatrix();
                    guiGraphics.disableScissor();
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void chatremastered$renderReplies(
            GuiGraphics guiGraphics, Font font, int ticks, int mouseX, int mouseY,
            boolean isChatting, boolean changeCursorOnInsertions,
            CallbackInfo ci2) {
        Minecraft mc = Minecraft.getInstance();

        ChatComponentAccessor accessor = (ChatComponentAccessor) this;
        List<GuiMessage.Line> trimmed = accessor.getTrimmedMessages();
        List<GuiMessage> allMessages = accessor.getAllMessages();
        int scrollPos = accessor.getChatScrollbarPos();
        int linesPerPage = ((ChatComponent)(Object) this).getLinesPerPage();

        float scale = mc.options.chatScale().get().floatValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
        int chatBottomGui = mc.getWindow().getGuiScaledHeight() - 40;
        int chatTopGui = chatBottomGui - (int)(linesPerPage * entryHeight * scale);

        // ── Рисуем reply-плашку над каждым сообщением (addedTime известен заранее из addReplyToChat) ──
        java.util.List<ChatRemasteredStore.ReplyMessage> allReplies = new java.util.ArrayList<>(ChatRemasteredStore.getRepliesList());
        for (ChatRemasteredStore.ReplyMessage reply : allReplies) {
            if (reply.getAddedTime() < 0) continue;  // ещё не привязано


            // В trimmedMessages: меньший индекс = нижняя строка (ближе к вводу), больший = верхняя.
            // Сообщение "\n<Nick> text" разбивается на две строки:
            //   строка ника "<Nick> text" — нижняя → МЕНЬШИЙ индекс
            //   строка-отступ "\n"        — верхняя → БОЛЬШИЙ индекс (там место для плашки)
            // Ищем строку ника — наименьший индекс с нужным addedTime.
            int nickLineIdx = -1;
            for (int i = 0; i < trimmed.size(); i++) {
                if (trimmed.get(i).addedTime() == reply.getAddedTime()) {
                    nickLineIdx = i;
                    break;
                }
            }
            if (nickLineIdx < 0) continue;

            // Ищем верхнюю пустую строку-отступ — наибольший индекс с тем же addedTime.
            int nlLineIndex = nickLineIdx;
            for (int i = nickLineIdx + 1; i < trimmed.size(); i++) {
                if (trimmed.get(i).addedTime() == reply.getAddedTime()) {
                    nlLineIndex = i;
                } else {
                    break;
                }
            }

            int lineIndexFromBottom = nlLineIndex - scrollPos;
            if (lineIndexFromBottom < 0 || lineIndexFromBottom >= linesPerPage) continue;

            // Alpha как у ванильного чата
            float alpha;
            if (isChatting) {
                alpha = mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f;
            } else {
                double t = (ticks - reply.getAddedTime()) / 200.0;
                t = (1.0 - t) * 10.0;
                t = Math.max(0.0, Math.min(1.0, t));
                alpha = (float)(t * t) * (mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f);
                if (alpha <= 1e-5f) continue;
            }

            // Координаты строки 

            int nlLineBottom = chatBottom - lineIndexFromBottom * entryHeight;
            int nlLineTop    = nlLineBottom - entryHeight;

            // Плашка занимает строку 

            int REPLY_BAR_H = entryHeight;
            int barBottom = nlLineBottom;
            int barTop    = nlLineTop;

            // Клипуем — плашка должна быть в видимой области чата
            int chatTopScaled2 = chatBottom - linesPerPage * entryHeight;
            if (barBottom <= chatTopScaled2) continue;

            int alphaInt = (int)(alpha * 255);

            // Переводим в GUI-координаты
            int guiBarTop    = chatBottomGui - (int)((chatBottom - barTop)    * scale);
            int guiBarBottom = chatBottomGui - (int)((chatBottom - barBottom) * scale);
            // Плашка от x=0 до правого края чата.
            // Правый край берём точно как у scissor чата: (int)(4*scale) + (int)(chatWidthPx*scale)
            int guiBarLeft   = 0;
            // Используем (chatWidthPx+8)*scale — точно как cr$drawLineHighlight в ChatScreenMixin
            int guiBarRight  = Math.round((chatWidthPx + 8) * scale);
            int guiBarH      = Math.max(1, guiBarBottom - guiBarTop);

            if (guiBarTop < chatTopGui || guiBarBottom > chatBottomGui) continue;

            // Фон плашки — тёмно-серый нейтральный, видно и при hover-highlight
            int bgAlpha = (int)(alpha * 0x88);
            guiGraphics.fill(guiBarLeft, guiBarTop, guiBarRight, guiBarBottom,
                    (bgAlpha << 24) | 0x2A2A2A);
            // Левый акцент — синяя полоска 2px (маркер ответа)
            int accentAlpha = (int)(alpha * 0xFF);
            guiGraphics.fill(guiBarLeft, guiBarTop, guiBarLeft + Math.round(2 * scale), guiBarBottom,
                    (accentAlpha << 24) | 0x3399EE);

            // Текст плашки в GUI-пространстве через pose scale
            String replyToSender = stripHeadPlaceholders(reply.getReplyToSender());
            String replyToText   = stripHeadPlaceholders(reply.getReplyToText());
            String replyToImgId  = reply.getReplyToImageId();

            // Ищем addedTime оригинального сообщения (кэшируем в reply.replyToAddedTime)
            if (reply.getReplyToAddedTime() < 0) {
                if (!replyToImgId.isEmpty()) {
                    // Для фото-ответа: ищем по imageId в ImageMessage Store
                    for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
                        if (imgMsg.getImageId().equals(replyToImgId) && imgMsg.getAddedTime() >= 0) {
                            reply.setReplyToAddedTime(imgMsg.getAddedTime());
                            break;
                        }
                    }
                } else {
                    // Для текст-ответа: ищем в allMessages по тексту и отправителю
                    // Используем contains вместо equals — для серверов без подписи чата
                    String expectedPrefix = "<" + replyToSender + "> ";
                    for (GuiMessage gm : allMessages) {
                        String gmText = stripObjectContents(gm.content());
                        String stripped = gmText.startsWith("\n") ? gmText.substring(1) : gmText;
                        if (stripped.startsWith(expectedPrefix)) {
                            String msgBody = stripped.substring(expectedPrefix.length());
                            if (msgBody.equals(replyToText) || msgBody.contains(replyToText) || replyToText.contains(msgBody)) {
                                reply.setReplyToAddedTime(gm.addedTime());
                                break;
                            }
                        }
                    }
                }
            }

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(scale, scale);

            int textX = 4 + 4;  // 4px chat offset + 4px indent от левого края
            int textY = barTop + (REPLY_BAR_H - mc.font.lineHeight) / 2 + 1;
            int maxTextW = chatWidthPx - 10;
            int textAlpha = alphaInt << 24;

            String label;
            if (!replyToImgId.isEmpty()) {
                // ── Ответ на фото: ↩ + мини-превью + синий ник + текст ──
                String arrowStr = "\u21A9 ";
                guiGraphics.drawString(mc.font, arrowStr, textX, textY, textAlpha | 0xFFFFFF, false);
                int curX = textX + mc.font.width(arrowStr);

                // Мини-превью фото (вписываем в 10x10 с сохранением пропорций)
                net.minecraft.resources.Identifier thumbTex = ImageCache.INSTANCE.getTexture(replyToImgId);
                if (thumbTex != null) {
                    int maxW = 10, maxH = 10;
                    kotlin.Pair<Integer,Integer> ts = ImageCache.INSTANCE.getTexSize(replyToImgId);
                    int texW = ts != null && ts.getFirst() > 0  ? ts.getFirst()  : maxW;
                    int texH = ts != null && ts.getSecond() > 0 ? ts.getSecond() : maxH;
                    float s = Math.min((float) maxW / texW, (float) maxH / texH);
                    int drawW = Math.round(texW * s);
                    int drawH = Math.round(texH * s);
                    int photoY = barTop + (REPLY_BAR_H - drawH) / 2;
                    guiGraphics.pose().pushMatrix();
                    guiGraphics.pose().translate(curX, photoY);
                    guiGraphics.pose().scale(s, s);
                    guiGraphics.blit(
                            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                            thumbTex,
                            0, 0,
                            0f, 0f,
                            texW, texH,
                            texW, texH,
                            textAlpha | 0xFFFFFF
                    );
                    guiGraphics.pose().popMatrix();
                    curX += drawW + 3;
                }

                // Ник с цветом: приоритеты:
                // 1. reply.senderComponent — из ReplyChatPacket (сервер передаёт напрямую)
                // 2. ImageMessage.senderComponent — из ImageChatPacket
                // 3. allMessages TranslatableContents — fallback для обычного чата
                net.minecraft.network.chat.Component senderColorComp = reply.getSenderComponent();
                if (senderColorComp == null || stripObjectContents(senderColorComp).isBlank()) {
                    senderColorComp = null;
                    for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
                        if (imgMsg.getImageId().equals(replyToImgId)) {
                            senderColorComp = imgMsg.getSenderComponent();
                            break;
                        }
                    }
                }
                // Fallback: ищем цветной компонент в allMessages через TranslatableContents
                if (senderColorComp == null && reply.getReplyToAddedTime() >= 0) {
                    for (GuiMessage gm : allMessages) {
                        if (gm.addedTime() == reply.getReplyToAddedTime()) {
                            net.minecraft.network.chat.Component c = gm.content();
                            // Убираем \n-wrapper
                            if (!c.getSiblings().isEmpty() && stripObjectContents(c.getSiblings().get(0)).equals("\n") && c.getSiblings().size() >= 2)
                                c = c.getSiblings().get(1);
                            // TranslatableContents: args[0] — ник-компонент с цветом
                            if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                                Object[] args = tc.getArgs();
                                if (args.length >= 1 && args[0] instanceof net.minecraft.network.chat.Component nc) {
                                    senderColorComp = nc;
                                }
                            }
                            break;
                        }
                    }
                }

                // Ник + текст
                String suffix = !replyToText.isEmpty() ? replyToText : "";
                int remW = maxTextW - (curX - textX);
                net.minecraft.network.chat.MutableComponent labelComp;
                if (senderColorComp != null) {
                    labelComp = net.minecraft.network.chat.Component.empty();
                    // stripObjectContents убирает PlayerSprite (Chat Heads) из компонента ника
                    labelComp.append(stripObjectContentsComponent(senderColorComp));
                    if (!suffix.isEmpty()) labelComp.append(net.minecraft.network.chat.Component.literal("\u00A7r\u00A77: " + suffix));
                } else if (!replyToSender.isEmpty()) {
                    String lb = "\u00A7b" + replyToSender + "\u00A7r\u00A77" + (suffix.isEmpty() ? "" : ": " + suffix);
                    labelComp = net.minecraft.network.chat.Component.literal(lb);
                } else {
                    labelComp = net.minecraft.network.chat.Component.literal("\u00A77" + suffix);
                }
                label = stripObjectContents(labelComp);
                if (mc.font.width(labelComp) > remW) {
                    String plain = label;
                    String ellipsis = "\u2026";
                    int ellW = mc.font.width(ellipsis);
                    while (!plain.isEmpty() && mc.font.width(plain) + ellW > remW)
                        plain = plain.substring(0, plain.length() - 1);
                    label = plain + ellipsis;
                }
                guiGraphics.drawString(mc.font, net.minecraft.network.chat.Component.literal(label), curX, textY, textAlpha | 0xFFFFFF, false);
            } else {
                // ── Ответ на текст: ↩ + только текст (ник не нужен — он уже есть в самом сообщении) ──
                String arrowStr = "\u21A9 ";
                guiGraphics.drawString(mc.font, arrowStr, textX, textY, textAlpha | 0xFFFFFF, false);
                int curX2 = textX + mc.font.width(arrowStr);

                String suffix = !replyToText.isEmpty() ? replyToText : "";
                int remW2 = maxTextW - (curX2 - textX);
                net.minecraft.network.chat.MutableComponent labelComp2 =
                        net.minecraft.network.chat.Component.literal("\u00A77" + suffix);
                label = stripObjectContents(labelComp2);
                if (mc.font.width(labelComp2) > remW2) {
                    String plain2 = label;
                    String ellipsis2 = "\u2026";
                    int ellW2 = mc.font.width(ellipsis2);
                    while (!plain2.isEmpty() && mc.font.width(plain2) + ellW2 > remW2)
                        plain2 = plain2.substring(0, plain2.length() - 1);
                    label = plain2 + ellipsis2;
                }
                guiGraphics.drawString(mc.font, net.minecraft.network.chat.Component.literal(label), curX2, textY, textAlpha | 0xFFFFFF, false);
            }

            guiGraphics.pose().popMatrix();
        }
    }

    /**
     * Fullscreen открытого чата: перехватываем getLinesPerPage только внутри render,
     * не трогая focused-ветку в других контекстах.
     */
    @ModifyReturnValue(method = "getLinesPerPage", at = @At("RETURN"))
    private int cr$modifyLinesPerPage(int original) {
        Minecraft mc = Minecraft.getInstance();
        // instanceof ChatScreen надёжнее чем isChatFocused() —
        // isChatFocused() читает поле которое выставляется ПОСЛЕ rescaleChat(),
        // а screen уже != null в момент открытия чата
        boolean focused = mc.screen instanceof ChatScreen;

        if (focused) {
            if (!ChatRemasteredConfig.INSTANCE.getFullscreenChat()) return original;
            float scale = (float) mc.options.chatScale().get().doubleValue();
            if (scale <= 0f) return original;
            double lineSpacing = mc.options.chatLineSpacing().get();
            int lineH = (int) Math.round(9.0 * (lineSpacing + 1.0));
            if (lineH <= 0) return original;
            int chatBottomScaled = (int) ((mc.getWindow().getGuiScaledHeight() - 40) / scale);
            int availableH = chatBottomScaled - 2;
            return Math.max(original, availableH / lineH);
        } else {
            return ChatRemasteredConfig.INSTANCE.getClosedChatLines();
        }
    }



    // Возвращает копию компонента без ObjectContents (PlayerSprite и др.) — для рендера.
    private static net.minecraft.network.chat.MutableComponent stripObjectContentsComponent(net.minecraft.network.chat.Component component) {
        net.minecraft.network.chat.ComponentContents contents = component.getContents();
        net.minecraft.network.chat.MutableComponent copy;
        if (contents instanceof net.minecraft.network.chat.contents.PlainTextContents plain) {
            copy = net.minecraft.network.chat.Component.literal(plain.text());
        } else if (contents instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            copy = net.minecraft.network.chat.Component.translatable(tc.getKey(), tc.getArgs());
        } else if (contents instanceof net.minecraft.network.chat.contents.ObjectContents) {
            copy = net.minecraft.network.chat.Component.empty(); // убираем PlayerSprite
        } else {
            copy = net.minecraft.network.chat.Component.empty();
        }
        copy.setStyle(component.getStyle());
        for (net.minecraft.network.chat.Component sib : component.getSiblings()) {
            copy.append(stripObjectContentsComponent(sib));
        }
        return copy;
    }

    // Извлекает текст из компонента, пропуская ObjectContents (PlayerSprite и др.)
    // чтобы не получать "[Player head]" в строке (актуально для 1.21.9+ с Chat Heads).
    private static String stripObjectContents(net.minecraft.network.chat.Component component) {
        StringBuilder sb = new StringBuilder();
        collectPlainText(component, sb);
        return stripHeadPlaceholders(sb.toString());
    }

    /** Удаляет "[Player head]", "[unknown player head]" и подобные паттерны из строки. */
    private static String stripHeadPlaceholders(String text) {
        return text.replaceAll("\\[[^\\]]*\\s*head\\]", "").trim();
    }

    private static void collectPlainText(net.minecraft.network.chat.Component component, StringBuilder sb) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents plain) {
            sb.append(plain.text());
        } else if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            for (Object arg : tc.getArgs()) {
                if (arg instanceof net.minecraft.network.chat.Component c) {
                    collectPlainText(c, sb);
                } else {
                    sb.append(arg);
                }
            }
        }
        // ObjectContents (PlayerSprite и др.) — намеренно пропускаем
        for (net.minecraft.network.chat.Component sibling : component.getSiblings()) {
            collectPlainText(sibling, sb);
        }
    }

}