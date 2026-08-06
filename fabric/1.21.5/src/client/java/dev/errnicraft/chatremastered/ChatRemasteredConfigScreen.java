package dev.errnicraft.chatremastered;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ChatRemasteredConfigScreen {

    private ChatRemasteredConfigScreen() {
    }

    public static Screen build(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("chat-remastered.config_title"));

        builder.setSavingRunnable(ChatRemasteredConfig::saveConfig);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory chat = builder.getOrCreateCategory(Component.translatable("chat-remastered.section_chat"));

        chat.addEntry(entryBuilder
                .startIntSlider(Component.translatable("chat-remastered.closed_chat_lines_slider_label"),
                        ChatRemasteredConfig.getClosedChatLines(), 8, 20)
                .setDefaultValue(10)
                .setTextGetter(v -> Component.translatable("chat-remastered.closed_chat_lines_slider", v))
                .setTooltip(Component.translatable("chat-remastered.closed_chat_lines_slider_tooltip"))
                .setSaveConsumer(v -> {
                    ChatRemasteredConfig.setClosedChatLines(v);
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null) {
                        mc.gui.getChat().rescaleChat();
                    }
                })
                .build());

        chat.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("chat-remastered.fullscreen_chat_label"),
                        ChatRemasteredConfig.getFullscreenChat())
                .setDefaultValue(false)
                .setYesNoTextSupplier(v -> v
                        ? Component.translatable("chat-remastered.chat_height_fullscreen")
                        : Component.translatable("chat-remastered.chat_height_vanilla"))
                .setTooltip(Component.translatable("chat-remastered.fullscreen_chat_tooltip"))
                .setSaveConsumer(v -> {
                    ChatRemasteredConfig.setFullscreenChat(v);
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null) {
                        mc.gui.getChat().rescaleChat();
                    }
                })
                .build());

        ConfigCategory photo = builder.getOrCreateCategory(Component.translatable("chat-remastered.section_photo"));

        photo.addEntry(entryBuilder
                .startIntSlider(Component.translatable("chat-remastered.preview_scale_slider_label"),
                        Math.round(ChatRemasteredConfig.getPreviewScale() * 100), 50, 200)
                .setDefaultValue(100)
                .setTextGetter(v -> Component.translatable("chat-remastered.preview_scale_slider", v + "%"))
                .setTooltip(Component.translatable("chat-remastered.preview_scale_slider_tooltip"))
                .setSaveConsumer(v -> ChatRemasteredConfig.setPreviewScale(v / 100.0f))
                .build());

        photo.addEntry(entryBuilder
                .startIntSlider(Component.translatable("chat-remastered.input_scale_slider_label"),
                        Math.round(ChatRemasteredConfig.getInputPreviewScale() * 100), 50, 200)
                .setDefaultValue(100)
                .setTextGetter(v -> Component.translatable("chat-remastered.input_scale_slider", v + "%"))
                .setTooltip(Component.translatable("chat-remastered.input_scale_slider_tooltip"))
                .setSaveConsumer(v -> ChatRemasteredConfig.setInputPreviewScale(v / 100.0f))
                .build());

        photo.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("chat-remastered.group_photos_row_mode_label"),
                        ChatRemasteredConfig.getGroupPhotosRowMode())
                .setDefaultValue(false)
                .setYesNoTextSupplier(v -> v
                        ? Component.translatable("chat-remastered.group_photos_row_mode_row")
                        : Component.translatable("chat-remastered.group_photos_row_mode_strip"))
                .setTooltip(Component.translatable("chat-remastered.group_photos_row_mode_tooltip"))
                .setSaveConsumer(ChatRemasteredConfig::setGroupPhotosRowMode)
                .build());

        photo.addEntry(entryBuilder
                .startSelector(Component.translatable("chat-remastered.remove_anim_label"),
                        new Integer[]{0, 1, 2}, ChatRemasteredConfig.getRemoveAnimMode())
                .setDefaultValue(0)
                .setNameProvider(v -> switch (v) {
                    case 1 -> Component.translatable("chat-remastered.remove_anim_shatter_small");
                    case 2 -> Component.translatable("chat-remastered.remove_anim_shatter_large");
                    default -> Component.translatable("chat-remastered.remove_anim_fly");
                })
                .setTooltip(Component.translatable("chat-remastered.remove_anim_tooltip"))
                .setSaveConsumer(ChatRemasteredConfig::setRemoveAnimMode)
                .build());

        photo.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("chat-remastered.screenshots_panel_side_label"),
                        ChatRemasteredConfig.getScreenshotsPanelOnLeft())
                .setDefaultValue(false)
                .setYesNoTextSupplier(v -> v
                        ? Component.translatable("chat-remastered.screenshots_panel_side_left")
                        : Component.translatable("chat-remastered.screenshots_panel_side_right"))
                .setTooltip(Component.translatable("chat-remastered.screenshots_panel_side_tooltip"))
                .setSaveConsumer(ChatRemasteredConfig::setScreenshotsPanelOnLeft)
                .build());

        return builder.build();
    }
}
