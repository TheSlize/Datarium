package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cet.CETConfig;
import com.slize.datarium.client.cet.CETSubject;
import com.slize.datarium.client.cet.CETVariantProvider;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OptiFine "random models": {@code wolf2.jem}, {@code wolf3.jem} ... next to {@code wolf.jem}, optionally
 * driven by {@code wolf.properties} in the Random Entities rule format ({@code models.N=...}), evaluated by the
 * Custom Entity Textures property engine exactly like EMF does through ETF.
 * Variant models are addressed as {@code "<base>#<index>"} so every cache keyed by model name keeps working.
 */
public final class CEMRandomModels {
    private static final int PRUNE_TICKS = 1200;

    private static final Map<String, Variants> VARIANTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Choice>> CHOICES = new HashMap<>();
    private static long lastPrune;

    private CEMRandomModels() {}

    private static final class Choice {
        String model;
        int ruleIndex;
        long usedAt;
        long updatedAt = Long.MIN_VALUE;
    }

    public static void invalidate() {
        VARIANTS.clear();
        CHOICES.clear();
    }

    public static String select(String baseName, Entity entity) {
        return select(baseName, CETSubject.of(entity));
    }

    public static String select(String baseName, @Nullable TileEntity tile) {
        if (tile == null || tile.getWorld() == null) return baseName;
        return select(baseName, CETSubject.of(tile));
    }

    private static String select(String baseName, CETSubject subject) {
        Variants variants = variants(baseName);
        if (variants.provider == null) return baseName;
        return choose(baseName, variants, subject).model;
    }

    public static int ruleIndex(Entity entity, @Nullable String modelName) {
        return ruleIndex(entity.getUniqueID(), modelName);
    }

    public static int ruleIndex(@Nullable TileEntity tile, @Nullable String modelName) {
        if (tile == null || tile.getWorld() == null) return 0;
        return ruleIndex(CETSubject.of(tile).uuid(), modelName);
    }

    private static int ruleIndex(UUID id, @Nullable String modelName) {
        Map<String, Choice> byModel = CHOICES.get(id);
        if (byModel == null || modelName == null) return 0;
        Choice choice = byModel.get(CEMManager.baseName(modelName));
        return choice != null ? choice.ruleIndex : 0;
    }

    private static Choice choose(String baseName, Variants variants, CETSubject subject) {
        World world = subject.world();
        long now = world != null ? world.getTotalWorldTime() : 0L;
        UUID id = subject.uuid();
        Map<String, Choice> byModel = CHOICES.computeIfAbsent(id, k -> new HashMap<>(4));
        Choice choice = byModel.get(baseName);
        if (choice == null) {
            choice = new Choice();
            byModel.put(baseName, choice);
        } else if (world != null && choice.updatedAt == now || !shouldUpdate(variants.provider, id, now)) {
            choice.usedAt = now;
            return choice;
        }
        choice.usedAt = now;
        choice.updatedAt = now;
        variants.lastRule = 0;
        int suffix = variants.provider.getSuffix(subject);
        choice.model = variants.modelFor(baseName, suffix);
        choice.ruleIndex = variants.lastRule;
        prune(now);
        return choice;
    }

    private static boolean shouldUpdate(CETVariantProvider provider, UUID id, long now) {
        if (!provider.entityCanUpdate(id)) return false;
        CETConfig.UpdateFrequency frequency = CETConfig.textureUpdateFrequency;
        if (frequency == CETConfig.UpdateFrequency.Never) return false;
        if (frequency == CETConfig.UpdateFrequency.Instant) return true;
        int delay = frequency.getDelay();
        return now % delay == Math.abs(id.hashCode()) % delay;
    }

    private static void prune(long now) {
        if (Math.abs(now - lastPrune) < PRUNE_TICKS) return;
        lastPrune = now;
        Iterator<Map<String, Choice>> it = CHOICES.values().iterator();
        while (it.hasNext()) {
            Map<String, Choice> byModel = it.next();
            byModel.values().removeIf(c -> Math.abs(now - c.usedAt) > PRUNE_TICKS);
            if (byModel.isEmpty()) it.remove();
        }
    }

    private static Variants variants(String baseName) {
        Variants cached = VARIANTS.get(baseName);
        if (cached != null) return cached;
        Variants loaded = Variants.load(baseName);
        VARIANTS.put(baseName, loaded);
        return loaded;
    }

    private static final class Variants {
        @Nullable final CETVariantProvider provider;
        int lastRule;

        private Variants(@Nullable CETVariantProvider provider) {
            this.provider = provider;
        }

        static Variants load(String baseName) {
            ResourceLocation base = CEMManager.getModelLocation(baseName);
            if (base == null) return new Variants(null);
            String file = CEMManager.fileNameOf(baseName);
            ResourceLocation propertiesId = CEMManager.siblingOf(base, file + ".properties");
            CETVariantProvider provider = CETVariantProvider.of(propertiesId, base, "models");
            Variants variants = new Variants(provider);
            if (provider instanceof CETVariantProvider.PropertiesProvider properties) {
                properties.setOnMeetsRuleHook((subject, rule) -> variants.lastRule = rule == null ? 0 : rule.ruleNumber);
            }
            if (provider != null) {
                DatariumMain.LOGGER.info("[CEM] random models for {}: variants {} ({} rule(s))",
                        baseName, provider.getAllSuffixes(), provider.size());
            }
            return variants;
        }

        String modelFor(String baseName, int index) {
            if (index <= 1) return baseName;
            String name = baseName + "#" + index;
            return CEMManager.getModel(name) != null ? name : baseName;
        }
    }
}
