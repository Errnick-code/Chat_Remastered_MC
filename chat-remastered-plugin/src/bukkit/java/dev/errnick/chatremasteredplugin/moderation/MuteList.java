package dev.errnick.chatremasteredplugin.moderation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class MuteList {

    private static final Gson GSON = new Gson();

    private final Set<UUID> mutedPlayers = Collections.synchronizedSet(new java.util.HashSet<>());
    private volatile Path file;

    public void load(Path dataFolder) {
        Path f = dataFolder.resolve("chat-remastered-mutes.json");
        this.file = f;
        if (!Files.exists(f)) {
            return;
        }
        try {
            String content = Files.readString(f);
            JsonArray arr = GSON.fromJson(content, JsonArray.class);
            if (arr == null) {
                return;
            }
            mutedPlayers.clear();
            for (var el : arr) {
                JsonObject obj = el.getAsJsonObject();
                if (obj == null || !obj.has("uuid")) {
                    continue;
                }
                try {
                    mutedPlayers.add(UUID.fromString(obj.get("uuid").getAsString()));
                } catch (Exception ignored) {
                }
            }
            Bukkit.getLogger().info("[Chat Remastered] Loaded mute list: " + mutedPlayers.size() + " players");
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Chat Remastered] Error loading mute list: " + e.getMessage());
        }
    }

    public void save() {
        Path f = this.file;
        if (f == null) {
            return;
        }
        try {
            if (f.getParent() != null) {
                Files.createDirectories(f.getParent());
            }
            JsonArray arr = new JsonArray();
            synchronized (mutedPlayers) {
                for (UUID uuid : mutedPlayers) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    String name = player.getName() != null ? player.getName() : uuid.toString();
                    JsonObject obj = new JsonObject();
                    obj.addProperty("uuid", uuid.toString());
                    obj.addProperty("name", name);
                    arr.add(obj);
                }
            }
            Files.writeString(f, GSON.toJson(arr));
        } catch (IOException e) {
            Bukkit.getLogger().warning("[Chat Remastered] Error saving mute list: " + e.getMessage());
        }
    }

    public boolean isMuted(UUID uuid) {
        return mutedPlayers.contains(uuid);
    }

    public void add(UUID uuid) {
        mutedPlayers.add(uuid);
    }

    public boolean remove(UUID uuid) {
        return mutedPlayers.remove(uuid);
    }
}
