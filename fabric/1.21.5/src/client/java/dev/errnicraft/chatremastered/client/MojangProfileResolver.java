package dev.errnicraft.chatremastered.client;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class MojangProfileResolver {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final long CACHE_TTL_MILLIS = Duration.ofMinutes(15).toMillis();

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private record CacheEntry(CompletableFuture<GameProfile> future, long fetchedAtMillis) {
    }

    private MojangProfileResolver() {
    }

    public static CompletableFuture<GameProfile> resolve(String playerName) {
        String key = playerName.toLowerCase(java.util.Locale.ROOT);
        CacheEntry cached = CACHE.get(key);
        if (cached != null) {
            if (System.currentTimeMillis() - cached.fetchedAtMillis() < CACHE_TTL_MILLIS) {
                return cached.future();
            }
            CACHE.remove(key, cached);
        }
        CompletableFuture<GameProfile> future = fetchProfile(playerName);
        CacheEntry entry = new CacheEntry(future, System.currentTimeMillis());
        CACHE.put(key, entry);
        future.thenAccept(result -> {
            if (result == null) {
                CACHE.remove(key, entry);
            }
        });
        return future;
    }

    private static CompletableFuture<GameProfile> fetchProfile(String playerName) {
        chatDebug("requesting skin for '" + playerName + "'");
        HttpRequest uuidRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + playerName))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return CLIENT.sendAsync(uuidRequest, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200 || response.body() == null || response.body().isEmpty()) {
                        chatDebug("uuid lookup for '" + playerName + "' failed, status=" + response.statusCode() + ", trying PlayerDB");
                        return fetchViaPlayerDb(playerName);
                    }
                    JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                    String rawId = obj.get("id").getAsString();
                    UUID uuid = dashUuid(rawId);

                    HttpRequest texturesRequest = HttpRequest.newBuilder()
                            .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + rawId + "?unsigned=false"))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();

                    return CLIENT.sendAsync(texturesRequest, HttpResponse.BodyHandlers.ofString())
                            .thenCompose(texResp -> {
                                if (texResp.statusCode() != 200 || texResp.body() == null || texResp.body().isEmpty()) {
                                    chatDebug("textures lookup for '" + playerName + "' failed, status=" + texResp.statusCode() + ", trying PlayerDB");
                                    return fetchViaPlayerDb(playerName);
                                }
                                ImmutableMultimap.Builder<String, Property> propsBuilder = ImmutableMultimap.builder();
                                JsonObject texObj = JsonParser.parseString(texResp.body()).getAsJsonObject();
                                if (texObj.has("properties")) {
                                    for (var el : texObj.getAsJsonArray("properties")) {
                                        JsonObject prop = el.getAsJsonObject();
                                        String name = prop.get("name").getAsString();
                                        String value = prop.get("value").getAsString();
                                        String signature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                                        propsBuilder.put(name, new Property(name, value, signature));
                                    }
                                }
                                PropertyMap propertyMap = new PropertyMap();
                                propertyMap.putAll(propsBuilder.build());
                                chatDebug("skin resolved OK for '" + playerName + "', properties=" + propertyMap.size());
                                GameProfile profile = new GameProfile(uuid, playerName);
                                profile.getProperties().putAll(propertyMap);
                                return CompletableFuture.completedFuture(profile);
                            });
                })
                .exceptionally(ex -> {
                    chatDebug("exception resolving '" + playerName + "': " + ex);
                    return null;
                });
    }

    private static CompletableFuture<GameProfile> fetchViaPlayerDb(String playerName) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://playerdb.co/api/player/minecraft/" + playerName))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "chat-remastered-fabric-mod")
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200 || response.body() == null || response.body().isEmpty()) {
                        chatDebug("PlayerDB lookup for '" + playerName + "' failed, status=" + response.statusCode());
                        return null;
                    }
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (!root.has("data")) { chatDebug("PlayerDB: no 'data' field for '" + playerName + "'"); return null; }
                    JsonObject data = root.getAsJsonObject("data");
                    if (!data.has("player")) { chatDebug("PlayerDB: player '" + playerName + "' not found"); return null; }
                    JsonObject player = data.getAsJsonObject("player");

                    String rawId = player.get("id").getAsString().replace("-", "");
                    UUID uuid = dashUuid(rawId);

                    ImmutableMultimap.Builder<String, Property> propsBuilder = ImmutableMultimap.builder();
                    if (player.has("raw_id") && player.has("properties")) {
                        for (var el : player.getAsJsonArray("properties")) {
                            JsonObject prop = el.getAsJsonObject();
                            String name = prop.get("name").getAsString();
                            String value = prop.get("value").getAsString();
                            String signature = prop.has("signature") && !prop.get("signature").isJsonNull()
                                    ? prop.get("signature").getAsString() : null;
                            propsBuilder.put(name, new Property(name, value, signature));
                        }
                    }
                    PropertyMap propertyMap = new PropertyMap();
                    propertyMap.putAll(propsBuilder.build());
                    GameProfile profile = new GameProfile(uuid, playerName);
                    profile.getProperties().putAll(propertyMap);
                    return profile;
                })
                .exceptionally(ex -> {
                    chatDebug("PlayerDB exception for '" + playerName + "': " + ex);
                    return null;
                });
    }

    private static void chatDebug(String message) {
        String full = "[ChatRemastered] MojangProfileResolver: " + message;
        System.err.println(full);
    }

    private static UUID dashUuid(String raw) {
        StringBuilder sb = new StringBuilder(raw);
        sb.insert(20, '-');
        sb.insert(16, '-');
        sb.insert(12, '-');
        sb.insert(8, '-');
        return UUID.fromString(sb.toString());
    }
}
