package com.slize.datarium.util;

import com.slize.datarium.DatariumMain;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class GuiSheetComposer {
    private GuiSheetComposer() {}

    public record Composed(String pack, byte[] png) {
    }

    @Nullable
    public static Composed compose(List<IResourcePack> packs, ResourceLocation location, PackConverter.GuiSheet sheet) {
        try {
            BufferedImage base = null;
            int lowest = -1;
            boolean changed = false;
            for (int i = packs.size() - 1; i >= 0 && base == null; i--) {
                if (!packs.get(i).resourceExists(location)) continue;
                BufferedImage image = read(packs.get(i), location);
                if (image != null && image.getWidth() == image.getHeight()) {
                    base = image;
                    lowest = i;
                } else {
                    changed = true;
                }
            }
            if (base == null) return null;

            List<PackConverter.GuiSprite> sprites = sheet.sprites();
            BufferedImage[] images = new BufferedImage[sprites.size()];
            int scale = Math.max(1, (int) Math.ceil(base.getWidth() / (double) sheet.size()));
            for (int i = 0; i < images.length; i++) {
                PackConverter.GuiSprite sprite = sprites.get(i);
                ResourceLocation texture = new ResourceLocation(sprite.texture());
                for (int j = packs.size() - 1; j >= lowest && images[i] == null; j--) {
                    IResourcePack pack = packs.get(j);
                    if (PackConverter.formatOf(pack) >= PackConverter.FLATTENING && pack.resourceExists(texture)) images[i] = read(pack, texture);
                }
                if (images[i] != null) {
                    changed = true;
                    scale = Math.max(scale, (int) Math.ceil(images[i].getHeight() / (double) sprite.height()));
                }
            }
            if (!changed) return null;

            int side = sheet.size() * scale;
            BufferedImage composed = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    composed.setRGB(x, y, base.getRGB(x * base.getWidth() / side, y * base.getHeight() / side));
                }
            }
            for (int i = 0; i < images.length; i++) {
                if (images[i] != null) draw(composed, scale, sprites.get(i), images[i]);
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(composed, "png", bytes);
            return new Composed(packs.get(lowest).getPackName(), bytes.toByteArray());
        } catch (Exception e) {
            DatariumMain.LOGGER.error("[Converter] failed to compose {}", location, e);
            return null;
        }
    }

    @Nullable
    private static BufferedImage read(IResourcePack pack, ResourceLocation location) throws IOException {
        try (InputStream in = pack.getInputStream(location)) {
            return read(in);
        }
    }

    @Nullable
    static BufferedImage read(InputStream in) throws IOException {
        BufferedImage image = ImageIO.read(in);
        if (image == null || image.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_GRAY) return image;

        Raster raster = image.getRaster();
        int gray = (1 << raster.getSampleModel().getSampleSize(0)) - 1;
        int opaque = raster.getNumBands() > 1 ? (1 << raster.getSampleModel().getSampleSize(1)) - 1 : 0;
        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int value = raster.getSample(x, y, 0) * 255 / gray;
                int alpha = opaque == 0 ? 255 : raster.getSample(x, y, 1) * 255 / opaque;
                converted.setRGB(x, y, alpha << 24 | value << 16 | value << 8 | value);
            }
        }
        return converted;
    }

    private static void draw(BufferedImage composed, int scale, PackConverter.GuiSprite sprite, BufferedImage image) {
        int width = sprite.width() * scale;
        int height = sprite.height() * scale;
        int border = sprite.border();
        double sourceScale = image.getHeight() / (double) sprite.height();
        double sourceWidth = image.getWidth() / sourceScale;
        for (int y = 0; y < height; y++) {
            int py = sprite.y() * scale + y;
            if (py >= composed.getHeight()) break;
            int sy = Math.min(image.getHeight() - 1, (int) ((y + 0.5) * image.getHeight() / height));
            for (int x = 0; x < width; x++) {
                int px = sprite.x() * scale + x;
                if (px >= composed.getWidth()) break;
                double u = (x + 0.5) / scale;
                if (border == 0) {
                    u = u * sourceWidth / sprite.width();
                } else if (u >= sprite.width() - border) {
                    u += sourceWidth - sprite.width();
                } else if (u > border) {
                    u = border + (u - border) * (sourceWidth - 2 * border) / (sprite.width() - 2 * border);
                }
                int argb = image.getRGB(Math.max(0, Math.min(image.getWidth() - 1, (int) (u * sourceScale))), sy);
                composed.setRGB(px, py, sprite.blend() ? blend(composed.getRGB(px, py), argb) : argb);
            }
        }
    }

    static int blend(int below, int above) {
        int alpha = above >>> 24;
        if (alpha == 0) return below;
        if (alpha == 255 || below >>> 24 == 0) return above;
        int result = Math.max(alpha, below >>> 24) << 24;
        for (int shift = 0; shift <= 16; shift += 8) {
            result |= (((above >> shift & 255) * alpha + (below >> shift & 255) * (255 - alpha)) / 255) << shift;
        }
        return result;
    }
}
