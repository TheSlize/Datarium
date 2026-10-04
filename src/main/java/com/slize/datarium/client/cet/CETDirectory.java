package com.slize.datarium.client.cet;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum CETDirectory {
    DOES_NOT_EXIST(null, null),
    ETF("textures", "etf/random"),
    OLD_OPTIFINE("textures/entity", "optifine/mob"),
    OPTIFINE("textures", "optifine/random"),
    VANILLA(null, null);

    @Nullable private final String from;
    @Nullable private final String to;

    CETDirectory(@Nullable String from, @Nullable String to) {
        this.from = from;
        this.to = to;
    }

    @Nullable
    public static ResourceLocation getDirectoryVersionOf(@Nullable ResourceLocation vanilla) {
        if (vanilla == null) return null;
        CETDirectory directory = getDirectoryOf(vanilla);
        return switch (directory) {
            case DOES_NOT_EXIST -> null;
            case VANILLA -> vanilla;
            default -> asDirectory(vanilla, directory);
        };
    }

    public static CETDirectory getDirectoryOf(ResourceLocation vanilla) {
        Map<ResourceLocation, CETDirectory> cache = CETManager.directoryCache();
        CETDirectory value = cache.get(vanilla);
        if (value == null) {
            value = find(vanilla);
            cache.put(vanilla, value);
        }
        return value;
    }

    private static CETDirectory find(ResourceLocation vanilla) {
        String path = vanilla.getPath();
        if (path.contains("etf/random/entity") && CETUtils.exists(vanilla)) return ETF;
        if (path.contains("optifine/random/entity") && CETUtils.exists(vanilla)) return OPTIFINE;
        if (path.contains("optifine/mob") && CETUtils.exists(vanilla)) return OLD_OPTIFINE;

        Map<String, CETDirectory> byPack = new HashMap<>();
        List<String> packs = new ArrayList<>();
        for (CETDirectory directory : new CETDirectory[]{VANILLA, OLD_OPTIFINE, OPTIFINE, ETF}) {
            String pack = CETUtils.packOf(asDirectory(vanilla, directory));
            if (pack != null) {
                byPack.put(pack, directory);
                packs.add(pack);
            }
        }
        if (packs.isEmpty()) return DOES_NOT_EXIST;
        if (packs.size() == 1) return byPack.get(packs.get(0));
        String best = CETUtils.highestPackOf(packs);
        return best != null ? byPack.get(best) : VANILLA;
    }

    public static ResourceLocation asDirectory(ResourceLocation identifier, CETDirectory directory) {
        if (directory.from == null || directory.to == null) return identifier;
        return new ResourceLocation(identifier.getNamespace(), identifier.getPath().replace(directory.from, directory.to));
    }
}
