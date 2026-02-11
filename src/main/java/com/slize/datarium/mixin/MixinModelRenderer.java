package com.slize.datarium.mixin;

import com.slize.datarium.client.cem.CEMModelRenderer;
import com.slize.datarium.client.cem.CEMRenderHooks;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelRenderer.class)
public class MixinModelRenderer {
    @Shadow public float rotationPointX;
    @Shadow public float rotationPointY;
    @Shadow public float rotationPointZ;
    @Shadow public boolean showModel;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void datarium$onRender(float scale, CallbackInfo ci) {
        CEMModelRenderer replacement = CEMRenderHooks.getReplacement((ModelRenderer)(Object)this);

        if (replacement != null) {
            if (!this.showModel) {
            }

            if (replacement.isAttached()) {
            } else {
                replacement.renderWithVanilla(scale);
                ci.cancel();
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void datarium$onRenderReturn(float scale, CallbackInfo ci) {
        CEMModelRenderer replacement = CEMRenderHooks.getReplacement((ModelRenderer)(Object)this);
        if (replacement != null && replacement.isAttached()) {
            replacement.renderWithVanilla(scale);
        }
    }
}