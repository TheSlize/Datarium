package com.slize.datarium.client.cit;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class CITArmorHandler {

    @Nullable
    public static ResourceLocation getArmorTexture(ItemStack stack, EntityEquipmentSlot slot, String type) {
        if (stack.isEmpty()) return null;

        List<CITEntry> matches = CITManager.getMatchesOfType(stack, CITEntry.CITType.ARMOR);
        if (matches.isEmpty()) return null;

        CITEntry entry = matches.getFirst();

        boolean isLayer2 = (slot == EntityEquipmentSlot.LEGS);
        String layerSuffix = isLayer2 ? "_layer_2" : "_layer_1";
        String typeStr = (type != null && !type.isEmpty()) ? "_" + type : "";

        Map<String, ResourceLocation> subTextures = entry.subTextures();
        if (!subTextures.isEmpty()) {
            String fullSuffix = layerSuffix + typeStr;
            for (Map.Entry<String, ResourceLocation> e : subTextures.entrySet()) {
                if (e.getKey().endsWith(fullSuffix)) {
                    return e.getValue();
                }
            }
            if (!typeStr.isEmpty()) {
                for (Map.Entry<String, ResourceLocation> e : subTextures.entrySet()) {
                    if (e.getKey().endsWith(layerSuffix)) {
                        return e.getValue();
                    }
                }
            }
            String numKey = isLayer2 ? "2" : "1";
            if (subTextures.containsKey(numKey)) return subTextures.get(numKey);

            return subTextures.values().iterator().next();
        }

        return entry.texture();
    }

    @Nullable
    public static ResourceLocation getElytraTexture(ItemStack stack) {
        if (stack.isEmpty()) return null;
        List<CITEntry> matches = CITManager.getMatchesOfType(stack, CITEntry.CITType.ELYTRA);
        if (matches.isEmpty()) return null;
        CITEntry entry = matches.getFirst();
        Map<String, ResourceLocation> sub = entry.subTextures();
        if (!sub.isEmpty()) return sub.values().iterator().next();
        return entry.texture();
    }
}