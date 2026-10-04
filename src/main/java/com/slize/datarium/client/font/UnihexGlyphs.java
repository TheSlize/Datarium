package com.slize.datarium.client.font;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class UnihexGlyphs implements GlyphSource {
    private final Int2ObjectOpenHashMap<byte[]> glyphs = new Int2ObjectOpenHashMap<>();
    private final List<int[]> overrides;

    public UnihexGlyphs(InputStream in, List<int[]> overrides) throws IOException {
        this.overrides = overrides;

        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (!entry.getName().endsWith(".hex")) continue;

            BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                this.readLine(line);
            }
        }
    }

    private void readLine(String line) {
        int split = line.indexOf(':');
        int length = line.length() - split - 1;
        if (split <= 0 || length != 32 && length != 64 && length != 96 && length != 128) return;

        int codePoint;
        try {
            codePoint = Integer.parseInt(line.substring(0, split), 16);
        } catch (NumberFormatException e) {
            return;
        }

        byte[] rows = new byte[length / 2];
        for (int i = 0; i < rows.length; ++i) {
            int index = split + 1 + i * 2;
            rows[i] = (byte) (Character.digit(line.charAt(index), 16) << 4 | Character.digit(line.charAt(index + 1), 16));
        }
        this.glyphs.put(codePoint, rows);
    }

    @Override
    public boolean has(int codePoint) {
        return this.glyphs.containsKey(codePoint);
    }

    @Override
    public BitmapGlyph bake(int codePoint, GlyphAtlas atlas) {
        byte[] rows = this.glyphs.get(codePoint);
        int stride = rows.length / 16;
        int bits = stride * 8;
        int left = bits;
        int right = -1;

        int[] override = null;
        for (int[] range : this.overrides) {
            if (codePoint >= range[0] && codePoint <= range[1]) {
                override = range;
                break;
            }
        }

        if (override != null) {
            left = override[2];
            right = override[3];
        } else {
            for (int x = 0; x < bits; ++x) {
                for (int y = 0; y < 16; ++y) {
                    if ((rows[y * stride + x / 8] >> 7 - x % 8 & 1) != 0) {
                        left = Math.min(left, x);
                        right = x;
                        break;
                    }
                }
            }
            if (right < 0) {
                left = 0;
                right = bits;
            }
        }

        int width = right - left + 1;
        float advance = (float) (width / 2 + 1);
        if (width <= 0) {
            return BitmapGlyph.space(advance);
        }

        int[] pixels = new int[width * 16];
        boolean empty = true;
        for (int y = 0; y < 16; ++y) {
            for (int x = 0; x < width; ++x) {
                int column = left + x;
                boolean set = column >= 0 && column < bits && (rows[y * stride + column / 8] >> 7 - column % 8 & 1) != 0;
                pixels[y * width + x] = set ? 0xFFFFFFFF : 0xFFFFFF;
                empty &= !set;
            }
        }
        if (empty) {
            return BitmapGlyph.space(advance);
        }

        return atlas.bake(pixels, width, 16, false, 0.0F, 7.0F, 2.0F, advance, 0.5F);
    }
}
