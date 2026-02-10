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
        // Check if this renderer is replaced by a CEM part
        CEMModelRenderer replacement = CEMRenderHooks.getReplacement((ModelRenderer)(Object)this);

        if (replacement != null) {
            // Check visibility (Vanilla hides it? We hide it unless CEM forces?)
            if (!this.showModel) {
                // If vanilla hides, usually means we shouldn't render, but sometimes CEM animates visibility
                // For now, let's respect vanilla showModel only if replacement doesn't have an override
            }

            if (replacement.isAttached()) {
                // Attached: Vanilla renders first, then we render attached part
                // We let the method continue, but we need to render replacement in "RETURN"
            } else {
                // Replaced: We render INSTEAD
                // Pass the VANILLA rotation points so CEM can calculate offsets correctly
                replacement.renderWithVanilla(scale);
                ci.cancel(); // Skip vanilla rendering
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