package dev.errnicraft.chatremastered;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;

import java.io.File;

public final class DragDropHandler {

    private static boolean registered = false;
    private static volatile boolean enteredFromOutside = false;
    private static volatile boolean wasOutside = true;

    private DragDropHandler() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (registered) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            long windowHandle = mc.getWindow().handle();

            GLFW.glfwSetDropCallback(windowHandle, (window, count, names) -> {
                DragDropOverlay.setActive(false);
                enteredFromOutside = false;
                wasOutside = true;

                if (!(mc.screen instanceof ChatScreen)) {
                    return;
                }
                java.util.Set<String> seenPaths = new java.util.HashSet<>();
                for (int i = 0; i < count; i++) {
                    String path = GLFWDropCallback.getName(names, i);
                    if (!seenPaths.add(path)) {
                        continue;
                    }
                    File file = new File(path);
                    String name = file.getName().toLowerCase();
                    boolean isImage = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                            || name.endsWith(".webp") || name.endsWith(".bmp")
                            || name.endsWith(".tiff") || name.endsWith(".tif")
                            || name.endsWith(".gif");
                    if (isImage) {
                        if (file.length() <= 10L * 1024 * 1024) {
                            mc.execute(() -> ChatRemasteredClient.stageImage(file));
                        } else {
                            mc.execute(() -> mc.gui.getChat().addMessage(
                                    Component.literal(
                                            "§c[Chat Remastered] " + ChatRemasteredConfig.tr("chat-remastered.file_too_large")
                                    )
                            ));
                        }
                    }
                }
            });

            registered = true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!(client.screen instanceof ChatScreen)) {
                DragDropOverlay.setActive(false);
                enteredFromOutside = false;
                wasOutside = true;
                return;
            }
            var window = client.getWindow();
            long handle = window.handle();

            double[] xArr = new double[1];
            double[] yArr = new double[1];
            GLFW.glfwGetCursorPos(handle, xArr, yArr);
            double cx = xArr[0];
            double cy = yArr[0];
            double w = window.getWidth();
            double h = window.getHeight();
            boolean overWindow = cx >= 0 && cy >= 0 && cx <= w && cy <= h;

            boolean lmbDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (!overWindow) {
                wasOutside = true;
                enteredFromOutside = false;
                DragDropOverlay.setActive(false);
                return;
            }

            if (wasOutside && lmbDown) {
                enteredFromOutside = true;
            }
            if (wasOutside && !lmbDown) {
                enteredFromOutside = false;
            }
            wasOutside = false;

            boolean isDragging = enteredFromOutside && lmbDown;
            DragDropOverlay.setActive(isDragging);

            if (!lmbDown) {
                enteredFromOutside = false;
            }
        });
    }
}
