package dev.errnicraft.chatremastered.server.groups;

import dev.errnicraft.chatremastered.network.packet.GroupChatPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GroupNetwork {

    private static final Map<UUID, Boolean> GROUP_CHANNEL = new HashMap<>();

    private GroupNetwork() {
    }

    public static boolean isGroupChannel(UUID player) {
        return GROUP_CHANNEL.getOrDefault(player, false);
    }

    public static void setGroupChannel(UUID player, boolean on) {
        if (on) {
            GROUP_CHANNEL.put(player, true);
        } else {
            GROUP_CHANNEL.remove(player);
        }
    }

    public static boolean tryGroupBroadcast(MinecraftServer server, ServerPlayer sender,
            java.util.function.BiConsumer<ServerPlayer, ChatGroup> perMember) {
        ChatGroup g = ChatGroupManager.groupOf(sender.getUUID());
        if (g == null || !isGroupChannel(sender.getUUID())) {
            return false;
        }
        for (UUID m : g.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null && p.getChatVisibility() == net.minecraft.world.entity.player.ChatVisiblity.FULL) {
                perMember.accept(p, g);
            }
        }
        return true;
    }

    public static boolean tryGroupBroadcast(MinecraftServer server, java.util.UUID senderUuid,
            java.util.function.BiConsumer<ServerPlayer, ChatGroup> perMember) {
        ChatGroup g = ChatGroupManager.groupOf(senderUuid);
        if (g == null || !isGroupChannel(senderUuid)) {
            return false;
        }
        for (UUID m : g.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null && p.getChatVisibility() == net.minecraft.world.entity.player.ChatVisiblity.FULL) {
                perMember.accept(p, g);
            }
        }
        return true;
    }

    public static void register() {
        System.out.println("GroupChatPacket receiver registered");
        ServerPlayNetworking.registerGlobalReceiver(GroupChatPacket.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            String text = payload.text();
            server.execute(() -> handleText(server, player, text));
        });
        ServerPlayNetworking.registerGlobalReceiver(dev.errnicraft.chatremastered.network.packet.GroupModePacket.TYPE, (payload, context) -> {
            setGroupChannel(context.player().getUUID(), payload.enabled());
        });
    }

    private static void handleText(MinecraftServer server, ServerPlayer player, String text) {
        ChatGroup g = ChatGroupManager.groupOf(player.getUUID());
        if (g == null) {
            player.sendSystemMessage(Component.translatable("chatremastered.group.send_not_in_group")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        String safe = text.length() > 256 ? text.substring(0, 255) + "…" : text;
        MutableComponent msg = Component.empty()
                .append(Component.literal("[" + g.name + "] ")
                        .withStyle(s -> s.withColor(g.color).withItalic(true)))
                .append(Component.literal("<")
                        .withStyle(s -> s.withColor(nickColorRgb(g))))
                .append(groupNick(player.getName().getString(), g))
                .append(Component.literal("> ")
                        .withStyle(s -> s.withColor(nickColorRgb(g))))
                .append(Component.literal(safe)
                        .withStyle(s -> s.withColor(bodyColorRgb(g))));
        for (UUID m : g.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                p.sendSystemMessage(msg);
            }
        }
    }

    public static void sendState(ServerPlayer player) {
        ChatGroup g = ChatGroupManager.groupOf(player.getUUID());
        ServerPlayNetworking.send(player, GroupChatPacket.state(g != null, g == null ? "" : g.name));
    }

    public static ChatFormatting nickColor(ChatGroup g) {
        return g.color == ChatFormatting.YELLOW ? ChatFormatting.GOLD : g.color;
    }

    private static int shadeRgb(int rgb, double factor) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        if (factor >= 1.0) {
            double t = factor - 1.0;
            r = (int) Math.min(255, Math.round(r + (255 - r) * t));
            gr = (int) Math.min(255, Math.round(gr + (255 - gr) * t));
            b = (int) Math.min(255, Math.round(b + (255 - b) * t));
        } else {
            r = (int) Math.max(0, Math.round(r * factor));
            gr = (int) Math.max(0, Math.round(gr * factor));
            b = (int) Math.max(0, Math.round(b * factor));
        }
        return (r << 16) | (gr << 8) | b;
    }

    private static int baseRgb(ChatGroup g) {
        Integer c = g.color.getColor();
        return c != null ? c : 0xFFFFFF;
    }

    public static net.minecraft.network.chat.TextColor nickColorRgb(ChatGroup g) {
        return net.minecraft.network.chat.TextColor.fromRgb(shadeRgb(baseRgb(g), 0.62));
    }

    public static net.minecraft.network.chat.TextColor bodyColorRgb(ChatGroup g) {
        return net.minecraft.network.chat.TextColor.fromRgb(shadeRgb(baseRgb(g), 1.35));
    }

    public static MutableComponent groupNick(String playerName, ChatGroup g) {
        return Component.literal(playerName)
                .withStyle(s -> s.withColor(nickColorRgb(g)));
    }

    public static String groupLabelLegacy(ChatGroup g) {
        return "§" + legacyCode(g.color) + "§o[" + g.name + "]";
    }

    public static int groupBodyRgb(ChatGroup g) {
        return shadeRgb(baseRgb(g), 1.35);
    }

    public static String groupBodyLegacy(ChatGroup g, String text) {
        return "\u00a7" + legacyCode(g.textColor()) + (text == null ? "" : text);
    }

    private static char legacyCode(ChatFormatting colorFormatting) {
        return "0123456789abcdef".charAt(colorFormatting.ordinal());
    }

    public static ChatFormatting darker(ChatGroup g) {
        return switch (g.color) {
            case YELLOW -> ChatFormatting.GOLD;
            case RED -> ChatFormatting.DARK_RED;
            case GREEN -> ChatFormatting.DARK_GREEN;
            case AQUA -> ChatFormatting.DARK_AQUA;
            case BLUE -> ChatFormatting.DARK_BLUE;
            case LIGHT_PURPLE -> ChatFormatting.DARK_PURPLE;
            default -> g.color;
        };
    }

    public static Component systemDark(ChatGroup g, String message) {
        return Component.literal(message).withStyle(s -> s.withColor(darker(g)).withItalic(true));
    }

    public static void broadcastToGroup(ChatGroup g, Component msg) {
        MinecraftServer server = ChatRemasteredState.currentServer;
        if (server == null) {
            return;
        }
        for (UUID m : g.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                p.sendSystemMessage(msg);
            }
        }
    }
}
