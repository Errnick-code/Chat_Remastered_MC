package dev.errnick.chatremasteredplugin.net.handler;

import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.moderation.ModerationActions;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import dev.errnick.chatremasteredplugin.net.packet.PhotoDeniedPacket;
import dev.errnick.chatremasteredplugin.net.packet.ReplyChatPacket;
import dev.errnick.chatremasteredplugin.net.packet.ReplyMetaPacket;
import dev.errnick.chatremasteredplugin.text.LegacyNickParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ReplyHandler implements PluginMessageListener {

    public static void register() {
        MessengerService.registerIncoming(ModProtocol.CH_REPLY_META, new ReplyHandler());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PluginState.hasModInstalled(player.getUniqueId())) {
            return;
        }
        ReplyMetaPacket payload = ReplyMetaPacket.read(new PacketByteBuf(message));

        if (ModerationActions.isEffectivelyMuted(player.getUniqueId())) {
            Bukkit.getLogger().info("[Chat Remastered] " + player.getName() + " tried to send reply but is muted");
            MessengerService.send(player, ModProtocol.CH_PHOTO_DENIED, new PhotoDeniedPacket("muted").write());
            return;
        }
        PluginState.recentModReply.put(player.getUniqueId(), System.currentTimeMillis());
        String senderName = player.getName();
        String senderComponentJson = LegacyNickParser.parseLegacyNickToJson(senderName);
        String text = payload.text().length() > 256 ? payload.text().substring(0, 256) : payload.text();

        byte[] replyData = new ReplyChatPacket(
                senderName, senderComponentJson, text,
                payload.replyToSender(), payload.replyToText(), payload.replyToImageId()
        ).write();
        String plainMsg = "<" + senderName + "> " + text;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isFullChat(p)) {
                if (PluginState.hasModInstalled(p.getUniqueId())) {
                    MessengerService.send(p, ModProtocol.CH_REPLY_CHAT, replyData);
                } else {
                    p.sendMessage(plainMsg);
                }
            }
        }
    }

    private static boolean isFullChat(Player p) {
        return true;
    }
}
