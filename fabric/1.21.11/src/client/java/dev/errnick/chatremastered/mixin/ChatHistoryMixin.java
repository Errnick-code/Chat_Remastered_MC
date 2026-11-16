package dev.errnick.chatremastered.mixin;

import dev.errnick.chatremastered.ChatRemasteredConfig;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ChatComponent.class)
public class ChatHistoryMixin {

    @ModifyConstant(
        method = "addMessageToQueue",
        constant = @Constant(intValue = 100)
    )
    private int cr$limitAllMessages(int original) {
        return ChatRemasteredConfig.getChatHistoryLimit();
    }

    @ModifyConstant(
        method = "addMessageToDisplayQueue",
        constant = @Constant(intValue = 100)
    )
    private int cr$limitTrimmedMessages(int original) {
        return ChatRemasteredConfig.getChatHistoryLimit();
    }
}
