package dev.errnicraft.chatremastered.server;

import dev.errnicraft.chatremastered.server.config.ServerConfig;
import dev.errnicraft.chatremastered.server.moderation.BanList;
import dev.errnicraft.chatremastered.server.moderation.MuteList;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatRemasteredState {

    public static final long REPLY_SUPPRESS_WINDOW_MS = 2000L;

    public static final Set<UUID> modPlayers = Collections.synchronizedSet(new java.util.HashSet<>());

    public static final Map<UUID, String> playerTokens = Collections.synchronizedMap(new java.util.HashMap<>());

    public static final Set<UUID> bhMutedByUs = Collections.synchronizedSet(new java.util.HashSet<>());

    public static final Map<String, UUID> imageOwners = Collections.synchronizedMap(new java.util.HashMap<>());

    public static final Map<UUID, Long> recentModReply = new ConcurrentHashMap<>();

    public static final Map<String, PendingBroadcast> pendingBroadcasts = Collections.synchronizedMap(new java.util.HashMap<>());

    public static final Map<String, PendingBroadcast> externalPendingBroadcasts = Collections.synchronizedMap(new java.util.HashMap<>());

    public static final Set<String> groupStubSent = Collections.synchronizedSet(new java.util.HashSet<>());

    public static final BanList banList = new BanList();
    public static final MuteList muteList = new MuteList();

    public static volatile MinecraftServer currentServer;

    public static volatile ServerConfig cachedConfig;

    public static boolean hasModInstalled(UUID uuid) {
        return modPlayers.contains(uuid);
    }

    public static boolean isPhotoBanned(UUID uuid) {
        return banList.isBanned(uuid);
    }

    public static boolean isMuted(UUID uuid) {
        return muteList.isMuted(uuid);
    }

    private ChatRemasteredState() {
    }

    public record PendingBroadcast(
            String sender,
            String caption,
            UUID senderUuid,
            int width,
            int height,
            String replyToSender,
            String groupId,
            int groupIndex,
            int groupCount
    ) {
        public PendingBroadcast(String sender, String caption, UUID senderUuid, int width, int height) {
            this(sender, caption, senderUuid, width, height, "", "", 0, 1);
        }
    }
}
