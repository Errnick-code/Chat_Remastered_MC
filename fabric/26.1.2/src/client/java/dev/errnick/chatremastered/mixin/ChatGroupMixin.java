package dev.errnick.chatremastered.mixin;

import dev.errnick.chatremastered.client.chat.ChatGrouping;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatGroupMixin {

    @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void cr$suppressRepeated(Component message, MessageSignature signature,
                                     GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (ChatGrouping.shouldSuppress(message, source)) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage", at = @At("TAIL"))
    private void cr$rememberMessage(Component message, MessageSignature signature,
                                    GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        ChatGrouping.remember(message, source);
    }
}
