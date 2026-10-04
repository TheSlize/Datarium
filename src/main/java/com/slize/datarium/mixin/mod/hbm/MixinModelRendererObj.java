package com.slize.datarium.mixin.mod.hbm;

import com.hbm.render.loader.ModelRendererObj;
import com.slize.datarium.client.cem.CEMModelRenderer;
import com.slize.datarium.client.cem.CEMRenderHooks;
import com.slize.datarium.client.cem.CEMRenderState;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(ModelRendererObj.class)
public class MixinModelRendererObj {

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