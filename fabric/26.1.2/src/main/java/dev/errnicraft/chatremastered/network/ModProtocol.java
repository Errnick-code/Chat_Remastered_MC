package dev.errnicraft.chatremastered.network;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;

public final class ModProtocol {

    public static final String MOD_PROTOCOL_VERSION = resolveProtocolVersion();

    private static String cachedModVersion;

    private static String resolveProtocolVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer("chat-remastered")
                    .map(c -> c.getMetadata().getCustomValue("chatremastered:protocol_version"))
                    .filter(v -> v.getType() == CustomValue.CvType.STRING)
                    .map(CustomValue::getAsString)
                    .orElse("?.?.?");
        } catch (Exception e) {
            return "?.?.?";
        }
    }

    public static String getModVersion() {
        if (cachedModVersion == null) {
            try {
                cachedModVersion = FabricLoader.getInstance()
                        .getModContainer("chat-remastered")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("?.?.?");
            } catch (Exception e) {
                cachedModVersion = "?.?.?";
            }
        }
        return cachedModVersion;
    }

    private ModProtocol() {
    }
}
