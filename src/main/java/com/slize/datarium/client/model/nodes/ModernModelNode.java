package com.slize.datarium.client.model.nodes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public interface ModernModelNode {
    /**
     * Resolves the logic tree.
     * @return A ResourceLocation (single model) or a List&lt;ResourceLocation&gt; (composite model), or null.
     */
    @Nullable
    Object resolve(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity);
}
