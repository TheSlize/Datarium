package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CEMManager {
    private static final Map<String, CEMModel> modelCache = new ConcurrentHashMap<>();
    private static final Map<String, CEMAnimator> animatorCache = new ConcurrentHashMap<>();
    private static final Map<String, CEMModelWrapper> wrapperCache = new ConcurrentHashMap<>();
    private static final Map<Long, CEMRenderState> entityStates = new ConcurrentHashMap<>();

    private static final Map<String, String> ENTITY_MODEL_MAP = new HashMap<>();

    static {
        ENTITY_MODEL_MAP.put("EntityVillager", "villager");
        ENTITY_MODEL_MAP.put("EntityZombie", "zombie");
        ENTITY_MODEL_MAP.put("EntityZombieVillager", "zombie_villager");
        ENTITY_MODEL_MAP.put("EntitySkeleton", "skeleton");
        ENTITY_MODEL_MAP.put("EntityWitherSkeleton", "wither_skeleton");
        ENTITY_MODEL_MAP.put("EntityStray", "stray");
        ENTITY_MODEL_MAP.put("EntityCreeper", "creeper");
        ENTITY_MODEL_MAP.put("EntitySpider", "spider");
        ENTITY_MODEL_MAP.put("EntityCaveSpider", "cave_spider");
        ENTITY_MODEL_MAP.put("EntityEnderman", "enderman");
        ENTITY_MODEL_MAP.put("EntityPig", "pig");
        ENTITY_MODEL_MAP.put("EntityCow", "cow");
        ENTITY_MODEL_MAP.put("EntityMooshroom", "mooshroom");
        ENTITY_MODEL_MAP.put("EntitySheep", "sheep");
        ENTITY_MODEL_MAP.put("EntityChicken", "chicken");
        ENTITY_MODEL_MAP.put("EntityWolf", "wolf");
        ENTITY_MODEL_MAP.put("EntityOcelot", "ocelot");
        ENTITY_MODEL_MAP.put("EntityRabbit", "rabbit");
        ENTITY_MODEL_MAP.put("EntityHorse", "horse");
        ENTITY_MODEL_MAP.put("EntityDonkey", "donkey");
        ENTITY_MODEL_MAP.put("EntityMule", "mule");
        ENTITY_MODEL_MAP.put("EntityZombieHorse", "zombie_horse");
        ENTITY_MODEL_MAP.put("EntitySkeletonHorse", "skeleton_horse");
        ENTITY_MODEL_MAP.put("EntityLlama", "llama");
        ENTITY_MODEL_MAP.put("EntityBat", "bat");
        ENTITY_MODEL_MAP.put("EntitySquid", "squid");
        ENTITY_MODEL_MAP.put("EntityGhast", "ghast");
        ENTITY_MODEL_MAP.put("EntityBlaze", "blaze");
        ENTITY_MODEL_MAP.put("EntityMagmaCube", "magma_cube");
        ENTITY_MODEL_MAP.put("EntitySlime", "slime");
        ENTITY_MODEL_MAP.put("EntityWitch", "witch");
        ENTITY_MODEL_MAP.put("EntityIronGolem", "iron_golem");
        ENTITY_MODEL_MAP.put("EntitySnowman", "snowman");
        ENTITY_MODEL_MAP.put("EntityGuardian", "guardian");
        ENTITY_MODEL_MAP.put("EntityElderGuardian", "elder_guardian");
        ENTITY_MODEL_MAP.put("EntityShulker", "shulker");
        ENTITY_MODEL_MAP.put("EntityVex", "vex");
        ENTITY_MODEL_MAP.put("EntityEvoker", "evoker");
        ENTITY_MODEL_MAP.put("EntityVindicator", "vindicator");
        ENTITY_MODEL_MAP.put("EntityIllusionIllager", "illusioner");
        ENTITY_MODEL_MAP.put("EntityParrot", "parrot");
        ENTITY_MODEL_MAP.put("EntityPolarBear", "polar_bear");
        ENTITY_MODEL_MAP.put("EntityEnderDragon", "dragon");
        ENTITY_MODEL_MAP.put("EntityWither", "wither");
        ENTITY_MODEL_MAP.put("EntityGiantZombie", "giant");
    }

    public static void invalidate() {
        modelCache.clear();
        animatorCache.clear();
        wrapperCache.clear();
    }

    @Nullable
    public static CEMModel getModel(String name) {
        if (modelCache.containsKey(name)) {
            return modelCache.get(name);
        }

        ResourceLocation[] locations = {
                new ResourceLocation("minecraft", "optifine/cem/" + name + ".jem"),
                new ResourceLocation("minecraft", "citresewn/cem/" + name + ".jem"),
                new ResourceLocation("minecraft", "mcpatcher/cem/" + name + ".jem"),
                new ResourceLocation("minecraft", "cem/" + name + ".jem")
        };

        for (ResourceLocation loc : locations) {
            try {
                Minecraft.getMinecraft().getResourceManager().getResource(loc);
                CEMModel model = CEMModelLoader.loadJEM(loc);
                if (model != null) {
                    modelCache.put(name, model);
                    DatariumMain.LOGGER.info("[CEM] Loaded model: {} from {}", name, loc);
                    return model;
                }
            } catch (Exception ignored) {
            }
        }

        modelCache.put(name, null);
        return null;
    }

    @Nullable
    public static CEMAnimator getAnimator(String name) {
        if (animatorCache.containsKey(name)) {
            return animatorCache.get(name);
        }

        CEMModel model = getModel(name);
        if (model == null || model.animations.isEmpty()) {
            animatorCache.put(name, null);
            return null;
        }

        CEMAnimator animator = new CEMAnimator(model);
        animatorCache.put(name, animator);
        DatariumMain.LOGGER.info("[CEM] Created animator for: {} with {} animation blocks", name, model.animations.size());
        return animator;
    }

    @Nullable
    public static CEMModelWrapper getWrapper(ModelBase vanillaModel, String name) {
        if (wrapperCache.containsKey(name)) {
            return wrapperCache.get(name);
        }

        CEMModel model = getModel(name);
        if (model == null) {
            wrapperCache.put(name, null);
            return null;
        }

        CEMModelWrapper wrapper = new CEMModelWrapper(model, vanillaModel);
        wrapperCache.put(name, wrapper);
        return wrapper;
    }

    public static CEMRenderState getEntityState(Entity entity) {
        return entityStates.computeIfAbsent((long) entity.getEntityId(), k -> new CEMRenderState());
    }

    @Nullable
    public static String getModelNameForEntity(Entity entity) {
        String entityName = entity.getClass().getSimpleName();
        return ENTITY_MODEL_MAP.get(entityName);
    }

}