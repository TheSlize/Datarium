package com.slize.datarium.mixin.render.cet;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slize.datarium.client.cet.CETRender;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ModelRenderer.class, priority = 2000)
public abstract class MixinModelRendererCET {

    @WrapMethod(method = "render")
    private void datarium$cetOverlays(float scale, Operation<Void> original) {
        if (!CETRender.isTracking()) {
            original.call(scale);
            return;
        }
        CETRender.renderTopLevel(() -> original.call(scale), (ModelRenderer) (Object) this, scale);
    }

    @WrapMethod(method = "renderWithRotation")
    private void datarium$cetOverlaysRotated(float scale, Operation<Void> original) {
        if (!CETRender.isTracking()) {
            original.call(scale);
            return;
        }
        CETRender.renderTopLevel(() -> original.call(scale), (ModelRenderer) (Object) this, scale);
    }
}
