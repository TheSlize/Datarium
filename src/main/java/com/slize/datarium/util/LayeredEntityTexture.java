package com.slize.datarium.util;

import com.slize.datarium.DatariumMain;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class LayeredEntityTexture extends AbstractTexture {
    private static final int UNITS = 64;

    private final List<String> layers;
    private final int[][] moves;

    public LayeredEntityTexture(List<String> layers, int[][] moves) {
        this.layers = layers;
        this.moves = moves;
    }

    @Override
    public void loadTexture(IResourceManager resourceManager) {
        this.deleteGlTexture();
        List<BufferedImage> images = new ArrayList<>();
        for (String layer : layers) {
            try (IResource resource = resourceManager.getResource(new ResourceLocation(layer)); InputStream in = resource.getInputStream()) {
                BufferedImage image = GuiSheetComposer.read(in);
                if (image != null) images.add(image);
            } catch (Exception e) {
                DatariumMain.LOGGER.error("[Converter] failed to load layer {}", layer, e);
            }
        }
        if (!images.isEmpty()) TextureUtil.uploadTextureImage(this.getGlTextureId(), compose(images, moves));
    }

    static BufferedImage compose(List<BufferedImage> images, int[][] moves) {
        BufferedImage base = images.get(0);
        int width = base.getWidth();
        for (BufferedImage image : images) width = Math.max(width, image.getWidth());
        int height = width * base.getHeight() / base.getWidth();

        int[] pixels = new int[width * height];
        for (BufferedImage image : images) {
            for (int y = 0; y < height; y++) {
                int sy = Math.min(image.getHeight() - 1, y * image.getHeight() / height);
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    pixels[index] = GuiSheetComposer.blend(pixels[index], image.getRGB(x * image.getWidth() / width, sy));
                }
            }
        }

        int[] source = moves.length == 0 ? pixels : pixels.clone();
        for (int[] move : moves) {
            int w = move[2] * width / UNITS;
            int h = move[3] * width / UNITS;
            for (int y = 0; y < h; y++) {
                int from = (move[1] * width / UNITS + y) * width + move[0] * width / UNITS;
                int to = (move[5] * width / UNITS + y) * width + move[4] * width / UNITS;
                if (from + w <= source.length && to + w <= pixels.length) System.arraycopy(source, from, pixels, to, w);
            }
        }

        BufferedImage composed = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        composed.setRGB(0, 0, width, height, pixels, 0, width);
        return composed;
    }
}
