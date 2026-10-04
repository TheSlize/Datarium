package com.slize.datarium.client.font;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public final class GlyphAtlas {
    private static final int SIZE = 512;
    private static int counter;

    private final List<Page> pages = new ArrayList<>();
    private final List<Upload> pending = new ArrayList<>();
    private Page open;

    public BitmapGlyph bake(int[] pixels, int width, int height, boolean smooth, float bearing, float ascent, float oversample, float advance, float offset) {
        int w = width + 2;
        int h = height + 2;
        if (w > SIZE || h > SIZE) {
            return BitmapGlyph.space(advance);
        }

        Page page = this.open;
        if (page != null && page.x + w > SIZE) {
            page.x = 0;
            page.y += page.rowHeight;
            page.rowHeight = 0;
        }
        if (page == null || page.y + h > SIZE) {
            page = new Page(new ResourceLocation("datarium", "font/atlas_" + counter++));
            this.pages.add(page);
            this.open = page;
        }

        int[] padded = new int[w * h];
        Arrays.fill(padded, 0xFFFFFF);
        for (int row = 0; row < height; ++row) {
            System.arraycopy(pixels, row * width, padded, (row + 1) * w + 1, width);
        }
        this.pending.add(new Upload(page, padded, page.x, page.y, w, h));

        float u0 = (float) (page.x + 1) / (float) SIZE;
        float v0 = (float) (page.y + 1) / (float) SIZE;
        float u1 = (float) (page.x + 1 + width) / (float) SIZE;
        float v1 = (float) (page.y + 1 + height) / (float) SIZE;
        page.x += w;
        page.rowHeight = Math.max(page.rowHeight, h);

        return new BitmapGlyph(page.location, bearing, ascent, (float) height / oversample, advance, (float) width / oversample, offset, smooth, u0, v0, u1, v1);
    }

    public void flush() {
        if (this.pending.isEmpty()) return;

        TextureManager manager = Minecraft.getMinecraft().getTextureManager();
        for (Upload upload : this.pending) {
            Page page = upload.page();
            if (!page.allocated) {
                page.allocated = true;
                TextureUtil.allocateTexture(page.getGlTextureId(), SIZE, SIZE);
                manager.loadTexture(page.location, page);
            }
            GlStateManager.bindTexture(page.getGlTextureId());
            TextureUtil.uploadTextureMipmap(new int[][]{upload.pixels()}, upload.width(), upload.height(), upload.x(), upload.y(), false, false);
        }
        this.pending.clear();
    }

    public void clear() {
        TextureManager manager = Minecraft.getMinecraft().getTextureManager();
        for (Page page : this.pages) {
            if (page.allocated) {
                manager.deleteTexture(page.location);
            }
        }
        this.pages.clear();
        this.pending.clear();
        this.open = null;
    }

    private record Upload(Page page, int[] pixels, int x, int y, int width, int height) {
    }

    private static final class Page extends AbstractTexture {
        private final ResourceLocation location;
        private boolean allocated;
        private int x;
        private int y;
        private int rowHeight;

        private Page(ResourceLocation location) {
            this.location = location;
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) {
        }
    }
}
