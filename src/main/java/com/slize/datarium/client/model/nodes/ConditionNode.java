package com.slize.datarium.client.model.nodes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public record ConditionNode(String property, ModernModelNode onTrue, ModernModelNode onFalse) implements ModernModelNode {
    @Override
    public Object resolve(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        boolean result = false;
        if ("minecraft:using_item".equals(property)) {
            if (entity != null && entity.isHandActive()) {
                result = entity.getActiveItemStack().isItemEqual(stack);
            }
        }
        return result ? onTrue.resolve(stack, world, entity) : onFalse.resolve(stack, world, entity);
    }
}