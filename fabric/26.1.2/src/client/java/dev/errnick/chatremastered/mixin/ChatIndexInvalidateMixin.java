package dev.errnick.chatremastered.mixin;

import dev.errnick.chatremastered.client.chat.ChatGrouping;
import dev.errnick.chatremastered.client.chat.ChatLineIndex;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatIndexInvalidateMixin {

    @Inject(method = "addMessage", at = @At("TAIL"))
    private void cr$invalidateAfterAdd(CallbackInfo ci) {
        ChatLineIndex.invalidate();
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("TAIL"))
    private void cr$invalidateAfterRefresh(CallbackInfo ci) {
        ChatLineIndex.invalidate();
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void cr$resetAfterClear(CallbackInfo ci) {
        ChatLineIndex.invalidate();
        ChatGrouping.reset();
    }
}
