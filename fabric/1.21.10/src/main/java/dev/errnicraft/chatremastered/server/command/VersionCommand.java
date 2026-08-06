package dev.errnicraft.chatremastered.server.command;

import dev.errnicraft.chatremastered.network.ModProtocol;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class VersionCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("chat-remastered")
                        .executes(ctx -> {
                            var src = ctx.getSource();
                            String mcVersion;
                            try {
                                mcVersion = FabricLoader.getInstance()
                                        .getModContainer("minecraft")
                                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                                        .orElse("?");
                            } catch (Exception e) {
                                mcVersion = "?";
                            }
                            String fullVersion = ModProtocol.getModVersion() + "+" + mcVersion;

                            src.sendSystemMessage(
                                    Component.empty()
                                            .append(Component.literal("Chat Remastered").withStyle(s -> s.withColor(0xFF6B00).withBold(true)))
                                            .append(Component.literal("  Version: ").withStyle(ChatFormatting.GRAY))
                                            .append(Component.literal(fullVersion).withStyle(ChatFormatting.AQUA))
                                            .append(Component.literal("  Protocol: v" + ModProtocol.MOD_PROTOCOL_VERSION).withStyle(ChatFormatting.AQUA))
                            );
                            return 1;
                        })
        ));
    }

    private VersionCommand() {
    }
}
