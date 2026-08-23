package dev.errnicraft.chatremastered.client;

import dev.errnicraft.chatremastered.ChatRemasteredStore;
import dev.errnicraft.chatremastered.ChatTimeHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class EntityChatRenderer {

    public static final int ENTITY_LINES = 9;

    public static final int ITEM_LINES = 5;

    public static final int BLOCK_LINES = 5;

    private EntityChatRenderer() {
    }

    private static net.minecraft.network.chat.Style resolveBracketStyle(Component senderComp) {
        var rootStyle = senderComp.getStyle();
        if (rootStyle.getColor() != null) {
            return rootStyle;
        }
        for (Component sib : senderComp.getSiblings()) {
            if (sib.getStyle().getColor() != null) {
                return sib.getStyle();
            }
        }
        return rootStyle;
    }

    public static void addItemToChat(Minecraft mc, String sender, Component senderComponent,
                                      String itemNamespace, String itemPath, String itemNbt, String caption) {
        addItemToChat(mc, sender, senderComponent, itemNamespace, itemPath, itemNbt, caption, "", 0);
    }

    public static void addItemToChat(Minecraft mc, String sender, Component senderComponent,
                                      String itemNamespace, String itemPath, String itemNbt, String caption,
                                      String groupLabel) {
        addItemToChat(mc, sender, senderComponent, itemNamespace, itemPath, itemNbt, caption, groupLabel, 0);
    }

    public static void addItemToChat(Minecraft mc, String sender, Component senderComponent,
                                      String itemNamespace, String itemPath, String itemNbt, String caption,
                                      String groupLabel, int bodyRgb) {
        Component senderComp = (senderComponent != null && !senderComponent.getString().isEmpty())
                ? senderComponent : Component.literal(sender);

        MutableComponent msgText = MutableComponent.create(PlainTextContents.EMPTY);
        var bracketStyle = resolveBracketStyle(senderComp);
        if (groupLabel != null && !groupLabel.isEmpty()) {
            msgText.append(dev.errnicraft.chatremastered.client.NickFormatting.parseLegacyNick(Component.literal(groupLabel)));
            msgText.append(Component.literal(" "));
        }
        msgText.append(Component.literal("<").withStyle(bracketStyle));
        msgText.append(senderComp);
        msgText.append(Component.literal(">").withStyle(bracketStyle));
        if (caption != null && !caption.isEmpty()) {
            if (bodyRgb != 0) {
                msgText.append(Component.literal(" " + caption)
                        .withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(bodyRgb))));
            } else {
                msgText.append(Component.literal(" " + caption));
            }
        }
        msgText.append(Component.literal(" §7[\uD83D\uDCE6]"));
        for (int i = 0; i < ITEM_LINES; i++) {
            msgText.append(Component.literal("\n"));
        }

        mc.gui.getChat().addClientSystemMessage(msgText);
        int addedTime = ChatTimeHolder.lastAddedTime;
        ChatRemasteredStore.addItemMessage(sender, senderComp, itemNamespace, itemPath, itemNbt,
                caption != null ? caption : "", addedTime);
    }

    public static void addEntityToChat(Minecraft mc, String sender, Component senderComponent,
                                      String targetPlayerName, String behavior, String caption) {
        addEntityToChat(mc, sender, senderComponent, targetPlayerName, behavior, caption, "", 0);
    }

    public static void addEntityToChat(Minecraft mc, String sender, Component senderComponent,
                                      String targetPlayerName, String behavior, String caption,
                                      String groupLabel, int bodyRgb) {
        Component senderComp = (senderComponent != null && !senderComponent.getString().isEmpty())
                ? senderComponent : Component.literal(sender);

        MutableComponent msgText = MutableComponent.create(PlainTextContents.EMPTY);
        var bracketStyle = resolveBracketStyle(senderComp);
        if (groupLabel != null && !groupLabel.isEmpty()) {
            msgText.append(NickFormatting.parseLegacyNick(Component.literal(groupLabel)));
            msgText.append(Component.literal(" "));
        }
        msgText.append(Component.literal("<").withStyle(bracketStyle));
        msgText.append(senderComp);
        msgText.append(Component.literal(">").withStyle(bracketStyle));
        if (caption != null && !caption.isEmpty()) {
            msgText.append(Component.literal(" " + caption));
        }
        msgText.append(Component.literal(" §7[👤]"));
        for (int i = 0; i < ENTITY_LINES; i++) {
            msgText.append(Component.literal("\n"));
        }

        mc.gui.getChat().addClientSystemMessage(msgText);
        int addedTime = ChatTimeHolder.lastAddedTime;
        ChatRemasteredStore.addEntityMessage(sender, senderComp, targetPlayerName, behavior,
                caption != null ? caption : "", addedTime);
    }

    public static void addEntityMobToChat(Minecraft mc, String sender, Component senderComponent,
                                           String entityNamespace, String entityPath, String entityNbt,
                                           String behavior, int size, int offsetX, int offsetY, String caption) {
        addEntityMobToChat(mc, sender, senderComponent, entityNamespace, entityPath, entityNbt, behavior, size, offsetX, offsetY, caption, "", 0);
    }

    public static void addEntityMobToChat(Minecraft mc, String sender, Component senderComponent,
                                           String entityNamespace, String entityPath, String entityNbt,
                                           String behavior, int size, int offsetX, int offsetY, String caption,
                                           String groupLabel, int bodyRgb) {
        Component senderComp = (senderComponent != null && !senderComponent.getString().isEmpty())
                ? senderComponent : Component.literal(sender);

        MutableComponent msgText = MutableComponent.create(PlainTextContents.EMPTY);
        var bracketStyle = resolveBracketStyle(senderComp);
        if (groupLabel != null && !groupLabel.isEmpty()) {
            msgText.append(NickFormatting.parseLegacyNick(Component.literal(groupLabel)));
            msgText.append(Component.literal(" "));
        }
        msgText.append(Component.literal("<").withStyle(bracketStyle));
        msgText.append(senderComp);
        msgText.append(Component.literal(">").withStyle(bracketStyle));
        if (caption != null && !caption.isEmpty()) {
            msgText.append(Component.literal(" " + caption));
        }
        msgText.append(Component.literal(" §7[👤]"));
        for (int i = 0; i < ENTITY_LINES; i++) {
            msgText.append(Component.literal("\n"));
        }

        mc.gui.getChat().addClientSystemMessage(msgText);
        int addedTime = ChatTimeHolder.lastAddedTime;
        ChatRemasteredStore.addEntityMobMessage(sender, senderComp, entityNamespace, entityPath, entityNbt,
                behavior, size, offsetX, offsetY, caption != null ? caption : "", addedTime);
    }

    public static void addBlockToChat(Minecraft mc, String sender, Component senderComponent,
                                      String blockNamespace, String blockPath, String blockState, String caption) {
        addBlockToChat(mc, sender, senderComponent, blockNamespace, blockPath, blockState, caption, "", 0);
    }

    public static void addBlockToChat(Minecraft mc, String sender, Component senderComponent,
                                      String blockNamespace, String blockPath, String blockState, String caption,
                                      String groupLabel, int bodyRgb) {
        Component senderComp = (senderComponent != null && !senderComponent.getString().isEmpty())
                ? senderComponent : Component.literal(sender);

        MutableComponent msgText = MutableComponent.create(PlainTextContents.EMPTY);
        var bracketStyle = resolveBracketStyle(senderComp);
        if (groupLabel != null && !groupLabel.isEmpty()) {
            msgText.append(NickFormatting.parseLegacyNick(Component.literal(groupLabel)));
            msgText.append(Component.literal(" "));
        }
        msgText.append(Component.literal("<").withStyle(bracketStyle));
        msgText.append(senderComp);
        msgText.append(Component.literal(">").withStyle(bracketStyle));
        if (caption != null && !caption.isEmpty()) {
            msgText.append(Component.literal(" " + caption));
        }
        msgText.append(Component.literal(" §7[🧱]"));
        for (int i = 0; i < BLOCK_LINES; i++) {
            msgText.append(Component.literal("\n"));
        }

        mc.gui.getChat().addClientSystemMessage(msgText);
        int addedTime = ChatTimeHolder.lastAddedTime;
        ChatRemasteredStore.addBlockMessage(sender, senderComp, blockNamespace, blockPath, blockState,
                caption != null ? caption : "", addedTime);
    }
}


