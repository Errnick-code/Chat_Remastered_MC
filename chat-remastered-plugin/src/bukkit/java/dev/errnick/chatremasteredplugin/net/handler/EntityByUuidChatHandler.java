package dev.errnick.chatremasteredplugin.net.handler;

import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.moderation.ModerationActions;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import dev.errnick.chatremasteredplugin.net.packet.EntityByUuidChatPacket;
import dev.errnick.chatremasteredplugin.net.packet.EntityChatPacket;
import dev.errnick.chatremasteredplugin.net.packet.EntityMobChatPacket;
import dev.errnick.chatremasteredplugin.net.packet.PhotoDeniedPacket;
import dev.errnick.chatremasteredplugin.text.LegacyNickParser;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;

public final class EntityByUuidChatHandler implements PluginMessageListener {

    public static void register() {
        MessengerService.registerIncoming(ModProtocol.CH_ENTITY_BY_UUID_CHAT, new EntityByUuidChatHandler());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PluginState.hasModInstalled(player.getUniqueId())) {
            return;
        }
        EntityByUuidChatPacket payload = EntityByUuidChatPacket.read(new PacketByteBuf(message));

        if (ModerationActions.isEffectivelyMuted(player.getUniqueId())) {
            MessengerService.send(player, ModProtocol.CH_PHOTO_DENIED, new PhotoDeniedPacket("muted").write());
            return;
        }
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(payload.uuid());
        } catch (IllegalArgumentException e) {
            return;
        }
        Entity target = Bukkit.getEntity(targetUuid);
        if (target == null) {
            return;
        }
        String senderName = player.getName();
        String senderComponentJson = LegacyNickParser.parseLegacyNickToJson(senderName);
        String caption = payload.caption().length() > 256 ? payload.caption().substring(0, 256) : payload.caption();

        if (target instanceof Player targetPlayer) {
            byte[] outData = new EntityChatPacket(
                    senderName, senderComponentJson, targetPlayer.getName(), "rotate", caption).write();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isFullChat(p) && PluginState.hasModInstalled(p.getUniqueId())) {
                    MessengerService.send(p, ModProtocol.CH_ENTITY_CHAT, outData);
                }
            }
            return;
        }

        NamespacedKey entityId = target.getType().getKey();
        byte[] outData = new EntityMobChatPacket(
                senderName, senderComponentJson, entityId.getNamespace(), entityId.getKey(), "",
                "rotate", -1, 0, 0, caption).write();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isFullChat(p) && PluginState.hasModInstalled(p.getUniqueId())) {
                MessengerService.send(p, ModProtocol.CH_ENTITY_MOB_CHAT, outData);
            }
        }
    }

    private static boolean isFullChat(Player p) {
        return true;
    }
}
