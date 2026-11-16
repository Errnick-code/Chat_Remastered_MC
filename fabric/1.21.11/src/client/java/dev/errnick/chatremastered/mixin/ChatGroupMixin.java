package dev.errnick.chatremastered.mixin;

import dev.errnick.chatremastered.client.chat.ChatGrouping;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatGroupMixin {

    private static final String ADD_MESSAGE =
            "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V";

    @Inject(method = ADD_MESSAGE, at = @At("HEAD"), cancellable = true)
    private void cr$suppressRepeated(Component message, MessageSignature signature,
                                     GuiMessageTag tag, CallbackInfo ci) {
        if (ChatGrouping.shouldSuppress(message, tag)) {
            ci.cancel();
        }
    }

    @Inject(method = ADD_MESSAGE, at = @At("TAIL"))
    private void cr$rememberMessage(Component message, MessageSignature signature,
                                    GuiMessageTag tag, CallbackInfo ci) {
        ChatGrouping.remember(message, tag);
    }
}
