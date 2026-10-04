package com.slize.datarium.mixin.accessors;

import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlStateManager.class)
public interface AccessorGlStateManager {
    @Accessor("activeTextureUnit")
    static int datarium$getActiveTextureUnit() {
        throw new AssertionError();
    }
}
