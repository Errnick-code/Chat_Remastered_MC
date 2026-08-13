package dev.errnicraft.chatremastered.mixin;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import dev.errnicraft.chatremastered.ChatTimeHolder;
import org.jspecify.annotations.Nullable;

@Mixin(ChatComponent.class)
public class ChatUniqueTimeMixin {

    private static int cr$lastUnique = Integer.MIN_VALUE;

    @Redirect(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/client/multiplayer/chat/GuiMessage"
            )
    )
    private GuiMessage cr$makeUniqueMessage(int addedTime, Component content,
                                            @Nullable MessageSignature signature,
                                            GuiMessageSource source,
                                            @Nullable GuiMessageTag tag) {
        int base = addedTime;

        int uniqueTime = base > cr$lastUnique ? base : cr$lastUnique + 1;
        cr$lastUnique = uniqueTime;

        ChatTimeHolder.lastAddedTime = uniqueTime;

        return new GuiMessage(uniqueTime, content, signature, source, tag);
    }
}