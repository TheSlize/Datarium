package com.slize.datarium.mixin.mod.hbm;

import com.hbm.render.loader.ModelRendererObj;
import com.slize.datarium.client.cem.CEMFirstPerson;
import com.slize.datarium.client.cem.CEMModelRenderer;
import com.slize.datarium.client.cem.CEMRenderHooks;
import com.slize.datarium.client.cem.CEMRenderState;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(ModelRendererObj.class)
public class MixinModelRendererObj {

    @Unique private boolean datarium$armMirrored = false;
    @Unique private float datarium$rpx, datarium$rpy, datarium$rpz;
    @Unique private float datarium$rax, datarium$ray, datarium$raz;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void datarium$onRender(float scale, CallbackInfo ci) {
        CEMModelRenderer arm = CEMFirstPerson.armSource(this);
        if (arm == null) return;
        ModelRendererObj self = (ModelRendererObj) (Object) this;
        datarium$rpx = self.rotationPointX; datarium$rpy = self.rotationPointY; datarium$rpz = self.rotationPointZ;
        datarium$rax = self.rotateAngleX;   datarium$ray = self.rotateAngleY;   datarium$raz = self.rotateAngleZ;
        if (arm.hasAnimatedTranslate()) {
            self.rotationPointX = arm.effectivePivotX();
            self.rotationPointY = arm.effectivePivotY();
            self.rotationPointZ = arm.effectivePivotZ();
        }
        if (arm.hasAnimatedRotate()) {
            self.rotateAngleX = arm.effectiveRotateX();
            self.rotateAngleY = arm.effectiveRotateY();
            self.rotateAngleZ = arm.effectiveRotateZ();
        }
        datarium$armMirrored = true;
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void datarium$onRenderReturn(float scale, CallbackInfo ci) {
        if (!datarium$armMirrored) return;
        datarium$armMirrored = false;
        ModelRendererObj self = (ModelRendererObj) (Object) this;
        self.rotationPointX = datarium$rpx; self.rotationPointY = datarium$rpy; self.rotationPointZ = datarium$rpz;
        self.rotateAngleX = datarium$rax;   self.rotateAngleY = datarium$ray;   self.rotateAngleZ = datarium$raz;
    }

    @Inject(method = "copyFrom", at = @At("HEAD"), cancellable = true, remap = false)
    private void datarium$onCopyFrom(ModelRenderer source, CallbackInfo ci) {
        CEMModelRenderer anim = CEMRenderHooks.getReplacement(source);
        if (anim == null) anim = CEMRenderHooks.getSecondaryReplacement(source);
        if (anim == null) anim = CEMRenderHooks.getMirrorSource(source);

        CEMRenderState state = CEMRenderHooks.getActiveState();
        boolean isSneaking = state != null && state.context != null &&
                state.context.getEntity() instanceof EntityPlayer &&
                state.context.getEntity().isSneaking();

        ModelRendererObj self = (ModelRendererObj) (Object) this;

        if (anim != null) {
            if (anim.hasAnimatedRotate()) {
                self.rotateAngleX = anim.effectiveRotateX();
                self.rotateAngleY = anim.effectiveRotateY();
                self.rotateAngleZ = anim.effectiveRotateZ();
            } else {
                self.rotateAngleX = source.rotateAngleX;
                self.rotateAngleY = source.rotateAngleY;
                self.rotateAngleZ = source.rotateAngleZ;
            }

            if (anim.hasAnimatedTranslate()) {
                self.rotationPointX = anim.effectivePivotX();
                self.rotationPointY = anim.effectivePivotY() - (isSneaking ? 3.2F : 0.0F);
                self.rotationPointZ = anim.effectivePivotZ();
            } else {
                self.rotationPointX = source.rotationPointX;
                self.rotationPointY = source.rotationPointY - (isSneaking ? 3.2F : 0.0F);
                self.rotationPointZ = source.rotationPointZ;
            }

            self.partOffsetX = source.offsetX;
            self.partOffsetY = source.offsetY;
            self.partOffsetZ = source.offsetZ;

            ci.cancel();
        } else if (isSneaking) {

            self.rotateAngleX = source.rotateAngleX;
            self.rotateAngleY = source.rotateAngleY;
            self.rotateAngleZ = source.rotateAngleZ;

            self.rotationPointX = source.rotationPointX;
            self.rotationPointY = source.rotationPointY - 3.2F;
            self.rotationPointZ = source.rotationPointZ;

            self.partOffsetX = source.offsetX;
            self.partOffsetY = source.offsetY;
            self.partOffsetZ = source.offsetZ;

            ci.cancel();
        }
    }
}