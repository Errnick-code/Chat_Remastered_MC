package dev.errnicraft.chatremastered.server.moderation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Consumer;

public final class BanHammerCompat {

    public static boolean isMuted(UUID uuid) {
        try {
            Class<?> clazz = Class.forName("eu.pb4.banhammer.api.BanHammer");
            Class<?> ptClazz = Class.forName("eu.pb4.banhammer.api.PunishmentType");
            Object muteConst = ptClazz.getField("MUTE").get(null);
            Method method = clazz.getMethod("isPunished", UUID.class, ptClazz);
            return (Boolean) method.invoke(null, uuid, muteConst);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean mute(UUID uuid, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        try {
            Class<?> bhClass = Class.forName("eu.pb4.banhammer.api.BanHammer");
            Class<?> ptClass = Class.forName("eu.pb4.banhammer.api.PunishmentType");
            Object muteType = ptClass.getField("MUTE").get(null);
            Class<?> pdClass = Class.forName("eu.pb4.banhammer.api.PunishmentData");

            String playerName = player != null ? player.getGameProfile().getName() : uuid.toString();
            String playerIp = player != null ? player.getIpAddress() : "";
            Component playerDisplay = player != null ? player.getDisplayName() : Component.literal(playerName);
            CommandSourceStack adminSource = server.createCommandSourceStack();

            Method createMethod = pdClass.getMethod(
                    "create",
                    UUID.class,
                    String.class,
                    Component.class,
                    String.class,
                    CommandSourceStack.class,
                    String.class,
                    long.class,
                    ptClass
            );
            Object punishData = createMethod.invoke(
                    null, uuid, playerIp, playerDisplay, playerName,
                    adminSource, "Muted via Chat Remastered", -1L, muteType
            );
            Method punishMethod = bhClass.getMethod("punish", pdClass, boolean.class);
            punishMethod.invoke(null, punishData, false);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            System.out.println("[Chat Remastered] BanHammer mute error: " + e.getMessage());
            return false;
        }
    }

    public static void unmute(UUID uuid) {
        try {
            Class<?> bhClass = Class.forName("eu.pb4.banhammer.api.BanHammer");
            Class<?> ptClass = Class.forName("eu.pb4.banhammer.api.PunishmentType");
            Object muteType = ptClass.getField("MUTE").get(null);
            Method removeMethod = bhClass.getMethod("removePunishment", UUID.class, ptClass);
            removeMethod.invoke(null, uuid, muteType);
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            System.out.println("[Chat Remastered] BanHammer unmute error: " + e.getMessage());
        }
    }

    public static boolean isPresent() {
        try {
            Class.forName("eu.pb4.banhammer.api.BanHammer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void registerPunishmentListener(Consumer<UUID> onMute) {
        try {
            Class<?> bhClass = Class.forName("eu.pb4.banhammer.api.BanHammer");
            Class<?> ptClass = Class.forName("eu.pb4.banhammer.api.PunishmentType");
            Object muteType = ptClass.getField("MUTE").get(null);
            Class<?> eventIface = Class.forName("eu.pb4.banhammer.api.BanHammer$PunishmentEvent");
            Method registerMethod = bhClass.getMethod("registerPunishmentEvent", eventIface);

            InvocationHandler handler = (proxy, method, args) -> {
                if ("onPunishment".equals(method.getName()) && args != null && args.length >= 1) {
                    Object data = args[0];
                    Object punishType = data.getClass().getField("type").get(data);
                    if (punishType == muteType) {
                        Object uuidValue = data.getClass().getField("playerUUID").get(data);
                        if (uuidValue instanceof UUID uuid) {
                            onMute.accept(uuid);
                        }
                    }
                }
                return null;
            };
            Object proxy = Proxy.newProxyInstance(eventIface.getClassLoader(), new Class<?>[]{eventIface}, handler);
            registerMethod.invoke(null, proxy);
            System.out.println("[Chat Remastered] BanHammer integration active");
        } catch (ClassNotFoundException e) {
            System.out.println("[Chat Remastered] BanHammer not found — integration disabled");
        } catch (Exception e) {
            System.out.println("[Chat Remastered] BanHammer integration error: " + e.getMessage());
        }
    }

    private BanHammerCompat() {
    }
}
