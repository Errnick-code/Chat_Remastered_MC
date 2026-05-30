package dev.errnicraft.chatremastered;

/**
 * Простое хранилище для addedTime последнего созданного GuiMessage.
 * Находится вне пакета .mixin.* — иначе Mixin запрещает прямые ссылки на него.
 */
public final class ChatTimeHolder {
    private ChatTimeHolder() {}

    /** addedTime последнего GuiMessage, созданного через ChatUniqueTimeMixin. */
    public static int lastAddedTime = 0;
}
