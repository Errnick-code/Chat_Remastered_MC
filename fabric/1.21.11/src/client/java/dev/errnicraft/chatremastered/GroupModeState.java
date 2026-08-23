package dev.errnicraft.chatremastered;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Files;

public final class GroupModeState {
    public static int mode = 0;
    public static boolean inGroup = false;
    public static String groupName = "";

    private static final File FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("chatremastered_groupmode.txt").toFile();

    private GroupModeState() {
    }

    public static void load() {
        try {
            if (FILE.exists()) {
                String v = Files.readString(FILE.toPath()).trim();
                mode = "1".equals(v) ? 1 : 0;
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.toPath().getParent());
            Files.writeString(FILE.toPath(), String.valueOf(mode));
        } catch (Exception ignored) {
        }
    }
}
