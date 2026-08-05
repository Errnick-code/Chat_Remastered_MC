package dev.errnicraft.chatremastered.network;

import net.fabricmc.loader.api.FabricLoader;

public final class ModProtocol {

    public static final String MOD_PROTOCOL_VERSION = "1.260308";

    private static String cachedModVersion;

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
