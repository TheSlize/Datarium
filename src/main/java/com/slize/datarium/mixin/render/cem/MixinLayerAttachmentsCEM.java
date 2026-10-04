package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMAttachments;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import net.minecraft.client.renderer.entity.layers.LayerHeldItemWitch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({LayerCustomHead.class, LayerHeldItemWitch.class})
public abstract class MixinLayerAttachmentsCEM {

    @Redirect(method = "doRenderLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelRenderer;postRender(F)V"))
    private void datarium$attachmentPostRender(ModelRenderer part, float scale) {
        String type = (Object) this instanceof LayerCustomHead ? CEMAttachments.HEAD : CEMAttachments.WITCH;
        if (!CEMAttachments.apply(type)) part.postRender(scale);
    }
}
