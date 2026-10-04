package com.slize.datarium.client.cet;

import com.slize.datarium.util.PackConverter;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class CETTextureVariator {

    public static CETTextureVariator of(ResourceLocation vanilla) {
        CETVariantProvider provider = providerOf(vanilla);
        if (provider != null) return new Multiple(vanilla, vanilla, provider);
        for (ResourceLocation modern : PackConverter.modernTextures(vanilla)) {
            provider = providerOf(modern);
            if (provider != null) return new Multiple(vanilla, modern, provider);
        }
        return new Singleton(vanilla);
    }

    @Nullable
    private static CETVariantProvider providerOf(ResourceLocation texture) {
        ResourceLocation propertiesId = CETUtils.replace(texture, "\\.png$", ".properties");
        return propertiesId == null ? null : CETVariantProvider.of(propertiesId, texture, "skins", "textures");
    }

    public abstract CETTexture getVariantOf(CETSubject subject);

    private static final class Singleton extends CETTextureVariator {
        private final CETTexture self;

        Singleton(ResourceLocation vanilla) {
            self = CETManager.getNoVariation(vanilla);
            if (CETConfig.logTextureDataInitialization) {
                CETUtils.log("Initializing texture for the first time: " + vanilla);
                CETUtils.log(" - no variants for: " + self);
            }
        }

        @Override
        public CETTexture getVariantOf(CETSubject subject) {
            return self;
        }
    }

    private static final class Multiple extends CETTextureVariator {
        private final CETLru.Ints entitySuffixMap = new CETLru.Ints(500);
        private final CETVariantProvider provider;
        private final Map<Integer, CETTexture> variantMap = new HashMap<>();
        private final CETTexture vanillaTexture;

        Multiple(ResourceLocation vanilla, ResourceLocation lookup, CETVariantProvider provider) {
            this.provider = provider;
            if (provider instanceof CETVariantProvider.PropertiesProvider properties) {
                properties.setOnMeetsRuleHook((subject, rule) -> {
                    if (rule == null) CETManager.lastRules().remove(subject.uuid());
                    else CETManager.lastRules().put(subject.uuid(), rule.ruleNumber);
                });
            }
            ResourceLocation directorized = CETConfig.optifinePreventBaseTextureInOptifineDirectory
                    ? null : CETDirectory.getDirectoryVersionOf(lookup);
            vanillaTexture = CETManager.getNoVariation(directorized == null ? vanilla : directorized);
            variantMap.put(1, vanillaTexture);

            boolean logging = CETConfig.logTextureDataInitialization;
            if (logging) CETUtils.log("Initializing texture for the first time: " + vanilla);
            Set<Integer> suffixes = provider.getAllSuffixes();
            suffixes.remove(0);
            suffixes.remove(1);
            for (int suffix : suffixes) {
                ResourceLocation numbered = CETUtils.addVariantNumberSuffix(lookup, suffix);
                ResourceLocation variant = numbered == null ? null : CETDirectory.getDirectoryVersionOf(numbered);
                if (logging) CETUtils.log(" - looked for variant: " + variant);
                variantMap.put(suffix, variant != null ? CETManager.getNoVariation(variant) : vanillaTexture);
            }
            if (logging) {
                CETUtils.log("Final variant map for: " + vanilla);
                variantMap.forEach((k, v) -> CETUtils.log(" - " + k + " = " + v));
            }
        }

        private CETTexture variant(int suffix) {
            CETTexture texture = variantMap.get(suffix);
            return texture != null ? texture : vanillaTexture;
        }

        private void checkIfShouldExpire(CETSubject subject, UUID id) {
            if (!provider.entityCanUpdate(id)) return;
            CETConfig.UpdateFrequency frequency = CETConfig.textureUpdateFrequency;
            switch (frequency) {
                case Never:
                    break;
                case Instant:
                    entitySuffixMap.remove(id);
                    break;
                default:
                    World world = subject.world();
                    if (world == null) break;
                    int delay = frequency.getDelay();
                    int time = (int) (world.getTotalWorldTime() % delay);
                    if (time == Math.abs(id.hashCode()) % delay) entitySuffixMap.remove(id);
            }
        }

        @Override
        public CETTexture getVariantOf(CETSubject subject) {
            UUID id = subject.uuid();
            int known = entitySuffixMap.getInt(id);
            if (known != -1) {
                checkIfShouldExpire(subject, id);
                return variant(known);
            }
            int suffix = determineNewSuffix(subject);
            entitySuffixMap.put(id, suffix);
            return variant(suffix);
        }

        private int determineNewSuffix(CETSubject subject) {
            if (CETState.isRenderingFeatures) {
                if (provider instanceof CETVariantProvider.PropertiesProvider) return provider.getSuffix(subject);
                int base = CETManager.lastSuffixOf(subject.uuid());
                if (base != -1 && variantMap.containsKey(base)) return base;
                return provider.getSuffix(subject);
            }
            int suffix = provider.getSuffix(subject);
            CETManager.lastSuffixes().put(subject.uuid(), suffix);
            return suffix;
        }
    }
}
