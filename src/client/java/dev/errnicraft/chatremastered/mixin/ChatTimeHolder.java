package dev.errnicraft.chatremastered.mixin;

/**
 * Простое хранилище для addedTime последнего созданного GuiMessage.
 * Нужен потому что Mixin-класс не может содержать public static методы —
 * они попытаются влиться в целевой класс и вызовут InvalidMixinException.
 */
public final class ChatTimeHolder {
    private ChatTimeHolder() {}

    /** addedTime последнего GuiMessage, созданного через ChatUniqueTimeMixin. */
    public static int lastAddedTime = 0;
}
