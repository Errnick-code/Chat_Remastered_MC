package dev.errnick.chatremasteredplugin.command;

import dev.errnick.chatremasteredplugin.PluginState;
import dev.errnick.chatremasteredplugin.moderation.ModerationActions;
import dev.errnick.chatremasteredplugin.net.ModProtocol;
import dev.errnick.chatremasteredplugin.net.packet.ServerHelloPacket;
import dev.errnick.chatremasteredplugin.net.MessengerService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class AdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("Usage: /chat-remastered-admin <block-photo|unblock-photo|mute|unmute|delete|test> <player>");
            return true;
        }

        String subCommand = args[0];

        switch (subCommand) {
            case "block-photo" -> handleBlockPhoto(sender, args);
            case "unblock-photo" -> handleUnblockPhoto(sender, args);
            case "mute" -> handleMute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "test" -> handleTest(sender, args);
            default -> sender.sendMessage("Unknown subcommand: " + subCommand
                    + ". Usage: /chat-remastered-admin <block-photo|unblock-photo|mute|unmute|delete|test> <player>");
        }
        return true;
    }

    private void handleBlockPhoto(CommandSender sender, String[] args) {
        UUID target = resolveTargetUuid(sender, args);
        if (target == null) {
            return;
        }
        ModerationActions.banPlayer(target);
        sender.sendMessage("Blocked photo uploads for " + args[1] + ".");
    }

    private void handleUnblockPhoto(CommandSender sender, String[] args) {
        UUID target = resolveTargetUuid(sender, args);
        if (target == null) {
            return;
        }
        ModerationActions.unbanPlayer(target);
        sender.sendMessage("Unblocked photo uploads for " + args[1] + ".");
    }

    private void handleMute(CommandSender sender, String[] args) {
        UUID target = resolveTargetUuid(sender, args);
        if (target == null) {
            return;
        }
        ModerationActions.mutePlayer(target);
        sender.sendMessage("Muted " + args[1] + ".");
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        UUID target = resolveTargetUuid(sender, args);
        if (target == null) {
            return;
        }
        ModerationActions.unmutePlayer(target);
        sender.sendMessage("Unmuted " + args[1] + ".");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /chat-remastered-admin delete <imageId>");
            return;
        }
        String imageId = args[1];
        PluginState.imageOwners.remove(imageId);
        dev.errnick.chatremasteredplugin.tcp.ImageTcpServer.deleteImage(imageId);

        byte[] data = new dev.errnick.chatremasteredplugin.net.packet.ImageDeletedPacket(imageId, true).write();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (PluginState.hasModInstalled(p.getUniqueId())) {
                MessengerService.send(p, ModProtocol.CH_IMAGE_DELETED, data);
            }
        }
        sender.sendMessage("Deleted image " + imageId + ".");
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /chat-remastered-admin test <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Player not found or not online: " + args[1]);
            return;
        }
        MessengerService.send(target, ModProtocol.CH_SERVER_HELLO,
                new ServerHelloPacket(ModProtocol.MOD_PROTOCOL_VERSION).write());
        sender.sendMessage("Sent test server hello packet to " + target.getName() + ".");
    }

    private UUID resolveTargetUuid(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /chat-remastered-admin " + args[0] + " <player>");
            return null;
        }
        Player online = Bukkit.getPlayer(args[1]);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
        return offline.getUniqueId();
    }
}
