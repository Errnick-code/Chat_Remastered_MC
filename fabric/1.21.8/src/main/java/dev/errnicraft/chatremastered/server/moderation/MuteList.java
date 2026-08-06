package dev.errnicraft.chatremastered.server.moderation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

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

    public void load(Path serverDir) {
        Path f = serverDir.resolve("config/chat-remastered-mutes.json");
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
            System.out.println("[Chat Remastered] Loaded mute list: " + mutedPlayers.size() + " players");
        } catch (Exception e) {
            System.out.println("[Chat Remastered] Error loading mute list: " + e.getMessage());
        }
    }

    public void save(MinecraftServer server) {
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
                    var player = server.getPlayerList().getPlayer(uuid);
                    String name = player != null ? player.getName().getString() : uuid.toString();
                    JsonObject obj = new JsonObject();
                    obj.addProperty("uuid", uuid.toString());
                    obj.addProperty("name", name);
                    arr.add(obj);
                }
            }
            Files.writeString(f, GSON.toJson(arr));
        } catch (IOException e) {
            System.out.println("[Chat Remastered] Error saving mute list: " + e.getMessage());
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
