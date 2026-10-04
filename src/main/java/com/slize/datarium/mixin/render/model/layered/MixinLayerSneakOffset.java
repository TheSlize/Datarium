package com.slize.datarium.mixin.render.model.layered;

import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.client.cem.CEMRenderHooks;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({LayerHeldItem.class, LayerCustomHead.class})
public class MixinLayerSneakOffset {

    @Redirect(method = {"renderHeldItem", "doRenderLayer"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"))
    private void datarium$skipSneakOffset(float x, float y, float z) {
        String model = CEMRenderHooks.getActiveModelName();
        if (y == 0.2F && x == 0.0F && z == 0.0F && model != null && CEMManager.isPlayerModel(model)) return;
        GlStateManager.translate(x, y, z);
    }
}
