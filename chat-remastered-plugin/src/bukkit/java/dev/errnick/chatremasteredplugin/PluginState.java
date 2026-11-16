package dev.errnick.chatremasteredplugin;

import dev.errnick.chatremasteredplugin.config.ServerConfig;
import dev.errnick.chatremasteredplugin.moderation.BanList;
import dev.errnick.chatremasteredplugin.moderation.MuteList;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginState {

    public static final long REPLY_SUPPRESS_WINDOW_MS = 2000L;

    public static final Set<UUID> modPlayers = Collections.synchronizedSet(new java.util.HashSet<>());

    public static final Map<UUID, String> playerTokens = Collections.synchronizedMap(new java.util.HashMap<>());

    public static final Map<String, UUID> imageOwners = Collections.synchronizedMap(new java.util.HashMap<>());

    public static final Map<UUID, Long> recentModReply = new ConcurrentHashMap<>();

    public static final Map<String, PendingBroadcast> pendingBroadcasts = Collections.synchronizedMap(new java.util.HashMap<>());

    public static final Set<String> groupStubSent = Collections.synchronizedSet(new java.util.HashSet<>());

    public static final BanList banList = new BanList();
    public static final MuteList muteList = new MuteList();

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

    private PluginState() {
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
    }
}
