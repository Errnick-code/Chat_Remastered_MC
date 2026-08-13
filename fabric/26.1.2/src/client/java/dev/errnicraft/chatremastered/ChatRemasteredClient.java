package dev.errnicraft.chatremastered;

import dev.errnicraft.chatremastered.client.ChatMessageRenderer;
import dev.errnicraft.chatremastered.client.DebugCommands;
import dev.errnicraft.chatremastered.client.ImageUploadFlow;
import dev.errnicraft.chatremastered.client.NetworkPacketHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.function.Consumer;

public final class ChatRemasteredClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ChatRemasteredConfig.loadConfig();
        DragDropHandler.register();

        NetworkPacketHandlers.register();
        DebugCommands.register();
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
