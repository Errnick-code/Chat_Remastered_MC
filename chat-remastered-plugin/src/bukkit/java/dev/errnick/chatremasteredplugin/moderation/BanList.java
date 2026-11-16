package dev.errnick.chatremasteredplugin.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class BanList {

    private static final Gson GSON = new Gson();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private final Set<UUID> bannedPlayers = Collections.synchronizedSet(new java.util.HashSet<>());
    private volatile Path file;

    public void load(Path dataFolder) {
        Path f = dataFolder.resolve("chat-remastered-bans.json");
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
            bannedPlayers.clear();
            for (var el : arr) {
                JsonObject obj = el.getAsJsonObject();
                if (obj == null || !obj.has("uuid")) {
                    continue;
                }
                try {
                    bannedPlayers.add(UUID.fromString(obj.get("uuid").getAsString()));
                } catch (Exception ignored) {
                }
            }
            Bukkit.getLogger().info("[Chat Remastered] Loaded ban list: " + bannedPlayers.size() + " players");
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Chat Remastered] Error loading ban list: " + e.getMessage());
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
            synchronized (bannedPlayers) {
                for (UUID uuid : bannedPlayers) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    String name = player.getName() != null ? player.getName() : uuid.toString();
                    JsonObject obj = new JsonObject();
                    obj.addProperty("uuid", uuid.toString());
                    obj.addProperty("name", name);
                    arr.add(obj);
                }
            }
            Files.writeString(f, GSON_PRETTY.toJson(arr), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[Chat Remastered] Error saving ban list: " + e.getMessage());
        }
    }

    public boolean isBanned(UUID uuid) {
        return bannedPlayers.contains(uuid);
    }

    public void add(UUID uuid) {
        bannedPlayers.add(uuid);
    }

    public void remove(UUID uuid) {
        bannedPlayers.remove(uuid);
    }
}
