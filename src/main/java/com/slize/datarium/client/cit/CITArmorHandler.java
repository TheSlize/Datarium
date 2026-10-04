package com.slize.datarium.client.cit;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

public class CITArmorHandler {

    @Nullable
    public static ResourceLocation getArmorTexture(ItemStack stack, EntityEquipmentSlot slot, @Nullable String type) {
        if (stack.isEmpty()) return null;
        CITEntry entry = CITManager.getMatch(stack, CITEntry.CITType.ARMOR);
        if (entry == null) return null;

        Map<String, ResourceLocation> subTextures = entry.subTextures();
        if (!subTextures.isEmpty()) {
            int layer = slot == EntityEquipmentSlot.LEGS ? 2 : 1;
            boolean overlay = type != null && !type.isEmpty();
            String suffix = "_layer_" + layer + (overlay ? "_" + type : "");

            if (stack.getItem() instanceof ItemArmor armor) {
                String material = armor.getArmorMaterial().getName();
                int colon = material.indexOf(':');
                if (colon >= 0) material = material.substring(colon + 1);
                ResourceLocation exact = subTextures.get(material + suffix);
                if (exact != null) return exact;
            }
            for (Map.Entry<String, ResourceLocation> e : subTextures.entrySet()) {
                if (e.getKey().endsWith(suffix)) return e.getValue();
            }
            if (!overlay) {
                ResourceLocation numbered = subTextures.get(Integer.toString(layer));
                if (numbered != null) return numbered;
            }
        }

        return entry.texture();
    }

    @Nullable
    public static ResourceLocation getElytraTexture(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CITEntry entry = CITManager.getMatch(stack, CITEntry.CITType.ELYTRA);
        if (entry == null) return null;
        if (entry.texture() != null) return entry.texture();
        Map<String, ResourceLocation> sub = entry.subTextures();
        return sub.isEmpty() ? null : sub.values().iterator().next();
    }
}
