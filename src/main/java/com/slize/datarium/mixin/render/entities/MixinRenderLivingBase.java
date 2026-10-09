package com.slize.datarium.mixin.render.entities;

import com.slize.datarium.client.cem.*;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

@Mixin(RenderLivingBase.class)
public abstract class MixinRenderLivingBase<T extends EntityLivingBase> {

    @Shadow protected ModelBase mainModel;

    @Unique private Map<String, ModelRenderer> datarium$cachedPartMap = null;
    @Unique private String datarium$cachedModelName = null;
    @Unique private Class<?> datarium$cachedModelClass = null;
    @Unique private final ArrayDeque<ModelRenderer> datarium$hiddenParts = new ArrayDeque<>();

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void datarium$onDoRenderHead(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        String modelName = CEMManager.getModelNameForEntity(entity);
        if (modelName == null) { CEMRenderHooks.clearAll(); return; }

        CEMModelWrapper wrapper = CEMManager.getWrapper(mainModel, modelName);
        if (wrapper == null) { CEMRenderHooks.clearAll(); return; }

        if (datarium$cachedPartMap == null || datarium$cachedModelClass != mainModel.getClass()
                || !modelName.equals(datarium$cachedModelName)) {
            datarium$cachedPartMap = CEMPartMapping.mapParts(mainModel, modelName);
            datarium$cachedModelClass = mainModel.getClass();
            datarium$cachedModelName = modelName;
        }

        CEMRenderState state = CEMManager.getEntityState(entity);
        state.lastRenderFrame = CEMManager.getFrameCounter();
        float limbSwing = entity.limbSwing - entity.limbSwingAmount * (1.0F - partialTicks);
        float limbSwingAmount = entity.prevLimbSwingAmount + (entity.limbSwingAmount - entity.prevLimbSwingAmount) * partialTicks;
        if (limbSwingAmount > 1.0F) limbSwingAmount = 1.0F;
        float ageInTicks = entity.ticksExisted + partialTicks;
        float yawOffset = datarium$interpolateRotation(entity.prevRenderYawOffset, entity.renderYawOffset, partialTicks);
        float headYaw = datarium$interpolateRotation(entity.prevRotationYawHead, entity.rotationYawHead, partialTicks);
        float netHeadYaw = headYaw - yawOffset;
        netHeadYaw += CEMManager.getHeadYawOffset(modelName);
        while (netHeadYaw < -180.0F) netHeadYaw += 360.0F;
        while (netHeadYaw >= 180.0F) netHeadYaw -= 360.0F;
        float headPitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;

        // do you have any other non-crazy ways of optimizing fps? I don't
        double distSq = x * x + y * y + z * z;
        long now = System.nanoTime();
        state.animateThisFrame = CEMThrottle.shouldUpdate(distSq, state.lastUpdateTime, now) && !CEMApiState.isPaused(entity);

        if (state.animateThisFrame) {
            float effectiveFrameTime = CEMManager.getFrameTime();
            if (state.lastUpdateTime != 0L) {
                float elapsed = (float) ((now - state.lastUpdateTime) / 1.0E9D);
                if (elapsed > 0.0F) effectiveFrameTime = Math.min(elapsed, 0.5F);
            }
            state.lastUpdateTime = now;

            state.context.setup(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTicks, effectiveFrameTime);
            state.context.setRuleIndex(CEMRandomModels.ruleIndex(entity, modelName));
            state.clearTransforms();
        }

        CEMRenderHooks.setActiveModelName(modelName);
        CEMRenderHooks.setActiveEntity(entity);
        CEMRenderHooks.setActivePartialTicks(partialTicks);
        CEMRenderHooks.setActivePartMap(datarium$cachedPartMap);
        CEMRenderHooks.setActiveMainModel(mainModel);
        CEMRenderHooks.setActiveWrapper(wrapper);
        CEMRenderHooks.setActiveContext(state.context);
        CEMRenderHooks.setActiveState(state);

        wrapper.detachVanillaParts();
        wrapper.beginRenderLog();

        Map<ModelRenderer, CEMModelRenderer> replacements = new HashMap<>();
        for (Map.Entry<String, ModelRenderer> entry : datarium$cachedPartMap.entrySet()) {
            String partName = entry.getKey();
            ModelRenderer vanillaPart = entry.getValue();

            CEMModelRenderer replacement = wrapper.getPartRenderer(partName);
            if (replacement == null) continue;

            state.saveOriginalState(partName, vanillaPart);

            if (replacement.getCemPart().parent != null && !replacement.isAttached()) {
                vanillaPart.showModel = false;
                continue;
            }

            replacement.setVanillaPart(vanillaPart);
            replacements.put(vanillaPart, replacement);
        }
        CEMRenderHooks.setActiveReplacements(replacements);
        for (CEMModelRenderer root : wrapper.getRootRenderers()) {
            if (root.getVanillaPart() != null || root.isAttached()) continue;
            String name = root.getCemPart().id != null ? root.getCemPart().id : root.getCemPart().part;
            String hostName = name != null ? CEMPartMapping.getExtraHost(name) : null;
            if (hostName == null) continue;
            CEMModelRenderer host = wrapper.getPartRenderer(hostName);
            if (host != null && host.getVanillaPart() != null) {
                host.addExtra(root);
                wrapper.markHosted(root);
            }
        }

        Map<String, String> reparents = CEMPartMapping.getReparents(modelName);
        if (reparents != null) {
            for (Map.Entry<String, String> e : reparents.entrySet()) {
                CEMModelRenderer child = wrapper.getPartRenderer(e.getKey());
                CEMModelRenderer parent = wrapper.getPartRenderer(e.getValue());
                if (child == null || parent == null || child == parent) continue;
                child.setDeferred(parent);
                parent.addExtra(child);
            }
        }

        datarium$hiddenParts.clear();
        for (ModelRenderer part : mainModel.boxList) {
            if (part instanceof CEMModelRenderer || !part.showModel) continue;
            if (replacements.containsKey(part)) continue;
            part.showModel = false;
            datarium$hiddenParts.add(part);
        }

        CEMManager.logMapping(modelName, mainModel, wrapper, replacements);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ModelBase;setRotationAngles(FFFFFFLnet/minecraft/entity/Entity;)V",
                    shift = At.Shift.AFTER))
    private void datarium$afterSetRotationAngles(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        CEMRenderState state = CEMRenderHooks.getActiveState();
        CEMModelWrapper wrapper = CEMRenderHooks.getActiveWrapper();
        if (state == null || wrapper == null) return;

        CEMManager.adaptVanillaState(entity, CEMRenderHooks.getActivePartMap(), CEMRenderHooks.getActivePartialTicks());
        wrapper.clearTransforms();
        if (state.animateThisFrame) {
            CEMAnimator animator = CEMManager.getAnimator(CEMRenderHooks.getActiveModelName());
            if (animator != null) animator.evaluate(state.context, state.transforms, wrapper.prunedEntries(state));
            CEMPartMapping.applyTransformAliases(CEMRenderHooks.getActiveModelName(), state.transforms);
        }
        wrapper.applyTransforms(state);
    }

    @Inject(method = "renderModel", at = @At("HEAD"))
    private void datarium$onRenderModelHead(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scaleFactor, CallbackInfo ci) {
        CEMRenderState state = CEMRenderHooks.getActiveState();
        if (state == null) return;

        float[] adj = CEMManager.getRenderAdjust(CEMRenderHooks.getActiveModelName());
        if (adj != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, (adj[0] - 1.0F) * -1.5078125F, 0.0F);
            GlStateManager.scale(adj[0], adj[0], adj[0]);
            GlStateManager.translate(0.0F, adj[1] * scaleFactor, 0.0F);
        }

        if (entity.isChild()) {
            float[] baby = CEMManager.getBabyTransform(CEMRenderHooks.getActiveModelName());
            if (baby != null) {
                GlStateManager.scale(baby[0], baby[0], baby[0]);
                GlStateManager.translate(0.0F, baby[1] * scaleFactor, 0.0F);
            }
        }
        CEMPartTransform rootTransform = state.transforms.get("root");
        if (rootTransform != null) {
            GlStateManager.translate(rootTransform.translateX * scaleFactor, rootTransform.translateY * scaleFactor, rootTransform.translateZ * scaleFactor);

            if (rootTransform.hasRotateZ) GlStateManager.rotate((float) Math.toDegrees(rootTransform.rotateZ), 0, 0, 1);
            if (rootTransform.hasRotateY) GlStateManager.rotate((float) Math.toDegrees(rootTransform.rotateY), 0, 1, 0);
            if (rootTransform.hasRotateX) GlStateManager.rotate((float) Math.toDegrees(rootTransform.rotateX), 1, 0, 0);

            if (rootTransform.hasScaleX || rootTransform.hasScaleY || rootTransform.hasScaleZ) {
                GlStateManager.scale(rootTransform.scaleX, rootTransform.scaleY, rootTransform.scaleZ);
            }
        }
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void datarium$onRenderModelReturn(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scaleFactor, CallbackInfo ci) {
        CEMModelWrapper wrapper = CEMRenderHooks.getActiveWrapper();
        if (wrapper != null) {
            wrapper.renderOrphanRoots(scaleFactor);
            if (CEMDebugSystem.enabled) {
                wrapper.renderDebug(scaleFactor);
            }
        }
        if (CEMManager.getRenderAdjust(CEMRenderHooks.getActiveModelName()) != null) {
            GlStateManager.popMatrix();
        }
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void datarium$onDoRenderReturn(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        CEMRenderState state = CEMRenderHooks.getActiveState();
        Map<String, ModelRenderer> partMap = CEMRenderHooks.getActivePartMap();
        CEMModelWrapper wrapper = CEMRenderHooks.getActiveWrapper();
        if (state != null && wrapper != null) {
            wrapper.updatePruning(state, CEMManager.getAnimator(CEMRenderHooks.getActiveModelName()));
        }

        ModelRenderer hidden;
        while ((hidden = datarium$hiddenParts.pollFirst()) != null) hidden.showModel = true;

        if (state != null && partMap != null) {
            for (Map.Entry<String, ModelRenderer> entry : partMap.entrySet()) {
                CEMRenderState.OriginalPartState original = state.originalStates.get(entry.getKey());
                if (original == null) continue;
                ModelRenderer renderer = entry.getValue();
                renderer.rotationPointX = original.rotationPointX;
                renderer.rotationPointY = original.rotationPointY;
                renderer.rotationPointZ = original.rotationPointZ;
            }
        }
        CEMRenderHooks.clearAll();
    }

    // shulkers' models (mob ones, I mean) tend not to rotate their sight to where they actually look
    // probably mc api differences paying off again
    @Unique
    private static float datarium$interpolateRotation(float prev, float current, float partialTicks) {
        float f = current - prev;
        while (f < -180.0F) f += 360.0F;
        while (f >= 180.0F) f -= 360.0F;
        return prev + partialTicks * f;
    }
}