package dev.errnicraft.chatremastered;

public class DragDropOverlay {
    private static volatile boolean active = false;

    public static boolean isActive() { return active; }
    public static void setActive(boolean v) { active = v; }
}
