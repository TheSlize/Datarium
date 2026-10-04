package com.slize.datarium.client.cem.expr;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum CEMRenderVar {
    SHADOW_SIZE, SHADOW_OPACITY, SHADOW_OFFSET_X, SHADOW_OFFSET_Z,
    LEASH_OFFSET_X, LEASH_OFFSET_Y, LEASH_OFFSET_Z,
    LEASH_HOLDER_OFFSET_X, LEASH_HOLDER_OFFSET_Y, LEASH_HOLDER_OFFSET_Z,
    FIRE_X, FIRE_Y, FIRE_Z, FIRE_SCALE, FIRE_HEIGHT;

    private static final Map<String, CEMRenderVar> BY_NAME = new HashMap<>();

    static {
        for (CEMRenderVar v : values()) BY_NAME.put(v.name().toLowerCase(Locale.ROOT), v);
    }

    @Nullable
    public static CEMRenderVar byName(String name) {
        return BY_NAME.get(name);
    }
}
