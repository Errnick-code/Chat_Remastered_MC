package dev.errnicraft.chatremastered.client;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plain (non-@Mixin) interface implemented by ChatComponentMixin.
 *
 * ChatComponentMixin is a class-target mixin, so it has no real runtime
 * supertype relationship with ChatScreen — the Mixin transformer cannot
 * resolve a cast to it from another mixin's local variable table. Casting
 * to this ordinary interface instead works fine, since interfaces are
 * resolved normally by the JVM/transformer regardless of which mixin
 * implements them.
 *
 * This type must live outside the dev.errnicraft.chatremastered.mixin
 * package: that whole package is registered as the mixin config's
 * "package", so Mixin's class loader refuses to let any class from it
 * (mixin or not) be referenced/loaded directly.
 */
public interface ChatOverlayRenderer {
    void chatremastered$renderImages(GuiGraphics guiGraphics, int ticks, int mouseX, int mouseY, boolean isChatting, CallbackInfo ci);
    void chatremastered$renderReplies(GuiGraphics guiGraphics, int ticks, int mouseX, int mouseY, boolean isChatting, CallbackInfo ci);
    void chatremastered$renderEntities(GuiGraphics guiGraphics, int ticks, int mouseX, int mouseY, boolean isChatting, CallbackInfo ci);
    void chatremastered$renderItems(GuiGraphics guiGraphics, int ticks, int mouseX, int mouseY, boolean isChatting, CallbackInfo ci);

    int chatremastered$getLastTicks();
    int chatremastered$getLastMouseX();
    int chatremastered$getLastMouseY();
    boolean chatremastered$getLastIsChatting();
}
