package com.slize.datarium.mixin;

import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ModelBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ModelBlock.class)
public interface IModelBlockAccessor {
    @Accessor("overrides")
    List<ItemOverride> getOverrides();

    @Accessor("overrides")
    void setOverrides(List<ItemOverride> overrides);
}
