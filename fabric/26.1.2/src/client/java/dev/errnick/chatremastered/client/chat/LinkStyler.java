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
        return URL_PATTERN.matcher(text).find();
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
                return text.substring(start, end);
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
            if (start > index) {
                result.append(Component.literal(text.substring(index, start)).withStyle(baseStyle));
            }
            result.append(linkComponent(text.substring(start, end), baseStyle));
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
