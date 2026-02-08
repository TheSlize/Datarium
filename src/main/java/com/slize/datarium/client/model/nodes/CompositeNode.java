package com.slize.datarium.client.model.nodes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public record CompositeNode(List<ModernModelNode> models) implements ModernModelNode {
    @Override
    public Object resolve(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        List<ResourceLocation> result = new ArrayList<>();
        for (ModernModelNode node : models) {
            Object res = node.resolve(stack, world, entity);
            if (res instanceof ResourceLocation loc) {
                result.add(loc);
            } else if (res instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof ResourceLocation loc) result.add(loc);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }
}