package dev.errnicraft.chatremastered;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EntityChatParser {

    private static final Pattern PLAYER_PATTERN = Pattern.compile(
            "^<chat_remastered:player:([A-Za-z0-9_]{1,16}):(tocursor|rotate)>\\s?(.*)$",
            Pattern.DOTALL
    );

    private static final Pattern SHORT_PLAYER_PATTERN = Pattern.compile(
            "^<player:([A-Za-z0-9_]{1,16})>\\s?(.*)$",
            Pattern.DOTALL
    );

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^<([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})>\\s?(.*)$",
            Pattern.DOTALL
    );

    private static final Pattern ENTITY_PATTERN = Pattern.compile(
            "^<chat_remastered:entity:([a-z0-9_.-]+):([a-z0-9_./-]+)(\\{[^>]*})?:(tocursor|rotate)"
                    + "(?::(-?[0-9]{1,4}))?(?::(-?[0-9]{1,4}))?(?::(-?[0-9]+(?:\\.[0-9]+)?))?>\\s?(.*)$",
            Pattern.DOTALL
    );

    private static final Pattern ITEM_PATTERN = Pattern.compile(
            "^<chat_remastered:item:([a-z0-9_.-]+):([a-z0-9_./-]+)(\\{[^>]*})?>\\s?(.*)$",
            Pattern.DOTALL
    );

    private static final Pattern HAND_ITEM_PATTERN = Pattern.compile(
            "^<item>\\s?(.*)$",
            Pattern.DOTALL
    );

    private static final Pattern LOOK_ENTITY_PATTERN = Pattern.compile(
            "^<entity>\\s?(.*)$",
            Pattern.DOTALL
    );

    private EntityChatParser() {
    }

    public record ParsedCommand(String targetPlayerName, String behavior, String caption) {
    }

    public record ParsedEntityCommand(String entityNamespace, String entityPath, String entityNbt,
                                       String behavior, Integer offsetX, Integer offsetY, Float size,
                                       String caption) {
    }

    public record ParsedItemCommand(String itemNamespace, String itemPath, String itemNbt, String caption) {
    }

    public record ParsedHandItemCommand(String caption) {
    }

    public record ParsedLookEntityCommand(String caption) {
    }

    public record ParsedShortPlayerCommand(String targetPlayerName, String caption) {
    }

    public record ParsedUuidCommand(String uuid, String caption) {
    }

    public static ParsedCommand parse(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        Matcher m = PLAYER_PATTERN.matcher(rawInput);
        if (!m.matches()) {
            return null;
        }
        String targetPlayerName = m.group(1);
        String behavior = m.group(2);
        String caption = m.group(3) != null ? m.group(3).trim() : "";
        return new ParsedCommand(targetPlayerName, behavior, caption);
    }

    public static ParsedEntityCommand parseEntity(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        Matcher m = ENTITY_PATTERN.matcher(rawInput);
        if (!m.matches()) {
            return null;
        }
        String entityNamespace = m.group(1);
        String entityPath = m.group(2);
        String entityNbt = m.group(3) != null ? m.group(3) : "";
        String behavior = m.group(4);
        Integer offsetX = m.group(5) != null ? Integer.parseInt(m.group(5)) : null;
        Integer offsetY = m.group(6) != null ? Integer.parseInt(m.group(6)) : null;
        Float size = m.group(7) != null ? Float.parseFloat(m.group(7)) : null;
        String caption = m.group(8) != null ? m.group(8).trim() : "";
        return new ParsedEntityCommand(entityNamespace, entityPath, entityNbt, behavior,
                offsetX, offsetY, size, caption);
    }

    public static ParsedItemCommand parseItem(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        Matcher m = ITEM_PATTERN.matcher(rawInput);
        if (!m.matches()) {
            return null;
        }
        String itemNamespace = m.group(1);
        String itemPath = m.group(2);
        String itemNbt = m.group(3) != null ? m.group(3) : "";
        String caption = m.group(4) != null ? m.group(4).trim() : "";
        return new ParsedItemCommand(itemNamespace, itemPath, itemNbt, caption);
    }

    public static ParsedHandItemCommand parseHandItem(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        Matcher m = HAND_ITEM_PATTERN.matcher(rawInput);
        if (!m.matches()) {
            return null;
        }
        String caption = m.group(1) != null ? m.group(1).trim() : "";
        return new ParsedHandItemCommand(caption);
    }

    public static ParsedLookEntityCommand parseLookEntity(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        Matcher m = LOOK_ENTITY_PATTERN.matcher(rawInput);
        if (!m.matches()) {
            return null;
        }
        String caption = m.group(1) != null ? m.group(1).trim() : "";
        return new ParsedLookEntityCommand(caption);
    }

    public static ParsedShortPlayerCommand parseShortPlayer(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        Matcher m = SHORT_PLAYER_PATTERN.matcher(rawInput);
        if (!m.matches()) {
            return null;
        }
        String targetPlayerName = m.group(1);
        String caption = m.group(2) != null ? m.group(2).trim() : "";
        return new ParsedShortPlayerCommand(targetPlayerName, caption);
    }

    public static ParsedUuidCommand parseUuid(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        Matcher m = UUID_PATTERN.matcher(rawInput);
        if (!m.matches()) {
            return null;
        }
        String uuid = m.group(1);
        String caption = m.group(2) != null ? m.group(2).trim() : "";
        return new ParsedUuidCommand(uuid, caption);
    }
}
