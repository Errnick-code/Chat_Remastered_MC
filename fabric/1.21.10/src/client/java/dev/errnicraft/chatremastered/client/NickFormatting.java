package dev.errnicraft.chatremastered.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.regex.Pattern;

public final class NickFormatting {

    private static final Pattern HEAD_PLACEHOLDER = Pattern.compile("\\[[^\\]]*\\s*head\\]", Pattern.CASE_INSENSITIVE);
    private static final String LEGACY_CODES = "0123456789abcdefklmnor";

    private NickFormatting() {
    }

    public static String stripHeadPlaceholders(String text) {
        return HEAD_PLACEHOLDER.matcher(text).replaceAll("").trim();
    }

    private static MutableComponent stripObjectContents(Component comp) {
        var contents = comp.getContents();
        MutableComponent copy;
        if (contents instanceof PlainTextContents plain) {
            copy = Component.literal(plain.text());
        } else if (contents instanceof TranslatableContents translatable) {
            copy = Component.translatable(translatable.getKey(), translatable.getArgs());
        } else {

            copy = Component.empty();
        }
        copy.setStyle(comp.getStyle());
        for (Component sib : comp.getSiblings()) {
            copy.append(stripObjectContents(sib));
        }
        return copy;
    }

    public static Component parseLegacyNick(Component comp) {

        MutableComponent cleaned = stripObjectContents(comp);
        String raw = stripHeadPlaceholders(cleaned.getString());
        boolean hasSectionCodes = raw.indexOf('§') >= 0;
        boolean hasAmpCodes = false;
        int ampIdx = raw.indexOf('&');
        if (ampIdx >= 0 && raw.length() > 1 && ampIdx + 1 < raw.length()) {
            char next = Character.toLowerCase(raw.charAt(ampIdx + 1));
            hasAmpCodes = LEGACY_CODES.indexOf(next) >= 0;
        }
        if (!hasSectionCodes && !hasAmpCodes) {
            return cleaned;
        }
        String text = (hasAmpCodes && !hasSectionCodes) ? raw.replace('&', '§') : raw;

        MutableComponent root = Component.empty();
        Style currentStyle = Style.EMPTY;
        int i = 0;
        StringBuilder sb = new StringBuilder();
        while (i < text.length()) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                if (sb.length() > 0) {
                    root.append(Component.literal(sb.toString()).withStyle(currentStyle));
                    sb.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(i + 1));
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    currentStyle = (fmt == ChatFormatting.RESET) ? Style.EMPTY : currentStyle.applyLegacyFormat(fmt);
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

    public static int compareModVersions(String a, String b) {
        int[] pa = parseVersion(a);
        int[] pb = parseVersion(b);
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? pa[i] : 0;
            int vb = i < pb.length ? pb[i] : 0;
            int diff = va - vb;
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }
}
