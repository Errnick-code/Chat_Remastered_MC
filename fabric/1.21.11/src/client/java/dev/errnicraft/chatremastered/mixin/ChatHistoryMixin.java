package dev.errnicraft.chatremastered.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ChatComponent.class)
public class ChatHistoryMixin {

    private static final int MAX_HISTORY = 16384;

    @ModifyConstant(
        method = "addMessageToQueue",
        constant = @Constant(intValue = 100)
    )
    private int cr$unlimitAllMessages(int original) {
        return MAX_HISTORY;
    }

    @ModifyConstant(
        method = "addMessageToDisplayQueue",
        constant = @Constant(intValue = 100)
    )
    private int cr$unlimitTrimmedMessages(int original) {
        return MAX_HISTORY;
    }
}
