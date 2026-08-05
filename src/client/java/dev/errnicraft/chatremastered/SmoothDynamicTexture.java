package dev.errnicraft.chatremastered;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.function.Supplier;

public final class SmoothDynamicTexture extends DynamicTexture {

    public SmoothDynamicTexture(Supplier<String> label, NativeImage image) {
        super(label, image);
        this.sampler = RenderSystem.getSamplerCache()
                .getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
    }
}
