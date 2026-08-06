package dev.errnicraft.chatremastered.server.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfigLoader {

    private static final Gson GSON = new Gson();

    public static ServerConfig loadOrCreate(Path serverDir) {
        Path configFile = serverDir.resolve("config/chat-remastered-server.json5");
        Path legacyConfigFile = serverDir.resolve("config/chat-remastered-server.json");
        ServerConfig defaults = ServerConfig.defaults();

        if (!Files.exists(configFile)) {
            ServerConfig migrated = Files.exists(legacyConfigFile) ? tryReadLegacy(legacyConfigFile, defaults) : defaults;
            return createDefault(configFile, migrated);
        }

        try {
            String raw = Files.readString(configFile, StandardCharsets.UTF_8);
            String stripped = stripComments(raw);
            JsonObject json = GSON.fromJson(stripped, JsonObject.class);
            if (json == null) {
                json = new JsonObject();
            }

            String res = readResolution(json);
            int port = readPort(json);
            int cooldown = json.has("photoCooldownSeconds") ? Math.max(0, json.get("photoCooldownSeconds").getAsInt()) : defaults.photoCooldownSeconds();
            boolean gifEnabled = !json.has("gifEnabled") || json.get("gifEnabled").getAsBoolean();
            int gifMaxDim = json.has("gifMaxDim") ? clamp(json.get("gifMaxDim").getAsInt(), 240, 1920) : defaults.gifMaxDim();
            String mutedMsg = readMutedMessage(json, defaults);
            int maxPhotosPerMessage = json.has("maxPhotosPerMessage")
                    ? clamp(json.get("maxPhotosPerMessage").getAsInt(), 1, 15)
                    : defaults.maxPhotosPerMessage();

            return new ServerConfig(res, port, cooldown, gifEnabled, gifMaxDim, mutedMsg, maxPhotosPerMessage);
        } catch (Exception e) {
            System.out.println("[Chat Remastered] Config read error: " + e.getMessage() + ". Using defaults.");
            return defaults;
        }
    }

    private static ServerConfig tryReadLegacy(Path legacyFile, ServerConfig defaults) {
        try {
            String content = Files.readString(legacyFile, StandardCharsets.UTF_8);
            JsonObject json = GSON.fromJson(content, JsonObject.class);
            if (json == null) {
                return defaults;
            }
            String res = readResolution(json);
            int port = readPort(json);
            int cooldown = json.has("photoCooldownSeconds") ? Math.max(0, json.get("photoCooldownSeconds").getAsInt()) : defaults.photoCooldownSeconds();
            boolean gifEnabled = !json.has("gifEnabled") || json.get("gifEnabled").getAsBoolean();
            int gifMaxDim = json.has("gifMaxDim") ? clamp(json.get("gifMaxDim").getAsInt(), 240, 1920) : defaults.gifMaxDim();
            String mutedMsg = readMutedMessage(json, defaults);
            return new ServerConfig(res, port, cooldown, gifEnabled, gifMaxDim, mutedMsg, defaults.maxPhotosPerMessage());
        } catch (Exception e) {
            return defaults;
        }
    }

    private static String stripComments(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inString = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char prev = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    out.append(c);
                }
                prev = c;
                continue;
            }
            if (inBlockComment) {
                if (prev == '*' && c == '/') {
                    inBlockComment = false;
                    prev = 0;
                } else {
                    prev = c;
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (c == '"' && prev != '\\') {
                    inString = false;
                } else if (c == '\\' && prev == '\\') {

                    prev = 0;
                    continue;
                }
                prev = c;
                continue;
            }

            if (c == '"') {
                inString = true;
                out.append(c);
                prev = c;
                continue;
            }
            if (c == '/' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
                inLineComment = true;
                i++;
                prev = 0;
                continue;
            }
            if (c == '/' && i + 1 < input.length() && input.charAt(i + 1) == '*') {
                inBlockComment = true;
                i++;
                prev = 0;
                continue;
            }
            out.append(c);
            prev = c;
        }
        return out.toString();
    }

    private static String readResolution(JsonObject json) {
        String res = json.has("resolution") ? json.get("resolution").getAsString().trim() : null;
        if (res == null) {
            return "720";
        }
        return switch (res) {
            case "360", "480", "720", "HD", "2K" -> res;
            default -> "720";
        };
    }

    private static int readPort(JsonObject json) {
        if (!json.has("imagePort")) {
            return 5050;
        }
        int port = json.get("imagePort").getAsInt();
        return (port >= 1024 && port <= 65535) ? port : 5050;
    }

    private static String readMutedMessage(JsonObject json, ServerConfig defaults) {
        if (json.has("mutedMessage")) {
            String msg = json.get("mutedMessage").getAsString();
            if (!msg.isBlank()) {
                return msg;
            }
        }
        return defaults.mutedMessage();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ServerConfig createDefault(Path configFile, ServerConfig values) {
        try {
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            String content = """
                    {
                      // Image resolution: 360 | 480 | 720 | HD | 2K
                      "resolution": "%s",

                      // TCP server port for images. Open it in firewall!
                      // If port is busy — change imagePort here and restart.
                      "imagePort": %d,

                      // Cooldown in seconds between photo sends per player. 0 = no cooldown. Default: 5.
                      "photoCooldownSeconds": %d,

                      // If true — players can send animated GIFs. Default: true.
                      "gifEnabled": %b,

                      // Maximum GIF resolution (width/height) players can upload. Range: 240..1920. Default: 480.
                      "gifMaxDim": %d,

                      // How many photos a player can send at once in a single message. Range: 1..10. Default: 1.
                      "maxPhotosPerMessage": %d,

                      // Message sent to a muted player when they try to send a chat message, photo or reply. Supports UTF-8.
                      "mutedMessage": "%s"
                    }
                    """.formatted(
                    values.resolution(),
                    values.imagePort(),
                    values.photoCooldownSeconds(),
                    values.gifEnabled(),
                    values.gifMaxDim(),
                    values.maxPhotosPerMessage(),
                    values.mutedMessage().replace("\\", "\\\\").replace("\"", "\\\"")
            );

            Files.writeString(configFile.toAbsolutePath(), content, StandardCharsets.UTF_8);
            System.out.println("[Chat Remastered] Config created: " + configFile.toAbsolutePath());
            System.out.println("[Chat Remastered] TCP port: " + values.imagePort() + ". If busy — change imagePort in config.");
        } catch (Exception e) {
            System.out.println("[Chat Remastered] Could not create config: " + e.getMessage());
        }
        return values;
    }

    private ServerConfigLoader() {
    }
}
