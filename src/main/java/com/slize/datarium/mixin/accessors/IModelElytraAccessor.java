package com.slize.datarium.mixin.accessors;

import net.minecraft.client.model.ModelElytra;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Th3_Sl1ze: I still haven't made the decision whether I want to transfer it all to at or mass-use accessors...
@Mixin(ModelElytra.class)
public interface IModelElytraAccessor {
    @Accessor("rightWing")
    ModelRenderer datarium$getRightWing();

    @Accessor("leftWing")
    ModelRenderer datarium$getLeftWing();
}
