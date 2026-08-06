package dev.errnicraft.chatremastered.server.network;

import dev.errnicraft.chatremastered.network.packet.ItemChatPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import dev.errnicraft.chatremastered.server.moderation.ModerationActions;
import dev.errnicraft.chatremastered.server.tgbridge.TgBridgeCompat;
import dev.errnicraft.chatremastered.text.LegacyNickParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

public final class ItemChatHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ItemChatPacket.TYPE, (payload, context) -> {
            if (!ChatRemasteredState.hasModInstalled(context.player().getUUID())) {
                return;
            }
            ServerPlayer player = context.player();
            if (ModerationActions.isEffectivelyMuted(player.getUUID())) {
                ServerPlayNetworking.send(player, new PhotoDeniedPacket("muted"));
                return;
            }
            String itemNamespace = payload.itemNamespace();
            String itemPath = payload.itemPath();
            if (itemNamespace.isEmpty() || itemPath.isEmpty()) {
                return;
            }
            String itemNbt = payload.itemNbt().length() > 512
                    ? payload.itemNbt().substring(0, 512) : payload.itemNbt();
            String senderName = player.getName().getString();
            Component rawComp = player.getDisplayName() != null ? player.getDisplayName() : Component.literal(senderName);
            Component senderComp = LegacyNickParser.parseLegacyNick(rawComp, senderName);
            String caption = payload.caption().length() > 256 ? payload.caption().substring(0, 256) : payload.caption();

            ItemChatPacket outPacket = new ItemChatPacket(
                    senderName, senderComp, itemNamespace, itemPath, itemNbt, caption);

            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                if (p.getChatVisibility() == ChatVisiblity.FULL && ChatRemasteredState.hasModInstalled(p.getUUID())) {
                    ServerPlayNetworking.send(p, outPacket);
                }
            }

            String fallbackLabel = "[item:" + itemNamespace + ":" + itemPath + "]";
            String fallbackText = !caption.isEmpty() ? fallbackLabel + " " + caption : fallbackLabel;
            ChatType.Bound chatType = ChatType.bind(
                    ChatType.CHAT, player.level().registryAccess(), Component.literal(senderName));
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                if (p.getChatVisibility() == ChatVisiblity.FULL && !ChatRemasteredState.hasModInstalled(p.getUUID())) {
                    p.connection.sendDisguisedChatMessage(Component.literal(fallbackText), chatType);
                }
            }
            if (TgBridgeCompat.isAvailable()) {
                TgBridgeCompat.sendPlainTextStub(senderName, fallbackText);
            }
        });
    }

    private ItemChatHandler() {
    }
}
