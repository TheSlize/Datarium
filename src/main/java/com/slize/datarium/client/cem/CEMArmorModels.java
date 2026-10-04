package com.slize.datarium.client.cem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EMF armor models: {@code <entity>[_baby]_inner_armor.jem} / {@code <entity>[_baby]_outer_armor.jem},
 * falling back to {@code baby_inner_armor} / {@code inner_armor} (and the outer equivalents).
 */
public final class CEMArmorModels {
    private static final Map<String, String> NAME_FALLBACKS = new HashMap<>();
    private static final Map<String, String> RESOLVED = new ConcurrentHashMap<>();

    @Nullable private static Boolean innerLayer;
    @Nullable private static ModelBase layerModel;

    static {
        NAME_FALLBACKS.put("player_slim", "player");
        NAME_FALLBACKS.put("armor_stand_small", "armor_stand");
    }

    private CEMArmorModels() {}

    public static void invalidate() {
        RESOLVED.clear();
    }

    public static void beginLayer(boolean inner, @Nullable ModelBase vanillaLayerModel) {
        innerLayer = inner;
        layerModel = vanillaLayerModel;
    }

    public static void endLayer() {
        innerLayer = null;
        layerModel = null;
    }

    /** Non-null only while LayerArmorBase renders its own vanilla armor model for a slot. */
    @Nullable
    public static Boolean renderingInnerLayer() {
        return innerLayer;
    }

    public static boolean isVanillaLayerModel(ModelBase model) {
        return innerLayer != null && model == layerModel;
    }

    @Nullable
    public static String modelName(String entityName, boolean inner, boolean baby) {
        String key = entityName + (inner ? "|inner|" : "|outer|") + baby;
        String cached = RESOLVED.get(key);
        if (cached != null) return cached.isEmpty() ? null : cached;

        String layer = inner ? "inner_armor" : "outer_armor";
        String fallback = NAME_FALLBACKS.get(entityName);
        List<String> candidates = new ArrayList<>(6);
        if (baby) {
            candidates.add(entityName + "_baby_" + layer);
            if (fallback != null) candidates.add(fallback + "_baby_" + layer);
            candidates.add("baby_" + layer);
        }
        candidates.add(entityName + "_" + layer);
        if (fallback != null) candidates.add(fallback + "_" + layer);
        candidates.add(layer);

        String found = CEMManager.firstExisting(candidates.toArray(new String[0]));
        RESOLVED.put(key, found != null ? found : "");
        return found;
    }

    /** Armor jem for an entity that has no jem of its own, so no main CEM session is running. */
    public static boolean beginStandalone(EntityLivingBase entity, ModelBase model, boolean inner, float limbSwing,
                                          float limbSwingAmount, float partialTicks, float ageInTicks,
                                          float netHeadYaw, float headPitch) {
        if (CEMApiState.forcesVanilla(entity)) return false;
        String entityName = CEMManager.armorEntityName(entity);
        if (entityName == null) return false;
        String base = modelName(entityName, inner, entity.isChild());
        if (base == null) return false;
        String name = CEMRandomModels.select(base, entity);

        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        double distSq = view != null ? entity.getDistanceSq(view) : 0.0D;
        CEMRenderState state = CEMManager.getArmorState(entity, inner);
        return CEMGenericRender.beginModel(model, model, name, state, distSq, (ctx, frameTime) -> {
            ctx.setup(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTicks, frameTime);
            ctx.setRuleIndex(CEMRandomModels.ruleIndex(entity, name));
        });
    }
}
