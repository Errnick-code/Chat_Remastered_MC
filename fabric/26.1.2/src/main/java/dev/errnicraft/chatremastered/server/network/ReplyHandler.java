package dev.errnicraft.chatremastered.server.network;

import dev.errnicraft.chatremastered.server.tgbridge.TgBridgeCompat;
import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.network.packet.ReplyChatPacket;
import dev.errnicraft.chatremastered.network.packet.ReplyMetaPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import dev.errnicraft.chatremastered.server.moderation.ModerationActions;
import dev.errnicraft.chatremastered.text.LegacyNickParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

public final class ReplyHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ReplyMetaPacket.TYPE, (payload, context) -> {
            if (!ChatRemasteredState.hasModInstalled(context.player().getUUID())) {
                return;
            }
            ServerPlayer player = context.player();

            if (ModerationActions.isEffectivelyMuted(player.getUUID())) {
                System.out.println("[Chat Remastered] " + player.getName().getString() + " tried to send reply but is muted");

                ServerPlayNetworking.send(player, new PhotoDeniedPacket("muted"));
                return;
            }
            ChatRemasteredState.recentModReply.put(player.getUUID(), System.currentTimeMillis());
            String senderName = player.getName().getString();
            Component rawComp = player.getDisplayName() != null ? player.getDisplayName() : Component.literal(senderName);
            Component senderComp = LegacyNickParser.parseLegacyNick(rawComp, senderName);
            String text = payload.text().length() > 256 ? payload.text().substring(0, 256) : payload.text();

            ReplyChatPacket replyPacket = new ReplyChatPacket(
                    senderName,
                    senderComp,
                    text,
                    payload.replyToSender(),
                    payload.replyToText(),
                    payload.replyToImageId()
            );
            Component plainMsg = Component.empty()
                    .append(Component.literal("<"))
                    .append(senderComp)
                    .append(Component.literal("> " + text));

            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                if (p.getChatVisibility() == ChatVisiblity.FULL) {
                    if (ChatRemasteredState.hasModInstalled(p.getUUID())) {
                        ServerPlayNetworking.send(p, replyPacket);
                    } else {
                        p.sendSystemMessage(plainMsg);
                    }
                }
            }

            if (TgBridgeCompat.isAvailable()) {
                TgBridgeCompat.onReplySent(payload.replyToSender(), text, senderName);
            }
        });
    }

    private ReplyHandler() {
    }
}
