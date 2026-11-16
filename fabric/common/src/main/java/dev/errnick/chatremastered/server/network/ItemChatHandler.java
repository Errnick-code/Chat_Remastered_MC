package dev.errnick.chatremastered.server.network;

import dev.errnick.chatremastered.network.packet.ItemChatPacket;
import dev.errnick.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnick.chatremastered.server.ChatRemasteredState;
import dev.errnick.chatremastered.server.LuckPermsBridge;
import dev.errnick.chatremastered.server.groups.GroupNetwork;
import dev.errnick.chatremastered.server.moderation.ModerationActions;
import dev.errnick.chatremastered.server.tgbridge.TgBridgeCompat;
import dev.errnick.chatremastered.text.LegacyNickParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
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
            if (!LuckPermsBridge.check(context.player().getUUID(), "chatremastered.chat.item", true)) {
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
            CompoundTag itemNbt = payload.itemNbt();
            String senderName = player.getName().getString();
            Component rawComp = player.getDisplayName() != null ? player.getDisplayName() : Component.literal(senderName);
            Component senderComp = LegacyNickParser.parseLegacyNick(rawComp, senderName);
            String caption = payload.caption().length() > 256 ? payload.caption().substring(0, 256) : payload.caption();

            ItemChatPacket outPacket = new ItemChatPacket(
                    senderName, senderComp, itemNamespace, itemPath, itemNbt, caption);

            if (!GroupNetwork.tryGroupBroadcast(context.server(), player, (p, g) -> {
                ItemChatPacket groupPacket = new ItemChatPacket(
                        senderName,
                        GroupNetwork.groupNick(senderName, g),
                        itemNamespace, itemPath, itemNbt,
                        caption,
                        GroupNetwork.groupLabelLegacy(g),
                        GroupNetwork.groupBodyRgb(g));
                ServerPlayNetworking.send(p, groupPacket);
            })) {
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    if (p.getChatVisibility() == ChatVisiblity.FULL && ChatRemasteredState.hasModInstalled(p.getUUID())) {
                        ServerPlayNetworking.send(p, outPacket);
                    }
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
