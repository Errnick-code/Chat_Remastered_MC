package dev.errnicraft.chatremastered.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class LegacyNickParser {

    private static MutableComponent stripObjectContents(Component comp) {
        var contents = comp.getContents();
        MutableComponent copy;
        if (contents instanceof PlainTextContents plain) {
            copy = Component.literal(plain.text());
        } else if (contents instanceof TranslatableContents translatable) {
            copy = Component.translatable(translatable.getKey(), (Object[]) translatable.getArgs());
        } else {
            copy = Component.empty();
        }
        copy.setStyle(comp.getStyle());
        for (Component sibling : comp.getSiblings()) {
            copy.append(stripObjectContents(sibling));
        }
        return copy;
    }

    public static Component parseLegacyNick(Component comp, String plainName) {

        MutableComponent cleaned = stripObjectContents(comp);
        String raw = cleaned.getString();
        if (raw.isBlank()) {
            raw = plainName;
        }
        boolean hasSectionCodes = raw.indexOf('§') >= 0;
        boolean hasAmpCodes = hasAmpersandCodes(raw);
        if (!hasSectionCodes && !hasAmpCodes) {
            return comp;
        }
        String text = (hasAmpCodes && !hasSectionCodes) ? raw.replace('&', '§') : raw;
        return parseSectionCodes(text);
    }

    private static boolean hasAmpersandCodes(String raw) {
        if (!raw.contains("&") || raw.length() <= 1) {
            return false;
        }
        int i = raw.indexOf('&');
        return i + 1 < raw.length()
                && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(raw.charAt(i + 1))) >= 0;
    }

    private static Component parseSectionCodes(String text) {
        MutableComponent root = Component.empty();
        Style currentStyle = Style.EMPTY;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                if (sb.length() > 0) {
                    root.append(Component.literal(sb.toString()).withStyle(currentStyle));
                    sb.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(i + 1));
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    currentStyle = (fmt == ChatFormatting.RESET)
                            ? Style.EMPTY
                            : currentStyle.applyLegacyFormat(fmt);
                }
                i += 2;
            } else {
                sb.append(text.charAt(i));
                i++;
            }
        }
        if (sb.length() > 0) {
            root.append(Component.literal(sb.toString()).withStyle(currentStyle));
        }
        return root;
    }

    private LegacyNickParser() {
    }
}
