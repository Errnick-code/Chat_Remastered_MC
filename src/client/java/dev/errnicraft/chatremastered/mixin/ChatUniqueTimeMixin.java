package dev.errnicraft.chatremastered.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import dev.errnicraft.chatremastered.ChatTimeHolder;
import org.jspecify.annotations.Nullable;

/**
 * Гарантирует уникальный addedTime для каждого сообщения,
 * сохраняя корректный фейдинг (addedTime = guiTicks + маленькое смещение).
 *
 * Итоговый addedTime сохраняется в ChatTimeHolder откуда его читает
 * ChatRemasteredClient для синхронизации с ChatRemasteredStore.
 */
@Mixin(ChatComponent.class)
public class ChatUniqueTimeMixin {

    // Все поля приватные — Mixin не будет пытаться слить их в ChatComponent
    private static int cr$offset   = 0;
    private static int cr$lastBase = 0;

    @Redirect(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At(
            value = "NEW",
            target = "net/minecraft/client/GuiMessage"
        )
    )
    private GuiMessage cr$makeUniqueMessage(int addedTime, Component content,
                                             @Nullable MessageSignature signature,
                                             @Nullable GuiMessageTag tag) {
        int base = addedTime; // реальный guiTicks

        if (base != cr$lastBase) {
            cr$lastBase = base;
            if (cr$offset > 0) cr$offset--;
        }

        int uniqueTime = base + cr$offset;
        cr$offset++;

        // Сохраняем в отдельный утилитный класс (не в Mixin — там нельзя public static)
        ChatTimeHolder.lastAddedTime = uniqueTime;

        return new GuiMessage(uniqueTime, content, signature, tag);
    }
}
