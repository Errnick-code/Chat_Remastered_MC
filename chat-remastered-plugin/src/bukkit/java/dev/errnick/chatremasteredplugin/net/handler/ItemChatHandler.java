package dev.errnick.chatremasteredplugin.net.handler;

import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.moderation.ModerationActions;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import dev.errnick.chatremasteredplugin.net.packet.ItemChatPacket;
import dev.errnick.chatremasteredplugin.net.packet.PhotoDeniedPacket;
import dev.errnick.chatremasteredplugin.text.LegacyNickParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ItemChatHandler implements PluginMessageListener {

    public static void register() {
        MessengerService.registerIncoming(ModProtocol.CH_ITEM_CHAT, new ItemChatHandler());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PluginState.hasModInstalled(player.getUniqueId())) {
            return;
        }
        ItemChatPacket payload = ItemChatPacket.read(new PacketByteBuf(message));

        if (ModerationActions.isEffectivelyMuted(player.getUniqueId())) {
            MessengerService.send(player, ModProtocol.CH_PHOTO_DENIED, new PhotoDeniedPacket("muted").write());
            return;
        }
        String itemNamespace = payload.itemNamespace();
        String itemPath = payload.itemPath();
        if (itemNamespace.isEmpty() || itemPath.isEmpty()) {
            return;
        }
        String itemNbt = payload.itemNbt().length() > 512
                ? payload.itemNbt().substring(0, 512) : payload.itemNbt();
        String senderName = player.getName();
        String senderComponentJson = LegacyNickParser.parseLegacyNickToJson(senderName);
        String caption = payload.caption().length() > 256 ? payload.caption().substring(0, 256) : payload.caption();

        byte[] outData = new ItemChatPacket(
                senderName, senderComponentJson, itemNamespace, itemPath, itemNbt, caption).write();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isFullChat(p) && PluginState.hasModInstalled(p.getUniqueId())) {
                MessengerService.send(p, ModProtocol.CH_ITEM_CHAT, outData);
            }
        }

        String fallbackLabel = "[item:" + itemNamespace + ":" + itemPath + "]";
        String fallbackText = !caption.isEmpty() ? fallbackLabel + " " + caption : fallbackLabel;
        String line = "<" + senderName + "> " + fallbackText;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isFullChat(p) && !PluginState.hasModInstalled(p.getUniqueId())) {
                p.sendMessage(line);
            }
        }
    }

    private static boolean isFullChat(Player p) {
        return true;
    }
}
