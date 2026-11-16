package dev.errnick.chatremasteredplugin.net.handler;

import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import dev.errnick.chatremasteredplugin.net.packet.AdminDeleteImagePacket;
import dev.errnick.chatremasteredplugin.net.packet.ImageDeletedPacket;
import dev.errnick.chatremasteredplugin.tcp.ImageTcpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class AdminDeleteImageHandler implements PluginMessageListener {

    public static void register() {
        MessengerService.registerIncoming(ModProtocol.CH_ADMIN_DELETE_IMAGE, new AdminDeleteImageHandler());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        AdminDeleteImagePacket payload = AdminDeleteImagePacket.read(new PacketByteBuf(message));
        String imageId = payload.imageId();

        if (!player.isOp()) {
            return;
        }

        PluginState.imageOwners.remove(imageId);
        ImageTcpServer.deleteImage(imageId);

        byte[] data = new ImageDeletedPacket(imageId, true).write();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (PluginState.hasModInstalled(p.getUniqueId())) {
                MessengerService.send(p, ModProtocol.CH_IMAGE_DELETED, data);
            }
        }
    }
}
