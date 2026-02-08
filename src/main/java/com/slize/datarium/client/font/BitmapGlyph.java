package com.slize.datarium.client.font;

import net.minecraft.util.ResourceLocation;

public record BitmapGlyph(ResourceLocation texture, int ascent, int cellHeight, float advance, float width, float u0,
                          float v0, float u1, float v1) {
}
