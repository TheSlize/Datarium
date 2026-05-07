package com.slize.datarium.client.cit;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
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

        CITEntry entry = matches.getFirst();
        String layerName = (slot == EntityEquipmentSlot.LEGS) ? "armor_layer_2" : "armor_layer_1";
        if (type != null && !type.isEmpty()) layerName = layerName + "_" + type;

        // Check sub-texture with layer name
        Map<String, ResourceLocation> subTextures = entry.getSubTextures();
        if (!subTextures.isEmpty()) {
            // Try "layer_1" / "layer_2" keys matching vanilla armor texture filenames
            for (Map.Entry<String, ResourceLocation> e : subTextures.entrySet()) {
                if (layerName.contains(e.getKey()) || e.getKey().contains(layerName)) {
                    return e.getValue();
                }
            }
            // Fall back to first sub-texture for this layer
            String key = (slot == EntityEquipmentSlot.LEGS) ? "2" : "1";
            if (subTextures.containsKey(key)) return subTextures.get(key);
        }

        // Fallback: top-level texture
        return entry.getTexture();
    }

    @Nullable
    public static ResourceLocation getElytraTexture(ItemStack stack) {
        if (stack.isEmpty()) return null;
        List<CITEntry> matches = CITManager.getMatchesOfType(stack, CITEntry.CITType.ELYTRA);
        if (matches.isEmpty()) return null;
        CITEntry entry = matches.getFirst();
        Map<String, ResourceLocation> sub = entry.getSubTextures();
        if (!sub.isEmpty()) return sub.values().iterator().next();
        return entry.getTexture();
    }
}