package dev.errnicraft.chatremastered.server.network;

import dev.errnicraft.chatremastered.network.packet.EntityByUuidChatPacket;
import dev.errnicraft.chatremastered.network.packet.EntityChatPacket;
import dev.errnicraft.chatremastered.network.packet.EntityMobChatPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import dev.errnicraft.chatremastered.server.moderation.ModerationActions;
import dev.errnicraft.chatremastered.text.LegacyNickParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.UUID;

public final class EntityByUuidChatHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EntityByUuidChatPacket.TYPE, (payload, context) -> {
            if (!ChatRemasteredState.hasModInstalled(context.player().getUUID())) {
                return;
            }
            ServerPlayer player = context.player();
            if (ModerationActions.isEffectivelyMuted(player.getUUID())) {
                ServerPlayNetworking.send(player, new PhotoDeniedPacket("muted"));
                return;
            }
            UUID targetUuid;
            try {
                targetUuid = UUID.fromString(payload.uuid());
            } catch (IllegalArgumentException e) {
                return;
            }
            Entity target = player.level().getEntity(targetUuid);
            if (target == null) {
                return;
            }
            String senderName = player.getName().getString();
            Component rawComp = player.getDisplayName() != null ? player.getDisplayName() : Component.literal(senderName);
            Component senderComp = LegacyNickParser.parseLegacyNick(rawComp, senderName);
            String caption = payload.caption().length() > 256 ? payload.caption().substring(0, 256) : payload.caption();

            if (target instanceof Player targetPlayer) {
                EntityChatPacket outPacket = new EntityChatPacket(
                        senderName, senderComp, targetPlayer.getName().getString(), "rotate", caption);
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    if (p.getChatVisibility() == ChatVisiblity.FULL && ChatRemasteredState.hasModInstalled(p.getUUID())) {
                        ServerPlayNetworking.send(p, outPacket);
                    }
                }
                return;
            }

            Identifier entityId = EntityType.getKey(target.getType());
            TagValueOutput nbtOutput = TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING, target.registryAccess());
            target.saveWithoutId(nbtOutput);
            String targetNbt = nbtOutput.buildResult().toString();
            if (targetNbt.length() > 512) {
                targetNbt = targetNbt.substring(0, 512);
            }
            EntityMobChatPacket outPacket = new EntityMobChatPacket(
                    senderName, senderComp, entityId.getNamespace(), entityId.getPath(), targetNbt,
                    "rotate", -1, 0, 0, caption);
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                if (p.getChatVisibility() == ChatVisiblity.FULL && ChatRemasteredState.hasModInstalled(p.getUUID())) {
                    ServerPlayNetworking.send(p, outPacket);
                }
            }
        });
    }

    private EntityByUuidChatHandler() {
    }
}
