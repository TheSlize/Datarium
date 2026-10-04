package com.slize.datarium.client.font;

import net.minecraft.util.ResourceLocation;

public record BitmapGlyph(ResourceLocation texture, float bearing, float ascent, float cellHeight, float advance,
                          float width, float offset, boolean smooth, float u0, float v0, float u1, float v1) {
    public static final BitmapGlyph VANILLA = space(0.0F);

    public static BitmapGlyph space(float advance) {
        return new BitmapGlyph(null, 0.0F, 0.0F, 0.0F, advance, 0.0F, 1.0F, false, 0.0F, 0.0F, 0.0F, 0.0F);
    }
}
