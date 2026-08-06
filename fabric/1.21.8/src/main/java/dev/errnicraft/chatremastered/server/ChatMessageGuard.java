package dev.errnicraft.chatremastered.server;

import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.server.moderation.ModerationActions;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.UUID;

public final class ChatMessageGuard {

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            UUID uuid = sender.getUUID();

            if (ModerationActions.isBanHammerMuted(uuid)) {
                ServerPlayNetworking.send(sender, new PhotoDeniedPacket("muted_silent"));
                return false;
            }

            if (ChatRemasteredState.isMuted(uuid)) {
                ServerPlayNetworking.send(sender, new PhotoDeniedPacket("muted"));
                return false;
            }

            Long lastReply = ChatRemasteredState.recentModReply.get(uuid);
            if (lastReply != null && System.currentTimeMillis() - lastReply < ChatRemasteredState.REPLY_SUPPRESS_WINDOW_MS) {
                return false;
            }

            return true;
        });
    }

    private ChatMessageGuard() {
    }
}
