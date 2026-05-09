package com.slize.datarium.mixin.render.textures;

import com.google.common.collect.Lists;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.data.AnimationFrame;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite {

    @Shadow
    protected List<int[][]> framesTextureData;

    @Shadow
    private AnimationMetadataSection animationMetadata;

    @Shadow
    protected int width;

    @Shadow
    protected int height;

    @Shadow
    protected abstract void allocateFrameTextureData(int index);

    /**
     * Fixes crash with invalid frame indices for sprites where frame amount in .png.mcmeta file exceeds image bounds.
     * Also fixes the IOOBE crash by ensuring at least one frame is loaded if metadata is invalid.
     * @author Th3_Sl1ze
     */
    @Overwrite
    public void loadSpriteFrames(IResource resource, int mipmaplevels) throws IOException {
        BufferedImage bufferedimage = TextureUtil.readBufferedImage(resource.getInputStream());
        AnimationMetadataSection animationmetadatasection = resource.getMetadata("animation");

        int[][] aint = new int[mipmaplevels][];
        aint[0] = new int[bufferedimage.getWidth() * bufferedimage.getHeight()];
        bufferedimage.getRGB(0, 0, bufferedimage.getWidth(), bufferedimage.getHeight(), aint[0], 0, bufferedimage.getWidth());

        if (animationmetadatasection == null) {
            this.framesTextureData.add(aint);
        } else {
            int availableFrames = bufferedimage.getHeight() / this.width;

            if (animationmetadatasection.getFrameCount() > 0) {
                List<AnimationFrame> validFrames = new ArrayList<>();

                for (int i = 0; i < animationmetadatasection.getFrameCount(); i++) {
                    int index = animationmetadatasection.getFrameIndex(i);

                    if (index >= availableFrames) {
                        continue;
                    }

                    this.allocateFrameTextureData(index);
                    this.framesTextureData.set(index, datarium$getFrameTextureData(aint, this.width, this.width, index));

                    validFrames.add(new AnimationFrame(index, animationmetadatasection.getFrameTimeSingle(i)));
                }

                if (!validFrames.isEmpty()) {
                    this.animationMetadata = new AnimationMetadataSection(
                            validFrames,
                            this.width,
                            this.height,
                            animationmetadatasection.getFrameTime(),
                            animationmetadatasection.isInterpolate()
                    );
                } else {
                    this.allocateFrameTextureData(0);
                    this.framesTextureData.set(0, datarium$getFrameTextureData(aint, this.width, this.width, 0));
                    this.animationMetadata = null;
                }
            } else {
                List<AnimationFrame> list = Lists.newArrayList();

                for (int k = 0; k < availableFrames; ++k) {
                    this.framesTextureData.add(datarium$getFrameTextureData(aint, this.width, this.width, k));
                    list.add(new AnimationFrame(k, -1));
                }

                this.animationMetadata = new AnimationMetadataSection(
                        list,
                        this.width,
                        this.height,
                        animationmetadatasection.getFrameTime(),
                        animationmetadatasection.isInterpolate()
                );
            }
        }
    }

    /**
     * Duplicate of TextureAtlasSprite.getFrameTextureData.
     * No, I can't shadow it. Live with it.
     */
    @Unique
    private static int[][] datarium$getFrameTextureData(int[][] data, int rows, int columns, int frameIndex) {
        int[][] aint = new int[data.length][];

        for(int i = 0; i < data.length; ++i) {
            int[] aint1 = data[i];
            if (aint1 != null) {
                aint[i] = new int[(rows >> i) * (columns >> i)];
                System.arraycopy(aint1, frameIndex * aint[i].length, aint[i], 0, aint[i].length);
            }
        }

        return aint;
    }
}