package dev.errnicraft.chatremastered.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class ScreenshotMetadataCollector {

    public static final int MAX_RESOURCE_PACKS_LISTED = 10;

    private ScreenshotMetadataCollector() {
    }

    public static String getAuthorName() {
        Minecraft mc = Minecraft.getInstance();
        try {
            return mc.getUser().getName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static String getShaderPackName() {
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Method getCurrentPackName = irisClass.getMethod("getCurrentPackName");
            Object result = getCurrentPackName.invoke(null);
            if (result instanceof String s && !s.isEmpty()) {
                return s;
            }
            return null;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> getActiveResourcePackIds() {
        List<String> ids = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            for (Pack pack : mc.getResourcePackRepository().getSelectedPacks()) {
                String id = pack.getId();
                if (!id.startsWith("file/")) {
                    continue;
                }
                ids.add(id.substring("file/".length()));
            }
        } catch (Exception e) {

        }
        return ids;
    }

    public static String formatResourcePacksList(List<String> packIds) {
        if (packIds.isEmpty()) {
            return "vanilla";
        }
        int shown = Math.min(packIds.size(), MAX_RESOURCE_PACKS_LISTED);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(", ");
            sb.append(packIds.get(i));
        }
        int remaining = packIds.size() - shown;
        if (remaining > 0) {
            sb.append(" +").append(remaining).append(" more");
        }
        return sb.toString();
    }
}
