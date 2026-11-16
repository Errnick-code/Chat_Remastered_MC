package dev.errnick.chatremastered;

import dev.errnick.chatremastered.client.ChatMessageRenderer;
import dev.errnick.chatremastered.client.DebugCommands;
import dev.errnick.chatremastered.client.ImageUploadFlow;
import dev.errnick.chatremastered.client.NetworkPacketHandlers;
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
        dev.errnick.chatremastered.GroupModeState.load();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.errnick.chatremastered.network.packet.GroupChatPacket.TYPE, (payload, context) -> {
            dev.errnick.chatremastered.GroupModeState.inGroup = payload.inGroup();
            dev.errnick.chatremastered.GroupModeState.groupName = payload.groupName();
            if (!payload.inGroup()) dev.errnick.chatremastered.GroupModeState.mode = 0;
            if (payload.inGroup() && context.client() != null) {
                context.client().execute(() ->
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new dev.errnick.chatremastered.network.packet.GroupModePacket(
                                        dev.errnick.chatremastered.GroupModeState.mode == 1)));
            }
        });
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
