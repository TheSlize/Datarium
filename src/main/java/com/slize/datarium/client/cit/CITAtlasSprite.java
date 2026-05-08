package com.slize.datarium.client.cit;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationFrame;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CITAtlasSprite extends TextureAtlasSprite {
    private final ResourceLocation actualTexturePath;

    private static final int MAX_MIPMAP_LEVELS = 5;

    private static final Field ANIMATION_METADATA_FIELD;

    private boolean standalone = false;
    private int standaloneGlTextureId = -1;

    static {
        Field field = null;
        try {
            field = TextureAtlasSprite.class.getDeclaredField("animationMetadata");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                field = TextureAtlasSprite.class.getDeclaredField("field_110982_k");
                field.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                ex.printStackTrace();
            }
        }
        ANIMATION_METADATA_FIELD = field;
    }

    public CITAtlasSprite(String spriteName, ResourceLocation actualTexturePath) {
        super(spriteName);
        this.actualTexturePath = actualTexturePath;
    }

    public boolean isStandalone() {
        return standalone;
    }

    public int getStandaloneGlTextureId() {
        return standaloneGlTextureId;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        try {
            IResource resource = manager.getResource(actualTexturePath);
            BufferedImage image = TextureUtil.readBufferedImage(resource.getInputStream());

            AnimationMetadataSection animMeta = resource.getMetadata("animation");

            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();

            this.width = imgWidth;
            this.height = animMeta != null ? imgWidth : imgHeight;

            int[] pixels = new int[imgWidth * imgHeight];

            if (imgWidth > 256 || imgHeight > 256) {
                this.standalone = true;
                uploadStandaloneTexture(image);
                int[][] pixelData = new int[MAX_MIPMAP_LEVELS][];
                pixelData[0] = pixels;
                this.framesTextureData.add(pixelData);
                return false;
            }

            image.getRGB(0, 0, imgWidth, imgHeight, pixels, 0, imgWidth);

            if (animMeta == null) {
                int[][] pixelData = new int[MAX_MIPMAP_LEVELS][];
                pixelData[0] = pixels;
                this.framesTextureData.add(pixelData);
            } else {
                int frameHeight = this.width;
                int frameCount = imgHeight / frameHeight;

                if (frameCount <= 0) {
                    int[][] pixelData = new int[MAX_MIPMAP_LEVELS][];
                    pixelData[0] = pixels;
                    this.framesTextureData.add(pixelData);
                    return false;
                }

                if (animMeta.getFrameCount() > 0) {
                    List<AnimationFrame> validFrames = new ArrayList<>();
                    for (int i = 0; i < animMeta.getFrameCount(); i++) {
                        int frameIndex = animMeta.getFrameIndex(i);
                        if (frameIndex < frameCount) {
                            allocateFrameTextureData(frameIndex);
                            this.framesTextureData.set(frameIndex, extractFrameData(pixels, imgWidth, frameHeight, frameIndex));
                            validFrames.add(new AnimationFrame(frameIndex, animMeta.getFrameTimeSingle(i)));
                        }
                    }
                    if (!validFrames.isEmpty()) {
                        setAnimationMetadata(new AnimationMetadataSection(
                                validFrames,
                                this.width,
                                this.height,
                                animMeta.getFrameTime(),
                                animMeta.isInterpolate()
                        ));
                    }
                } else {
                    List<AnimationFrame> frames = new ArrayList<>();
                    for (int i = 0; i < frameCount; i++) {
                        int[][] frameData = extractFrameData(pixels, imgWidth, frameHeight, i);
                        this.framesTextureData.add(frameData);
                        frames.add(new AnimationFrame(i, -1));
                    }
                    setAnimationMetadata(new AnimationMetadataSection(
                            frames,
                            this.width,
                            this.height,
                            animMeta.getFrameTime(),
                            animMeta.isInterpolate()
                    ));
                }
            }

            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private int[][] extractFrameData(int[] pixels, int imgWidth, int frameHeight, int frameIndex) {
        int[][] result = new int[MAX_MIPMAP_LEVELS][];
        int frameSize = imgWidth * frameHeight;
        int offset = frameIndex * frameSize;

        result[0] = new int[frameSize];
        System.arraycopy(pixels, offset, result[0], 0, frameSize);

        return result;
    }

    private void setAnimationMetadata(AnimationMetadataSection metadata) {
        if (ANIMATION_METADATA_FIELD != null) {
            try {
                ANIMATION_METADATA_FIELD.set(this, metadata);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void allocateFrameTextureData(int index) {
        while (this.framesTextureData.size() <= index) {
            this.framesTextureData.add(null);
        }
    }

    @Override
    public float getMinU() {
        return standalone ? 0.0f : super.getMinU();
    }

    @Override
    public float getMaxU() {
        return standalone ? 1.0f : super.getMaxU();
    }

    @Override
    public float getMinV() {
        return standalone ? 0.0f : super.getMinV();
    }

    @Override
    public float getMaxV() {
        return standalone ? 1.0f : super.getMaxV();
    }

    @Override
    public float getInterpolatedU(double u) {
        return standalone ? (float)(u / 16.0) : super.getInterpolatedU(u);
    }

    @Override
    public float getInterpolatedV(double v) {
        return standalone ? (float)(v / 16.0) : super.getInterpolatedV(v);
    }

    @Override
    public void generateMipmaps(int level) {
        if (standalone) return;
        super.generateMipmaps(level);
    }

    private void uploadStandaloneTexture(BufferedImage image) {
        if (standaloneGlTextureId != -1) return;
        standaloneGlTextureId = GL11.glGenTextures();
        GlStateManager.bindTexture(standaloneGlTextureId);
        TextureUtil.uploadTextureImageAllocate(standaloneGlTextureId, image, false, false);
    }
}