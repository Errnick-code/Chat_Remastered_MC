package dev.errnicraft.chatremastered.server.network;

import dev.errnicraft.chatremastered.server.tgbridge.TgBridgeCompat;
import dev.errnicraft.chatremastered.network.packet.AdminDeleteImagePacket;
import dev.errnicraft.chatremastered.network.packet.ImageDeletedPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import dev.errnicraft.chatremastered.tcp.ImageTcpServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class AdminDeleteImageHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(AdminDeleteImagePacket.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String imageId = payload.imageId();

            boolean isOp = context.server().getPlayerList().isOp(player.getGameProfile());
            if (!isOp) {
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
