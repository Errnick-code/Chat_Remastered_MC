package dev.errnick.chatremastered.server;

import dev.errnick.chatremastered.server.tgbridge.TgBridgeCompat;
import dev.errnick.chatremastered.tcp.ImageTcpServer;

public final class ImageReadyBridge {

    public static void register() {
        ImageTcpServer.addOnImageReadyListener(imageId -> {

            ChatRemasteredState.PendingBroadcast pending = null;
            int attempts = 0;
            while (pending == null && attempts < 20) {
                pending = ChatRemasteredState.pendingBroadcasts.remove(imageId);
                if (pending == null) {
                    attempts++;
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            if (pending == null) {
                System.out.println("[Chat Remastered] DEBUG onImageReady: no pending broadcast for imageId='"
                        + imageId + "' after " + attempts + " attempts");
                return;
            }
            ImageTcpServer.evictOld(100);

            System.out.println("[Chat Remastered] DEBUG onImageReady: imageId='" + imageId
                    + "' sender='" + pending.sender() + "' tgAvailable=" + TgBridgeCompat.isAvailable());
            if (!TgBridgeCompat.isAvailable()) {
                return;
            }

            if (pending.groupId() != null && !pending.groupId().isEmpty()) {
                ChatRemasteredState.pendingBroadcasts.remove(imageId);
                ChatRemasteredState.externalPendingBroadcasts.remove(imageId);
                return;
            }

            if (ChatRemasteredState.isInPrivateGroupByName(pending.sender())) {
                ChatRemasteredState.pendingBroadcasts.remove(imageId);
                ChatRemasteredState.externalPendingBroadcasts.remove(imageId);
                System.out.println("[Chat Remastered] onImageReady: '" + imageId
                        + "' from private group member '" + pending.sender() + "' - not mirrored to TG");
                return;
            }
            byte[] bytes = ImageTcpServer.getCachedBytes(imageId);
            System.out.println("[Chat Remastered] DEBUG onImageReady: bytes="
                    + (bytes != null ? bytes.length : "null"));
            if (bytes == null) {
                return;
            }
            boolean isGif = bytes.length >= 4
                    && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8';
            String fileName = imageId + (isGif ? ".gif" : ".png");
            TgBridgeCompat.onImageSent(imageId, pending.sender(), pending.caption(), bytes, fileName,
                    pending.replyToSender(), pending.groupId(), pending.groupCount());
        });
    }

    private ImageReadyBridge() {
    }
}
