package dev.errnick.chatremastered.server.network;

import dev.errnick.chatremastered.network.packet.EntityMobChatPacket;
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

public final class EntityMobChatHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EntityMobChatPacket.TYPE, (payload, context) -> {
            if (!ChatRemasteredState.hasModInstalled(context.player().getUUID())) {
                return;
            }
            if (!LuckPermsBridge.check(context.player().getUUID(), "chatremastered.chat.mob", true)) {
                return;
            }
            ServerPlayer player = context.player();
            if (ModerationActions.isEffectivelyMuted(player.getUUID())) {
                ServerPlayNetworking.send(player, new PhotoDeniedPacket("muted"));
                return;
            }
            String entityNamespace = payload.entityNamespace();
            String entityPath = payload.entityPath();
            String behavior = payload.behavior();
            if (entityNamespace.isEmpty() || entityPath.isEmpty()
                    || (!behavior.equals("tocursor") && !behavior.equals("rotate"))) {
                return;
            }
            CompoundTag entityNbt = payload.entityNbt();
            String senderName = player.getName().getString();
            Component rawComp = player.getDisplayName() != null ? player.getDisplayName() : Component.literal(senderName);
            Component senderComp = LegacyNickParser.parseLegacyNick(rawComp, senderName);
            String caption = payload.caption().length() > 256 ? payload.caption().substring(0, 256) : payload.caption();
            int size = payload.size() < 0 ? -1 : Math.min(payload.size(), 50000);
            int offsetX = Math.max(-4000, Math.min(payload.offsetX(), 4000));
            int offsetY = Math.max(-4000, Math.min(payload.offsetY(), 4000));

            if (!GroupNetwork.tryGroupBroadcast(context.server(), player, (p, g) -> {
                ServerPlayNetworking.send(p, new EntityMobChatPacket(
                        senderName, GroupNetwork.groupNick(senderName, g), entityNamespace, entityPath, entityNbt, behavior, size, offsetX, offsetY, caption,
                        GroupNetwork.groupLabelLegacy(g), GroupNetwork.groupBodyRgb(g)));
            })) {
            EntityMobChatPacket outPacket = new EntityMobChatPacket(
                    senderName, senderComp, entityNamespace, entityPath, entityNbt, behavior,
                    size, offsetX, offsetY, caption);

            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                if (p.getChatVisibility() == ChatVisiblity.FULL && ChatRemasteredState.hasModInstalled(p.getUUID())) {
                    ServerPlayNetworking.send(p, outPacket);
                }
            }
            }

            String fallbackLabel = "[entity:" + entityNamespace + ":" + entityPath + "]";
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

    private EntityMobChatHandler() {
    }
}
