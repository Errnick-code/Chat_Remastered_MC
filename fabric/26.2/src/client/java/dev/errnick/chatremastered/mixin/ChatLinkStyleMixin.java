package dev.errnick.chatremastered.mixin;

import dev.errnick.chatremastered.client.chat.LinkStyler;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatLinkStyleMixin {

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component cr$styleLinks(Component message) {
        return LinkStyler.style(message);
    }
}
