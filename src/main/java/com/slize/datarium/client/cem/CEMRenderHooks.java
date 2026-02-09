package com.slize.datarium.client.cem;

import com.slize.datarium.client.cem.expr.CEMRenderContext;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;
import java.util.Map;

public class CEMRenderHooks {
    private static final ThreadLocal<CEMModelWrapper> activeWrapper = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, CEMPartTransform>> activeTransforms = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, ModelRenderer>> activePartMap = new ThreadLocal<>();
    private static final ThreadLocal<Map<ModelRenderer, CEMModelRenderer>> activeReplacements = new ThreadLocal<>();
    private static final ThreadLocal<EntityLivingBase> activeEntity = new ThreadLocal<>();
    private static final ThreadLocal<Float> activePartialTicks = new ThreadLocal<>();
    private static final ThreadLocal<String> activeModelName = new ThreadLocal<>();
    private static final ThreadLocal<CEMRenderContext> activeContext = new ThreadLocal<>();
    private static final ThreadLocal<CEMRenderState> activeState = new ThreadLocal<>();

    public static void setActiveWrapper(@Nullable CEMModelWrapper wrapper) {
        activeWrapper.set(wrapper);
        if (wrapper != null) {
            CEMDebugSystem.updateAvailableParts(wrapper.getAllParts().keySet());
        }
    }

    @Nullable
    public static CEMModelWrapper getActiveWrapper() {
        return activeWrapper.get();
    }

    public static void clearAll() {
        activeWrapper.remove();
        activeTransforms.remove();
        activePartMap.remove();
        activeReplacements.remove();
        activeEntity.remove();
        activePartialTicks.remove();
        activeModelName.remove();
        activeContext.remove();
        activeState.remove();
    }

    public static void setActivePartMap(Map<String, ModelRenderer> partMap) {
        activePartMap.set(partMap);
    }

    @Nullable
    public static Map<String, ModelRenderer> getActivePartMap() {
        return activePartMap.get();
    }

    public static void setActiveReplacements(Map<ModelRenderer, CEMModelRenderer> replacements) {
        activeReplacements.set(replacements);
    }

    @Nullable
    public static CEMModelRenderer getReplacement(ModelRenderer vanillaPart) {
        Map<ModelRenderer, CEMModelRenderer> map = activeReplacements.get();
        return map != null ? map.get(vanillaPart) : null;
    }

    public static void setActiveEntity(EntityLivingBase entity) {
        activeEntity.set(entity);
    }

    public static void setActivePartialTicks(float partialTicks) {
        activePartialTicks.set(partialTicks);
    }

    public static void setActiveModelName(String name) {
        activeModelName.set(name);
    }

    @Nullable
    public static String getActiveModelName() {
        return activeModelName.get();
    }

    public static void setActiveContext(CEMRenderContext context) {
        activeContext.set(context);
    }

    public static void setActiveState(CEMRenderState state) {
        activeState.set(state);
    }

    @Nullable
    public static CEMRenderState getActiveState() {
        return activeState.get();
    }
}