package com.slize.datarium.mixin.render.model;

import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.client.cem.CEMRenderHooks;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({ModelBiped.class, ModelPlayer.class})
public class MixinModelBipedSneak {

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"))
    private void datarium$skipSneakOffset(float x, float y, float z) {
        String model = CEMRenderHooks.getActiveModelName();
        if (y == 0.2F && x == 0.0F && z == 0.0F && model != null && CEMManager.isPlayerModel(model)) return;
        GlStateManager.translate(x, y, z);
    }
}
