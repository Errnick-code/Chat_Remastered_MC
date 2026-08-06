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
import org.jetbrains.annotations.Nullable;

@Mixin(ChatComponent.class)
public class ChatUniqueTimeMixin {

    private static int cr$lastUnique = Integer.MIN_VALUE;

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
        int base = addedTime;

        int uniqueTime = base > cr$lastUnique ? base : cr$lastUnique + 1;
        cr$lastUnique = uniqueTime;

        ChatTimeHolder.lastAddedTime = uniqueTime;

        return new GuiMessage(uniqueTime, content, signature, tag);
    }
}