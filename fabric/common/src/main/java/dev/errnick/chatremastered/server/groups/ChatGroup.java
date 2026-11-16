package dev.errnick.chatremastered.server.groups;

import net.minecraft.ChatFormatting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ChatGroup {
    public final String name;

    public volatile UUID owner;
    public volatile String ownerName;
    public final ChatFormatting color;
    public final Set<UUID> members = new HashSet<>();
    public final Set<UUID> invited = new HashSet<>();
    public final Set<UUID> canInvite = new HashSet<>();
    public final Set<UUID> canKick = new HashSet<>();

    public final Set<UUID> admins = new HashSet<>();

    public ChatGroup(String name, UUID owner, String ownerName, ChatFormatting color) {
        this.name = name;
        this.owner = owner;
        this.ownerName = ownerName;
        this.color = color;
        if (owner != null) {
            this.members.add(owner);
        }
    }

    public boolean isOwner(UUID u) {
        return u != null && u.equals(owner);
    }

    public boolean isAdmin(UUID u) {
        return admins.contains(u);
    }

    public boolean canInvite(UUID u) {
        return isOwner(u) || isAdmin(u) || canInvite.contains(u);
    }

    public boolean canKick(UUID u) {
        return isOwner(u) || isAdmin(u) || canKick.contains(u);
    }

    public ChatFormatting textColor() {
        return color;
    }
}
