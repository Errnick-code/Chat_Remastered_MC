package dev.errnicraft.chatremastered;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.function.Supplier;

public final class SmoothDynamicTexture extends DynamicTexture {

    public SmoothDynamicTexture(Supplier<String> label, NativeImage image) {
        super(label, image);
        this.getTexture().setTextureFilter(FilterMode.LINEAR, false);
    }
}
