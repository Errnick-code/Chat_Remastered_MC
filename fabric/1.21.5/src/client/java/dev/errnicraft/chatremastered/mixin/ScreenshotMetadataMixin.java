package dev.errnicraft.chatremastered.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.errnicraft.chatremastered.client.ScreenshotMetadataWriter;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.util.function.Consumer;

@Mixin(KeyboardHandler.class)
public class ScreenshotMetadataMixin {

    @Redirect(
            method = "keyPress(JIIII)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"
            )
    )
    private void chatremastered$grabWithMetadata(File workDir, RenderTarget target, Consumer<Component> callback) {
        File picDir = new File(workDir, "screenshots");
        long beforeTimeMs = System.currentTimeMillis();

        Consumer<Component> wrappedCallback = message -> {

            callback.accept(message);
            try {
                File newest = chatremastered$findNewestScreenshot(picDir, beforeTimeMs);
                if (newest != null) {
                    ScreenshotMetadataWriter.writeMetadata(newest, ScreenshotMetadataWriter.buildFields());
                }
            } catch (Exception e) {
                Minecraft.getInstance().gui.getChat().addMessage(
                        Component.literal("§8[Chat Remastered] §7Не удалось вшить метаданные скриншота: " + e.getMessage()));
            }
        };

        Screenshot.grab(workDir, target, wrappedCallback);
    }

    private static File chatremastered$findNewestScreenshot(File picDir, long beforeTimeMs) {
        File[] files = picDir.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
        if (files == null || files.length == 0) {
            return null;
        }
        File newest = null;
        long newestTime = -1L;
        for (File f : files) {
            long lm = f.lastModified();
            if (lm >= beforeTimeMs - 1000L && lm > newestTime) {
                newest = f;
                newestTime = lm;
            }
        }
        return newest;
    }
}
