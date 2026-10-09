package com.slize.datarium.client.cem;

import com.slize.datarium.client.cem.expr.CEMRenderContext;
import com.slize.datarium.mixin.accessors.IModelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

public class CEMRenderHooks {
    private static final Slot<CEMModelWrapper> activeWrapper = new Slot<>();
    private static final Slot<Map<String, CEMPartTransform>> activeTransforms = new Slot<>();
    private static final Slot<Map<String, ModelRenderer>> activePartMap = new Slot<>();
    private static final Slot<Map<ModelRenderer, CEMModelRenderer>> activeReplacements = new Slot<>();
    private static final Slot<EntityLivingBase> activeEntity = new Slot<>();
    private static final Slot<Float> activePartialTicks = new Slot<>();
    private static final Slot<String> activeModelName = new Slot<>();
    private static final Slot<CEMRenderContext> activeContext = new Slot<>();
    private static final Slot<CEMRenderState> activeState = new Slot<>();
    private static final Slot<ModelBase> activeMainModel = new Slot<>();
    private static final Slot<CEMModelWrapper> activeSecondaryWrapper = new Slot<>();
    private static boolean renderingInGui;

    private static final class Slot<T> {
        private T value;

        T get() { return value; }

        void set(T value) { this.value = value; }

        void remove() { this.value = null; }
    }

    public static void setActiveMainModel(ModelBase model) { activeMainModel.set(model); }
    public static Object[] snapshot() {
        return new Object[]{
                activeWrapper.get(), activeTransforms.get(), activePartMap.get(), activeReplacements.get(),
                activeEntity.get(), activePartialTicks.get(), activeModelName.get(), activeContext.get(),
                activeState.get(), activeMainModel.get(), activeSecondaryWrapper.get()
        };
    }

    @SuppressWarnings("unchecked")
    public static void restore(Object[] s) {
        set(activeWrapper, (CEMModelWrapper) s[0]);
        set(activeTransforms, (Map<String, CEMPartTransform>) s[1]);
        set(activePartMap, (Map<String, ModelRenderer>) s[2]);
        set(activeReplacements, (Map<ModelRenderer, CEMModelRenderer>) s[3]);
        set(activeEntity, (EntityLivingBase) s[4]);
        set(activePartialTicks, (Float) s[5]);
        set(activeModelName, (String) s[6]);
        set(activeContext, (CEMRenderContext) s[7]);
        set(activeState, (CEMRenderState) s[8]);
        set(activeMainModel, (ModelBase) s[9]);
        set(activeSecondaryWrapper, (CEMModelWrapper) s[10]);
    }

    private static <T> void set(Slot<T> local, @Nullable T value) {
        if (value == null) local.remove();
        else local.set(value);
    }

    @Nullable
    public static EntityLivingBase getActiveEntity() {
        return activeEntity.get();
    }

    /** Full CEM replacement for a part owned by a layer model that has its own .jem. */
    @Nullable
    public static CEMModelRenderer getSecondaryReplacement(ModelRenderer part) {
        CEMManager.SecondaryBinding b = datarium$binding(part);
        if (b == null) return null;
        CEMModelRenderer r = b.replacements.get(part);
        if (r != null) {
            if (r.getVanillaPart() != part) r.setVanillaPart(part);
            CEMManager.ensureSecondaryFrame(b, activeState.get());
        }
        return r;
    }

    /** Transform-only source for parts of a layer model without its own .jem (armor). */
    @Nullable
    public static CEMModelRenderer getMirrorSource(ModelRenderer part) {
        CEMManager.SecondaryBinding b = datarium$binding(part);
        CEMModelRenderer mirror = b != null ? b.mirrors.get(part) : null;
        if (mirror != null) mirror.pin();
        return mirror;
    }

    @Nullable
    private static CEMManager.SecondaryBinding datarium$binding(ModelRenderer part) {
        CEMModelWrapper wrapper = activeWrapper.get();
        CEMRenderState state = activeState.get();
        String name = activeModelName.get();
        if (wrapper == null || state == null || name == null) return null;

        ModelBase owner = ((IModelRendererAccessor) part).datarium$getBaseModel();
        if (owner == null || owner == activeMainModel.get()) return null;

        return CEMManager.getSecondaryBinding(owner, name, wrapper, activeEntity.get());
    }

    public static void setActiveWrapper(@Nullable CEMModelWrapper wrapper) {
        activeWrapper.set(wrapper);
        if (wrapper != null && CEMDebugSystem.enabled) {
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
        activeMainModel.remove();
        activeSecondaryWrapper.remove();
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

    /** @return true if {@code vanillaPart} is replaced by a CEM part that draws nothing with {@code texture} bound. */
    public static boolean drawsNothing(ModelRenderer vanillaPart, ResourceLocation texture, float scale) {
        if (CEMGenericRender.inSession()) return false;
        CEMModelRenderer replacement = getReplacement(vanillaPart);
        if (replacement == null) return false;
        ITextureObject object = Minecraft.getMinecraft().getTextureManager().getTexture(texture);
        if (object == null) return false;
        CEMTextureMask mask = CEMTextureMask.peek(object.getGlTextureId());
        return mask != null && replacement.drawsNothingFor(mask, scale);
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

    @Nullable
    public static CEMRenderContext getActiveContext() {
        return activeContext.get();
    }

    public static void setActiveState(CEMRenderState state) {
        activeState.set(state);
    }

    @Nullable
    public static CEMRenderState getActiveState() {
        return activeState.get();
    }

    public static float getActivePartialTicks() { return activePartialTicks.get(); }

    public static void setRenderingInGui(boolean value) { renderingInGui = value; }

    public static boolean isRenderingInGui() { return renderingInGui; }

    /** Layer part the pack's secondary .jem does not define - OptiFine drops it entirely. */
    public static boolean isSecondaryHidden(ModelRenderer part) {
        CEMManager.SecondaryBinding b = datarium$binding(part);
        return b != null && b.wrapper != null && b.hidden.contains(part);
    }

    public static void setActiveSecondaryWrapper(@Nullable CEMModelWrapper wrapper) {
        if (wrapper == null) activeSecondaryWrapper.remove();
        else activeSecondaryWrapper.set(wrapper);
    }

    @Nullable
    public static CEMModelWrapper getActiveSecondaryWrapper() {
        return activeSecondaryWrapper.get();
    }
}