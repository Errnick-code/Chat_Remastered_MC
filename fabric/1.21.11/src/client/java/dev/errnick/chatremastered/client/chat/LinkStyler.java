package dev.errnick.chatremastered.client.chat;

import dev.errnick.chatremastered.ChatRemasteredConfig;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinkStyler {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://|www\\.)[^\\s<>\"'`]+"
                    + "|(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}(?::\\d{1,5})?(?:/[^\\s<>\"'`]*)?"
                    + "|(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?");
    private static final Pattern EXPLICIT_URL_PATTERN =
            Pattern.compile("(?i)(?:https?://|www\\.)[^\\s<>\"'`]+");
    private static final String TRAILING_CHARS = ".,;:!?)]}>\"'";

    private LinkStyler() {
    }

    public static Component style(Component source) {
        if (source == null) {
            return null;
        }
        return restyle(source);
    }

    private static MutableComponent restyle(Component source) {
        MutableComponent out;
        ComponentContents contents = source.getContents();
        Style style = source.getStyle();
        ClickEvent click = style.getClickEvent();

        if (contents instanceof PlainTextContents plain) {
            if (click instanceof ClickEvent.OpenUrl openUrl) {
                out = Component.literal(plain.text()).withStyle(style.withColor(linkColor(openUrl.uri())));
            } else if (click == null) {
                out = hasUrl(plain.text())
                        ? splitText(plain.text(), style)
                        : Component.literal(plain.text()).withStyle(style);
            } else {
                out = Component.literal(plain.text()).withStyle(style);
            }
        } else if (contents instanceof TranslatableContents translatable) {
            out = restyleTranslatable(source, translatable, style);
        } else {
            out = source.plainCopy().setStyle(style);
            if (click instanceof ClickEvent.OpenUrl openUrl) {
                out.setStyle(style.withColor(linkColor(openUrl.uri())));
            }
        }

        for (Component sibling : source.getSiblings()) {
            out.append(restyle(sibling));
        }
        return out;
    }

    private static MutableComponent restyleTranslatable(Component source, TranslatableContents translatable, Style style) {
        Object[] args = translatable.getArgs();
        Object[] styledArgs = new Object[args.length];
        boolean changed = false;

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Component componentArg) {
                styledArgs[i] = restyle(componentArg);
                changed = true;
            } else if (arg instanceof String textArg && hasUrl(textArg)) {
                styledArgs[i] = splitText(textArg, Style.EMPTY);
                changed = true;
            } else {
                styledArgs[i] = arg;
            }
        }

        MutableComponent out;
        if (changed) {
            out = MutableComponent.create(new TranslatableContents(
                    translatable.getKey(), translatable.getFallback(), styledArgs));
        } else {
            out = source.plainCopy();
        }
        return out.setStyle(style);
    }

    private static boolean hasUrl(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return findUrl(text) != null;
    }

    public static String findUrl(String text) {
        return findMatch(URL_PATTERN, text);
    }

    public static String findExplicitUrl(String text) {
        return findMatch(EXPLICIT_URL_PATTERN, text);
    }

    private static String findMatch(Pattern pattern, String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = trimTrailing(text, start, matcher.end());
            if (end > start) {
                String candidate = text.substring(start, end);
                if (isPlausibleUrl(candidate)) {
                    return candidate;
                }
            }
            matcher.region(start + 1, text.length());
        }
        return null;
    }

    private static MutableComponent splitText(String text, Style baseStyle) {
        MutableComponent result = Component.empty();
        int index = 0;
        Matcher matcher = URL_PATTERN.matcher(text);

        while (index < text.length()) {
            matcher.region(index, text.length());
            if (!matcher.find()) {
                break;
            }
            int start = matcher.start();
            int end = trimTrailing(text, matcher.start(), matcher.end());
            if (end <= start) {
                index = start + 1;
                continue;
            }
            String candidate = text.substring(start, end);
            if (!isPlausibleUrl(candidate)) {
                index = start + 1;
                continue;
            }
            if (start > index) {
                result.append(Component.literal(text.substring(index, start)).withStyle(baseStyle));
            }
            result.append(linkComponent(candidate, baseStyle));
            index = end;
        }

        if (index < text.length()) {
            result.append(Component.literal(text.substring(index)).withStyle(baseStyle));
        }
        return result;
    }

    private static MutableComponent linkComponent(String link, Style baseStyle) {
        URI uri = toUri(link);
        Style style = baseStyle.withColor(linkColor(uri));
        if (uri != null && ChatRemasteredConfig.getLinkOpenEnabled()) {
            style = style.withClickEvent(new ClickEvent.OpenUrl(uri));
        }
        return Component.literal(link).withStyle(style);
    }

    public static boolean isPlausibleUrl(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        String lower = candidate.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
            return true;
        }
        int cut = candidate.length();
        int slash = candidate.indexOf('/');
        if (slash >= 0) {
            cut = slash;
        }
        String authority = candidate.substring(0, cut);
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) {
            String port = authority.substring(colon + 1);
            if (!isValidPort(port)) {
                return false;
            }
            authority = authority.substring(0, colon);
        }
        return isRealHost(authority, slash >= 0);
    }

    private static boolean isValidPort(String port) {
        if (port.isEmpty() || port.length() > 5) {
            return false;
        }
        int value = 0;
        for (int i = 0; i < port.length(); i++) {
            char ch = port.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
            value = value * 10 + (ch - '0');
        }
        return value >= 1 && value <= 65535;
    }

    private static final java.util.Set<String> FILE_LIKE_SUFFIXES = java.util.Set.of(
            "zip", "mov", "app", "dev", "sh", "md", "rs", "so", "py", "js", "ts", "run");

    private static boolean isRealHost(String host, boolean hasPath) {
        if (host == null || host.isEmpty() || host.indexOf('_') >= 0) {
            return false;
        }
        if (isIpv4(host)) {
            return true;
        }
        try {
            com.google.common.net.InternetDomainName name = com.google.common.net.InternetDomainName.from(host);
            if (!name.isUnderPublicSuffix() || name.isPublicSuffix()) {
                return false;
            }
            if (!hasPath) {
                String tld = name.publicSuffix().toString().toLowerCase(java.util.Locale.ROOT);
                if (FILE_LIKE_SUFFIXES.contains(tld)) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }
    private static int trimTrailing(String text, int start, int end) {
        int result = end;
        while (result > start && TRAILING_CHARS.indexOf(text.charAt(result - 1)) >= 0) {
            result--;
        }
        return result;
    }

    private static int linkColor(URI uri) {
        boolean visited = uri != null && LinkVisitedStore.isVisited(uri.toString());
        return visited
                ? ChatRemasteredConfig.getLinkColorPressed()
                : ChatRemasteredConfig.getLinkColorNormal();
    }

    private static URI toUri(String link) {
        if (link == null || link.isEmpty()) {
            return null;
        }
        String candidate = link;
        if (candidate.regionMatches(true, 0, "www.", 0, 4)) {
            candidate = "https://" + candidate;
        } else if (!candidate.regionMatches(true, 0, "http://", 0, 7)
                && !candidate.regionMatches(true, 0, "https://", 0, 8)) {
            candidate = "https://" + candidate;
        }
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                return null;
            }
            return uri;
        } catch (Exception e) {
            return null;
        }
    }
}
