package com.slize.datarium.client.font;

public interface GlyphSource {
    boolean has(int codePoint);

    BitmapGlyph bake(int codePoint, GlyphAtlas atlas);
}
