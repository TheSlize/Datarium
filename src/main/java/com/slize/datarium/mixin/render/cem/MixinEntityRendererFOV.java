package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMThrottle;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class MixinEntityRendererFOV {
    @Inject(method = "getFOVModifier", at = @At("RETURN"))
    private void datarium$onGetFOVModifier(float partialTicks, boolean useFOVSetting, CallbackInfoReturnable<Float> cir) {
        if (useFOVSetting) {
            CEMThrottle.setCurrentFov(cir.getReturnValue());
        }
    }
}