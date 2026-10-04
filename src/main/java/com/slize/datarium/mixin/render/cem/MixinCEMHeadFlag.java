package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMGenericRender;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerCustomHead.class)
public abstract class MixinCEMHeadFlag {

    @Inject(method = "doRenderLayer", at = @At("HEAD"))
    private void datarium$enterHead(CallbackInfo ci) {
        CEMGenericRender.pushOnHead();
    }

    @Inject(method = "doRenderLayer", at = @At("RETURN"))
    private void datarium$leaveHead(CallbackInfo ci) {
        CEMGenericRender.popOnHead();
    }
}
