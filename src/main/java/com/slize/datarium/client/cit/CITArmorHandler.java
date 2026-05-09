package com.slize.datarium.client.cit;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class CITArmorHandler {

    @Nullable
    public static ResourceLocation getArmorTexture(EntityLivingBase entity, ItemStack stack, EntityEquipmentSlot slot, String type) {
        if (stack.isEmpty()) return null;

        List<CITEntry> matches = CITManager.getMatchesOfType(stack, CITEntry.CITType.ARMOR);
        if (matches.isEmpty()) return null;

        CITEntry entry = matches.get(0);

        // Determine which layer we need
        boolean isLayer2 = (slot == EntityEquipmentSlot.LEGS);
        String layerSuffix = isLayer2 ? "_layer_2" : "_layer_1";
        // If overlay type (e.g. "overlay"), append it
        String typeStr = (type != null && !type.isEmpty()) ? "_" + type : "";

        Map<String, ResourceLocation> subTextures = entry.getSubTextures();
        if (!subTextures.isEmpty()) {
            // 1. Try to find sub-texture key that ends with layerSuffix (+ optional type)
            //    e.g. "diamond_layer_1", "netherite_layer_1", "layer_1"
            String fullSuffix = layerSuffix + typeStr;
            for (Map.Entry<String, ResourceLocation> e : subTextures.entrySet()) {
                if (e.getKey().endsWith(fullSuffix)) {
                    return e.getValue();
                }
            }
            // 2. Try without type suffix
            if (!typeStr.isEmpty()) {
                for (Map.Entry<String, ResourceLocation> e : subTextures.entrySet()) {
                    if (e.getKey().endsWith(layerSuffix)) {
                        return e.getValue();
                    }
                }
            }
            // 3. Try numeric keys "1" / "2"
            String numKey = isLayer2 ? "2" : "1";
            if (subTextures.containsKey(numKey)) return subTextures.get(numKey);

            // 4. Fallback: first sub-texture
            return subTextures.values().iterator().next();
        }

        // No sub-textures — use top-level texture
        ResourceLocation topTex = entry.getTexture();
        if (topTex != null) return topTex;

        // Build vanilla-style path from top-level texture name if possible
        return null;
    }

    @Nullable
    public static ResourceLocation getElytraTexture(ItemStack stack) {
        if (stack.isEmpty()) return null;
        List<CITEntry> matches = CITManager.getMatchesOfType(stack, CITEntry.CITType.ELYTRA);
        if (matches.isEmpty()) return null;
        CITEntry entry = matches.get(0);
        Map<String, ResourceLocation> sub = entry.getSubTextures();
        if (!sub.isEmpty()) return sub.values().iterator().next();
        return entry.getTexture();
    }
}