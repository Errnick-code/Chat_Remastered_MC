package dev.errnick.chatremastered.mixin;

import dev.errnick.chatremastered.ChatRemasteredConfig;
import dev.errnick.chatremastered.client.chat.LinkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenLinkMixin {

    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
    private static void cr$handleChatLink(ClickEvent event, Minecraft minecraft, Screen activeScreen, CallbackInfo ci) {
        if (!(event instanceof ClickEvent.OpenUrl openUrl)) {
            return;
        }
        if (!(activeScreen instanceof ChatScreen)) {
            return;
        }
        LinkHandler.handleOpenUrl(minecraft, openUrl.uri());
        if (!ChatRemasteredConfig.getLinkOpenEnabled()) {
            ci.cancel();
        }
    }
}
