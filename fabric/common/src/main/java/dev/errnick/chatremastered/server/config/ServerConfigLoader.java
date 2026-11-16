package dev.errnick.chatremastered.server.config;

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
            HostConfig hostConfig = readHostConfig(json, port);
            int cooldown = json.has("photoCooldownSeconds") ? Math.max(0, json.get("photoCooldownSeconds").getAsInt()) : defaults.photoCooldownSeconds();
            boolean gifEnabled = !json.has("gifEnabled") || json.get("gifEnabled").getAsBoolean();
            int gifMaxDim = json.has("gifMaxDim") ? clamp(json.get("gifMaxDim").getAsInt(), 240, 1920) : defaults.gifMaxDim();
            String mutedMsg = readMutedMessage(json, defaults);
            int maxPhotosPerMessage = json.has("maxPhotosPerMessage")
                    ? clamp(json.get("maxPhotosPerMessage").getAsInt(), 1, 15)
                    : defaults.maxPhotosPerMessage();

            return new ServerConfig(res, port, hostConfig.host(), hostConfig.publicPort(), cooldown, gifEnabled, gifMaxDim, mutedMsg, maxPhotosPerMessage);
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
            HostConfig hostConfig = readHostConfig(json, port);
            int cooldown = json.has("photoCooldownSeconds") ? Math.max(0, json.get("photoCooldownSeconds").getAsInt()) : defaults.photoCooldownSeconds();
            boolean gifEnabled = !json.has("gifEnabled") || json.get("gifEnabled").getAsBoolean();
            int gifMaxDim = json.has("gifMaxDim") ? clamp(json.get("gifMaxDim").getAsInt(), 240, 1920) : defaults.gifMaxDim();
            String mutedMsg = readMutedMessage(json, defaults);
            return new ServerConfig(res, port, hostConfig.host(), hostConfig.publicPort(), cooldown, gifEnabled, gifMaxDim, mutedMsg, defaults.maxPhotosPerMessage());
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
            return "HD";
        }
        return switch (res) {
            case "360", "480", "720", "HD", "2K" -> res;
            default -> "HD";
        };
    }

    private static int readPort(JsonObject json) {
        if (!json.has("imagePort")) {
            return 5050;
        }
        int port = json.get("imagePort").getAsInt();
        return (port >= 1024 && port <= 65535) ? port : 5050;
    }

    private static HostConfig readHostConfig(JsonObject json, int localPort) {
        String rawHost = "";
        if (json.has("imageHost") && !json.get("imageHost").isJsonNull()) {
            rawHost = json.get("imageHost").getAsString().trim();
        }

        HostAndPort parsed = splitHostAndPort(rawHost);
        String host = parsed.host();
        int publicPort = -1;

        if (json.has("imagePublicPort") && !json.get("imagePublicPort").isJsonNull()) {
            try {
                int configured = json.get("imagePublicPort").getAsInt();
                if (configured >= 1024 && configured <= 65535) {
                    publicPort = configured;
                }
            } catch (RuntimeException ignored) {
            }
        }

        if (publicPort < 0 && parsed.port() != null) {
            publicPort = parsed.port();
        }
        if (publicPort < 0) {
            publicPort = localPort;
        }
        if (host.length() > 255) {
            host = host.substring(0, 255);
        }
        return new HostConfig(host, publicPort);
    }

    private static HostAndPort splitHostAndPort(String value) {
        if (value == null || value.isBlank()) {
            return new HostAndPort("", null);
        }
        String host = value.trim();

        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            if (close > 0) {
                String bare = host.substring(1, close);
                if (close + 1 < host.length() && host.charAt(close + 1) == ':') {
                    Integer port = parsePort(host.substring(close + 2));
                    if (port != null) {
                        return new HostAndPort(bare, port);
                    }
                }
                return new HostAndPort(bare, null);
            }
        }

        int firstColon = host.indexOf(':');
        int lastColon = host.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            Integer port = parsePort(host.substring(lastColon + 1));
            if (port != null) {
                return new HostAndPort(host.substring(0, lastColon), port);
            }
        }
        return new HostAndPort(host, null);
    }

    private static Integer parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1024 && port <= 65535 ? port : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
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
                      "resolution": "%s",

                      // Local TCP bind port for images. Must differ from the Minecraft server port.
                      "imagePort": %d,

                      // Public image host for FRP / NAT. Empty = use the Minecraft server address.
                      "imageHost": "%s",

                      // Public image port. Same as imagePort unless it is forwarded.
                      "imagePublicPort": %d,

                      "photoCooldownSeconds": %d,

                      "gifEnabled": %b,

                      "gifMaxDim": %d,

                      "maxPhotosPerMessage": %d,

                      "mutedMessage": "%s"
                    }
                    """.formatted(
                    values.resolution(),
                    values.imagePort(),
                    values.imageHost().replace("\\", "\\\\").replace("\"", "\\\""),
                    values.imagePublicPort(),
                    values.photoCooldownSeconds(),
                    values.gifEnabled(),
                    values.gifMaxDim(),
                    values.maxPhotosPerMessage(),
                    values.mutedMessage().replace("\\", "\\\\").replace("\"", "\\\"")
            );

            Files.writeString(configFile.toAbsolutePath(), content, StandardCharsets.UTF_8);
            System.out.println("[Chat Remastered] Config created: " + configFile.toAbsolutePath());
            System.out.println("[Chat Remastered] TCP bind port: " + values.imagePort() + ". If busy — change imagePort in config.");
            System.out.println("[Chat Remastered] Public image endpoint: "
                    + (values.imageHost().isBlank() ? "<auto-detected server host>" : values.imageHost())
                    + ":" + values.imagePublicPort());
        } catch (Exception e) {
            System.out.println("[Chat Remastered] Could not create config: " + e.getMessage());
        }
        return values;
    }

    private record HostAndPort(String host, Integer port) {
    }

    private record HostConfig(String host, int publicPort) {
    }

    private ServerConfigLoader() {
    }
}
