package com.slize.datarium.client.cet;

import com.slize.datarium.client.cet.player.CETPlayerTexture;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class CETManager {
    public static final String SKIN_NAMESPACE = "datarium_cet_skin";

    private static final Map<ResourceLocation, CETTexture> TEXTURE_CACHE = new HashMap<>();
    private static final Map<ResourceLocation, CETDirectory> DIRECTORY_CACHE = new HashMap<>();
    private static final Map<ResourceLocation, CETTextureVariator> VARIATORS = new HashMap<>();
    private static final CETLru.Ints LAST_SUFFIX = new CETLru.Ints();
    private static final CETLru.Ints LAST_RULE = new CETLru.Ints();
    private static final Map<String, Integer> LIGHT_OVERRIDES = new HashMap<>();
    private static final Set<String> IGNORE_PARTICLES = new HashSet<>();
    private static final Map<String, RenderLayerOverride> LAYER_OVERRIDES = new HashMap<>();
    private static final CETLru<CETPlayerTexture> PLAYER_TEXTURES = new CETLru<>(256);
    @Nullable private static Set<String> emissiveSuffixes;

    private CETManager() {}

    public enum RenderLayerOverride {
        TRANSLUCENT, TRANSLUCENT_CULL, END, OUTLINE
    }

    public static void reset() {
        TEXTURE_CACHE.clear();
        DIRECTORY_CACHE.clear();
        VARIATORS.clear();
        LAST_SUFFIX.clear();
        LAST_RULE.clear();
        LIGHT_OVERRIDES.clear();
        IGNORE_PARTICLES.clear();
        LAYER_OVERRIDES.clear();
        PLAYER_TEXTURES.clear();
        emissiveSuffixes = null;
        CETState.clear();
        CETUtils.resetPackOrder();
        CETNbt.invalidate();
        CETMooshroom.reset();
        CETLayout.reset();
        CETHorse.reset();
    }

    public static Set<String> emissiveSuffixes() {
        Set<String> suffixes = emissiveSuffixes;
        if (suffixes != null) return suffixes;
        suffixes = new LinkedHashSet<>();
        for (String path : new String[]{"optifine/emissive.properties", "textures/emissive.properties", "etf/emissive.properties"}) {
            for (Properties properties : CETUtils.readAllLayeredProperties(new ResourceLocation(path))) {
                for (String key : new String[]{"entities.suffix.emissive", "suffix.emissive"}) {
                    String value = properties.getProperty(key);
                    if (value != null) suffixes.add(value.trim());
                }
            }
        }
        if (CETConfig.alwaysCheckVanillaEmissiveSuffix) suffixes.add("_e");
        if (suffixes.isEmpty()) {
            CETUtils.log("no emissive suffixes found: default emissive suffix '_e' used");
            suffixes.add("_e");
        } else {
            CETUtils.log("emissive suffixes loaded: " + suffixes);
        }
        emissiveSuffixes = suffixes;
        return suffixes;
    }

    static Map<ResourceLocation, CETDirectory> directoryCache() {
        return DIRECTORY_CACHE;
    }

    public static CETLru.Ints lastSuffixes() {
        return LAST_SUFFIX;
    }

    public static CETLru.Ints lastRules() {
        return LAST_RULE;
    }

    public static int lastSuffixOf(UUID id) {
        return LAST_SUFFIX.getInt(id);
    }

    public static int lastRuleOf(UUID id) {
        return LAST_RULE.getInt(id);
    }

    public static void putTexture(ResourceLocation location, CETTexture texture) {
        TEXTURE_CACHE.put(location, texture);
    }

    public static CETTexture getNoVariation(ResourceLocation location) {
        CETTexture texture = TEXTURE_CACHE.get(location);
        if (texture == null) {
            texture = new CETTexture(location);
            TEXTURE_CACHE.put(location, texture);
        }
        return texture;
    }

    public static CETTexture getVariant(ResourceLocation vanilla, @Nullable CETSubject subject) {
        if (subject == null || subject.isGeneric() || SKIN_NAMESPACE.equals(vanilla.getNamespace())) {
            return getNoVariation(vanilla);
        }
        if (!vanilla.getPath().endsWith(".png")) {
            CETTexture horse = CETHorse.resolve(vanilla, subject);
            return horse != null ? horse : getNoVariation(vanilla);
        }
        CETTextureVariator variator = VARIATORS.get(vanilla);
        if (variator == null) {
            variator = CETTextureVariator.of(vanilla);
            VARIATORS.put(vanilla, variator);
        }
        return variator.getVariantOf(subject);
    }

    public static void grabSpecialProperties(Properties properties, @Nullable CETSubject subject) {
        if (subject == null) return;
        String key = subject.entityKey();
        if (key.isEmpty()) return;
        if (properties.containsKey("vanillaBrightnessOverride")) {
            try {
                int value = Integer.parseInt(properties.getProperty("vanillaBrightnessOverride").trim().replaceAll("\\D", ""));
                LIGHT_OVERRIDES.put(key, Math.max(0, Math.min(15, value)));
            } catch (NumberFormatException ignored) {
            }
        }
        if ("true".equals(properties.getProperty("suppressParticles"))) IGNORE_PARTICLES.add(key);
        String layer = properties.getProperty("entityRenderLayerOverride");
        if (layer != null) {
            switch (layer.trim().replace("\"", "")) {
                case "translucent" -> LAYER_OVERRIDES.put(key, RenderLayerOverride.TRANSLUCENT);
                case "translucent_cull" -> LAYER_OVERRIDES.put(key, RenderLayerOverride.TRANSLUCENT_CULL);
                case "end_portal" -> LAYER_OVERRIDES.put(key, RenderLayerOverride.END);
                case "outline" -> LAYER_OVERRIDES.put(key, RenderLayerOverride.OUTLINE);
                default -> {
                }
            }
        }
    }

    public static boolean hasLightOverrides() {
        return !LIGHT_OVERRIDES.isEmpty();
    }

    @Nullable
    public static Integer lightOverride(String entityKey) {
        return LIGHT_OVERRIDES.isEmpty() ? null : LIGHT_OVERRIDES.get(entityKey);
    }

    public static boolean suppressesParticles(String entityKey) {
        return !IGNORE_PARTICLES.isEmpty() && CETConfig.canDoCustomTextures() && IGNORE_PARTICLES.contains(entityKey);
    }

    @Nullable
    public static RenderLayerOverride layerOverride(@Nullable CETSubject subject) {
        if (LAYER_OVERRIDES.isEmpty() || subject == null) return null;
        return LAYER_OVERRIDES.get(subject.entityKey());
    }

    public static CETLru<CETPlayerTexture> playerTextures() {
        return PLAYER_TEXTURES;
    }
}
