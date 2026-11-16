package dev.errnick.chatremasteredplugin.net.handler;

import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.moderation.ModerationActions;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import dev.errnick.chatremasteredplugin.net.packet.EntityMobChatPacket;
import dev.errnick.chatremasteredplugin.net.packet.PhotoDeniedPacket;
import dev.errnick.chatremasteredplugin.text.LegacyNickParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class EntityMobChatHandler implements PluginMessageListener {

    public static void register() {
        MessengerService.registerIncoming(ModProtocol.CH_ENTITY_MOB_CHAT, new EntityMobChatHandler());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PluginState.hasModInstalled(player.getUniqueId())) {
            return;
        }
        EntityMobChatPacket payload = EntityMobChatPacket.read(new PacketByteBuf(message));

        if (ModerationActions.isEffectivelyMuted(player.getUniqueId())) {
            MessengerService.send(player, ModProtocol.CH_PHOTO_DENIED, new PhotoDeniedPacket("muted").write());
            return;
        }
        String entityNamespace = payload.entityNamespace();
        String entityPath = payload.entityPath();
        String behavior = payload.behavior();
        if (entityNamespace.isEmpty() || entityPath.isEmpty()
                || (!behavior.equals("tocursor") && !behavior.equals("rotate"))) {
            return;
        }
        String entityNbt = payload.entityNbt().length() > 512
                ? payload.entityNbt().substring(0, 512) : payload.entityNbt();
        String senderName = player.getName();
        String senderComponentJson = LegacyNickParser.parseLegacyNickToJson(senderName);
        String caption = payload.caption().length() > 256 ? payload.caption().substring(0, 256) : payload.caption();
        int size = payload.size() < 0 ? -1 : Math.min(payload.size(), 50000);
        int offsetX = Math.max(-4000, Math.min(payload.offsetX(), 4000));
        int offsetY = Math.max(-4000, Math.min(payload.offsetY(), 4000));

        byte[] outData = new EntityMobChatPacket(
                senderName, senderComponentJson, entityNamespace, entityPath, entityNbt, behavior,
                size, offsetX, offsetY, caption).write();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isFullChat(p) && PluginState.hasModInstalled(p.getUniqueId())) {
                MessengerService.send(p, ModProtocol.CH_ENTITY_MOB_CHAT, outData);
            }
        }

        String fallbackLabel = "[entity:" + entityNamespace + ":" + entityPath + "]";
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
