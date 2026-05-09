package com.slize.datarium.client.cit;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CITModelCache {

    private static final Map<Key, IBakedModel> cache = new ConcurrentHashMap<>();

    public record Key(ResourceLocation model, ResourceLocation texture) {
        @Override
            public boolean equals(Object o) {
                if (!(o instanceof Key(ResourceLocation model1, ResourceLocation texture1))) return false;
                return Objects.equals(model, model1) && Objects.equals(texture, texture1);
            }
    }

    public static void put(ResourceLocation model, ResourceLocation texture, IBakedModel baked) {
        cache.put(new Key(model, texture), baked);
    }

    public static IBakedModel get(ResourceLocation model, ResourceLocation texture) {
        return cache.get(new Key(model, texture));
    }

    public static boolean contains(ResourceLocation model, ResourceLocation texture) {
        return cache.containsKey(new Key(model, texture));
    }

    public static void clear() {
        cache.clear();
    }
}