package dev.errnicraft.chatremastered;

import dev.errnicraft.chatremastered.client.ChatMessageRenderer;
import dev.errnicraft.chatremastered.client.ChatOverlayRenderer;
import dev.errnicraft.chatremastered.client.DebugCommands;
import dev.errnicraft.chatremastered.client.ImageUploadFlow;
import dev.errnicraft.chatremastered.client.NetworkPacketHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

import java.io.File;
import java.util.function.Consumer;

public final class ChatRemasteredClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ChatRemasteredConfig.loadConfig();
        DragDropHandler.register();

        NetworkPacketHandlers.register();
        DebugCommands.register();

        // ChatComponent/ChatScreen's own render pass is NOT actually last in
        // the frame — vanilla chat text can still be painted after our
        // overlays even when they're drawn at the TAIL of ChatScreen.render().
        // ScreenEvents.afterRender fires once the screen has genuinely
        // finished rendering for the frame (per Fabric's own contract), so
        // redrawing our overlays here guarantees they're the true last thing
        // painted while the chat screen is open.
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen)) return;

            ScreenEvents.afterRender(screen).register((screen1, graphics, mouseX, mouseY, tickDelta) -> {
                ChatOverlayRenderer chatMixin = (ChatOverlayRenderer) (Object) client.gui.getChat();
                int ticks = chatMixin.chatremastered$getLastTicks();
                int chatMouseX = chatMixin.chatremastered$getLastMouseX();
                int chatMouseY = chatMixin.chatremastered$getLastMouseY();
                boolean isChatting = chatMixin.chatremastered$getLastIsChatting();

                graphics.flush();
                chatMixin.chatremastered$renderImages(graphics, ticks, chatMouseX, chatMouseY, isChatting, null);
                chatMixin.chatremastered$renderReplies(graphics, ticks, chatMouseX, chatMouseY, isChatting, null);
                chatMixin.chatremastered$renderEntities(graphics, ticks, chatMouseX, chatMouseY, isChatting, null);
                chatMixin.chatremastered$renderItems(graphics, ticks, chatMouseX, chatMouseY, isChatting, null);
                graphics.flush();
            });
        });
    }

    public static void saveImageAs(String imageId) {
        ImageUploadFlow.saveImageAs(imageId);
    }

    public static void stageImage(File file) {
        ImageUploadFlow.stageImage(file);
    }

    public static void fetchFullImage(String imageId, Consumer<byte[]> onReady) {
        ChatMessageRenderer.fetchFullImage(imageId, onReady);
    }

    public static boolean canSendPhoto(Minecraft mc) {
        return ImageUploadFlow.canSendPhoto(mc);
    }

    public static void pasteImageFromClipboard() {
        ImageUploadFlow.pasteImageFromClipboard();
    }

    public static void sendPendingImage() {
        ImageUploadFlow.sendPendingImage();
    }

    public static void sendPendingImageWithCaption(String caption) {
        ImageUploadFlow.sendPendingImageWithCaption(caption);
    }

    public static void sendPendingImageWithCaptionAndReply(String caption, String replyToSender, String replyToText, String replyToImageId) {
        ImageUploadFlow.sendPendingImageWithCaptionAndReply(caption, replyToSender, replyToText, replyToImageId);
    }
}
