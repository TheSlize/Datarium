package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.expr.CEMRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.IntBuffer;
import java.util.*;

public final class CEMGenericRender {
    private static final Map<Class<?>, List<Field>> MODEL_FIELDS = new HashMap<>();
    private static final Map<Object, Map<String, Binding>> BINDINGS = new WeakHashMap<>();
    private static final Map<String, SpaceAdapter> ADAPTERS = new HashMap<>();
    private static final double ALWAYS_ANIMATE_DIST_SQ = 24.0 * 24.0;
    private static final String[] DOUBLE_CHEST_KINDS = {"normal", "trapped", "christmas"};
    private static final Map<ResourceLocation, Boolean> TEXTURE_EXISTS = new HashMap<>();
    private static final IntBuffer TEXTURE_BUF = BufferUtils.createIntBuffer(16);

    static {
        Map<String, float[]> chest = new HashMap<>();
        chest.put("base", new float[]{0, 0, 0});
        chest.put("lid", new float[]{0, 9, 1});
        chest.put("knob", new float[]{0, 9, 1});
        SpaceAdapter single = new SpaceAdapter(true, chest, Collections.emptyMap(), false);
        for (String name : new String[]{"chest", "trapped_chest", "ender_chest"}) ADAPTERS.put(name, single);

        Map<String, float[]> large = new HashMap<>(chest);
        Map<String, float[]> halfOffsets = new HashMap<>();
        for (Map.Entry<String, float[]> e : chest.entrySet()) {
            large.put(e.getKey() + "_left", e.getValue());
            large.put(e.getKey() + "_right", e.getValue());
            halfOffsets.put(e.getKey() + "_right", new float[]{16, 0, 0});
        }
        SpaceAdapter doubled = new SpaceAdapter(true, large, halfOffsets, true);
        for (String name : new String[]{"chest_large", "trapped_chest_large"}) ADAPTERS.put(name, doubled);
    }

    public interface Setup {
        void apply(CEMRenderContext context, float frameTime);
    }

    private record SpaceAdapter(boolean flipX, Map<String, float[]> pivots, Map<String, float[]> offsets, boolean splitHalves) {}

    private record Bound(String name, CEMModelRenderer renderer) {}

    private static final class Binding {
        CEMModelWrapper wrapper;
        ModelBase mainModel;
        Map<ModelRenderer, List<Bound>> parts;
        Map<ModelRenderer, CEMModelRenderer> replacements;
        Map<String, ModelRenderer> partMap;
        Map<ModelRenderer, ModelBase> owners;
        Set<ModelRenderer> hidden;
    }

    private static final class Session {
        String modelName;
        Binding binding;
        @Nullable SpaceAdapter adapter;
        CEMRenderState state;
        Object[] previousHooks;
        Session previous;
        boolean animated;
        boolean orphansRendered;
        @Nullable ModelBase renderingModel;
    }

    @Nullable private static Session active;
    private static int inHandDepth;
    private static int onHeadDepth;
    private static int inItemFrameDepth;

    private CEMGenericRender() {}

    private static final List<Entity> HOLDERS = new ArrayList<>();

    public static void pushInHand(@Nullable Entity holder) {
        inHandDepth++;
        HOLDERS.add(holder);
    }

    public static void popInHand() {
        inHandDepth = Math.max(0, inHandDepth - 1);
        if (!HOLDERS.isEmpty()) HOLDERS.removeLast();
    }

    /** Entity whose held item is being rendered right now, if any. */
    @Nullable
    public static Entity currentHolder() {
        return HOLDERS.isEmpty() ? null : HOLDERS.getLast();
    }
    public static void pushOnHead() { onHeadDepth++; }
    public static void popOnHead() { onHeadDepth = Math.max(0, onHeadDepth - 1); }
    public static void pushInItemFrame() { inItemFrameDepth++; }
    public static void popInItemFrame() { inItemFrameDepth = Math.max(0, inItemFrameDepth - 1); }

    @Nullable
    public static String activeModelName() {
        Session session = active;
        return session != null ? session.modelName : null;
    }

