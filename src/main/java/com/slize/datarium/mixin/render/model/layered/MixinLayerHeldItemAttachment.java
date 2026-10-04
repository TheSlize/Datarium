package com.slize.datarium.mixin.render.model.layered;

import com.slize.datarium.client.cem.CEMAttachments;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.util.EnumHandSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerHeldItem.class)
public class MixinLayerHeldItemAttachment {

    @Inject(method = "translateToHand", at = @At("HEAD"), cancellable = true)
    private void datarium$attachmentHand(EnumHandSide side, CallbackInfo ci) {
        if (CEMAttachments.apply(side == EnumHandSide.LEFT ? CEMAttachments.LEFT_HAND : CEMAttachments.RIGHT_HAND)) {
            ci.cancel();
        }
    }
}
