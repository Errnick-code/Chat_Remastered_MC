package dev.errnick.chatremastered.server.tgbridge;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TgMuteCache {

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = TypeToken.getParameterized(java.util.List.class, String.class).getType();

    private static volatile Set<UUID> muted = new HashSet<>();
    private static volatile long lastRead = 0L;

    private TgMuteCache() {
    }

    public static boolean isMuted(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        refreshIfNeeded();
        return muted.contains(uuid);
    }

    private static synchronized void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastRead < 5_000L) {
            return;
        }
        lastRead = now;
        try {
            Path file = FabricLoader.getInstance().getConfigDir()
                    .resolve("tgbridge").resolve("muted_users.json");
            if (!Files.exists(file)) {
                muted = new HashSet<>();
                return;
            }
            java.util.List<String> raw = GSON.fromJson(Files.readString(file), LIST_TYPE);
            Set<UUID> parsed = new HashSet<>();
            if (raw != null) {
                for (String s : raw) {
                    try {
                        parsed.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            muted = parsed;
        } catch (Exception e) {

        }
    }
}
