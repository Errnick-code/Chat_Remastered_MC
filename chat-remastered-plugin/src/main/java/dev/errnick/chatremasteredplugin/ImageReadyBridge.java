package dev.errnick.chatremasteredplugin;

import dev.errnick.chatremasteredplugin.tcp.ImageTcpServer;

public final class ImageReadyBridge {

    public static void register() {
        ImageTcpServer.addOnImageReadyListener(imageId -> {
            PluginState.PendingBroadcast pending = null;
            int attempts = 0;
            while (pending == null && attempts < 20) {
                pending = PluginState.pendingBroadcasts.get(imageId);
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
                return;
            }
            ImageTcpServer.evictOld(100);
        });
    }

    private ImageReadyBridge() {
    }
}
