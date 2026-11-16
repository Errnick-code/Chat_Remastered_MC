package dev.errnick.chatremastered.server.network;

import dev.errnick.chatremastered.server.tgbridge.TgBridgeCompat;
import dev.errnick.chatremastered.network.packet.DeleteImagePacket;
import dev.errnick.chatremastered.network.packet.ImageDeletedPacket;
import dev.errnick.chatremastered.server.ChatRemasteredState;
import dev.errnick.chatremastered.tcp.ImageTcpServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class DeleteImageHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DeleteImagePacket.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String imageId = payload.imageId();

            UUID owner = ChatRemasteredState.imageOwners.get(imageId);
            if (owner == null || !owner.equals(player.getUUID())) {

                return;
            }

            ChatRemasteredState.imageOwners.remove(imageId);
            ImageTcpServer.deleteImage(imageId);
            if (TgBridgeCompat.isAvailable()) {
                TgBridgeCompat.onImageDeleted(imageId);
            }

            ImageDeletedPacket packet = new ImageDeletedPacket(imageId, false);
            context.server().execute(() -> {
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    if (ChatRemasteredState.hasModInstalled(p.getUUID())) {
                        ServerPlayNetworking.send(p, packet);
                    }
                }
            });
        });
    }

    private DeleteImageHandler() {
    }
}
