package com.slize.datarium.mixin.accessors;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelRenderer.class)
public interface IModelRendererAccessor {
    @Accessor("baseModel")
    ModelBase datarium$getBaseModel();
}