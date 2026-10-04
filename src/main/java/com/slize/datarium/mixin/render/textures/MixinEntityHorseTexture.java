package com.slize.datarium.mixin.render.textures;

import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.util.PackConverter;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityHorse.class)
public class MixinEntityHorseTexture {

    @Inject(method = "getVariantTexturePaths", at = @At("RETURN"), cancellable = true)
    private void datarium$modernLayers(CallbackInfoReturnable<String[]> cir) {
        if (CEMManager.getModel("horse") == null) return;

        String[] layers = cir.getReturnValue();
        if (layers == null) return;

        String[] out = new String[layers.length];
        boolean changed = false;
        for (int i = 0; i < layers.length; i++) {
            out[i] = layers[i];
            if (layers[i] == null || layers[i].isEmpty() || layers[i].indexOf(':') >= 0) continue;
            ResourceLocation layer = new ResourceLocation(layers[i]);
            ResourceLocation modern = PackConverter.modernLayout(layer);
            if (!modern.equals(layer)) {
                out[i] = modern.toString();
                changed = true;
            }
        }
        if (changed) cir.setReturnValue(out);
    }
}
