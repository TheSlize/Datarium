package com.slize.datarium.mixin.render.model;

import com.slize.datarium.client.cem.CEMManager;
import net.minecraft.client.model.ModelGhast;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelGhast.class)
public class MixinModelGhast {

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"))
    private void datarium$skipGhastOffset(float x, float y, float z) {
        if (CEMManager.getModel("ghast") == null) GlStateManager.translate(x, y, z);
    }
}
