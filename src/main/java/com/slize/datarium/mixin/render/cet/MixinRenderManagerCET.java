package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETRender;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderManager.class)
public abstract class MixinRenderManagerCET {

    @Inject(method = "renderEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private void datarium$beginCet(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideDebug, CallbackInfo ci) {
        CETRender.beginEntity(entity);
    }

    @Inject(method = "renderEntity", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private void datarium$endCet(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideDebug, CallbackInfo ci) {
        CETRender.end();
    }
}
