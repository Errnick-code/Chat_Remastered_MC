package dev.errnick.chatremastered.server.network;

import dev.errnick.chatremastered.server.tgbridge.TgBridgeCompat;
import dev.errnick.chatremastered.network.packet.AdminDeleteImagePacket;
import dev.errnick.chatremastered.network.packet.ImageDeletedPacket;
import dev.errnick.chatremastered.server.LuckPermsBridge;
import dev.errnick.chatremastered.server.ChatRemasteredState;
import dev.errnick.chatremastered.tcp.ImageTcpServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class AdminDeleteImageHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(AdminDeleteImagePacket.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String imageId = payload.imageId();

            boolean isOp = context.server().getPlayerList().isOp(player.getGameProfile());
            boolean lpAllowed = LuckPermsBridge.check(player.getUUID(),
                    "chatremastered.admin.deletephoto", false);
            if (!isOp && !lpAllowed) {
                return;
            }

            ChatRemasteredState.imageOwners.remove(imageId);
            ImageTcpServer.deleteImage(imageId);
            if (TgBridgeCompat.isAvailable()) {
                TgBridgeCompat.onImageDeleted(imageId);
            }

            ImageDeletedPacket packet = new ImageDeletedPacket(imageId, true);
            context.server().execute(() -> {
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    if (ChatRemasteredState.hasModInstalled(p.getUUID())) {
                        ServerPlayNetworking.send(p, packet);
                    }
                }
            });
        });
    }

    private AdminDeleteImageHandler() {
    }
}
