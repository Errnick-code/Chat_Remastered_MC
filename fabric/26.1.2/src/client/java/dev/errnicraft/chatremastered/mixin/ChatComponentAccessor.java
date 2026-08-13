package dev.errnicraft.chatremastered.mixin;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int getChatScrollbarPos();

    @Accessor("chatScrollbarPos")
    void setChatScrollbarPos(int pos);

    @Accessor("allMessages")
    List<GuiMessage> getAllMessages();
}
