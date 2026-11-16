package dev.errnick.chatremastered.client.chat;

import dev.errnick.chatremastered.mixin.ChatComponentAccessor;
import dev.errnick.chatremastered.mixin.ChatRefreshAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.List;

public final class LinkHandler {

    private LinkHandler() {
    }

    public static void handleOpenUrl(Minecraft mc, URI uri) {
        if (mc == null || uri == null) {
            return;
        }
        if (!LinkVisitedStore.markVisited(uri.toString())) {
            return;
        }
        restyleChat(mc);
    }

    private static void restyleChat(Minecraft mc) {
        if (mc.gui == null) {
            return;
        }
        ChatComponent chat = mc.gui.getChat();
        List<GuiMessage> messages = ((ChatComponentAccessor) chat).getAllMessages();
        boolean changed = false;

        for (int i = 0; i < messages.size(); i++) {
            GuiMessage message = messages.get(i);
            Component styled = LinkStyler.style(message.content());
            if (styled == null || styled.equals(message.content())) {
                continue;
            }
            messages.set(i, new GuiMessage(message.addedTime(), styled, message.signature(),
                    message.source(), message.tag()));
            changed = true;
        }

        if (changed) {
            ((ChatRefreshAccessor) chat).cr$refreshTrimmedMessages();
        }
    }
}
