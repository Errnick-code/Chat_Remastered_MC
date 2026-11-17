package dev.errnick.chatremasteredplugin.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ServerMessages {

    private static final String RESOURCE = "/server-messages.json";
    private static final Map<String, String[]> MESSAGES = load();
    private static final String[] FALLBACK = {"Photos and replies right in chat.", "Download"};

    public static String text(String locale) {
        return entry(locale)[0];
    }

    public static String button(String locale) {
        return entry(locale)[1];
    }

    private static String[] entry(String locale) {
        String key = normalize(locale);
        String[] exact = MESSAGES.get(key);
        if (exact != null) {
            return exact;
        }
        int sep = key.indexOf('_');
        if (sep > 0) {
            String lang = key.substring(0, sep);
            for (Map.Entry<String, String[]> candidate : MESSAGES.entrySet()) {
                if (candidate.getKey().startsWith(lang + "_")) {
                    return candidate.getValue();
                }
            }
        }
        return MESSAGES.getOrDefault("en_us", FALLBACK);
    }

    private static String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en_us";
        }
        return locale.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static Map<String, String[]> load() {
        Map<String, String[]> messages = new HashMap<>();
        try (InputStream in = ServerMessages.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return messages;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject root = new Gson().fromJson(json, JsonObject.class);
            for (String key : root.keySet()) {
                JsonObject value = root.getAsJsonObject(key);
                String text = value.has("text") ? value.get("text").getAsString() : FALLBACK[0];
                String button = value.has("button") ? value.get("button").getAsString() : FALLBACK[1];
                messages.put(key.toLowerCase(Locale.ROOT), new String[]{text, button});
            }
        } catch (Exception ignored) {
        }
        return messages;
    }

    private ServerMessages() {
    }
}
