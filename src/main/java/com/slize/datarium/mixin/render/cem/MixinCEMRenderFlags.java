package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMGenericRender;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class MixinCEMRenderFlags {

    @Inject(method = "renderItemSide", at = @At("HEAD"))
    private void datarium$enterHand(EntityLivingBase holder, ItemStack stack, ItemCameraTransforms.TransformType transform,
                                    boolean leftHanded, CallbackInfo ci) {
        CEMGenericRender.pushInHand(holder);
    }

    @Inject(method = "renderItemSide", at = @At("RETURN"))
    private void datarium$leaveHand(EntityLivingBase holder, ItemStack stack, ItemCameraTransforms.TransformType transform,
                                    boolean leftHanded, CallbackInfo ci) {
        CEMGenericRender.popInHand();
    }
}
