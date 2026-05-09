package com.slize.datarium.mixin.render.entities;

import com.slize.datarium.util.DatariumContext;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderEntityItem.class)
public class MixinRenderEntityItem {

    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("HEAD"))
    public void onDoRenderHead(EntityItem entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.set(ItemCameraTransforms.TransformType.GROUND);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("RETURN"))
    public void onDoRenderReturn(EntityItem entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.remove();
    }
}