package dev.errnick.chatremasteredplugin.text;

public final class LegacyNickParser {

    public static String parseLegacyNickToJson(String plainName) {
        String raw = plainName;
        boolean hasSectionCodes = raw.indexOf('\u00A7') >= 0;
        boolean hasAmpCodes = hasAmpersandCodes(raw);
        if (!hasSectionCodes && !hasAmpCodes) {
            return jsonText(plainName);
        }
        String text = (hasAmpCodes && !hasSectionCodes) ? raw.replace('&', '\u00A7') : raw;

        StringBuilder sb = new StringBuilder("[");
        String color = null;
        boolean bold = false, italic = false, underlined = false, strikethrough = false, obfuscated = false;
        StringBuilder run = new StringBuilder();

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                flush(sb, run, color, bold, italic, underlined, strikethrough, obfuscated);
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'r') {
                    color = null; bold = italic = underlined = strikethrough = obfuscated = false;
                } else if ("klmnor0123456789abcdef".indexOf(code) >= 0) {
                    switch (code) {
                        case 'k' -> obfuscated = true;
                        case 'l' -> bold = true;
                        case 'm' -> strikethrough = true;
                        case 'n' -> underlined = true;
                        case 'o' -> italic = true;
                        default -> { color = codeToHex(code); bold = italic = underlined = strikethrough = obfuscated = false; }
                    }
                }
                i += 2;
            } else {
                run.append(c);
                i++;
            }
        }
        flush(sb, run, color, bold, italic, underlined, strikethrough, obfuscated);
        return sb.append(']').toString();
    }

    private static void flush(StringBuilder sb, StringBuilder run, String color,
                              boolean bold, boolean italic, boolean underlined,
                              boolean strikethrough, boolean obfuscated) {
        if (run.length() == 0) {
            return;
        }
        sb.append('{').append(jsonText(run.toString()));
        if (color != null) sb.append(",\"color\":\"").append(color).append('"');
        if (bold) sb.append(",\"bold\":true");
        if (italic) sb.append(",\"italic\":true");
        if (underlined) sb.append(",\"underlined\":true");
        if (strikethrough) sb.append(",\"strikethrough\":true");
        if (obfuscated) sb.append(",\"obfuscated\":true");
        sb.append('}');
        run.setLength(0);
    }

    private static String jsonText(String s) {
        StringBuilder sb = new StringBuilder("\"text\":\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    private static String codeToHex(char code) {
        return switch (code) {
            case '0' -> "#000000"; case '1' -> "#0000AA"; case '2' -> "#00AA00"; case '3' -> "#00AAAA";
            case '4' -> "#AA0000"; case '5' -> "#AA00AA"; case '6' -> "#FFAA00"; case '7' -> "#AAAAAA";
            case '8' -> "#555555"; case '9' -> "#5555FF"; case 'a' -> "#55FF55"; case 'b' -> "#55FFFF";
            case 'c' -> "#FF5555"; case 'd' -> "#FF55FF"; case 'e' -> "#FFFF55"; case 'f' -> "#FFFFFF";
            default -> "#FFFFFF";
        };
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
