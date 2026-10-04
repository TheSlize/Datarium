package com.slize.datarium.mixin.render.model;

import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.client.cem.CEMRenderHooks;
import net.minecraft.client.model.ModelHorse;
import net.minecraft.entity.passive.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelHorse.class)
public class MixinModelHorseBaby {

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/passive/AbstractHorse;getHorseSize()F"))
    private float datarium$neutraliseBabyScale(AbstractHorse horse) {
        String active = CEMRenderHooks.getActiveModelName();
        return active != null && CEMManager.getBabyTransform(active) != null ? 1.0F : horse.getHorseSize();
    }
}
