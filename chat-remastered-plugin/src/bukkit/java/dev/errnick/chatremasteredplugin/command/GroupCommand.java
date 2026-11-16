package dev.errnick.chatremasteredplugin.command;

import dev.errnick.chatremasteredplugin.groups.Groups;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public final class GroupCommand implements CommandExecutor {

    private static final java.util.Map<String, ChatColor> COLORS = new java.util.HashMap<>();
    static {
        for (String k : Arrays.asList("yellow","gold","red","dark_red","green","dark_green",
                "aqua","dark_aqua","blue","dark_blue","light_purple","dark_purple")) {
            COLORS.put(k, ChatColor.valueOf(k.toUpperCase()));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only for players");
            return true;
        }
        if (args.length < 1) {
            p.sendMessage(ChatColor.GRAY + "Использование: /chatgroup create|join|list|leave|invite|kick");
            return true;
        }
        switch (args[0]) {
            case "create" -> create(p, args);
            case "join" -> join(p, args);
            case "leave" -> leave(p);
            case "list" -> list(p);
            case "invite" -> invite(p, args);
            default -> p.sendMessage(ChatColor.RED + "Неизвестная подкоманда");
        }
        return true;
    }

    private void create(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(ChatColor.RED + "/chatgroup create <название> <цвет>");
            return;
        }
        ChatColor color = COLORS.get(args[2].toLowerCase());
        if (color == null) {
            p.sendMessage(ChatColor.RED + "Неизвестный цвет. Доступные цвета: " + String.join(", ", COLORS.keySet()));
            return;
        }
        String err = Groups.create(args[1], p.getUniqueId(), color);
        if (err != null) {
            p.sendMessage(ChatColor.RED + errText(err));
            return;
        }
        p.sendMessage(color + "Группа \"" + args[1] + "\" создана");
        Groups.sendState(p);
    }

    private void join(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "/chatgroup join <название>");
            return;
        }
        Groups.ChatGroup g = Groups.byName(args[1]);
        if (g == null) { p.sendMessage(ChatColor.RED + "Группа не найдена"); return; }
        Groups.ChatGroup cur = Groups.groupOf(p.getUniqueId());
        if (cur == g) { p.sendMessage(ChatColor.RED + "Вы уже состоите в этой группе"); return; }
        if (!g.invited.contains(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Вас не приглашали в эту группу"); return; }
        Groups.join(p.getUniqueId(), g);
        Groups.broadcast(g, ChatColor.DARK_GRAY.toString() + p.getName() + " присоединился к группе");
        Groups.sendState(p);
    }

    private void leave(Player p) {
        Groups.ChatGroup g = Groups.groupOf(p.getUniqueId());
        if (g == null) { p.sendMessage(ChatColor.RED + "Вы не в группе"); return; }
        boolean owner = g.isOwner(p.getUniqueId());
        Groups.leave(p.getUniqueId(), g);
        p.sendMessage(ChatColor.DARK_GRAY + "Вы покинули группу \"" + g.name + "\"");
        Groups.broadcast(g, ChatColor.DARK_GRAY.toString() + p.getName() + " покинул группу");
        Groups.sendState(p);
    }

    private void list(Player p) {
        Groups.ChatGroup g = Groups.groupOf(p.getUniqueId());
        if (g == null) { p.sendMessage(ChatColor.RED + "Вы не в группе"); return; }
        StringBuilder sb = new StringBuilder("Участники группы " + g.name + ": ");
        for (java.util.UUID m : g.members) {
            Player mp = org.bukkit.Bukkit.getPlayer(m);
            sb.append(mp != null ? mp.getName() : m.toString()).append(", ");
        }
        p.sendMessage(sb.toString());
    }

    private void invite(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "/chatgroup invite <игрок>");
            return;
        }
        Groups.ChatGroup g = Groups.groupOf(p.getUniqueId());
        if (g == null || !g.canInvite(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Вы не можете приглашать в эту группу");
            return;
        }
        Player t = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (t == null) { p.sendMessage(ChatColor.RED + "Игрок не в сети"); return; }
        g.invited.add(t.getUniqueId());
        t.sendMessage(g.color + "[Приглашение] " + ChatColor.GRAY + p.getName()
                + " приглашает вас в группу \"" + g.name + "\". Вход: /chatgroup join " + g.name);
        p.sendMessage(ChatColor.GRAY + "Приглашение отправлено: " + t.getName());
    }

    private String errText(String key) {
        return switch (key) {
            case "too_long" -> "Название группы слишком длинное";
            case "duplicate" -> "Группа уже существует";
            case "already_in" -> "Вы уже состоите в группе";
            default -> key;
        };
    }
}
