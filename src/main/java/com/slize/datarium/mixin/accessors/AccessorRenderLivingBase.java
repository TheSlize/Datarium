package com.slize.datarium.mixin.accessors;

import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderLivingBase.class)
public interface AccessorRenderLivingBase {
    @Invoker("addLayer")
    boolean datarium$addLayer(LayerRenderer<?> layer);
}
