package dev.errnicraft.chatremastered.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ImageDiskCache;
import dev.errnicraft.chatremastered.TcpImageClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

import java.util.LinkedHashMap;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class DebugCommands {

    private record Ratio(int w, int h) {
    }

    private DebugCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {

            Map<String, Ratio> presets = new LinkedHashMap<>();
            presets.put("1_1", new Ratio(1, 1));
            presets.put("4_3", new Ratio(4, 3));
            presets.put("3_2", new Ratio(3, 2));
            presets.put("16_9", new Ratio(16, 9));
            presets.put("16_10", new Ratio(16, 10));
            presets.put("21_9", new Ratio(21, 9));
            presets.put("9_16", new Ratio(9, 16));
            presets.put("3_4", new Ratio(3, 4));
            presets.put("2_3", new Ratio(2, 3));

            Map<String, String> presetLabels = new LinkedHashMap<>();
            presetLabels.put("1_1", "1:1");
            presetLabels.put("4_3", "4:3");
            presetLabels.put("3_2", "3:2");
            presetLabels.put("16_9", "16:9");
            presetLabels.put("16_10", "16:10");
            presetLabels.put("21_9", "21:9");
            presetLabels.put("9_16", "9:16");
            presetLabels.put("3_4", "3:4");
            presetLabels.put("2_3", "2:3");

            var placeholderNode = literal("placeholder")
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> debugShowPlaceholder(mc, 16.0 / 9.0, "16:9"));
                        return 1;
                    });

            for (var entry : presets.entrySet()) {
                Ratio ratio = entry.getValue();
                String label = presetLabels.getOrDefault(entry.getKey(), entry.getKey());
                placeholderNode = placeholderNode.then(
                        literal(entry.getKey()).executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> debugShowPlaceholder(mc, (double) ratio.w() / ratio.h(), label));
                            return 1;
                        })
                );
            }

            placeholderNode = placeholderNode.then(
                    literal("custom").then(
                            argument("width", IntegerArgumentType.integer(1, 7680)).then(
                                    argument("height", IntegerArgumentType.integer(1, 4320))
                                            .executes(ctx -> {
                                                int w = IntegerArgumentType.getInteger(ctx, "width");
                                                int h = IntegerArgumentType.getInteger(ctx, "height");
                                                Minecraft mc = Minecraft.getInstance();
                                                mc.execute(() -> debugShowPlaceholder(mc, (double) w / h, w + "x" + h));
                                                return 1;
                                            })
                            )
                    )
            );

            var testNode = literal("test")
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> debugTestConnection(mc));
                        return 1;
                    });

            var placeholderDeletedNode = literal("placeholder_deleted")
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> debugShowPlaceholderState(mc, "deleted", 16.0 / 9.0, "16:9"));
                        return 1;
                    });
            for (var entry : presets.entrySet()) {
                Ratio ratio = entry.getValue();
                String label = presetLabels.getOrDefault(entry.getKey(), entry.getKey());
                placeholderDeletedNode = placeholderDeletedNode.then(
                        literal(entry.getKey()).executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> debugShowPlaceholderState(mc, "deleted", (double) ratio.w() / ratio.h(), label));
                            return 1;
                        })
                );
            }

            var placeholderErrorNode = literal("placeholder_error")
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> debugShowPlaceholderState(mc, "error", 16.0 / 9.0, "16:9"));
                        return 1;
                    });
            for (var entry : presets.entrySet()) {
                Ratio ratio = entry.getValue();
                String label = presetLabels.getOrDefault(entry.getKey(), entry.getKey());
                placeholderErrorNode = placeholderErrorNode.then(
                        literal(entry.getKey()).executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> debugShowPlaceholderState(mc, "error", (double) ratio.w() / ratio.h(), label));
                            return 1;
                        })
                );
            }

            var clearcacheNode = literal("clearcache")
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        Thread thread = new Thread(() -> {
                            int deleted = ImageDiskCache.clearDisk();
                            ImageDiskCache.CacheStats stats = ImageDiskCache.stats();
                            mc.execute(() -> mc.gui.getChat().addMessage(Component.literal(
                                    "§8[Chat Remastered] §7Кэш очищен: удалено §f" + deleted + "§7 файлов. "
                                            + "В ОЗУ: §f" + stats.getRamCount() + "§7 (" + stats.ramMb() + " МБ). "
                                            + "На диске: §f" + stats.getDiskCount() + "§7 (" + stats.diskMb() + " МБ).")));
                        });
                        thread.setDaemon(true);
                        thread.start();
                        return 1;
                    });

            var deleteNode = literal("delete")
                    .then(
                            argument("imageId", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String imageId = StringArgumentType.getString(ctx, "imageId");
                                        Minecraft mc = Minecraft.getInstance();
                                        var conn = mc.getConnection();
                                        if (conn == null) {
                                            return 0;
                                        }
                                        conn.send(new ServerboundChatCommandPacket("chat-remastered delete " + imageId));
                                        return 1;
                                    })
                    );

            dispatcher.register(
                    literal("chat-remastered")
                            .then(clearcacheNode)
                            .then(deleteNode)
            );

            dispatcher.register(
                    literal("chatremastereddebug")
                            .then(placeholderNode)
                            .then(placeholderDeletedNode)
                            .then(placeholderErrorNode)
                            .then(testNode)
            );
        });
    }

    public static void debugShowPlaceholder(Minecraft mc, double aspectRatio, String label) {
        String imageId = "debug_placeholder_" + System.currentTimeMillis();
        ChatMessageRenderer.addImageToChat(mc, imageId, "Debug", "placeholder " + label, (int) (aspectRatio * 100), 100);
    }

    public static void debugShowPlaceholderState(Minecraft mc, String state, double aspectRatio, String label) {
        String imageId = "debug_" + state + "_" + System.currentTimeMillis();
        ChatMessageRenderer.addImageToChat(mc, imageId, "Debug", state + " " + label, (int) (aspectRatio * 100), 100);
    }

    public static void debugTestConnection(Minecraft mc) {
        ChatComponent chat = mc.gui.getChat();
        String host = ChatRemasteredConfig.getServerHost();
        int port = ChatRemasteredConfig.getImagePort();
        String hasMod = ChatRemasteredConfig.getServerHasModVersion();
        String token = ChatRemasteredConfig.getUploadToken();

        chat.addMessage(Component.literal("§8[Chat Remastered] §7--- Connection test ---"));

        if (hasMod == null) {
            chat.addMessage(Component.literal("§8[Chat Remastered] §cServer mod: §cnot detected (no handshake)"));
        } else {
            chat.addMessage(Component.literal("§8[Chat Remastered] §aServer mod: §fv" + hasMod));
        }

        if (token.isEmpty()) {
            chat.addMessage(Component.literal("§8[Chat Remastered] §cUpload token: §cnot received"));
        } else {
            chat.addMessage(Component.literal("§8[Chat Remastered] §aUpload token: §freceived (" + token.length() + " chars)"));
        }

        chat.addMessage(Component.literal("§8[Chat Remastered] §7TCP " + host + ":" + port + " — pinging..."));
        Thread thread = new Thread(() -> {
            long start = System.currentTimeMillis();
            boolean ok = TcpImageClient.ping();
            long ms = System.currentTimeMillis() - start;
            ChatRemasteredConfig.setServerReachable(ok);
            mc.execute(() -> {
                if (ok) {
                    chat.addMessage(Component.literal("§8[Chat Remastered] §aTCP: §aOK §7(" + ms + "ms)"));
                    chat.addMessage(Component.literal("§8[Chat Remastered] §a✔ " + ChatRemasteredConfig.tr("chat-remastered.tcp_ok")));
                } else {
                    chat.addMessage(Component.literal("§8[Chat Remastered] §cTCP: §ccannot connect to " + host + ":" + port));
                    chat.addMessage(Component.literal("§8[Chat Remastered] §c✘ " + ChatRemasteredConfig.tr("chat-remastered.tcp_fail")));
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }
}
