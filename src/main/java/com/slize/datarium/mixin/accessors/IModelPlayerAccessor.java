package com.slize.datarium.mixin.accessors;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPlayer.class)
public interface IModelPlayerAccessor {
    @Accessor("bipedCape")
    ModelRenderer datarium$getCape();
}
