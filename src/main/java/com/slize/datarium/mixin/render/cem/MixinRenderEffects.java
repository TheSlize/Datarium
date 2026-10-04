package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMRenderEffects;
import com.slize.datarium.client.cem.expr.CEMRenderContext;
import com.slize.datarium.client.cem.expr.CEMRenderVar;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Render.class)
public abstract class MixinRenderEffects {

    @Shadow protected float shadowSize;
    @Shadow protected float shadowOpaque;

    @Unique private boolean datarium$shadowChanged;
    @Unique private float datarium$savedShadowSize;
    @Unique private float datarium$savedShadowOpaque;
    @Unique private double datarium$shadowOffsetX;
    @Unique private double datarium$shadowOffsetZ;

    @Inject(method = "doRenderShadowAndFire", at = @At("HEAD"))
    private void datarium$applyShadow(Entity entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        datarium$shadowChanged = false;
        datarium$shadowOffsetX = 0;
        datarium$shadowOffsetZ = 0;
        CEMRenderContext ctx = CEMRenderEffects.contextFor(entity);
        if (ctx == null) return;

        double size = ctx.getRenderValue(CEMRenderVar.SHADOW_SIZE);
        double opacity = ctx.getRenderValue(CEMRenderVar.SHADOW_OPACITY);
        double offsetX = ctx.getRenderValue(CEMRenderVar.SHADOW_OFFSET_X);
        double offsetZ = ctx.getRenderValue(CEMRenderVar.SHADOW_OFFSET_Z);
        if (!Double.isNaN(offsetX)) datarium$shadowOffsetX = offsetX;
        if (!Double.isNaN(offsetZ)) datarium$shadowOffsetZ = offsetZ;
        if (Double.isNaN(size) && Double.isNaN(opacity)) return;

        datarium$shadowChanged = true;
        datarium$savedShadowSize = shadowSize;
        datarium$savedShadowOpaque = shadowOpaque;
        if (!Double.isNaN(size)) shadowSize = (float) size;
        if (!Double.isNaN(opacity)) shadowOpaque = (float) opacity;
    }

    @Inject(method = "doRenderShadowAndFire", at = @At("RETURN"))
    private void datarium$restoreShadow(Entity entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        if (!datarium$shadowChanged) return;
        shadowSize = datarium$savedShadowSize;
        shadowOpaque = datarium$savedShadowOpaque;
        datarium$shadowChanged = false;
    }

    @ModifyArg(method = "doRenderShadowAndFire", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;renderShadow(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private double datarium$shadowX(double x) {
        return x + datarium$shadowOffsetX;
    }

    @ModifyArg(method = "doRenderShadowAndFire", index = 3, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;renderShadow(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private double datarium$shadowZ(double z) {
        return z + datarium$shadowOffsetZ;
    }

    @Redirect(method = "renderEntityOnFire", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/Entity;width:F", opcode = Opcodes.GETFIELD))
    private float datarium$fireWidth(Entity entity) {
        CEMRenderContext ctx = CEMRenderEffects.contextFor(entity);
        if (ctx == null) return entity.width;
        double fx = ctx.getRenderValue(CEMRenderVar.FIRE_X);
        double fy = ctx.getRenderValue(CEMRenderVar.FIRE_Y);
        double fz = ctx.getRenderValue(CEMRenderVar.FIRE_Z);
        if (!Double.isNaN(fx) || !Double.isNaN(fy) || !Double.isNaN(fz)) {
            GlStateManager.translate(Double.isNaN(fx) ? 0 : fx, Double.isNaN(fy) ? 0 : fy, Double.isNaN(fz) ? 0 : fz);
        }
        double scale = ctx.getRenderValue(CEMRenderVar.FIRE_SCALE);
        return Double.isNaN(scale) ? entity.width : (float) scale;
    }

    @Redirect(method = "renderEntityOnFire", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/Entity;height:F", opcode = Opcodes.GETFIELD))
    private float datarium$fireHeight(Entity entity) {
        CEMRenderContext ctx = CEMRenderEffects.contextFor(entity);
        double height = ctx != null ? ctx.getRenderValue(CEMRenderVar.FIRE_HEIGHT) : Double.NaN;
        return Double.isNaN(height) ? entity.height : (float) height;
    }
}
