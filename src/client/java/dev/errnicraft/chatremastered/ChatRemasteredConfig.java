package dev.errnicraft.chatremastered;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ChatRemasteredConfig {

    private static final int CURRENT_CONFIG_VERSION = 1;

    private static volatile String resolution = "720";
    private static volatile String serverHost = "";
    private static volatile int imagePort = 5050;
    private static volatile String uploadToken = "";
    private static volatile String serverHasModVersion = null;
    private static volatile boolean handshakeComplete = false;
    private static volatile boolean handshakeIncompatible = false;
    private static volatile boolean serverReachable = false;

    private static volatile boolean banned = false;

    private static volatile boolean muted = false;

    private static volatile int cooldownSeconds = 5;

    private static volatile long cooldownUntilMs = 0L;

    private static volatile float previewScale = 1.0f;

    private static volatile float inputPreviewScale = 1.0f;

    private static volatile boolean fullscreenChat = false;

    private static volatile int closedChatLines = 10;

    private static volatile int gifMaxDimServer = 480;

    private static volatile boolean gifEnabled = true;

    private static volatile int maxPhotosPerMessage = 1;

    private static volatile boolean groupPhotosRowMode = false;

    private static volatile boolean screenshotsPanelOnLeft = false;

    private static volatile int removeAnimMode = 0;

    private static volatile java.util.List<String> screenshotFolders = new java.util.ArrayList<>();

    private static volatile int lastScreenshotFolderIndex = 0;

    private ChatRemasteredConfig() {
    }

    public static String getResolution() {
        return resolution;
    }

    public static void setResolution(String value) {
        resolution = value;
    }

    public static String getServerHost() {
        return serverHost;
    }

    public static void setServerHost(String value) {
        serverHost = value;
    }

    public static int getImagePort() {
        return imagePort;
    }

    public static void setImagePort(int value) {
        imagePort = value;
    }

    public static String getUploadToken() {
        return uploadToken;
    }

    public static void setUploadToken(String value) {
        uploadToken = value;
    }

    public static String getServerHasModVersion() {
        return serverHasModVersion;
    }

    public static void setServerHasModVersion(String value) {
        serverHasModVersion = value;
    }

    public static boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    public static void setHandshakeComplete(boolean value) {
        handshakeComplete = value;
    }

    public static boolean isHandshakeIncompatible() {
        return handshakeIncompatible;
    }

    public static void setHandshakeIncompatible(boolean value) {
        handshakeIncompatible = value;
    }

    public static boolean getServerReachable() {
        return serverReachable;
    }

    public static void setServerReachable(boolean value) {
        serverReachable = value;
    }

    public static boolean getBanned() {
        return banned;
    }

    public static void setBanned(boolean value) {
        banned = value;
    }

    public static boolean getMuted() {
        return muted;
    }

    public static void setMuted(boolean value) {
        muted = value;
    }

    public static int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public static void setCooldownSeconds(int value) {
        cooldownSeconds = value;
    }

    public static long getCooldownUntilMs() {
        return cooldownUntilMs;
    }

    public static void setCooldownUntilMs(long value) {
        cooldownUntilMs = value;
    }

    public static long cooldownRemainingMs() {
        return Math.max(cooldownUntilMs - System.currentTimeMillis(), 0L);
    }

    public static void startCooldown() {
        cooldownUntilMs = System.currentTimeMillis() + cooldownSeconds * 1000L;
    }

    public static float getPreviewScale() {
        return previewScale;
    }

    public static void setPreviewScale(float value) {
        previewScale = clamp(value, 0.5f, 2.0f);
    }

    public static float getInputPreviewScale() {
        return inputPreviewScale;
    }

    public static void setInputPreviewScale(float value) {
        inputPreviewScale = clamp(value, 0.5f, 2.0f);
    }

    public static boolean getFullscreenChat() {
        return fullscreenChat;
    }

    public static void setFullscreenChat(boolean value) {
        fullscreenChat = value;
    }

    public static int getClosedChatLines() {
        return closedChatLines;
    }

    public static void setClosedChatLines(int value) {
        closedChatLines = value;
    }

    public static int getGifMaxDimServer() {
        return gifMaxDimServer;
    }

    public static void setGifMaxDimServer(int value) {
        gifMaxDimServer = clamp(value, 240, 1920);
    }

    public static boolean getGifEnabled() {
        return gifEnabled;
    }

    public static void setGifEnabled(boolean value) {
        gifEnabled = value;
    }

    public static int getMaxPhotosPerMessage() {
        return maxPhotosPerMessage;
    }

    public static void setMaxPhotosPerMessage(int value) {
        maxPhotosPerMessage = clamp(value, 1, 15);
    }

    public static boolean getGroupPhotosRowMode() {
        return groupPhotosRowMode;
    }

    public static void setGroupPhotosRowMode(boolean value) {
        groupPhotosRowMode = value;
        saveConfig();
    }

    public static boolean getScreenshotsPanelOnLeft() {
        return screenshotsPanelOnLeft;
    }

    public static void setScreenshotsPanelOnLeft(boolean value) {
        screenshotsPanelOnLeft = value;
        saveConfig();
    }

    public static int getRemoveAnimMode() {
        return removeAnimMode;
    }

    public static void setRemoveAnimMode(int value) {
        removeAnimMode = value;
        saveConfig();
    }

    public static java.util.List<String> getScreenshotFolders() {
        return new java.util.ArrayList<>(screenshotFolders);
    }

    public static boolean addScreenshotFolder(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (screenshotFolders.contains(path)) {
            return false;
        }
        java.util.List<String> updated = new java.util.ArrayList<>(screenshotFolders);
        updated.add(path);
        screenshotFolders = updated;
        saveConfig();
        return true;
    }

    public static void removeScreenshotFolder(String path) {
        java.util.List<String> updated = new java.util.ArrayList<>(screenshotFolders);
        if (updated.remove(path)) {
            screenshotFolders = updated;
            if (lastScreenshotFolderIndex > updated.size()) {
                lastScreenshotFolderIndex = 0;
            }
            saveConfig();
        }
    }

    public static int getLastScreenshotFolderIndex() {
        return lastScreenshotFolderIndex;
    }

    public static void setLastScreenshotFolderIndex(int index) {
        lastScreenshotFolderIndex = Math.max(0, index);
        saveConfig();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static File configFile() {
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Exception e) {
            mc = null;
        }
        File gameDir = mc != null ? mc.gameDirectory : new File(".");
        return new File(gameDir, "config/chat-remastered.json5");
    }

    private static File legacyConfigFile() {
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Exception e) {
            mc = null;
        }
        File gameDir = mc != null ? mc.gameDirectory : new File(".");
        return new File(gameDir, "config/chat-remastered.json");
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

    public static void saveConfig() {
        try {
            File f = configFile();
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            float scale = clamp(previewScale, 0.5f, 2.0f);
            float inputScale = clamp(inputPreviewScale, 0.5f, 2.0f);
            com.google.gson.JsonArray foldersArr = new com.google.gson.JsonArray();
            for (String p : screenshotFolders) {
                foldersArr.add(p);
            }
            Gson foldersGson = new GsonBuilder().setPrettyPrinting().create();
            String foldersJson = foldersGson.toJson(foldersArr);

            String content = """
                    {
                      "configVersion": %d,

                      // Масштаб превью фото в чате: 0.5..2.0. Default: 1.0.
                      "previewScale": %s,

                      // Масштаб карточки над полем ввода (pending image): 0.5..2.0. Default: 1.0.
                      "inputPreviewScale": %s,

                      // Высота открытого чата: false = ванильная (примерно половина экрана), true = на весь экран.
                      "fullscreenChat": %b,

                      // Кол-во строк закрытого (затухающего) чата. Range: 8..20. Default: 10.
                      "closedChatLines": %d,

                      // Режим отображения группы фото: false = одна карточка + скролл колёсиком, true = все фото группы в ряд.
                      "groupPhotosRowMode": %b,

                      // Положение выезжающей панели скриншотов: false = справа, true = слева.
                      "screenshotsPanelOnLeft": %b,

                      // Анимация удаления карточки над полем ввода: 0 = полёт вверх и падение, 1 = мелкие осколки, 2 = крупные осколки.
                      "removeAnimMode": %d,

                      // Дополнительные папки со скриншотами, добавленные пользователем.
                      "screenshotFolders": %s,

                      // Индекс последней выбранной папки в панели скриншотов (0 = дефолтная).
                      "lastScreenshotFolderIndex": %d
                    }
                    """.formatted(
                    CURRENT_CONFIG_VERSION,
                    scale,
                    inputScale,
                    fullscreenChat,
                    clamp(closedChatLines, 8, 20),
                    groupPhotosRowMode,
                    screenshotsPanelOnLeft,
                    removeAnimMode,
                    foldersJson,
                    lastScreenshotFolderIndex
            );
            Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[Chat Remastered] Config save error: " + e.getMessage());
        }
    }

    public static void loadConfig() {
        try {
            File f = configFile();
            if (!f.exists()) {
                File legacy = legacyConfigFile();
                if (legacy.exists()) {
                    loadFrom(legacy, false);
                    saveConfig();
                }
                return;
            }
            loadFrom(f, true);
        } catch (IOException e) {
            System.out.println("[Chat Remastered] Config load error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[Chat Remastered] Config load error: " + e.getMessage());
        }
    }

    private static void loadFrom(File f, boolean isJson5) throws IOException {
        String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        String parseable = isJson5 ? stripComments(text) : text;
        JsonObject json;
        try {
            json = new Gson().fromJson(parseable, JsonObject.class);
        } catch (Exception e) {
            json = null;
        }
        if (json == null) {
            return;
        }

        previewScale = json.has("previewScale") ? clamp(json.get("previewScale").getAsFloat(), 0.5f, 2.0f) : 1.0f;
        inputPreviewScale = json.has("inputPreviewScale") ? clamp(json.get("inputPreviewScale").getAsFloat(), 0.5f, 2.0f) : 1.0f;
        fullscreenChat = json.has("fullscreenChat") && json.get("fullscreenChat").getAsBoolean();
        closedChatLines = json.has("closedChatLines") ? clamp(json.get("closedChatLines").getAsInt(), 8, 20) : 10;
        groupPhotosRowMode = json.has("groupPhotosRowMode") && json.get("groupPhotosRowMode").getAsBoolean();
        screenshotsPanelOnLeft = json.has("screenshotsPanelOnLeft") && json.get("screenshotsPanelOnLeft").getAsBoolean();
        if (json.has("removeAnimMode")) {
            removeAnimMode = clamp(json.get("removeAnimMode").getAsInt(), 0, 2);
        } else if (json.has("removeAnimShatter") && json.get("removeAnimShatter").getAsBoolean()) {
            removeAnimMode = 1;
        } else {
            removeAnimMode = 0;
        }
        java.util.List<String> loadedFolders = new java.util.ArrayList<>();
        if (json.has("screenshotFolders") && json.get("screenshotFolders").isJsonArray()) {
            for (var el : json.get("screenshotFolders").getAsJsonArray()) {
                String p = el.getAsString();
                if (p != null && !p.isBlank()) {
                    loadedFolders.add(p);
                }
            }
        }
        screenshotFolders = loadedFolders;
        lastScreenshotFolderIndex = json.has("lastScreenshotFolderIndex")
                ? Math.max(0, json.get("lastScreenshotFolderIndex").getAsInt()) : 0;
    }

    public static int getPreviewMaxW() {
        int base = switch (resolution) {
            case "360" -> 84;
            case "480" -> 112;
            case "HD" -> 187;
            default -> 140;
        };
        return Math.max(Math.round(base * previewScale), 16);
    }

    public static int getPreviewMaxH() {
        int base = switch (resolution) {
            case "360" -> 32;
            case "480" -> 42;
            case "HD" -> 70;
            default -> 52;
        };
        return Math.max(Math.round(base * previewScale), 8);
    }

    public static int getInputPreviewMaxW() {
        int base = switch (resolution) {
            case "360" -> 84;
            case "480" -> 112;
            case "HD" -> 187;
            default -> 140;
        };
        return Math.max(Math.round(base * inputPreviewScale), 16);
    }

    public static int getInputPreviewMaxH() {
        int base = switch (resolution) {
            case "360" -> 32;
            case "480" -> 42;
            case "HD" -> 70;
            default -> 52;
        };
        return Math.max(Math.round(base * inputPreviewScale), 8);
    }

    public static int getMaxDim() {
        return switch (resolution) {
            case "360" -> 480;
            case "480" -> 640;
            case "HD" -> 1920;
            case "2K" -> 2560;
            default -> 1280;
        };
    }

    public static void reset() {
        resolution = "720";
        serverHost = "";
        imagePort = 5050;
        uploadToken = "";
        serverHasModVersion = null;
        handshakeComplete = false;
        handshakeIncompatible = false;
        serverReachable = false;
        banned = false;
        muted = false;
        gifEnabled = true;
        gifMaxDimServer = 480;
        maxPhotosPerMessage = 1;
        cooldownSeconds = 5;
        cooldownUntilMs = 0L;

    }

    public static String tr(String key, Object... args) {
        try {
            return Component.translatable(key, args).getString();
        } catch (Exception e) {
            return key;
        }
    }

    public static String tr(String ru, String en) {
        try {
            String locale = Minecraft.getInstance().options.languageCode;
            return (locale != null && locale.startsWith("ru")) ? ru : en;
        } catch (Exception e) {
            return en;
        }
    }
}
