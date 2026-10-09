package com.slize.datarium.client.cem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.util.EnumHandSide;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class CEMFirstPerson {

    private static EnumHandSide activeHand;
    private static long lastFrame = -1;
    private static int lastEntity = Integer.MIN_VALUE;

    private static Map<String, ModelRenderer> cachedPartMap;
    private static String cachedModelName;
    private static Class<?> cachedModelClass;

    private static Object armTarget;
    private static CEMModelRenderer armSource;

    public static boolean isActive() { return activeHand != null; }

    public static void bindArm(Object part) {
        CEMModelWrapper wrapper = CEMRenderHooks.getActiveWrapper();
        if (activeHand == null || wrapper == null) return;
        armSource = wrapper.getPartRenderer(activeHand == EnumHandSide.RIGHT ? "right_arm" : "left_arm");
        armTarget = armSource != null ? part : null;
    }

    @Nullable
    public static CEMModelRenderer armSource(Object part) {
        return armTarget != null && part == armTarget ? armSource : null;
    }

    public static void begin(AbstractClientPlayer player, ModelBase mainModel, EnumHandSide side) {
        String modelName = CEMManager.getModelNameForEntity(player);
        if (modelName == null) return;
        CEMModelWrapper wrapper = CEMManager.getWrapper(mainModel, modelName);
        if (wrapper == null) return;

        activeHand = side;

        boolean recompute = CEMManager.getFrameCounter() != lastFrame || player.getEntityId() != lastEntity;
        lastFrame = CEMManager.getFrameCounter();
        lastEntity = player.getEntityId();

        float partialTicks = Minecraft.getMinecraft().getRenderPartialTicks();
        // mapParts() is reflective, cache it like MixinRenderLivingBase does.
        if (cachedPartMap == null || cachedModelClass != mainModel.getClass() || !modelName.equals(cachedModelName)) {
            cachedPartMap = CEMPartMapping.mapParts(mainModel, modelName);
            cachedModelClass = mainModel.getClass();
            cachedModelName = modelName;
        }
        Map<String, ModelRenderer> partMap = cachedPartMap;
        CEMRenderState state = CEMManager.getFirstPersonState(player);

        CEMRenderHooks.setActiveModelName(modelName);
        CEMRenderHooks.setActiveEntity(player);
        CEMRenderHooks.setActivePartialTicks(partialTicks);
        CEMRenderHooks.setActivePartMap(partMap);
        CEMRenderHooks.setActiveMainModel(mainModel);
        CEMRenderHooks.setActiveWrapper(wrapper);
        CEMRenderHooks.setActiveContext(state.context);
        CEMRenderHooks.setActiveState(state);

        wrapper.detachVanillaParts();
        Map<ModelRenderer, CEMModelRenderer> replacements = new HashMap<>();
        for (Map.Entry<String, ModelRenderer> e : partMap.entrySet()) {
            CEMModelRenderer r = wrapper.getPartRenderer(e.getKey());
            if (r == null) continue;
            if (r.getCemPart().parent != null && !r.isAttached()) continue;
            r.setVanillaPart(e.getValue());
            replacements.put(e.getValue(), r);
        }
        CEMRenderHooks.setActiveReplacements(replacements);

        // Capped to the CEMThrottle rate. The wrapper is shared with third-person renders,
        // so clearTransforms()/applyTransforms() below still run every call.
        if (recompute) {
            long now = System.nanoTime();
            if (CEMThrottle.shouldUpdateClose(state.lastUpdateTime, now) && !CEMApiState.isPaused(player)) {
                float effectiveFrameTime = CEMManager.getFrameTime();
                if (state.lastUpdateTime != 0L) {
                    float elapsed = (float) ((now - state.lastUpdateTime) / 1.0E9D);
                    if (elapsed > 0.0F) effectiveFrameTime = Math.min(elapsed, 0.5F);
                }
                state.lastUpdateTime = now;

                mainModel.setRotationAngles(0, 0, 0, 0, 0, 0.0625F, player);
                state.context.setup(player, 0, 0, player.ticksExisted + partialTicks, 0, 0, partialTicks, effectiveFrameTime);
                state.context.setRuleIndex(CEMRandomModels.ruleIndex(player, modelName));
                state.clearTransforms();
                CEMManager.adaptVanillaState(player, partMap, partialTicks);
                CEMAnimator animator = CEMManager.getAnimator(modelName);
                if (animator != null) animator.evaluate(state.context, state.transforms);
                CEMPartMapping.applyTransformAliases(modelName, state.transforms);
            }
        }
        wrapper.clearTransforms();
        wrapper.applyTransforms(state.transforms);
    }

    public static void end() {
        activeHand = null;
        armTarget = null;
        armSource = null;
        CEMRenderHooks.clearAll();
    }
}
