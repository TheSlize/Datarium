package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMAttachments;
import net.minecraft.client.renderer.entity.layers.LayerHeldBlock;
import net.minecraft.entity.monster.EntityEnderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerHeldBlock.class)
public abstract class MixinLayerHeldBlockCEM {

    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/monster/EntityEnderman;FFFFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V", ordinal = 0))
    private void datarium$attachmentBlock(EntityEnderman enderman, float limbSwing, float limbSwingAmount, float partialTicks,
                                          float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        CEMAttachments.apply(CEMAttachments.ENDERMAN);
    }
}
