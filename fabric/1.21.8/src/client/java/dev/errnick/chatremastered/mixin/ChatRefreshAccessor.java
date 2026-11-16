package dev.errnick.chatremastered.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatRefreshAccessor {

    @Invoker("refreshTrimmedMessages")
    void cr$refreshTrimmedMessages();
}
