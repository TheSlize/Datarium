package com.slize.datarium.client.cit;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class CITModelCache {

    private record Key(@Nullable ResourceLocation model, List<ResourceLocation> textures, IBakedModel base) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Key(ResourceLocation m, List<ResourceLocation> t, IBakedModel b)
                    && Objects.equals(model, m) && textures.equals(t) && base == b;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Objects.hashCode(model) + textures.hashCode()) + System.identityHashCode(base);
        }
    }

    private static final Map<Key, Optional<IBakedModel>> cache = new HashMap<>();

    @Nullable
    public static IBakedModel get(@Nullable ResourceLocation model, List<ResourceLocation> textures, IBakedModel base, Supplier<IBakedModel> factory) {
        Key key = new Key(model, textures, base);
        Optional<IBakedModel> cached = cache.get(key);
        if (cached == null) {
            cached = Optional.ofNullable(factory.get());
            cache.put(key, cached);
        }
        return cached.orElse(null);
    }

    public static void clear() {
        cache.clear();
    }
}
