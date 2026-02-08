package com.slize.datarium.client.model.nodes;

import com.slize.datarium.util.DatariumContext;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SelectNode(String property, String componentKey, List<Case> cases, @Nullable ModernModelNode fallback) implements ModernModelNode {
    public record Case(List<String> values, List<EnchantmentCondition> enchantmentConditions, ModernModelNode model) {}
    public record EnchantmentCondition(String enchantmentId, int level) {}

    @Override
    public Object resolve(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        if ("minecraft:display_context".equals(property)) {
            ItemCameraTransforms.TransformType type = DatariumContext.CURRENT_TRANSFORM.get();
            String query = type == null ? "none" : switch (type) {
                case THIRD_PERSON_LEFT_HAND -> "thirdperson_lefthand";
                case THIRD_PERSON_RIGHT_HAND -> "thirdperson_righthand";
                case FIRST_PERSON_LEFT_HAND -> "firstperson_lefthand";
                case FIRST_PERSON_RIGHT_HAND -> "firstperson_righthand";
                case HEAD -> "head";
                case GUI -> "gui";
                case GROUND -> "ground";
                case FIXED -> "fixed";
                default -> "none";
            };

            for (Case c : cases) {
                if (c.values() != null) {
                    for (String val : c.values()) {
                        if (val.equals(query)) {
                            return c.model().resolve(stack, world, entity);
                        }
                    }
                }
            }
        } else if ("minecraft:component".equals(property)) {
            if ("minecraft:custom_name".equals(componentKey)) {
                String query = stack.getDisplayName();
                for (Case c : cases) {
                    if (c.values() != null) {
                        for (String val : c.values()) {
                            if (val.equals(query)) {
                                return c.model().resolve(stack, world, entity);
                            }
                        }
                    }
                }
            } else if ("minecraft:stored_enchantments".equals(componentKey)) {
                Map<ResourceLocation, Integer> enchants = getStoredEnchantments(stack);

                for (Case c : cases) {
                    if (c.enchantmentConditions() != null && !c.enchantmentConditions().isEmpty()) {
                        for (EnchantmentCondition cond : c.enchantmentConditions()) {
                            ResourceLocation enchLoc = new ResourceLocation(cond.enchantmentId());
                            Integer level = enchants.get(enchLoc);
                            if (level != null && level == cond.level()) {
                                return c.model().resolve(stack, world, entity);
                            }
                        }
                    }
                }
            }
        }

        return fallback != null ? fallback.resolve(stack, world, entity) : null;
    }

    private static Map<ResourceLocation, Integer> getStoredEnchantments(ItemStack stack) {
        Map<ResourceLocation, Integer> result = new HashMap<>();

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
                int lvl = enchTag.getShort("lvl");

                Enchantment ench = Enchantment.getEnchantmentByID(id);
                if (ench != null) {
                    ResourceLocation regName = ench.getRegistryName();
                    if (regName != null) {
                        result.put(regName, lvl);
                    }
                }
            }
        }

        return result;
    }
}