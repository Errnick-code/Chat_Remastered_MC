package dev.errnick.chatremasteredplugin.groups;

import dev.errnick.chatremasteredplugin.net.MessengerService;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.PacketByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class Groups {

    public static final Map<String, ChatGroup> GROUPS = new HashMap<>();
    public static final Map<UUID, String> PLAYER_GROUP = new HashMap<>();
    public static final Set<UUID> GROUP_CHANNEL_ON = new HashSet<>();

    public static class ChatGroup {
        public final String name;
        public UUID owner;
        public final ChatColor color;
        public final Set<UUID> members = new HashSet<>();
        public final Set<UUID> invited = new HashSet<>();
        public final Set<UUID> canInvite = new HashSet<>();
        public final Set<UUID> canKick = new HashSet<>();

        public ChatGroup(String name, UUID owner, ChatColor color) {
            this.name = name;
            this.owner = owner;
            this.color = color;
            this.members.add(owner);
        }

        public boolean isOwner(UUID u) { return owner.equals(u); }
        public boolean canInvite(UUID u) { return isOwner(u) || canInvite.contains(u); }
        public boolean canKick(UUID u) { return isOwner(u) || canKick.contains(u); }
    }

    private Groups() {
    }

    public static void registerIncoming() {
        MessengerService.registerIncoming(ModProtocol.CH_GROUP_MODE, (channel, player, message) -> {
            PacketByteBuf buf = new PacketByteBuf(message);
            boolean enabled = buf.readBoolean();
            if (enabled) GROUP_CHANNEL_ON.add(player.getUniqueId());
            else GROUP_CHANNEL_ON.remove(player.getUniqueId());
        });
    }

    public static ChatGroup groupOf(UUID u) {
        String key = PLAYER_GROUP.get(u);
        return key == null ? null : GROUPS.get(key);
    }

    public static ChatGroup byName(String name) {
        return GROUPS.get(name.toLowerCase());
    }

    public static String create(String name, UUID owner, ChatColor color) {
        String key = name.toLowerCase();
        if (name.length() > 8) return "too_long";
        if (GROUPS.containsKey(key)) return "duplicate";
        if (groupOf(owner) != null) return "already_in";
        GROUPS.put(key, new ChatGroup(name, owner, color));
        PLAYER_GROUP.put(owner, key);
        return null;
    }

    public static void join(UUID u, ChatGroup g) {
        ChatGroup prev = groupOf(u);
        if (prev != null && prev != g) prev.members.remove(u);
        g.members.add(u);
        g.invited.remove(u);
        PLAYER_GROUP.put(u, g.name.toLowerCase());
    }

    public static void leave(UUID u, ChatGroup g) {
        g.members.remove(u);
        g.canInvite.remove(u);
        g.canKick.remove(u);
        if (g.name.toLowerCase().equals(PLAYER_GROUP.get(u))) PLAYER_GROUP.remove(u);
    }

    public static boolean isChannelOn(UUID u) {
        return GROUP_CHANNEL_ON.contains(u);
    }

    public static boolean tryBroadcast(Player sender, BiConsumer<Player, ChatGroup> perMember) {
        ChatGroup g = groupOf(sender.getUniqueId());
        if (g == null || !isChannelOn(sender.getUniqueId())) return false;
        for (UUID m : g.members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) perMember.accept(p, g);
        }
        return true;
    }

    public static void broadcast(ChatGroup g, String legacyText) {
        for (UUID m : g.members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) p.sendMessage(legacyText);
        }
    }

    public static void sendState(Player p) {
        ChatGroup g = groupOf(p.getUniqueId());
        PacketByteBuf buf = new PacketByteBuf();
        buf.writeUtf("");
        buf.writeBoolean(g != null);
        buf.writeUtf(g == null ? "" : g.name);
        MessengerService.send(p, ModProtocol.CH_GROUP_CHAT, buf.toByteArray());
    }

    public static int bodyRgb(ChatGroup g) {
        String h = toHex(g.color).substring(1);
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int gr = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        r = Math.min(255, (int) Math.round(r + (255 - r) * 0.35));
        gr = Math.min(255, (int) Math.round(gr + (255 - gr) * 0.35));
        b = Math.min(255, (int) Math.round(b + (255 - b) * 0.35));
        return (r << 16) | (gr << 8) | b;
    }

    public static String labelLegacy(ChatGroup g) {
        return g.color + "[" + g.name + "]";
    }

private static String toHex(ChatColor c) {
        switch (c) {
            case RED: return "#c84040";
            case GOLD: return "#caa53d";
            case YELLOW: return "#d9c34a";
            case GREEN: return "#54a04f";
            case DARK_GREEN: return "#2e7a32";
            case AQUA: return "#4a9fb0";
            case DARK_AQUA: return "#2f6f80";
            case BLUE: return "#5566d6";
            case DARK_BLUE: return "#33418f";
            case LIGHT_PURPLE: return "#b05fb0";
            case DARK_PURPLE: return "#7a3b7a";
            default: return "#aaaaaa";
        }
    }
}
