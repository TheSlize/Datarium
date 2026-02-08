package com.slize.datarium.client.model.nodes;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
public record RangeDispatchNode(String property, List<Entry> entries, @Nullable ModernModelNode fallback) implements ModernModelNode {
    public record Entry(float threshold, ModernModelNode model) {}

    @Override
    public Object resolve(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        float value = 0;

        if ("minecraft:use_duration".equals(property)) {
            if (entity != null && entity.isHandActive() && entity.getActiveItemStack() == stack) {
                value = (float) (stack.getMaxItemUseDuration() - entity.getItemInUseCount());
            }
        }

        ModernModelNode selected = fallback;

        for (Entry entry : entries) {
            if (value >= entry.threshold()) {
                selected = entry.model();
            }
        }

        return selected != null ? selected.resolve(stack, world, entity) : null;
    }
}
