package com.slize.datarium.client.font;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Set;

public final class TrueTypeGlyphs implements GlyphSource {
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private static final int TAG_TTCF = 0x74746366;
    private static final int TAG_HEAD = 0x68656164;
    private static final int TAG_HHEA = 0x68686561;

    private final Font font;
    private final float oversample;
    private final float shiftX;
    private final float shiftY;
    private final float ascent;
    private final Set<Integer> skip;

    public TrueTypeGlyphs(InputStream in, float size, float oversample, float shiftX, float shiftY, Set<Integer> skip) throws IOException, FontFormatException {
        byte[] data = in.readAllBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int base = buffer.getInt(0) == TAG_TTCF ? buffer.getInt(12) : 0;
        int tables = buffer.getShort(base + 4) & 0xFFFF;
        int unitsPerEm = 0;
        int ascent = 0;
        int descent = 0;
        for (int i = 0; i < tables; ++i) {
            int record = base + 12 + i * 16;
            int tag = buffer.getInt(record);
            int offset = buffer.getInt(record + 8);
            if (tag == TAG_HEAD) {
                unitsPerEm = buffer.getShort(offset + 18) & 0xFFFF;
            } else if (tag == TAG_HHEA) {
                ascent = buffer.getShort(offset + 4);
                descent = buffer.getShort(offset + 6);
            }
        }
        if (unitsPerEm == 0 || ascent == descent) {
            throw new FontFormatException("Invalid ttf");
        }

        float scale = size * oversample / (float) (ascent - descent);
        this.font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(data)).deriveFont(scale * (float) unitsPerEm);
        this.oversample = oversample;
        this.shiftX = shiftX * oversample;
        this.shiftY = shiftY * oversample;
        this.ascent = (float) ascent * scale;
        this.skip = skip;
    }

    @Override
    public boolean has(int codePoint) {
        return !Character.isISOControl(codePoint) && !this.skip.contains(codePoint) && this.font.canDisplay(codePoint);
    }

    @Override
    public BitmapGlyph bake(int codePoint, GlyphAtlas atlas) {
        GlyphVector vector = this.font.createGlyphVector(CONTEXT, Character.toChars(codePoint));
        float advance = (float) vector.getLogicalBounds().getWidth() / this.oversample;
        Shape outline = vector.getOutline(this.shiftX, this.shiftY);
        Rectangle2D bounds = outline.getBounds2D();
        if (bounds.isEmpty()) {
            return BitmapGlyph.space(advance);
        }

        int x = (int) Math.floor(bounds.getMinX());
        int y = (int) Math.floor(bounds.getMinY());
        int width = (int) Math.ceil(bounds.getMaxX()) - x;
        int height = (int) Math.ceil(bounds.getMaxY()) - y;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.translate(-x, -y);
        graphics.setColor(Color.WHITE);
        graphics.fill(outline);
        graphics.dispose();

        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; ++i) {
            pixels[i] |= 0xFFFFFF;
        }

        float bearingX = ((float) bounds.getMinX() + (float) x) / this.oversample;
        float bearingY = (this.ascent + (float) y + this.shiftY) / this.oversample;
        return atlas.bake(pixels, width, height, true, bearingX, 10.0F - bearingY, this.oversample, advance, 1.0F);
    }
}
