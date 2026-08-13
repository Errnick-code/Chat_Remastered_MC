package dev.errnicraft.chatremastered.client.chatscreen;

import net.minecraft.client.renderer.RenderType;
import com.mojang.math.Axis;

import dev.errnicraft.chatremastered.PendingCardAnimator;
import dev.errnicraft.chatremastered.PendingImageState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class PendingCardRenderer {

    public static final int PENDING_STRIP_GAP = 4;

    private PendingCardRenderer() {
    }

    public static int pendingRowTotalWidth(List<PendingImageState.PendingImage> pendingAll) {
        int total = 0;
        for (int i = 0; i < pendingAll.size(); i++) {
            total += pendingAll.get(i).getWidth();
            if (i < pendingAll.size() - 1) total += PENDING_STRIP_GAP;
        }
        return total;
    }

    public static int renderPendingCard(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY,
                                         PendingImageState.PendingImage pending, int previewLeft, int previewBottom,
                                         PendingCardAnimator.SpawnState spawn) {
        int dispW = pending.getWidth();
        int dispH = pending.getHeight();
        int previewTop = previewBottom - dispH;

        if (spawn != null && !spawn.isDone()) {
            float scale = Math.max(0.02f, spawn.scale());
            int cx = previewLeft + dispW / 2;
            int cy = previewBottom - dispH / 2;
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0f);
            graphics.pose().scale(scale, scale, 1f);
            graphics.pose().translate(-dispW / 2f, -dispH / 2f, 0f);
            renderPendingCardContent(graphics, mc, mouseX, mouseY, pending, 0, 0, dispH, dispW, dispH, 0xFFFFFFFF);
            graphics.pose().popPose();
            return dispW;
        }

        renderPendingCardContent(graphics, mc, mouseX, mouseY, pending, previewLeft, previewTop, previewBottom, dispW, dispH, 0xFFFFFFFF);
        return dispW;
    }

    public static void renderPendingCardContent(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY,
                                                  PendingImageState.PendingImage pending, int previewLeft, int previewTop, int previewBottom,
                                                  int dispW, int dispH, int alphaTint) {
        boolean isLoaded = pending.isLoaded();
        ResourceLocation tex = pending.getTextureId();
        int a = (alphaTint >>> 24) & 0xFF;

        graphics.fill(previewLeft - 1, previewTop - 2, previewLeft + dispW + 1, previewBottom + 1, tintAlpha(0xAA000000, a));

        if (!isLoaded || tex == null) {
            boolean sizeKnown = pending.getSizeKnown();
            graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewBottom, tintAlpha(0xFF2A2A2A, a));
            int borderColor = tintAlpha(0xFF555555, a);
            graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewTop + 1, borderColor);
            graphics.fill(previewLeft, previewBottom - 1, previewLeft + dispW, previewBottom, borderColor);
            graphics.fill(previewLeft, previewTop, previewLeft + 1, previewBottom, borderColor);
            graphics.fill(previewLeft + dispW - 1, previewTop, previewLeft + dispW, previewBottom, borderColor);

            float progress = pending.getProgress();
            if (progress < 0f && dispH >= 6) {
                int barH = Math.max(3, dispH / 8);
                int barTop = previewBottom - barH;
                int barFull = dispW - 2;
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barFull, previewBottom - 1, tintAlpha(0xFF1A1A1A, a));
                long now = System.currentTimeMillis();
                float phase = (now % 1200L) / 1200f;
                int dotW = Math.max(4, barFull / 3);
                int dotStart = Math.round((barFull - dotW) * phase);
                graphics.fill(previewLeft + 1 + dotStart, barTop, previewLeft + 1 + dotStart + dotW, previewBottom - 1, tintAlpha(0xFF3399EE, a));
                if (dispW >= 40 && barTop - previewTop >= mc.font.lineHeight + 2) {
                    String waitStr = "...";
                    int textX = previewLeft + (dispW - mc.font.width(waitStr)) / 2;
                    int textY = barTop - mc.font.lineHeight - 1;
                    if (textY >= previewTop + 1)
                        graphics.drawString(mc.font, waitStr, textX, textY, tintAlpha(0xFFAAAAAA, a), false);
                }
            } else if (progress >= 0f && dispH >= 6) {
                int barH = Math.max(3, dispH / 8);
                int barTop = previewBottom - barH;
                int barFull = dispW - 2;
                int barW = Math.max(1, Math.round(barFull * progress));
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barFull, previewBottom - 1, tintAlpha(0xFF1A1A1A, a));
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barW, previewBottom - 1, tintAlpha(0xFF3399EE, a));
                if (dispW >= 40 && barTop - previewTop >= mc.font.lineHeight + 2) {
                    int pct = Math.round(progress * 100f);
                    String pctStr = pct + "%";
                    int textX = previewLeft + (dispW - mc.font.width(pctStr)) / 2;
                    int textY = barTop - mc.font.lineHeight - 1;
                    if (textY >= previewTop + 1)
                        graphics.drawString(mc.font, pctStr, textX, textY, tintAlpha(0xFFFFFFFF, a), false);
                }
            }

            int barAreaH = (dispH >= 6) ? Math.max(3, dispH / 8) + 2 : 0;
            int iconAreaH = dispH - barAreaH;
            if (iconAreaH >= 12) {
                float iconScale = iconAreaH * 0.38f / 9.0f;
                iconScale = Math.min(iconScale, dispW * 0.55f / mc.font.lineHeight);
                iconScale = Math.max(iconScale, 1.0f);
                String icon = !sizeKnown ? "?" : "🖼";
                int iconColor = tintAlpha(!sizeKnown ? 0xFF888888 : 0xFFCCCCCC, a);
                int iconPxW = Math.round(mc.font.width(icon) * iconScale);
                int iconPxH = Math.round(mc.font.lineHeight * iconScale);
                int iconX = previewLeft + (dispW - iconPxW) / 2;
                int iconY = previewTop + (iconAreaH - iconPxH) / 2;
                graphics.pose().pushPose();
                graphics.pose().translate(iconX, iconY, 0f);
                graphics.pose().scale(iconScale, iconScale, 1f);
                graphics.drawString(mc.font, icon, 0, 0, iconColor, false);
                graphics.pose().popPose();
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
            graphics.pose().pushPose();
            graphics.pose().translate(previewLeft + offsetX, previewTop + offsetY, 0f);
            graphics.pose().scale(s, s, 1f);
            graphics.blit(RenderType::guiTextured, tex, 0, 0, 0f, 0f, texW, texH, texW, texH, tintAlpha(0xFFFFFFFF, a));
            graphics.pose().popPose();
        }

        int closeSize = 14;
        int closeX = previewLeft + dispW - closeSize - 2;
        int closeY = previewTop + 2;
        boolean hoverClose = mouseX >= closeX && mouseX < closeX + closeSize && mouseY >= closeY && mouseY < closeY + closeSize;
        int closeColor = tintAlpha(hoverClose ? 0xFFFF5555 : 0x88FFFFFF, a);
        graphics.drawCenteredString(mc.font, "✕", closeX + closeSize / 2, closeY + (closeSize - mc.font.lineHeight) / 2, closeColor);
    }

    public static int tintAlpha(int argb, int tintA) {
        int origA = (argb >>> 24) & 0xFF;
        int newA = Math.min(origA, tintA);
        return (newA << 24) | (argb & 0x00FFFFFF);
    }

    public static void renderRemovingCard(GuiGraphics graphics, Minecraft mc, PendingCardAnimator.RemoveState r, int screenH) {
        if (r.shards != null) {
            renderShatterCard(graphics, mc, r);
            return;
        }
        int top = r.currentY(screenH);
        float alpha = r.alpha(screenH);
        int a = Math.round(alpha * 255f);
        if (a <= 0) return;

        int centerX = r.currentX() + r.cardW / 2;
        int centerY = top + r.cardH / 2;
        float angleRad = (float) Math.toRadians(r.currentRotation());

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0f);
        graphics.pose().mulPose(Axis.ZP.rotation(angleRad));
        graphics.pose().translate(-r.cardW / 2f, -r.cardH / 2f, 0f);
        renderRemovingCardContent(graphics, mc, r, 0, 0, r.cardW, r.cardH, a);
        graphics.pose().popPose();
    }

    public static void renderShatterCard(GuiGraphics graphics, Minecraft mc, PendingCardAnimator.RemoveState r) {
        if (!r.isLoaded || r.tex == null) {
            float p = Math.min(1f, r.elapsedMs() / (float) PendingCardAnimator.SHATTER_MS);
            int a = Math.round(255 * (1f - p));
            if (a > 0) {
                renderRemovingCardContent(graphics, mc, r, r.cardX, r.cardTop, r.cardW, r.cardH, a);
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

            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0f);
            graphics.pose().mulPose(Axis.ZP.rotation(angleRad));
            graphics.pose().translate(-(pivotX - s.minX), -(pivotY - s.minY), 0f);
            renderShardTexture(graphics, r, s, a);
            graphics.pose().popPose();
        }
    }

    private static void renderShardTexture(GuiGraphics graphics, PendingCardAnimator.RemoveState r, PendingCardAnimator.Shard s, int alphaByte) {
        int texW = r.texW;
        int texH = r.texH;
        float scaleX = (float) r.cardW / texW;
        float scaleY = (float) r.cardH / texH;
        float scale = Math.min(scaleX, scaleY);
        int fitW = Math.max(1, Math.round(texW * scale));
        int fitH = Math.max(1, Math.round(texH * scale));
        int fitOffX = (r.cardW - fitW) / 2;
        int fitOffY = (r.cardH - fitH) / 2;

        int tint = tintAlpha(0xFFFFFFFF, alphaByte);
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
                    blitShardRun(graphics, r, s, rowStart, my, mx - rowStart, texW, texH, scale, fitOffX, fitOffY, tint);
                    rowStart = -1;
                }
            }
        }
    }

    private static void blitShardRun(GuiGraphics graphics, PendingCardAnimator.RemoveState r, PendingCardAnimator.Shard s,
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

        graphics.pose().pushPose();
        graphics.pose().translate(destX, destY, 0f);
        graphics.pose().scale((float) destW / srcW, (float) destH / srcH, 1f);
        graphics.blit(RenderType::guiTextured, r.tex, 0, 0, srcU0, srcV0, (int) srcW, (int) srcH, texW, texH, tint);
        graphics.pose().popPose();
    }

    public static void renderRemovingCardContent(GuiGraphics graphics, Minecraft mc, PendingCardAnimator.RemoveState r,
                                                   int previewLeft, int previewTop, int dispW, int dispH, int alphaByte) {
        int previewBottom = previewTop + dispH;
        graphics.fill(previewLeft - 1, previewTop - 2, previewLeft + dispW + 1, previewBottom + 1, tintAlpha(0xAA000000, alphaByte));

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
            graphics.pose().pushPose();
            graphics.pose().translate(previewLeft + offsetX, previewTop + offsetY, 0f);
            graphics.pose().scale(s, s, 1f);
            graphics.blit(RenderType::guiTextured, r.tex, 0, 0, 0f, 0f, texW, texH, texW, texH, tintAlpha(0xFFFFFFFF, alphaByte));
            graphics.pose().popPose();
        } else {
            graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewBottom, tintAlpha(0xFF2A2A2A, alphaByte));

            float progress = r.progressSnapshot;
            int barAreaH = (dispH >= 6) ? Math.max(3, dispH / 8) + 2 : 0;
            if (dispH >= 6) {
                int barH = Math.max(3, dispH / 8);
                int barTop = previewBottom - barH;
                int barFull = dispW - 2;
                graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barFull, previewBottom - 1, tintAlpha(0xFF1A1A1A, alphaByte));
                if (progress >= 0f) {
                    int barW = Math.max(1, Math.round(barFull * progress));
                    graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + barW, previewBottom - 1, tintAlpha(0xFF3399EE, alphaByte));
                } else {
                    int dotW = Math.max(4, barFull / 3);
                    graphics.fill(previewLeft + 1, barTop, previewLeft + 1 + dotW, previewBottom - 1, tintAlpha(0xFF3399EE, alphaByte));
                }
            }

            int iconAreaH = dispH - barAreaH;
            if (iconAreaH >= 12) {
                float iconScale = iconAreaH * 0.38f / 9.0f;
                iconScale = Math.min(iconScale, dispW * 0.55f / mc.font.lineHeight);
                iconScale = Math.max(iconScale, 1.0f);
                String icon = !r.sizeKnown ? "?" : "🖼";
                int iconColor = tintAlpha(!r.sizeKnown ? 0xFF888888 : 0xFFCCCCCC, alphaByte);
                int iconPxW = Math.round(mc.font.width(icon) * iconScale);
                int iconPxH = Math.round(mc.font.lineHeight * iconScale);
                int iconX = previewLeft + (dispW - iconPxW) / 2;
                int iconY = previewTop + (iconAreaH - iconPxH) / 2;
                graphics.pose().pushPose();
                graphics.pose().translate(iconX, iconY, 0f);
                graphics.pose().scale(iconScale, iconScale, 1f);
                graphics.drawString(mc.font, icon, 0, 0, iconColor, false);
                graphics.pose().popPose();
            }
        }

        int borderColor = tintAlpha(0xFF555555, alphaByte);
        graphics.fill(previewLeft, previewTop, previewLeft + dispW, previewTop + 1, borderColor);
        graphics.fill(previewLeft, previewBottom - 1, previewLeft + dispW, previewBottom, borderColor);
        graphics.fill(previewLeft, previewTop, previewLeft + 1, previewBottom, borderColor);
        graphics.fill(previewLeft + dispW - 1, previewTop, previewLeft + dispW, previewBottom, borderColor);
    }
}
