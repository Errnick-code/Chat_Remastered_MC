package dev.errnick.chatremastered.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.errnick.chatremastered.client.ScreenshotMetadataWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public class ScreenshotMetadataMixin {

    @Inject(method = "grab(Lnet/minecraft/client/Minecraft;Z)V", at = @At("HEAD"), cancellable = true)
    private static void chatremastered$grabWithMetadata(Minecraft minecraft, boolean debugPanoramaRequested, CallbackInfo ci) {
        File workDir = minecraft.gameDirectory;
        File picDir = new File(workDir, "screenshots");
        long beforeTimeMs = System.currentTimeMillis();

        Consumer<Component> wrappedCallback = message -> {
            minecraft.execute(() -> minecraft.showDebugChat(message));
            try {
                File newest = chatremastered$findNewestScreenshot(picDir, beforeTimeMs);
                if (newest != null) {
                    ScreenshotMetadataWriter.writeMetadata(newest, ScreenshotMetadataWriter.buildFields());
                }
            } catch (Exception e) {
                minecraft.gui.hud.getChat().addClientSystemMessage(
                        Component.literal("§8[Chat Remastered] §7Не удалось вшить метаданные скриншота: " + e.getMessage()));
            }
        };

        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        Screenshot.grab(workDir, target, wrappedCallback);
        ci.cancel();
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
