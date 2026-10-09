package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMTextureMask;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class MixinGlStateManagerCEM {

    @Shadow private static int activeTextureUnit;

    @Inject(method = "bindTexture", at = @At("HEAD"))
    private static void datarium$maskBind(int texture, CallbackInfo ci) {
        if (activeTextureUnit == 0) CEMTextureMask.onBind(texture);
    }

    @Inject(method = "deleteTexture", at = @At("HEAD"))
    private static void datarium$maskDelete(int texture, CallbackInfo ci) {
        CEMTextureMask.onDelete(texture);
    }

    @Inject(method = {"glTexImage2D", "glTexSubImage2D", "glCopyTexSubImage2D"}, at = @At("HEAD"))
    private static void datarium$maskUpload(CallbackInfo ci) {
        if (activeTextureUnit == 0) CEMTextureMask.onUpload();
    }
}
