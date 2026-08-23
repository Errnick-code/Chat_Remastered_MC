package dev.errnicraft.chatremastered.server.groups;

import net.minecraft.ChatFormatting;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChatGroupManager {
    private static final Map<String, ChatGroup> GROUPS = new HashMap<>();
    private static final Map<UUID, String> PLAYER_GROUP = new HashMap<>();
    private static final Map<UUID, String> PENDING_INVITE = new HashMap<>();

    public static final Map<String, ChatFormatting> COLORS = new HashMap<>();

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
        return null;
    }

    public static String delete(ChatGroup g, UUID caller) {
        if (!g.isOwner(caller)) {
            return "chatremastered.group.delete_only_owner";
        }
        for (UUID m : g.members) {
            PLAYER_GROUP.remove(m);
        }
        GROUPS.remove(g.name.toLowerCase());
        return null;
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
    }

    public static void leave(UUID player, ChatGroup g) {
        g.members.remove(player);
        g.canInvite.remove(player);
        g.canKick.remove(player);
        if (PLAYER_GROUP.get(player) != null && PLAYER_GROUP.get(player).equals(g.name.toLowerCase())) {
            PLAYER_GROUP.remove(player);
        }
    }

    public static String leavePlayer(UUID player) {
        ChatGroup g = groupOf(player);
        if (g == null) {
            return "chatremastered.group.send_not_in_group";
        }
        boolean owner = g.isOwner(player);
        leave(player, g);
        g.invited.remove(player);
        if (!owner || g.members.isEmpty()) {
            return "chatremastered.group.you_left";
        }
        UUID next = g.members.iterator().next();
        ChatGroup ng = new ChatGroup(g.name, next, "?", g.color);
        ng.members.addAll(g.members);
        ng.canInvite.addAll(g.canInvite);
        ng.canKick.addAll(g.canKick);
        ng.invited.addAll(g.invited);
        GROUPS.put(ng.name.toLowerCase(), ng);
        PLAYER_GROUP.put(next, ng.name.toLowerCase());
        return "chatremastered.group.left_owner_transfer";
    }

    public static void setPendingInvite(UUID player, String groupName) {
        PENDING_INVITE.put(player, groupName.toLowerCase());
    }

    public static String takePendingInvite(UUID player) {
        return PENDING_INVITE.remove(player);
    }

    public static String colorError(String msg) {
        return msg;
    }
}

