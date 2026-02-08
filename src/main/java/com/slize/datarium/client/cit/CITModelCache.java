package com.slize.datarium.client.cit;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CITModelCache {
    private static final Map<ResourceLocation, IBakedModel> cache = new ConcurrentHashMap<>();

    public static void put(ResourceLocation texture, IBakedModel model) {
        cache.put(texture, model);
    }

    public static IBakedModel get(ResourceLocation texture) {
        return cache.get(texture);
    }

    public static void clear() {
        cache.clear();
    }

    public static boolean contains(ResourceLocation texture) {
        return cache.containsKey(texture);
    }
}