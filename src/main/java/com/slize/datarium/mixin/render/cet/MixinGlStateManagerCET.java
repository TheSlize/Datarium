package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETState;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GlStateManager.class)
public abstract class MixinGlStateManagerCET {

    @Shadow private static int activeTextureUnit;

    @ModifyVariable(method = "bindTexture", at = @At("HEAD"), argsOnly = true)
    private static int datarium$trackBoundTexture(int texture) {
        if (activeTextureUnit == 0 && CETState.isActive()) CETState.onGlBind(texture);
        return texture;
    }
}
