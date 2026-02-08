package com.slize.datarium.client.model.nodes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public record ModelLeafNode(ResourceLocation modelLocation) implements ModernModelNode {
    @Override
    public Object resolve(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        return modelLocation;
    }
}
