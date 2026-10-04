package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMRenderEffects;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLiving.class)
public abstract class MixinRenderLivingLeash {

    @Inject(method = "renderLeash", at = @At("HEAD"), cancellable = true)
    private void datarium$leashOffsets(EntityLiving entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        Entity holder = entity.getLeashHolder();
        if (holder == null) return;
        double[] own = CEMRenderEffects.leashOffset(entity, false);
        double[] held = CEMRenderEffects.leashOffset(holder, true);
        if (own == null && held == null) return;
        CEMRenderEffects.renderLeash(entity, holder, x, y, z, partialTicks, own, held);
        ci.cancel();
    }
}
