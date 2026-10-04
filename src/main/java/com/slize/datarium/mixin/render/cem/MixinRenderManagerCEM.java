package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMGenericRender;
import com.slize.datarium.client.cem.CEMManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(RenderManager.class)
public abstract class MixinRenderManagerCEM {

    @Unique private final Deque<Boolean> datarium$sessions = new ArrayDeque<>();

    @Inject(method = "renderEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private void datarium$beginCem(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideDebug, CallbackInfo ci) {
        boolean started = false;
        if (!(entity instanceof EntityLivingBase)) {
            String modelName = CEMManager.getModelNameForEntity(entity);
            if (modelName != null) {
                Render<Entity> render = ((RenderManager) (Object) this).getEntityRenderObject(entity);
                if (render != null) {
                    float limbSwing = 0;
                    float limbSpeed = 0;
                    if (entity instanceof EntityMinecart) {
                        limbSpeed = 1;
                        limbSwing = (float) -(entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks
                                + entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks);
                    } else if (entity instanceof EntityBoat boat) {
                        limbSpeed = 1;
                        limbSwing = Math.max(boat.getRowingTime(0, partialTicks), boat.getRowingTime(1, partialTicks));
                    }
                    started = CEMGenericRender.beginEntity(entity, render, modelName,
                            x * x + y * y + z * z, partialTicks, limbSwing, limbSpeed);
                }
            }
        }
        datarium$sessions.push(started);
    }

    @Inject(method = "renderEntity", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private void datarium$endCem(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideDebug, CallbackInfo ci) {
        Boolean started = datarium$sessions.poll();
        if (started != null && started) CEMGenericRender.end();
    }
}
