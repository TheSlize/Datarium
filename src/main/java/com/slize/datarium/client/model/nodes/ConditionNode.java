package com.slize.datarium.client.model.nodes;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ConditionNode(String property, @Nullable String predicate, @Nullable String component,
                            @Nullable List<EnchantmentValue> values, ModernModelNode onTrue,
                            ModernModelNode onFalse) implements ModernModelNode {

    public record EnchantmentValue(String enchantmentId) {
    }

    @Override
    public Object resolve(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        boolean result = evaluateCondition(stack, world, entity);
        return result ? onTrue.resolve(stack, world, entity) : onFalse.resolve(stack, world, entity);
    }

    private boolean evaluateCondition(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        String normalizedProperty = normalizeId(property);

        return switch (normalizedProperty) {
            case "using_item" -> {
                if (entity != null && entity.isHandActive()) {
                    yield entity.getActiveItemStack().isItemEqual(stack);
                }
                yield false;
            }
            case "component" -> {
                if (predicate != null) {
                    String normalizedPredicate = normalizeId(predicate);
                    if ("enchantments".equals(normalizedPredicate)) {
                        yield checkEnchantments(stack);
                    }
                }
                yield false;
            }
            case "has_component" -> {
                if (component != null) {
                    String normalizedComponent = normalizeId(component);
                    yield hasComponent(stack, normalizedComponent);
                }
                yield false;
            }
            default -> false;
        };
    }

    private boolean checkEnchantments(ItemStack stack) {
        if (values == null || values.isEmpty()) {
            return false;
        }

        Set<ResourceLocation> itemEnchants = getEnchantmentIds(stack);

        for (EnchantmentValue ev : values) {
            ResourceLocation enchLoc = new ResourceLocation(ev.enchantmentId());
            if (itemEnchants.contains(enchLoc)) {
                return true;
            }
        }
        return false;
    }

    private static Set<ResourceLocation> getEnchantmentIds(ItemStack stack) {
        Set<ResourceLocation> result = new HashSet<>();

        if (stack.isEmpty() || !stack.hasTagCompound()) {
            return result;
        }

        NBTTagCompound tag = stack.getTagCompound();
        String tagKey = stack.getItem() == Items.ENCHANTED_BOOK ? "StoredEnchantments" : "ench";

        if (tag != null && tag.hasKey(tagKey, 9)) {
            NBTTagList enchList = tag.getTagList(tagKey, 10);
            for (int i = 0; i < enchList.tagCount(); i++) {
                NBTTagCompound enchTag = enchList.getCompoundTagAt(i);
                int id = enchTag.getShort("id");

                Enchantment ench = Enchantment.getEnchantmentByID(id);
                if (ench != null) {
                    ResourceLocation regName = ench.getRegistryName();
                    if (regName != null) {
                        result.add(regName);
                    }
                }
            }
        }

        return result;
    }

    private boolean hasComponent(ItemStack stack, String componentType) {
        if (stack.isEmpty() || !stack.hasTagCompound()) {
            return false;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;

        return switch (componentType) {
            case "trim" -> tag.hasKey("Trim", 10);
            default -> tag.hasKey(componentType);
        };
    }

    private static String normalizeId(String id) {
        if (id == null) return null;
        if (id.startsWith("minecraft:")) {
            return id.substring("minecraft:".length());
        }
        return id;
    }
}