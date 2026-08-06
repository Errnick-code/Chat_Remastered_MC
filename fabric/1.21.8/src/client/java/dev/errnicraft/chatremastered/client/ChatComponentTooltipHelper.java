package dev.errnicraft.chatremastered.client;

import dev.errnicraft.chatremastered.ChatRemasteredConfig;
import dev.errnicraft.chatremastered.ImageCache;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Map;

public final class ChatComponentTooltipHelper {

    private static final int COLOR_TITLE = 0xFFFFFF;
    private static final int COLOR_VALUE = 0xAAAAAA;
    private static final int MAX_RESOURCE_PACKS_LISTED = 10;

    private ChatComponentTooltipHelper() {
    }

    public static void appendMetadataLines(List<ClientTooltipComponent> lines, String imageId) {
        byte[] fullBytes = ImageCache.getFullData(imageId);
        if (fullBytes == null) {
            return;
        }
        Map<String, String> meta = ScreenshotMetadataWriter.readMetadata(fullBytes);
        if (meta.isEmpty()) {
            return;
        }

        String author = meta.get("Author");
        if (author != null && !author.isEmpty()) {
            addLine(lines, ChatRemasteredConfig.tr("chat-remastered.meta_author"), author);
        }

        String created = meta.get("Created");
        if (created != null && !created.isEmpty()) {
            addLine(lines, ChatRemasteredConfig.tr("chat-remastered.meta_time"), created);
        }

        String shader = meta.get("Shaderpack");
        if (shader != null && !shader.isEmpty() && !shader.equals("none")) {
            addLine(lines, ChatRemasteredConfig.tr("chat-remastered.meta_shaderpack"), shader);
        }

        String resourcePacksRaw = meta.get("ResourcePacks");
        if (resourcePacksRaw != null && !resourcePacksRaw.isEmpty() && !resourcePacksRaw.equals("vanilla")) {
            String[] packIds = resourcePacksRaw.split(",\\s*");
            addResourcePacksLines(lines, packIds);
        }
    }

    private static void addLine(List<ClientTooltipComponent> lines, String title, String value) {
        Component titleComp = Component.literal(title + ": ").setStyle(Style.EMPTY.withColor(COLOR_TITLE));
        Component valueComp = Component.literal(value).setStyle(Style.EMPTY.withColor(COLOR_VALUE));
        lines.add(ClientTooltipComponent.create(titleComp.copy().append(valueComp).getVisualOrderText()));
    }

    private static void addResourcePacksLines(List<ClientTooltipComponent> lines, String[] packIds) {
        Component titleComp = Component.literal(ChatRemasteredConfig.tr("chat-remastered.meta_resourcepacks") + ":")
                .setStyle(Style.EMPTY.withColor(COLOR_TITLE));
        lines.add(ClientTooltipComponent.create(titleComp.getVisualOrderText()));

        int shown = Math.min(packIds.length, MAX_RESOURCE_PACKS_LISTED);
        for (int i = 0; i < shown; i++) {
            String id = packIds[i].trim();
            if (id.isEmpty()) continue;
            Component valueComp = Component.literal(id).setStyle(Style.EMPTY.withColor(COLOR_VALUE));
            lines.add(ClientTooltipComponent.create(valueComp.getVisualOrderText()));
        }

        int remaining = packIds.length - shown;
        if (remaining > 0) {
            String moreText = ChatRemasteredConfig.tr("chat-remastered.meta_resourcepacks_more", remaining);
            Component moreComp = Component.literal(moreText).setStyle(Style.EMPTY.withColor(COLOR_VALUE));
            lines.add(ClientTooltipComponent.create(moreComp.getVisualOrderText()));
        }
    }
}
