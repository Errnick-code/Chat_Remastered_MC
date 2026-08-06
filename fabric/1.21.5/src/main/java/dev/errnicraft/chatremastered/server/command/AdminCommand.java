package dev.errnicraft.chatremastered.server.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.errnicraft.chatremastered.server.tgbridge.TgBridgeCompat;
import dev.errnicraft.chatremastered.network.packet.ImageDeletedPacket;
import dev.errnicraft.chatremastered.network.packet.PhotoDeniedPacket;
import dev.errnicraft.chatremastered.server.ChatRemasteredState;
import dev.errnicraft.chatremastered.server.moderation.ModerationActions;
import dev.errnicraft.chatremastered.tcp.ImageTcpServer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AdminCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("chat-remastered-admin")

                        .requires(src -> {
                            var server = src.getServer();
                            if (server == null) {
                                return false;
                            }
                            var p = src.getPlayer();
                            return p == null || server.getPlayerList().isOp(p.getGameProfile());
                        })
                        .then(Commands.literal("block-photo")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(AdminCommand::blockPhoto)))
                        .then(Commands.literal("unblock-photo")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(AdminCommand::unblockPhoto)))
                        .then(Commands.literal("mute")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(AdminCommand::mute)))
                        .then(Commands.literal("unmute")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(AdminCommand::unmute)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("imageId", StringArgumentType.word())
                                        .executes(AdminCommand::delete)))
                        .then(Commands.literal("test")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(AdminCommand::test)))
        ));
    }

    private static int blockPhoto(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ModerationActions.banPlayer(target.getUUID(), ctx.getSource().getServer());
        ServerPlayNetworking.send(target, new PhotoDeniedPacket("banned"));
        ctx.getSource().sendSuccess(() ->
                Component.literal("§8[Chat Remastered] §c" + target.getName().getString() + " §7— отправка фото заблокирована."), true);
        return 1;
    }

    private static int unblockPhoto(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ModerationActions.unbanPlayer(target.getUUID(), ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() ->
                Component.literal("§8[Chat Remastered] §a" + target.getName().getString() + " §7— отправка фото разблокирована."), true);
        return 1;
    }

    private static int mute(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ModerationActions.mutePlayer(target.getUUID(), ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() ->
                Component.literal("§8[Chat Remastered] §c" + target.getName().getString() + " §7— фото и ответы заблокированы."), true);
        return 1;
    }

    private static int unmute(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ModerationActions.unmutePlayer(target.getUUID(), ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() ->
                Component.literal("§8[Chat Remastered] §a" + target.getName().getString() + " §7— фото и ответы разблокированы."), true);
        return 1;
    }

    private static int delete(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        String imageId = StringArgumentType.getString(ctx, "imageId");
        var server = ctx.getSource().getServer();
        ImageTcpServer.deleteImage(imageId);
        if (TgBridgeCompat.isAvailable()) {
            TgBridgeCompat.onImageDeleted(imageId);
        }
        ImageDeletedPacket packet = new ImageDeletedPacket(imageId, true);
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ChatRemasteredState.hasModInstalled(player.getUUID())) {
                    ServerPlayNetworking.send(player, packet);
                }
            }
        });
        ctx.getSource().sendSuccess(() ->
                Component.literal("§8[Chat Remastered] §7Фото §f" + imageId + " §7удалено."), true);
        return 1;
    }

    private static int test(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String targetName = target.getName().getString();
        boolean hasMod = ChatRemasteredState.hasModInstalled(target.getUUID());
        boolean isBanned = ChatRemasteredState.isPhotoBanned(target.getUUID());
        String token = ChatRemasteredState.playerTokens.get(target.getUUID());

        ctx.getSource().sendSuccess(() ->
                Component.literal("§8[Chat Remastered] §7--- Проверка игрока §f" + targetName + " §7---"), false);
        ctx.getSource().sendSuccess(() -> hasMod
                ? Component.literal("§8[Chat Remastered] §aMod: §aустановлен ✔")
                : Component.literal("§8[Chat Remastered] §cMod: §cне установлен ✘"), false);
        ctx.getSource().sendSuccess(() -> isBanned
                ? Component.literal("§8[Chat Remastered] §cБан: §cзаблокирован ✘")
                : Component.literal("§8[Chat Remastered] §aБан: §aнет ✔"), false);
        ctx.getSource().sendSuccess(() -> (token != null && !token.isEmpty())
                ? Component.literal("§8[Chat Remastered] §aТокен: §fполучен (" + token.length() + " символов)")
                : Component.literal("§8[Chat Remastered] §cТокен: §cне выдан"), false);
        return 1;
    }

    private AdminCommand() {
    }
}
