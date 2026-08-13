package dev.errnicraft.chatremastered.server.config;

public record ServerConfig(
        String resolution,
        int imagePort,
        int photoCooldownSeconds,
        boolean gifEnabled,
        int gifMaxDim,
        String mutedMessage,
        int maxPhotosPerMessage
) {
    public static ServerConfig defaults() {
        return new ServerConfig(
                "720",
                5050,
                5,
                true,
                480,
                "You are muted and cannot send messages, photos or replies.",
                5
        );
    }
}
