package dev.errnicraft.chatremastered.client;

import java.util.ArrayList;
import java.util.List;

public final class EntityTagSuggestions {

    private static final String PREFIX_ENTITY = "<chat_remastered:entity:";
    private static final String PREFIX_PLAYER = "<chat_remastered:player:";
    private static final String PREFIX_ITEM = "<chat_remastered:item:";
    private static final String PREFIX_ROOT = "<chat_remastered:";

    private static final String[] BEHAVIORS = new String[] { "tocursor", "rotate" };

    private EntityTagSuggestions() {
    }

    private static String[] mobIds() {
        java.util.List<String> ids = new ArrayList<>();
        for (net.minecraft.resources.ResourceLocation id
                : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet()) {
            ids.add(id.getPath());
        }
        ids.add("player");
        ids.sort(String::compareTo);
        return ids.toArray(new String[0]);
    }

    private static String[] itemIds() {
        java.util.List<String> ids = new ArrayList<>();
        for (net.minecraft.resources.ResourceLocation id
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
            ids.add(id.getPath());
        }
        ids.sort(String::compareTo);
        return ids.toArray(new String[0]);
    }

    public record Suggestion(String display, String insertText) {
    }

    public static List<Suggestion> compute(String text, int cursor) {
        List<Suggestion> result = new ArrayList<>();
        if (text == null || cursor < 0 || cursor > text.length()) {
            return result;
        }
        String head = text.substring(0, cursor);

        if (!head.startsWith(PREFIX_ROOT)) {
            if (PREFIX_ROOT.startsWith(head) && !head.isEmpty()) {
                addPrefixed(result, new String[] { PREFIX_ENTITY, PREFIX_PLAYER, PREFIX_ITEM }, head, "");
            }
            return result;
        }

        if (head.equals(PREFIX_ROOT)) {
            result.add(new Suggestion("entity:", "entity:"));
            result.add(new Suggestion("player:", "player:"));
            result.add(new Suggestion("item:", "item:"));
            return result;
        }

        if (head.startsWith(PREFIX_PLAYER)) {
            String tail = head.substring(PREFIX_PLAYER.length());

            int firstColon = tail.indexOf(':');
            if (firstColon < 0) {
                return result;
            }
            String afterName = tail.substring(firstColon + 1);
            if (!afterName.contains(">")) {
                addPrefixed(result, BEHAVIORS, afterName, "");
            }
            return result;
        }

        if (head.startsWith(PREFIX_ENTITY)) {
            String tail = head.substring(PREFIX_ENTITY.length());

            int firstColon = tail.indexOf(':');
            if (firstColon < 0) {
                if (tail.equals("minecraft")) {
                    result.add(new Suggestion(":", ":"));
                } else {
                    addPrefixed(result, new String[] { "minecraft" }, tail, ":");
                }
                return result;
            }
            String namespace = tail.substring(0, firstColon);
            if (!namespace.equals("minecraft")) {
                return result;
            }
            String afterNamespace = tail.substring(firstColon + 1);

            String[] mobIds = mobIds();
            int braceIdx = afterNamespace.indexOf('{');
            int colonAfterPath = afterNamespace.indexOf(':');
            boolean pathClosed = braceIdx >= 0 || colonAfterPath >= 0;

            if (!pathClosed) {
                boolean exactMatch = false;
                for (String id : mobIds) {
                    if (id.equals(afterNamespace)) { exactMatch = true; break; }
                }
                if (!exactMatch) {
                    addPrefixed(result, mobIds, afterNamespace, "");
                    return result;
                }

                result.add(new Suggestion(":", ":"));
                return result;
            }

            int pathEnd = braceIdx >= 0 ? braceIdx : colonAfterPath;
            String path = afterNamespace.substring(0, pathEnd);
            boolean validId = false;
            for (String id : mobIds) {
                if (id.equals(path)) { validId = true; break; }
            }
            if (!validId) {
                return result;
            }

            String rest = afterNamespace.substring(pathEnd);

            if (rest.startsWith("{")) {
                int closeBrace = rest.indexOf('}');
                if (closeBrace < 0) {
                    return result;
                }
                rest = rest.substring(closeBrace + 1);
            }

            if (rest.isEmpty()) {
                result.add(new Suggestion(":", ":"));
                return result;
            }
            if (!rest.startsWith(":")) {
                return result;
            }
            String afterColon = rest.substring(1);
            String[] segments = afterColon.split(":", -1);

            if (segments.length == 1) {
                addPrefixed(result, BEHAVIORS, segments[0], "");
                return result;
            }

            int segIdx = segments.length - 1;
            String curSeg = segments[segIdx];
            if (curSeg.isEmpty()) {
                String label = switch (segIdx) {
                    case 1 -> "offsetX (число, напр. 0)";
                    case 2 -> "offsetY (число, напр. 0)";
                    case 3 -> "size (множитель, напр. 1)";
                    default -> null;
                };
                if (label != null) {
                    result.add(new Suggestion(label, ""));
                }
            }
            return result;
        }

        if (head.startsWith(PREFIX_ITEM)) {
            String tail = head.substring(PREFIX_ITEM.length());

            int firstColon = tail.indexOf(':');
            if (firstColon < 0) {
                if (tail.equals("minecraft")) {
                    result.add(new Suggestion(":", ":"));
                } else {
                    addPrefixed(result, new String[] { "minecraft" }, tail, ":");
                }
                return result;
            }
            String namespace = tail.substring(0, firstColon);
            if (!namespace.equals("minecraft")) {
                return result;
            }
            String afterNamespace = tail.substring(firstColon + 1);

            int braceIdx = afterNamespace.indexOf('{');
            boolean pathClosed = braceIdx >= 0 || afterNamespace.contains(">");

            String[] itemIds = itemIds();
            if (!pathClosed) {
                boolean exactMatch = false;
                for (String id : itemIds) {
                    if (id.equals(afterNamespace)) { exactMatch = true; break; }
                }
                if (!exactMatch) {
                    addPrefixed(result, itemIds, afterNamespace, "");
                    return result;
                }

                result.add(new Suggestion(">", ">"));
                return result;
            }

            return result;
        }

        return result;
    }

    private static void addPrefixed(List<Suggestion> out, String[] options, String typed, String suffix) {
        for (String opt : options) {
            if (opt.startsWith(typed) && !opt.equals(typed)) {
                String insertion = opt.substring(typed.length()) + suffix;
                out.add(new Suggestion(opt + suffix, insertion));
            }
        }
    }
}
