package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETRender;
import com.slize.datarium.client.cet.CETState;
import com.slize.datarium.mixin.accessors.AccessorGlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextureManager.class)
public abstract class MixinTextureManagerCET {

    @ModifyVariable(method = "bindTexture", at = @At("HEAD"), argsOnly = true)
    private ResourceLocation datarium$cetVariant(ResourceLocation location) {
        if (!CETState.isActive() || AccessorGlStateManager.datarium$getActiveTextureUnit() != 0) return location;
        return CETRender.onBindTexture(location);
    }
}
