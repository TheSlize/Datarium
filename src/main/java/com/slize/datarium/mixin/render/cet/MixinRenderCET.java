package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETState;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Render.class)
public abstract class MixinRenderCET<T extends Entity> {

    @Inject(method = "renderLivingLabel", at = @At("HEAD"))
    private void datarium$protectLabel(T entity, String label, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        CETState.pushModify(false);
    }

    @Inject(method = "renderLivingLabel", at = @At("RETURN"))
    private void datarium$unprotectLabel(T entity, String label, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        CETState.popModify();
    }
}
