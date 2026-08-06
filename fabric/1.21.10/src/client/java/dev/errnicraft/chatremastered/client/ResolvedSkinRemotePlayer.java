package dev.errnicraft.chatremastered.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.function.Supplier;

public final class ResolvedSkinRemotePlayer extends RemotePlayer {
    private final Supplier<PlayerSkin> skinLookup;

    public ResolvedSkinRemotePlayer(ClientLevel level, GameProfile gameProfile) {
        super(level, gameProfile);
        boolean requireSecure = !Minecraft.getInstance().isLocalPlayer(gameProfile.id());
        this.skinLookup = Minecraft.getInstance().getSkinManager().createLookup(gameProfile, requireSecure);
        int allPartsMask = 0;
        for (net.minecraft.world.entity.player.PlayerModelPart part : net.minecraft.world.entity.player.PlayerModelPart.values()) {
            allPartsMask |= part.getMask();
        }
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) allPartsMask);
    }

    @Override
    public PlayerSkin getSkin() {
        return this.skinLookup.get();
    }
}
