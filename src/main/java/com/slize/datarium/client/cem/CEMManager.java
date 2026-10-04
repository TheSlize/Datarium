package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.expr.CEMGlobalVars;
import com.slize.datarium.client.cet.CETNbt;
import com.slize.datarium.util.PackConverter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.*;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.AbstractIllager;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.block.BlockChest;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.tileentity.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class CEMManager {
    private static final Map<String, CEMModel> modelCache = new ConcurrentHashMap<>();
    private static final Map<String, CEMAnimator> animatorCache = new ConcurrentHashMap<>();
    private static final Map<String, CEMModelWrapper> wrapperCache = new ConcurrentHashMap<>();
    private static final Map<Long, CEMRenderState> entityStates = new ConcurrentHashMap<>();

    private static final Map<String, ResourceLocation> modelLocations = new ConcurrentHashMap<>();
    private static final String[] PRIMARY_DIRS = {"emf/cem/", "optifine/cem/"};
    private static final String[] LEGACY_DIRS = {"citresewn/cem/", "mcpatcher/cem/", "cem/"};
    @Nullable private static List<String> packOrder;

    private static final Set<String> noModelNames = ConcurrentHashMap.newKeySet();
    private static final Set<String> noAnimatorNames = ConcurrentHashMap.newKeySet();
    private static final Set<String> noWrapperNames = ConcurrentHashMap.newKeySet();

    private static long frameCounter = 0L;
    private static long lastFrameNano = 0L;
    private static float frameTime = 0.05F;

    public static void onFrameStart() {
        long now = System.nanoTime();
        if (lastFrameNano != 0L) {
            float dt = (float) ((now - lastFrameNano) / 1.0E9D);
            frameTime = dt < 0.0F ? 0.0F : Math.min(dt, 0.5F);
        }
        lastFrameNano = now;
        frameCounter++;
    }

    public static long getFrameCounter() { return frameCounter; }
    public static float getFrameTime() { return frameTime; }

    private static final Map<String, String[]> COMPANIONS = new HashMap<>();
    private static final Map<String, String> resolvedModelNames = new ConcurrentHashMap<>();
    private static final Set<String> loggedMappings = ConcurrentHashMap.newKeySet();
    private static final String NO_MODEL = "";
    private static final Pattern BABY_SEGMENT = Pattern.compile("_baby(?=$|_)");
    private static final Map<String, String> MAPPING_NAMES = new ConcurrentHashMap<>();

    static {
        COMPANIONS.put("player", new String[]{"player_cape"});
        COMPANIONS.put("player_slim", new String[]{"player_cape"});
    }

    @Nullable
    private static String entityKey(Entity entity) {
        ResourceLocation id = EntityList.getKey(entity);
        if (id != null) {
            return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
        }
        String simple = entity.getClass().getSimpleName();
        if (!simple.startsWith("Entity") || simple.length() <= 6) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 6; i < simple.length(); i++) {
            char c = simple.charAt(i);
            if (Character.isUpperCase(c) && !out.isEmpty()) out.append('_');
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    /** One line per model telling us exactly what the 1.12.2 mapping missed. */
    public static void logMapping(String modelName, ModelBase model, CEMModelWrapper wrapper,
                                  Map<ModelRenderer, CEMModelRenderer> replacements) {
        if (!loggedMappings.add(modelName)) return;

        Set<String> unboundPack = new TreeSet<>();
        for (Map.Entry<String, CEMModelRenderer> e : wrapper.getAllParts().entrySet()) {
            CEMModelRenderer r = e.getValue();
            if (r.isTopLevel() && r.getVanillaPart() == null) unboundPack.add(e.getKey());
        }

        Map<ModelRenderer, String> names = CEMPartMapping.fieldNames(model);
        Set<String> unboundVanilla = new TreeSet<>();
        for (ModelRenderer r : model.boxList) {
            if (r instanceof CEMModelRenderer || replacements.containsKey(r)) continue;
            unboundVanilla.add(names.getOrDefault(r, "<anonymous child>"));
        }

        DatariumMain.LOGGER.warn("[CEM] {} -> pack parts with no vanilla field: {} | vanilla fields hidden: {}",
                modelName, unboundPack, unboundVanilla);
    }

    public static void invalidate() {
        modelCache.clear();
        animatorCache.clear();
        wrapperCache.clear();
        noModelNames.clear();
        noAnimatorNames.clear();
        noWrapperNames.clear();
        secondaryBindings.clear();
        resolvedModelNames.clear();
        modelLocations.clear();
        packOrder = null;
        CEMRandomModels.invalidate();
        CETNbt.invalidate();
        CEMArmorModels.invalidate();
        PackConverter.invalidate();
        CEMGenericRender.invalidateTextures();
        CEMGlobalVars.clear();
    }

    @Nullable
    public static CEMModel getModel(String name) {
        CEMModel cached = modelCache.get(name);
        if (cached != null) {
            return cached;
        }
        if (noModelNames.contains(name)) {
            return null;
        }

        ResourceLocation location = locateModel(name);
        CEMModel model = location != null ? CEMModelLoader.loadJEM(location) : null;
        if (model == null) {
            noModelNames.add(name);
            return null;
        }
        mergeCompanions(name, model, location);
        modelLocations.put(name, location);
        modelCache.put(name, model);
        return model;
    }

    @Nullable
    public static ResourceLocation getModelLocation(String name) {
        return getModel(name) != null ? modelLocations.get(name) : null;
    }

    public static boolean isPlayerModel(String name) {
        String base = baseName(name);
        return base.equals("player") || base.equals("player_slim");
    }

    public static String baseName(String name) {
        int hash = name.indexOf('#');
        return hash >= 0 ? name.substring(0, hash) : name;
    }

    public static String mappingName(String name) {
        String mapped = MAPPING_NAMES.get(name);
        if (mapped == null) {
            mapped = BABY_SEGMENT.matcher(baseName(name)).replaceFirst("");
            MAPPING_NAMES.put(name, mapped);
        }
        return mapped;
    }

    public static String fileNameOf(String name) {
        String base = baseName(name);
        int colon = base.indexOf(':');
        return colon >= 0 ? base.substring(colon + 1) : base;
    }

    private static String namespaceOf(String name) {
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(0, colon) : "minecraft";
    }

    public static ResourceLocation siblingOf(ResourceLocation location, String fileName) {
        String path = location.getPath();
        return new ResourceLocation(location.getNamespace(), path.substring(0, path.lastIndexOf('/') + 1) + fileName);
    }

    public static boolean resourceExists(ResourceLocation location) {
        try (IResource ignored = Minecraft.getMinecraft().getResourceManager().getResource(location)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * EMFDirectoryHandler order: emf/cem beats optifine/cem and "name/name.jem" beats "name.jem"
     * unless the other one comes from a higher priority resource pack.
     */
    @Nullable
    private static ResourceLocation locateModel(String name) {
        int hash = name.indexOf('#');
        if (hash >= 0) {
            String base = name.substring(0, hash);
            ResourceLocation baseLocation = getModelLocation(base);
            if (baseLocation == null) return null;
            ResourceLocation variant = siblingOf(baseLocation, fileNameOf(base) + name.substring(hash + 1) + ".jem");
            return resourceExists(variant) ? variant : null;
        }

        String namespace = namespaceOf(name);
        String file = fileNameOf(name);
        ResourceLocation best = null;
        int bestIndex = Integer.MIN_VALUE;
        for (String dir : PRIMARY_DIRS) {
            for (String sub : new String[]{file + "/", ""}) {
                ResourceLocation location;
                try {
                    location = new ResourceLocation(namespace, dir + sub + file + ".jem");
                } catch (Exception e) {
                    return null;
                }
                int index = packIndex(location);
                if (index != Integer.MIN_VALUE && (best == null || index > bestIndex)) {
                    best = location;
                    bestIndex = index;
                }
            }
        }
        if (best != null) return best;

        for (String dir : LEGACY_DIRS) {
            ResourceLocation location = new ResourceLocation(namespace, dir + file + ".jem");
            if (resourceExists(location)) return location;
        }
        return null;
    }

    private static int packIndex(ResourceLocation location) {
        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location)) {
            return packOrder().indexOf(resource.getResourcePackName());
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private static List<String> packOrder() {
        List<String> order = packOrder;
        if (order == null) {
            order = new ArrayList<>();
            for (ResourcePackRepository.Entry entry : Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries()) {
                order.add(entry.getResourcePackName());
            }
            packOrder = order;
        }
        return order;
    }

    @Nullable
    public static CEMAnimator getAnimator(String name) {
        CEMAnimator cached = animatorCache.get(name);
        if (cached != null) {
            return cached;
        }
        if (noAnimatorNames.contains(name)) {
            return null;
        }

        CEMModel model = getModel(name);
        if (model == null || model.animations.isEmpty()) {
            noAnimatorNames.add(name);
            return null;
        }

        CEMAnimator animator = new CEMAnimator(model, name);
        animatorCache.put(name, animator);
        return animator;
    }

    @Nullable
    public static CEMModelWrapper peekWrapper(String name) {
        return wrapperCache.get(name);
    }

    @Nullable
    public static CEMModelWrapper getWrapper(ModelBase vanillaModel, String name) {
        CEMModelWrapper cached = wrapperCache.get(name);
        if (cached != null) {
            return cached;
        }
        if (noWrapperNames.contains(name)) {
            return null;
        }

        CEMModel model = getModel(name);
        if (model == null) {
            noWrapperNames.add(name);
            return null;
        }

        CEMModelWrapper wrapper = new CEMModelWrapper(model, vanillaModel, name);
        wrapperCache.put(name, wrapper);
        return wrapper;
    }

    @Nullable
    public static CEMRenderState peekEntityState(Entity entity) {
        return entityStates.get((long) entity.getEntityId());
    }

    public static CEMRenderState getEntityState(Entity entity) {
        long id = entity.getEntityId();
        if (CEMRenderHooks.isRenderingInGui()) id = -1L - id;
        return entityStates.computeIfAbsent(id, _ -> new CEMRenderState());
    }

    private static final Map<TileEntity, CEMRenderState> tileStates = new WeakHashMap<>();
    private static final Map<String, CEMRenderState> sharedStates = new HashMap<>();

    public static CEMRenderState getTileState(TileEntity tile) {
        return tileStates.computeIfAbsent(tile, _ -> new CEMRenderState());
    }

    public static CEMRenderState getSharedState(String key) {
        return sharedStates.computeIfAbsent(key, _ -> new CEMRenderState());
    }

    @Nullable
    public static CEMRenderState peekTileState(TileEntity tile) {
        return tileStates.get(tile);
    }

    @Nullable
    public static String getModelNameForEntity(Entity entity) {
        if (CEMApiState.forcesVanilla(entity)) return null;
        String base = baseModelNameForEntity(entity);
        return base != null ? CEMRandomModels.select(base, entity) : null;
    }

    @Nullable
    private static String baseModelNameForEntity(Entity entity) {
        if (entity instanceof AbstractClientPlayer) {
            boolean slim = "slim".equals(((AbstractClientPlayer) entity).getSkinType());
            if (slim && getModel("player_slim") != null) return "player_slim";
            return getModel("player") != null ? "player" : null;
        }
        if (entity instanceof EntityBoat boat) {
            return firstExisting(boat.getBoatType().getName() + "_boat", "boat");
        }
        if (entity instanceof EntityArmorStand stand && stand.isSmall()) {
            String small = firstExisting("armor_stand_small");
            if (small != null) return small;
        }
        String key = entityKey(entity);
        if (key == null) return null;

        boolean baby = entity instanceof EntityLivingBase living && !(entity instanceof EntityArmorStand) && living.isChild();
        String cacheKey = baby ? key + "|baby" : key;
        String cached = resolvedModelNames.get(cacheKey);
        if (cached != null) return cached.isEmpty() ? null : cached;

        String[] candidates = PackConverter.entityModels(key);

        if (baby) {
            for (String candidate : candidates) {
                String babyName = candidate + "_baby";
                if (getModel(babyName) != null) {
                    resolvedModelNames.put(cacheKey, babyName);
                    return babyName;
                }
            }
        }
        for (String candidate : candidates) {
            if (getModel(candidate) != null) {
                resolvedModelNames.put(cacheKey, candidate);
                return candidate;
            }
        }
        resolvedModelNames.put(cacheKey, NO_MODEL);
        return null;
    }

    @Nullable
    public static String firstExisting(String... candidates) {
        for (String candidate : candidates) {
            if (getModel(candidate) != null) return candidate;
        }
        return null;
    }

    @Nullable
    public static String getModelNameForTile(TileEntity tile) {
        if (CEMApiState.forcesVanilla(tile)) return null;
        String base = baseModelNameForTile(tile);
        return base != null ? CEMRandomModels.select(base, tile) : null;
    }

    @Nullable
    private static String baseModelNameForTile(TileEntity tile) {
        if (tile instanceof TileEntityChest chest) {
            boolean trapped = chest.getChestType() == BlockChest.Type.TRAP;
            boolean large = chest.adjacentChestXPos != null || chest.adjacentChestZPos != null;
            if (large) return trapped ? firstExisting("trapped_chest_large", "chest_large") : firstExisting("chest_large");
            return trapped ? firstExisting("trapped_chest", "chest") : firstExisting("chest");
        }
        if (tile instanceof TileEntityEnderChest) return firstExisting("ender_chest", "chest");
        if (tile instanceof TileEntityBed) return firstExisting("bed");
        if (tile instanceof TileEntityBanner) {
            boolean wall = tile.hasWorld() && tile.getBlockType() == Blocks.WALL_BANNER;
            return wall ? firstExisting("wall_banner", "banner") : firstExisting("banner");
        }
        if (tile instanceof TileEntitySign) {
            boolean wall = tile.hasWorld() && tile.getBlockType() == Blocks.WALL_SIGN;
            return wall ? firstExisting("wall_sign", "sign") : firstExisting("sign");
        }
        if (tile instanceof TileEntityEnchantmentTable) return firstExisting("enchanting_book", "book");
        if (tile instanceof TileEntityShulkerBox box) {
            EnumDyeColor color = box.getColor();
            return color != null ? firstExisting(color.getName() + "_shulker_box", "shulker_box") : firstExisting("shulker_box");
        }
        String key = TILE_KEYS.computeIfAbsent(tile.getClass(), CEMManager::tileKey);
        return key.isEmpty() ? null : firstExisting(key);
    }

    private static final Map<Class<?>, String> TILE_KEYS = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private static String tileKey(Class<?> type) {
        ResourceLocation id = TileEntity.getKey((Class<? extends TileEntity>) type);
        if (id == null) return NO_MODEL;
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    @Nullable
    public static String getModelNameForSkull(int skullType) {
        return switch (skullType) {
            case 0 -> firstExisting("head_skeleton");
            case 1 -> firstExisting("head_wither_skeleton", "head_skeleton");
            case 2 -> firstExisting("head_zombie");
            case 3 -> firstExisting("head_player");
            case 4 -> firstExisting("head_creeper");
            case 5 -> firstExisting("head_dragon");
            default -> null;
        };
    }

    public static final class SecondaryBinding {
        public final Map<ModelRenderer, CEMModelRenderer> replacements = new IdentityHashMap<>();
        public final Map<ModelRenderer, CEMModelRenderer> mirrors = new IdentityHashMap<>();
        public final Set<ModelRenderer> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
        public CEMModelWrapper wrapper;
        public String modelName;
        public long lastFrame = -1L;
        public long lastEntity = Long.MIN_VALUE;
    }

    private static final Map<ModelBase, Map<String, SecondaryBinding>> secondaryBindings = new WeakHashMap<>();

    public static SecondaryBinding getSecondaryBinding(ModelBase model, String mainName, CEMModelWrapper mainWrapper,
                                                       @Nullable EntityLivingBase entity) {
        String secondaryName = getSecondaryModelName(mainName, model, entity);
        if (secondaryName != null && entity != null) secondaryName = CEMRandomModels.select(secondaryName, entity);

        Map<String, SecondaryBinding> byName = secondaryBindings.computeIfAbsent(model, _ -> new HashMap<>());
        String key = mainName + "|" + secondaryName;
        SecondaryBinding binding = byName.get(key);
        if (binding != null) return binding;

        binding = new SecondaryBinding();
        CEMModelWrapper wrapper = secondaryName != null ? getWrapper(model, secondaryName) : null;

        if (wrapper != null) {
            binding.wrapper = wrapper;
            binding.modelName = secondaryName;
            for (Map.Entry<String, ModelRenderer> entry : CEMPartMapping.mapParts(model, secondaryName).entrySet()) {
                CEMModelRenderer r = wrapper.getPartRenderer(entry.getKey());
                if (r == null || r.getCemPart().parent != null) continue;
                r.setVanillaPart(entry.getValue());
                binding.replacements.put(entry.getValue(), r);
            }
            for (ModelRenderer r : model.boxList) {
                if (r instanceof CEMModelRenderer || binding.replacements.containsKey(r)) continue;
                binding.hidden.add(r);
            }
        }
        if (mainWrapper != null && (model instanceof ModelBiped || model instanceof ModelVillager)) {
            for (Map.Entry<String, ModelRenderer> entry : CEMPartMapping.mapParts(model, mainName).entrySet()) {
                CEMModelRenderer r = mainWrapper.getPartRenderer(entry.getKey());
                if (r != null && r.getCemPart().parent == null) binding.mirrors.put(entry.getValue(), r);
            }
        }

        byName.put(key, binding);
        DatariumMain.LOGGER.info("[CEM] secondary {} -> {} (wrapper={} bound={} hidden={} mirrors={})",
                mainName, secondaryName, wrapper != null, binding.replacements.size(), binding.hidden.size(), binding.mirrors.size());
        return binding;
    }

    @Nullable
    private static String getSecondaryModelName(String mainName, ModelBase layerModel, @Nullable EntityLivingBase entity) {
        String base = mappingName(mainName);
        boolean baby = entity != null && entity.isChild();
        if (layerModel instanceof ModelElytra) {
            return base.startsWith("player") ? firstExisting("elytra") : null;
        }
        if (CEMArmorModels.isVanillaLayerModel(layerModel)) {
            return CEMArmorModels.modelName(base, CEMArmorModels.renderingInnerLayer(), baby);
        }
        return switch (base) {
            case "sheep" -> layerName("sheep", "wool", baby);
            case "slime" -> layerName("slime", "outer", baby);
            case "stray" -> layerName("stray", "outer", baby);
            case "creeper" -> layerName("creeper", "charge", baby);
            case "pig" -> layerName("pig", "saddle", baby);
            case "wither" -> layerName("wither", "armor", baby);
            case "llama" -> layerName("llama", "decor", baby);
            case "horse", "donkey", "mule", "zombie_horse", "skeleton_horse" -> layerName("horse", "armor", baby);
            default -> null;
        };
    }

    private static String layerName(String mob, String layer, boolean baby) {
        String adult = mob + "_" + layer;
        if (!baby) return adult;
        String babyName = firstExisting(mob + "_baby_" + layer);
        return babyName != null ? babyName : adult;
    }

    public static void ensureSecondaryFrame(SecondaryBinding binding, CEMRenderState state) {
        if (binding.wrapper == null) return;
        long entityId = state.context.getEntityId();
        if (binding.lastFrame == frameCounter && binding.lastEntity == entityId) return;
        binding.lastFrame = frameCounter;
        binding.lastEntity = entityId;

        // Follow the owner's LOD decision (CEMThrottle) so layers hold their pose in step with the body.
        Map<String, CEMPartTransform> transforms = state.secondaryTransforms.computeIfAbsent(binding.modelName, _ -> new HashMap<>());
        binding.wrapper.clearTransforms();
        if (state.animateThisFrame) {
            for (CEMPartTransform t : transforms.values()) t.reset();
            CEMAnimator animator = getAnimator(binding.modelName);
            if (animator != null) {
                CEMRenderHooks.setActiveSecondaryWrapper(binding.wrapper);
                try {
                    animator.evaluate(state.context, transforms);
                } finally {
                    CEMRenderHooks.setActiveSecondaryWrapper(null);
                }
            }
        }
        binding.wrapper.applyTransforms(transforms);
    }

    private static float shulkerLidYaw;

    /**
     * 1.13+ encodes horse grazing/rearing in neck.y and Fresh Animations reads it back through
     * neck.ty. 1.12.2 keeps neck.rotationPointY constant, so synthesise the modern value.
     * FA's thresholds: neutral 4, fully eating 11 ((ty-4)/7), fully rearing -4 (-(ty-4)/8).
     */
    public static void adaptVanillaState(EntityLivingBase entity, Map<String, ModelRenderer> parts, float partialTicks) {
        if (parts == null) return;

        if (entity instanceof AbstractHorse horse) {
            ModelRenderer neck = parts.get("neck");
            if (neck != null) {
                neck.rotationPointY = 4.0F + 7.0F * horse.getGrassEatingAmount(partialTicks)
                        - 8.0F * horse.getRearingAmount(partialTicks);
            }
        } else if (entity instanceof EntityShulker shulker) {
            ModelRenderer lid = parts.get("lid");
            if (lid != null) {
                if (shulker.getClientPeekAmount(partialTicks) > 0.3F) shulkerLidYaw = lid.rotateAngleY;
                else lid.rotateAngleY = shulkerLidYaw;
            }
        } else if (entity instanceof AbstractIllager illager) {
            boolean crossed = illager.getArmPose() == AbstractIllager.IllagerArmPose.CROSSED;
            datarium$setVisible(parts.get("arms"), crossed);
            datarium$setVisible(parts.get("right_arm"), !crossed);
            datarium$setVisible(parts.get("left_arm"), !crossed);
        } else if (entity instanceof EntityPlayer) {
            boolean sneaking = entity.isSneaking();
            datarium$setPivotY(parts, sneaking ? 3.2F : 0.0F, "body", "jacket");
            datarium$setPivotY(parts, sneaking ? 4.2F : 0.0F, "head", "headwear");
            datarium$setPivotY(parts, sneaking ? 5.2F : 2.0F, "right_arm", "left_arm", "right_sleeve", "left_sleeve");
            datarium$setPivotY(parts, sneaking ? 12.2F : 12.0F, "right_leg", "left_leg", "right_pants", "left_pants");
        }
    }

    public static void adaptGenericState(Entity entity, @Nullable Map<String, ModelRenderer> parts, float partialTicks) {
        if (parts == null || !(entity instanceof EntityBoat boat)) return;
        datarium$poseBoatPaddle(boat, parts.get("paddle_left"), 0, partialTicks);
        datarium$poseBoatPaddle(boat, parts.get("paddle_right"), 1, partialTicks);
    }

    private static void datarium$poseBoatPaddle(EntityBoat boat, @Nullable ModelRenderer paddle, int side, float partialTicks) {
        if (paddle == null) return;
        float rowing = boat.getRowingTime(side, partialTicks);
        paddle.rotateAngleX = (float) MathHelper.clampedLerp(-1.0471975803375244D, -0.2617993950843811D, (MathHelper.sin(-rowing) + 1.0F) / 2.0F);
        paddle.rotateAngleY = (float) MathHelper.clampedLerp(-(Math.PI / 4D), Math.PI / 4D, (MathHelper.sin(-rowing + 1.0F) + 1.0F) / 2.0F);
        if (side == 1) paddle.rotateAngleY = (float) Math.PI - paddle.rotateAngleY;
    }

    private static void datarium$setPivotY(Map<String, ModelRenderer> parts, float y, String... names) {
        for (String name : names) datarium$setPivotY(parts.get(name), y);
    }

    private static void datarium$setPivotY(@Nullable ModelRenderer part, float y) {
        if (part != null) part.rotationPointY = y;
    }

    /** { bodyScale, bodyYOffset } from modern AgeableListModel, or null to leave vanilla alone. */
    @Nullable
    public static float[] getBabyTransform(@Nullable String modelName) {
        if (modelName == null) return null;
        return switch (mappingName(modelName)) {
            case "horse", "donkey", "mule", "zombie_horse", "skeleton_horse" -> new float[]{0.5F, 20.0F};
            default -> null;
        };
    }

    private static void datarium$setVisible(@Nullable ModelRenderer part, boolean visible) {
        if (part != null) part.showModel = visible;
    }

    /** { scale, yOffset in model units } for mobs 1.12.2 shrinks and 1.13+ does not. */
    @Nullable
    public static float[] getRenderAdjust(@Nullable String modelName) {
        return modelName != null && "vex".equals(mappingName(modelName)) ? new float[]{2.5F, 1.5F} : null;
    }

    /** 1.12.2 parks the shulker body at 180°, 1.13+ parks it at 0°. */
    public static float getHeadYawOffset(@Nullable String modelName) {
        return modelName != null && "shulker".equals(mappingName(modelName)) ? 180.0F : 0.0F;
    }

    public static final int AUX_ARMOR_INNER = 1;
    public static final int AUX_ARMOR_OUTER = 2;
    public static final int AUX_CAPE = 3;
    public static final int AUX_SHOULDER_LEFT = 4;
    public static final int AUX_SHOULDER_RIGHT = 5;
    public static final int AUX_SHIELD = 6;

    /** Separate animation state for models that render alongside an entity but outside its main session. */
    public static CEMRenderState getAuxState(Entity entity, int channel) {
        long key = ((long) channel << 40) + (entity.getEntityId() & 0xFFFFFFFFL);
        return entityStates.computeIfAbsent(key, _ -> new CEMRenderState());
    }

    public static CEMRenderState getArmorState(EntityLivingBase entity, boolean inner) {
        return getAuxState(entity, inner ? AUX_ARMOR_INNER : AUX_ARMOR_OUTER);
    }

    @Nullable
    public static String armorEntityName(Entity entity) {
        if (entity instanceof AbstractClientPlayer player) return "slim".equals(player.getSkinType()) ? "player_slim" : "player";
        if (entity instanceof EntityArmorStand stand) return stand.isSmall() ? "armor_stand_small" : "armor_stand";
        String key = entityKey(entity);
        if (key == null) return null;
        return PackConverter.entityModels(key)[0];
    }

    @Nullable
    public static String exportNameForEntity(Class<? extends Entity> entityClass) {
        ResourceLocation id = EntityList.getKey(entityClass);
        if (id == null) return null;
        String key = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
        return PackConverter.entityModels(key)[0];
    }

    @Nullable
    public static String exportNameForTile(TileEntity tile) {
        if (tile instanceof TileEntityChest chest) {
            boolean trapped = chest.getChestType() == BlockChest.Type.TRAP;
            boolean large = chest.adjacentChestXPos != null || chest.adjacentChestZPos != null;
            return (trapped ? "trapped_chest" : "chest") + (large ? "_large" : "");
        }
        if (tile instanceof TileEntityEnderChest) return "ender_chest";
        if (tile instanceof TileEntityBed) return "bed";
        if (tile instanceof TileEntityBanner) return "banner";
        if (tile instanceof TileEntitySign) return "sign";
        if (tile instanceof TileEntityEnchantmentTable) return "enchanting_book";
        if (tile instanceof TileEntityShulkerBox) return "shulker_box";
        if (tile instanceof TileEntitySkull skull) {
            return switch (skull.getSkullType()) {
                case 0 -> "head_skeleton";
                case 1 -> "head_wither_skeleton";
                case 2 -> "head_zombie";
                case 3 -> "head_player";
                case 4 -> "head_creeper";
                case 5 -> "head_dragon";
                default -> null;
            };
        }
        ResourceLocation id = TileEntity.getKey(tile.getClass());
        if (id == null) return null;
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    public static CEMRenderState getFirstPersonState(EntityLivingBase entity) {
        return entityStates.computeIfAbsent(Long.MIN_VALUE + entity.getEntityId(), _ -> new CEMRenderState());
    }

    /**
     * 1.12.2 draws the cape from ModelPlayer.bipedCape - the same ModelBase as the body - so a
     * secondary binding cannot separate them. Fold player_cape.jem into the player model instead.
     */
    private static void mergeCompanions(String name, CEMModel model, ResourceLocation base) {
        String[] companions = COMPANIONS.get(baseName(name));
        if (companions == null) return;

        for (String companion : companions) {
            ResourceLocation loc = locateModel(companion);
            if (loc == null) {
                loc = siblingOf(base, companion + ".jem");
                if (!resourceExists(loc)) continue;
            }
            CEMModel extra = CEMModelLoader.loadJEM(loc);
            if (extra == null) continue;
            if ("player_cape".equals(companion)) {
                model.parts.removeIf(part -> part.parent == null && ("cloak".equals(part.part) || "cape".equals(part.part)));
                model.customCape = true;
            }
            model.parts.addAll(extra.parts);
            model.animations.addAll(extra.animations);
        }
        model.indexParts();
    }
}