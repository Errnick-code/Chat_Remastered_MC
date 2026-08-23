package dev.errnicraft.chatremastered.server.groups;

import net.minecraft.ChatFormatting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ChatGroup {
    public final String name;
    public UUID owner;
    public String ownerName;
    public final ChatFormatting color;
    public final Set<UUID> members = new HashSet<>();
    public final Set<UUID> invited = new HashSet<>();
    public final Set<UUID> canInvite = new HashSet<>();
    public final Set<UUID> canKick = new HashSet<>();

    public ChatGroup(String name, UUID owner, String ownerName, ChatFormatting color) {
        this.name = name;
        this.owner = owner;
        this.ownerName = ownerName;
        this.color = color;
        this.members.add(owner);
    }

    public boolean isOwner(UUID u) {
        return owner.equals(u);
    }

    public boolean canInvite(UUID u) {
        return isOwner(u) || canInvite.contains(u);
    }

    public boolean canKick(UUID u) {
        return isOwner(u) || canKick.contains(u);
    }

    public ChatFormatting textColor() {
        return color;
    }
}
