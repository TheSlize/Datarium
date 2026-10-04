package com.slize.datarium.mixin.render.textures;

import com.slize.datarium.util.PackConverter;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Render.class)
public class MixinRenderTexture {

    @ModifyVariable(method = "bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
            at = @At("HEAD"), argsOnly = true, index = 1)
    private ResourceLocation datarium$redirect(ResourceLocation location) {
        return PackConverter.entityTexture(location);
    }
}