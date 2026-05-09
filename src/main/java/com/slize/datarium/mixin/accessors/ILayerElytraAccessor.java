package com.slize.datarium.mixin.accessors;

import net.minecraft.client.model.ModelElytra;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.entity.layers.LayerElytra;

@Mixin(LayerElytra.class)
public interface ILayerElytraAccessor {

    @Accessor("modelElytra")
    ModelElytra datarium$getModelElytra();
}