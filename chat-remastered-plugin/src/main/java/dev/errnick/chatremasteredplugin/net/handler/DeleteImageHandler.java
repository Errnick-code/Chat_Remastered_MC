package dev.errnick.chatremasteredplugin.net.handler;

import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import dev.errnick.chatremasteredplugin.net.packet.DeleteImagePacket;
import dev.errnick.chatremasteredplugin.net.packet.ImageDeletedPacket;
import dev.errnick.chatremasteredplugin.tcp.ImageTcpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;

public final class DeleteImageHandler implements PluginMessageListener {

    public static void register() {
        MessengerService.registerIncoming(ModProtocol.CH_DELETE_IMAGE, new DeleteImageHandler());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        DeleteImagePacket payload = DeleteImagePacket.read(new PacketByteBuf(message));
        String imageId = payload.imageId();

        UUID owner = PluginState.imageOwners.get(imageId);
        if (owner == null || !owner.equals(player.getUniqueId())) {
            return;
        }

        PluginState.imageOwners.remove(imageId);
        ImageTcpServer.deleteImage(imageId);

        byte[] data = new ImageDeletedPacket(imageId, false).write();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (PluginState.hasModInstalled(p.getUniqueId())) {
                MessengerService.send(p, ModProtocol.CH_IMAGE_DELETED, data);
            }
        }
    }
}
