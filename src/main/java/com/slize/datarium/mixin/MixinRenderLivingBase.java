package com.slize.datarium.mixin;

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

import java.util.HashMap;
import java.util.Map;

@Mixin(RenderLivingBase.class)
public abstract class MixinRenderLivingBase<T extends EntityLivingBase> {

    @Shadow protected ModelBase mainModel;

    @Unique private Map<String, ModelRenderer> datarium$cachedPartMap = null;
    @Unique private Class<?> datarium$cachedModelClass = null;

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void datarium$onDoRenderHead(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        String modelName = CEMManager.getModelNameForEntity(entity);
        if (modelName == null) {
            CEMRenderHooks.clearAll();
            return;
        }

        CEMModelWrapper wrapper = CEMManager.getWrapper(mainModel, modelName);
        if (wrapper == null) {
            CEMRenderHooks.clearAll();
            return;
        }

        if (datarium$cachedPartMap == null || datarium$cachedModelClass != mainModel.getClass()) {
            datarium$cachedPartMap = CEMPartMapping.mapParts(mainModel, modelName);
            datarium$cachedModelClass = mainModel.getClass();
        }

        CEMRenderState state = CEMManager.getEntityState(entity);
        float limbSwing = entity.limbSwing - entity.limbSwingAmount * (1.0F - partialTicks);
        float limbSwingAmount = entity.prevLimbSwingAmount + (entity.limbSwingAmount - entity.prevLimbSwingAmount) * partialTicks;
        if (limbSwingAmount > 1.0F) limbSwingAmount = 1.0F;
        float ageInTicks = entity.ticksExisted + partialTicks;
        float yawOffset = entity.prevRenderYawOffset + (entity.renderYawOffset - entity.prevRenderYawOffset) * partialTicks;
        float headYaw = entity.prevRotationYawHead + (entity.rotationYawHead - entity.prevRotationYawHead) * partialTicks;
        float netHeadYaw = headYaw - yawOffset;
        float headPitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;

        state.context.setup(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTicks);
        state.clearTransforms();

        CEMRenderHooks.setActiveModelName(modelName);
        CEMRenderHooks.setActiveEntity(entity);
        CEMRenderHooks.setActivePartialTicks(partialTicks);
        CEMRenderHooks.setActivePartMap(datarium$cachedPartMap);
        CEMRenderHooks.setActiveWrapper(wrapper);
        CEMRenderHooks.setActiveContext(state.context);
        CEMRenderHooks.setActiveState(state);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ModelBase;setRotationAngles(FFFFFFLnet/minecraft/entity/Entity;)V",
                    shift = At.Shift.AFTER))
    private void datarium$afterSetRotationAngles(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        CEMRenderState state = CEMRenderHooks.getActiveState();
        CEMModelWrapper wrapper = CEMRenderHooks.getActiveWrapper();
        Map<String, ModelRenderer> partMap = CEMRenderHooks.getActivePartMap();

        if (state == null || wrapper == null || partMap == null) return;

        CEMAnimator animator = CEMManager.getAnimator(CEMRenderHooks.getActiveModelName());
        if (animator != null) {
            animator.evaluate(state.context, state.transforms);
        }

        wrapper.applyTransforms(state.transforms);

        Map<ModelRenderer, CEMModelRenderer> replacements = new HashMap<>();

        for (Map.Entry<String, ModelRenderer> entry : partMap.entrySet()) {
            String partName = entry.getKey();
            ModelRenderer vanillaPart = entry.getValue();

            CEMModelRenderer replacement = wrapper.getPartRenderer(partName);
            if (replacement != null) {
                CEMModelPart cemPart = replacement.getCemPart();
                // If it's a child part (has parent and not attached directly), hide vanilla
                boolean isChildReplacement = (cemPart.parent != null && !replacement.isAttached());

                if (isChildReplacement) {
                    state.saveOriginalState(partName, vanillaPart);
                    vanillaPart.showModel = false;
                } else {
                    replacements.put(vanillaPart, replacement);
                }
            }
        }

        CEMRenderHooks.setActiveReplacements(replacements);
    }

    @Inject(method = "renderModel", at = @At("HEAD"))
    private void datarium$onRenderModelHead(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scaleFactor, CallbackInfo ci) {
        CEMRenderState state = CEMRenderHooks.getActiveState();
        if (state != null) {
            CEMPartTransform rootTransform = state.transforms.get("root");
            if (rootTransform != null) {
                // Apply root transform to the global model matrix
                GlStateManager.translate(rootTransform.translateX * scaleFactor, rootTransform.translateY * scaleFactor, rootTransform.translateZ * scaleFactor);

                if (rootTransform.hasRotateZ) GlStateManager.rotate((float)Math.toDegrees(rootTransform.rotateZ), 0, 0, 1);
                if (rootTransform.hasRotateY) GlStateManager.rotate((float)Math.toDegrees(rootTransform.rotateY), 0, 1, 0);
                if (rootTransform.hasRotateX) GlStateManager.rotate((float)Math.toDegrees(rootTransform.rotateX), 1, 0, 0);

                if (rootTransform.hasScaleX || rootTransform.hasScaleY || rootTransform.hasScaleZ) {
                    GlStateManager.scale(rootTransform.scaleX, rootTransform.scaleY, rootTransform.scaleZ);
                }
            }
        }
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void datarium$onDoRenderReturn(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        CEMRenderState state = CEMRenderHooks.getActiveState();
        Map<String, ModelRenderer> partMap = CEMRenderHooks.getActivePartMap();

        if (state != null && partMap != null) {
            for (Map.Entry<String, ModelRenderer> entry : partMap.entrySet()) {
                String partName = entry.getKey();
                ModelRenderer renderer = entry.getValue();
                CEMRenderState.OriginalPartState original = state.originalStates.get(partName);
                if (original != null) {
                    renderer.showModel = original.showModel;
                }
            }
        }
        CEMRenderHooks.clearAll();
    }
}