package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMGenericRender;
import net.minecraft.client.renderer.entity.RenderItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItemFrame.class)
public abstract class MixinCEMItemFrameFlag {

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void datarium$enterFrame(CallbackInfo ci) {
        CEMGenericRender.pushInItemFrame();
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void datarium$leaveFrame(CallbackInfo ci) {
        CEMGenericRender.popInItemFrame();
    }
}
