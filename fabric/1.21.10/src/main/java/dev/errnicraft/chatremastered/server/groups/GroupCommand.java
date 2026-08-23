package dev.errnicraft.chatremastered.server.groups;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.errnicraft.chatremastered.network.packet.GroupModePacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class GroupCommand {

    private static ChatFormatting colorOf(String name) {
        return ChatGroupManager.COLORS.get(name.toLowerCase());
    }

    private static MutableComponent nick(ServerPlayer p, ChatGroup g) {
        return Component.literal(p.getName().getString())
                .withStyle(s -> s.withColor(GroupNetwork.nickColor(g)).withBold(true));
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                literal("chatgroup")
                        .then(literal("create")
                                .then(argument("name", StringArgumentType.string())
                                        .then(argument("color", StringArgumentType.word())
                                                .suggests((c, b) -> suggest(b, ChatGroupManager.COLORS.keySet()))
                                                .executes(ctx -> create(ctx)))))
                        .then(literal("delete").executes(ctx -> delete(ctx)))
                        .then(literal("invite")
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> invite(ctx))))
                        .then(literal("join")
                                .executes(ctx -> listInvites(ctx))
                                .then(argument("name", StringArgumentType.string())
                                        .suggests((c, b) -> suggestGroups(c, b, true))
                                        .executes(ctx -> joinNamed(ctx))))
                        .then(literal("list").executes(ctx -> list(ctx)))
                        .then(literal("leave").executes(ctx -> leave(ctx)))
                        .then(literal("kick")
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> kick(ctx))))
                        .then(literal("allowinvite")
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> allowInvite(ctx))))
                        .then(literal("allowkick")
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> allowKick(ctx))))
        ));
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder b, Iterable<String> options) {
        String left = b.getRemainingLowerCase();
        for (String o : options) {
            if (o.toLowerCase().startsWith(left)) {
                b.suggest(o);
            }
        }
        return b.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestGroups(CommandContext<CommandSourceStack> ctx,
                                                                 SuggestionsBuilder b, boolean invitedOnly) {
        List<String> names = new ArrayList<>();
        ServerPlayer p = ctx.getSource().getPlayer();
        for (ChatGroup g : ChatGroupManager.all()) {
            if (!invitedOnly || (p != null && g.invited.contains(p.getUUID()))) {
                names.add(g.name);
            }
        }
        return suggest(b, names);
    }

    private static CompletableFuture<Suggestions> suggestMembers(CommandContext<CommandSourceStack> ctx,
                                                                  SuggestionsBuilder b) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            return b.buildFuture();
        }
        ChatGroup g = ChatGroupManager.groupOf(p.getUUID());
        if (g == null) {
            return b.buildFuture();
        }
        List<String> names = new ArrayList<>();
        for (UUID m : g.members) {
            if (m.equals(p.getUUID()) || g.isOwner(m)) {
                continue;
            }
            var sp = ChatRemasteredState.currentServer.getPlayerList().getPlayer(m);
            names.add(sp != null ? sp.getName().getString() : m.toString());
        }
        return suggest(b, names);
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        try {
        ServerPlayer p = player(ctx);
        String name = StringArgumentType.getString(ctx, "name");
        String colorName = StringArgumentType.getString(ctx, "color");
        ChatFormatting color = colorOf(colorName);
        if (color == null) {
            p.sendSystemMessage(tr("chatremastered.group.no_color",
                    String.join(", ", ChatGroupManager.COLORS.keySet())).withStyle(ChatFormatting.RED));
            return 0;
        }
        String err = ChatGroupManager.create(name, p.getUUID(), p.getName().getString(), color);
        if (err != null) {
            p.sendSystemMessage(tr(err).withStyle(ChatFormatting.RED));
            return 0;
        }
        ChatGroup created = ChatGroupManager.byName(name);
        p.sendSystemMessage(tr("chatremastered.group.created", name, colorName)
                .withStyle(s -> s.withColor(created.color)));
        GroupNetwork.sendState(p);
        return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        ChatGroup g = ChatGroupManager.groupOf(p.getUUID());
        if (g == null) {
            p.sendSystemMessage(tr("chatremastered.group.not_in_group_cmd").withStyle(ChatFormatting.RED));
            return 0;
        }
        String err = ChatGroupManager.delete(g, p.getUUID());
        if (err != null) {
            p.sendSystemMessage(tr(err).withStyle(ChatFormatting.RED));
            return 0;
        }
        p.sendSystemMessage(tr("chatremastered.group.deleted", g.name).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(ctx);
        ChatGroup g = ChatGroupManager.groupOf(p.getUUID());
        if (g == null) {
            p.sendSystemMessage(tr("chatremastered.group.not_in_group_cmd").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!g.canInvite(p.getUUID())) {
            p.sendSystemMessage(tr("chatremastered.group.no_invite_perm").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (target.getUUID().equals(p.getUUID())) {
            return 0;
        }
        ChatGroup tGrp = ChatGroupManager.groupOf(target.getUUID());
        if (tGrp != null) {
            p.sendSystemMessage(tr("chatremastered.group.already_in_other", target.getName().getString())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        g.invited.add(target.getUUID());
        ChatGroupManager.setPendingInvite(target.getUUID(), g.name);

        target.sendSystemMessage(Component.empty()
                .append(tr("chatremastered.group.invite_prefix", p.getName().getString(), g.name))
                .append(tr("chatremastered.group.join_button")
                        .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                                .withClickEvent(new ClickEvent.RunCommand("/chatgroup join " + g.name)))));
        p.sendSystemMessage(tr("chatremastered.group.invite_sent", target.getName().getString())
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int joinNamed(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        String name = StringArgumentType.getString(ctx, "name");
        ChatGroup g = ChatGroupManager.byName(name);
        if (g == null) {
            p.sendSystemMessage(tr("chatremastered.group.not_found").withStyle(ChatFormatting.RED));
            return 0;
        }
        ChatGroup currentGroup = ChatGroupManager.groupOf(p.getUUID());
        if (currentGroup == g) {
            p.sendSystemMessage(tr("chatremastered.group.already_in").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!g.invited.contains(p.getUUID())) {
            p.sendSystemMessage(tr("chatremastered.group.not_invited").withStyle(ChatFormatting.RED));
            return 0;
        }
        ChatGroupManager.join(p.getUUID(), g);
        GroupNetwork.broadcastToGroup(g, tr("chatremastered.group.member_join", p.getName().getString())
                .withStyle(s -> s.withColor(GroupNetwork.darker(g)).withItalic(true)));
        GroupNetwork.sendState(p);
        return 1;
    }

    private static int listInvites(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        StringBuilder invited = new StringBuilder();
        StringBuilder all = new StringBuilder();
        for (ChatGroup g : ChatGroupManager.all()) {
            if (all.length() > 0) all.append(", ");
            all.append(g.name);
            if (g.invited.contains(p.getUUID())) {
                if (invited.length() > 0) invited.append(", ");
                invited.append(g.name);
            }
        }
        if (invited.length() > 0) {
            p.sendSystemMessage(tr("chatremastered.group.list_invites")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(invited.toString()).withStyle(ChatFormatting.YELLOW)));
        } else {
            p.sendSystemMessage(tr("chatremastered.group.list_invites").withStyle(ChatFormatting.GRAY));
        }
        if (all.length() > 0) {
            p.sendSystemMessage(tr("chatremastered.group.list_all")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(all.toString()).withStyle(ChatFormatting.WHITE)));
        } else {
            p.sendSystemMessage(tr("chatremastered.group.list_empty_hint").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        ChatGroup g = ChatGroupManager.groupOf(p.getUUID());
        if (g == null) {
            p.sendSystemMessage(tr("chatremastered.group.not_in_group_cmd").withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean canK = g.canKick(p.getUUID());
        p.sendSystemMessage(Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(g.name).withStyle(s -> s.withColor(g.color).withBold(true)))
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY)));
        for (UUID m : g.members) {
            var sp = ChatRemasteredState.currentServer.getPlayerList().getPlayer(m);
            boolean online = sp != null;
            String nm = online ? sp.getName().getString() : m.toString();
            MutableComponent line = Component.empty()
                    .append(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(nm).withStyle(online ? g.color : ChatFormatting.DARK_GRAY));
            if (g.isOwner(m)) {
                line.append(tr("chatremastered.group.owner_crown").withStyle(ChatFormatting.GOLD));
            }
            if (!online) {
                line.append(tr("chatremastered.group.list_offline").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (canK && !g.isOwner(m)) {
                line.append(tr("chatremastered.group.list_kick_btn")
                        .withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(
                                new ClickEvent.RunCommand("/chatgroup kick " + nm))));
            }
            p.sendSystemMessage(line);
        }
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        ChatGroup g = ChatGroupManager.groupOf(p.getUUID());
        if (g == null) {
            p.sendSystemMessage(tr("chatremastered.group.not_in_group_cmd").withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean wasOwner = g.isOwner(p.getUUID());
        ChatGroupManager.leavePlayer(p.getUUID());
        p.sendSystemMessage(tr("chatremastered.group.you_left", g.name)
                .withStyle(s -> s.withColor(GroupNetwork.darker(g)).withItalic(true)));
        GroupNetwork.broadcastToGroup(g, tr("chatremastered.group.member_leave", p.getName().getString())
                .withStyle(s -> s.withColor(GroupNetwork.darker(g)).withItalic(true)));
        if (wasOwner) {
            p.sendSystemMessage(tr("chatremastered.group.left_owner_transfer")
                    .withStyle(s -> s.withColor(GroupNetwork.darker(g)).withItalic(true)));
        }
        GroupNetwork.sendState(p);
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(ctx);
        ChatGroup g = ChatGroupManager.groupOf(p.getUUID());
        if (g == null) {
            return 0;
        }
        if (!g.canKick(p.getUUID())) {
            p.sendSystemMessage(tr("chatremastered.group.no_kick_perm").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (g.isOwner(target.getUUID())) {
            p.sendSystemMessage(tr("chatremastered.group.owner_unkickable").withStyle(ChatFormatting.RED));
            return 0;
        }
        ChatGroupManager.leave(target.getUUID(), g);
        target.sendSystemMessage(tr("chatremastered.group.you_kicked", g.name)
                .withStyle(s -> s.withColor(GroupNetwork.darker(g)).withItalic(true)));
        GroupNetwork.broadcastToGroup(g, tr("chatremastered.group.member_kicked", target.getName().getString())
                .withStyle(s -> s.withColor(GroupNetwork.darker(g)).withItalic(true)));
        GroupNetwork.sendState(target);
        return 1;
    }

    private static int allowInvite(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(ctx);
        ChatGroup g = ChatGroupManager.groupOf(p.getUUID());
        if (g == null || !g.isOwner(p.getUUID())) {
            p.sendSystemMessage(tr("chatremastered.group.rights_only_owner").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        g.canInvite.add(target.getUUID());
        target.sendSystemMessage(tr("chatremastered.group.can_invite_now", g.name).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int allowKick(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(ctx);
        ChatGroup g = ChatGroupManager.groupOf(p.getUUID());
        if (g == null || !g.isOwner(p.getUUID())) {
            p.sendSystemMessage(tr("chatremastered.group.rights_only_owner").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        g.canKick.add(target.getUUID());
        target.sendSystemMessage(tr("chatremastered.group.can_kick_now", g.name).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private GroupCommand() {
    }
}
