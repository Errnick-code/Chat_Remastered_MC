package dev.errnicraft.chatremastered.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EntityTagHighlighter {

    private static final Style PUNCT_STYLE   = Style.EMPTY.withColor(ChatFormatting.GRAY);
    private static final Style ROOT_STYLE     = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE);
    private static final Style KIND_STYLE     = Style.EMPTY.withColor(ChatFormatting.YELLOW);
    private static final Style NAMESPACE_STYLE = Style.EMPTY.withColor(ChatFormatting.AQUA);
    private static final Style PATH_STYLE     = Style.EMPTY.withColor(ChatFormatting.DARK_AQUA);
    private static final Style NBT_STYLE      = Style.EMPTY.withColor(ChatFormatting.GOLD);
    private static final Style BEHAVIOR_STYLE = Style.EMPTY.withColor(ChatFormatting.GREEN);
    private static final Style NUMBER_STYLE   = Style.EMPTY.withColor(ChatFormatting.DARK_GREEN);
    private static final Style PLAYER_STYLE   = Style.EMPTY.withColor(ChatFormatting.AQUA);
    private static final Style DEFAULT_STYLE  = Style.EMPTY;

    private static final Pattern ENTITY_HL_PATTERN = Pattern.compile(
            "^(<)(chat_remastered)(:)(entity)(:)([a-z0-9_.-]*)(:)([a-z0-9_./-]*)(\\{[^>]*})?"
                    + "(?:(:)(tocursor|rotate)?)?"
                    + "(?:(:)(-?[0-9]{0,4}))?"
                    + "(?:(:)(-?[0-9]{0,4}))?"
                    + "(?:(:)(-?[0-9]*(?:\\.[0-9]*)?))?"
                    + "(>)?"
    );

    private static final Pattern PLAYER_HL_PATTERN = Pattern.compile(
            "^(<)(chat_remastered)(:)(player)(:)([A-Za-z0-9_]{0,16})"
                    + "(?:(:)(tocursor|rotate)?)?"
                    + "(>)?"
    );

    private static final Pattern ITEM_HL_PATTERN = Pattern.compile(
            "^(<)(chat_remastered)(:)(item)(:)([a-z0-9_.-]*)(:)([a-z0-9_./-]*)(\\{[^>]*})?(>)?"
    );

    private static final Pattern HAND_ITEM_HL_PATTERN = Pattern.compile(
            "^(<)(item)(>)?"
    );

    private static final Pattern BLOCK_HL_PATTERN = Pattern.compile(
            "^(<)(chat_remastered)(:)(block)(:)([a-z0-9_.-]*)(:)([a-z0-9_./-]*)(\\{[^>]*})?(>)?"
    );

    private static final Pattern LOOK_BLOCK_HL_PATTERN = Pattern.compile(
            "^(<)(block)(>)?"
    );

    private static final Pattern LOOK_ENTITY_HL_PATTERN = Pattern.compile(
            "^(<)(entity)(>)?"
    );

    private static final Pattern SHORT_PLAYER_HL_PATTERN = Pattern.compile(
            "^(<)(player)(:)([A-Za-z0-9_]{0,16})(>)?"
    );

    private static final Pattern UUID_HL_PATTERN = Pattern.compile(
            "^(<)([0-9a-fA-F-]{0,36})(>)?"
    );

    private enum TagKind { ENTITY, PLAYER, ITEM, HAND_ITEM, LOOK_ENTITY, SHORT_PLAYER, UUID, BLOCK, LOOK_BLOCK }

    private EntityTagHighlighter() {
    }

    public static FormattedCharSequence highlight(String fullText, String fragment, int offset) {
        if (fullText == null
                || !(fullText.startsWith("<chat_remastered:") || fullText.startsWith("<item")
                     || fullText.startsWith("<entity") || fullText.startsWith("<player:")
                     || fullText.startsWith("<block")
                     || isUuidStart(fullText))) {
            return FormattedCharSequence.forward(fragment, DEFAULT_STYLE);
        }

        FormattedCharSequence wholeLine = highlightWhole(fullText);
        int fragEnd = offset + fragment.length();
        if (offset <= 0 && fragEnd >= fullText.length()) {
            return wholeLine;
        }

        int clampedOffset = Mth.clamp(offset, 0, fullText.length());
        int clampedEnd = Mth.clamp(fragEnd, clampedOffset, fullText.length());
        List<FormattedCharSequence> parts = new ArrayList<>();
        int runStart = clampedOffset;
        Style runStyle = clampedEnd > clampedOffset ? styleAt(fullText, clampedOffset) : DEFAULT_STYLE;
        for (int i = clampedOffset + 1; i < clampedEnd; i++) {
            Style style = styleAt(fullText, i);
            if (!style.equals(runStyle)) {
                parts.add(FormattedCharSequence.forward(fullText.substring(runStart, i), runStyle));
                runStart = i;
                runStyle = style;
            }
        }
        if (clampedEnd > runStart) {
            parts.add(FormattedCharSequence.forward(fullText.substring(runStart, clampedEnd), runStyle));
        }
        return FormattedCharSequence.composite(parts);
    }

    private static boolean isUuidStart(String text) {
        if (!text.startsWith("<") || text.length() < 2) return false;
        char c = text.charAt(1);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static Style styleAt(String fullText, int charIndex) {
        Matcher entityM = ENTITY_HL_PATTERN.matcher(fullText);
        if (entityM.lookingAt()) {
            return styleFromMatch(entityM, charIndex, TagKind.ENTITY);
        }
        Matcher itemM = ITEM_HL_PATTERN.matcher(fullText);
        if (itemM.lookingAt()) {
            return styleFromMatch(itemM, charIndex, TagKind.ITEM);
        }
        Matcher blockM = BLOCK_HL_PATTERN.matcher(fullText);
        if (blockM.lookingAt()) {
            return styleFromMatch(blockM, charIndex, TagKind.BLOCK);
        }
        Matcher playerM = PLAYER_HL_PATTERN.matcher(fullText);
        if (playerM.lookingAt()) {
            return styleFromMatch(playerM, charIndex, TagKind.PLAYER);
        }
        Matcher handItemM = HAND_ITEM_HL_PATTERN.matcher(fullText);
        if (handItemM.lookingAt()) {
            return styleFromMatch(handItemM, charIndex, TagKind.HAND_ITEM);
        }
        Matcher lookEntityM = LOOK_ENTITY_HL_PATTERN.matcher(fullText);
        if (lookEntityM.lookingAt()) {
            return styleFromMatch(lookEntityM, charIndex, TagKind.LOOK_ENTITY);
        }
        Matcher lookBlockM = LOOK_BLOCK_HL_PATTERN.matcher(fullText);
        if (lookBlockM.lookingAt()) {
            return styleFromMatch(lookBlockM, charIndex, TagKind.LOOK_BLOCK);
        }
        Matcher shortPlayerM = SHORT_PLAYER_HL_PATTERN.matcher(fullText);
        if (shortPlayerM.lookingAt()) {
            return styleFromMatch(shortPlayerM, charIndex, TagKind.SHORT_PLAYER);
        }
        Matcher uuidM = UUID_HL_PATTERN.matcher(fullText);
        if (uuidM.lookingAt()) {
            return styleFromMatch(uuidM, charIndex, TagKind.UUID);
        }
        return DEFAULT_STYLE;
    }

    private static Style styleFromMatch(Matcher active, int charIndex, TagKind kind) {
        if (charIndex >= active.end()) {
            return DEFAULT_STYLE;
        }
        int groupCount = active.groupCount();
        for (int g = 1; g <= groupCount; g++) {
            int gs = active.start(g);
            int ge = active.end(g);
            if (gs < 0) continue;
            if (charIndex >= gs && charIndex < ge) {
                return styleForGroup(g, kind);
            }
        }
        return DEFAULT_STYLE;
    }

    private static FormattedCharSequence highlightWhole(String text) {
        Matcher em = ENTITY_HL_PATTERN.matcher(text);
        if (em.lookingAt()) {
            return buildFromMatcher(text, em, TagKind.ENTITY);
        }
        Matcher im = ITEM_HL_PATTERN.matcher(text);
        if (im.lookingAt()) {
            return buildFromMatcher(text, im, TagKind.ITEM);
        }
        Matcher bm = BLOCK_HL_PATTERN.matcher(text);
        if (bm.lookingAt()) {
            return buildFromMatcher(text, bm, TagKind.BLOCK);
        }
        Matcher pm = PLAYER_HL_PATTERN.matcher(text);
        if (pm.lookingAt()) {
            return buildFromMatcher(text, pm, TagKind.PLAYER);
        }
        Matcher hm = HAND_ITEM_HL_PATTERN.matcher(text);
        if (hm.lookingAt()) {
            return buildFromMatcher(text, hm, TagKind.HAND_ITEM);
        }
        Matcher lem = LOOK_ENTITY_HL_PATTERN.matcher(text);
        if (lem.lookingAt()) {
            return buildFromMatcher(text, lem, TagKind.LOOK_ENTITY);
        }
        Matcher lbm = LOOK_BLOCK_HL_PATTERN.matcher(text);
        if (lbm.lookingAt()) {
            return buildFromMatcher(text, lbm, TagKind.LOOK_BLOCK);
        }
        Matcher spm = SHORT_PLAYER_HL_PATTERN.matcher(text);
        if (spm.lookingAt()) {
            return buildFromMatcher(text, spm, TagKind.SHORT_PLAYER);
        }
        Matcher um = UUID_HL_PATTERN.matcher(text);
        if (um.lookingAt()) {
            return buildFromMatcher(text, um, TagKind.UUID);
        }

        if (text.startsWith("<chat_remastered")) {
            return FormattedCharSequence.composite(
                    FormattedCharSequence.forward("<", PUNCT_STYLE),
                    FormattedCharSequence.forward("chat_remastered", ROOT_STYLE),
                    FormattedCharSequence.forward(text.substring("<chat_remastered".length()), DEFAULT_STYLE)
            );
        }
        return FormattedCharSequence.forward(text, DEFAULT_STYLE);
    }

    private static FormattedCharSequence buildFromMatcher(String text, Matcher m, TagKind kind) {
        List<FormattedCharSequence> parts = new ArrayList<>();
        int matchEnd = m.end();
        int groupCount = m.groupCount();

        for (int g = 1; g <= groupCount; g++) {
            String val = m.group(g);
            if (val == null || val.isEmpty()) continue;
            Style style = styleForGroup(g, kind);
            parts.add(FormattedCharSequence.forward(val, style));
        }

        if (matchEnd < text.length()) {

            parts.add(FormattedCharSequence.forward(text.substring(matchEnd), DEFAULT_STYLE));
        }

        return FormattedCharSequence.composite(parts);
    }

    private static Style styleForGroup(int g, TagKind kind) {
        return switch (kind) {
            case ENTITY -> switch (g) {
                case 1, 3, 5, 7, 10, 12, 14, 16, 18 -> PUNCT_STYLE;
                case 2 -> ROOT_STYLE;
                case 4 -> KIND_STYLE;
                case 6 -> NAMESPACE_STYLE;
                case 8 -> PATH_STYLE;
                case 9 -> NBT_STYLE;
                case 11 -> BEHAVIOR_STYLE;
                case 13, 15 -> NUMBER_STYLE;
                case 17 -> NUMBER_STYLE;
                default -> PUNCT_STYLE;
            };
            case PLAYER -> switch (g) {
                case 1, 3, 5, 7, 9 -> PUNCT_STYLE;
                case 2 -> ROOT_STYLE;
                case 4 -> KIND_STYLE;
                case 6 -> PLAYER_STYLE;
                case 8 -> BEHAVIOR_STYLE;
                default -> PUNCT_STYLE;
            };
            case ITEM -> switch (g) {
                case 1, 3, 5, 7, 10 -> PUNCT_STYLE;
                case 2 -> ROOT_STYLE;
                case 4 -> KIND_STYLE;
                case 6 -> NAMESPACE_STYLE;
                case 8 -> PATH_STYLE;
                case 9 -> NBT_STYLE;
                default -> PUNCT_STYLE;
            };
            case HAND_ITEM -> switch (g) {
                case 1, 3 -> PUNCT_STYLE;
                case 2 -> KIND_STYLE;
                default -> PUNCT_STYLE;
            };
            case LOOK_BLOCK -> switch (g) {
                case 1, 3 -> PUNCT_STYLE;
                case 2 -> KIND_STYLE;
                default -> PUNCT_STYLE;
            };
            case BLOCK -> switch (g) {
                case 1, 3, 5, 7, 10 -> PUNCT_STYLE;
                case 2 -> ROOT_STYLE;
                case 4 -> KIND_STYLE;
                case 6 -> NAMESPACE_STYLE;
                case 8 -> PATH_STYLE;
                case 9 -> NBT_STYLE;
                default -> PUNCT_STYLE;
            };
            case LOOK_ENTITY -> switch (g) {
                case 1, 3 -> PUNCT_STYLE;
                case 2 -> KIND_STYLE;
                default -> PUNCT_STYLE;
            };
            case SHORT_PLAYER -> switch (g) {
                case 1, 3, 5 -> PUNCT_STYLE;
                case 2 -> KIND_STYLE;
                case 4 -> PLAYER_STYLE;
                default -> PUNCT_STYLE;
            };
            case UUID -> switch (g) {
                case 1, 3 -> PUNCT_STYLE;
                case 2 -> NAMESPACE_STYLE;
                default -> PUNCT_STYLE;
            };
        };
    }
}
