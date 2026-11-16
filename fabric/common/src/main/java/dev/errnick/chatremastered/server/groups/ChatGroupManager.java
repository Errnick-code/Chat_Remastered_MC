package dev.errnick.chatremastered.server.groups;

import net.minecraft.ChatFormatting;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatGroupManager {
    private static final Map<String, ChatGroup> GROUPS = new HashMap<>();
    private static final Map<UUID, String> PLAYER_GROUP = new HashMap<>();
    private static final Map<UUID, String> PENDING_INVITE = new HashMap<>();

    private static final Map<String, Long> ALL_OFFLINE_SINCE = new HashMap<>();

    private static final long EMPTY_SERVER_TTL_MS = 10 * 60_000L;

    public static final Map<String, ChatFormatting> COLORS = new HashMap<>();

    private static final AtomicBoolean MAINTENANCE_STARTED = new AtomicBoolean(false);

    static {
        COLORS.put("yellow", ChatFormatting.YELLOW);
        COLORS.put("gold", ChatFormatting.GOLD);
        COLORS.put("red", ChatFormatting.RED);
        COLORS.put("dark_red", ChatFormatting.DARK_RED);
        COLORS.put("green", ChatFormatting.GREEN);
        COLORS.put("dark_green", ChatFormatting.DARK_GREEN);
        COLORS.put("aqua", ChatFormatting.AQUA);
        COLORS.put("dark_aqua", ChatFormatting.DARK_AQUA);
        COLORS.put("blue", ChatFormatting.BLUE);
        COLORS.put("dark_blue", ChatFormatting.DARK_BLUE);
        COLORS.put("light_purple", ChatFormatting.LIGHT_PURPLE);
        COLORS.put("dark_purple", ChatFormatting.DARK_PURPLE);
    }

    private ChatGroupManager() {
    }

    public static Collection<ChatGroup> all() {
        return GROUPS.values();
    }

    public static ChatGroup byName(String name) {
        return GROUPS.get(name.toLowerCase());
    }

    public static String create(String name, UUID owner, String ownerName, ChatFormatting color) {
        System.out.println("[ChatGroups] create name=" + name + " color=" + color + " owner=" + ownerName);
        String key = name.toLowerCase();
        if (name.length() > 8) {
            System.out.println("[ChatGroups] create FAIL: name too long");
            return "chatremastered.group.name_too_long";
        }
        if (GROUPS.containsKey(key)) {
            System.out.println("[ChatGroups] create FAIL: duplicate");
            return "chatremastered.group.duplicate";
        }
        if (groupOf(owner) != null) {
            System.out.println("[ChatGroups] create FAIL: already in group");
            return "chatremastered.group.already_in";
        }
        GROUPS.put(key, new ChatGroup(name, owner, ownerName, color));
        PLAYER_GROUP.put(owner, key);
        ALL_OFFLINE_SINCE.remove(key);
        startMaintenance();
        return null;
    }

    public static String delete(ChatGroup g, UUID caller) {
        boolean allowed = g.isOwner(caller) || g.isAdmin(caller);
        if (!allowed) {
            return "chatremastered.group.delete_only_owner";
        }
        deleteSilently(g);
        return null;
    }

    public static void deleteSilently(ChatGroup g) {
        for (UUID m : g.members) {
            PLAYER_GROUP.remove(m);
        }
        GROUPS.remove(g.name.toLowerCase());
        ALL_OFFLINE_SINCE.remove(g.name.toLowerCase());
    }

    public static void deleteQuietly(ChatGroup g, String reason) {
        System.out.println("[ChatGroups] auto-delete '" + g.name + "' (" + reason + ")");
        deleteSilently(g);
    }

    public static ChatGroup groupOf(UUID player) {
        String key = PLAYER_GROUP.get(player);
        return key == null ? null : GROUPS.get(key);
    }

    public static void join(UUID player, ChatGroup g) {
        ChatGroup prev = groupOf(player);
        if (prev != null && prev != g) {
            prev.members.remove(player);
        }
        g.members.add(player);
        g.invited.remove(player);
        PLAYER_GROUP.put(player, g.name.toLowerCase());
        ALL_OFFLINE_SINCE.remove(g.name.toLowerCase());
    }

    public static void joinAsAdmin(UUID player, ChatGroup g) {
        ChatGroup prev = groupOf(player);
        if (prev != null && prev != g) {
            prev.members.remove(player);
            prev.admins.remove(player);
        }
        g.members.add(player);
        g.admins.add(player);
        g.invited.remove(player);
        PLAYER_GROUP.put(player, g.name.toLowerCase());
        ALL_OFFLINE_SINCE.remove(g.name.toLowerCase());
    }

    public static void leave(UUID player, ChatGroup g) {
        g.members.remove(player);
        g.canInvite.remove(player);
        g.canKick.remove(player);
        g.admins.remove(player);
        if (PLAYER_GROUP.get(player) != null && PLAYER_GROUP.get(player).equals(g.name.toLowerCase())) {
            PLAYER_GROUP.remove(player);
        }
    }

    public static String leavePlayer(UUID player) {
        ChatGroup g = groupOf(player);
        if (g == null) {
            return "chatremastered.group.send_not_in_group";
        }
        leave(player, g);
        g.invited.remove(player);

        if (g.members.isEmpty()) {
            deleteSilently(g);
            return "chatremastered.group.you_left";
        }
        return "chatremastered.group.you_left";
    }

    public static void setPendingInvite(UUID player, String groupName) {
        PENDING_INVITE.put(player, groupName.toLowerCase());
    }

    public static String takePendingInvite(UUID player) {
        return PENDING_INVITE.remove(player);
    }

    public static void maintenance(java.util.function.Function<UUID, Boolean> isOnline) {
        long now = System.currentTimeMillis();
        java.util.List<ChatGroup> toDelete = new java.util.ArrayList<>();
        String reason = "";
        for (ChatGroup g : GROUPS.values()) {
            if (g.members.isEmpty()) {
                toDelete.add(g);
                reason = "no members";
                break;
            }
            boolean anyOnline = false;
            for (UUID m : g.members) {
                if (Boolean.TRUE.equals(isOnline.apply(m))) {
                    anyOnline = true;
                    break;
                }
            }
            String key = g.name.toLowerCase();
            if (anyOnline) {
                ALL_OFFLINE_SINCE.remove(key);
                continue;
            }
            long since = ALL_OFFLINE_SINCE.computeIfAbsent(key, k -> now);
            if (now - since >= EMPTY_SERVER_TTL_MS) {
                toDelete.add(g);
                reason = "all members offline > 10 min";
            }
        }
        if (!toDelete.isEmpty()) {
            System.out.println("[ChatGroups] auto-delete '" + toDelete.get(0).name + "' (" + reason + ")");
            for (ChatGroup g : toDelete) {
                deleteSilently(g);
            }
        }
    }

    private static void startMaintenance() {
        if (!MAINTENANCE_STARTED.compareAndSet(false, true)) {
            return;
        }
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChatGroups-Maintenance");
            t.setDaemon(true);
            return t;
        });
        ses.scheduleAtFixedRate(() -> {
            try {
                java.util.function.Function<UUID, Boolean> online = uuid -> {
                    var server = dev.errnick.chatremastered.server.ChatRemasteredState.currentServer;
                    if (server == null || server.getPlayerList() == null) {
                        return Boolean.FALSE;
                    }
                    return server.getPlayerList().getPlayer(uuid) != null;
                };
                maintenance(online);
            } catch (Exception e) {
                System.out.println("[ChatGroups] maintenance error: " + e);
            }
        }, 30L, 30L, TimeUnit.SECONDS);
    }

    public static String colorError(String msg) {
        return msg;
    }
}
