package dev.errnick.chatremasteredplugin.net.handler;

import com.destroystokyo.paper.ClientOption;
import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.moderation.ModerationActions;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import dev.errnick.chatremasteredplugin.net.packet.EntityChatPacket;
import dev.errnick.chatremasteredplugin.net.packet.PhotoDeniedPacket;
import dev.errnick.chatremasteredplugin.text.LegacyNickParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class EntityChatHandler implements PluginMessageListener {

    public static void register() {
        MessengerService.registerIncoming(ModProtocol.CH_ENTITY_CHAT, new EntityChatHandler());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PluginState.hasModInstalled(player.getUniqueId())) {
            return;
        }
        EntityChatPacket payload = EntityChatPacket.read(new PacketByteBuf(message));

        if (ModerationActions.isEffectivelyMuted(player.getUniqueId())) {
            MessengerService.send(player, ModProtocol.CH_PHOTO_DENIED, new PhotoDeniedPacket("muted").write());
            return;
        }
        String targetPlayerName = payload.targetPlayerName();
        String behavior = payload.behavior();
        if (targetPlayerName.isEmpty() || (!behavior.equals("tocursor") && !behavior.equals("rotate"))) {
            return;
        }
        String senderName = player.getName();
        String senderComponentJson = LegacyNickParser.parseLegacyNickToJson(senderName);
        String caption = payload.caption().length() > 256 ? payload.caption().substring(0, 256) : payload.caption();

        byte[] outData = new EntityChatPacket(senderName, senderComponentJson, targetPlayerName, behavior, caption).write();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isFullChat(p) && PluginState.hasModInstalled(p.getUniqueId())) {
                MessengerService.send(p, ModProtocol.CH_ENTITY_CHAT, outData);
            }
        }

        String fallbackLabel = "[player:" + targetPlayerName + "]";
        String fallbackText = !caption.isEmpty() ? fallbackLabel + " " + caption : fallbackLabel;
        Component line = Component.text("<" + senderName + "> " + fallbackText);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isFullChat(p) && !PluginState.hasModInstalled(p.getUniqueId())) {
                p.sendMessage(line);
            }
        }
    }

    private static boolean isFullChat(Player p) {
        return p.getClientOption(ClientOption.CHAT_VISIBILITY) == ClientOption.ChatVisibility.FULL;
    }
}
