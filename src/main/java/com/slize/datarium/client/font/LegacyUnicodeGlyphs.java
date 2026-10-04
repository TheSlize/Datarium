package com.slize.datarium.client.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

public final class LegacyUnicodeGlyphs implements GlyphSource {
    private final byte[] sizes;
    private final String template;
    private final ResourceLocation[] pages = new ResourceLocation[256];
    private final boolean[] checked = new boolean[256];

    public LegacyUnicodeGlyphs(byte[] sizes, String template) {
        this.sizes = sizes;
        this.template = template;
    }

    private ResourceLocation page(int index) {
        if (!this.checked[index]) {
            this.checked[index] = true;
            try {
                ResourceLocation id = new ResourceLocation(String.format(this.template, String.format("%02x", index)));
                ResourceLocation location = new ResourceLocation(id.getNamespace(), "textures/" + id.getPath());
                try (IResource ignored = Minecraft.getMinecraft().getResourceManager().getResource(location)) {
                    this.pages[index] = location;
                }
            } catch (Exception e) {
            }
        }
        return this.pages[index];
    }

    @Override
    public boolean has(int codePoint) {
        if (codePoint < 0 || codePoint >= this.sizes.length || codePoint > 0xFFFF) return false;

        int size = this.sizes[codePoint] & 255;
        return size != 0 && size >> 4 <= (size & 15) && this.page(codePoint >> 8) != null;
    }

    @Override
    public BitmapGlyph bake(int codePoint, GlyphAtlas atlas) {
        int size = this.sizes[codePoint] & 255;
        int left = size >> 4;
        int right = (size & 15) + 1;
        int width = right - left;
        int x = (codePoint & 15) * 16;
        int y = (codePoint >> 4 & 15) * 16;

        return new BitmapGlyph(this.page(codePoint >> 8), 0.0F, 7.0F, 8.0F, (float) (width / 2 + 1), (float) width / 2.0F, 0.5F, false, (float) (x + left) / 256.0F, (float) y / 256.0F, (float) (x + right) / 256.0F, (float) (y + 16) / 256.0F);
    }
}