    public static List<ModelBase> modelsOf(Object renderer) {
        List<Field> fields = MODEL_FIELDS.computeIfAbsent(renderer.getClass(), CEMGenericRender::findModelFields);
        List<ModelBase> models = new ArrayList<>(fields.size());
        for (Field field : fields) {
            try {
                Object value = field.get(renderer);
                if (value instanceof ModelBase model && !models.contains(model)) models.add(model);
            } catch (IllegalAccessException ignored) {
            }
        }
        return models;
    }

    private static List<Field> findModelFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !ModelBase.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    out.add(field);
                } catch (RuntimeException e) {
                    DatariumMain.LOGGER.debug("[CEM] cannot access model field {} of {}", field.getName(), type.getName(), e);
                }
            }
        }
        return out;
    }

    public static boolean beginEntity(Entity entity, Object renderer, String modelName, double camDistSq,
                                      float partialTicks, float limbSwing, float limbSpeed) {
        CEMRenderState state = CEMManager.getEntityState(entity);
        boolean started = begin(renderer, null, entity, modelName, state, camDistSq, (ctx, frameTime) -> {
            ctx.setupGeneric(entity.getEntityId(), entity, null, limbSwing, limbSpeed, entity.ticksExisted + partialTicks, partialTicks, frameTime);
            ctx.setRuleIndex(CEMRandomModels.ruleIndex(entity, modelName));
        });
        if (started) CEMManager.adaptGenericState(entity, CEMRenderHooks.getActivePartMap(), partialTicks);
        return started;
    }

    public static boolean beginTile(@Nullable TileEntity tile, String stateKey, Object renderer, String modelName,
                                    double camDistSq, float partialTicks) {
        boolean inWorld = tile != null && tile.getWorld() != null;
        CEMRenderState state = inWorld ? CEMManager.getTileState(tile) : CEMManager.getSharedState(stateKey);
        long id = inWorld ? tile.getPos().toLong() : stateKey.hashCode();
        float age = inWorld ? tile.getWorld().getTotalWorldTime() + partialTicks : partialTicks;
        return begin(renderer, null, tile, modelName, state, camDistSq, (ctx, frameTime) -> {
            ctx.setupGeneric(id, null, tile, 0, 0, age, partialTicks, frameTime);
            ctx.setRuleIndex(CEMRandomModels.ruleIndex(tile, modelName));
        });
    }

    /** Session for a single model instance that is not a field of a renderer (layer/shoulder/item models). */
    public static boolean beginModel(Object key, ModelBase model, String modelName, CEMRenderState state,
                                     double camDistSq, Setup setup) {
        return begin(key, Collections.singletonList(model), null, modelName, state, camDistSq, setup);
    }

    @Nullable
    private static Binding bindingFor(Object renderer, @Nullable List<ModelBase> explicitModels, String modelName) {
        Map<String, Binding> byName = BINDINGS.computeIfAbsent(renderer, _ -> new HashMap<>());
        Binding binding = byName.get(modelName);
        if (binding != null && binding.wrapper == CEMManager.peekWrapper(modelName)) return binding;

        List<ModelBase> models = explicitModels != null ? explicitModels : modelsOf(renderer);
        if (models.isEmpty()) return null;
        CEMModelWrapper wrapper = CEMManager.getWrapper(models.getFirst(), modelName);
        if (wrapper == null) return null;
        SpaceAdapter adapter = ADAPTERS.get(CEMManager.baseName(modelName));

        binding = new Binding();
        binding.wrapper = wrapper;
        binding.mainModel = models.getFirst();
        binding.parts = new IdentityHashMap<>();
        binding.replacements = new IdentityHashMap<>();
        binding.partMap = new HashMap<>();
        binding.owners = new IdentityHashMap<>();
        for (ModelBase model : models) {
            for (Map.Entry<String, ModelRenderer> entry : CEMPartMapping.mapParts(model, modelName).entrySet()) {
                binding.partMap.putIfAbsent(entry.getKey(), entry.getValue());
                binding.owners.putIfAbsent(entry.getValue(), model);
                if (binding.parts.containsKey(entry.getValue())) continue;
                String[] names = adapter != null && adapter.splitHalves()
                        ? new String[]{entry.getKey(), entry.getKey() + "_left", entry.getKey() + "_right"}
                        : new String[]{entry.getKey()};
                List<Bound> bound = new ArrayList<>(names.length);
                for (String name : names) {
                    CEMModelRenderer replacement = wrapper.getPartRenderer(name);
                    if (replacement == null || (replacement.getCemPart().parent != null && !replacement.isAttached())) continue;
                    boolean duplicate = false;
                    for (Bound b : bound) duplicate |= b.renderer() == replacement;
                    if (!duplicate) bound.add(new Bound(name, replacement));
                }
                if (bound.isEmpty()) continue;
                binding.parts.put(entry.getValue(), bound);
                binding.replacements.put(entry.getValue(), bound.getFirst().renderer());
            }
        }
        binding.hidden = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelBase model : models) {
            for (ModelRenderer part : model.boxList) {
                if (!(part instanceof CEMModelRenderer) && !binding.parts.containsKey(part)) binding.hidden.add(part);
            }
        }
        byName.put(modelName, binding);
        return binding;
    }

    private static boolean begin(Object renderer, @Nullable List<ModelBase> explicitModels, @Nullable Object subject,
                                 String modelName, CEMRenderState state, double camDistSq, Setup setup) {
        Binding binding = bindingFor(renderer, explicitModels, modelName);
        if (binding == null) return false;
        CEMModelWrapper wrapper = binding.wrapper;

        Session session = new Session();
        session.modelName = modelName;
        session.binding = binding;
        session.adapter = ADAPTERS.get(CEMManager.baseName(modelName));
        session.state = state;
        session.previousHooks = CEMRenderHooks.snapshot();
        session.previous = active;

        state.lastRenderFrame = CEMManager.getFrameCounter();
        long now = System.nanoTime();
        state.animateThisFrame = (camDistSq < ALWAYS_ANIMATE_DIST_SQ || CEMThrottle.shouldUpdate(camDistSq, state.lastUpdateTime, now))
                && !CEMApiState.isPaused(subject);
        if (state.animateThisFrame) {
            float frameTime = CEMManager.getFrameTime();
            if (state.lastUpdateTime != 0L) {
                float elapsed = (float) ((now - state.lastUpdateTime) / 1.0E9D);
                if (elapsed > 0.0F) frameTime = Math.min(elapsed, 0.5F);
            }
            state.lastUpdateTime = now;
            setup.apply(state.context, frameTime);
            state.context.setRenderFlags(inHandDepth > 0, onHeadDepth > 0, inItemFrameDepth > 0);
            state.clearTransforms();
        }

        wrapper.detachVanillaParts();
        for (Map.Entry<ModelRenderer, List<Bound>> entry : binding.parts.entrySet()) {
            for (Bound bound : entry.getValue()) {
                if (bound.renderer().getVanillaPart() == null) bound.renderer().setVanillaPart(entry.getKey());
                bound.renderer().setPivotOverride(session.adapter != null ? session.adapter.pivots().get(bound.name()) : null);
            }
        }

        CEMRenderHooks.setActiveModelName(modelName);
        CEMRenderHooks.setActivePartMap(binding.partMap);
        CEMRenderHooks.setActiveMainModel(binding.mainModel);
        CEMRenderHooks.setActiveWrapper(wrapper);
        CEMRenderHooks.setActiveContext(state.context);
        CEMRenderHooks.setActiveState(state);
        CEMRenderHooks.setActiveReplacements(binding.replacements);
        CEMRenderHooks.setActiveSecondaryWrapper(null);

        active = session;
        return true;
    }

    public static void end() {
        Session session = active;
        if (session == null) return;
        active = session.previous;
        CEMRenderHooks.restore(session.previousHooks);
    }

    public static boolean isHidden(ModelRenderer part) {
        Session session = active;
        return session != null && session.binding.hidden.contains(part);
    }

    public static boolean renderPart(ModelRenderer vanillaPart, CEMModelRenderer replacement, float scale) {
        Session session = active;
        if (session == null) return false;
        List<Bound> bound = session.binding.parts.get(vanillaPart);
        if (bound == null) return false;
        selectRenderingModel(session, vanillaPart);
        ensureAnimated(session);
        if (replacement.isAttached()) return false;

        GlStateManager.pushMatrix();
        if (session.adapter != null && session.adapter.flipX()) {
            GlStateManager.translate(0.0F, 16.0F * scale, 16.0F * scale);
            GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        }
        if (!session.orphansRendered) {
            session.orphansRendered = true;
            session.binding.wrapper.renderOrphanRoots(scale);
        }
        int previousTexture = -1;
        String halfKind = null;
        if (session.adapter != null && session.adapter.splitHalves()) {
            TEXTURE_BUF.clear();
            GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D, TEXTURE_BUF);
            previousTexture = TEXTURE_BUF.get(0);
            halfKind = doubleChestKind(previousTexture);
        }
        for (Bound b : bound) {
            CEMModelRenderer renderer = b.renderer();
            if (renderer.getVanillaPart() != vanillaPart) renderer.setVanillaPart(vanillaPart);
            if (renderer.isDeferred()) continue;
            if (halfKind != null) bindHalfTexture(halfKind, b.name(), previousTexture);
            float[] offset = session.adapter != null ? session.adapter.offsets().get(b.name()) : null;
            if (offset == null) {
                renderer.renderWithVanilla(scale);
                continue;
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(offset[0] * scale, offset[1] * scale, offset[2] * scale);
            renderer.renderWithVanilla(scale);
            GlStateManager.popMatrix();
        }
        if (halfKind != null) GlStateManager.bindTexture(previousTexture);
        GlStateManager.popMatrix();
        return true;
    }

    public static void invalidateTextures() {
        TEXTURE_EXISTS.clear();
    }

    @Nullable
    private static String doubleChestKind(int textureId) {
        TextureManager manager = Minecraft.getMinecraft().getTextureManager();
        for (String kind : DOUBLE_CHEST_KINDS) {
            ITextureObject texture = manager.getTexture(new ResourceLocation("textures/entity/chest/" + kind + "_double.png"));
            if (texture != null && texture.getGlTextureId() == textureId) return kind;
        }
        return null;
    }

    private static void bindHalfTexture(String kind, String partName, int fallbackTexture) {
        String side = partName.endsWith("_left") ? "right" : partName.endsWith("_right") ? "left" : null;
        ResourceLocation location = side != null ? new ResourceLocation("textures/entity/chest/" + kind + "_" + side + ".png") : null;
        if (location != null && TEXTURE_EXISTS.computeIfAbsent(location, CEMGenericRender::textureExists)) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(location);
        } else {
            GlStateManager.bindTexture(fallbackTexture);
        }
    }

    private static boolean textureExists(ResourceLocation location) {
        try (IResource ignored = Minecraft.getMinecraft().getResourceManager().getResource(location)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void beforePartRender(ModelRenderer vanillaPart, CEMModelRenderer replacement) {
        Session session = active;
        if (session == null || !session.binding.parts.containsKey(vanillaPart)) return;
        selectRenderingModel(session, vanillaPart);
        if (replacement.getVanillaPart() != vanillaPart) replacement.setVanillaPart(vanillaPart);
        ensureAnimated(session);
    }

    private static void selectRenderingModel(Session session, ModelRenderer vanillaPart) {
        ModelBase model = session.binding.owners.get(vanillaPart);
        if (model == null || model == session.renderingModel) return;
        session.renderingModel = model;
        for (Map.Entry<ModelRenderer, List<Bound>> entry : session.binding.parts.entrySet()) {
            if (session.binding.owners.get(entry.getKey()) != model) continue;
            for (Bound b : entry.getValue()) {
                if (b.renderer().getVanillaPart() != entry.getKey()) b.renderer().setVanillaPart(entry.getKey());
            }
        }
    }

    private static void ensureAnimated(Session session) {
        if (session.animated) return;
        session.animated = true;

        CEMModelWrapper wrapper = session.binding.wrapper;
        CEMRenderState state = session.state;
        wrapper.clearTransforms();
        if (state.animateThisFrame) {
            CEMAnimator animator = CEMManager.getAnimator(session.modelName);
            if (animator != null) animator.evaluate(state.context, state.transforms);
        }
        wrapper.applyTransforms(state.transforms);
    }
}
