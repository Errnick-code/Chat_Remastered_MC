package dev.errnick.chatremastered.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

public final class ClientCompat {

    private ClientCompat() {
    }

    public static ChatComponent chat(Minecraft mc) {
        return mc.gui.getChat();
    }

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }
}
