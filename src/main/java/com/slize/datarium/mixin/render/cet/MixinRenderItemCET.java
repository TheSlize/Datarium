package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETState;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public abstract class MixinRenderItemCET {

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("HEAD"))
    private void datarium$cetProtectItem(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        if (CETState.isActive()) CETState.pushModify(false);
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("RETURN"))
    private void datarium$cetUnprotectItem(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        if (CETState.isActive()) CETState.popModify();
    }
}
