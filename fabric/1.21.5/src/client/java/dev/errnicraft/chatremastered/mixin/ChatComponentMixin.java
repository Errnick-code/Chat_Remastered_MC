package dev.errnicraft.chatremastered.mixin;

import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.ChatRemasteredClient;
import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ImageCache;
import dev.errnicraft.chatremastered.client.ResolvedSkinRemotePlayer;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
    private static final int IMG_VERT_PAD = 2;
    private static final int IMG_TOP_GAP  = 2;

    @Inject(method = "render", at = @At("TAIL"))
    private void chatremastered$renderImages(
            GuiGraphics guiGraphics, int ticks, int mouseX, int mouseY,
            boolean isChatting,
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

        final int chatTopScaled = chatBottom - linesPerPage * entryHeight;

        for (ChatRemasteredStore.ImageMessage msg : msgs) {
            if (msg.getDismissed()) {
                msg.setScreenBounds(0, 0, 0, 0);
                msg.clearRowCardBounds();
                continue;
            }

            String activeImageId = msg.getActiveStripImageId();

            dev.errnicraft.chatremastered.IntPair size = ImageCache.getSize(msg.getImageId());
            if (size == null) {

                msg.setScreenBounds(0, 0, 0, 0);
                msg.clearRowCardBounds();
                continue;
            }

            int dispW = size.getFirst();
            int dispH = size.getSecond();

            ResourceLocation tex = ImageCache.getTexture(activeImageId);
            boolean isLoaded = tex != null;

            dev.errnicraft.chatremastered.IntPair orig = ImageCache.getOrigSize(activeImageId);
            if (orig == null) {
                msg.setScreenBounds(0, 0, 0, 0);
                msg.clearRowCardBounds();
                continue;
            }
            int origW = orig.getFirst();
            int origH = orig.getSecond();

            int nickLineIndex = -1;
            for (int i = trimmed.size() - 1; i >= 0; i--) {
                GuiMessage.Line line = trimmed.get(i);
                if (line.addedTime() == msg.getAddedTime()) {
                    nickLineIndex = i;
                    break;
                }
            }
            if (nickLineIndex == -1) { msg.setScreenBounds(0, 0, 0, 0); msg.clearRowCardBounds(); continue; }

            boolean hasReply = ChatRemasteredStore.getReplyForAddedTime(msg.getAddedTime()) != null;
            int blockMinForImg = nickLineIndex;
            for (int i = nickLineIndex - 1; i >= 0; i--) {
                GuiMessage.Line line = trimmed.get(i);
                if (line.addedTime() == msg.getAddedTime()) {
                    blockMinForImg = i;
                } else {
                    break;
                }
            }
            int imageSlotIndex = blockMinForImg;
            if (imageSlotIndex < 0 || imageSlotIndex > (hasReply ? nickLineIndex - 2 : nickLineIndex - 1)) {
                msg.setScreenBounds(0, 0, 0, 0);
                msg.clearRowCardBounds();
                continue;
            }

            int lineIndexFromBottom = imageSlotIndex - scrollPos;

            if (lineIndexFromBottom < -64 || lineIndexFromBottom >= linesPerPage) {
                msg.setScreenBounds(0, 0, 0, 0);
                msg.clearRowCardBounds();
                continue;
            }

            float alpha;
            if (isChatting) {
                alpha = mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f;
            } else {
                double t = (ticks - msg.getAddedTime()) / 200.0;
                t = (1.0 - t) * 10.0;
                t = Math.max(0.0, Math.min(1.0, t));
                alpha = (float)(t * t) * (mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f);
                if (alpha <= 1e-5f) { msg.setScreenBounds(0, 0, 0, 0); msg.clearRowCardBounds(); continue; }
            }

            int alphaInt  = (int)(alpha * 255) << 24;
            int blitColor = alphaInt | 0x00FFFFFF;

            int slotTopLineBottom = chatBottom - lineIndexFromBottom * entryHeight;

            int imgBottom = slotTopLineBottom - IMG_VERT_PAD;
            int imgTop    = imgBottom - dispH + IMG_TOP_GAP;

            if (imgBottom <= chatTopScaled) { msg.setScreenBounds(0, 0, 0, 0); msg.clearRowCardBounds(); continue; }
            if (imgTop >= chatBottom)       { msg.setScreenBounds(0, 0, 0, 0); msg.clearRowCardBounds(); continue; }

            int clampedImgTop    = Math.max(imgTop,    chatTopScaled);
            int clampedImgBottom = Math.min(imgBottom, chatBottom);
            if (clampedImgBottom <= clampedImgTop) { msg.setScreenBounds(0, 0, 0, 0); msg.clearRowCardBounds(); continue; }

            int drawH = clampedImgBottom - clampedImgTop;

            guiGraphics.enableScissor(
                    (int)(4 * scale) + 1, Math.max(0, chatTopGui),
                    (int)(4 * scale) + (int)(chatWidthPx * scale), chatBottomGui
            );

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(scale, scale);
            guiGraphics.pose().translate(4.0f, 0.0f);

            boolean groupRowMode = msg.isGroup() && ChatRemasteredConfig.getGroupPhotosRowMode();

            if (groupRowMode) {

                guiGraphics.pose().popMatrix();
                guiGraphics.disableScissor();

                int guiX0 = (int)((IMG_LEFT_MARGIN + 4) * scale);
                int guiXW = (int)(dispW * scale);
                int guiY0 = chatBottomGui - (int)((chatBottom - clampedImgTop)    * scale);
                int guiY1 = chatBottomGui - (int)((chatBottom - clampedImgBottom) * scale);
                int chatRightGui = (int) ((4 + chatWidthPx) * scale);

                java.util.List<String> allGroupIds = new java.util.ArrayList<>();
                allGroupIds.add(msg.getImageId());
                allGroupIds.addAll(msg.getGroupImageIds());

                chatremastered$renderGroupRow(guiGraphics, mc, msg, allGroupIds, activeImageId,
                        dispW, guiXW, guiX0, guiY0, guiY1, chatTopGui, chatBottomGui, chatRightGui,
                        alpha, alphaInt, mouseX, mouseY);

                int[] headBounds = msg.getRowCardBounds().get(msg.getImageId());
                if (headBounds != null) {
                    msg.setScreenBounds(headBounds[0], headBounds[1], headBounds[2], headBounds[3]);
                } else {
                    msg.setScreenBounds(0, 0, 0, 0);
                }
                continue;
            }

            if (!isLoaded) {
                boolean isDeleted = ImageCache.isDeleted(activeImageId);
                boolean isError   = ImageCache.isError(activeImageId);

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

                guiGraphics.fill(IMG_LEFT_MARGIN, clampedImgTop, IMG_LEFT_MARGIN + dispW, clampedImgTop + 1, borderColor);
                guiGraphics.fill(IMG_LEFT_MARGIN, clampedImgBottom - 1, IMG_LEFT_MARGIN + dispW, clampedImgBottom, borderColor);
                guiGraphics.fill(IMG_LEFT_MARGIN, clampedImgTop, IMG_LEFT_MARGIN + 1, clampedImgBottom, borderColor);
                guiGraphics.fill(IMG_LEFT_MARGIN + dispW - 1, clampedImgTop, IMG_LEFT_MARGIN + dispW, clampedImgBottom, borderColor);

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

                boolean isGif = ImageCache.isGif(activeImageId);

                if (isGif) {
                    dev.errnicraft.chatremastered.ImageCache.GifFrameEntry frame =
                            ImageCache.getCurrentGifFrame(activeImageId);
                    if (frame != null) {
                        tex    = frame.getTextureId();
                        origW  = frame.getWidth();
                        origH  = frame.getHeight();
                    }
                }

                float scaleX = (float) dispW / origW;
                float scaleY = (float) dispH / origH;

                float vOffsetOrig = (imgTop < chatTopScaled)
                        ? (float)(chatTopScaled - imgTop) / scaleY
                        : 0f;
                vOffsetOrig = Math.max(0f, vOffsetOrig);
                int drawHOrig = Math.round(drawH / scaleY);

                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(IMG_LEFT_MARGIN, clampedImgTop);
                guiGraphics.pose().scale(scaleX, scaleY);

                guiGraphics.blit(
                        RenderType::guiTextured,
                        tex,
                        0, 0,
                        0.0f, vOffsetOrig,
                        origW, drawHOrig,
                        origW, origH,
                        blitColor
                );

                guiGraphics.pose().popMatrix();

                int guiX0Hover = (int)((IMG_LEFT_MARGIN + 4) * scale);
                int guiX1Hover = (int)((IMG_LEFT_MARGIN + 4 + dispW) * scale);
                int guiY0Hover = chatBottomGui - (int)((chatBottom - clampedImgTop)    * scale);
                int guiY1Hover = chatBottomGui - (int)((chatBottom - clampedImgBottom) * scale);
                boolean isHovered = mouseX >= guiX0Hover && mouseX < guiX1Hover
                        && mouseY >= guiY0Hover && mouseY < guiY1Hover;

                int imgBorderColor = isHovered
                        ? (alphaInt | 0x00CCCCCC)
                        : (alphaInt | 0x002E2E2E);

                guiGraphics.fill(IMG_LEFT_MARGIN, clampedImgTop, IMG_LEFT_MARGIN + dispW, clampedImgTop + 1, imgBorderColor);
                guiGraphics.fill(IMG_LEFT_MARGIN, clampedImgBottom - 1, IMG_LEFT_MARGIN + dispW, clampedImgBottom, imgBorderColor);
                guiGraphics.fill(IMG_LEFT_MARGIN, clampedImgTop, IMG_LEFT_MARGIN + 1, clampedImgBottom, imgBorderColor);
                guiGraphics.fill(IMG_LEFT_MARGIN + dispW - 1, clampedImgTop, IMG_LEFT_MARGIN + dispW, clampedImgBottom, imgBorderColor);
            }

            boolean overlayIsDeleted = ImageCache.isDeleted(activeImageId);
            boolean overlayIsError   = ImageCache.isError(activeImageId);
            if (!overlayIsDeleted && !overlayIsError) {
                boolean isOwn = mc.player != null && mc.player.getGameProfile().getName().equals(msg.getSender());
                boolean isUploading = isOwn && ImageCache.isUploading(activeImageId);
                boolean isDownloading = !isOwn
                        && ImageCache.getDownloadState(activeImageId) == ImageCache.DownloadState.IN_PROGRESS;
                String overlayImageId = activeImageId;

                if (!isUploading && !isDownloading && msg.isGroup()) {
                    java.util.List<String> groupIds = new java.util.ArrayList<>();
                    groupIds.add(msg.getImageId());
                    groupIds.addAll(msg.getGroupImageIds());
                    for (String gid : groupIds) {
                        if (gid.equals(activeImageId)) continue;
                        if (ImageCache.isDeleted(gid) || ImageCache.isError(gid)) continue;
                        boolean gUploading = isOwn && ImageCache.isUploading(gid);
                        boolean gDownloading = !isOwn
                                && ImageCache.getDownloadState(gid) == ImageCache.DownloadState.IN_PROGRESS;
                        if (gUploading || gDownloading) {
                            isUploading = gUploading;
                            isDownloading = gDownloading;
                            overlayImageId = gid;
                            break;
                        }
                    }
                }

                if (isUploading || isDownloading) {
                    float progress = isUploading
                            ? ImageCache.getUploadProgress(overlayImageId)
                            : ImageCache.getDownloadProgress(overlayImageId);
                    String arrow = isUploading ? "↑" : "↓";
                    String overlayText = arrow + Math.round(progress * 100f) + "%";
                    int overlayColor = ((int) (alpha * 255) << 24) | 0x00CCCCCC;
                    int textW = mc.font.width(overlayText);
                    int overlayX = IMG_LEFT_MARGIN + (dispW - textW) / 2;
                    float fracFromBottom = 0.20f;
                    int overlayY = imgTop + dispH - Math.round(dispH * fracFromBottom) - mc.font.lineHeight / 2;
                    if (overlayY < clampedImgBottom && overlayY + mc.font.lineHeight > clampedImgTop) {
                        guiGraphics.drawString(mc.font, overlayText, overlayX, overlayY, overlayColor, false);
                    }
                }
            }

            if (msg.isGroup()) {
                String posText = (msg.getStripScrollOffset() + 1) + "/" + msg.getGroupSize();
                int posColor = (alphaInt) | 0x00FFFFFF;
                int posBgColor = ((int) (alpha * 160) << 24) | 0x00000000;
                int posTextW = mc.font.width(posText);
                int posX = IMG_LEFT_MARGIN + dispW - posTextW - 3;
                int posY = clampedImgBottom - mc.font.lineHeight - 2;
                if (posY < clampedImgBottom && posY + mc.font.lineHeight > clampedImgTop) {
                    guiGraphics.fill(posX - 2, posY - 1, posX + posTextW + 2, posY + mc.font.lineHeight + 1, posBgColor);
                    guiGraphics.drawString(mc.font, posText, posX, posY, posColor, false);
                }
            }

            guiGraphics.pose().popMatrix();
            guiGraphics.disableScissor();

            int guiX0 = (int)((IMG_LEFT_MARGIN + 4) * scale);
            int guiX1 = (int)((IMG_LEFT_MARGIN + 4 + dispW) * scale);
            int guiY0 = chatBottomGui - (int)((chatBottom - clampedImgTop)    * scale);
            int guiY1 = chatBottomGui - (int)((chatBottom - clampedImgBottom) * scale);
            msg.setScreenBounds(guiX0, guiY0, guiX1, guiY1);
            msg.clearRowCardBounds();

        }
    }

    private static final int GROUP_ROW_GAP = 4;

    private void chatremastered$renderGroupRow(
            GuiGraphics guiGraphics, Minecraft mc, ChatRemasteredStore.ImageMessage msg,
            List<String> allIds, String activeImageId, int dispW, int guiXW,
            int startGuiX, int guiY0, int guiY1, int chatTopGui, int chatBottomGui, int chatRightGui,
            float alpha, int alphaInt, int mouseX, int mouseY) {

        msg.clearRowCardBounds();
        if (allIds.isEmpty()) return;

        int cardH = guiY1 - guiY0;
        if (cardH <= 0) return;

        int gap = Math.round(GROUP_ROW_GAP * (float) guiXW / Math.max(dispW, 1));

        int[] cardWidths = new int[allIds.size()];
        double[] cardWidthsExact = new double[allIds.size()];
        double runningX = 0.0;
        for (int i = 0; i < allIds.size(); i++) {
            double exactW;
            if (i == 0) {
                exactW = guiXW;
            } else {
                dev.errnicraft.chatremastered.IntPair otherOrig = ImageCache.getOrigSize(allIds.get(i));
                double aspect = (otherOrig != null && otherOrig.getSecond() > 0)
                        ? (double) otherOrig.getFirst() / otherOrig.getSecond()
                        : (double) guiXW / Math.max(cardH, 1);
                exactW = Math.max(1.0, cardH * aspect);
            }
            cardWidthsExact[i] = exactW;
            double nextRunningX = runningX + exactW;
            cardWidths[i] = (int) Math.round(nextRunningX) - (int) Math.round(runningX);
            runningX = nextRunningX;
            if (i < allIds.size() - 1) runningX += gap;
        }
        int totalRowW = (int) Math.round(runningX);

        int availW = chatRightGui - startGuiX;
        int maxScrollX = Math.max(0, totalRowW - Math.max(0, availW));
        if (msg.getRowScrollX() > maxScrollX) msg.setRowScrollX(maxScrollX);

        guiGraphics.enableScissor(startGuiX, Math.max(0, chatTopGui), chatRightGui, chatBottomGui);

        int cardX = startGuiX - msg.getRowScrollX();

        for (int i = 0; i < allIds.size(); i++) {
            String id = allIds.get(i);
            int cardW = cardWidths[i];

            if (cardX + cardW < 0) { cardX += cardW + gap; continue; }
            if (cardX >= chatRightGui) break;

            boolean isLoadedCard = ImageCache.getTexture(id) != null;
            boolean isDeleted = ImageCache.isDeleted(id);
            boolean isError = ImageCache.isError(id);

            if (!isLoadedCard || isDeleted || isError) {
                int bgColor;
                int plColor;
                if (isDeleted) {
                    bgColor = ((int) (alpha * 180) << 24) | 0x00110000;
                    plColor = ((int) (alpha * 160) << 24) | 0x00662222;
                } else if (isError) {
                    bgColor = ((int) (alpha * 180) << 24) | 0x00111100;
                    plColor = ((int) (alpha * 160) << 24) | 0x00664400;
                } else {
                    bgColor = ((int) (alpha * 200) << 24) | 0x00111111;
                    plColor = ((int) (alpha * 160) << 24) | 0x00444444;
                }
                guiGraphics.fill(cardX, guiY0, cardX + cardW, guiY1, bgColor);

                if (cardH >= 12) {
                    String icon;
                    int iconTint;
                    if (isError) {
                        icon = "⚠";
                        iconTint = ((int) (alpha * 255) << 24) | 0x00FFAA00;
                    } else if (isDeleted) {
                        icon = "✗";
                        iconTint = ((int) (alpha * 255) << 24) | 0x00AA4444;
                    } else {
                        icon = "🖼";
                        iconTint = ((int) (alpha * 255) << 24) | 0x00888888;
                    }
                    float iconScale = cardH * 0.40f / 9.0f;
                    iconScale = Math.min(iconScale, cardW * 0.60f / mc.font.lineHeight);
                    iconScale = Math.max(iconScale, 1.0f);
                    int iconPxW = Math.round(mc.font.width(icon) * iconScale);
                    int iconPxH = Math.round(mc.font.lineHeight * iconScale);
                    int iconX = cardX + (cardW - iconPxW) / 2;
                    int iconY = guiY0 + (cardH - iconPxH) / 2;
                    guiGraphics.pose().pushMatrix();
                    guiGraphics.pose().translate(iconX, iconY);
                    guiGraphics.pose().scale(iconScale, iconScale);
                    guiGraphics.drawString(mc.font, icon, 0, 0, iconTint, false);
                    guiGraphics.pose().popMatrix();
                }

                if (!isDeleted && !isError) {
                    boolean isOwnPh = mc.player != null && mc.player.getGameProfile().getName().equals(msg.getSender());
                    boolean isUploadingPh = isOwnPh && ImageCache.isUploading(id);
                    boolean isDownloadingPh = !isOwnPh
                            && ImageCache.getDownloadState(id) == ImageCache.DownloadState.IN_PROGRESS;
                    if (isUploadingPh || isDownloadingPh) {
                        float progressPh = isUploadingPh ? ImageCache.getUploadProgress(id) : ImageCache.getDownloadProgress(id);
                        String arrowPh = isUploadingPh ? "↑" : "↓";
                        String overlayTextPh = arrowPh + Math.round(progressPh * 100f) + "%";
                        int overlayColorPh = ((int) (alpha * 255) << 24) | 0x00CCCCCC;
                        int textWPh = mc.font.width(overlayTextPh);
                        int overlayXPh = cardX + (cardW - textWPh) / 2;
                        int overlayYPh = guiY1 - Math.round(cardH * 0.20f) - mc.font.lineHeight / 2;
                        guiGraphics.drawString(mc.font, overlayTextPh, overlayXPh, overlayYPh, overlayColorPh, false);
                    }
                }
            } else {
                ResourceLocation tex = ImageCache.getTexture(id);
                dev.errnicraft.chatremastered.IntPair orig = ImageCache.getOrigSize(id);
                if (tex != null && orig != null) {
                    boolean isGif = ImageCache.isGif(id);
                    int origW = orig.getFirst();
                    int origH = orig.getSecond();
                    if (isGif) {
                        var frame = ImageCache.getCurrentGifFrame(id);
                        if (frame != null) {
                            tex = frame.getTextureId();
                            origW = frame.getWidth();
                            origH = frame.getHeight();
                        }
                    }
                    guiGraphics.pose().pushMatrix();
                    guiGraphics.pose().translate(cardX, guiY0);
                    guiGraphics.pose().scale((float) cardW / origW, (float) cardH / origH);
                    guiGraphics.blit(
                            RenderType::guiTextured,
                            tex,
                            0, 0,
                            0.0f, 0.0f,
                            origW, origH,
                            origW, origH,
                            (alphaInt | 0x00FFFFFF)
                    );
                    guiGraphics.pose().popMatrix();
                }

                boolean isOwn = mc.player != null && mc.player.getGameProfile().getName().equals(msg.getSender());
                boolean isUploading = isOwn && ImageCache.isUploading(id);
                boolean isDownloading = !isOwn
                        && ImageCache.getDownloadState(id) == ImageCache.DownloadState.IN_PROGRESS;
                if (isUploading || isDownloading) {
                    float progress = isUploading ? ImageCache.getUploadProgress(id) : ImageCache.getDownloadProgress(id);
                    String arrow = isUploading ? "↑" : "↓";
                    String overlayText = arrow + Math.round(progress * 100f) + "%";
                    int overlayColor = ((int) (alpha * 255) << 24) | 0x00CCCCCC;
                    int textW = mc.font.width(overlayText);
                    int overlayX = cardX + (cardW - textW) / 2;
                    int overlayY = guiY1 - Math.round(cardH * 0.20f) - mc.font.lineHeight / 2;
                    guiGraphics.drawString(mc.font, overlayText, overlayX, overlayY, overlayColor, false);
                }
            }

            boolean isHovered = mouseX >= cardX && mouseX < cardX + cardW
                    && mouseY >= guiY0 && mouseY < guiY1;
            int borderColor = isHovered
                    ? (alphaInt | 0x00CCCCCC)
                    : (((int) (alpha * 160) << 24) | (isDeleted ? 0x00662222 : (isError ? 0x00664400 : 0x00444444)));
            guiGraphics.fill(cardX, guiY0, cardX + cardW, guiY0 + 1, borderColor);
            guiGraphics.fill(cardX, guiY1 - 1, cardX + cardW, guiY1, borderColor);
            guiGraphics.fill(cardX, guiY0, cardX + 1, guiY1, borderColor);
            guiGraphics.fill(cardX + cardW - 1, guiY0, cardX + cardW, guiY1, borderColor);

            int visCardX0 = Math.max(cardX, 0);
            int visCardX1 = Math.min(cardX + cardW, chatRightGui);
            if (visCardX1 > visCardX0) {
                msg.setRowCardBounds(id, visCardX0, guiY0, visCardX1, guiY1);
            }

            cardX += cardW + gap;
        }

        guiGraphics.disableScissor();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void chatremastered$renderReplies(
            GuiGraphics guiGraphics, int ticks, int mouseX, int mouseY,
            boolean isChatting,
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

        java.util.List<ChatRemasteredStore.ReplyMessage> allReplies = new java.util.ArrayList<>(ChatRemasteredStore.getRepliesList());

        java.util.Set<Integer> resolvedThisFrame = new java.util.HashSet<>();
        for (ChatRemasteredStore.ReplyMessage reply : allReplies) {
            if (reply.getAddedTime() < 0) continue;

            int blockMin = -1;
            int blockMax = -1;
            for (int i = 0; i < trimmed.size(); i++) {
                if (trimmed.get(i).addedTime() == reply.getAddedTime()) {
                    if (blockMin == -1) blockMin = i;
                    blockMax = i;
                }
            }
            if (blockMin < 0) continue;

            boolean isPhotoReply = ChatRemasteredStore.getMessageList().stream()
                    .anyMatch(im -> im.getAddedTime() == reply.getAddedTime());

            int nickLineIdx = isPhotoReply ? blockMax : blockMax - 1;
            if (nickLineIdx < blockMin) continue;

            int nlLineIndex = isPhotoReply ? nickLineIdx - 1 : nickLineIdx + 1;
            if (nlLineIndex < blockMin || nlLineIndex > blockMax) continue;

            int lineIndexFromBottom = nlLineIndex - scrollPos;
            if (lineIndexFromBottom < 0 || lineIndexFromBottom >= linesPerPage) continue;

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

            int nlLineBottom = chatBottom - lineIndexFromBottom * entryHeight;
            int nlLineTop    = nlLineBottom - entryHeight;

            int REPLY_BAR_H = entryHeight;
            int barBottom = nlLineBottom;
            int barTop    = nlLineTop;

            int chatTopScaled2 = chatBottom - linesPerPage * entryHeight;
            if (barBottom <= chatTopScaled2) continue;

            int alphaInt = (int)(alpha * 255);

            int guiBarTop    = chatBottomGui - (int)((chatBottom - barTop)    * scale);
            int guiBarBottom = chatBottomGui - (int)((chatBottom - barBottom) * scale);

            int guiBarLeft   = 0;

            int guiBarRight  = Math.round((chatWidthPx + 8) * scale);
            int guiBarH      = Math.max(1, guiBarBottom - guiBarTop);

            if (guiBarTop < chatTopGui || guiBarBottom > chatBottomGui) continue;

            int bgAlpha = (int)(alpha * 0x88);
            guiGraphics.fill(guiBarLeft, guiBarTop, guiBarRight, guiBarBottom,
                    (bgAlpha << 24) | 0x2A2A2A);

            int accentAlpha = (int)(alpha * 0xFF);
            guiGraphics.fill(guiBarLeft, guiBarTop, guiBarLeft + Math.round(2 * scale), guiBarBottom,
                    (accentAlpha << 24) | 0x3399EE);

            String replyToSender = stripHeadPlaceholders(reply.getReplyToSender());
            String replyToText   = stripHeadPlaceholders(reply.getReplyToText());
            String replyToImgId  = reply.getReplyToImageId();

            if (!replyToSender.isEmpty()) {
                String dupPrefix = "<" + replyToSender + "> ";
                if (replyToText.startsWith(dupPrefix)) {
                    replyToText = replyToText.substring(dupPrefix.length());
                }
            }

            if (reply.getReplyToAddedTime() < 0) {
                if (!replyToImgId.isEmpty()) {

                    for (ChatRemasteredStore.ImageMessage imgMsg : ChatRemasteredStore.getMessageList()) {
                        if (imgMsg.getImageId().equals(replyToImgId) && imgMsg.getAddedTime() >= 0) {
                            reply.setReplyToAddedTime(imgMsg.getAddedTime());
                            break;
                        }
                    }
                } else {

                    java.util.Set<Integer> takenAddedTimes = new java.util.HashSet<>();
                    for (ChatRemasteredStore.ReplyMessage other : allReplies) {
                        if (other == reply) continue;
                        if (other.getReplyToAddedTime() >= 0
                                && other.getReplyToSender().equals(replyToSender)
                                && other.getReplyToText().equals(reply.getReplyToText())) {
                            takenAddedTimes.add(other.getReplyToAddedTime());
                        }
                    }
                    takenAddedTimes.addAll(resolvedThisFrame);

                    String expectedPrefix = "<" + replyToSender + "> ";
                    for (GuiMessage gm : allMessages) {
                        if (takenAddedTimes.contains(gm.addedTime())) continue;
                        String gmText = stripObjectContents(gm.content());
                        String stripped = gmText.startsWith("\n") ? gmText.substring(1) : gmText;
                        if (stripped.startsWith(expectedPrefix)) {
                            String msgBody = stripped.substring(expectedPrefix.length());
                            if (msgBody.equals(replyToText) || msgBody.contains(replyToText) || replyToText.contains(msgBody)) {
                                reply.setReplyToAddedTime(gm.addedTime());
                                resolvedThisFrame.add(gm.addedTime());
                                break;
                            }
                        }
                    }
                }
            }

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scale(scale, scale);

            int textX = 4 + 4;
            int textY = barTop + (REPLY_BAR_H - mc.font.lineHeight) / 2 + 1;
            int maxTextW = chatWidthPx - 10;
            int textAlpha = alphaInt << 24;

            if (!replyToImgId.isEmpty()) {

                String arrowStr = "\u21A9 ";
                guiGraphics.drawString(mc.font, arrowStr, textX, textY, textAlpha | 0xBBBBBB, false);
                int curX = textX + mc.font.width(arrowStr);

                net.minecraft.resources.ResourceLocation thumbTex = ImageCache.getTexture(replyToImgId);
                if (thumbTex != null) {
                    int maxW = 10, maxH = 10;
                    dev.errnicraft.chatremastered.IntPair ts = ImageCache.getTexSize(replyToImgId);
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
                            net.minecraft.client.renderer.RenderType::guiTextured,
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

                String suffix = !replyToText.isEmpty() ? replyToText : "";
                int remW = maxTextW - (curX - textX);
                net.minecraft.network.chat.MutableComponent labelComp;
                if (!replyToSender.isEmpty()) {
                    labelComp = net.minecraft.network.chat.Component.empty();
                    labelComp.append(net.minecraft.network.chat.Component.literal(replyToSender)
                            .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x3399EE)));
                    if (!suffix.isEmpty()) labelComp.append(net.minecraft.network.chat.Component.literal(": " + suffix)
                            .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAAAAA)));
                } else {
                    labelComp = net.minecraft.network.chat.Component.literal(suffix)
                            .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAAAAA));
                }

                labelComp = truncateReplyLabel(labelComp, remW, mc);
                guiGraphics.drawString(mc.font, labelComp, curX, textY, textAlpha | 0xAAAAAA, false);
            } else {

                String arrowStr = "\u21A9 ";
                guiGraphics.drawString(mc.font, arrowStr, textX, textY, textAlpha | 0xBBBBBB, false);
                int curX2 = textX + mc.font.width(arrowStr);

                String suffix = !replyToText.isEmpty() ? replyToText : "";
                int remW2 = maxTextW - (curX2 - textX);

                net.minecraft.network.chat.MutableComponent labelComp2;
                if (!replyToSender.isEmpty()) {
                    labelComp2 = net.minecraft.network.chat.Component.empty();
                    labelComp2.append(net.minecraft.network.chat.Component.literal(replyToSender)
                            .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x3399EE)));
                    if (!suffix.isEmpty()) labelComp2.append(net.minecraft.network.chat.Component.literal(": " + suffix)
                            .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAAAAA)));
                } else {
                    labelComp2 = net.minecraft.network.chat.Component.literal(suffix)
                            .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAAAAA));
                }

                labelComp2 = truncateReplyLabel(labelComp2, remW2, mc);
                guiGraphics.drawString(mc.font, labelComp2, curX2, textY, textAlpha | 0xAAAAAA, false);
            }

            guiGraphics.pose().popMatrix();
        }
    }

    @ModifyReturnValue(method = "getLinesPerPage", at = @At("RETURN"))
    private int cr$modifyLinesPerPage(int original) {
        Minecraft mc = Minecraft.getInstance();

        boolean focused = mc.screen instanceof ChatScreen;

        if (focused) {
            if (!ChatRemasteredConfig.getFullscreenChat()) return original;
            float scale = (float) mc.options.chatScale().get().doubleValue();
            if (scale <= 0f) return original;
            double lineSpacing = mc.options.chatLineSpacing().get();
            int lineH = (int) Math.round(9.0 * (lineSpacing + 1.0));
            if (lineH <= 0) return original;
            int chatBottomScaled = (int) ((mc.getWindow().getGuiScaledHeight() - 40) / scale);
            int availableH = chatBottomScaled - 2;
            return Math.max(original, availableH / lineH);
        } else {
            return ChatRemasteredConfig.getClosedChatLines();
        }
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

    private static String stripObjectContents(net.minecraft.network.chat.Component component) {
        StringBuilder sb = new StringBuilder();
        collectPlainText(component, sb);
        return stripHeadPlaceholders(sb.toString());
    }

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

        for (net.minecraft.network.chat.Component sibling : component.getSiblings()) {
            collectPlainText(sibling, sb);
        }
    }

    private static net.minecraft.network.chat.MutableComponent truncateReplyLabel(
            net.minecraft.network.chat.MutableComponent comp, int maxW, net.minecraft.client.Minecraft mc) {
        if (mc.font.width(comp) <= maxW) return comp;
        String plain = comp.getString();
        String ellipsis = "\u2026";
        int ellW = mc.font.width(ellipsis);

        while (!plain.isEmpty() && mc.font.width(plain) + ellW > maxW)
            plain = plain.substring(0, plain.length() - 1);

        net.minecraft.network.chat.MutableComponent result = net.minecraft.network.chat.Component.empty();
        boolean nickDone = false;
        int remaining = plain.length();
        for (net.minecraft.network.chat.Component sib : comp.getSiblings()) {
            if (remaining <= 0) break;
            String sibText = sib.getString();
            if (sibText.length() <= remaining) {
                result.append(net.minecraft.network.chat.Component.literal(sibText).setStyle(sib.getStyle()));
                remaining -= sibText.length();
            } else {
                result.append(net.minecraft.network.chat.Component.literal(sibText.substring(0, remaining)).setStyle(sib.getStyle()));
                remaining = 0;
            }
        }
        result.append(net.minecraft.network.chat.Component.literal(ellipsis)
                .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAAAAA)));
        return result;
    }

    private static final int ENTITY_LEFT_MARGIN = 10;
    private static final int ENTITY_VERT_PAD = 0;
    private static final int ENTITY_TOP_GAP = 0;
    private static final float ENTITY_ROTATE_SPEED_DEG_PER_TICK = 1.2f;
    private static final float ENTITY_ROTATE_SPEED_DEG_PER_SEC = ENTITY_ROTATE_SPEED_DEG_PER_TICK * 20.0f;

    @Inject(method = "render", at = @At("TAIL"))
    private void chatremastered$renderEntities(
            GuiGraphics guiGraphics, int ticks, int mouseX, int mouseY,
            boolean isChatting,
            CallbackInfo ci3
    ) {
        Minecraft mc = Minecraft.getInstance();
        List<ChatRemasteredStore.EntityMessage> entityMsgs = ChatRemasteredStore.getEntityMessageList();
        if (entityMsgs.isEmpty()) return;
        if (mc.level == null) return;

        float scale = mc.options.chatScale().get().floatValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int) (9.0 * (chatLineSpacing + 1.0));
        int chatWidthPx = Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);

        ChatComponentAccessor accessor = (ChatComponentAccessor) this;
        List<GuiMessage.Line> trimmed = accessor.getTrimmedMessages();
        int scrollPos = accessor.getChatScrollbarPos();
        int linesPerPage = ((ChatComponent)(Object) this).getLinesPerPage();

        int chatBottomGui = mc.getWindow().getGuiScaledHeight() - 40;
        int chatTopGui = chatBottomGui - (int)(linesPerPage * entryHeight * scale);
        final int chatTopScaled = chatBottom - linesPerPage * entryHeight;

        int dispHDefault = dev.errnicraft.chatremastered.client.EntityChatRenderer.ENTITY_LINES * entryHeight;

        for (ChatRemasteredStore.EntityMessage msg : entityMsgs) {
            if (msg.getDismissed()) continue;
            if (msg.isItemMode()) continue;

            int nickLineIndex = -1;
            for (int i = trimmed.size() - 1; i >= 0; i--) {
                GuiMessage.Line line = trimmed.get(i);
                if (line.addedTime() == msg.getAddedTime()) {
                    nickLineIndex = i;
                    break;
                }
            }
            if (nickLineIndex == -1) continue;

            int dispH = dispHDefault;
            int dispW = Math.round(dispH * 0.55f);

            int defaultModelScale = Math.round(dispHDefault * 0.44f);
            int modelScale = msg.customSize > 0
                    ? Math.max(1, Math.round(defaultModelScale * (msg.customSize / 1000f)))
                    : defaultModelScale;

            int blockMin = nickLineIndex;
            for (int i = nickLineIndex - 1; i >= 0; i--) {
                GuiMessage.Line line = trimmed.get(i);
                if (line.addedTime() == msg.getAddedTime()) {
                    blockMin = i;
                } else {
                    break;
                }
            }
            int entitySlotIndex = blockMin;
            if (entitySlotIndex < 0 || entitySlotIndex > nickLineIndex - 1) continue;

            int lineIndexFromBottom = entitySlotIndex - scrollPos;
            if (lineIndexFromBottom < 0 || lineIndexFromBottom >= linesPerPage) continue;

            float alpha;
            if (isChatting) {
                alpha = mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f;
            } else {
                double t = (ticks - msg.getAddedTime()) / 200.0;
                t = (1.0 - t) * 10.0;
                t = Math.max(0.0, Math.min(1.0, t));
                alpha = (float)(t * t) * (mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f);
                if (alpha <= 1e-5f) continue;
            }

            int slotTopLineBottom = chatBottom - lineIndexFromBottom * entryHeight;
            int entBottom = slotTopLineBottom - ENTITY_VERT_PAD;
            int entTop    = entBottom - dispH + ENTITY_TOP_GAP;

            if (entBottom <= chatTopScaled) continue;
            if (entTop >= chatBottom) continue;

            int clampedTop    = Math.max(entTop,    chatTopScaled);
            int clampedBottom = Math.min(entBottom, chatBottom);
            if (clampedBottom <= clampedTop) continue;

            int entGuiX0 = Mth.floor((ENTITY_LEFT_MARGIN + 4) * scale);
            int entGuiX1 = Mth.floor((ENTITY_LEFT_MARGIN + 4 + dispW) * scale);
            int entGuiY0 = chatBottomGui - Mth.floor((chatBottom - clampedTop)    * scale);
            int entGuiY1 = chatBottomGui - Mth.floor((chatBottom - clampedBottom) * scale);
            if (entGuiX1 <= entGuiX0 || entGuiY1 <= entGuiY0) continue;

            guiGraphics.enableScissor(0, entGuiY0, mc.getWindow().getGuiScaledWidth(), entGuiY1);

            int entCenterX = (entGuiX0 + entGuiX1) / 2;
            int viewportHalfW = Math.max(entGuiX1 - entGuiX0, mc.getWindow().getGuiScaledWidth());
            int entGuiX0Viewport = entCenterX - viewportHalfW;
            int entGuiX1Viewport = entCenterX + viewportHalfW;

            net.minecraft.world.entity.LivingEntity renderEntity =
                    msg.isEntityMode()
                            ? chatremastered$resolveMobEntity(mc, msg)
                            : chatremastered$resolvePlayerEntity(mc, msg);

            if (renderEntity != null) {

                int fadedScale = Math.max(1, Math.round(modelScale * alpha));
                int shrink = modelScale - fadedScale;
                int fadedX0 = entGuiX0Viewport + shrink / 2;
                int fadedX1 = entGuiX1Viewport - shrink / 2;
                int fadedY0 = entGuiY0 + shrink / 2;
                int fadedY1 = entGuiY1 - shrink / 2;
                if (fadedX1 <= fadedX0) fadedX1 = fadedX0 + 1;
                if (fadedY1 <= fadedY0) fadedY1 = fadedY0 + 1;

                if ("rotate".equals(msg.getBehavior())) {
                    long nowNanos = System.nanoTime();
                    if (msg.lastRotateFrameNanos != 0L) {
                        double deltaSeconds = (nowNanos - msg.lastRotateFrameNanos) / 1_000_000_000.0;
                        msg.rotateAngleDeg += (float) (ENTITY_ROTATE_SPEED_DEG_PER_SEC * deltaSeconds);
                    }
                    msg.lastRotateFrameNanos = nowNanos;
                    if (msg.rotateAngleDeg >= 360.0f) msg.rotateAngleDeg -= 360.0f;

                    float offWorldX = msg.offsetX / (float) fadedScale;
                    float offWorldY = -msg.offsetY / (float) fadedScale;
                    chatremastered$renderEntityRotating(guiGraphics, fadedX0, fadedY0, fadedX1, fadedY1,
                            fadedScale, renderEntity, msg.rotateAngleDeg, offWorldX, offWorldY);
                } else {
                    net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
                            guiGraphics, fadedX0, fadedY0, fadedX1, fadedY1,
                            fadedScale, 0.0625f, mouseX, mouseY, renderEntity
                    );
                }
            }

            guiGraphics.disableScissor();

            msg.setScreenBounds(entGuiX0, entGuiY0, entGuiX1, entGuiY1);
        }
    }

    private net.minecraft.world.entity.player.Player chatremastered$resolvePlayerEntity(
            Minecraft mc, ChatRemasteredStore.EntityMessage msg) {
        String targetName = msg.getTargetPlayerName();

        com.mojang.authlib.GameProfile onlineProfile = null;
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(targetName);
            if (info != null) {
                onlineProfile = info.getProfile();
            }
        }

        if (onlineProfile != null) {
            if (!(msg.cachedPlayerEntity instanceof net.minecraft.world.entity.player.Player cached)
                    || !onlineProfile.getId().toString().equals(msg.cachedForUuid)) {
                ResolvedSkinRemotePlayer entity =
                        new ResolvedSkinRemotePlayer(mc.level, onlineProfile);
                msg.cachedPlayerEntity = entity;
                msg.cachedForUuid = onlineProfile.getId().toString();
                msg.skinResolvePending = false;
                return entity;
            }
            return cached;
        }

        if (msg.cachedPlayerEntity instanceof net.minecraft.world.entity.player.Player cached) {
            if (!msg.skinResolvePending) {
                return cached;
            }
            long now = System.currentTimeMillis();
            if (now - msg.lastSkinResolveAttemptMs < 5000L) {
                return cached;
            }
        }

        java.util.UUID offlineUuid = net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(targetName);
        com.mojang.authlib.GameProfile fallbackProfile = new com.mojang.authlib.GameProfile(offlineUuid, targetName);
        ResolvedSkinRemotePlayer fallbackEntity =
                new ResolvedSkinRemotePlayer(mc.level, fallbackProfile);
        if (msg.cachedPlayerEntity == null) {
            msg.cachedPlayerEntity = fallbackEntity;
            msg.cachedForUuid = fallbackProfile.getId().toString();
        }
        msg.skinResolvePending = true;
        msg.lastSkinResolveAttemptMs = System.currentTimeMillis();

        dev.errnicraft.chatremastered.client.MojangProfileResolver.resolve(targetName)
                .thenAccept(resolved -> {
                    if (resolved == null || resolved.getProperties().isEmpty()) {
                        msg.lastSkinResolveAttemptMs = System.currentTimeMillis();
                        return;
                    }
                    ResolvedSkinRemotePlayer resolvedEntity =
                            new ResolvedSkinRemotePlayer(mc.level, resolved);
                    msg.cachedPlayerEntity = resolvedEntity;
                    msg.cachedForUuid = resolved.getId().toString();
                    msg.skinResolvePending = false;
                });

        return msg.cachedPlayerEntity instanceof net.minecraft.world.entity.player.Player p ? p : fallbackEntity;
    }

    private net.minecraft.world.entity.LivingEntity chatremastered$resolveMobEntity(
            Minecraft mc, ChatRemasteredStore.EntityMessage msg) {
        String cacheKey = msg.entityNamespace + ":" + msg.entityPath + ":" + msg.entityNbt;
        if (msg.cachedPlayerEntity instanceof net.minecraft.world.entity.LivingEntity cached
                && cacheKey.equals(msg.cachedForUuid)) {
            return cached;
        }

        net.minecraft.resources.ResourceLocation typeId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(msg.entityNamespace, msg.entityPath);
        var typeOpt = net.minecraft.world.entity.EntityType.byString(typeId.toString());
        if (typeOpt.isEmpty()) {
            return null;
        }
        net.minecraft.world.entity.EntityType<?> type = typeOpt.get();
        net.minecraft.world.entity.Entity created =
                type.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        if (created == null) {
            return null;
        }

        String nbt = msg.entityNbt;
        if (nbt != null && nbt.length() >= 2) {
            try {
                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(nbt);
                net.minecraft.util.ProblemReporter.Collector problems = new net.minecraft.util.ProblemReporter.Collector();
                net.minecraft.world.level.storage.ValueInput input = net.minecraft.world.level.storage.TagValueInput.create(
                        problems, mc.level.registryAccess(), tag);
                created.load(input);
                if (!problems.isEmpty()) {
                    mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                            "§c[Chat Remastered] NBT для " + msg.entityNamespace + ":" + msg.entityPath
                                    + " применён частично: " + problems.getReport()));
                }
            } catch (Exception e) {
                mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                        "§c[Chat Remastered] NBT для " + msg.entityNamespace + ":" + msg.entityPath
                                + " не применён: " + e.getMessage()));
            }
        }

        if (!(created instanceof net.minecraft.world.entity.LivingEntity living)) {
            return null;
        }

        msg.cachedPlayerEntity = living;
        msg.cachedForUuid = cacheKey;
        return living;
    }

    private static void chatremastered$renderEntityRotating(
            GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int size,
            net.minecraft.world.entity.LivingEntity entity, float angleDeg,
            float offWorldX, float offWorldY) {
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf yRotation = new org.joml.Quaternionf()
                .rotateY((float) Math.toRadians(angleDeg));
        rotation.mul(yRotation);

        net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher =
                Minecraft.getInstance().getEntityRenderDispatcher();
        net.minecraft.client.renderer.entity.EntityRenderer<? super net.minecraft.world.entity.LivingEntity, ?> renderer =
                dispatcher.getRenderer(entity);
        net.minecraft.client.renderer.entity.state.EntityRenderState renderState =
                renderer.createRenderState(entity, 1.0f);

        if (renderState instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState livingState) {
            livingState.bodyRot = 180.0f;
            livingState.yRot = 0.0f;
            livingState.xRot = 0.0f;
            livingState.boundingBoxWidth = livingState.boundingBoxWidth / livingState.scale;
            livingState.boundingBoxHeight = livingState.boundingBoxHeight / livingState.scale;
            livingState.scale = 1.0f;
        }

        org.joml.Vector3f translation = new org.joml.Vector3f(
                offWorldX, renderState.boundingBoxHeight / 2.0f + 0.0625f + offWorldY, 0.0f);
        guiGraphics.submitEntityRenderState(renderState, size, translation, rotation, yRotation, x0, y0, x1, y1);
    }

    private static final int ITEM_LEFT_MARGIN = 10;
    private static final int ITEM_VERT_PAD = 0;
    private static final int ITEM_TOP_GAP = 0;

    private static final float ITEM_ROTATE_SPEED_DEG_PER_SEC = ENTITY_ROTATE_SPEED_DEG_PER_SEC;

    @Inject(method = "render", at = @At("TAIL"))
    private void chatremastered$renderItems(
            GuiGraphics guiGraphics, int ticks, int mouseX, int mouseY,
            boolean isChatting,
            CallbackInfo ci4
    ) {
        Minecraft mc = Minecraft.getInstance();
        List<ChatRemasteredStore.EntityMessage> entityMsgs = ChatRemasteredStore.getEntityMessageList();
        if (entityMsgs.isEmpty()) return;
        if (mc.level == null) return;

        float scale = mc.options.chatScale().get().floatValue();
        if (scale < 0.01f) scale = 1f;
        double chatLineSpacing = mc.options.chatLineSpacing().get();
        int entryHeight = (int) (9.0 * (chatLineSpacing + 1.0));
        int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);

        ChatComponentAccessor accessor = (ChatComponentAccessor) this;
        List<GuiMessage.Line> trimmed = accessor.getTrimmedMessages();
        int scrollPos = accessor.getChatScrollbarPos();
        int linesPerPage = ((ChatComponent)(Object) this).getLinesPerPage();

        int chatBottomGui = mc.getWindow().getGuiScaledHeight() - 40;
        final int chatTopScaled = chatBottom - linesPerPage * entryHeight;

        int dispHDefault = dev.errnicraft.chatremastered.client.EntityChatRenderer.ITEM_LINES * entryHeight;

        for (ChatRemasteredStore.EntityMessage msg : entityMsgs) {
            if (msg.getDismissed()) continue;
            if (!msg.isItemMode()) continue;

            int nickLineIndex = -1;
            for (int i = trimmed.size() - 1; i >= 0; i--) {
                GuiMessage.Line line = trimmed.get(i);
                if (line.addedTime() == msg.getAddedTime()) {
                    nickLineIndex = i;
                    break;
                }
            }
            if (nickLineIndex == -1) continue;

            int dispH = dispHDefault;
            int dispW = Math.round(dispH * 0.9f);
            int modelScale = Math.round(dispHDefault * 0.44f * 4.0f);

            int blockMin = nickLineIndex;
            for (int i = nickLineIndex - 1; i >= 0; i--) {
                GuiMessage.Line line = trimmed.get(i);
                if (line.addedTime() == msg.getAddedTime()) {
                    blockMin = i;
                } else {
                    break;
                }
            }
            int itemSlotIndex = blockMin;
            if (itemSlotIndex < 0 || itemSlotIndex > nickLineIndex - 1) continue;

            int lineIndexFromBottom = itemSlotIndex - scrollPos;
            if (lineIndexFromBottom < 0 || lineIndexFromBottom >= linesPerPage) continue;

            float alpha;
            if (isChatting) {
                alpha = mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f;
            } else {
                double t = (ticks - msg.getAddedTime()) / 200.0;
                t = (1.0 - t) * 10.0;
                t = Math.max(0.0, Math.min(1.0, t));
                alpha = (float)(t * t) * (mc.options.chatOpacity().get().floatValue() * 0.9f + 0.1f);
                if (alpha <= 1e-5f) continue;
            }

            int slotTopLineBottom = chatBottom - lineIndexFromBottom * entryHeight;
            int itemBottom = slotTopLineBottom - ITEM_VERT_PAD;
            int itemTop    = itemBottom - dispH + ITEM_TOP_GAP;

            if (itemBottom <= chatTopScaled) continue;
            if (itemTop >= chatBottom) continue;

            int clampedTop    = Math.max(itemTop,    chatTopScaled);
            int clampedBottom = Math.min(itemBottom, chatBottom);
            if (clampedBottom <= clampedTop) continue;

            int itemGuiX0 = Mth.floor((ITEM_LEFT_MARGIN + 4) * scale);
            int itemGuiX1 = Mth.floor((ITEM_LEFT_MARGIN + 4 + dispW) * scale);
            int itemGuiY0 = chatBottomGui - Mth.floor((chatBottom - clampedTop)    * scale);
            int itemGuiY1 = chatBottomGui - Mth.floor((chatBottom - clampedBottom) * scale);
            if (itemGuiX1 <= itemGuiX0 || itemGuiY1 <= itemGuiY0) continue;

            guiGraphics.enableScissor(0, itemGuiY0, mc.getWindow().getGuiScaledWidth(), itemGuiY1);

            int itemCenterX = (itemGuiX0 + itemGuiX1) / 2;
            int viewportHalfW = Math.max(itemGuiX1 - itemGuiX0, mc.getWindow().getGuiScaledWidth());
            int itemGuiX0Viewport = itemCenterX - viewportHalfW;
            int itemGuiX1Viewport = itemCenterX + viewportHalfW;

            net.minecraft.world.item.ItemStack renderStack = chatremastered$resolveItemStack(mc, msg);

            if (renderStack != null && !renderStack.isEmpty()) {
                int fadedScale = Math.max(1, Math.round(modelScale * alpha));
                int shrink = modelScale - fadedScale;
                int fadedX0 = itemGuiX0Viewport + shrink / 2;
                int fadedX1 = itemGuiX1Viewport - shrink / 2;
                int fadedY0 = itemGuiY0 + shrink / 2;
                int fadedY1 = itemGuiY1 - shrink / 2;
                if (fadedX1 <= fadedX0) fadedX1 = fadedX0 + 1;
                if (fadedY1 <= fadedY0) fadedY1 = fadedY0 + 1;

                long nowNanos = System.nanoTime();
                if (msg.lastRotateFrameNanos != 0L) {
                    double deltaSeconds = (nowNanos - msg.lastRotateFrameNanos) / 1_000_000_000.0;
                    msg.rotateAngleDeg += (float) (ITEM_ROTATE_SPEED_DEG_PER_SEC * deltaSeconds);
                }
                msg.lastRotateFrameNanos = nowNanos;
                if (msg.rotateAngleDeg >= 360.0f) msg.rotateAngleDeg -= 360.0f;

                chatremastered$renderItemRotating(mc, guiGraphics, fadedX0, fadedY0, fadedX1, fadedY1,
                        fadedScale, renderStack, msg.rotateAngleDeg, msg);
            }

            guiGraphics.disableScissor();

            msg.setScreenBounds(itemGuiX0, itemGuiY0, itemGuiX1, itemGuiY1);

            if (renderStack != null && !renderStack.isEmpty()
                    && mouseX >= itemGuiX0 && mouseX < itemGuiX1 && mouseY >= itemGuiY0 && mouseY < itemGuiY1) {
                guiGraphics.setTooltipForNextFrame(mc.font, renderStack, mouseX, mouseY);
            }
        }
    }

    private net.minecraft.world.item.ItemStack chatremastered$resolveItemStack(
            Minecraft mc, ChatRemasteredStore.EntityMessage msg) {
        String cacheKey = msg.itemNamespace + ":" + msg.itemPath + ":" + msg.itemNbt;
        if (msg.cachedItemStack instanceof net.minecraft.world.item.ItemStack cached
                && cacheKey.equals(msg.cachedForUuid)) {
            return cached;
        }

        net.minecraft.resources.ResourceLocation itemId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(msg.itemNamespace, msg.itemPath);
        var holderOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        if (holderOpt.isEmpty()) {
            return null;
        }

        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(holderOpt.get());

        String nbt = msg.itemNbt;
        if (nbt != null && nbt.length() >= 2) {
            try {
                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(nbt);
                net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops =
                        mc.level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                net.minecraft.core.component.DataComponentPatch patch =
                        net.minecraft.core.component.DataComponentPatch.CODEC.parse(ops, tag).getOrThrow();
                stack.applyComponents(patch);
            } catch (Exception ignored) {
            }
        }

        msg.cachedItemStack = stack;
        msg.cachedForUuid = cacheKey;
        return stack;
    }

    private static void chatremastered$renderItemRotating(
            Minecraft mc, GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int size,
            net.minecraft.world.item.ItemStack stack, float angleDeg,
            ChatRemasteredStore.EntityMessage msg) {
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf yRotation = new org.joml.Quaternionf()
                .rotateY((float) Math.toRadians(angleDeg));
        rotation.mul(yRotation);

        String cacheKey = "item:" + msg.itemNamespace + ":" + msg.itemPath + ":" + msg.itemNbt;
        net.minecraft.world.entity.item.ItemEntity itemEntity;
        if (msg.cachedPlayerEntity instanceof net.minecraft.world.entity.item.ItemEntity cached
                && cacheKey.equals(msg.cachedForUuid)) {
            itemEntity = cached;
        } else {
            itemEntity = new net.minecraft.world.entity.item.ItemEntity(mc.level, 0.0, 0.0, 0.0, stack);
            itemEntity.setNeverPickUp();
            msg.cachedPlayerEntity = itemEntity;
            msg.cachedForUuid = cacheKey;
        }

        net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        net.minecraft.client.renderer.entity.EntityRenderer<? super net.minecraft.world.entity.item.ItemEntity, ?> renderer =
                dispatcher.getRenderer(itemEntity);
        net.minecraft.client.renderer.entity.state.EntityRenderState renderState =
                renderer.createRenderState(itemEntity, 0.0f);
        renderState.ageInTicks = 0.0f;
        if (renderState instanceof net.minecraft.client.renderer.entity.state.ItemEntityRenderState itemState) {
            itemState.bobOffset = 0.0f;
        }

        org.joml.Vector3f translation = new org.joml.Vector3f(0.0f, 0.4f, 0.0f);
        guiGraphics.submitEntityRenderState(renderState, size, translation, rotation, yRotation, x0, y0, x1, y1);
    }

}