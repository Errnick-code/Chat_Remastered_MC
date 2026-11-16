package dev.errnick.chatremasteredplugin.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class LegacyNickParser {

    public static String parseLegacyNickToJson(String plainName) {
        String raw = plainName;
        boolean hasSectionCodes = raw.indexOf('\u00A7') >= 0;
        boolean hasAmpCodes = hasAmpersandCodes(raw);
        if (!hasSectionCodes && !hasAmpCodes) {
            return GsonComponentSerializer.gson().serialize(Component.text(plainName));
        }
        String text = (hasAmpCodes && !hasSectionCodes) ? raw.replace('&', '\u00A7') : raw;
        Component parsed = LegacyComponentSerializer.legacySection().deserialize(text);
        return GsonComponentSerializer.gson().serialize(parsed);
    }

    private static boolean hasAmpersandCodes(String raw) {
        if (!raw.contains("&") || raw.length() <= 1) {
            return false;
        }
        int i = raw.indexOf('&');
        return i + 1 < raw.length()
                && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(raw.charAt(i + 1))) >= 0;
    }

    private LegacyNickParser() {
    }
}
